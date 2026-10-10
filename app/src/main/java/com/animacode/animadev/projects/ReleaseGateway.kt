package com.animacode.animadev.projects

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.LocalServerSocket
import android.os.Build
import android.util.Base64
import androidx.compose.runtime.mutableIntStateOf
import com.animacode.animadev.repos.RepoGateway
import com.animacode.animadev.repos.RepoStore
import com.animacode.animadev.termux.TermuxGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import org.json.JSONObject
import java.util.UUID

/** Only IDs, progress and artifact checksums survive process death. Never credentials. */
class ReleaseStore(context:Context) {
    companion object {
        val generation=mutableIntStateOf(0)
        private const val PREFS="release_jobs_v1"
        val active=setOf("REQUESTED","UNKNOWN")
    }
    private val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    fun read():JSONObject=runCatching {JSONObject(prefs.getString("job","{}") ?: "{}")}.getOrElse {
        JSONObject().put("state","UNKNOWN").put("message","История Release повреждена. Не запускайте повторную сборку.")
    }
    fun busy():Boolean=read().optString("state") in active
    fun write(record:JSONObject):Boolean {
        val ok=prefs.edit().putString("job",record.toString()).commit()
        if(ok)generation.intValue++
        return ok
    }
    fun begin(project:String,id:String,sha:String):Boolean {
        if(busy())return false
        return write(JSONObject().put("project",project).put("id",id).put("sha",sha)
            .put("state","REQUESTED").put("started_at",System.currentTimeMillis())
            .put("message","Termux собирает Release AAB. Задание может занять несколько минут; не запускайте его повторно."))
    }
    fun failed(id:String,message:String) {
        val old=read()
        if(old.optString("id")==id && old.optString("state")=="REQUESTED")
            write(old.put("state","UNKNOWN").put("message",message))
    }
}

/** One local Android-to-Termux command. No key or password is passed as an argument. */
class ReleaseGateway(private val context:Context) {
    private val store=ReleaseStore(context)
    private val signing=SigningStore(context)

    suspend fun launch(project:LocalProject,storePassword:String,keyPassword:String):String {
        val repo=RepoStore(context).read()
        if(store.busy())return "Предыдущее Release-задание ещё не подтверждено."
        if(!project.supported || !RepoGateway(context).mayBuild(repo,project.id)
           || repo.optBoolean("pending") || context.checkSelfPermission(TermuxGateway.PERMISSION)!=android.content.pm.PackageManager.PERMISSION_GRANTED
           || TermuxGateway(context).installedVersion()==null) {
            return "Сначала проверьте GitHub SHA и готовность Termux. Повторный запуск при активном задании запрещён."
        }
        if(!signing.hasDocument(project.id))return "Выберите файл JKS через настройки подписи."
        val alias=signing.alias(project.id).trim()
        if(!alias.matches(Regex("[A-Za-z0-9_.-]{1,100}")))return "Проверьте alias ключа."
        if(storePassword.isEmpty() || storePassword.length>1024 || keyPassword.length>1024)
            return "Пароль пуст или превышает допустимую длину."
        val sha=repo.optJSONObject("snapshot")?.optString("local_commit") ?: ""
        if(!sha.matches(Regex("[a-f0-9]{40,64}")))return "GitHub SHA не подтверждён."
        val file=Uri.parse(signing.documentUri(project.id))
        val data=withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(file)?.use { it.readNBytes(ReleaseWire.MAX_KEY_BYTES+1) }
            } catch(_:Exception) { null }
        } ?: return "Невозможно прочитать JKS. Выберите файл повторно."
        val key=data
        try {
            if(key.size>ReleaseWire.MAX_KEY_BYTES)return "Этот JKS больше 96 КБ. Передача остановлена; ключ не изменён."
            if(key.size<32 || !JksInspector.isJks(key))return "Для текущего безопасного профиля нужен настоящий JKS."
            val password=storePassword.toCharArray()
            try {
                val outcome=JksInspector.inspect(key,alias,password)
                if(outcome!=SigningVerification.Outcome.JKS_STORE_AND_ALIAS_VERIFIED)
                    return SigningVerification.message(outcome)
            } finally {password.fill(0.toChar())}

            val script=withContext(Dispatchers.IO) {
                runCatching {
                    val signer=context.assets.open("release/release_signer.py").bufferedReader().use {it.readText()}
                    val worker=context.assets.open("release/release_worker.py").bufferedReader().use {it.readText()}
                    "$signer\n$worker"
                }.getOrNull()
            } ?: return "Нет подписывающего модуля в APK."
            if(script.length>60_000)return "Подписывающий модуль повреждён."
            val request=UUID.randomUUID().toString().replace("-","")
            val socketName="animadev-release-"+UUID.randomUUID().toString().replace("-","")
            val profile=JSONObject().put("id",project.id).put("name",project.name)
                .put("root",project.path)
                .put("remote",if(project.id=="animaquiz")"github.com/krmobdev/quizapp" else project.remote)
                .put("branch",if(project.id=="animaquiz")"test/decomposition-v1" else project.branch)
            val payload=JSONObject().put("request_id",request).put("project",profile)
                .put("alias",alias).put("keystore",Base64.encodeToString(key,Base64.NO_WRAP))
                .put("store_password",storePassword).put("key_password",keyPassword)
            val recipientUid=try {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo("com.termux",0).uid
            } catch(_:Exception) { return "Не удалось подтвердить установленный Termux." }
            val server=try {LocalServerSocket(socketName)}
                catch(_:Exception) {return "Android запретил защищённый локальный канал. Секреты не отправлены."}
            try {
                if(!store.begin(project.id,request,sha))return "Не удалось сохранить идентификатор задания."
                val callbackIntent=Intent(context,ReleaseResultReceiver::class.java).apply {
                    action=ReleaseWire.CALLBACK
                    this.data=Uri.parse("animadev://release/$request")
                }
                val flags=PendingIntent.FLAG_ONE_SHOT or
                    if(Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0
                val callback=PendingIntent.getBroadcast(context,request.hashCode(),callbackIntent,flags)
                // Important: the Intent contains NO credential bytes, only public socket coordinates.
                val command=Intent("com.termux.RUN_COMMAND").apply {
                    setClassName("com.termux","com.termux.app.RunCommandService")
                    putExtra("com.termux.RUN_COMMAND_PATH",TermuxGateway.PYTHON)
                    putExtra("com.termux.RUN_COMMAND_ARGUMENTS",arrayOf("-c",script,socketName,request,context.applicationInfo.uid.toString()))
                    putExtra("com.termux.RUN_COMMAND_WORKDIR",TermuxGateway.HOME)
                    putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
                    putExtra("com.termux.RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL","0")
                    putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL","AnimaDev · подписать Release AAB")
                    putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback)
                }
                try {
                    if(context.startService(command)==null) {
                        callback.cancel()
                        store.failed(request,"Termux не подтвердил запуск. Статус неизвестен; повтор заблокирован.")
                        return "Termux не подтвердил запуск Release."
                    }
                } catch (_:Exception) {
                    callback.cancel()
                    store.failed(request,"Android отказал в запуске. Статус неизвестен; повтор заблокирован.")
                    return "Не удалось отправить задание Termux."
                }
                val accepted=withContext(Dispatchers.IO) {
                    val watchdog=CoroutineScope(Dispatchers.IO).launch {
                        delay(20_000)
                        runCatching {server.close()}
                    }
                    try {
                        server.accept().use {client->
                            client.soTimeout=8_000
                            // Linux kernel credentials, not a caller-supplied string.
                            if(client.peerCredentials.uid!=recipientUid) return@withContext false
                            val greeting=ByteArray(32)
                            DataInputStream(client.inputStream).readFully(greeting)
                            if(!greeting.contentEquals(request.toByteArray(Charsets.US_ASCII)))
                                return@withContext false
                            val encoded=payload.toString().toByteArray(Charsets.UTF_8)
                            try {
                                if(encoded.size>180_000) return@withContext false
                                val output=DataOutputStream(client.outputStream)
                                output.writeInt(encoded.size)
                                output.write(encoded)
                                output.flush()
                            } finally {encoded.fill(0)}
                            true
                        }
                    } catch (_:Exception) {false}
                    finally {
                        watchdog.cancel()
                    }
                }
                if(!accepted) {
                    store.failed(request,"Локальный канал не прошёл проверку UID/nonce или недоступен. Передача секрета отменена.")
                    return "Защищённое соединение с Termux не установлено. Секреты не переданы."
                }
                return "Termux принял задание. Подписанный AAB появится после проверки SHA и сертификата."
            } finally {runCatching {server.close()}}
        } finally {key.fill(0)}
    }
}

