package com.animacode.animadev.repos

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject
import com.animacode.animadev.projects.ProjectStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JobMonitor(context: Context, record: JSONObject, permitted: Boolean, openJournal: (String)->Unit) {
    val settings=remember { context.getSharedPreferences("monitor_settings",Context.MODE_PRIVATE) }
    val store=remember { RepoStore(context) }; val gateway=remember { RepoGateway(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true) { delay(1000);now=System.currentTimeMillis() } }
    val jid=record.optString("job_id"); if(jid.isEmpty())return
    val projectId=record.optString("project_id","animaquiz")
    val projectName=ProjectStore(context).all().find {it.id==projectId}?.name ?: projectId
    val job=record.optJSONObject("job")
    val state=record.optString("state")
    val start=((job?.optDouble("started_at",job.optDouble("created_at",0.0)) ?: record.optDouble("sent_at",0.0)/1000)*1000).toLong()
    val progress=((job?.optDouble("last_progress_at",0.0) ?: 0.0)*1000).toLong()
    val ended=((job?.optDouble("ended_at",0.0) ?: 0.0)*1000).toLong()
    val elapsed=(if(ended>0)ended else now)-start
    val minutes=MonitorPolicy.minutes(settings.getInt("warning_minutes",10))
    val until=settings.getLong("snooze_$jid",0)
    var snoozeGeneration by remember { mutableIntStateOf(0) }
    val warning=remember(now,state,progress,until,snoozeGeneration) { MonitorPolicy.warning(now,progress,start,until,minutes,state) }
    val quiet=progress>0 && now-progress>60_000L
    val manual=MonitorPolicy.manualVisible(state,record.optLong("last_confirmed",record.optLong("sent_at")),now)
    var details by remember { mutableStateOf(false) }
    var confirmCancel by remember(jid) { mutableStateOf(false) }
    val terminal=state in RepoPolicy.terminalStates
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Задание · $projectName · "+when(record.optString("action")) {
                "TEST"->"Unit-тесты"
                "BUILD_DEBUG"->"Debug APK"
                else->"Git / "+record.optString("action")
            },style=MaterialTheme.typography.titleMedium)
            Text(MonitorPolicy.label(state,quiet,warning))
            if(details)Text("Job ID: $jid",style=MaterialTheme.typography.bodySmall)
            if(details)Text("Начало: ${if(start>0)SimpleDateFormat("dd.MM HH:mm:ss",Locale.getDefault()).format(Date(start)) else "не подтверждено"}")
            Text("Прошло: ${maxOf(0,elapsed)/60_000} мин ${maxOf(0,elapsed)/1000%60} с")
            if(job?.optBoolean("cancel_requested")==true)
                Text("Отмена запрошена · ожидаем остановку Gradle",color=MaterialTheme.colorScheme.error,
                    style=MaterialTheme.typography.titleSmall)
            if(!terminal)Text("Последний прогресс: ${if(progress>0)"${maxOf(0,now-progress)/1000} с назад" else "пока не подтверждён"}",style=MaterialTheme.typography.bodySmall)
            if(!terminal)Text(job?.optString("stage")?.ifEmpty { "Ожидаем подтверждение этапа" } ?: "Ожидаем подтверждение этапа",maxLines=2)
            val alive=job?.let { if(it.isNull("process_alive") || !it.has("process_alive"))null else it.optBoolean("process_alive") }
            if(details) {
                Text("Процесс: "+when(alive) {true->"работает";false->"не обнаружен";else->"статус неизвестен"},
                    style=MaterialTheme.typography.bodySmall)
                val cpu=job?.optDouble("cpu_percent",Double.NaN) ?: Double.NaN
                Text("CPU процесса Gradle: "+if(cpu.isFinite() && cpu>=0)"%.1f%%".format(java.util.Locale.getDefault(),cpu) else "нет доступа к счётчикам",
                    style=MaterialTheme.typography.bodySmall)
                val ram=job?.optInt("memory_available_mib",-1) ?: -1
                Text("Свободно доступной RAM устройства: "+if(ram>=0)"$ram МиБ" else "показатель недоступен",
                    style=MaterialTheme.typography.bodySmall)
                val rss=job?.optDouble("process_rss_mib",Double.NaN) ?: Double.NaN
                Text("RAM процесса (RSS): "+if(rss.isFinite() && rss>=0)"%.1f МиБ".format(java.util.Locale.getDefault(),rss) else "показатель недоступен",
                    style=MaterialTheme.typography.bodySmall)
                Text("CPU — процент одного логического ядра; многопоточный Gradle может превышать 100%. Показатели из Termux, не из прогноза.",
                    style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(state in setOf("REQUESTED","QUEUED","RUNNING")) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(job!=null && !job.isNull("exit_code"))Text("Exit code: ${job.optInt("exit_code")}")
            Text(record.optString("message"),style=MaterialTheme.typography.bodySmall)
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                if(record.optString("action") in setOf("TEST","TEST_DEVICE","BUILD_DEBUG") &&
                    state in setOf("REQUESTED","QUEUED","RUNNING") &&
                    job?.optBoolean("cancel_requested")!=true) {
                    OutlinedButton(onClick={confirmCancel=true},
                        enabled=permitted && !record.optBoolean("pending"),modifier=Modifier.fillMaxWidth(),
                        colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)) {
                        Text("Остановить · Ctrl+C")
                    }
                }
                OutlinedButton(onClick={openJournal(jid)},modifier=Modifier.fillMaxWidth()) { Text("Открыть журнал") }
                if(manual) OutlinedButton(onClick={gateway.query(true)},enabled=permitted && !record.optBoolean("pending")) {
                    Text(if(record.optBoolean("pending") && record.optBoolean("manual_query"))"Проверяем…" else "Проверить состояние задания")
                }
            }
            OutlinedButton(onClick={details=!details},modifier=Modifier.fillMaxWidth()){Text(if(details)"Скрыть сведения" else "Сведения о задании")}
            if(record.optString("query_result").isNotEmpty())Text(record.optString("query_result"),style=MaterialTheme.typography.bodySmall)
        }
    }
    if(confirmCancel)AlertDialog(
        onDismissRequest={confirmCancel=false},
        title={Text("Остановить задачу?")},
        text={Text("Отправить Ctrl+C процессу Gradle в Termux? Отмена не удаляет исходники и не изменяет Git. Пока завершение не подтверждено, другая сборка заблокирована.")},
        confirmButton={TextButton(onClick={confirmCancel=false;gateway.cancel()}){Text("Остановить")}},
        dismissButton={TextButton(onClick={confirmCancel=false}){Text("Продолжить")}}
    )
    if(warning) AlertDialog(onDismissRequest={settings.edit().putLong("snooze_$jid",MonitorPolicy.snooze(now,minutes)).apply();snoozeGeneration++},title={Text("Давно нет подтверждённого прогресса")},
        text={Text("Задание выполняется ${maxOf(0,elapsed)/60_000} минут. Последняя подтверждённая активность — ${maxOf(0,now-maxOf(progress,start))/60_000} минут назад. Процесс может продолжать вычисления.")},
        confirmButton={TextButton(onClick={settings.edit().putLong("snooze_$jid",MonitorPolicy.snooze(now,minutes)).apply();snoozeGeneration++}) { Text("Продолжить ждать") }},
        dismissButton={TextButton(onClick={settings.edit().putLong("snooze_$jid",MonitorPolicy.snooze(now,minutes)).apply();snoozeGeneration++;openJournal(jid)}) { Text("Открыть журнал") }})
}

