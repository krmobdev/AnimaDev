package com.animacode.animadev.projects

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.runtime.mutableIntStateOf
import com.animacode.animadev.repos.RepoGateway
import com.animacode.animadev.repos.RepoStore
import com.animacode.animadev.termux.TermuxGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    fun read():JSONObject=runCatching {
        val state=JSONObject(prefs.getString("job","{}") ?: "{}")
        if(state.optString("state")=="REQUESTED" && state.optString("phase")=="ENROLLING"
            && System.currentTimeMillis()-state.optLong("started_at")>180_000L) {
            state.put("state","FAILED").put("message",
                "Согласование шифрования не завершилось. JKS не передавался; можно повторить.")
            prefs.edit().putString("job",state.toString()).commit()
        }
        state
    }.getOrElse {
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
            .put("state","REQUESTED").put("phase","ENROLLING").put("started_at",System.currentTimeMillis())
            .put("message","Termux собирает Release AAB. Задание может занять несколько минут; не запускайте его повторно."))
    }
    fun failedWithoutTransfer(id:String,message:String) {
        val old=read()
        if(old.optString("id")==id && old.optString("state")=="REQUESTED")
            write(old.put("state","FAILED").put("message",message))
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
        if(store.busy())return "Release AAB уже выполняется или его результат неизвестен."
        if(!project.supported || !RepoGateway(context).mayBuild(repo,project.id)
           || repo.optBoolean("pending") || RepoStore(context).blocked() || context.checkSelfPermission(TermuxGateway.PERMISSION)!=android.content.pm.PackageManager.PERMISSION_GRANTED
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
            val profile=JSONObject().put("id",project.id).put("name",project.name)
                .put("root",project.path)
                .put("remote",if(project.id=="animaquiz")"github.com/krmobdev/quizapp" else project.remote)
                .put("branch",if(project.id=="animaquiz")"test/decomposition-v1" else project.branch)
            val payload=JSONObject().put("request_id",request).put("project",profile)
                .put("alias",alias).put("keystore",Base64.encodeToString(key,Base64.NO_WRAP))
                .put("store_password",storePassword).put("key_password",keyPassword)
            val reserved=synchronized(BuildConcurrencyPolicy.launchMonitor) {
                val current=RepoStore(context)
                if(current.blocked() || current.read().optBoolean("pending"))false
                else store.begin(project.id,request,sha)
            }
            if(!reserved)return "Другая операция Termux ещё активна. Дождитесь подтверждения её результата."

            // Stage one: no user secrets, only a Termux-generated one-time RSA public key.
            val publicKey=ReleaseHybridTransport.enroll(context,request)
            if(publicKey==null) {
                store.failedWithoutTransfer(request,"Termux не вернул одноразовый ключ шифрования. JKS не передавался.")
                return "Не удалось согласовать защищённый обмен с Termux. Секреты не отправлены."
            }
            if(store.read().optString("state")!="REQUESTED")
                return "Подготовка Release отменена до передачи секретов."
            val sealed=ReleaseHybridTransport.seal(request,publicKey,payload)
            if(sealed==null) {
                store.failedWithoutTransfer(request,"Не удалось зашифровать JKS для Termux. Ничего не отправлено.")
                return "Android не смог зашифровать Release-задание."
            }
            val transportSha=ReleaseHybridTransport.sourceHash(context)
            if(!store.write(store.read().put("phase","DISPATCHING")
                    .put("message","Шифрованное задание отправляется в Termux. Ожидаем подтверждение подписи.")))
                return "Не удалось сохранить состояние задания. Повторно пока не запускайте."

            val callbackIntent=Intent(context,ReleaseResultReceiver::class.java).apply {
                action=ReleaseWire.CALLBACK
                this.data=Uri.parse("animadev://release/$request")
            }
            val flags=PendingIntent.FLAG_ONE_SHOT or
                if(Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0
            val callback=PendingIntent.getBroadcast(context,request.hashCode(),callbackIntent,flags)
            // Only ciphertext passes RUN_COMMAND_STDIN. Neither plaintext JKS nor passwords
            // are present in Android Intent arguments, logs, stdout, or GitHub.
            val command=Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux","com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH",TermuxGateway.PYTHON)
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS",arrayOf("-c",script,request,transportSha))
                putExtra("com.termux.RUN_COMMAND_STDIN",sealed)
                putExtra("com.termux.RUN_COMMAND_WORKDIR",TermuxGateway.HOME)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL","0")
                putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL","AnimaDev · зашифрованная сборка Release AAB")
                putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback)
            }
            return try {
                if(context.startService(command)==null) {
                    callback.cancel()
                    store.failedWithoutTransfer(request,"Termux отказал в запуске до передачи шифротекста.")
                    "Termux не подтвердил запуск. Секреты не раскрыты."
                } else "Шифрованное задание отправлено в Termux. Сборка и подпись выполняются локально."
            } catch (_:Exception) {
                callback.cancel()
                store.failedWithoutTransfer(request,"Android отклонил передачу зашифрованного задания.")
                "Android отказал в запуске Termux. Пароли не раскрыты."
            }

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
                record.put("state",when {
                    code=="CANCELLED"->"CANCELLED"
                    code.endsWith("_UNKNOWN") || code=="RESULT_UNCONFIRMED"->"UNKNOWN"
                    else->"FAILED"
                })
                    .put("message",when(code) {
                        "CANCELLED"->"Сборка остановлена через Ctrl+C. Подписанный AAB не опубликован."
                        "ENVELOPE_MISSING"->"Termux не получил зашифрованное содержимое. Проверьте RUN_COMMAND_STDIN."
                        "TRANSPORT_DECRYPT_FAILED"->"Termux получил данные, но отклонил шифрование. Проверьте связь и повторите с новым одноразовым ключом."
                        "TRANSPORT_UNAVAILABLE"->"Не удалось запустить локальный модуль расшифровки в Termux."
                        else->"Release не подтверждён: $code."
                    })
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
