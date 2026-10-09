package com.animacode.animadev.projects

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.animacode.animadev.repos.*
import com.animacode.animadev.termux.TermuxGateway
import org.json.JSONObject

@Composable fun SingleChoiceRow(label:String,selected:Boolean,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).heightIn(min=48.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { RadioButton(selected=selected,onClick=null);Text(label,Modifier.padding(start=8.dp)) }
}
@Composable fun rememberRepoRecord(context:Context):JSONObject {
    val store=remember { RepoStore(context) };var record by remember { mutableStateOf(store.read()) }
    DisposableEffect(Unit) { val l=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_->record=store.read() };store.prefs.registerOnSharedPreferenceChangeListener(l);onDispose { store.prefs.unregisterOnSharedPreferenceChangeListener(l) } }
    return record
}
@Composable fun rememberCatalogRecord(context:Context):JSONObject {
    val gateway=remember { CatalogGateway(context) };var generation by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { val l=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_->generation++ };gateway.prefs.registerOnSharedPreferenceChangeListener(l);onDispose { gateway.prefs.unregisterOnSharedPreferenceChangeListener(l) } }
    return remember(generation) { JSONObject().put("pending",gateway.prefs.getBoolean("pending",false)).put("message",gateway.prefs.getString("message","")).put("catalog",JSONObject(gateway.prefs.getString("catalog","{}") ?: "{}")).put("environment",JSONObject(gateway.prefs.getString("environment","{}") ?: "{}")) }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProjectsScreen(context:Context,permitted:Boolean,onOpen:(LocalProject)->Unit) {
    val store=remember { ProjectStore(context) };val gateway=remember { CatalogGateway(context) }
    var generation by remember { mutableIntStateOf(0) };var add by rememberSaveable { mutableStateOf(false) };var remove by remember { mutableStateOf<LocalProject?>(null) }
    DisposableEffect(Unit) { val l=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_->generation++ };store.prefs.registerOnSharedPreferenceChangeListener(l);onDispose { store.prefs.unregisterOnSharedPreferenceChangeListener(l) } }
    val projects=remember(generation) { store.all() };val info=rememberCatalogRecord(context);val record=rememberRepoRecord(context)
    val a=info.getJSONObject("catalog").optJSONArray("projects")
    val found=remember(info.toString(),projects) { buildList { if(a!=null) for(i in 0 until a.length())runCatching { LocalProject.parse(a.getJSONObject(i)) }.getOrNull()?.takeIf { p->projects.none { it.path==p.path } }?.let { add(it) } } }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("Локальные репозитории",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
            FilledTonalButton(onClick={add=true;if(permitted)gateway.read("CATALOG")}) { Text("+ Добавить") }
        } }
        if(projects.isEmpty())item { Text("Добавьте существующий репозиторий Termux. Файлы не копируются.") }
        items(projects,key={it.id}) { project ->
            val snap=if(project.supported)record.optJSONObject("snapshot") else null
            val branch=snap?.optString("branch")?.ifEmpty { project.branch } ?: project.branch
            val dirty=snap?.optBoolean("dirty") ?: project.dirty
            val active=project.supported && RepoPolicy.blocked(record.optString("job_id"),record.optString("state"))
            Card(onClick={onOpen(project)}) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Icon(BetaIcons.Folder,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(12.dp));Text(project.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick={remove=project}){Text("Убрать")} }
                    Text(branch.ifEmpty { "Ветка ещё не проверена" },style=MaterialTheme.typography.bodyMedium)
                    Text(if(active)"Задание: ${MonitorPolicy.label(record.optString("state"),false,false)}" else when(dirty) {true->"Есть локальные изменения";false->"Рабочее дерево чистое";else->"Состояние не проверено"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text("GitHub → Termux · изменения никогда не отправляются на GitHub",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if(add)ModalBottomSheet(onDismissRequest={add=false}) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=560.dp),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text("Добавить проект",style=MaterialTheme.typography.titleLarge);Text("Только доступные локальные Git-репозитории",style=MaterialTheme.typography.bodySmall) }
            item { OutlinedButton(onClick={gateway.read("CATALOG")},enabled=permitted && !info.optBoolean("pending")) { Text(if(info.optBoolean("pending"))"Поиск…" else "Найти репозитории") };Text(if(permitted)info.optString("message") else "Разрешите доступ к Termux в разделе Termux.") }
            items(found,key={it.id}) { p -> ListItem(headlineContent={Text(p.name)},supportingContent={Text("${p.branch.ifEmpty { "Не проверено" }}\n${p.path.removePrefix(ProjectPolicy.HOME+"/")}")},trailingContent={TextButton(onClick={store.add(p);add=false}){Text("Добавить")}}) }
            if(found.isEmpty() && !info.optBoolean("pending"))item { Text("Новых доступных репозиториев в последнем обзоре нет.") }
            if(info.getJSONObject("catalog").optBoolean("limited"))item { Text("Обзор ограничен по времени/числу проектов. Защищённые каталоги не обходятся.") }
        }
    }
    remove?.let { p -> AlertDialog(onDismissRequest={remove=null},title={Text("Убрать ${p.name} из списка?")},text={Text("Репозиторий, файлы и история останутся в Termux.")},confirmButton={TextButton(onClick={store.remove(p.id);remove=null}){Text("Убрать")}},dismissButton={TextButton(onClick={remove=null}){Text("Отмена")}}) }
}
@Composable fun ProjectWorkspace(context:Context,project:LocalProject,onTool:(String)->Unit) {
    val record=rememberRepoRecord(context);val snap=if(project.supported)record.optJSONObject("snapshot") else null
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text(snap?.optString("branch")?.ifEmpty { project.branch } ?: project.branch.ifEmpty { "Ветка не проверена" },style=MaterialTheme.typography.titleMedium);Text(project.remote.ifEmpty { if(project.supported)"github.com/krmobdev/quizapp" else "Origin не проверен" },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        item { BoxWithConstraints { val columns=if(maxWidth>=600.dp)4 else 2
            val tools=listOf(Triple("git","Git",BetaIcons.Git),Triple("tests","Тесты",BetaIcons.Tests),Triple("build","Сборка",BetaIcons.Build),Triple("history","История",BetaIcons.History))
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) { tools.chunked(columns).forEach { row->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { row.forEach { (id,title,icon)->Card(onClick={onTool(id)},modifier=Modifier.weight(1f)) { Column(Modifier.fillMaxWidth().heightIn(min=104.dp).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) { Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Text(title,style=MaterialTheme.typography.titleMedium);if(id=="build" || (!project.supported && id!="history"))Text("Скоро",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } } } } } }
        } }
        if(project.supported && RepoPolicy.blocked(record.optString("job_id"),record.optString("state")))item { Card(onClick={onTool(if(record.optString("action")=="TEST")"tests" else "git")}) { Column(Modifier.padding(16.dp)) { Text("Текущее задание · ${record.optString("action")}",style=MaterialTheme.typography.titleMedium);Text(MonitorPolicy.label(record.optString("state"),false,false));LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=12.dp)) } } }
        if(!project.supported)item { Text("Проект добавлен для просмотра. Git-синхронизация и тесты пока не подключены к безопасному профилю.",style=MaterialTheme.typography.bodyMedium) }
    }
}
@Composable fun UnavailableTool(title:String,reason:String) { Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { Icon(BetaIcons.Build,null,tint=MaterialTheme.colorScheme.onSurfaceVariant);Text(title,style=MaterialTheme.typography.titleLarge);Text(reason);Text("Действие недоступно в 0.4.0 Beta",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
