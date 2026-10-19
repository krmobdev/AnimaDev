package com.animacode.animadev.projects

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.animacode.animadev.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

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


    private const val INSTALL_CACHE="shared_apk"
    private const val MIME_APK="application/vnd.android.package-archive"

    /** A document picked with OpenDocument can be used again without Termux. */
    fun rememberedUri(context:Context,artifact:JSONObject):Uri? {
        val sha=artifact.optString("sha256")
        if(!sha.matches(Regex("[a-f0-9]{64}")))return null
        val saved=context.getSharedPreferences("apk_documents_v1",Context.MODE_PRIVATE)
            .getString(sha,null) ?:return null
        return runCatching {Uri.parse(saved)}.getOrNull()
    }

    private fun clearRememberedUri(context:Context,sha:String) {
        context.getSharedPreferences("apk_documents_v1",Context.MODE_PRIVATE)
            .edit().remove(sha).apply()
    }

    /** Creates an app-owned, checksum-verified copy before granting Android installer access. */
    private fun verifiedCopy(context:Context,source:Uri,sha:String,size:Long):File {
        val directory=File(context.cacheDir,INSTALL_CACHE)
        check(directory.isDirectory || directory.mkdirs()) {"CACHE_UNAVAILABLE"}
        check(!Files.isSymbolicLink(directory.toPath())) {"CACHE_UNAVAILABLE"}
        val target=File(directory,"$sha.apk")
        check(!Files.isSymbolicLink(target.toPath())) {"CACHE_UNAVAILABLE"}
        // Copy again from the user-authorized URI; never trust metadata alone.
        val temp=File.createTempFile(".verify-",".tmp",directory)
        try {
            val digest=MessageDigest.getInstance("SHA-256")
            var length=0L
            context.contentResolver.openInputStream(source)?.use {input->
                FileOutputStream(temp).use {output->
                    val buffer=ByteArray(1024*1024)
                    while(true) {
                        val count=input.read(buffer)
                        if(count<0)break
                        length+=count
                        check(length<=size) {"APK_SIZE_MISMATCH"}
                        digest.update(buffer,0,count)
                        output.write(buffer,0,count)
                    }
                    output.fd.sync()
                }
            } ?:error("APK_UNREADABLE")
            check(length==size && digest.digest().joinToString("") {"%02x".format(it)}==sha) {
                "APK_CHECKSUM_MISMATCH"
            }
            check(temp.renameTo(target)) {"CACHE_UNAVAILABLE"}
            // Retain the APK currently passed to FileProvider and at most two previous builds.
            // Never follow symlinks or remove unrelated files from the private cache.
            InstallCachePolicy.prune(directory,target)
            return target
        } finally {
            temp.delete()
        }
    }

    fun installerIntent(context:Context,apk:File):Intent {
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.reports",apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri,MIME_APK)
            clipData=ClipData.newUri(context.contentResolver,"Android APK",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun install(context:Context,artifact:JSONObject,uri:Uri):Boolean {
        val path=artifact.optString("path")
        val sha=artifact.optString("sha256")
        val size=artifact.optLong("size")
        if(!path.endsWith(".apk") || !ArtifactPolicy.valid(path,sha,size)) {
            message(context,"Нет проверенного APK.");return false
        }
        val result=withContext(Dispatchers.IO) {
            runCatching {verifiedCopy(context,uri,sha,size)}
        }
        val apk=result.getOrNull()
        if(apk==null) {
            clearRememberedUri(context,sha)
            message(context,"Не удалось прочитать или проверить APK. Выберите файл заново в системном проводнике.")
            return false
        }
        // Save only an authorized document URI, never a direct filesystem path.
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.getSharedPreferences("apk_documents_v1",Context.MODE_PRIVATE)
                .edit().putString(sha,uri.toString()).apply()
        }
        if(Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()) {
            message(context,"Разрешите установку приложений из AnimaDev, затем нажмите «Установить APK» ещё раз.")
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:"+context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return true
        }
        try {
            // Android's Package Installer receives a FileProvider content URI;
            // no termux-open, Termux service, file:// URI or shell command.
            context.startActivity(installerIntent(context,apk))
            return true
        } catch(_:Exception) {
            message(context,"Системный установщик Android не открылся. Проверьте разрешение на установку APK.")
            return false
        }
    }

    fun openFolder(context:Context,artifact:JSONObject) {
        val path=artifact.optString("path")
        if(!ArtifactPolicy.valid(path,artifact.optString("sha256"),artifact.optLong("size"))) {
            message(context,"Нет проверенного файла сборки.");return
        }
        val destination=ExportDestination(context)
        val parent=path.substringBeforeLast('/')
        val folder=if(parent==destination.artifactDirectory())destination.folderUri()
            else DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents",
                "primary:"+parent.removePrefix("/storage/emulated/0/"))
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

    internal fun message(context:Context,text:String)=Toast.makeText(context,
        AppTranslation.translate(text,AppLanguage.english(context)),Toast.LENGTH_LONG).show()
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
