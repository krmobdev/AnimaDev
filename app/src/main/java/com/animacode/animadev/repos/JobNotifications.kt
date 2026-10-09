package com.animacode.animadev.repos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.animacode.animadev.MainActivity
import org.json.JSONObject

/** Observed updates only. Does not keep Termux/JVM alive and requires no Android service. */
object JobNotifications {
    fun publish(context: Context, record: JSONObject) {
        if(Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val jid=record.optString("job_id"); if(jid.isEmpty()) return
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("jobs","Задания AnimaDev",NotificationManager.IMPORTANCE_LOW))
        val intent=Intent(context,MainActivity::class.java).putExtra("journal_job",jid).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val click=PendingIntent.getActivity(context,jid.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val job=record.optJSONObject("job")
        val start=((job?.optDouble("started_at",0.0) ?: 0.0)*1000).toLong()
        val running=RepoPolicy.blocked(jid,record.optString("state"))
        val end=((job?.optDouble("ended_at",0.0) ?: 0.0)*1000).toLong()
        val duration=if(start>0) (if(end>0)end else System.currentTimeMillis())-start else 0
        val status=MonitorPolicy.label(record.optString("state"),false,false)
        manager.notify(jid.hashCode(),NotificationCompat.Builder(context,"jobs")
            .setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle((com.animacode.animadev.projects.ProjectStore(context).all().find { it.id==record.optString("project_id","animaquiz") }?.name ?: "AnimaDev")+" · "+record.optString("action"))
            .setContentText("$status · ${duration/60_000} мин ${duration/1000%60} с")
            .setContentIntent(click).setOnlyAlertOnce(true).setOngoing(running).setAutoCancel(!running).build())
    }
}
