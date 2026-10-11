package com.animacode.animadev.projects

import android.content.Context
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.animacode.animadev.repos.NotificationSettings
import com.animacode.animadev.repos.MonitorPolicy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

@Composable fun SectionLabel(text:String) { Text(text,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=8.dp,bottom=4.dp)) }
@Composable fun DetailRow(label:String,value:String) {
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,style=MaterialTheme.typography.bodyMedium)
    }
}
val settingsSections=listOf("appearance" to "Внешний вид","git" to "Проекты и Git","profiles" to "Профили тестирования и сборки","signing" to "Подпись Release AAB","jobs" to "Выполнение заданий и ожидание","notifications" to "Уведомления","files" to "Файлы и загрузки","about" to "О приложении / Диагностика")
private val descriptions=mapOf("appearance" to "Тема, динамические цвета, вибрация и звуки","git" to "Получение изменений, локальные репозитории","profiles" to "Тесты и Debug APK","signing" to "Выбор JKS, alias и переход к подписанной сборке","jobs" to "Предупреждения и защитный предел","notifications" to "Разрешение Android и состояние заданий","files" to "Папка отчётов и загрузок","about" to "Версия, Android и диагностика")

@Composable private fun Preference(title:String,value:String,onClick:(()->Unit)?=null,trailing:(@Composable ()->Unit)?=null) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val feedback=remember {UiFeedback(context)}
    val view=LocalView.current
    if(LocalNeonSkin.current) Surface(
        modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=5.dp)
            .then(if(onClick!=null)Modifier.clickable {feedback.tap(view);onClick()} else Modifier),
        color=NeonGlass.surface,shape=RoundedCornerShape(19.dp),
        border=BorderStroke(1.dp,NeonGlass.outline)) {
        Row(Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium,
                    fontWeight=FontWeight.SemiBold,color=NeonGlass.white)
                Text(value,style=MaterialTheme.typography.bodySmall,color=NeonGlass.muted)
            }
            if(trailing!=null)trailing()
            else if(onClick!=null)Icon(AppIcons.Chevron,null,tint=NeonGlass.green)
        }
    } else ListItem(headlineContent={Text(title)},supportingContent={Text(value)},
        trailingContent=trailing ?: if(onClick!=null)({Icon(AppIcons.Chevron,null)}) else null,
        modifier=if(onClick!=null)Modifier.clickable {feedback.tap(view);onClick()} else Modifier)
}
@Composable fun AppearanceSettings(context:Context,theme:String,dynamic:Boolean,onTheme:(String)->Unit,onDynamic:(Boolean)->Unit,section:String="",onSection:(String)->Unit={},pulse:Boolean=true,onPulse:(Boolean)->Unit={},onOpenBuild:(String)->Unit={},language:String="system",onLanguage:(String)->Unit={},emerald:Boolean=false,onEmerald:(Boolean)->Unit={},onSupport:(String)->Unit={}) {
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val feedback=remember {UiFeedback(context)}
    val view=LocalView.current
    var haptics by remember {mutableStateOf(feedback.vibrations())}
    var sounds by remember {mutableStateOf(feedback.sounds())}
    fun openSection(value:String){focus.clearFocus();keyboard?.hide();onSection(value)}
    var query by rememberSaveable {mutableStateOf("")}
    var selector by rememberSaveable {mutableStateOf("")}
    var showCommand by rememberSaveable {mutableStateOf(false)}
    val state=rememberLazyListState()
    val category=SettingsPolicy.section(section)
    LaunchedEffect(section) {state.scrollToItem(SettingsPolicy.index(section))}
    val prefs=remember {context.getSharedPreferences("monitor_settings",Context.MODE_PRIVATE)}
    var minutes by remember {mutableIntStateOf(MonitorPolicy.minutes(prefs.getInt("warning_minutes",10)))}
    val themeLabel=listOf("system" to "Как в системе","light" to "Светлая","dark" to "Тёмная").toMap()[theme] ?: "Как в системе"
    val englishUi=AppLanguage.english(LocalAppLanguage.current,androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language)
    val neon=LocalNeonSkin.current
    LazyColumn(Modifier.fillMaxSize().imePadding(),state=state,contentPadding=PaddingValues(bottom=24.dp)) {
        if(section.isEmpty()) {
            if(neon)item {
                Text("Settings",style=MaterialTheme.typography.headlineLarge,
                    fontWeight=androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier=Modifier.padding(start=20.dp,top=12.dp,bottom=8.dp))
            }
            item {OutlinedTextField(query,{query=it},
                label={Text(if(neon)"Search settings" else "Поиск настроек")},
                leadingIcon={Icon(AppIcons.Search,null)},
                trailingIcon={if(query.isNotEmpty())IconButton(onClick={query=""}) {
                    Icon(AppIcons.Close,"Очистить поиск")
                }},singleLine=true,shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp))}
            if(query.isBlank())settingsSections.forEach {(id,title)->item(key=id) {
                val tileIcon=when(id) {
                    "git"->AppIcons.Git;"profiles"->AppIcons.Tests;"signing"->AppIcons.Build
                    "jobs"->AppIcons.Waiting;"files"->AppIcons.Folder;"about"->AppIcons.Info
                    "notifications"->AppIcons.Notifications;else->AppIcons.Palette
                }
                if(neon)Box(Modifier.padding(horizontal=16.dp,vertical=5.dp)) {
                    NeonSettingsRow(title,descriptions.getValue(id),tileIcon) {
                        feedback.tap(view);openSection(id)
                    }
                } else ListItem(headlineContent={Text(title)},
                    supportingContent={Text(descriptions.getValue(id))},
                    leadingContent={Icon(tileIcon,null,tint=MaterialTheme.colorScheme.primary)},
                    trailingContent={Icon(AppIcons.Chevron,null)},
                    modifier=Modifier.clickable{feedback.tap(view);openSection(id)})
            }}
            if(neon && query.isBlank()) {
                item(key="app_support_header") {
                    NeonSection(if(englishUi)"App & support" else "Приложение и помощь",
                        AppIcons.Info,
                        if(englishUi)"Information, documentation and changes"
                        else "Информация, документация и изменения",
                        modifier=Modifier.padding(start=20.dp,end=20.dp,top=23.dp,bottom=9.dp))
                }
                item(key="app_support_changelog") {
                    Box(Modifier.padding(horizontal=16.dp,vertical=5.dp)) {
                        NeonSettingsRow(
                            if(englishUi)"Change log" else "Список изменений",
                            if(englishUi)"Newest versions, dates and fixes first"
                            else "Версии, даты и исправления — новые сверху",
                            AppIcons.History) {feedback.tap(view);onSupport("changelog")}
                    }
                }
                item(key="app_support_docs") {
                    Box(Modifier.padding(horizontal=16.dp,vertical=5.dp)) {
                        NeonSettingsRow(
                            if(englishUi)"Documentation" else "Документация",
                            if(englishUi)"Git, Termux, tests and build instructions"
                            else "Git, Termux, тесты и инструкции по сборке",
                            AppIcons.Folder) {feedback.tap(view);onSupport("help")}
                    }
                }
                item(key="app_support_about") {
                    Box(Modifier.padding(horizontal=16.dp,vertical=5.dp)) {
                        NeonSettingsRow(
                            if(englishUi)"About AnimaDev" else "О программе AnimaDev",
                            if(englishUi)"Version, platform and GitHub"
                            else "Версия, система и GitHub",
                            AppIcons.Info) {feedback.tap(view);onSupport("about")}
                    }
                }
            }
            else if(query.isNotBlank()) {
                val results=SettingsPolicy.search(query,englishUi)
                if(results.isEmpty())item {DetailRow("Ничего не найдено","Попробуйте другое название или описание")}
                results.forEach {entry->item(key=entry.section+entry.id){Preference(entry.title,entry.description,{openSection(entry.section+"/"+entry.id)})}}
            }
        } else {
            if(neon)item {
                val sectionTitle=settingsSections.firstOrNull{it.first==category}?.second
                    ?: "Настройки"
                NeonSection(sectionTitle,AppIcons.Settings,
                    "Настройки сохраняются автоматически",
                    modifier=Modifier.padding(horizontal=20.dp,vertical=12.dp))
            }
            when(category) {
            "appearance"->{
                item {Preference("Emerald Glass · новый скин",
                    "Тёмное стекло, зелёное свечение и новый интерфейс проектов. Pulse остаётся доступен.",
                    trailing={Switch(emerald,onEmerald)})}
                item {
                    Preference("Pulse · молодёжный дизайн",
                        if(emerald)"Сохранён как запасной стиль" else if(pulse)"Зелёные акценты, светлая или тёмная палитра" else "Классическая палитра Material",
                        trailing={Switch(pulse,onPulse)})
                }
                item {Preference("Язык / Language",
                    when(language) {"en"->"English";"ru"->"Русский";else->"System / Как в системе"},
                    {selector="language"})}
                item {Preference("Тема",themeLabel,{selector="theme"})}
                item {Preference("Динамические цвета",if(pulse || emerald)"Не используются в выбранном стиле" else "Палитра обоев Android",trailing={Switch(dynamic,onDynamic,enabled=!pulse && !emerald)})}
                item {HorizontalDivider(Modifier.padding(horizontal=20.dp,vertical=8.dp))}
                item {Preference("Вибрация при нажатии","Тактильный отклик для кнопок и навигации",
                    trailing={Switch(haptics,{v->haptics=v;feedback.setVibrations(v);if(v)feedback.tap(view)})})}
                item {Preference("Звуки нажатий","Системный звук клика, учитывает настройки громкости Android",
                    trailing={Switch(sounds,{v->sounds=v;feedback.setSounds(v);if(v)feedback.tap(view)})})}
                item {DetailRow("Отклик интерфейса","Переключатели работают независимо. Тишина и отключённая системная вибрация также учитываются Android.")}
            }
            "git"->{
                item {Preference("Получение изменений","GitHub → Termux · только безопасный fast-forward. Push и автоматическое разрешение конфликтов недоступны.")}
                item {Preference("Локальные проекты","Worktree сгруппированы по репозиторию. Удаление из списка не удаляет файлы; дополнительные проекты доступны для просмотра.")}
            }
            "profiles"->{
                item {Preference("AnimaQuiz · unit-тесты Debug","main или test/decomposition-v1 · проверенный GitHub SHA",{showCommand=!showCommand})
                    if(showCommand)DetailRow("Команда профиля","bash ./gradlew :app:testDebugUnitTest --offline --no-daemon --rerun-tasks --console=plain")}
                item {Preference("Профили сборки","Debug APK и подписанный Release AAB после проверки GitHub и выбранного JKS")}
            }
            "signing"->item {SigningSettings(context,onOpenBuild)}
            "jobs"->{
                item {Preference("Предупреждать без прогресса","Через $minutes минут",{selector="warning"})}
                item {Preference("Защитный предел TEST","90 минут. Предупреждение не останавливает процесс; UNKNOWN блокирует повтор.")}
            }
            "notifications"->item {NotificationSettings(context)}
            "files"->item {FileSettings(context)}
            "about"->{
                item {Preference("AnimaDev",context.packageManager.getPackageInfo(context.packageName,0).versionName ?: "Версия недоступна")}
                item {Preference("Диагностика","Связь и инструменты — в разделе Termux. Очищенные журналы и отправка отчётов — в истории проекта.")}
                item {Preference("Android","${android.os.Build.VERSION.RELEASE} · API ${android.os.Build.VERSION.SDK_INT}")}
            }
            }
        }
    }
    if(selector.isNotEmpty())AlertDialog(onDismissRequest={selector=""},title={Text(when(selector) {"theme"->"Тема";"language"->"Язык / Language";else->"Предупреждение без прогресса"})},text={Column {
        if(selector=="theme")listOf("system" to "Как в системе","light" to "Светлая","dark" to "Тёмная").forEach {(id,label)->SingleChoiceRow(label,theme==id){onTheme(id);selector=""}}
        else if(selector=="language") listOf("system" to "System / Как в системе","ru" to "Русский","en" to "English").forEach {(id,label)->SingleChoiceRow(label,language==id){onLanguage(id);selector=""}}
        else MonitorPolicy.thresholds.forEach {value->SingleChoiceRow("$value минут",minutes==value){minutes=value;prefs.edit().putInt("warning_minutes",value).apply();selector=""}}
    }},confirmButton={com.animacode.animadev.projects.ActionButton(compact=true,onClick={selector=""}){Text("Отмена")}})
}
@Composable fun FileSettings(context:Context) {
    val scope=rememberCoroutineScope();val destination=remember {ExportDestination(context)}
    var generation by remember {mutableIntStateOf(0)};var error by remember {mutableStateOf("")}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri->
        if(uri!=null)scope.launch {if(!kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){destination.choose(uri)})error="Не удалось сохранить доступ к папке.";generation++}
    }
    val label=remember(generation){destination.label()}
    Column {
        Preference("Папка сохранения",label,{picker.launch(null)})
        Preference("Использовать Загрузки","Вернуть системную папку, сохранив существующие файлы",{destination.useDownloads();generation++;error=""})
        if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(16.dp))
    }
}
