package com.animacode.animadev.projects

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
import org.json.JSONObject

/** Notification content is non-secret: project, stage, clock and artifact status only. */
object ReleaseNotifications {
    private const val CHANNEL="release_pulse_v1"
    fun publish(context:Context,record:JSONObject) {
        if(Build.VERSION.SDK_INT>=33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val id=record.optString("id")
        if(!id.matches(Regex("[a-f0-9]{32}")))return
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"AnimaDev · Release AAB",
            NotificationManager.IMPORTANCE_LOW).apply {
                description="Локальные подписанные сборки и их подтверждённый результат"
                enableVibration(false)
            })
        val status=record.optString("state")
        val busy=status=="REQUESTED" || status=="UNKNOWN"
        val project=ProjectStore(context).all().find {
            it.id==record.optString("project")
        }?.name ?: "Android-проект"
        val stage=when {
            status=="SUCCEEDED"->"AAB подписан и сохранён ✓"
            status=="CANCELLED"->"Остановлено через Ctrl+C"
            status=="FAILED"->"Ошибка · требуется проверка"
            status=="UNKNOWN"->"Результат пока не подтверждён"
            record.optString("phase")=="EXPORTING"->"AAB подписан · переносим файл"
            record.optBoolean("cancel_requested")->"Отмена отправлена · ожидаем Termux"
            record.optString("phase")=="ENROLLING"->"Создаём одноразовый ключ шифрования"
            record.optString("phase")=="DISPATCHING"->"Шифрованное задание передано"
            else->"Локальная сборка в Termux"
        }
        val open=PendingIntent.getActivity(context,id.hashCode(),
            Intent(context,MainActivity::class.java).putExtra("open_release",true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,CHANNEL)
            .setSmallIcon(if(busy)android.R.drawable.stat_sys_download else android.R.drawable.stat_sys_download_done)
            .setColor(Color.rgb(123,255,174))
            .setContentTitle("$project  ·  Release AAB")
            .setContentText(stage)
            .setSubText("AnimaDev · локально")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$stage\n"+record.optString("message").take(180)))
            .setContentIntent(open).setOnlyAlertOnce(true)
            .setOngoing(busy).setAutoCancel(!busy).setProgress(0,0,busy)
        val start=record.optLong("started_at")
        if(start>0 && busy)notification.setWhen(start).setUsesChronometer(true)
        val artifact=record.optJSONObject("artifact")
        val artifactOpen=if(status=="SUCCEEDED" && artifact!=null &&
            ArtifactPolicy.valid(artifact.optString("path"),artifact.optString("sha256"),artifact.optLong("size")))
            PendingIntent.getBroadcast(context,id.hashCode(),ArtifactGateway.intent(context,artifact),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) else null
        notification.addAction(android.R.drawable.ic_menu_view,"Открыть",artifactOpen ?: open)
        if(status=="REQUESTED" && !record.optBoolean("cancel_requested")) {
            val action=PendingIntent.getBroadcast(context,id.hashCode(),
                Intent(context,ReleaseCancelActionReceiver::class.java).apply {
                    this.action="com.animacode.animadev.RELEASE_CANCEL_ACTION"
                    data=Uri.parse("animadev://release-cancel-action/$id")
                },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notification.addAction(android.R.drawable.ic_media_pause,"Стоп · Ctrl+C",action)
        }
        manager.notify(id.hashCode(),notification.build())
    }
}

class ReleaseCancelActionReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val id=intent.data?.lastPathSegment ?:return
        if(intent.action!="com.animacode.animadev.RELEASE_CANCEL_ACTION" ||
            intent.data?.scheme!="animadev" || intent.data?.host!="release-cancel-action" ||
            !id.matches(Regex("[a-f0-9]{32}")))return
        val record=ReleaseStore(context).read()
        if(record.optString("id")==id && record.optString("state")=="REQUESTED")
            ReleaseCancel.request(context)
    }
}
