package com.animacode.animadev.projects

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local, sanitized lifecycle events — never raw signing subprocess output. */
@Composable fun ReleaseJournalDialog(context:Context,jobId:String,onDismiss:()->Unit) {
    val cache=remember(context){ReleaseHistoryCache(context)}
    val generation=ReleaseStore.generation.intValue
    val current=remember(context,generation){ReleaseStore(context).read()}
    val job=if(current.optString("id")==jobId)current else cache.get(jobId) ?: JSONObject()
    val events=remember(jobId,generation){cache.events(jobId)}
    val formatter=remember {SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale.getDefault())}
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("Журнал Release AAB")},
        text={
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=470.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                item {
                    Text("ID: "+jobId,style=MaterialTheme.typography.bodySmall)
                    Text("Git SHA: "+job.optString("sha").ifBlank {"Не подтверждён"},
                        style=MaterialTheme.typography.bodySmall)
                    Text("Статус: "+job.optString("state").ifBlank {"Неизвестен"})
                    job.optString("error_code").takeIf {it.isNotBlank()}?.let {
                        Text("Код ошибки: "+it,color=MaterialTheme.colorScheme.error)
                    }
                    job.optJSONObject("artifact")?.let {a->
                        Text("Файл: "+a.optString("path"),style=MaterialTheme.typography.bodySmall)
                        Text("SHA-256: "+a.optString("sha256"),style=MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(Modifier.padding(vertical=8.dp))
                    Text("События сборки",style=MaterialTheme.typography.titleSmall)
                }
                items(events) {event->
                    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text(formatter.format(Date(event.optLong("at")))+" · "+
                            event.optString("phase").ifBlank {event.optString("state")},
                            style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                        Text(event.optString("message"),style=MaterialTheme.typography.bodySmall)
                    }
                }
                if(events.isEmpty())item {
                    Text("Для этой сборки события не сохранились.",
                        style=MaterialTheme.typography.bodySmall)
                }
                item {
                    Text("Журнал содержит подтверждённые этапы и ошибки. Сырой вывод Gradle "+
                        "и данные подписи не сохраняются.",
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton={TextButton(onClick=onDismiss){Text("Закрыть")}}
    )
}
