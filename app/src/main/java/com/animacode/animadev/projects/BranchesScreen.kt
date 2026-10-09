package com.animacode.animadev.projects

import android.content.Context
import androidx.compose.foundation.layout.*
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
    var generation by remember {mutableIntStateOf(0)}
    var search by rememberSaveable(project.id) {mutableStateOf("")}
    DisposableEffect(Unit) {val l=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,_->generation++};gateway.prefs.registerOnSharedPreferenceChangeListener(l);onDispose {gateway.prefs.unregisterOnSharedPreferenceChangeListener(l)}}
    val data=remember(generation){JSONObject(gateway.prefs.getString("branches","{}") ?: "{}")}
    val entries=data.optJSONArray("projects")
    val metadata=entries?.let {if(it.length()==1)it.optJSONObject(0)?.takeIf {it.optString("path")==project.path} else null}
    val refs=remember(data.toString(),project.path) {buildList {val a=metadata?.optJSONArray("branches");if(a!=null)for(i in 0 until a.length()){val b=a.getJSONObject(i);add(BranchRef(b.optString("name"),b.optString("commit"),b.optString("date"),b.optBoolean("local"),b.optBoolean("active"),b.optBoolean("default")))}}}
    val rows=remember(refs,search){BranchPolicy.merge(refs,search)}
    val pending=gateway.prefs.getBoolean("pending",false)
    val light=MaterialTheme.colorScheme.surface.luminance()>0.5f
    val gold=if(light)Color(0xFF775A00) else Color(0xFFFFD977)
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {Text(project.name,style=MaterialTheme.typography.titleMedium);Text("Локальный снимок · без fetch и переключения веток",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item {OutlinedButton(onClick={gateway.read("BRANCHES",project.path)},enabled=permitted && !pending){Icon(AppIcons.Refresh,null);Spacer(Modifier.width(8.dp));Text("Обновить список")};if(pending)LinearProgressIndicator(Modifier.fillMaxWidth());Text(gateway.prefs.getString("message","") ?: "",style=MaterialTheme.typography.bodySmall)}
        item {OutlinedTextField(search,{search=it},label={Text("Поиск по загруженным веткам")},leadingIcon={Icon(AppIcons.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth())}
        if(metadata!=null) {
            item {Text("Рабочее дерево: "+BranchPolicy.workingTree(metadata.opt("dirty") as? Boolean,metadata.optString("error").isNotBlank()),style=MaterialTheme.typography.bodyMedium)}
            item {Text("Проверено: "+java.text.SimpleDateFormat("dd.MM.yyyy · HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date((data.optDouble("scanned_at",0.0)*1000).toLong())),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(metadata.optBoolean("branches_limited"))Text("Старый снимок неполный. Обновите список для чтения всех страниц.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                Text("Загружено веток: ${BranchPolicy.merge(refs).size}"+(if(metadata.optBoolean("branches_more"))" · есть следующая страница" else ""),style=MaterialTheme.typography.bodySmall)
                if(metadata.optString("default_branch").isEmpty())Text("Главная ветка не подтверждена: origin/HEAD отсутствует.",style=MaterialTheme.typography.bodySmall)
                Text("Origin показывает последний локальный снимок, а не текущие данные GitHub. Для актуальности используйте проверку обновлений в Git.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(rows,key={it.name}) {b->OutlinedCard {Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(b.name,style=MaterialTheme.typography.titleMedium,color=if(b.default)gold else MaterialTheme.colorScheme.onSurface)
                Text(listOfNotNull(if(b.default)"Главная" else null,if(b.active)"Активная" else null,if(b.local!=null && b.remote!=null)"Локальная + origin" else if(b.local!=null)"Локальная" else "origin").joinToString(" · "),style=MaterialTheme.typography.labelMedium,color=if(b.active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                b.local?.let {Text("Локально: ${it.sha.take(8)} · ${BranchPolicy.date(it.date)}",style=MaterialTheme.typography.bodySmall)}
                b.remote?.let {if(b.local==null || b.local.sha!=it.sha)Text("Origin: ${it.sha.take(8)} · ${BranchPolicy.date(it.date)}",style=MaterialTheme.typography.bodySmall)}
                Text(b.comparison,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }}}
            if(rows.isEmpty())item {Text(if(search.isNotBlank())"В загруженных страницах совпадений нет" else "Ветки пока не получены")}
            if(metadata.optBoolean("branches_more"))item {FilledTonalButton(onClick={gateway.read("BRANCHES",project.path,metadata.optLong("branches_next"))},enabled=permitted && !pending,modifier=Modifier.fillMaxWidth()){Text("Загрузить следующие ветки")};Text("Страницы ограничены временем и размером ответа. Изменения refs во время чтения требуют обновить список.",style=MaterialTheme.typography.bodySmall)}
        } else item {Text("Обновите список. Результаты другого проекта здесь не показываются.",style=MaterialTheme.typography.bodyMedium)}
    }
}
