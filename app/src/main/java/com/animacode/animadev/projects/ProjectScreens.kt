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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
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
@Composable fun ProjectsScreen(context:Context,permitted:Boolean,onOpen:(LocalProject)->Unit,
    onMenuRoute:(String)->Unit={}) {
    val haptic=androidx.compose.ui.platform.LocalHapticFeedback.current
    val store=remember { ProjectStore(context) };val gateway=remember { CatalogGateway(context) }
    var generation by remember { mutableIntStateOf(0) };var add by rememberSaveable { mutableStateOf(false) };var remove by remember { mutableStateOf<LocalProject?>(null) }
    var neonMenu by remember {mutableStateOf(false)}
    var explicitPath by rememberSaveable { mutableStateOf("") };var showIssues by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(Unit) { val l=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_->generation++ };store.prefs.registerOnSharedPreferenceChangeListener(l);onDispose { store.prefs.unregisterOnSharedPreferenceChangeListener(l) } }
    val projects=remember(generation) { store.all() };val info=rememberCatalogRecord(context);val record=rememberRepoRecord(context)
    val neon=LocalNeonSkin.current
    val registeredGroups=ProjectGrouping.groups(projects.map {ProjectIdentity(it.id,it.path,it.repositoryPath,it.worktree)})
    val primaryProjects=registeredGroups.mapNotNull {g->projects.find {it.id==g.first().id}}
    val catalog=info.getJSONObject("catalog");val a=catalog.optJSONArray("projects")
    val found=remember(info.toString(),projects) { buildList { if(a!=null)for(i in 0 until a.length())runCatching { LocalProject.parse(a.getJSONObject(i)) }.getOrNull()?.takeIf { p->projects.none { it.path==p.path } }?.let { add(it) } } }
    val foundGroups=ProjectGrouping.groups(found.map {ProjectIdentity(it.id,it.path,it.repositoryPath,it.worktree)})
    val newProjects=foundGroups.mapNotNull {g->found.find {it.id==g.first().id}}.filter {candidate->primaryProjects.none {p->(p.repositoryPath.ifEmpty {p.path})==(candidate.repositoryPath.ifEmpty {candidate.path})}}
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=20.dp,bottom=110.dp),verticalArrangement=Arrangement.spacedBy(17.dp)) {
            if(neon)item {
                Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Projects",style=MaterialTheme.typography.displaySmall,
                        fontWeight=FontWeight.ExtraBold,color=NeonGlass.white,modifier=Modifier.weight(1f))
                    Box {
                        Surface(shape=RoundedCornerShape(16.dp),color=NeonGlass.raised,
                            border=BorderStroke(1.dp,NeonGlass.outline)) {
                            IconButton(onClick={neonMenu=true},modifier=Modifier.size(55.dp)) {
                                Icon(AppIcons.More,"Menu",tint=NeonGlass.green)
                            }
                        }
                        DropdownMenu(expanded=neonMenu,onDismissRequest={neonMenu=false}) {
                            listOf("settings" to "Настройки","changelog" to "Список изменений",
                                "about" to "О программе","help" to "Документация").forEach {(id,label)->
                                DropdownMenuItem(text={Text(label)},onClick={
                                    neonMenu=false;onMenuRoute(id)
                                })
                            }
                        }
                    }
                }
                Text("Репозитории: ${primaryProjects.size} · worktree: ${projects.count {it.worktree}}",
                    style=MaterialTheme.typography.bodySmall,color=NeonGlass.muted,
                    modifier=Modifier.padding(top=14.dp))
            } else item { Text("Репозитории: ${primaryProjects.size} · worktree: ${projects.count {it.worktree}}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=8.dp)) }
            if(projects.isEmpty())item { Text("Добавьте существующий Git-репозиторий. Его файлы останутся на месте.",style=MaterialTheme.typography.bodyLarge) }
            items(primaryProjects,key={it.id}) { p ->
                val snapshot=if(p.supported && record.optString("project_id","animaquiz")==p.id)
                    record.optJSONObject("snapshot")?.takeIf {it.optString("branch")==p.branch &&
                        it.optString("local_commit")==p.commit} else null
                var menu by remember { mutableStateOf(false) }
                val branch=p.branch.ifBlank {snapshot?.optString("branch").orEmpty()}
                val dirty=snapshot?.optBoolean("dirty") ?: p.dirty
                val active=p.supported && record.optString("project_id","animaquiz")==p.id && RepoPolicy.blocked(record.optString("job_id"),record.optString("state"))
                if(neon) {
                    Box {
                        NeonProjectCard(p,branch,dirty,active,onClick={onOpen(p)},onMore={menu=true})
                        DropdownMenu(menu,{menu=false}) {
                            DropdownMenuItem(text={Text("Убрать из приложения")},
                                onClick={menu=false;remove=p})
                        }
                    }
                } else Card(onClick={onOpen(p)},shape=RoundedCornerShape(24.dp),
                    colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer),
                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                    Row(Modifier.fillMaxWidth().padding(start=16.dp,top=12.dp,bottom=12.dp,end=4.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Surface(shape=RoundedCornerShape(15.dp),color=MaterialTheme.colorScheme.primaryContainer) { Icon(AppIcons.Folder,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(10.dp)) }
                        Column(Modifier.weight(1f).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Text(p.name,style=MaterialTheme.typography.titleLarge,color=Color.White,
                                fontWeight=FontWeight.ExtraBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text("● "+branch.ifEmpty { "Ветка не проверена" },
                                style=MaterialTheme.typography.titleSmall,
                                fontWeight=FontWeight.Bold,maxLines=1,
                                overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.primary)
                            Text(if(active)"${record.optString("action")} · ${MonitorPolicy.label(record.optString("state"),false,false)}" else if(p.error.isNotEmpty())"Нужна проверка" else if(p.worktree)"Worktree · только просмотр" else when(dirty){true->"Локальные изменения";false->"Чисто";else->"Не проверено"},style=MaterialTheme.typography.labelMedium,color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box { IconButton(onClick={haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick);menu=true}) {Icon(AppIcons.More,"Меню ${p.name}")}
                            DropdownMenu(menu,{menu=false}) {DropdownMenuItem(text={Text("Убрать из приложения")},onClick={menu=false;remove=p})}
                        }
                    }
                }
            }
        }
        if(neon) NeonPrimaryAction(
            title="Добавить проект",icon=AppIcons.Add,
            modifier=Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(start=20.dp,end=20.dp,bottom=10.dp).fillMaxWidth(),
            onClick={add=true;if(permitted)gateway.read("CATALOG")}
        )
        else ExtendedFloatingActionButton(onClick={add=true;if(permitted)gateway.read("CATALOG")},icon={Icon(AppIcons.Add,null)},text={Text("Добавить проект")},modifier=Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(16.dp))
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
            item { SectionLabel("По точному пути");OutlinedTextField(explicitPath,{explicitPath=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Локальный путь")},placeholder={Text("~/projects/MyApp")});com.animacode.animadev.projects.ActionButton(onClick={gateway.read("PATH",explicitPath)},enabled=permitted && explicitPath.isNotBlank() && !info.optBoolean("pending"),modifier=Modifier.padding(top=8.dp)){Text("Проверить путь")} }
            item { Text("Поиск в HOME до 6 уровней. Скрытые и служебные каталоги исключены; ссылки не обходятся.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            val issues=catalog.optJSONArray("issues")
            if(issues!=null && issues.length()>0)item {
                com.animacode.animadev.projects.ActionButton(onClick={showIssues=!showIssues}){Text(if(showIssues)"Скрыть ограничения" else "Пропуски и ограничения (${issues.length()})")}
                if(showIssues)Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {for(i in 0 until issues.length()) {val item=issues.getJSONObject(i);Text(item.optString("path").removePrefix(ProjectPolicy.HOME)+"\n"+item.optString("reason"),style=MaterialTheme.typography.bodySmall)} }
            }
            item {Spacer(Modifier.height(12.dp))}
        }
    }
    remove?.let { p->AlertDialog(onDismissRequest={remove=null},title={Text("Убрать проект?")},text={Text("${p.name} исчезнет из списка приложения. Файлы и история останутся в Termux.")},confirmButton={com.animacode.animadev.projects.ActionButton(compact=true,onClick={store.remove(p.id);remove=null}){Text("Убрать")}},dismissButton={com.animacode.animadev.projects.ActionButton(compact=true,onClick={remove=null}){Text("Отмена")}}) }
}
@Composable fun ProjectWorkspace(context:Context,project:LocalProject,onTool:(String)->Unit,
    permitted:Boolean=false,onJournal:(String)->Unit={}) {
    var worktreesExpanded by rememberSaveable(project.id) { mutableStateOf(false) }
    val neon=LocalNeonSkin.current
    val catalog=rememberCatalogRecord(context)
    val entries=catalog.getJSONObject("catalog").optJSONArray("projects")
    val worktrees=buildList {
        if(entries!=null) for(i in 0 until entries.length())
            runCatching {LocalProject.parse(entries.getJSONObject(i))}.getOrNull()
                ?.takeIf {it.worktree && it.repositoryPath.isNotEmpty() &&
                    it.repositoryPath==(project.repositoryPath.ifEmpty {project.path})}?.let {add(it)}
    }
    val record=rememberRepoRecord(context)
    val snap=if(project.supported && record.optString("project_id","animaquiz")==project.id)
        record.optJSONObject("snapshot")?.takeIf {
            it.optString("branch")==project.branch && it.optString("local_commit")==project.commit
        } else null
    val branch=project.branch.ifBlank {snap?.optString("branch").orEmpty()}
    val sha=project.commit.ifBlank {snap?.optString("local_commit").orEmpty()}
    val dirty=project.dirty ?: snap?.optBoolean("dirty")
    val isActiveJob=project.supported && record.optString("project_id","animaquiz")==project.id &&
        RepoPolicy.blocked(record.optString("job_id"),record.optString("state"))
    val releaseGeneration=ReleaseStore.generation.intValue
    val release=remember(releaseGeneration) {ReleaseStore(context).read()}
    val isActiveRelease=release.optString("project")==project.id && ReleaseStore.active.contains(release.optString("state"))
    var releaseJournal by remember {mutableStateOf(false)}
    var releaseStopConfirm by remember {mutableStateOf(false)}
    val feedback=remember {UiFeedback(context)}
    val view=androidx.compose.ui.platform.LocalView.current
    fun open(id:String) {feedback.tap(view);onTool(id)}
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding=PaddingValues(start=20.dp,end=20.dp,top=18.dp,bottom=48.dp),
        verticalArrangement=Arrangement.spacedBy(22.dp)) {
        item {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(neon) {
                    Text("Project",style=MaterialTheme.typography.labelLarge,color=NeonGlass.muted)
                    Text(project.name,style=MaterialTheme.typography.displaySmall,
                        fontWeight=FontWeight.ExtraBold,color=NeonGlass.white)
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        NeonStatus(branch.ifBlank {"Not verified"},dirty==false)
                        Text(sha.take(12).ifBlank {"SHA not checked"},
                            style=MaterialTheme.typography.bodyMedium,color=NeonGlass.muted)
                        IconButton(onClick={
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as
                                android.content.ClipboardManager).setPrimaryClip(
                                android.content.ClipData.newPlainText("Commit SHA",sha))
                        },enabled=sha.isNotBlank(),modifier=Modifier.size(38.dp)) {
                            Icon(AppIcons.Copy,"Copy SHA",tint=NeonGlass.muted)
                        }
                    }
                } else Text(project.name,style=MaterialTheme.typography.headlineLarge,
                    fontWeight=FontWeight.ExtraBold)
                if(catalog.optBoolean("pending"))
                    Text("Обновляем реальный HEAD из Termux…",
                        style=MaterialTheme.typography.bodySmall)
            }
        }
        if(!neon)item {ActiveBranchHero(branch,sha,dirty)}
        if(isActiveJob)item {
            val action=record.optString("action")
            val destination=when(action) {
                "TEST_DEVICE"->"tests_device"
                "TEST"->"tests_unit"
                "BUILD_DEBUG"->"build_debug"
                else->"git"
            }
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text("ЗАДАНИЕ В РАБОТЕ",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                com.animacode.animadev.repos.JobMonitor(context,record,permitted,onJournal)
                OutlinedButton(onClick={open(destination)},modifier=Modifier.fillMaxWidth()) {
                    Text("Открыть инструмент задания")
                }
            }
        }
        if(isActiveRelease)item {
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("RELEASE AAB · В РАБОТЕ",style=MaterialTheme.typography.titleMedium)
                    Text("Этап: "+release.optString("phase"))
                    Text("Job ID: "+release.optString("id").take(12))
                    Text(release.optString("message"),style=MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton(onClick={releaseJournal=true},modifier=Modifier.fillMaxWidth()) {Text("Журнал Release")}
                    OutlinedButton(onClick={open("build_release")},modifier=Modifier.fillMaxWidth()) {Text("Открыть сборку Release")}
                    if(!release.optBoolean("cancel_requested") && release.optString("phase")!="EXPORTING")
                        OutlinedButton(onClick={releaseStopConfirm=true},modifier=Modifier.fillMaxWidth()) {Text("Остановить Release · Ctrl+C")}
                }
            }
        }
        if(!neon)item {
            com.animacode.animadev.projects.ActionButton(onClick={open("branches")},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                Icon(AppIcons.Git,null);Spacer(Modifier.width(10.dp))
                Text("Ветки и переключение")
            }
        }
        item {
            if(neon) NeonPanel(Modifier.fillMaxWidth(),glowing=true) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Tools",style=MaterialTheme.typography.titleLarge,
                        color=NeonGlass.white,fontWeight=FontWeight.Bold,
                        modifier=Modifier.weight(1f))
                    Text("‹/›",color=NeonGlass.green,style=MaterialTheme.typography.titleLarge)
                }
                Text("Git, tests, builds and project history",
                    style=MaterialTheme.typography.bodySmall,color=NeonGlass.muted)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    NeonToolCard("Git","Branches & SHA",AppIcons.Git,Modifier.weight(1f)){open("git")}
                    NeonToolCard("Tests","Gradle & reports",AppIcons.Tests,Modifier.weight(1f)){open("tests")}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    NeonToolCard("Build","Debug & Release",AppIcons.Build,Modifier.weight(1f)){open("build")}
                    NeonToolCard("History","Jobs & results",AppIcons.History,Modifier.weight(1f)){open("history")}
                }
                NeonPrimaryAction("Build Now","Latest commit · "+sha.take(8).ifBlank {"not verified"},
                    AppIcons.Build,onClick={open("build")})
                OutlinedButton(onClick={open("branches")},modifier=Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Git,null);Spacer(Modifier.width(8.dp))
                    Text("Ветки и переключение")
                }
            } else Column(verticalArrangement=Arrangement.spacedBy(13.dp)) {
                Text("ИНСТРУМЕНТЫ",style=MaterialTheme.typography.titleSmall,
                    color=MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    PulseToolTile("Git","Проверка SHA",AppIcons.Git,Modifier.weight(1f)){open("git")}
                    PulseToolTile("Тесты","Gradle & отчёты",AppIcons.Tests,Modifier.weight(1f)){open("tests")}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    PulseToolTile("Сборка","APK / AAB",AppIcons.Build,Modifier.weight(1f)){open("build")}
                    PulseToolTile("История","Результаты",AppIcons.History,Modifier.weight(1f)){open("history")}
                }
            }
        }
        if(worktrees.isNotEmpty())item {
            OutlinedCard(onClick={worktreesExpanded=!worktreesExpanded},
                modifier=Modifier.fillMaxWidth(),
                shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text("WORKTREE",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                        Text("${worktrees.size}",color=MaterialTheme.colorScheme.primary,
                            style=MaterialTheme.typography.titleMedium)
                        Icon(AppIcons.Chevron,null,Modifier.rotate(if(worktreesExpanded)90f else 0f))
                    }
                    if(worktreesExpanded)worktrees.forEach {wt->
                        HorizontalDivider()
                        Text(wt.branch,style=MaterialTheme.typography.titleSmall)
                        Text(wt.commit.take(12)+" · только просмотр",
                            style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Text(project.remote.ifBlank {"Origin не подтверждён"},
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if(releaseJournal) ReleaseJournalDialog(context,release.optString("id")) {releaseJournal=false}
    if(releaseStopConfirm)AlertDialog(onDismissRequest={releaseStopConfirm=false},
        title={Text("Остановить Release?")},
        text={Text("Отправить Ctrl+C в Termux для этого Release AAB? Другие сборки останутся заблокированы до подтверждения результата.")},
        confirmButton={TextButton(onClick={releaseStopConfirm=false;ReleaseCancel.request(context)}) {Text("Остановить")}},
        dismissButton={TextButton(onClick={releaseStopConfirm=false}) {Text("Отмена")}})

}
@Composable fun UnavailableTool(title:String,reason:String) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(title,style=MaterialTheme.typography.titleMedium);Text(reason,style=MaterialTheme.typography.bodyMedium)} }
