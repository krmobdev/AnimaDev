package com.animacode.animadev.projects

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.animacode.animadev.repos.HighlightedLogChunk
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Same ALL / IMPORTANT split and Gradle task presentation used by Debug journal. */
@Composable
fun ReleaseJournalDialog(context:Context,jobId:String,onDismiss:()->Unit) {
    val cache=remember(context){ReleaseHistoryCache(context)}
    val generation=ReleaseStore.generation.intValue
    val current=remember(generation){ReleaseStore(context).read()}
    val job=if(current.optString("id")==jobId)current else cache.get(jobId) ?: JSONObject()
    val events=remember(jobId,generation){cache.events(jobId)}
    val stageLines=job.optString("progress_lines").lines().filter {it.isNotBlank()}
    var importantOnly by remember(jobId){mutableStateOf(false)}
    val filtered=remember(stageLines,importantOnly) {
        if(!importantOnly)stageLines else stageLines.filter {
            it.contains("FAILED",true) || it.contains("ошибк",true) ||
                it.startsWith("Gradle:") || it.contains("SIGNING",true)
        }
    }
    val listState=rememberLazyListState()
    val formatter=remember {SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale.getDefault())}
    val neon=LocalNeonSkin.current
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(
            modifier=Modifier.fillMaxWidth(.94f).fillMaxHeight(.86f),
            shape=RoundedCornerShape(24.dp),
            color=if(neon)NeonGlass.surface else MaterialTheme.colorScheme.surface,
            border=BorderStroke(1.dp,if(neon)NeonGlass.outline else MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(neon)NeonSection("Журнал Release AAB",AppIcons.History,
                    "Задачи Gradle · диагностические этапы · без секретов подписи")
                else Text("Журнал Release AAB",style=MaterialTheme.typography.titleLarge)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(neon)NeonStatus(job.optString("state").ifBlank {"Ожидание"},
                        job.optString("state")=="SUCCEEDED")
                    else Text(job.optString("state").ifBlank {"Ожидание"})
                }
                Text("SHA: "+job.optString("sha").take(12)+" · ID: "+jobId.take(10),
                    style=MaterialTheme.typography.bodySmall,color=if(neon)NeonGlass.muted else MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=!importantOnly,onClick={importantOnly=false},label={Text("ВСЕ")})
                    FilterChip(selected=importantOnly,onClick={importantOnly=true},label={Text("ВАЖНОЕ")})
                }
                HorizontalDivider(color=if(neon)NeonGlass.outline else MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(
                    modifier=Modifier.weight(1f),
                    state=listState,
                    verticalArrangement=Arrangement.spacedBy(10.dp),
                    contentPadding=PaddingValues(bottom=12.dp)
                ) {
                    if(filtered.isNotEmpty()) item {
                        Text("Задачи и этапы Gradle",style=MaterialTheme.typography.titleSmall)
                        Surface(color=if(neon)NeonGlass.raised else MaterialTheme.colorScheme.surfaceContainer,
                            shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,
                            if(neon)NeonGlass.outline else MaterialTheme.colorScheme.outlineVariant)) {
                            Column(Modifier.padding(12.dp)) {
                                HighlightedLogChunk(filtered.joinToString("\n"))
                            }
                        }
                    }
                    item {Text("Подтверждённые события",style=MaterialTheme.typography.titleSmall)}
                    items(events) { event->
                        Surface(
                            color=if(neon)NeonGlass.raised else MaterialTheme.colorScheme.surfaceContainer,
                            shape=RoundedCornerShape(15.dp),
                            border=BorderStroke(1.dp,if(neon)NeonGlass.outline else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Text(formatter.format(Date(event.optLong("at")))+" · "+
                                    event.optString("phase").ifBlank{event.optString("state")},
                                    color=if(neon)NeonGlass.softGreen else MaterialTheme.colorScheme.primary,
                                    fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.labelMedium)
                                Text(event.optString("message"),style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if(events.isEmpty() && filtered.isEmpty()) item {
                        Text("Этапы ещё не получены. Во время Release приложение запрашивает их у Termux.",
                            style=MaterialTheme.typography.bodyMedium)
                    }
                    job.optString("error_code").takeIf {it.isNotBlank()}?.let {code->
                        item { Text("Ошибка: "+code,color=MaterialTheme.colorScheme.error) }
                    }
                    job.optJSONObject("artifact")?.let {a->
                        item { Text("AAB: "+a.optString("path"),style=MaterialTheme.typography.bodySmall) }
                    }
                    item {
                        Text("Журнал хранит только проверенные названия задач Gradle и события подписи. "+
                            "Сырой вывод скриптов, ключ и пароли не записываются.",
                            style=MaterialTheme.typography.bodySmall,
                            color=if(neon)NeonGlass.muted else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color=if(neon)NeonGlass.outline else MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick=onDismiss,modifier=Modifier.align(Alignment.End)) {Text("Закрыть")}
            }
        }
    }
}
