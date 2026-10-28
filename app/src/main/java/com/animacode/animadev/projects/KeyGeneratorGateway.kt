package com.animacode.animadev.projects

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Base64
import androidx.compose.runtime.mutableIntStateOf
import com.animacode.animadev.repos.RepoStore
import com.animacode.animadev.termux.TermuxGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/** Stores only IDs, aliases, SAF URIs and output SHA hashes, never any plaintext password. */
class KeyGeneratorStore(private val context: Context) {
    companion object {
        val generation=mutableIntStateOf(0)
        const val CALLBACK="com.animacode.animadev.KEYGEN_RESULT"
        val busyStates=setOf("REQUESTED","UNKNOWN")
    }
    private val prefs=context.getSharedPreferences("generated_keys_v1",Context.MODE_PRIVATE)
    fun read():JSONObject=runCatching {
        val j=JSONObject(prefs.getString("job","{}") ?: "{}")
        if(j.optString("state")=="REQUESTED" && j.optString("phase") in setOf("PREPARING","ENROLLING")
            && System.currentTimeMillis()-j.optLong("started")>300_000L) {
            write(j.put("state","UNKNOWN").put("message","Соединение прервалось. Проверьте результат до повторной попытки."))
        }
        j
    }.getOrElse {JSONObject().put("state","UNKNOWN")}
    fun busy()=read().optString("state") in busyStates
    fun write(j:JSONObject)=prefs.edit().putString("job",j.toString()).commit().also {if(it)generation.intValue++}
    fun begin(id:String,project:String,title:String,alias:String,bits:Int,jks:Uri,backup:Uri):Boolean {
        if(busy())return false
        return write(JSONObject().put("id",id).put("project",project).put("title",title)
            .put("alias",alias).put("bits",bits).put("jks_dest",jks.toString())
            .put("archive_dest",backup.toString()).put("state","REQUESTED")
            .put("phase","PREPARING").put("started",System.currentTimeMillis())
            .put("message","Готовим одноразовое шифрование"))
    }
    fun fail(id:String,code:String,uncertain:Boolean=false) {
        val j=read()
        if(j.optString("id")==id && j.optString("state")=="REQUESTED")
            write(j.put("state",if(uncertain)"UNKNOWN" else "FAILED")
                .put("message",code).put("ended",System.currentTimeMillis()))
    }
    fun acknowledge():Boolean {
        val j=read()
        if(j.optString("state")!="UNKNOWN")return false
        return write(j.put("state","ACKNOWLEDGED")
            .put("message","Результат проверен пользователем; разрешена новая попытка"))
    }
}