object ReleaseWire {
    const val CALLBACK="com.animacode.animadev.RELEASE_RESULT"
    const val MAX_KEY_BYTES=96*1024
    fun validArtifact(raw:JSONObject, sha:String):Boolean=runCatching {
        raw.getString("kind")=="RELEASE_AAB" &&
            raw.getString("commit")==sha &&
            raw.getString("sha256").matches(Regex("[a-f0-9]{64}")) &&
            raw.getLong("size")>1024 &&
            raw.getString("path").matches(Regex("/storage/emulated/0/Download/[A-Za-z0-9_-]{1,120}[.]aab"))
    }.getOrDefault(false)
}
class ReleaseResultReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val store=ReleaseStore(context)
        val record=store.read()
        val uri=intent.data ?: return
        if(intent.action!=ReleaseWire.CALLBACK || uri.scheme!="animadev" || uri.host!="release"
           || uri.lastPathSegment!=record.optString("id") || record.optString("state")!="REQUESTED")
            return
        try {
            val result=intent.getBundleExtra("result") ?: error("Missing result")
            val stdout=result.getString("stdout","") ?: ""
            if(stdout.length>4096 || !result.containsKey("exitCode") || !result.containsKey("err"))
                error("Malformed reply")
            val payload=JSONObject(stdout)
            check(payload.optInt("version")==1 && payload.optString("request_id")==record.getString("id"))
            val ok=result.getInt("exitCode")==0 && result.getInt("err")==-1 && payload.optBoolean("ok")
            if(ok) {
                val artifact=payload.getJSONObject("artifact")
                check(ReleaseWire.validArtifact(artifact,record.getString("sha")))
                record.put("artifact",artifact).put("state","SUCCEEDED")
                    .put("message","Release AAB подписан, проверен и сохранён в Загрузки.")
            } else {
                val code=payload.optString("error_code","RESULT_UNCONFIRMED")
                if(!code.matches(Regex("[A-Z_]{3,50}")))error("Malformed code")
                // No secrets, stderr or raw command output are stored or displayed.
                record.put("state",if(code.endsWith("_UNKNOWN") || code=="RESULT_UNCONFIRMED")"UNKNOWN" else "FAILED")
                    .put("message","Release не подтверждён: $code. Пароли не сохранены.")
            }
            record.put("ended_at",System.currentTimeMillis())
            store.write(record)
        } catch(_:Exception) {
            store.write(record.put("state","UNKNOWN")
                .put("message","Результат подписи не подтверждён. Не запускайте повторно до проверки.")
                .put("ended_at",System.currentTimeMillis()))
        }
    }
}
