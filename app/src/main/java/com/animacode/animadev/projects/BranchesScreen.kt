package com.animacode.animadev.projects

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable fun BranchesScreen(context:Context,project:LocalProject,permitted:Boolean) {
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val neon=LocalNeonSkin.current
    val feedback=remember {UiFeedback(context)}
    val feedbackView=LocalView.current
    val gateway=remember {CatalogGateway(context)}
    val switcher=remember {BranchSwitchGateway(context)}
    var selectedBranch by remember {mutableStateOf<BranchRow?>(null)}
    var generation by remember {mutableIntStateOf(0)}
    var search by rememberSaveable(project.id) {mutableStateOf("")}
    DisposableEffect(Unit) {
        val l=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,_->generation++}
        gateway.prefs.registerOnSharedPreferenceChangeListener(l)
        switcher.prefs.registerOnSharedPreferenceChangeListener(l)
        onDispose {
            gateway.prefs.unregisterOnSharedPreferenceChangeListener(l)
            switcher.prefs.unregisterOnSharedPreferenceChangeListener(l)
        }
    }
    val data=remember(generation){JSONObject(gateway.prefs.getString("branches","{}") ?: "{}")}
    val entries=data.optJSONArray("projects")
    val metadata=entries?.let {if(it.length()==1)it.optJSONObject(0)?.takeIf {it.optString("path")==project.path} else null}
    val refs=remember(data.toString(),project.path) {buildList {val a=metadata?.optJSONArray("branches");if(a!=null)for(i in 0 until a.length()){val b=a.getJSONObject(i);add(BranchRef(b.optString("name"),b.optString("commit"),b.optString("date"),b.optBoolean("local"),b.optBoolean("active"),b.optBoolean("default")))}}}
    val rows=remember(refs,search){BranchPolicy.merge(refs,search)}
    val pending=gateway.prefs.getBoolean("pending",false)
    val light=MaterialTheme.colorScheme.surface.luminance()>0.5f
    val gold=if(light)Color(0xFF775A00) else Color(0xFFFFD977)
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=20.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            if(neon)NeonSection("Branches",AppIcons.Git,project.name)
            else Text(project.name,style=MaterialTheme.typography.titleMedium)
            Text("Здесь показаны Git-ссылки выбранного каталога, а не отдельные рабочие каталоги. Ночная ветка может быть открыта в другом worktree — список таких каталогов теперь находится на экране проекта выше инструментов.",style=MaterialTheme.typography.bodyMedium,color=if(neon)NeonGlass.softGreen else MaterialTheme.colorScheme.primary)
            Text("Выберите локальную ветку с подтверждённым SHA GitHub, чтобы безопасно переключиться. Для всех подключённых обычных репозиториев без локальных изменений.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(switcher.message().isNotBlank())Text(switcher.message(),style=MaterialTheme.typography.bodySmall)
            if(switcher.pending())LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        item {OutlinedButton(onClick={feedback.tap(feedbackView);gateway.read("BRANCHES",project.path)},enabled=permitted && !pending){Icon(AppIcons.Refresh,null);Spacer(Modifier.width(8.dp));Text("Обновить список")};if(pending)LinearProgressIndicator(Modifier.fillMaxWidth());Text(gateway.prefs.getString("message","") ?: "",style=MaterialTheme.typography.bodySmall)}
        item {
            if(neon) NeonPanel(Modifier.fillMaxWidth()) {
                OutlinedTextField(search,{search=it},label={Text("Search branches")},
                    leadingIcon={Icon(AppIcons.Search,null)},singleLine=true,
                    shape=RoundedCornerShape(19.dp),modifier=Modifier.fillMaxWidth())
            } else OutlinedTextField(search,{search=it},label={Text("Поиск по загруженным веткам")},
                leadingIcon={Icon(AppIcons.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth())
        }
        if(metadata!=null) {
            item {
                val workTree=BranchPolicy.workingTree(metadata.opt("dirty") as? Boolean,
                    metadata.optString("error").isNotBlank())
                if(neon) NeonPanel(Modifier.fillMaxWidth()) {
                    // A status cannot share a weighted row with a long Russian
                    // label at 360dp and large font scaling; stack vertically.
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Icon(AppIcons.Git,null,tint=NeonGlass.green,
                            modifier=Modifier.size(24.dp))
                        Text("Рабочее дерево",style=MaterialTheme.typography.titleMedium,
                            fontWeight=FontWeight.Bold,color=NeonGlass.white)
                    }
                    val verified=!metadata.isNull("dirty") &&
                        metadata.optString("error").isBlank()
                    NeonStatus(when {
                        !verified->"Не проверено"
                        metadata.optBoolean("dirty")->"Локальные изменения"
                        else->"Чисто"
                    },verified && !metadata.optBoolean("dirty"))
                    Text(if(!verified)"Не удалось подтвердить состояние файлов."
                        else if(metadata.optBoolean("dirty"))
                            "Незакоммиченные файлы. Переключение веток заблокировано до их сохранения."
                        else "Незакоммиченных изменений нет.",
                        style=MaterialTheme.typography.bodySmall,
                        color=NeonGlass.muted)
                } else Text("Рабочее дерево: "+workTree,
                    style=MaterialTheme.typography.bodyMedium)
            }
            item {Text("Проверено: "+java.text.SimpleDateFormat("dd.MM.yyyy · HH:mm:ss",locale).format(java.util.Date((data.optDouble("scanned_at",0.0)*1000).toLong())),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(metadata.optBoolean("branches_limited"))Text("Старый снимок неполный. Обновите список для чтения всех страниц.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                Text("Загружено веток: ${BranchPolicy.merge(refs).size}"+(if(metadata.optBoolean("branches_more"))" · есть следующая страница" else ""),style=MaterialTheme.typography.bodySmall)
                if(metadata.optString("default_branch").isEmpty())Text("Главная ветка не подтверждена: origin/HEAD отсутствует.",style=MaterialTheme.typography.bodySmall)
                Text("Origin — сохранённые локальные ссылки на ветки. Кнопка «Обновить список» перечитывает их в Termux, но не выполняет GitHub fetch. Актуальный SHA GitHub проверяйте в разделе Git. Несохранённые изменения не удаляются автоматически.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(rows,key={it.name}) {b->
                Surface(onClick={feedback.tap(feedbackView);selectedBranch=b},
                    modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),
                    color=if(b.active)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border=BorderStroke(if(b.active)2.dp else 1.dp,
                        if(b.active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.fillMaxWidth()
                        .then(if(neon && b.active)Modifier.background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color(0xFF145637),Color(0xFF09261C)))) else Modifier)
                        .padding(20.dp).animateContentSize(),
                        verticalArrangement=Arrangement.spacedBy(11.dp)) {
                if(neon && b.active)Icon(AppIcons.Git,null,tint=NeonGlass.green,
                    modifier=Modifier.size(36.dp))
                Text(b.name,style=if(b.active && neon)MaterialTheme.typography.headlineMedium else
                    if(b.active)MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight=if(b.active)FontWeight.Black else FontWeight.Bold,
                    color=if(b.active)MaterialTheme.colorScheme.primary else if(b.default)gold else MaterialTheme.colorScheme.onSurface)
                if(b.active)Surface(color=MaterialTheme.colorScheme.primary,shape=RoundedCornerShape(40.dp)) {
                    Text("● АКТИВНАЯ СЕЙЧАС",Modifier.padding(horizontal=12.dp,vertical=5.dp),
                        style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Black,
                        color=MaterialTheme.colorScheme.onPrimary)
                }
                Text(listOfNotNull(if(b.default)"Главная" else null,if(b.active)"Активная" else null,if(b.local!=null && b.remote!=null)"Локальная + origin" else if(b.local!=null)"Локальная" else "origin").joinToString(" · "),style=MaterialTheme.typography.labelMedium,color=if(b.active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Последний коммит: "+BranchPolicy.date(b.lastCommit?.date.orEmpty()),
                    style=MaterialTheme.typography.bodyMedium,color=if(b.active)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                b.local?.let {Text("Локальный SHA: "+it.sha.take(12),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                b.remote?.let {if(b.local==null || b.local.sha!=it.sha)
                    Text("Origin SHA: "+it.sha.take(12)+" · "+BranchPolicy.date(it.date),style=MaterialTheme.typography.bodySmall)}
                Text(b.comparison,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if(b.active)"Текущая рабочая ветка" else "Нажмите для выбора ветки",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
            }}}
            if(rows.isEmpty())item {Text(if(search.isNotBlank())"В загруженных страницах совпадений нет" else "Ветки пока не получены")}
            if(metadata.optBoolean("branches_more"))item {com.animacode.animadev.projects.ActionButton(onClick={feedback.tap(feedbackView);gateway.read("BRANCHES",project.path,metadata.optLong("branches_next"))},enabled=permitted && !pending,modifier=Modifier.fillMaxWidth()){Text("Загрузить следующие ветки")};Text("Страницы ограничены временем и размером ответа. Изменения refs во время чтения требуют обновить список.",style=MaterialTheme.typography.bodySmall)}
        } else item {Text("Обновите список. Результаты другого проекта здесь не показываются.",style=MaterialTheme.typography.bodyMedium)}
    }
    selectedBranch?.let {row->
        val eligible=project.supported && !project.worktree && row.local!=null && row.remote!=null &&
            row.local.sha==row.remote.sha && !row.active &&
            metadata?.optBoolean("dirty")==false && !switcher.pending() &&
            !gateway.prefs.getBoolean("pending",false) &&
            !com.animacode.animadev.repos.RepoStore(context).blocked() &&
            !ReleaseStore(context).busy()
        AlertDialog(
            onDismissRequest={selectedBranch=null},
            title={Text(row.name)},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(row.active)"Эта ветка уже активна." else "Выбрана ветка для переключения.",style=MaterialTheme.typography.bodyMedium)
                Text("Локальный SHA: "+(row.local?.sha?.take(12) ?: "отсутствует"),style=MaterialTheme.typography.bodySmall)
                Text("Origin SHA: "+(row.remote?.sha?.take(12) ?: "отсутствует"),style=MaterialTheme.typography.bodySmall)
                Text(if(eligible)"Перед переключением Termux заново проверит чистоту дерева, локальный SHA и GitHub. Без merge, reset или удаления."
                     else "Для безопасного переключения нужна локальная ветка, подтверждённая в origin. Только локальные или расходящиеся ветки пока не переключаются.",
                     style=MaterialTheme.typography.bodySmall)
            }},
            confirmButton={
                Button(onClick={
                    feedback.tap(feedbackView)
                    switcher.request(project,row,metadata?.optString("commit").orEmpty())
                    selectedBranch=null
                },enabled=eligible) {Text("Переключить ветку")}
            },
            dismissButton={com.animacode.animadev.projects.ActionButton(compact=true,onClick={selectedBranch=null}){Text("Закрыть")}}
        )
    }
}
