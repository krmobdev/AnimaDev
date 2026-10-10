package com.animacode.animadev.repos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.animacode.animadev.MainActivity
import com.animacode.animadev.projects.ProjectStore
import org.json.JSONObject

/** An Android notification reflects confirmed Termux state, never imaginary Gradle percent. */
object JobNotifications {
    private const val CHANNEL="jobs_pulse_v1"
    fun publish(context:Context,record:JSONObject) {
        if(Build.VERSION.SDK_INT>=33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val jid=record.optString("job_id")
        if(!jid.matches(Regex("[a-f0-9]{32}")))return
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"AnimaDev · сборки",
            NotificationManager.IMPORTANCE_LOW).apply {
                description="Реальные этапы Gradle, тестов и локальных сборок"
                enableVibration(false)
            })
        val action=record.optString("action")
        val state=record.optString("state")
        val running=state in RepoPolicy.activeStates
        val job=record.optJSONObject("job")
        val project=ProjectStore(context).all().find {
            it.id==record.optString("project_id","animaquiz")
        }?.name ?: "Проект"
        val operation=when(action) {
            "BUILD_DEBUG"->"Debug APK"
            "TEST"->"Unit-тесты"
            "CHECK"->"GitHub SHA"
            "SYNC"->"Синхронизация"
            else->"Git"
        }
        val stage=job?.optString("stage").orEmpty().ifBlank {
            when(state) {
                "SUCCEEDED"->"Готово · файл и отчёт сохранены"
                "INTERRUPTED"->"Остановлено через Ctrl+C"
                "FAILED"->"Ошибка · смотрите журнал"
                else->"Ожидаем этап от Termux…"
            }
        }
        val start=((job?.optDouble("started_at",0.0) ?: 0.0)*1000).toLong()
        val tap=PendingIntent.getActivity(context,jid.hashCode(),
            Intent(context,MainActivity::class.java).putExtra("journal_job",jid)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=NotificationCompat.Builder(context,CHANNEL)
            .setSmallIcon(if(running)android.R.drawable.stat_sys_download
                else android.R.drawable.stat_sys_download_done)
            .setColor(Color.rgb(82,231,144))
            .setContentTitle("$project  ·  $operation")
            .setContentText(when(state) {
                "RUNNING"->stage
                "SUCCEEDED"->"Готово ✓"
                "INTERRUPTED"->"Остановлено пользователем"
                "FAILED"->"Ошибка · открыть журнал"
                "UNKNOWN"->"Результат требует проверки"
                else->"Подготовка задания…"
            })
            .setSubText(when(state) {
                "RUNNING"->"Сборка выполняется"
                "SUCCEEDED"->"Успешно"
                "INTERRUPTED"->"Отменено"
                else->"AnimaDev · локально"
            })
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$project · $operation\n$stage\n"+
                (if(job?.optBoolean("cancel_requested")==true)"Отмена через Ctrl+C запрошена."
                 else "Процесс Termux работает независимо от окна приложения.")))
            .setContentIntent(tap).setOnlyAlertOnce(true)
            .setOngoing(running).setAutoCancel(!running)
            .setProgress(0,0,running)
        if(running && start>0)builder.setWhen(start).setUsesChronometer(true)
        builder.addAction(android.R.drawable.ic_menu_view,"Открыть",tap)
        if(running && state in setOf("REQUESTED","QUEUED","RUNNING") &&
            job?.optBoolean("cancel_requested")!=true &&
            action in setOf("TEST","BUILD_DEBUG")) {
            val cancel=PendingIntent.getBroadcast(context,jid.hashCode(),
                Intent(context,RepoCancelReceiver::class.java).apply {
                    this.action="com.animacode.animadev.REPO_CANCEL"
                    data=Uri.parse("animadev://cancel/$jid")
                },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_media_pause,"Стоп · Ctrl+C",cancel)
        }
        manager.notify(jid.hashCode(),builder.build())
    }
}

class RepoCancelReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val jid=intent.data?.lastPathSegment ?:return
        if(intent.action!="com.animacode.animadev.REPO_CANCEL" ||
            intent.data?.scheme!="animadev" || intent.data?.host!="cancel" ||
            !jid.matches(Regex("[a-f0-9]{32}")))return
        val record=RepoStore(context).read()
        if(record.optString("job_id")==jid &&
            record.optString("action") in setOf("BUILD_DEBUG","TEST") &&
            record.optString("state") in setOf("REQUESTED","QUEUED","RUNNING") &&
            !record.optBoolean("pending")) RepoGateway(context).cancel()
    }
}
