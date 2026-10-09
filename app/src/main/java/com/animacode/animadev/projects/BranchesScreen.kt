package com.animacode.animadev.projects

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable fun BranchesScreen(context:Context,project:LocalProject,permitted:Boolean) {
    val gateway=remember {CatalogGateway(context)}
    var generation by remember {mutableIntStateOf(0)}
    DisposableEffect(Unit) {val l=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,_->generation++};gateway.prefs.registerOnSharedPreferenceChangeListener(l);onDispose {gateway.prefs.unregisterOnSharedPreferenceChangeListener(l)}}
    val data=remember(generation){JSONObject(gateway.prefs.getString("branches","{}") ?: "{}")}
    val entries=data.optJSONArray("projects")
    val item=entries?.let {if(it.length()==1)it.optJSONObject(0)?.takeIf {it.optString("path")==project.path} else null}
    val rows=item?.optJSONArray("branches")
    val pending=gateway.prefs.getBoolean("pending",false)
    val light=MaterialTheme.colorScheme.surface.luminance()>0.5f
    val gold=if(light)Color(0xFF775A00) else Color(0xFFFFD977)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(project.name,style=MaterialTheme.typography.titleMedium);Text("Только локальные сведения. Сеть и переключение веток не выполняются.",style=MaterialTheme.typography.bodySmall)}
        item {Button(onClick={gateway.read("BRANCHES",project.path)},enabled=permitted && !pending,modifier=Modifier.fillMaxWidth()){Text(if(pending)"Читаем…" else "Обновить список веток")};if(pending)LinearProgressIndicator(Modifier.fillMaxWidth())}
        item {Text(gateway.prefs.getString("message","") ?: "",style=MaterialTheme.typography.bodySmall)}
        if(item!=null) {
            item {Text(when {item.optBoolean("dirty")->"Есть незакоммиченные изменения";item.optString("error").isNotEmpty()->"Метаданные не подтверждены";else->"Рабочее дерево чистое"},style=MaterialTheme.typography.bodyMedium)
                if(item.optString("default_branch").isEmpty())Text("Главная удалённая ветка не определена: нет подтверждённого origin/HEAD. main/master не угадываются.",style=MaterialTheme.typography.bodySmall)
                if(item.optBoolean("branches_limited"))Text("Список ограничен 40 ветками.",color=MaterialTheme.colorScheme.error)}
            if(rows!=null)for(i in 0 until rows.length()) {
                val b=rows.getJSONObject(i)
                item {OutlinedCard {Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(b.optString("name"),style=MaterialTheme.typography.titleMedium,color=if(b.optBoolean("default"))gold else MaterialTheme.colorScheme.onSurface)
                    Text(listOfNotNull(if(b.optBoolean("default"))"Главная удалённая" else null,if(b.optBoolean("active"))"Активная" else null,if(b.optBoolean("local"))"Локальная" else "origin").joinToString(" · "),style=MaterialTheme.typography.labelMedium,color=if(b.optBoolean("active"))MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(b.optString("commit").take(8)+" · "+b.optString("date"),style=MaterialTheme.typography.bodySmall)
                } } }
            }
        } else item {Text("Нажмите «Обновить список веток». Ранее сохранённые результаты другого проекта здесь не показываются.",style=MaterialTheme.typography.bodyMedium)}
    }
}
