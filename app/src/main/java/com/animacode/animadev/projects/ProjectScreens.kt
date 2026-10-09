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
    DisposableEffect(Unit) { val l=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_->record=store.read() };store.prefs.registerOnSharedPreferenceChangeListener(l);record=store.read();onDispose { store.prefs.unregisterOnSharedPreferenceChangeListener(l) } }
    return record
}
@Composable fun rememberCatalogRecord(context:Context):JSONObject {
    val gateway=remember { CatalogGateway(context) };var generation by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { val l=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_->generation++ };gateway.prefs.registerOnSharedPreferenceChangeListener(l);onDispose { gateway.prefs.unregisterOnSharedPreferenceChangeListener(l) } }
    return remember(generation) { JSONObject().put("pending",gateway.prefs.getBoolean("pending",false)).put("message",gateway.prefs.getString("message","")).put("catalog",JSONObject(gateway.prefs.getString("catalog","{}") ?: "{}")).put("environment",JSONObject(gateway.prefs.getString("environment","{}") ?: "{}")) }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProjectsScreen(context:Context,permitted:Boolean,onOpen:(LocalProject)->Unit) {
    val haptic=androidx.compose.ui.platform.LocalHapticFeedback.current
    val store=remember { ProjectStore(context) };val gateway=remember { CatalogGateway(context) }
    var generation by remember { mutableIntStateOf(0) };var add by rememberSaveable { mutableStateOf(false) };var remove by remember { mutableStateOf<LocalProject?>(null) }
    var explicitPath by rememberSaveable { mutableStateOf("") };var showIssues by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(Unit) { val l=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_->generation++ };store.prefs.registerOnSharedPreferenceChangeListener(l);onDispose { store.prefs.unregisterOnSharedPreferenceChangeListener(l) } }
    val projects=remember(generation) { store.all() };val info=rememberCatalogRecord(context);val record=rememberRepoRecord(context)
    val registeredGroups=ProjectGrouping.groups(projects.map {ProjectIdentity(it.id,it.path,it.repositoryPath,it.worktree)})
    val primaryProjects=registeredGroups.mapNotNull {g->projects.find {it.id==g.first().id}}
    val catalog=info.getJSONObject("catalog");val a=catalog.optJSONArray("projects")
    val found=remember(info.toString(),projects) { buildList { if(a!=null)for(i in 0 until a.length())runCatching { LocalProject.parse(a.getJSONObject(i)) }.getOrNull()?.takeIf { p->projects.none { it.path==p.path } }?.let { add(it) } } }
    val foundGroups=ProjectGrouping.groups(found.map {ProjectIdentity(it.id,it.path,it.repositoryPath,it.worktree)})
    val newProjects=foundGroups.mapNotNull {g->found.find {it.id==g.first().id}}.filter {candidate->primaryProjects.none {p->(p.repositoryPath.ifEmpty {p.path})==(candidate.repositoryPath.ifEmpty {candidate.path})}}
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=8.dp,bottom=100.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item { Text("Репозитории: ${primaryProjects.size} · worktree: ${projects.count {it.worktree}}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=8.dp)) }
            if(projects.isEmpty())item { Text("Добавьте существующий Git-репозиторий. Его файлы останутся на месте.",style=MaterialTheme.typography.bodyLarge) }
            items(primaryProjects,key={it.id}) { p ->
                val snapshot=if(p.supported)record.optJSONObject("snapshot") else null
                var menu by remember { mutableStateOf(false) }
                val branch=snapshot?.optString("branch")?.ifEmpty { p.branch } ?: p.branch
                val dirty=snapshot?.optBoolean("dirty") ?: p.dirty
                val active=p.supported && RepoPolicy.blocked(record.optString("job_id"),record.optString("state"))
                Card(onClick={onOpen(p)},colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(start=16.dp,top=12.dp,bottom=12.dp,end=4.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.secondaryContainer) { Icon(AppIcons.Folder,null,tint=MaterialTheme.colorScheme.onSecondaryContainer,modifier=Modifier.padding(10.dp)) }
                        Column(Modifier.weight(1f).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Text(p.name,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(branch.ifEmpty { "Ветка не проверена" },style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if(active)"${record.optString("action")} · ${MonitorPolicy.label(record.optString("state"),false,false)}" else if(p.error.isNotEmpty())"Нужна проверка" else if(p.worktree)"Worktree · только просмотр" else when(dirty){true->"Локальные изменения";false->"Чисто";else->"Не проверено"},style=MaterialTheme.typography.labelMedium,color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box { IconButton(onClick={haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick);menu=true}) {Icon(AppIcons.More,"Меню ${p.name}")}
                            DropdownMenu(menu,{menu=false}) {DropdownMenuItem(text={Text("Убрать из приложения")},onClick={menu=false;remove=p})}
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(onClick={add=true;if(permitted)gateway.read("CATALOG")},icon={Icon(AppIcons.Add,null)},text={Text("Добавить проект")},modifier=Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(16.dp))
    }
    if(add)ModalBottomSheet(onDismissRequest={add=false}) {
        LazyColumn(Modifier.fillMaxWidth().imePadding().heightIn(max=640.dp),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item { Text("Добавить проект",style=MaterialTheme.typography.titleLarge);Text("Из существующих репозиториев Termux",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Text(if(permitted)info.optString("message").ifEmpty { "Поиск ещё не выполнялся" } else "Разрешите Termux в разделе Termux.",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);IconButton(onClick={gateway.read("CATALOG")},enabled=permitted && !info.optBoolean("pending")){Icon(AppIcons.Refresh,"Повторить поиск")} };if(info.optBoolean("pending"))LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { Text("Найдено репозиториев: ${foundGroups.size} · worktree: ${found.count {it.worktree}}",style=MaterialTheme.typography.bodySmall) }
            items(newProjects,key={it.id}) { p ->
                Surface(onClick={if(store.add(p)){haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm);add=false}},shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(AppIcons.Folder,null,tint=MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Text(p.name,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                            if(p.path.removePrefix(ProjectPolicy.HOME+"/")!=p.name)Text(p.path.removePrefix(ProjectPolicy.HOME+"/"),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if(p.worktree)"Worktree · только просмотр" else p.branch.ifEmpty { "Метаданные не подтверждены" },style=MaterialTheme.typography.labelMedium)
                        }
                        Icon(AppIcons.Add,"Добавить ${p.name}")
                    }
                }
            }
            if(newProjects.isEmpty() && !info.optBoolean("pending"))item {Text("Новых проектов в последнем поиске нет.",style=MaterialTheme.typography.bodyMedium)}
            item { SectionLabel("По точному пути");OutlinedTextField(explicitPath,{explicitPath=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Локальный путь")},placeholder={Text("~/projects/MyApp")});FilledTonalButton(onClick={gateway.read("PATH",explicitPath)},enabled=permitted && explicitPath.isNotBlank() && !info.optBoolean("pending"),modifier=Modifier.padding(top=8.dp)){Text("Проверить путь")} }
            item { Text("Поиск в HOME до 6 уровней. Скрытые и служебные каталоги исключены; ссылки не обходятся.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            val issues=catalog.optJSONArray("issues")
            if(issues!=null && issues.length()>0)item {
                TextButton(onClick={showIssues=!showIssues}){Text(if(showIssues)"Скрыть ограничения" else "Пропуски и ограничения (${issues.length()})")}
                if(showIssues)Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {for(i in 0 until issues.length()) {val item=issues.getJSONObject(i);Text(item.optString("path").removePrefix(ProjectPolicy.HOME)+"\n"+item.optString("reason"),style=MaterialTheme.typography.bodySmall)} }
            }
            item {Spacer(Modifier.height(12.dp))}
        }
    }
    remove?.let { p->AlertDialog(onDismissRequest={remove=null},title={Text("Убрать проект?")},text={Text("${p.name} исчезнет из списка приложения. Файлы и история останутся в Termux.")},confirmButton={TextButton(onClick={store.remove(p.id);remove=null}){Text("Убрать")}},dismissButton={TextButton(onClick={remove=null}){Text("Отмена")}}) }
}
@Composable fun ProjectWorkspace(context:Context,project:LocalProject,onTool:(String)->Unit) {
    val catalogInfo=rememberCatalogRecord(context).getJSONObject("catalog")
    val entries=catalogInfo.optJSONArray("projects")
    val worktrees=buildList {if(entries!=null)for(i in 0 until entries.length())runCatching {LocalProject.parse(entries.getJSONObject(i))}.getOrNull()?.takeIf {it.worktree && it.repositoryPath.isNotEmpty() && it.repositoryPath==(project.repositoryPath.ifEmpty {project.path})}?.let {add(it)}}
    val record=rememberRepoRecord(context);val snap=if(project.supported)record.optJSONObject("snapshot") else null
    val branch=snap?.optString("branch")?.ifEmpty {project.branch} ?: project.branch
    val sha=snap?.optString("local_commit")?.ifEmpty {project.commit} ?: project.commit
    val dirty=snap?.optBoolean("dirty") ?: project.dirty
    val active=project.supported && RepoPolicy.blocked(record.optString("job_id"),record.optString("state"))
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.primaryContainer) { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(branch.ifEmpty {"Ветка не проверена"},style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)
            Text(sha.take(8).ifEmpty {"Коммит не проверен"}+" · "+when(dirty){true->"Локальные изменения";false->"Чисто";else->"Не проверено"},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)
            if(project.worktree)Text("Worktree · только просмотр",style=MaterialTheme.typography.labelMedium)
        } } }
        if(active)item { Surface(onClick={onTool(if(record.optString("action")=="TEST")"tests" else "git")},shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.secondaryContainer) { Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {Text("${record.optString("action")} · ${MonitorPolicy.label(record.optString("state"),false,false)}",style=MaterialTheme.typography.titleSmall);LinearProgressIndicator(Modifier.fillMaxWidth())} } }
        item { OutlinedButton(onClick={onTool("branches")},modifier=Modifier.fillMaxWidth()){Icon(AppIcons.Git,null);Spacer(Modifier.width(8.dp));Text("Ветки · просмотр")} }
        if(worktrees.isNotEmpty())item { Card {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            SectionLabel("Worktree (${worktrees.size})")
            worktrees.forEach {wt->Text(wt.branch+" · "+wt.commit.take(8),style=MaterialTheme.typography.titleSmall);Text(wt.path.removePrefix(ProjectPolicy.HOME+"/"),style=MaterialTheme.typography.bodySmall);Text(if(wt.dirty==true)"Локальные изменения" else if(wt.dirty==false)"Чисто · только просмотр" else "Не проверено",style=MaterialTheme.typography.labelSmall)}
        } } }
        item { Column { SectionLabel("Инструменты")
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
                val tools=listOf(Triple("git","Git",AppIcons.Git),Triple("tests","Тесты",AppIcons.Tests),Triple("build","Сборка",AppIcons.Build),Triple("history","История",AppIcons.History))
                tools.forEachIndexed { index,(id,title,icon)->
                    val enabled=id!="build" && (project.supported || id=="git")
                    val subtitle=when(id){"git"->if(project.supported)"Состояние и обновления" else "Метаданные · только просмотр";"tests"->if(project.supported)"Gradle unit-тесты" else "Нет профиля запуска";"history"->if(project.supported)"Задания, результаты и журналы" else "Заданий пока нет";else->"Недоступно · следующий этап"}
                    Surface(onClick={onTool(id)},enabled=enabled,color=MaterialTheme.colorScheme.surfaceContainerLow) { ListItem(headlineContent={Text(title,style=MaterialTheme.typography.titleMedium)},supportingContent={Text(subtitle)},leadingContent={Icon(icon,null,tint=if(enabled)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)},trailingContent={if(enabled)Icon(AppIcons.Chevron,null)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) }
                    if(index<3)HorizontalDivider(Modifier.padding(start=56.dp,end=16.dp),color=MaterialTheme.colorScheme.outlineVariant)
                }
            }
        } }
        item { Text(project.remote.ifEmpty {"Origin не проверен"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun UnavailableTool(title:String,reason:String) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(title,style=MaterialTheme.typography.titleMedium);Text(reason,style=MaterialTheme.typography.bodyMedium)} }
