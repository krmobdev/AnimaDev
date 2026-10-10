package com.animacode.animadev.projects

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import com.animacode.animadev.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

object ArtifactPolicy {
    private val name=Regex("[A-Za-z0-9_-]+[.](apk|aab)")
    fun valid(path:String,sha:String,size:Long):Boolean {
        if(!sha.matches(Regex("[a-f0-9]{64}")) || size !in 1024L..536870912L)return false
        if(!path.startsWith("/storage/emulated/0/") || !name.matches(path.substringAfterLast('/')))return false
        val parent=path.removePrefix("/storage/emulated/0/").substringBeforeLast('/',"")
        return parent.isNotEmpty() && parent.split('/').all {
            it.matches(Regex("[\\p{L}\\p{N}_ ().&-]{1,80}")) && it!="." && it!=".."
        }
    }
}

/** Install from Android's own SAF content URI, not a background termux-open command. */
object ArtifactGateway {
    fun intent(context:Context,artifact:JSONObject)=Intent(context,ArtifactOpenReceiver::class.java)
        .setAction("com.animacode.animadev.OPEN_ARTIFACT")
        .putExtra("path",artifact.optString("path"))
        .putExtra("sha256",artifact.optString("sha256"))
        .putExtra("size",artifact.optLong("size"))

    suspend fun install(context:Context,artifact:JSONObject,uri:Uri) {
        val path=artifact.optString("path")
        val sha=artifact.optString("sha256")
        val size=artifact.optLong("size")
        if(!path.endsWith(".apk") || !ArtifactPolicy.valid(path,sha,size)) {
            message(context,"Нет проверенного APK.");return
        }
        val actual=withContext(Dispatchers.IO) {
            runCatching {
                val hash=MessageDigest.getInstance("SHA-256")
                var length=0L
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer=ByteArray(1024*1024)
                    while(true) {
                        val n=input.read(buffer)
                        if(n<0)break
                        length+=n
                        if(length>size)error("size")
                        hash.update(buffer,0,n)
                    }
                } ?:error("read")
                length==size && hash.digest().joinToString("") { "%02x".format(it) }==sha
            }.getOrDefault(false)
        }
        if(!actual) {message(context,"APK недоступен или SHA-256 не совпадает. Выберите файл этой сборки.");return}
        if(Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()) {
            message(context,"Разрешите установку из AnimaDev, затем нажмите кнопку ещё раз.")
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:"+context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri,"application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch(_:Exception) {message(context,"Не удалось запустить системный установщик APK.")}
    }

    fun openFolder(context:Context,artifact:JSONObject) {
        val path=artifact.optString("path")
        if(!ArtifactPolicy.valid(path,artifact.optString("sha256"),artifact.optLong("size"))) {
            message(context,"Нет проверенного файла сборки.");return
        }
        val destination=ExportDestination(context)
        val folder=destination.folderUri()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(folder,DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch(_:Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type="*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI,folder)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch(_:Exception) {message(context,"Не удалось открыть системный файловый менеджер.")}
        }
    }

    internal fun message(context:Context,text:String)=Toast.makeText(context,text,Toast.LENGTH_LONG).show()
}

/** Notification returns to the foreground build screen; it never claims installation succeeded. */
class ArtifactOpenReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!="com.animacode.animadev.OPEN_ARTIFACT")return
        val path=intent.getStringExtra("path").orEmpty()
        if(!ArtifactPolicy.valid(path,intent.getStringExtra("sha256").orEmpty(),
            intent.getLongExtra("size",0L)))return
        context.startActivity(Intent(context,MainActivity::class.java).apply {
            putExtra("open_build",true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
    }
}

/** Kept for compatibility with older explicit callbacks from installed versions. */
class ArtifactResultReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) = Unit
}
