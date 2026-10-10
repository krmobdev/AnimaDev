package com.animacode.animadev.projects

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable fun BranchesScreen(context:Context,project:LocalProject,permitted:Boolean) {
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
            Text(project.name,style=MaterialTheme.typography.titleMedium)
            Text("Выберите строку, чтобы посмотреть коммиты и при необходимости явно переключиться. Переключение допустимо только для чистого AnimaQuiz с совпадающим SHA на GitHub.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(switcher.message().isNotBlank())Text(switcher.message(),style=MaterialTheme.typography.bodySmall)
            if(switcher.pending())LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        item {OutlinedButton(onClick={gateway.read("BRANCHES",project.path)},enabled=permitted && !pending){Icon(AppIcons.Refresh,null);Spacer(Modifier.width(8.dp));Text("Обновить список")};if(pending)LinearProgressIndicator(Modifier.fillMaxWidth());Text(gateway.prefs.getString("message","") ?: "",style=MaterialTheme.typography.bodySmall)}
        item {OutlinedTextField(search,{search=it},label={Text("Поиск по загруженным веткам")},leadingIcon={Icon(AppIcons.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth())}
        if(metadata!=null) {
            item {Text("Рабочее дерево: "+BranchPolicy.workingTree(metadata.opt("dirty") as? Boolean,metadata.optString("error").isNotBlank()),style=MaterialTheme.typography.bodyMedium)}
            item {Text("Проверено: "+java.text.SimpleDateFormat("dd.MM.yyyy · HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date((data.optDouble("scanned_at",0.0)*1000).toLong())),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(metadata.optBoolean("branches_limited"))Text("Старый снимок неполный. Обновите список для чтения всех страниц.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                Text("Загружено веток: ${BranchPolicy.merge(refs).size}"+(if(metadata.optBoolean("branches_more"))" · есть следующая страница" else ""),style=MaterialTheme.typography.bodySmall)
                if(metadata.optString("default_branch").isEmpty())Text("Главная ветка не подтверждена: origin/HEAD отсутствует.",style=MaterialTheme.typography.bodySmall)
                Text("Origin показывает последний локальный снимок, а не текущие данные GitHub. Для актуальности используйте проверку обновлений в Git.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(rows,key={it.name}) {b->OutlinedCard(modifier=Modifier.fillMaxWidth().clickable {selectedBranch=b},shape=MaterialTheme.shapes.large) {Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text(b.name,style=MaterialTheme.typography.titleMedium,color=if(b.default)gold else MaterialTheme.colorScheme.onSurface)
                Text(listOfNotNull(if(b.default)"Главная" else null,if(b.active)"Активная" else null,if(b.local!=null && b.remote!=null)"Локальная + origin" else if(b.local!=null)"Локальная" else "origin").joinToString(" · "),style=MaterialTheme.typography.labelMedium,color=if(b.active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Последний коммит: "+BranchPolicy.date(b.lastCommit?.date.orEmpty()),
                    style=MaterialTheme.typography.bodyMedium)
                b.local?.let {Text("Локальный SHA: "+it.sha.take(12),style=MaterialTheme.typography.bodySmall)}
                b.remote?.let {if(b.local==null || b.local.sha!=it.sha)
                    Text("Origin SHA: "+it.sha.take(12)+" · "+BranchPolicy.date(it.date),style=MaterialTheme.typography.bodySmall)}
                Text(b.comparison,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if(b.active)"Текущая рабочая ветка" else "Нажмите для выбора ветки",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
            }}}
            if(rows.isEmpty())item {Text(if(search.isNotBlank())"В загруженных страницах совпадений нет" else "Ветки пока не получены")}
            if(metadata.optBoolean("branches_more"))item {FilledTonalButton(onClick={gateway.read("BRANCHES",project.path,metadata.optLong("branches_next"))},enabled=permitted && !pending,modifier=Modifier.fillMaxWidth()){Text("Загрузить следующие ветки")};Text("Страницы ограничены временем и размером ответа. Изменения refs во время чтения требуют обновить список.",style=MaterialTheme.typography.bodySmall)}
        } else item {Text("Обновите список. Результаты другого проекта здесь не показываются.",style=MaterialTheme.typography.bodyMedium)}
    }
    selectedBranch?.let {row->
        val eligible=project.id=="animaquiz" && row.local!=null && row.remote!=null &&
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
                    switcher.request(project,row,metadata?.optString("commit").orEmpty())
                    selectedBranch=null
                },enabled=eligible) {Text("Переключить ветку")}
            },
            dismissButton={TextButton(onClick={selectedBranch=null}){Text("Закрыть")}}
        )
    }
}
