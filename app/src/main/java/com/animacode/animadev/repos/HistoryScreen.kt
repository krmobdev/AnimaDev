package com.animacode.animadev.repos

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.animacode.animadev.projects.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable fun HistoryScreen(context:Context,project:LocalProject,onOpen:(String)->Unit) {
    val store=remember {RepoStore(context)};val gateway=remember {RepoGateway(context)};val cache=remember {JournalCache(context)}
    val record=rememberRepoRecord(context);var jobs by remember {mutableStateOf(emptyList<JSONObject>())};var filter by rememberSaveable {mutableStateOf("ALL")}
    var refreshing by remember {mutableStateOf(true)};var message by remember {mutableStateOf("")}
    DisposableEffect(Unit) {onDispose {cache.close()} }
    LaunchedEffect(project.id,record.optLong("journal_generation"),record.optString("state"),record.optString("job_id")) {
        jobs=withContext(Dispatchers.IO) {(listOfNotNull(record.optJSONObject("job"),record.optJSONObject("test_job"),record.optJSONObject("build_job"))+cache.jobs()).filter {it.optString("job_id").matches(Regex("[a-f0-9]{32}")) && it.optString("project_id")==project.id}.distinctBy {it.optString("job_id")}.sortedByDescending {it.optDouble("created_at")} }
    }
    LaunchedEffect(refreshing) {
        if(!refreshing)return@LaunchedEffect
        if(record.optString("project_id","animaquiz")!=project.id){message="Для получения истории сначала проверьте Git этого проекта.";refreshing=false;return@LaunchedEffect}
        var offset=0L
        while(true) {
            while(store.read().optBoolean("pending")){delay(500);store.expire()}
            gateway.journal("HISTORY",record.optString("job_id").ifEmpty {"00000000000000000000000000000000"},record.optString("action").ifEmpty {"STATUS"},offset,"ALL")
            if(!store.read().optBoolean("pending")){message="Сохранённая история. Для обновления нужен актуальный помощник.";break}
            while(store.read().optBoolean("pending")){delay(500);store.expire()}
            val done=store.read()
            if(done.optString("journal_message")!="Журнал получен"){message="Связь не подтверждена. Сохранённая история доступна.";break}
            if(!done.optBoolean("journal_more")){message="";break}
            val next=done.optLong("history_next");if(next<=offset)break;offset=next
        }
        refreshing=false
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Text("Задания "+project.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);IconButton(onClick={refreshing=true},enabled=!refreshing){Icon(AppIcons.Refresh,"Обновить историю")}} }
        item {androidx.compose.foundation.layout.FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("ALL" to "Все","TEST" to "Тесты","BUILD_DEBUG" to "Сборки","GIT" to "Git").forEach {(id,label)->FilterChip(filter==id,{filter=id},label={Text(label)})}} }
        if(refreshing)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(message.isNotEmpty())item {Text(message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        val visible=jobs.filter {filter=="ALL" || when(filter) {"TEST"->it.optString("action")=="TEST";"BUILD_DEBUG"->it.optString("action")=="BUILD_DEBUG";else->it.optString("action") !in setOf("TEST","BUILD_DEBUG")}}
        if(visible.isEmpty() && !refreshing)item {Text("Заданий в этом разделе пока нет.",style=MaterialTheme.typography.bodyMedium)}
        items(visible,key={it.optString("job_id")}) {job->
            val state=job.optString("state");val start=job.optDouble("started_at",job.optDouble("created_at",0.0));val end=job.optDouble("ended_at")
            Card(onClick={onOpen(job.optString("job_id"))},colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
                ListItem(headlineContent={Text(when(job.optString("action")) {"TEST"->"Unit-тесты";"BUILD_DEBUG"->"Debug APK";else->"Git · ${job.optString("action")}"},style=MaterialTheme.typography.titleMedium)},supportingContent={Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {Text(MonitorPolicy.label(state,false,false));Text((if(start>0)SimpleDateFormat("dd MMM, HH:mm",Locale.getDefault()).format(Date((start*1000).toLong())) else "Время не сохранено")+if(end>start)" · ${((end-start)/60).toInt()} мин ${((end-start)%60).toInt()} с" else "",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}},leadingContent={Icon(if(state=="SUCCEEDED")AppIcons.Success else if(state in RepoPolicy.terminalStates)AppIcons.Error else AppIcons.History,null,tint=MaterialTheme.colorScheme.primary)},trailingContent={Icon(AppIcons.Chevron,null)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow))
            }
        }
    }
}
