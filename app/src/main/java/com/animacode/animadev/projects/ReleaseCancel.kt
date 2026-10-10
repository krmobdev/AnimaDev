package com.animacode.animadev.projects

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.animacode.animadev.termux.TermuxGateway
import org.json.JSONObject

/** An exact request-ID only; no key, password or executable from a Git repository. */
object ReleaseCancel {
    fun request(context:Context) {
        val store=ReleaseStore(context)
        val record=store.read()
        val id=record.optString("id")
        if(record.optString("state")!="REQUESTED" ||
            !id.matches(Regex("[a-f0-9]{32}")) || record.optBoolean("cancel_requested"))return
        if(record.optString("phase")=="ENROLLING") {
            store.failedWithoutTransfer(id,"Подготовка остановлена до передачи JKS.")
            return
        }
        val source=runCatching {
            context.assets.open("release/release_cancel.py").bufferedReader().use {it.readText()}
        }.getOrNull() ?:return
        val target=Intent(context,ReleaseCancelReceiver::class.java).apply {
            action="com.animacode.animadev.RELEASE_CANCEL"
            data=Uri.parse("animadev://release-cancel/$id")
        }
        val callback=PendingIntent.getBroadcast(context,id.hashCode(),target,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        val command=Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux","com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH",TermuxGateway.PYTHON)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS",arrayOf("-c",source,id))
            putExtra("com.termux.RUN_COMMAND_WORKDIR",TermuxGateway.HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL","AnimaDev · остановить локальную Release сборку")
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback)
        }
        try {
            if(context.startService(command)==null)error("No service")
            store.write(record.put("cancel_requested",true)
                .put("message","Отмена отправлена Termux. Ожидаем подтверждения остановки Gradle."))
        } catch(_:Exception) {
            callback.cancel()
            store.write(record.put("message","Не удалось отправить отмену; состояние сборки не изменено."))
        }
    }
}

class ReleaseCancelReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val id=intent.data?.lastPathSegment ?:return
        if(intent.action!="com.animacode.animadev.RELEASE_CANCEL" ||
            intent.data?.scheme!="animadev" || intent.data?.host!="release-cancel" ||
            !id.matches(Regex("[a-f0-9]{32}")))return
        val store=ReleaseStore(context)
        val record=store.read()
        if(record.optString("id")!=id || record.optString("state")!="REQUESTED")return
        val accepted=try {
            val raw=intent.getBundleExtra("result") ?:error("No result")
            val text=raw.getString("stdout","") ?: ""
            check(text.length<1200 && raw.getInt("exitCode")==0 && raw.getInt("err")==-1)
            val reply=JSONObject(text)
            reply.optString("request_id")==id && reply.optBoolean("ok") &&
                reply.optString("code")=="CANCEL_REQUESTED"
        } catch(_:Exception) {false}
        if(!accepted)
            store.write(record.put("cancel_requested",false).put("message",
                "Termux не подтвердил отмену. Задание может продолжаться; проверьте статус."))
        else store.write(record.put("message","Ctrl+C запрошен. Ожидаем завершение Release AAB в Termux."))
    }
}
