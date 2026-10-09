package com.animacode.animadev

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.animacode.animadev.termux.TermuxGateway
import com.animacode.animadev.repos.*
import com.animacode.animadev.projects.*
import kotlinx.coroutines.delay

class MainActivity:ComponentActivity() {
    private var permitted by mutableStateOf(false)
    private var version by mutableStateOf<String?>(null)
    private var requestedJournal by mutableStateOf("")
    private val termux by lazy { TermuxGateway(this) }
    override fun onResume() { super.onResume();permitted=termux.permitted();version=termux.installedVersion() }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent);setIntent(intent);requestedJournal=intent.getStringExtra("journal_job") ?: "" }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge();requestedJournal=intent.getStringExtra("journal_job") ?: ""
        setContent { BetaApp() }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun BetaApp() {
        val context=LocalContext.current
        val settings=remember { context.getSharedPreferences("interface_v1",MODE_PRIVATE) }
        var theme by remember { mutableStateOf(settings.getString("theme","system") ?: "system") }
        var dynamic by remember { mutableStateOf(settings.getBoolean("dynamic",true)) }
        val dark=when(theme) { "dark"->true;"light"->false;else->isSystemInDarkTheme() }
        val colors=if(dynamic) { if(dark)dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) } else if(dark)darkColorScheme() else lightColorScheme()
        SideEffect { enableEdgeToEdge(SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT){dark},SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT){dark}) }
        MaterialTheme(colorScheme=colors) {
            val projects=remember { ProjectStore(context) }
            var selected by rememberSaveable { mutableStateOf(projects.selected()) }
            var top by rememberSaveable { mutableStateOf("projects") }
            var route by rememberSaveable { mutableStateOf(if(projects.selected().isEmpty())"list" else "workspace") }
            var journalJob by rememberSaveable { mutableStateOf("") }
            var showSettings by rememberSaveable { mutableStateOf(false) }
            var confirmExit by rememberSaveable { mutableStateOf(false) }
            var projectGeneration by remember { mutableIntStateOf(0) }
            val current=remember(selected,projectGeneration) { projects.all().find { it.id==selected } }
            val store=remember { RepoStore(context) };val gateway=remember { RepoGateway(context) }
            val owner=LocalLifecycleOwner.current;var resumed by remember { mutableStateOf(false) }
            DisposableEffect(owner) {
                val observer=LifecycleEventObserver { _,_->resumed=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
                owner.lifecycle.addObserver(observer);resumed=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_->projectGeneration++ }
                projects.prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { owner.lifecycle.removeObserver(observer);projects.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            LaunchedEffect(resumed,permitted) {
                if(!resumed || !permitted)return@LaunchedEffect
                var restore=true
                while(true) {
                    store.expire();CatalogGateway(context).expire()
                    val r=store.read()
                    if(!r.optBoolean("pending") && r.optBoolean("ready") && r.optString("job_id").isNotEmpty() && (restore || (store.blocked() && System.currentTimeMillis()-r.optLong("last_confirmed")>=5000))) { gateway.query();restore=false }
                    delay(2000)
                }
            }
            LaunchedEffect(requestedJournal) {
                if(requestedJournal.isNotEmpty()) {
                    val quiz=projects.all().find { it.supported }
                    if(quiz!=null) { selected=quiz.id;projects.select(quiz.id);journalJob=requestedJournal;top="projects";route="history" }
                }
            }
            fun back() { if(top=="termux")top="projects" else when(route) { "git","tests","build","history","wait"->route="workspace";"workspace"->route="list";else->confirmExit=true } }
            BackHandler(enabled=!showSettings) { back() }
            val title=when { top=="termux"->"Termux";route=="list"->"Проекты";route=="git"->"Git";route=="tests"->"Тесты";route=="build"->"Сборка";route=="history"->"История";route=="wait"->"Контроль задания";else->current?.name ?: "Проект" }
            Scaffold(
                contentWindowInsets=WindowInsets.safeDrawing,
                topBar={TopAppBar(title={Text(title,maxLines=1,overflow=TextOverflow.Ellipsis)},navigationIcon={if(top=="projects" && route!="list")TextButton(onClick={back()}) { Text("Назад") }},actions={TextButton(onClick={showSettings=true}) { Text("Вид") }})},
                bottomBar={NavigationBar {
                    NavigationBarItem(selected=top=="projects",onClick={top="projects"},icon={Icon(BetaIcons.Folder,null)},label={Text("Проекты")})
                    NavigationBarItem(selected=top=="termux",onClick={top="termux"},icon={Icon(BetaIcons.Terminal,null)},label={Text("Termux")})
                }}
            ) { padding -> Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                when {
                    top=="termux"->TermuxOverview(context,permitted,version) { permitted=termux.permitted() }
                    route=="list" || current==null->ProjectsScreen(context,permitted,onOpen={p->projects.select(p.id);selected=p.id;route="workspace"})
                    route=="workspace"->ProjectWorkspace(context,current,onTool={route=it})
                    route=="git" || route=="tests"->ProjectTools(context,current,route,permitted,version,{permitted=termux.permitted()},{id->journalJob=id;route="history"},{route="wait"})
                    route=="history"->if(current.supported)JournalScreen(context,journalJob) else UnavailableTool("История", "У этого проекта ещё нет заданий AnimaDev. Файлы сторонних сеансов Termux не читаются.")
                    route=="wait"->MonitorSettings(context)
                    else->UnavailableTool("Сборка APK / AAB","В этой Beta сборка ещё не подключена. Debug APK будет отдельным следующим этапом.")
                }
            } }
            if(showSettings) ModalBottomSheet(onDismissRequest={showSettings=false}) {
                Column(Modifier.padding(horizontal=24.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Оформление",style=MaterialTheme.typography.titleLarge)
                    listOf("system" to "Как в системе","light" to "Светлая","dark" to "Тёмная").forEach { (id,label)->
                        SingleChoiceRow(label,theme==id) { theme=id;settings.edit().putString("theme",id).apply() }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("Динамические цвета",Modifier.weight(1f).padding(top=12.dp));Switch(checked=dynamic,onCheckedChange={dynamic=it;settings.edit().putBoolean("dynamic",it).apply()}) }
                    Text("AnimaDev 0.4.0 Beta",style=MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                }
            }
            if(confirmExit)AlertDialog(onDismissRequest={confirmExit=false},title={Text("Закрыть AnimaDev?")},text={Text(if(store.blocked())"Задание продолжится в Termux. Состояние и журнал восстановятся при следующем открытии." else "Проекты, результаты и журналы сохранятся.")},confirmButton={TextButton(onClick={confirmExit=false;finishAndRemoveTask()}){Text("Выйти")}},dismissButton={TextButton(onClick={confirmExit=false}){Text("Отмена")}})
        }
    }
}
