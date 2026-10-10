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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.animacode.animadev.termux.TermuxGateway
import com.animacode.animadev.repos.*
import com.animacode.animadev.projects.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity() {
    private var permitted by mutableStateOf(false)
    private var version by mutableStateOf<String?>(null)
    private var requestedJournal by mutableStateOf("")
    private var requestedRelease by mutableStateOf(false)
    private var requestedBuild by mutableStateOf(false)
    private val termux by lazy { TermuxGateway(this) }
    override fun onResume() {
        super.onResume()
        permitted=termux.permitted()
        version=termux.installedVersion()
        // Restore a previously authorized Pixel, never during a running Gradle job.
        if(permitted && version!=null) AdbDeviceGateway(this).autoRestore()
    }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent);setIntent(intent);requestedJournal=intent.getStringExtra("journal_job") ?: "";requestedRelease=intent.getBooleanExtra("open_release",false);requestedBuild=intent.getBooleanExtra("open_build",false) }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge();requestedJournal=NavigationState.notificationTarget(intent.getStringExtra("journal_job") ?: "",savedInstanceState!=null)
        requestedRelease=intent.getBooleanExtra("open_release",false);requestedBuild=intent.getBooleanExtra("open_build",false);setContent { BetaApp() }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun BetaApp() {
        val context=LocalContext.current
        val feedback=remember {UiFeedback(context)}
        val feedbackView=LocalView.current
        fun clickFeedback(){feedback.tap(feedbackView)}
        val settings=remember { context.getSharedPreferences("interface_v1",MODE_PRIVATE) }
        var theme by remember { mutableStateOf(settings.getString("theme","system") ?: "system") }
        var dynamic by remember { mutableStateOf(settings.getBoolean("dynamic",true)) }
        var pulse by remember { mutableStateOf(settings.getBoolean("pulse",true)) }
        var language by remember {mutableStateOf(settings.getString("language","system") ?: "system")}
        val english=AppLanguage.english(language,LocalConfiguration.current.locales[0].language)
        val dark=AppThemePolicy.dark(theme,isSystemInDarkTheme())
        val colors=if(pulse)pulseColorScheme(dark)
        else if(dynamic) { if(dark)dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }
        else if(dark)darkColorScheme() else lightColorScheme()
        SideEffect { enableEdgeToEdge(SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT){dark},SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT){dark}) }
        CompositionLocalProvider(LocalAppLanguage provides language) {
        MaterialTheme(colorScheme=colors,typography=Typography(
            labelLarge=Typography().labelLarge.copy(textAlign=TextAlign.Center))) {
            val projects=remember { ProjectStore(context) }
            var selected by rememberSaveable { mutableStateOf(projects.selected()) }
            var top by rememberSaveable { mutableStateOf("projects") }
            var route by rememberSaveable { mutableStateOf(NavigationState.freshLaunch(projects.selected()).route) }
            var journalJob by rememberSaveable { mutableStateOf("") }
            var journalParent by rememberSaveable { mutableStateOf("history") }
            // Keep the active subtab outside disposable tool screens.
            // Navigating to Journal removes those screens from composition.
            var selectedTestTab by rememberSaveable(selected) { mutableIntStateOf(0) }
            var selectedBuildTab by rememberSaveable(selected) { mutableIntStateOf(0) }
            var settingsSection by rememberSaveable { mutableStateOf("") }
            var settingsParent by rememberSaveable { mutableStateOf("list") }
            var supportParent by rememberSaveable { mutableStateOf("list") }
            var menu by remember { mutableStateOf(false) }
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
                    if(RepoPolicy.canRecoverJob(r.optBoolean("pending"),r.optString("job_id"),
                        r.optString("job_runner_sha"),r.optString("installed_sha")) &&
                        (restore || (store.blocked() &&
                            System.currentTimeMillis()-r.optLong("last_confirmed")>=5000))) {
                        gateway.query();restore=false
                    }
                    delay(2000)
                }
            }
            // Read-only local HEAD when opening any project tool. This does not
            // fetch, push, switch or alter Git worktrees.
            LaunchedEffect(route,selected,resumed,permitted) {
                if(resumed && permitted && top=="projects" &&
                    route in setOf("workspace","build","branches","git","tests")) {
                    val selectedProject=projects.all().firstOrNull {it.id==selected}
                    if(selectedProject!=null) {
                        val catalog=CatalogGateway(context)
                        catalog.expire()
                        if(!catalog.prefs.getBoolean("pending",false))
                            catalog.read("PATH",selectedProject.path)
                    }
                }
            }
            LaunchedEffect(requestedRelease,requestedBuild) {
                if(requestedRelease || requestedBuild) {
                    val id=if(requestedRelease)ReleaseStore(context).read().optString("project") else store.read().optString("project_id")
                    val target=projects.all().firstOrNull {it.id==id && it.supported}
                    if(target!=null) {
                        selected=target.id;projects.select(target.id);top="projects";route="build"
                    }
                    requestedRelease=false;requestedBuild=false
                }
            }
            LaunchedEffect(requestedJournal) {
                if(requestedJournal.isNotEmpty()) {
                    val savedProject=store.read().optString("project_id","animaquiz")
                    val quiz=projects.all().find { it.id==savedProject && it.supported }
                    if(quiz!=null) { selected=quiz.id;projects.select(quiz.id);journalJob=requestedJournal;journalParent="history";top="projects";route="journal" }
                    requestedJournal=""
                }
            }
            val folderScope=rememberCoroutineScope()
            val destination=remember {ExportDestination(context)}
            var destinationMissing by rememberSaveable {mutableStateOf(false)}
            var destinationError by remember {mutableStateOf("")}
            val folderPicker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) {uri->
                if(uri!=null)folderScope.launch {
                    if(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){destination.choose(uri)}){destinationMissing=false;destinationError=""}
                    else destinationError="Доступ к папке не подтверждён. Выберите другую или используйте Загрузки."
                }
            }
            LaunchedEffect(resumed) {
                if(resumed)destinationMissing=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){DestinationPolicy.needsRecovery(destination.selected(),destination.available())}
            }
            fun back() {
                if(route in setOf("changelog","help","about","termux_setup")) {route=supportParent;return}
                if(route=="settings" && settingsSection.isNotEmpty()){settingsSection="";return}
                val n=NavigationState(top,route,selected,journalJob,settingsParent,journalParent).back()
                if(n==null)confirmExit=true else {top=n.section;route=n.route;journalJob=n.job}
            }
            BackHandler { back() }
            val root=route=="list"
            val title=when {
                route=="changelog"->if(english)"Change log" else "Список изменений"
                route=="help"->if(english)"Documentation" else "Документация"
                route=="about"->if(english)"About" else "О программе"
                route=="termux_setup"->if(english)"Build setup" else "Подготовка сборки"
                route=="settings"->SettingsPolicy.toolbarTitle(settingsSection);top=="termux"->"Termux";route=="list"->"Проекты";route=="branches"->"Ветки";route=="git"->"Git";route=="tests"->"Тесты";route=="build"->"Сборка";route=="journal"->"Журнал";route=="history"->"История";route=="wait"->"Контроль задания";else->current?.name ?: "Проект" }
            Scaffold(
                contentWindowInsets=WindowInsets.safeDrawing,
                topBar={TopAppBar(title={Text(title,maxLines=1,overflow=TextOverflow.Ellipsis)},navigationIcon={if(!root)IconButton(onClick={clickFeedback();back()}){Icon(AppIcons.Back,"Вернуться")}},actions={
                    Box { IconButton(onClick={clickFeedback();menu=true}){Icon(AppIcons.More,"Меню приложения")}
                        DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                            DropdownMenuItem(text={Text(if(english)"Settings" else "Настройки")},leadingIcon={Icon(AppIcons.Settings,null)},onClick={clickFeedback();menu=false;if(route!="settings"){settingsParent=route;settingsSection="";route="settings"}})
                            DropdownMenuItem(text={Text(if(english)"Change log" else "Список изменений")},onClick={clickFeedback();menu=false;supportParent=route;route="changelog"})
                            DropdownMenuItem(text={Text(if(english)"About" else "О программе")},onClick={clickFeedback();menu=false;supportParent=route;route="about"})
                            DropdownMenuItem(text={Text(if(english)"Documentation" else "Документация")},onClick={clickFeedback();menu=false;supportParent=route;route="help"})
                        }
                    }
                })},
                bottomBar={if(root)NavigationBar {
                    NavigationBarItem(selected=top=="projects",onClick={clickFeedback();top="projects";route="list";journalJob=""},icon={Icon(AppIcons.Folder,null)},label={Text("Проекты")})
                    NavigationBarItem(selected=top=="termux",onClick={clickFeedback();top="termux";route="list";journalJob=""},icon={Icon(AppIcons.Terminal,null)},label={Text("Termux")})
                }}
            ) { padding -> Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                when {
                    route in setOf("changelog","help","about","termux_setup")->SupportScreen(context,route)
                    route=="settings"->AppearanceSettings(
                        context,theme,dynamic,
                        {theme=it;settings.edit().putString("theme",it).apply()},
                        {dynamic=it;settings.edit().putBoolean("dynamic",it).apply()},
                        settingsSection,{settingsSection=it},
                        pulse=pulse,onPulse={value->pulse=value;settings.edit().putBoolean("pulse",value).apply()},
                        language=language,onLanguage={value->language=value;settings.edit().putString("language",value).apply()},
                        onOpenBuild={projectId->
                            val project=projects.all().firstOrNull {it.id==projectId && it.supported}
                            if(project!=null) {
                                projects.select(project.id)
                                selected=project.id
                                top="projects"
                                settingsSection=""
                                route="build"
                            }
                        })
                    top=="termux"->TermuxOverview(context,permitted,version,
                        onPermissionUpdated={permitted=termux.permitted()},
                        onOpenSetup={supportParent="list";route="termux_setup"})
                    route=="list" || current==null->ProjectsScreen(context,permitted,onOpen={p->clickFeedback();projects.select(p.id);selected=p.id;route="workspace";journalJob=""})
                    route=="branches"->BranchesScreen(context,current,permitted)
                    route=="workspace"->ProjectWorkspace(context,current,
                        onTool={ id->
                            clickFeedback()
                            when(id) {
                                "tests_device"->{selectedTestTab=1;route="tests"}
                                "tests_unit"->{selectedTestTab=0;route="tests"}
                                "build_debug"->{selectedBuildTab=0;route="build"}
                                "build_release"->{selectedBuildTab=1;route="build"}
                                else->route=id
                            }
                        },permitted=permitted,
                        onJournal={id->journalJob=id;journalParent="workspace";route="journal"})
                    route=="build"->BuildScreen(context,current,permitted,version,{permitted=termux.permitted()},{id->journalJob=id;journalParent="build";route="journal"},
                        selectedBuildTab,{selectedBuildTab=it})
                    route=="git" || route=="tests"->ProjectTools(context,current,route,permitted,version,{permitted=termux.permitted()},{id->journalJob=id;journalParent=route;route="journal"},{route="wait"},
                        selectedTestTab,{selectedTestTab=it})
                    route=="history"->if(current.supported)HistoryScreen(context,current,{id->journalJob=id;journalParent="history";route="journal"}) else UnavailableTool("История", "У этого проекта пока нет заданий AnimaDev.")
                    route=="journal"->if(current.supported)JournalScreen(context,journalJob) else UnavailableTool("Журнал","Нет безопасного профиля этого проекта.")
                    route=="wait"->MonitorSettings(context)
                    else->ProjectWorkspace(context,current,onTool={route=it})
                }
            } }
            if(destinationMissing)AlertDialog(onDismissRequest={destinationMissing=false},title={Text("Папка сохранения недоступна")},text={Text(destinationError.ifEmpty {"Выберите папку повторно или вернитесь к обычным Загрузкам. История и файлы сохранятся."})},confirmButton={Button(onClick={folderPicker.launch(null)}){Text("Выбрать папку")}},dismissButton={OutlinedButton(onClick={destination.useDownloads();destinationMissing=false}){Text("Использовать Загрузки")}})
            if(confirmExit)AlertDialog(onDismissRequest={confirmExit=false},title={Text("Закрыть AnimaDev?")},text={Text(if(store.blocked())"Задание продолжится в Termux. Состояние и журнал восстановятся при следующем открытии." else "Проекты, результаты и журналы сохранятся.")},confirmButton={com.animacode.animadev.projects.ActionButton(compact=true,onClick={confirmExit=false;finishAndRemoveTask()}){Text("Выйти")}},dismissButton={com.animacode.animadev.projects.ActionButton(compact=true,onClick={confirmExit=false}){Text("Отмена")}})
        }
        }
    }
}
