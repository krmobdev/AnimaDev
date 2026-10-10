package com.animacode.animadev.projects

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.animacode.animadev.termux.TermuxGateway
import org.json.JSONObject

/**
 * Signed AAB is created in Downloads by the isolated signing worker. This
 * second, non-secret Termux command verifies its checksum before moving it to
 * the user's selected primary-storage directory. No signing protocol changes.
 */
object ArtifactMoveGateway {
    private const val CALLBACK="com.animacode.animadev.RELEASE_MOVE_RESULT"

    fun start(context:Context,record:JSONObject,artifact:JSONObject,destination:String):Boolean {
        val id=record.optString("id")
        val source=artifact.optString("path")
        val sha=artifact.optString("sha256")
        val size=artifact.optLong("size")
        if(!id.matches(Regex("[a-f0-9]{32}")) ||
            !source.matches(Regex("/storage/emulated/0/Download/[A-Za-z0-9_-]+[.]aab")) ||
            !ArtifactPolicy.valid(source,sha,size) ||
            destination=="/storage/emulated/0/Download" ||
            !destination.startsWith("/storage/emulated/0/") ||
            destination!=ExportDestination(context).artifactDirectory())return false
        val termux=TermuxGateway(context)
        if(!termux.permitted() || termux.installedVersion()==null)return false
        val callback=PendingIntent.getBroadcast(context,id.hashCode(),
            Intent(context,ArtifactMoveReceiver::class.java).apply {
                action=CALLBACK
                data=Uri.parse("animadev://release-move/$id")
            },PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        return try {
            val script=context.assets.open("artifact/move_artifact.py").bufferedReader().use {it.readText()}
            val command=Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux","com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH",TermuxGateway.PYTHON)
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS",arrayOf("-c",script,id,source,sha,size.toString(),destination))
                putExtra("com.termux.RUN_COMMAND_WORKDIR",TermuxGateway.HOME)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
                putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL","AnimaDev · перенос подписанного AAB")
                putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback)
            }
            check(context.startService(command)!=null)
            true
        } catch(_:Exception) {
            callback.cancel()
            false
        }
    }

    internal fun result(context:Context,record:JSONObject,ok:Boolean,path:String) {
        val store=ReleaseStore(context)
        val old=record.optJSONObject("artifact") ?:return
        val destination=ExportDestination(context).artifactDirectory()
        val valid=ok && destination!=null &&
            path.startsWith("$destination/") &&
            path.substringAfterLast('/')==old.optString("path").substringAfterLast('/') &&
            ArtifactPolicy.valid(path,old.optString("sha256"),old.optLong("size"))
        if(valid)old.put("path",path)
        record.put("state","SUCCEEDED").put("ended_at",System.currentTimeMillis())
            .put("message",if(valid)
                "Release AAB подписан, проверен и перенесён в выбранную папку."
                else "AAB подписан, но перенести его в выбранную папку не удалось. Исходный файл остался в Загрузках.")
        store.write(record)
    }

    internal const val ACTION=CALLBACK
}

class ArtifactMoveReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val record=ReleaseStore(context).read()
        val id=intent.data?.lastPathSegment ?:return
        if(intent.action!=ArtifactMoveGateway.ACTION ||
            intent.data?.scheme!="animadev" || intent.data?.host!="release-move" ||
            id!=record.optString("id") || record.optString("state")!="REQUESTED" ||
            record.optString("phase")!="EXPORTING")return
        val result=intent.getBundleExtra("result")
        val text=result?.getString("stdout","").orEmpty()
        val payload=runCatching {if(text.length>2048)null else JSONObject(text)}.getOrNull()
        val verified=payload?.optString("request_id")==id && payload?.optBoolean("ok")==true &&
            result?.getInt("exitCode",-1)==0 && result?.getInt("err",-2)==-1
        ArtifactMoveGateway.result(context,record,verified,payload?.optString("path").orEmpty())
    }
}
