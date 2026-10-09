package com.animacode.animadev.projects

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.animacode.animadev.repos.*
import com.animacode.animadev.termux.TermuxGateway

@Composable fun BuildScreen(context:Context,project:LocalProject,permitted:Boolean,termuxVersion:String?,
                           onPermissionUpdated:()->Unit,onJournal:(String)->Unit) {
    val record=rememberRepoRecord(context)
    val gateway=remember {RepoGateway(context)}
    val snapshot=record.optJSONObject("snapshot")
    val blocked=RepoPolicy.blocked(record.optString("job_id"),record.optString("state"))
    val ready=HelperPolicy.mayRun(permitted,termuxVersion!=null,record.optBoolean("ready"),record.optBoolean("pending"),blocked)
    val canDebug=project.supported && ready && gateway.mayBuild(record)
    val signing=remember {SigningStore(context)}
    val path=signing.path(project.id)
    val grant=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){onPermissionUpdated()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(project.name,style=MaterialTheme.typography.titleMedium)
            Text("Ветка: "+(snapshot?.optString("branch") ?: "не проверена")+" · "+(snapshot?.optString("local_commit")?.take(12) ?: "SHA неизвестен"),
                style=MaterialTheme.typography.bodySmall)
        }
        if(!permitted)item {Button(onClick={grant.launch(TermuxGateway.PERMISSION)}){Text("Разрешить Termux")}}
        if(!record.optBoolean("ready"))item {Card {Column(Modifier.padding(16.dp)) {
            Text("Локальный помощник необходимо обновить")
            Button(onClick={gateway.install()},enabled=permitted && termuxVersion!=null && !blocked && !record.optBoolean("pending")){Text("Обновить помощник")}
        }}}
        item {Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Debug APK",style=MaterialTheme.typography.titleMedium)
                Text("Офлайн Gradle; перед запуском повторно сверяем GitHub SHA",style=MaterialTheme.typography.bodySmall)
                Button(onClick={gateway.start("BUILD_DEBUG",project.id,project.path)},enabled=canDebug,
                    modifier=Modifier.fillMaxWidth()){Icon(AppIcons.Build,null);Spacer(Modifier.width(8.dp));Text("Собрать Debug APK")}
                if(!canDebug)Text(if(blocked)"Завершите или уточните предыдущее задание."
                    else "Сначала выполните в разделе Git проверку GitHub. Нужны чистое дерево и совпадающие SHA.",
                    style=MaterialTheme.typography.bodySmall)
            }
        }}
        item {Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Release AAB",style=MaterialTheme.typography.titleMedium)
                Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth()){Text("Собрать Release AAB")}
                Text(if(path.isBlank())"Укажите путь к ключу подписи в настройках."
                     else "Защищённая передача пароля пока не готова. Выпуск AAB заблокирован.",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }}
        val job=record.optJSONObject("job")
        if(job?.optString("action")=="BUILD_DEBUG")item {
            Text("Задание: "+MonitorPolicy.label(job.optString("state"),false,false),style=MaterialTheme.typography.titleSmall)
            Text(job.optString("message"),style=MaterialTheme.typography.bodySmall)
            job.optJSONObject("artifact")?.let {a->Card {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("Debug APK в Загрузках",style=MaterialTheme.typography.titleMedium)
                Text(a.optString("path"),style=MaterialTheme.typography.bodySmall)
                Text("SHA-256: "+a.optString("sha256"),style=MaterialTheme.typography.bodySmall)
                Text("Размер: "+a.optLong("size")+" байт · Git "+a.optString("commit").take(12),style=MaterialTheme.typography.bodySmall)
            }}}
            TextButton(onClick={onJournal(job.optString("job_id"))}){Text("Журнал задания")}
        }
    }
}
