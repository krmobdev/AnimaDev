package com.animacode.animadev.projects

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.animacode.animadev.termux.TermuxGateway
import org.json.JSONObject
import java.util.UUID

object ArtifactPolicy {
    fun valid(path:String,sha:String,size:Long)=
        path.matches(Regex("/storage/emulated/0/Download/[A-Za-z0-9_-]+\\.(apk|aab)")) &&
        sha.matches(Regex("[a-f0-9]{64}")) && size in 1024L..536870912L
}

/** The Termux app grants its own file URI to Android's installer; no storage bypass. */
object ArtifactGateway {
    fun intent(context:Context,artifact:JSONObject)=Intent(context,ArtifactOpenReceiver::class.java)
        .setAction("com.animacode.animadev.OPEN_ARTIFACT")
        .putExtra("path",artifact.optString("path")).putExtra("sha256",artifact.optString("sha256"))
        .putExtra("size",artifact.optLong("size"))

    fun open(context:Context,artifact:JSONObject) {
        val path=artifact.optString("path");val sha=artifact.optString("sha256");val size=artifact.optLong("size")
        if(!ArtifactPolicy.valid(path,sha,size)) {message(context,"Нет проверенного файла сборки.");return}
        val termux=TermuxGateway(context)
        if(!termux.permitted() || termux.installedVersion()==null) {
            message(context,"Для открытия нужен Termux и разрешение RUN_COMMAND в настройках AnimaDev.");return
        }
        val id=UUID.randomUUID().toString().replace("-","")
        val prefs=context.getSharedPreferences("artifact_open_v1",Context.MODE_PRIVATE)
        val now=System.currentTimeMillis()
        prefs.edit().apply {
            prefs.all.forEach {(key,value)->if(value is Long && now-value>60000)remove(key)}
            putLong(id,now)
        }.apply()
        val callback=PendingIntent.getBroadcast(context,id.hashCode(),
            Intent(context,ArtifactResultReceiver::class.java)
                .setAction("com.animacode.animadev.ARTIFACT_RESULT")
                .setData(Uri.parse("animadev://artifact/$id")),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        try {
            val script=context.assets.open("artifact/open_artifact.py").bufferedReader().use {it.readText()}
            val command=Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux","com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH",TermuxGateway.PYTHON)
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS",arrayOf("-c",script,id,path,sha,size.toString()))
                putExtra("com.termux.RUN_COMMAND_WORKDIR",TermuxGateway.HOME)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
                putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL","AnimaDev · открыть готовую сборку")
                putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback)
            }
            check(context.startService(command)!=null)
            message(context,if(path.endsWith(".apk"))"Открываю установщик Android…" else "Открываю AAB…")
        } catch(_:Exception) {
            callback.cancel();prefs.edit().remove(id).apply()
            message(context,"Android не разрешил открыть файл. Откройте Termux и проверьте разрешение RUN_COMMAND.")
        }
    }
    internal fun message(context:Context,text:String)=Toast.makeText(context,text,Toast.LENGTH_LONG).show()
}

/** Notification actions are explicit, private PendingIntents, never external file paths. */
class ArtifactOpenReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!="com.animacode.animadev.OPEN_ARTIFACT")return
        ArtifactGateway.open(context,JSONObject().put("path",intent.getStringExtra("path"))
            .put("sha256",intent.getStringExtra("sha256")).put("size",intent.getLongExtra("size",0)))
    }
}
class ArtifactResultReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!="com.animacode.animadev.ARTIFACT_RESULT" ||
            intent.data?.scheme!="animadev" || intent.data?.host!="artifact")return
        val id=intent.data?.lastPathSegment ?: return
        val prefs=context.getSharedPreferences("artifact_open_v1",Context.MODE_PRIVATE)
        if(!id.matches(Regex("[a-f0-9]{32}")) || !prefs.contains(id))return
        prefs.edit().remove(id).apply()
        val result=intent.getBundleExtra("result")
        val payload=runCatching {JSONObject(result?.getString("stdout").orEmpty())}.getOrNull()
        val success=result?.containsKey("exitCode")==true && result.getInt("exitCode")==0 &&
            result.getInt("err",-1)==-1 && payload!=null && payload.optString("request_id")==id && payload.optBoolean("ok")
        if(success)return // Dispatch is confirmed; Android still asks the user to install.
        ArtifactGateway.message(context,when(payload?.optString("error_code")) {
            "ARTIFACT_CHANGED"->"Файл изменился или повреждён. Соберите APK заново."
            "OPENER_MISSING"->"В Termux отсутствует termux-open. Обновите пакет termux-tools."
            else->"Не удалось открыть сборку. Проверьте файл в Загрузках и запуск Termux."
        })
    }
}
