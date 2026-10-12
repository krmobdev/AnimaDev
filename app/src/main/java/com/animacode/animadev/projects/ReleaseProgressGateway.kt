package com.animacode.animadev.projects

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import com.animacode.animadev.termux.TermuxGateway
import org.json.JSONObject

/**
 * Read-only Release status channel. The fixed Termux helper reads only a bounded,
 * allowlisted Gradle-task snapshot. JKS bytes/passwords never travel through this channel.
 */
class ReleaseProgressGateway(private val context: Context) {
    companion object {
        private val inFlight=mutableMapOf<String,Long>()
        @Synchronized private fun reserve(id:String):Boolean {
            if(!id.matches(Regex("[a-f0-9]{32}")))return false
            val now=SystemClock.elapsedRealtime()
            val old=inFlight[id] ?: 0L
            if(now-old<12_000L)return false
            inFlight[id]=now
            return true
        }
        @Synchronized fun complete(id:String) {inFlight.remove(id)}
    }

    fun poll(id:String) {
        if(!reserve(id))return
        val code=runCatching {
            context.assets.open("release/progress.py").bufferedReader().use {it.readText()}
        }.getOrNull()
        if(code==null || code.length>5000) {complete(id);return}
        val callback=PendingIntent.getBroadcast(context,id.hashCode(),
            Intent(context,ReleaseProgressReceiver::class.java).apply {
                action="com.animacode.animadev.RELEASE_PROGRESS"
                data=Uri.parse("animadev://release-progress/$id")
            },PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        try {
            val intent=Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux","com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH",TermuxGateway.PYTHON)
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS",arrayOf("-c",code,id))
                putExtra("com.termux.RUN_COMMAND_WORKDIR",TermuxGateway.HOME)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL","0")
                putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL","AnimaDev · чтение этапов Release")
                putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback)
            }
            if(context.startService(intent)==null) {callback.cancel();complete(id)}
        } catch(_:Exception) {
            callback.cancel()
            complete(id)
        }
    }
}

class ReleaseProgressReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val id=intent.data?.lastPathSegment ?:return
        ReleaseProgressGateway.complete(id)
        if(intent.action!="com.animacode.animadev.RELEASE_PROGRESS"
            || intent.data?.scheme!="animadev" || intent.data?.host!="release-progress"
            || !id.matches(Regex("[a-f0-9]{32}")))return
        try {
            val b=intent.getBundleExtra("result") ?:return
            val text=b.getString("stdout","") ?:return
            if(b.getInt("err")!=-1 || b.getInt("exitCode")!=0 || text.length>16000)return
            val response=JSONObject(text)
            if(!response.optBoolean("ok") || response.optString("request_id")!=id)return
            val phase=response.optString("phase")
            if(phase !in setOf("STARTING","GIT","GRADLE","SIGNING","EXPORTING","DONE","FAILED"))return
            val lines=response.optJSONArray("lines") ?:return
            if(lines.length()>60)return
            val excerpt=buildList {
                for(i in 0 until lines.length()){
                    val line=lines.optString(i,"")
                    if(line.length>165 || '\u0000' in line)return
                    add(line)
                }
            }.joinToString("\n").takeLast(9500)
            val store=ReleaseStore(context)
            val record=store.read()
            if(record.optString("id")!=id || record.optString("state")!="REQUESTED")return
            val updated=response.optLong("updated_at")
            if(updated<record.optLong("progress_updated_at"))return
            val msg=response.optString("last").take(165).ifBlank {"Ожидаем подтверждение Termux"}
            if(record.optString("phase")==phase && record.optString("message")==msg &&
               record.optString("progress_lines")==excerpt)return
            record.put("phase",phase).put("message",msg)
                .put("progress_lines",excerpt).put("progress_updated_at",updated)
            store.write(record)
        } catch(_:Exception) {
            // Failed reads must never change the trusted release result state.
        }
    }
}