class KeyGeneratorGateway(private val context:Context) {
    private val resolver=context.contentResolver
    private val state=KeyGeneratorStore(context)
    private fun stage(name:String,mime:String):Uri {
        val entry=ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME,name)
            put(MediaStore.Downloads.MIME_TYPE,mime)
            put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/")
            put(MediaStore.Downloads.IS_PENDING,0)
        }
        return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,entry)
            ?:error("MEDIASTORE_UNAVAILABLE")
    }
    private fun remove(raw:String) {
        if(raw.startsWith("content://media/"))runCatching {resolver.delete(Uri.parse(raw),null,null)}
    }
    private fun hash(source:java.io.InputStream,output:java.io.OutputStream?):Pair<String,Long> {
        val sha=MessageDigest.getInstance("SHA-256")
        val bytes=ByteArray(65536)
        var size=0L
        while(true) {
            val n=source.read(bytes)
            if(n<0)break
            if(n==0)continue
            size+=n
            check(size<=2L*1024*1024) {"KEYGEN_SIZE_DENIED"}
            sha.update(bytes,0,n)
            output?.write(bytes,0,n)
        }
        return sha.digest().joinToString(""){"%02x".format(it)} to size
    }
    private fun validUri(uri:Uri):Boolean=uri.scheme=="content" &&
        context.contentResolver.persistedUriPermissions.any {it.uri==uri && it.isReadPermission && it.isWritePermission}

    suspend fun launch(project:String,title:String,alias:String,bits:Int,archivePassword:String,
                       jksDestination:Uri,archiveDestination:Uri,makeDefault:Boolean):String {
        if(!alias.matches(Regex("[A-Za-z0-9_.-]{1,100}")) || title.isBlank() || title.length>70 ||
            bits !in listOf(2048,4096) || archivePassword.length !in 12..256 ||
            '\n' in archivePassword || '\r' in archivePassword)
            return "Проверьте alias, размер RSA и пароль архива (не менее 12 символов)."
        if(!validUri(jksDestination) || !validUri(archiveDestination) || jksDestination==archiveDestination)
            return "Выберите два разных доступных файла для JKS и архива 7z."
        if(context.checkSelfPermission(TermuxGateway.PERMISSION)!=android.content.pm.PackageManager.PERMISSION_GRANTED ||
            TermuxGateway(context).installedVersion()==null)return "Сначала предоставьте доступ Termux RUN_COMMAND."
        if(ReleaseStore(context).busy() || ManualApkSignStore(context).busy() || RepoStore(context).blocked())
            return "Другая сборка или подпись ещё активна."
        val id=UUID.randomUUID().toString().replace("-","")
        if(!state.begin(id,project,title,alias,bits,jksDestination,archiveDestination))
            return "Предыдущее создание ключа не завершено."
        var sent=false
        var jksMedia:Uri?=null
        var backupMedia:Uri?=null
        val random=ByteArray(32).also{SecureRandom().nextBytes(it)}
        val jksPass=Base64.encodeToString(random,Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        random.fill(0)
        val vault=SigningSecretVault(context)
        try {
            check(vault.save("gen_"+id,jksDestination.toString(),alias,jksPass,"")) {"VAULT_UNAVAILABLE"}
            jksMedia=stage("AnimaDev_gen_"+id+".jks","application/octet-stream")
            backupMedia=stage("AnimaDev_backup_"+id+".7z","application/x-7z-compressed")
            check(state.write(state.read().put("jks_media",jksMedia.toString())
                .put("archive_media",backupMedia.toString())
                .put("default",makeDefault).put("phase","ENROLLING")))
            val public=ReleaseHybridTransport.enroll(context,id) ?: error("ENROLLMENT_UNAVAILABLE")
            val request=JSONObject().put("request_id",id).put("variant","GENERATE_JKS")
                .put("alias",alias).put("bits",bits).put("jks_password",jksPass)
                .put("archive_password",archivePassword)
            val envelope=ReleaseHybridTransport.seal(id,public,request) ?:error("ENCRYPTION_FAILED")
            val script=context.assets.open("release/secure_7z_pty.py").bufferedReader().use {it.readText()}+"\n"+
                context.assets.open("release/keygen_archive_worker.py").bufferedReader().use {it.readText()}
            check(script.length<50000) {"WORKER_INVALID"}
            val receiver=Intent(context,KeyGeneratorResultReceiver::class.java).apply {
                action=KeyGeneratorStore.CALLBACK
                data=Uri.parse("animadev://keygen/"+id)
            }
            val flags=PendingIntent.FLAG_ONE_SHOT or
                if(Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0
            val callback=PendingIntent.getBroadcast(context,id.hashCode(),receiver,flags)
            val command=Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux","com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH",TermuxGateway.PYTHON)
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS",arrayOf("-c",script,id,ReleaseHybridTransport.sourceHash(context)))
                putExtra("com.termux.RUN_COMMAND_STDIN",envelope)
                putExtra("com.termux.RUN_COMMAND_WORKDIR",TermuxGateway.HOME)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL","0")
                putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL","AnimaDev · secure JKS generation")
                putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback)
            }
            try {
                check(context.startService(command)!=null) {"TERMUX_UNAVAILABLE"}
                sent=true
            } catch(e:Exception) {callback.cancel();throw e}
            state.write(state.read().put("phase","GENERATING")
                .put("message","Создаём JKS и зашифрованный архив. Не запускайте повторно."))
            return "Генерация запущена. Результат появится ниже."
        } catch(_:Exception) {
            state.fail(id,"Не удалось подготовить защищённую генерацию ключа.",sent)
            if(!sent) {
                jksMedia?.let{remove(it.toString())}
                backupMedia?.let{remove(it.toString())}
                vault.forget("gen_"+id)
                runCatching {DocumentsContract.deleteDocument(resolver,jksDestination)}
                runCatching {DocumentsContract.deleteDocument(resolver,archiveDestination)}
            }
            return "Генерация не запущена. Ничего не перезаписано."
        }
    }

    suspend fun finish(id:String,payload:JSONObject,ok:Boolean) {
        val rec=state.read()
        if(rec.optString("id")!=id || rec.optString("state")!="REQUESTED")return
        val vault=SigningSecretVault(context)
        val sourceJks=rec.optString("jks_media")
        val sourceArchive=rec.optString("archive_media")
        val jksDestination=Uri.parse(rec.optString("jks_dest"))
        val archiveDestination=Uri.parse(rec.optString("archive_dest"))
        var verified=false
        try {
            check(ok && payload.optBoolean("ok")) {payload.optString("error_code","RESULT_UNCONFIRMED")}
            val artifact=payload.getJSONObject("artifact")
            check(artifact.getString("kind")=="JKS_BACKUP" && artifact.getString("request_id")==id &&
                artifact.getString("jks_sha256").matches(Regex("[a-f0-9]{64}")) &&
                artifact.getString("archive_sha256").matches(Regex("[a-f0-9]{64}")) &&
                artifact.getLong("jks_size") in 100..(96*1024L) &&
                artifact.getLong("archive_size") in 200..(2*1024*1024L) &&
                artifact.getString("alias")==rec.optString("alias") &&
                artifact.getInt("key_bits")==rec.optInt("bits")) {"RESULT_MALFORMED"}
            check(artifact.getString("jks_sha256").length==64)
            suspend fun copyVerify(raw:String,dest:Uri,digest:String,size:Long) {
                val uri=Uri.parse(raw)
                withContext(Dispatchers.IO) {
                    val input=resolver.openInputStream(uri) ?: error("STAGED_MISSING")
                    val copied=input.use { data ->
                        resolver.openOutputStream(dest,"wt")?.use { hash(data,it) }
                            ?: error("DEST_UNAVAILABLE")
                    }
                    check(copied.first==digest && copied.second==size) {"OUTPUT_HASH_MISMATCH"}
                    val again=resolver.openInputStream(dest)?.use {hash(it,null)}
                        ?:error("READBACK_UNAVAILABLE")
                    check(again==copied) {"READBACK_HASH_MISMATCH"}
                }
            }
            copyVerify(sourceJks,jksDestination,artifact.getString("jks_sha256"),artifact.getLong("jks_size"))
            copyVerify(sourceArchive,archiveDestination,artifact.getString("archive_sha256"),artifact.getLong("archive_size"))
            verified=true
            val jksName=rec.optString("title").replace(Regex("[^A-Za-z0-9_-]"),"_").take(60)+".jks"
            val ring=SigningKeyring(context)
            val profile=ring.add(rec.optString("title"),jksDestination,jksName,rec.optString("alias"))
                ?:error("KEYRING_SAVE_FAILED")
            val pending=vault.load("gen_"+id,jksDestination.toString(),rec.optString("alias"))
                ?:error("PASSWORD_RECOVERY_FAILED")
            check(ring.saveCredentials(profile,pending.storePassword,"")) {"VAULT_SAVE_FAILED"}
            if(rec.optBoolean("default"))check(ring.setDefault(rec.optString("project"),profile)) {"DEFAULT_SAVE_FAILED"}
            vault.forget("gen_"+id)
            remove(sourceJks);remove(sourceArchive)
            state.write(rec.put("state","SUCCEEDED").put("phase","DONE")
                .put("profile_id",profile.id)
                .put("jks_sha256",artifact.getString("jks_sha256"))
                .put("archive_sha256",artifact.getString("archive_sha256"))
                .put("message","JKS создан, резервная копия проверена, ключ добавлен в библиотеку.")
                .put("ended",System.currentTimeMillis()))
        } catch(e:Exception) {
            val code=e.message.orEmpty()
            val certain=code in setOf("ARCHIVE_ENCRYPTION_FAILED","ARCHIVE_VERIFICATION_FAILED","TOOL_UNAVAILABLE",
                "KEY_SPEC_INVALID","JKS_GENERATION_FAILED","RESULT_MALFORMED","OUTPUT_NOT_EMPTY","JOB_BUSY")
            state.write(rec.put("state",if(certain)"FAILED" else "UNKNOWN")
                .put("message",if(verified)"Файлы сохранены, но регистрация ключа не подтверждена. Проверьте JKS/архив." else
                    "Результат генерации не подтверждён: "+code)
                .put("ended",System.currentTimeMillis()))
            if(certain && !verified) {
                remove(sourceJks);remove(sourceArchive)
                vault.forget("gen_"+id)
                runCatching {DocumentsContract.deleteDocument(resolver,jksDestination)}
                runCatching {DocumentsContract.deleteDocument(resolver,archiveDestination)}
            }
        }
    }
}

class KeyGeneratorResultReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val uri=intent.data ?:return
        val id=uri.lastPathSegment ?:return
        if(intent.action!=KeyGeneratorStore.CALLBACK || uri.scheme!="animadev" ||
            uri.host!="keygen" || !id.matches(Regex("[a-f0-9]{32}")))return
        if(KeyGeneratorStore(context).read().optString("id")!=id)return
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bundle=intent.getBundleExtra("result")
                val output=bundle?.getString("stdout","").orEmpty()
                val reply=if(output.length<=8192) JSONObject(output) else JSONObject()
                val valid=bundle?.getInt("exitCode")==0 && bundle.getInt("err")==-1 &&
                    reply.optInt("version")==1 && reply.optString("request_id")==id
                KeyGeneratorGateway(context).finish(id,reply,valid)
            } catch(_:Exception) {
                KeyGeneratorStore(context).fail(id,"Не удалось подтвердить результат. Проверьте файлы.",true)
            } finally {pending.finish()}
        }
    }
}