@Composable
fun MonitorSettings(context: Context,embedded:Boolean=false,showNotifications:Boolean=true) {
    val prefs=remember { context.getSharedPreferences("monitor_settings",Context.MODE_PRIVATE) }
    var minutes by remember { mutableIntStateOf(MonitorPolicy.minutes(prefs.getInt("warning_minutes",10))) }
    Column(if(embedded)Modifier.fillMaxWidth().padding(16.dp) else Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(if(embedded)"Ожидание заданий" else "Настройки ожидания",style=MaterialTheme.typography.titleMedium)
        Text("Предупреждать при отсутствии признаков прогресса")
        MonitorPolicy.thresholds.forEach { value ->
            Row { RadioButton(selected=minutes==value,onClick={minutes=value;prefs.edit().putInt("warning_minutes",value).apply()});Text("$value минут",Modifier.padding(top=12.dp)) }
        }
        Text("Это предупреждение, а не остановка. Защитный предел TEST — 90 минут.",style=MaterialTheme.typography.bodySmall)
        if(showNotifications)NotificationSettings(context)
    }
}

@Composable fun NotificationSettings(context:Context) {
    var enabled by remember { mutableStateOf(context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()) }
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val listener=androidx.lifecycle.LifecycleEventObserver { _,event -> if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)enabled=context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled() }
        owner.lifecycle.addObserver(listener);onDispose {owner.lifecycle.removeObserver(listener)}
    }
    val permission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {enabled=context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()}
    Column {
        ListItem(headlineContent={Text("Уведомления заданий")},supportingContent={Text(if(enabled)"Разрешены · изменить в Android" else "Отключены · разрешить в Android")},trailingContent={Icon(com.animacode.animadev.projects.AppIcons.Chevron,null)},modifier=Modifier.fillMaxWidth().then(Modifier.clickable {
            if(context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))
        }))
        Text("Отказ не блокирует задания. Уведомления не гарантируют сохранение процесса Termux.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=16.dp,vertical=8.dp))
    }
}
