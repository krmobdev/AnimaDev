package com.animacode.animadev.projects

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.animacode.animadev.repos.*

@Composable fun ProjectTools(context:Context,project:LocalProject,tool:String,permitted:Boolean,termuxVersion:String?,onPermissionUpdated:()->Unit,onJournal:(String)->Unit,onWait:()->Unit,
    selectedTestTab:Int?=null,onTestTabChange:((Int)->Unit)?=null) {
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val neon=LocalNeonSkin.current
    if(project.localOnly && tool=="git") {
        LocalOnlyGitTools(context,project,permitted,termuxVersion,onJournal)
        return
    }
    if(!project.supported) {
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(project.name,style=MaterialTheme.typography.titleMedium);Text(if(project.worktree)"Worktree · только просмотр" else "Только просмотр",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}
            item {Card {DetailRow("Ветка",project.branch.ifEmpty {"Не проверено"});DetailRow("Коммит",project.commit.take(8).ifEmpty {"Не проверено"});DetailRow("Origin",project.remote.ifEmpty {"Не проверено"});DetailRow("Каталог",project.path.removePrefix(ProjectPolicy.HOME));DetailRow("Рабочее дерево",when(project.dirty){true->"Локальные изменения";false->"Чисто";else->"Не проверено"})} }
            item {Text(project.error.ifEmpty {"Профиль запуска для этого проекта ещё не подключён. Операции другого репозитория не выполняются."},style=MaterialTheme.typography.bodySmall)}
        }
        return
    }
    val gateway=remember {RepoGateway(context)};val r=rememberRepoRecord(context);val store=remember {RepoStore(context)}
    val adb=remember {AdbDeviceGateway(context)}
    var adbVersion by remember {mutableIntStateOf(0)}
    var adbEndpoint by rememberSaveable(project.id) { mutableStateOf(adb.rememberedEndpoint()) }
    var deviceActionMessage by rememberSaveable(project.id) { mutableStateOf("") }
    LaunchedEffect(adb) {
        while(true) {
            kotlinx.coroutines.delay(1500)
            adb.expire()
        }
    }
    DisposableEffect(adb) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_->adbVersion++ }
        adb.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {adb.prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    adb.expire()
    // Observing adbVersion is essential: the Termux callback updates
    // SharedPreferences off-screen, so Compose must re-read those values.
    val adbState=remember(adbVersion) {
        adb.prefs.getString("message","Устройство ещё не проверено.").orEmpty()
    }
    val adbPending=remember(adbVersion) {adb.prefs.getBoolean("pending",false)}
    val deviceReady=remember(adbVersion) {adb.recentDevice()}
    val belongs=r.optString("project_id","animaquiz")==project.id
    val snap=if(belongs)r.optJSONObject("snapshot")?.takeIf {
        it.optString("branch")==project.branch && it.optString("local_commit")==project.commit
    } else null
    val pending=r.optBoolean("pending");val blocked=store.blocked();val ready=r.optBoolean("ready")
    val historyFull=RepoGateway.historyCapacityFailure(r)
    val recoveryUpgrade=belongs && gateway.mayUpgradeRecoveryHelper(r)
    val releaseGeneration=ReleaseStore.generation.intValue
    val releaseBusy=remember(releaseGeneration) { ReleaseStore(context).busy() }
    val available=HelperPolicy.mayRun(permitted,termuxVersion!=null,ready,pending,blocked)
    // Recovery is safe even when a previous job is UNKNOWN: QUERY never starts Gradle.
    val activeOrUnknown=blocked || r.optString("state")=="UNKNOWN"
    // A queued request is only a receipt. Once it is running, the monitor owns
    // progress feedback; never leave a stale "request recorded" success banner.
    LaunchedEffect(belongs,r.optString("job_id"),r.optString("state")) {
        if(belongs && r.optString("state") in setOf("QUEUED","RUNNING","SUCCEEDED",
                "FAILED","INTERRUPTED","TIMED_OUT") &&
            deviceActionMessage.startsWith("Запрос записан")) deviceActionMessage=""
    }
    var adbDetails by rememberSaveable(project.id) {mutableStateOf(!deviceReady)}
    var fallbackTestTab by rememberSaveable(project.id) {mutableIntStateOf(0)}
    val testTab=selectedTestTab ?: fallbackTestTab
    val toolsListState=rememberLazyListState()
    LaunchedEffect(testTab,r.optString("job_id"),tool) {
        if(belongs && r.optString("job_id").isNotBlank() && tool=="tests") {
            when(r.optString("action")) {
                "TEST" -> if(testTab==0) {kotlinx.coroutines.delay(250);toolsListState.scrollToItem((toolsListState.layoutInfo.totalItemsCount-1).coerceAtLeast(0))}
                "TEST_DEVICE" -> if(testTab==1) {kotlinx.coroutines.delay(250);toolsListState.scrollToItem((toolsListState.layoutInfo.totalItemsCount-1).coerceAtLeast(0))}
            }
        }
    }
    var confirmSync by rememberSaveable {mutableStateOf(false)};var changedFiles by rememberSaveable {mutableStateOf(false)}
    var gitDetails by rememberSaveable(project.id) {mutableStateOf(false)}
    val grant=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {onPermissionUpdated()}
    LazyColumn(Modifier.fillMaxSize(),state=toolsListState,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            if(neon)NeonPanel(Modifier.fillMaxWidth(),glowing=true) {
                NeonSection(if(tool=="git")"Git" else "Tests",
                    if(tool=="git")AppIcons.Git else AppIcons.Tests,project.name)
                NeonKeyValue("Branch",project.branch.ifBlank {"Not verified"},AppIcons.Git)
                NeonKeyValue("Commit",project.commit.take(12).ifBlank {"Not verified"},AppIcons.Info)
            }
            else if(tool=="git")Text(project.name,style=MaterialTheme.typography.headlineMedium)
            else OperationProjectHeader(project)
        }
        if(tool!="git")item {
            if(neon)NeonTabs(listOf("Unit tests","On device"),testTab,onSelect={
                if(onTestTabChange!=null)onTestTabChange(it) else fallbackTestTab=it
            }) else OperationTabs(listOf("Unit-тесты","На устройстве"),testTab) {
                if(onTestTabChange!=null)onTestTabChange(it) else fallbackTestTab=it
            }
        }
        if(!permitted)item {Button(onClick={grant.launch(com.animacode.animadev.termux.TermuxGateway.PERMISSION)}){Text("Разрешить Termux")} }
        if(termuxVersion==null)item {Text("Termux не найден. Действия недоступны.",color=MaterialTheme.colorScheme.error)}
        if(!ready)item {Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)) {Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            val installed=r.optString("installed_sha").isNotEmpty()
            Text(if(installed)"Доступно обновление локального помощника" else "Нужен локальный помощник Termux",style=MaterialTheme.typography.titleSmall)
            Text(if(historyFull)"Журнал Termux заполнен. Обновите помощника: старые задания и журналы сохранятся. Затем подтвердите статус прежнего ID."
                else if(recoveryUpgrade)"Старый CHECK не подтверждён. Обновите локального помощника, затем проверьте прежний Job ID. Повторного запуска не будет."
                else if(blocked)"Сначала уточните прежнее задание по Job ID. Обновление сейчас заблокировано." else "Результаты и история сохранятся. После подключения обновите состояние Git.",style=MaterialTheme.typography.bodySmall)
            com.animacode.animadev.projects.ActionButton(onClick={gateway.install()},enabled=HelperPolicy.mayConnect(permitted,termuxVersion!=null,pending,blocked && !historyFull && !recoveryUpgrade)){Text(if(pending)"Подключаем…" else HelperPolicy.label(installed))}
        }} }
        if(TestScreenStatusPolicy.needsRecovery(r.optString("state")) &&
            r.optString("job_id").isNotBlank()) item {
            OutlinedCard(modifier=Modifier.fillMaxWidth(),
                colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.errorContainer.copy(alpha=0.27f))) {
                Column(Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Восстановить состояние задания",style=MaterialTheme.typography.titleMedium)
                    Text("Операция: "+r.optString("action")+
                        " · "+r.optString("state")+
                        "\nJob ID: "+r.optString("job_id").take(12),
                        style=MaterialTheme.typography.bodySmall)
                    Text("Termux может уже завершить задачу, пока Android показывает старый статус. "+
                        "Проверка читает сохранённый результат по ID и не запускает Gradle повторно.",
                        style=MaterialTheme.typography.bodyMedium)
                    val queryAllowed=RepoPolicy.canRecoverJob(pending,
                        r.optString("job_id"),r.optString("job_runner_sha"),
                        r.optString("installed_sha"))
                    Button(onClick={gateway.query(true)},enabled=queryAllowed && permitted && termuxVersion!=null &&
                        (!historyFull || ready) && (!recoveryUpgrade || ready),modifier=Modifier.fillMaxWidth()) {
                        Text("Проверить статус в Termux")
                    }
                    if((historyFull || recoveryUpgrade) && !ready)
                        Text("Сначала обновите помощника кнопкой выше. Старое задание не перезапускается.",style=MaterialTheme.typography.bodySmall)
                    if(pending)Text("Ожидаем предыдущий ответ Termux. Если он потерялся, "+
                        "через 15 секунд станет доступен повторный запрос.",
                        style=MaterialTheme.typography.bodySmall)
                    if(r.optString("query_result").isNotBlank())
                        Text(r.optString("query_result"),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        if(tool=="git") {
            item {
                val gitSnap=if(belongs)r.optJSONObject("snapshot") else null
                val checked=gitSnap?.optBoolean("checked")==true
                val localSha=gitSnap?.optString("local_commit").orEmpty()
                val remoteSha=gitSnap?.optString("remote_commit").orEmpty()
                val branch=gitSnap?.optString("branch").orEmpty()
                val dirty=gitSnap?.optBoolean("dirty")==true
                val remoteOk=gitSnap?.optBoolean("remote_ok")==true
                val branchOk=gitSnap?.optBoolean("branch_allowed")==true
                val behind=gitSnap?.optInt("behind") ?: 0
                val ahead=gitSnap?.optInt("ahead") ?: 0
                val jobAction=if(belongs)r.optString("action") else ""
                val jobState=if(belongs)r.optString("state") else ""
                val gitJob=jobAction in setOf("STATUS","CHECK","SYNC") && r.optString("job_id").isNotEmpty()
                val working=gitJob && jobState in RepoPolicy.activeStates
                val justSynced=jobAction=="SYNC" && jobState=="SUCCEEDED"
                val stale=gitSnap!=null && project.commit.isNotBlank() &&
                    localSha.isNotBlank() && localSha!=project.commit && !justSynced
                val canReceive=available && !stale && gateway.maySync(r,project.id)
                val headline=GitScreenStatus.headline(
                    working=working,state=jobState,hasJob=gitJob,
                    hasSnapshot=gitSnap!=null,branchAllowed=branchOk,originOk=remoteOk,
                    dirty=dirty,checked=checked,ahead=ahead,behind=behind,
                    justSynced=justSynced)
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text("СИНХРОНИЗАЦИЯ С GITHUB",style=MaterialTheme.typography.labelMedium,
                                color=MaterialTheme.colorScheme.primary,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(headline,style=MaterialTheme.typography.titleLarge,
                                fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
                            if(working)LinearProgressIndicator(Modifier.fillMaxWidth())
                            HorizontalDivider()
                            Text("Ветка: "+branch.ifBlank {project.branch.ifBlank {"Не проверена"}},
                                style=MaterialTheme.typography.bodyMedium)
                            Text("Локальный SHA: "+localSha.take(12).ifBlank {"Не проверен"},
                                style=MaterialTheme.typography.bodyMedium)
                            Text("GitHub SHA: "+(if(checked)remoteSha.take(12) else "Не проверен"),
                                style=MaterialTheme.typography.bodyMedium)
                            Text("Origin: "+when {gitSnap==null->"Не проверен";remoteOk->"Подтверждён";else->"Не совпадает"},
                                style=MaterialTheme.typography.bodyMedium)
                            Text("Файлы: "+when {gitSnap==null->"Не проверены";dirty->"Есть изменения";else->"Чисто"},
                                style=MaterialTheme.typography.bodyMedium)
                            if(checked && localSha.isNotBlank() && localSha==remoteSha) {
                                Text("Коммиты синхронизированы с GitHub. Это не означает, что рабочие файлы неизменны.",
                                    style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if(dirty) {
                                val files=gitSnap?.optJSONArray("changed_files")
                                val count=files?.length() ?: 0
                                Text("Незакоммиченные локальные изменения · файлов: "+count,
                                    style=MaterialTheme.typography.bodyMedium,
                                    color=MaterialTheme.colorScheme.error)
                                if(count in 1..3) {
                                    for(i in 0 until count) {
                                        Text(files?.optString(i).orEmpty(),
                                            style=MaterialTheme.typography.bodySmall,
                                            color=MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines=3,overflow=TextOverflow.Ellipsis)
                                    }
                                }
                                Text("Проверка GitHub не удаляет локальные правки и не отправляет их на сервер. Для получения коммитов нужно чистое рабочее дерево.",
                                    style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                                com.animacode.animadev.projects.ActionButton(onClick={changedFiles=true}) {
                                    Text("Показать все изменённые файлы")
                                }
                            }
                            Text(if(checked)"Коммитов на GitHub: +"+behind+" · локально: +"+ahead
                                else "Для сравнения коммитов нужна проверка GitHub.",
                                style=MaterialTheme.typography.bodyMedium)
                            if(stale)Text("Каталог и ответ Termux расходятся по SHA. Повторите проверку.",
                                color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                            Text(when {
                                working->"Следующий шаг: дождаться завершения операции."
                                jobState in setOf("FAILED","TIMED_OUT","INTERRUPTED","UNKNOWN") && gitJob ->
                                    "Следующий шаг: посмотреть код ошибки и журнал ниже."
                                gitSnap==null || !checked->"Следующий шаг: проверить обновления."
                                dirty->"Перед обновлением сохраните локальные изменения самостоятельно."
                                ahead>0->"Есть локальные коммиты: автоматическое объединение запрещено."
                                behind>0 && canReceive->"Следующий шаг: получить изменения по кнопке ниже."
                                behind>0->"Получение пока заблокировано; причины указаны ниже."
                                else->"Получать нечего. Для нового APK откройте раздел «Сборка»."
                            },style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    com.animacode.animadev.projects.ActionButton(
                        onClick={gateway.start("CHECK",project.id,project.path)},
                        enabled=available,
                        colors=ButtonDefaults.outlinedButtonColors(
                            containerColor=MaterialTheme.colorScheme.primary,
                            contentColor=MaterialTheme.colorScheme.onPrimary),
                        modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                        Icon(AppIcons.Refresh,null);Spacer(Modifier.width(10.dp))
                        Text(if(working)"Ожидаем ответ Termux…" else if(checked)"Повторить проверку" else "Проверить обновления")
                    }
                    Text("Порядок: 1. Проверка ветки, origin и файлов. 2. Получение SHA с GitHub. "+
                        "3. Сравнение коммитов. Исходники на этом этапе не изменяются.",
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(checked && behind>0) {
                        com.animacode.animadev.projects.ActionButton(onClick={confirmSync=true},
                            enabled=canReceive,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                            Icon(AppIcons.Git,null);Spacer(Modifier.width(10.dp))
                            Text("Получить "+behind+" "+GitScreenStatus.commitWord(behind))
                        }
                        Text(if(canReceive)"Получение только по подтверждению. Termux повторно проверит безопасность."
                            else when {
                                dirty->"Получение заблокировано: незакоммиченные файлы."
                                ahead>0->"Получение заблокировано: есть локальные коммиты."
                                !branchOk->"Ветка не разрешена профилем."
                                !remoteOk->"Origin не соответствует профилю."
                                stale->"Повторите проверку: SHA проекта изменился."
                                else->"Fast-forward сейчас недоступен."
                            },style=MaterialTheme.typography.bodySmall,
                            color=if(canReceive)MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    }
                    if(gitJob) {
                        OutlinedCard {
                            Column(Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("РЕЗУЛЬТАТ ПОСЛЕДНЕЙ ОПЕРАЦИИ",
                                    style=MaterialTheme.typography.labelLarge,
                                    color=MaterialTheme.colorScheme.primary)
                                Text("Действие: "+when(jobAction) {"CHECK"->"Проверка GitHub";"STATUS"->"Локальная диагностика";else->"Получение изменений"})
                                Text("Состояние: "+when(jobState) {"SUCCEEDED"->"Успешно";"UNKNOWN"->"Неизвестно";"FAILED","TIMED_OUT","INTERRUPTED"->"Ошибка";else->"Выполняется"})
                                if(r.optString("message").isNotEmpty())Text(r.optString("message"))
                                val error=r.optJSONObject("job")?.optString("error_code").orEmpty()
                                if(error.isNotBlank())Text("Код ошибки: "+error,color=MaterialTheme.colorScheme.error)
                                val exit=r.optJSONObject("job")
                                if(exit!=null && !exit.isNull("exit_code"))Text("Exit code: "+exit.optInt("exit_code"))
                                val checkedAt=r.optLong("last_confirmed")
                                if(checkedAt>0L)Text("Ответ Termux: "+
                                    java.text.SimpleDateFormat("dd.MM HH:mm:ss",locale)
                                        .format(java.util.Date(checkedAt)),style=MaterialTheme.typography.bodySmall)
                                com.animacode.animadev.projects.ActionButton(onClick={onJournal(r.optString("job_id"))}) {
                                    Text("Открыть подробный журнал")
                                }
                            }
                        }
                    }
                    OutlinedCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth().clickable {gitDetails=!gitDetails},
                                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Технические сведения",modifier=Modifier.weight(1f),
                                    style=MaterialTheme.typography.titleSmall)
                                Text(if(gitDetails)"Скрыть −" else "Показать +",
                                    color=MaterialTheme.colorScheme.primary)
                            }
                            if(gitDetails) {
                                Text("Путь: "+project.path)
                                Text("Полная локальная SHA: "+localSha.ifBlank {"Не проверена"})
                                Text("Полная GitHub SHA: "+(if(checked)remoteSha else "Не проверена"))
                                if(gitJob)Text("Job ID: "+r.optString("job_id"))
                                if(dirty)com.animacode.animadev.projects.ActionButton(onClick={changedFiles=true}) {
                                    Text("Изменённые файлы")
                                }
                                Text("Локальная диагностика не обращается к GitHub и не изменяет исходники.",
                                    style=MaterialTheme.typography.bodySmall)
                                com.animacode.animadev.projects.ActionButton(
                                    onClick={gateway.start("STATUS",project.id,project.path)},
                                    enabled=available) {Text("Проверить только локальные файлы")}
                            }
                        }
                    }
                }
            }
        } else {
            val prepared=belongs && snap!=null && gateway.mayTest(r,project.id)
            val canRun=available && prepared
            val canRunDevice=DeviceTestLaunchPolicy.canStart(permitted,termuxVersion!=null,ready,
                pending,blocked,adbPending,releaseBusy)
            val needStatus=r.optString("project_id","animaquiz")!=project.id || snap==null
            if(testTab==0)item {
                Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(23.dp),
                    color=if(neon)NeonGlass.surface else MaterialTheme.colorScheme.surfaceContainer,
                    border=androidx.compose.foundation.BorderStroke(1.dp,
                        if(neon)NeonGlass.outline else MaterialTheme.colorScheme.outlineVariant),
                    modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(neon)NeonSection("Run unit tests",AppIcons.Tests,
                            "Gradle · :app:testDebugUnitTest")
                        else Text("Unit-тесты",style=MaterialTheme.typography.titleLarge)
                        Text("Gradle · :app:testDebugUnitTest",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TestOperationResult(context,r,project,"TEST",permitted,onJournal)
                        if(!ready) {
                            Text("Сначала обновите локальный помощник Termux (кнопка выше).",
                                color=MaterialTheme.colorScheme.error)
                        } else if(blocked && belongs) {
                            Text(TestScreenStatusPolicy.unitBlockedMessage(
                                r.optString("action"),r.optString("state")),
                                style=MaterialTheme.typography.bodyMedium,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if(needStatus) {
                            Text((if(project.localOnly) "Для unit-тестов сначала проверьте локальный Git. " else "Для unit-тестов сначала проверьте ветку и origin. ") +
                                "Инструментальные тесты проверяют Git автоматически.",
                                style=MaterialTheme.typography.bodyMedium)
                        } else if(!prepared) {
                            Text(when {
                                blocked->"Предыдущее задание ещё выполняется или его статус неизвестен."
                                !project.localOnly && !snap.optBoolean("remote_ok")->"Origin не подтверждён для этого проекта."
                                else->"Состояние проекта изменилось. Обновите локальный снимок."
                            },style=MaterialTheme.typography.bodyMedium)
                        }
                        if((!prepared || needStatus) && !blocked) {
                            OutlinedButton(onClick={gateway.start("STATUS",project.id,project.path)},
                                enabled=available,modifier=Modifier.fillMaxWidth()) {
                                Icon(AppIcons.Refresh,null)
                                Spacer(Modifier.width(8.dp))
                                Text("Подготовить тесты · проверить проект")
                            }
                        }
                        if(neon)NeonPrimaryAction(
                            if(blocked)"Waiting for available slot" else "Run unit tests",
                            "Verify the current source revision",AppIcons.Tests,
                            enabled=canRun,onClick={gateway.start("TEST",project.id,project.path)})
                        else Button(onClick={gateway.start("TEST",project.id,project.path)},
                            enabled=canRun,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                            Icon(AppIcons.Tests,null)
                            Spacer(Modifier.width(8.dp))
                            Text(if(blocked) "Unit-тесты ожидают свободный слот"
                                 else "Запустить unit-тесты")
                        }
                    }
                }
            }
            if(testTab==1)item {
                Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(23.dp),
                    color=if(neon)NeonGlass.surface else MaterialTheme.colorScheme.surfaceContainer,
                    border=androidx.compose.foundation.BorderStroke(1.dp,
                        if(neon)NeonGlass.outline else MaterialTheme.colorScheme.outlineVariant),
                    modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(neon)NeonSection("Instrumentation tests",AppIcons.Tests,
                            "Run tests on a real device or emulator")
                        else Text("Инструментальные тесты",style=MaterialTheme.typography.titleLarge)
                        if(project.id=="animaquiz")Text("Обычный набор · OS backup/restore проверяется отдельно",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Реальное устройство · ADB · :app:connectedDebugAndroidTest",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TestOperationResult(context,r,project,"TEST_DEVICE",permitted,onJournal)
                        if(neon)NeonStatus(if(deviceReady)"Device ready" else "Device not verified",deviceReady)
                        Text(adbState,style=MaterialTheme.typography.bodyMedium,
                            color=if(deviceReady)MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        ActionButton(onClick={adbDetails=!adbDetails}) {
                            Text(if(adbDetails)"Скрыть подключение ADB" else "Настроить подключение ADB")
                        }
                        if(adbDetails) {
                        Text("Для Wi-Fi укажи IP:порт с основного экрана беспроводной отладки Pixel. "+
                            "Это не порт из окна сопряжения.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(value=adbEndpoint,onValueChange={adbEndpoint=it.take(45)},
                            singleLine=true,label={Text("IP:порт подключения Pixel")},
                            placeholder={Text("192.168.1.132:37123")},
                            modifier=Modifier.fillMaxWidth(),
                            enabled=!adbPending)
                        // Never silently disable the button for a pasted address:
                        // validation remains strict in AdbDeviceGateway.connect().
                        if(adbEndpoint.isNotBlank() &&
                           !AdbDeviceGateway.validLanEndpoint(adbEndpoint)) {
                            Text("Адрес нужно проверить: укажите локальный IPv4 и порт от 1024 до 65535. "+
                                "После нажатия появится точная причина отказа.",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.error)
                        }
                        Button(onClick={adb.connect(adbEndpoint)},
                            enabled=permitted && termuxVersion!=null && !adbPending &&
                                adbEndpoint.isNotBlank(),
                            modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                            Text(if(adbPending)"Соединяемся…" else "Подключить Pixel через ADB")
                        }
                        if(!permitted || termuxVersion==null)
                            Text("Для подключения требуется разрешённый Termux RUN_COMMAND. "+
                                "Откройте настройки интеграции Termux.",
                                style=MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick={adb.refresh()},
                            enabled=permitted && termuxVersion!=null && !adbPending,
                            modifier=Modifier.fillMaxWidth()) {
                            Icon(AppIcons.Refresh,null)
                            Spacer(Modifier.width(8.dp))
                            Text(if(adbPending)"Проверяем ADB…" else "Проверить устройства · adb devices")
                        }
                        }
                        // The device runner checks the branch, origin, clean worktree,
                        // GitHub SHA and ADB immediately before running Gradle. It does
                        // not need the separate Unit-tests STATUS screen or cached ADB age.
                        if(neon)NeonPrimaryAction(
                            if(blocked)"Waiting for free build slot" else "Run instrumentation tests",
                            "Git and ADB verified at launch",AppIcons.Tests,
                            enabled=canRunDevice,onClick={
                                deviceActionMessage=gateway.startDeviceWithFeedback(project.id,project.path)
                            })
                        else Button(onClick={
                            deviceActionMessage=gateway.startDeviceWithFeedback(project.id,project.path)
                        },enabled=canRunDevice,
                            modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                            Icon(AppIcons.Tests,null)
                            Spacer(Modifier.width(8.dp))
                            Text(if(belongs && TestScreenStatusPolicy.active(r.optString("state")) &&
                                r.optString("action")=="TEST_DEVICE") "Тесты уже выполняются"
                                 else if(blocked) "Ожидаем свободный слот"
                                 else "Запустить инструментальные тесты")
                        }
                        if(deviceActionMessage.isNotBlank()) {
                            Text(deviceActionMessage,style=MaterialTheme.typography.bodyMedium,
                                color=if(deviceActionMessage.startsWith("Запрос записан"))
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        Text(if(deviceReady)
                            "ADB подтвердил Pixel. Перед запуском Gradle устройство будет проверено ещё раз."
                            else "Pixel пока не подтверждён. Подключи его через ADB; "+
                                "перед Gradle Termux самостоятельно проверит устройство.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text((if(project.localOnly) "Локальная ветка и рабочие файлы проверяются " else "GitHub, ветка и чистота проекта проверяются автоматически ") +
                            "при запуске. Переходить в Unit-тесты не нужно.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(!canRunDevice && !adbPending && !activeOrUnknown) {
                            Text(when {
                                !permitted -> "Нет разрешения на запуск команд в Termux."
                                termuxVersion==null -> "Termux не найден."
                                !ready -> "Обнови помощник Termux кнопкой выше."
                                releaseBusy -> "Release-сборка занимает очередь. Проверь её статус."
                                else -> "Сейчас выполняется другое задание. Дождись завершения."
                            },style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        if(tool=="git" && belongs && r.optString("job_id").isNotEmpty() &&
            (blocked || r.optString("state")=="UNKNOWN"))item {
            JobMonitor(context,r,permitted,onJournal)
        }
    }
    if(confirmSync)AlertDialog(onDismissRequest={confirmSync=false},title={Text("Получить изменения?")},text={Text(project.name+": будет получено "+
        (if(belongs)r.optJSONObject("snapshot")?.optInt("behind",0) ?: 0 else 0)+" коммитов.\n\n"+
        "Termux повторно проверит ветку, origin, SHA и чистоту рабочего дерева. "+
        "Только fast-forward. Push, reset и удаление файлов не выполняются.")},confirmButton={com.animacode.animadev.projects.ActionButton(compact=true,onClick={confirmSync=false;gateway.start("SYNC",project.id,project.path)}){Text("Получить")}},dismissButton={com.animacode.animadev.projects.ActionButton(compact=true,onClick={confirmSync=false}){Text("Отложить")}})
    if(changedFiles)AlertDialog(onDismissRequest={changedFiles=false},title={Text("Изменённые файлы")},text={Column {Text((if(belongs)r.optJSONObject("snapshot") else null)?.optJSONArray("changed_files")?.let {a->(0 until a.length()).joinToString("\n"){a.optString(it)}} ?: "Список недоступен");Text("Просмотр содержимого diff пока не подключён.",style=MaterialTheme.typography.bodySmall)}},confirmButton={com.animacode.animadev.projects.ActionButton(compact=true,onClick={changedFiles=false}){Text("Закрыть")}})
}


@Composable private fun LocalOnlyGitTools(context:Context,project:LocalProject,
                                           permitted:Boolean,termuxVersion:String?,
                                           onJournal:(String)->Unit) {
    val gateway=remember {RepoGateway(context)}
    val record=rememberRepoRecord(context)
    val belongs=record.optString("project_id","animaquiz")==project.id
    val snapshot=if(belongs)record.optJSONObject("snapshot") else null
    val pending=record.optBoolean("pending")
    val blocked=RepoPolicy.blocked(record.optString("job_id"),record.optString("state"))
    val recoveryUpgrade=belongs && gateway.mayUpgradeRecoveryHelper(record)
    val helperReady=record.optBoolean("ready")
    val ready=HelperPolicy.mayRun(permitted,termuxVersion!=null,helperReady,
                                  pending,blocked)
    val orphaned=belongs && record.optString("state")=="UNKNOWN" &&
        record.optString("action")=="CHECK" && record.optString("job_id").isNotBlank()
    val canRecover=RepoPolicy.canRecoverJob(pending,record.optString("job_id"),
        record.optString("job_runner_sha"),record.optString("installed_sha"))
    val canInstall=permitted && termuxVersion!=null && !pending &&
        (!blocked || recoveryUpgrade)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),
               verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text("Локальный Git",style=MaterialTheme.typography.titleLarge)}
        if(orphaned)item {
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Восстановление прежнего CHECK",
                        style=MaterialTheme.typography.titleMedium)
                    Text("Результат прежней проверки пока неизвестен. Job ID: "+
                        record.optString("job_id").take(12))
                    if(record.optString("error_code").isNotEmpty())
                        Text(record.optString("error_code") + ": "+
                            record.optString("message"),
                            style=MaterialTheme.typography.bodySmall)
                    Text("Ни сборка, ни повторная проверка Git не запускаются. "+
                        "Сначала обновляем локального помощника, затем читаем прежний Job ID.")
                    if(!helperReady) {
                        Button(onClick={gateway.install()},enabled=canInstall,
                            modifier=Modifier.fillMaxWidth()) {
                            Text(if(pending)"Подключаем…" else "Обновить помощник Termux")
                        }
                    }
                    OutlinedButton(onClick={gateway.query(true)},
                        enabled=canRecover && helperReady && permitted &&
                            termuxVersion!=null && !pending,
                        modifier=Modifier.fillMaxWidth()) {
                        Text("Проверить прежний Job ID")
                    }
                    if(!helperReady)Text("Проверка станет доступна после подтверждения установки помощника.",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        if(!helperReady && !orphaned)item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Нужно подключить помощник Termux",
                        style=MaterialTheme.typography.titleMedium)
                    Button(onClick={gateway.install()},enabled=canInstall,
                        modifier=Modifier.fillMaxWidth()) {
                        Text("Настроить помощник Termux")
                    }
                }
            }
        }
        item {Card {Column(Modifier.fillMaxWidth().padding(18.dp),
               verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(project.name,style=MaterialTheme.typography.titleMedium)
            Text("Ветка: "+project.branch)
            Text("HEAD: "+project.commit.take(12))
            Text("Origin не настроен. Это нормальный локальный проект.")
            Text("Изменения: "+when(project.dirty){true->"Есть локальные правки";
                false->"Чистое дерево";else->"Не проверено"})
            Text("GitHub-синхронизация недоступна. Никакие файлы не отправляются и не сбрасываются.")
        }}}
        item {Button(onClick={gateway.start("CHECK",project.id,project.path)},
                     enabled=ready,modifier=Modifier.fillMaxWidth()) {
            Text("Проверить локальный Git")
        }}
        item {OutlinedButton(onClick={gateway.start("STATUS",project.id,project.path)},
                             enabled=ready,modifier=Modifier.fillMaxWidth()) {
            Text("Обновить состояние")
        }}
        if(snapshot!=null)item {Card {Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(if(snapshot.optBoolean("checked")) "Локальный Git подтверждён"
                 else "Снимок проекта получен")
            Text("SHA: "+snapshot.optString("local_commit").take(12))
            Text(if(snapshot.optBoolean("dirty")) "Локальные изменения входят в Debug APK и тесты."
                 else "Рабочее дерево чистое.")
        }}}
    }
}
