package com.animacode.animadev.projects

import android.content.Context
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.animacode.animadev.repos.MonitorSettings
import com.animacode.animadev.repos.NotificationSettings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun SectionLabel(text:String) { Text(text,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=8.dp,bottom=4.dp)) }
@Composable fun DetailRow(label:String,value:String) {
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,style=MaterialTheme.typography.bodyMedium)
    }
}
val settingsSections=listOf("appearance" to "Внешний вид","git" to "Проекты и Git","profiles" to "Профили тестирования и сборки","jobs" to "Выполнение заданий и ожидание","notifications" to "Уведомления","files" to "Файлы и загрузки")

@Composable fun AppearanceSettings(context:Context,theme:String,dynamic:Boolean,onTheme:(String)->Unit,onDynamic:(Boolean)->Unit,section:String="",onSection:(String)->Unit={}) {
    var showCommand by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(section.isEmpty()) {
            settingsSections.forEach { (id,title)->item { ElevatedCard(onClick={onSection(id)},modifier=Modifier.fillMaxWidth()) { ListItem(headlineContent={Text(title)},trailingContent={Icon(AppIcons.Chevron,null)}) } } }
            item {Text("AnimaDev 0.4.0-beta.5",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        } else when(section) {
            "appearance"->item { Card { Column(Modifier.padding(16.dp)) {
                listOf("system" to "Как в системе","light" to "Светлая","dark" to "Тёмная").forEach { (id,label)->SingleChoiceRow(label,theme==id){onTheme(id)} }
                HorizontalDivider(Modifier.padding(vertical=8.dp))
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Text("Динамические цвета",Modifier.weight(1f));Switch(dynamic,onDynamic) }
            } } }
            "git"->item { Card { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("GitHub — источник истины",style=MaterialTheme.typography.titleMedium)
                Text("Приложение получает изменения через Termux. Push и автоматическое разрешение конфликтов недоступны.")
                Text("Проекты добавляются по существующему локальному пути. Worktree сгруппированы по Git-метаданным; неподдерживаемые проекты доступны для просмотра.",style=MaterialTheme.typography.bodyMedium)
            } } }
            "profiles"->{
                item { Card { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("AnimaQuiz · unit-тесты Debug",style=MaterialTheme.typography.titleMedium)
                    Text("Ветка test/decomposition-v1 · фиксированный офлайн-профиль",style=MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick={showCommand=!showCommand},modifier=Modifier.fillMaxWidth()){Text(if(showCommand)"Скрыть команду" else "Команда профиля")}
                    if(showCommand)Text("bash ./gradlew :app:testDebugUnitTest --offline --no-daemon --rerun-tasks --console=plain",style=MaterialTheme.typography.bodySmall)
                    Text("Профили сборки APK/AAB пока недоступны.",style=MaterialTheme.typography.bodySmall)
                } } }
                ProjectStore(context).all().filterNot {it.supported}.forEach {p->item { DetailRow(p.name,"Нет безопасного профиля исполнения · только просмотр") } }
            }
            "jobs"->item {MonitorSettings(context,embedded=true,showNotifications=false)}
            "notifications"->item {NotificationSettings(context)}
            "files"->item {FileSettings(context)}
        }
    }
}
@Composable fun FileSettings(context:Context) {
    val scope=rememberCoroutineScope()
    val destination=remember {ExportDestination(context)}
    var generation by remember {mutableIntStateOf(0)}
    var error by remember {mutableStateOf("")}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri->
        if(uri!=null)scope.launch {if(!kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){destination.choose(uri)})error="Не удалось сохранить доступ к папке.";generation++}
    }
    val label=remember(generation){destination.label()}
    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Каталог сохранения",style=MaterialTheme.typography.titleMedium)
        Text(label)
        Text("Единая папка для отчётов и будущих артефактов. По умолчанию — Загрузки. Папки не создаются автоматически.",style=MaterialTheme.typography.bodyMedium)
        Button(onClick={picker.launch(null)},modifier=Modifier.fillMaxWidth()){Text("Выбрать папку")}
        OutlinedButton(onClick={destination.useDownloads();generation++;error=""},modifier=Modifier.fillMaxWidth()){Text("Использовать Загрузки")}
        if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
    }
}
