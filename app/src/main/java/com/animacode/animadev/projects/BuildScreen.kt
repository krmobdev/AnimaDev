package com.animacode.animadev.projects

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.animacode.animadev.repos.*
import com.animacode.animadev.termux.TermuxGateway

@Composable fun BuildScreen(context:Context,project:LocalProject,permitted:Boolean,termuxVersion:String?,
                           onPermissionUpdated:()->Unit,onJournal:(String)->Unit,
                           selectedBuildTab:Int?=null,onBuildTabChange:((Int)->Unit)?=null) {
    val neon=LocalNeonSkin.current
    val feedback=remember {UiFeedback(context)}
    val view=LocalView.current
    fun tap(){feedback.tap(view)}
    val record=rememberRepoRecord(context)
    val gateway=remember {RepoGateway(context)}
    val belongs=record.optString("project_id","animaquiz")==project.id
    val snapshot=if(belongs)record.optJSONObject("snapshot") else null
    val blocked=RepoPolicy.blocked(record.optString("job_id"),record.optString("state"))
    val signing=remember {SigningStore(context)}
    val hasSigningKey=signing.hasDocument(project.id)
    val releaseStore=remember {ReleaseStore(context)}
    val releaseGateway=remember {ReleaseGateway(context)}
    val progressGateway=remember {ReleaseProgressGateway(context)}
    val vault=remember {SigningSecretVault(context)}
    var vaultGeneration by remember(project.id){mutableIntStateOf(0)}
    val vaultSaved=remember(project.id,vaultGeneration){vault.has(project.id)}
    val storedCredentials=remember(project.id,vaultGeneration,signing.documentUri(project.id),signing.alias(project.id)) {if(vaultSaved)vault.load(project.id,signing.documentUri(project.id),signing.alias(project.id)) else null}
    var vaultMessage by remember(project.id){mutableStateOf("")}
    val releaseGeneration=ReleaseStore.generation.intValue
    val releaseState=remember(releaseGeneration) {releaseStore.read()}
    val releaseForProject=releaseState.optString("project")==project.id
    val releaseBusy=BuildConcurrencyPolicy.releaseBusy(releaseState.optString("state"))
    val repoBuildBusy=BuildConcurrencyPolicy.repoBusy(record.optString("job_id"),record.optString("state"),record.optString("action"))
    val releaseOrDebugBusy=releaseBusy || repoBuildBusy
    val projects=remember {ProjectStore(context)}
    val repoOwner=projects.all().find {it.id==record.optString("project_id")}?.name ?: record.optString("project_id")
    val releaseOwner=projects.all().find {it.id==releaseState.optString("project")}?.name ?: releaseState.optString("project")
    val busyOwner=BuildConcurrencyPolicy.owner(repoOwner,record.optString("job_id"),record.optString("state"),record.optString("action"),releaseOwner,releaseState.optString("state"))
    val ready=HelperPolicy.mayRun(permitted,termuxVersion!=null,record.optBoolean("ready"),record.optBoolean("pending"),blocked)
    val catalogRefresh=rememberCatalogRecord(context).optBoolean("pending")
    val snapshotMatches=snapshot!=null && snapshot.optString("branch")==project.branch &&
        snapshot.optString("local_commit")==project.commit
    val destination=remember {ExportDestination(context)}
    val outputReady=destination.artifactDirectory()!=null
    val canDebug=project.supported && ready && !releaseBusy && !catalogRefresh &&
        snapshotMatches && outputReady && gateway.mayBuild(record,project.id)
    val canCheck=project.supported && ready && !releaseBusy
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(releaseOrDebugBusy) {
        if(releaseOrDebugBusy)while(true) { delay(1000);now=System.currentTimeMillis() }
    }
    val blockReason=when {
        !outputReady -> "Выбранная папка недоступна для Termux. В настройках выберите папку в основной памяти телефона или обычные Загрузки."
        busyOwner!=null -> BuildConcurrencyPolicy.waitingMessage(busyOwner,project.name)
        catalogRefresh -> "Проверяем фактическую активную ветку в Termux. Дождитесь ответа."
        !snapshotMatches -> "Ветка или SHA изменились после последней проверки. Обновите GitHub SHA."
        blocked -> "Предыдущее задание Termux не завершено или его результат неизвестен. Откройте журнал и уточните статус."
        record.optBoolean("pending") -> "Termux ещё обрабатывает предыдущий запрос. Дождитесь ответа."
        !permitted -> "Разрешите AnimaDev выполнять команды в Termux."
        termuxVersion==null -> "Termux не найден. Проверьте подключение."
        !record.optBoolean("ready") -> "Локальный помощник Termux не готов; обновите его."
        snapshot==null || !snapshot.optBoolean("checked") -> "Не выполнена актуальная проверка GitHub SHA."
        snapshot.optBoolean("dirty") -> "В рабочем дереве есть локальные изменения — сборка запрещена."
        !snapshot.optBoolean("remote_ok") -> "Адрес origin не соответствует выбранному проекту."
        snapshot.optInt("ahead",-1)!=0 || snapshot.optInt("behind",-1)!=0 -> "Локальный Git SHA и GitHub не совпадают. Синхронизируйте через Git."
        !project.supported -> "Для этого проекта нет безопасного профиля сборки."
        else -> "Ветка или Git SHA не соответствует разрешённому профилю."
    }
    var releasePassword by remember(project.id){mutableStateOf("")}
    var releaseKeyPassword by remember(project.id){mutableStateOf("")}
    var releaseMessage by remember(project.id){mutableStateOf("")}
    var showPasswordFields by remember(project.id){mutableStateOf(false)}
    var showSecurityDetails by remember(project.id){mutableStateOf(false)}
    var confirmReleaseStop by remember(project.id){mutableStateOf(false)}
    var confirmCacheClear by remember {mutableStateOf(false)}
    var cacheNotice by remember {mutableStateOf("")}
    var showReleaseJournal by remember(project.id){mutableStateOf(false)}
    val releaseScope=rememberCoroutineScope()
    val canRelease=canDebug && hasSigningKey && signing.alias(project.id).isNotBlank() && !releaseOrDebugBusy
    val effectivePasswordPresent=releasePassword.isNotEmpty() || storedCredentials?.storePassword?.isNotEmpty()==true
    var fallbackBuildTab by rememberSaveable(project.id) {mutableIntStateOf(0)}
    val buildTab=selectedBuildTab ?: fallbackBuildTab
    val grant=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){onPermissionUpdated()}
    val buildListState=rememberLazyListState()
    LaunchedEffect(releaseState.optString("id"),releaseBusy,permitted) {
        val id=releaseState.optString("id")
        if(permitted && releaseBusy && releaseForProject && id.matches(Regex("[a-f0-9]{32}"))) {
            while(true) {
                progressGateway.poll(id)
                delay(4500)
            }
        }
    }
    LaunchedEffect(buildTab,releaseState.optString("id")) {
        if(buildTab==1 && releaseForProject && releaseState.optString("id").isNotBlank()) {
            delay(300)
            buildListState.scrollToItem((buildListState.layoutInfo.totalItemsCount-1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(buildTab,record.optString("job_id")) {
        if(buildTab==0 && belongs && record.optString("action")=="BUILD_DEBUG" &&
           record.optString("job_id").isNotBlank()) {
            delay(300)
            buildListState.scrollToItem((buildListState.layoutInfo.totalItemsCount-1).coerceAtLeast(0))
        }
    }
    LazyColumn(Modifier.fillMaxSize(),state=buildListState,contentPadding=PaddingValues(start=20.dp,end=20.dp,top=24.dp,bottom=40.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item {
            if(neon) NeonPanel(Modifier.fillMaxWidth(),glowing=true) {
                Text(if(buildTab==0)"Debug APK Build" else "Release AAB Build",
                    style=MaterialTheme.typography.headlineSmall,
                    fontWeight=androidx.compose.ui.text.font.FontWeight.ExtraBold)
                Text(project.name+" · "+project.branch.ifBlank {"Branch not verified"},
                    style=MaterialTheme.typography.bodyMedium,color=NeonGlass.muted)
                Text("Git "+project.commit.take(12).ifBlank {"SHA unknown"},
                    style=MaterialTheme.typography.bodySmall,color=NeonGlass.softGreen)
            } else OperationProjectHeader(project)
        }
        if(neon && buildTab==0)item {
            NeonPanel(Modifier.fillMaxWidth()) {
                Text("Build Options",style=MaterialTheme.typography.titleLarge,
                    fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Verified configuration for the current project",
                    style=MaterialTheme.typography.bodySmall,color=NeonGlass.muted)
                Text("Variant · debug",style=MaterialTheme.typography.bodyMedium)
                HorizontalDivider(color=NeonGlass.outline)
                Text("Type · Debug APK",style=MaterialTheme.typography.bodyMedium)
                HorizontalDivider(color=NeonGlass.outline)
                Text("Git reference · "+project.branch.ifBlank {"Not verified"},
                    style=MaterialTheme.typography.bodyMedium)
                HorizontalDivider(color=NeonGlass.outline)
                Text("Mode · Gradle --rerun-tasks (offline)",style=MaterialTheme.typography.bodyMedium)
                if(!canDebug) NeonStatus("Build blocked until verified",false)
            }
        }
        item {
            if(neon)NeonTabs(listOf("Debug APK","Release AAB"),buildTab,onSelect={
                if(onBuildTabChange!=null)onBuildTabChange(it) else fallbackBuildTab=it
            }) else OperationTabs(listOf("Debug APK","Release AAB"),buildTab) {
                if(onBuildTabChange!=null)onBuildTabChange(it) else fallbackBuildTab=it
            }
        }
        if(catalogRefresh)item {Text("Сверяем активную ветку с Termux…",style=MaterialTheme.typography.bodySmall)}
        if(busyOwner!=null)item {
            Text("Очередь занята: "+busyOwner+" · "+when {
                releaseBusy->"Release AAB"
                record.optString("action")=="TEST"->"Unit-тесты"
                record.optString("action")=="TEST_DEVICE"->"Тесты на устройстве"
                else->"Debug APK"
            },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(!permitted)item {Button(onClick={grant.launch(TermuxGateway.PERMISSION)}){Text("Разрешить Termux")}}
        if(!record.optBoolean("ready"))item {Card {Column(Modifier.padding(16.dp)) {
            Text("Локальный помощник необходимо обновить")
            ActionButton(onClick={gateway.install()},enabled=permitted && termuxVersion!=null && !blocked && !record.optBoolean("pending")){Text("Обновить помощник")}
        }}}
        if(buildTab==0)item {OutlinedCard(shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(20.dp).animateContentSize(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(AppIcons.Build,null,tint=MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Debug APK",style=MaterialTheme.typography.titleLarge)
                        Text("Для установки и тестирования",style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if(neon) NeonPrimaryAction("Собрать Debug APK · полная пересборка",
                    "Generate verified Debug APK",AppIcons.Build,
                    enabled=canDebug,modifier=Modifier.fillMaxWidth(),
                    onClick={tap();gateway.start("BUILD_DEBUG",project.id,project.path)})
                else Button(onClick={tap();gateway.start("BUILD_DEBUG",project.id,project.path)},enabled=canDebug,
                    modifier=Modifier.fillMaxWidth()){Icon(AppIcons.Build,null);Spacer(Modifier.width(8.dp));Text("Собрать Debug APK · полная пересборка")}
                Text("Gradle выполняется с --rerun-tasks: старые результаты задач не используются. Кэш зависимостей и файлы проектов не удаляются.",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick={confirmCacheClear=true},enabled=!releaseOrDebugBusy,
                    modifier=Modifier.fillMaxWidth()) {Text("Очистить временный кэш AnimaDev")}
                if(cacheNotice.isNotBlank())Text(cacheNotice,style=MaterialTheme.typography.bodySmall)
                if(!canDebug) {
                    Text(blockReason,style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick={tap();gateway.start("CHECK",project.id,project.path)},
                        enabled=canCheck,modifier=Modifier.fillMaxWidth()) {
                        Text("Проверить GitHub SHA")
                    }
                }
            }
        }}
        if(buildTab==0)item {
            Box(Modifier.fillMaxWidth()) {
                DebugBuildResult(context,record,project,permitted,onJournal)
            }
        }
        if(buildTab==1)item {
            Surface(shape=RoundedCornerShape(23.dp),
                color=if(neon)NeonGlass.surface else MaterialTheme.colorScheme.surfaceContainer,
                border=BorderStroke(1.dp,if(neon)NeonGlass.outline else MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(20.dp).animateContentSize(),
                    verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Icon(AppIcons.Build,null,tint=MaterialTheme.colorScheme.primary)
                        Column {
                            Text(if(neon)"Release signing" else "Release AAB",
                                style=MaterialTheme.typography.titleLarge)
                            Text("Подписанный пакет для публикации",
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if(neon)NeonSection("Keystore",AppIcons.Build,
                        "Use the selected signing file and key alias")
                    if(hasSigningKey) {
                        Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,
                            shape=RoundedCornerShape(14.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(signing.fileLabel(project.id),style=MaterialTheme.typography.titleSmall)
                                Text("Ключ: "+signing.alias(project.id)+
                                    if(storedCredentials!=null)"   ·   Пароль защищён" else "   ·   Пароль не сохранён",
                                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        Text("Сначала выберите JKS и alias в настройках подписи.",
                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                    }
                    if(neon)NeonSection("Passwords",AppIcons.Info,
                        "Passwords stay protected by Android Keystore")
                    Text("Пароль хранилища JKS открывает файл .jks. Пароль закрытого ключа нужен только если отличается. "+
                        "Пароль не проверяется при простом сохранении; правильность окончательно подтверждает подпись в Termux.",
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(storedCredentials!=null && !showPasswordFields) {
                        Text("Зашифрованные пароли сохранены на этом телефоне. Повторный ввод не нужен, пока не сменишь JKS или alias.",style=MaterialTheme.typography.bodyMedium,
                            color=MaterialTheme.colorScheme.primary)
                        com.animacode.animadev.projects.ActionButton(onClick={tap();showPasswordFields=true},
                            enabled=!releaseOrDebugBusy) {Text("Использовать другой пароль")}
                    }
                    AnimatedVisibility(storedCredentials==null || showPasswordFields) {
                        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(releasePassword,{releasePassword=it},
                                label={Text("Пароль файла JKS (keystore password)")},
                                visualTransformation=PasswordVisualTransformation(),singleLine=true,
                                enabled=!releaseOrDebugBusy,modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(releaseKeyPassword,{releaseKeyPassword=it},
                                label={Text("Пароль ключа, если отличается")},
                                visualTransformation=PasswordVisualTransformation(),singleLine=true,
                                enabled=!releaseOrDebugBusy,modifier=Modifier.fillMaxWidth())
                            if(storedCredentials==null) {
                                com.animacode.animadev.projects.ActionButton(onClick={
                                    tap()
                                    val ok=vault.save(project.id,signing.documentUri(project.id),signing.alias(project.id),
                                        releasePassword,releaseKeyPassword)
                                    vaultMessage=if(ok)"Сохранено в Android Keystore. Пароль ещё не проверен; подтверждение будет при подписи Release."
                                        else "Не удалось сохранить пароль в Android Keystore. Проверьте доступ к телефону."
                                    if(ok) {
                                        releasePassword="";releaseKeyPassword=""
                                        showPasswordFields=false;vaultGeneration++
                                    }
                                },enabled=!releaseOrDebugBusy && hasSigningKey &&
                                    signing.alias(project.id).isNotBlank() && releasePassword.isNotEmpty()) {
                                    Text("Сохранить пароль защищённо")
                                }
                            } else {
                                com.animacode.animadev.projects.ActionButton(onClick={tap();showPasswordFields=false;releasePassword="";releaseKeyPassword=""}) {
                                    Text("Отмена")
                                }
                            }
                        }
                    }
                    if(vaultMessage.isNotBlank())Text(vaultMessage,style=MaterialTheme.typography.bodySmall)
                    if(neon)NeonSection("Build Actions",AppIcons.Build,
                        "Sign and verify an Android App Bundle")
                    Button(onClick={
                        tap()
                        val saved=storedCredentials
                        val storePass=releasePassword.ifEmpty {saved?.storePassword.orEmpty()}
                        val keyPass=if(releasePassword.isNotEmpty())releaseKeyPassword else
                            releaseKeyPassword.ifEmpty {saved?.keyPassword.orEmpty()}
                        releasePassword="";releaseKeyPassword=""
                        releaseMessage="Подготавливаю защищённую сборку…"
                        releaseScope.launch {releaseMessage=releaseGateway.launch(project,storePass,keyPass)}
                    },enabled=canRelease && effectivePasswordPresent,
                        modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)) {
                        Text(if(neon)"Build Release AAB" else "Собрать Release AAB")
                    }
                    OutlinedButton(onClick={tap();gateway.start("CHECK",project.id,project.path)},
                        enabled=canCheck,modifier=Modifier.fillMaxWidth()) {
                        Text("Проверить GitHub SHA")
                    }
                    if(!canRelease || !effectivePasswordPresent)Text(when {
                        !canDebug -> blockReason
                        !hasSigningKey -> "Выберите файл JKS в настройках."
                        signing.alias(project.id).isBlank() -> "Укажите alias ключа."
                        else -> "Введите пароль JKS или сохраните его на телефоне."
                    },style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    com.animacode.animadev.projects.ActionButton(onClick={tap();showSecurityDetails=!showSecurityDetails}) {
                        Text(if(showSecurityDetails)"Скрыть настройки безопасности" else "Безопасность и управление паролем")
                    }
                    AnimatedVisibility(showSecurityDetails) {
                        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text("JKS и пароль шифруются для Termux одноразовым RSA-ключом и AES-GCM. "+
                                "В GitHub, логах и командных аргументах нет открытых паролей.",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(vaultSaved) OutlinedButton(onClick={
                                tap()
                                vaultMessage=if(vault.forget(project.id))"Сохранённые пароли удалены." else "Удаление не подтверждено."
                                showPasswordFields=true;vaultGeneration++
                            },enabled=!releaseOrDebugBusy) {Text("Удалить сохранённый пароль")}
                        }
                    }
                }
            }
        }
        if(buildTab==1 && releaseForProject)item {
            val state=releaseState.optString("state")
            OutlinedCard(shape=RoundedCornerShape(22.dp),
                modifier=Modifier.fillMaxWidth(),
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(neon)NeonSection("Release AAB status",AppIcons.History,
                        "Signed bundle · local Termux build")
                    else Text("Последняя Release-сборка",style=MaterialTheme.typography.titleMedium)
                    Text("Job ID: "+releaseState.optString("id"),
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Git SHA: "+releaseState.optString("sha").ifBlank {"Не определён"},
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    val phase=releaseState.optString("phase")
                    if(phase.isNotBlank())Text("Этап: "+when(phase) {
                        "ENROLLING"->"Подготовка безопасной подписи"
                        "DISPATCHING"->"Передача защищённого задания"
                        "STARTING"->"Termux принял задание"
                        "GIT"->"Проверка GitHub и SHA"
                        "GRADLE"->"Gradle · сборка Release"
                        "SIGNING"->"Подпись ключом JKS"
                        "EXPORTING"->"Проверка и перенос AAB"
                        "DONE"->"AAB сохранён"
                        "FAILED"->"Ошибка сборки"
                        else->phase
                    },style=MaterialTheme.typography.bodySmall)
                    val startedAt=releaseState.optLong("started_at")
                    val endedAt=releaseState.optLong("ended_at")
                    val format=java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss",java.util.Locale.getDefault())
                    if(startedAt>0)Text("Начало: "+format.format(java.util.Date(startedAt)),
                        style=MaterialTheme.typography.bodySmall)
                    if(endedAt>startedAt && startedAt>0) {
                        Text("Окончание: "+format.format(java.util.Date(endedAt)),
                            style=MaterialTheme.typography.bodySmall)
                        Text("Длительность: "+((endedAt-startedAt)/60000)+" мин "+((endedAt-startedAt)/1000%60)+" с",
                            style=MaterialTheme.typography.bodySmall)
                    }
                    releaseState.optString("error_code").takeIf {it.isNotBlank()}?.let {
                        Text("Ошибка: "+it,color=MaterialTheme.colorScheme.error,
                            style=MaterialTheme.typography.bodySmall)
                    }
                    if(neon)NeonStatus(
                        when(state) {
                            "SUCCEEDED"->"Ready for publishing"
                            "FAILED"->"Build failed"
                            "CANCELLED"->"Cancelled"
                            "UNKNOWN"->"Needs verification"
                            "REQUESTED"->"Running in Termux"
                            else->state
                        },state=="SUCCEEDED")
                    Text(when(state) {
                        "SUCCEEDED"->"Готово к отправке"
                        "FAILED"->"Не удалось собрать"
                        "CANCELLED"->"Остановлено пользователем"
                        "UNKNOWN"->"Результат требует проверки"
                        "REQUESTED"->"Выполняется в Termux"
                        else->state
                    },style=MaterialTheme.typography.titleSmall,
                        color=if(state=="FAILED")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    if(releaseBusy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        val started=releaseState.optLong("started_at")
                        if(started>0)Text("Прошло: "+(maxOf(0L,now-started)/60_000)+
                            " мин "+(maxOf(0L,now-started)/1000%60)+" с")
                        HorizontalDivider()
                        ReleaseResourceMetrics(releaseState.optJSONObject("metrics"),now)
                        Text("Другие сборки заблокированы до подтверждения результата.",
                            style=MaterialTheme.typography.bodySmall)
                        if(releaseState.optString("phase")=="EXPORTING")
                            Text("Сборка завершена. Выполняется проверка и перенос AAB.",
                                style=MaterialTheme.typography.bodySmall)
                        else if(releaseState.optBoolean("cancel_requested"))
                            Text("Отправлен Ctrl+C · ожидаем подтверждение остановки",color=MaterialTheme.colorScheme.error,
                                style=MaterialTheme.typography.bodySmall)
                        else OutlinedButton(onClick={confirmReleaseStop=true},
                            modifier=Modifier.fillMaxWidth(),
                            colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)) {
                            Text("Остановить Release · Ctrl+C")
                        }
                    }
                    Text(releaseState.optString("message"),style=MaterialTheme.typography.bodyMedium)
                    releaseState.optString("progress_lines").takeIf {it.isNotBlank()}?.let {safeLog->
                        HorizontalDivider()
                        Text("Этапы Gradle · без ключей и паролей",style=MaterialTheme.typography.titleSmall)
                        Text(safeLog.lines().takeLast(7).joinToString("\n"),
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(releaseMessage.isNotBlank())Text(releaseMessage,
                        style=MaterialTheme.typography.bodySmall)
                    releaseState.optString("output_dir").takeIf {it.isNotBlank()}?.let {
                        Text("Папка сохранения: "+it,style=MaterialTheme.typography.bodySmall)
                    }
                    if(releaseState.optString("id").isNotBlank())
                        ActionButton(onClick={tap();showReleaseJournal=true}) {
                            Text("Открыть журнал сборки")
                        }
                    releaseState.optJSONObject("artifact")?.let {artifact->
                        HorizontalDivider()
                        if(state=="SUCCEEDED")BuildArtifactCard(context,artifact)
                    }
                }
            }
        }

    }
    if(confirmCacheClear)AlertDialog(
        onDismissRequest={confirmCacheClear=false},title={Text("Очистить временный кэш?")},
        text={Text("Удалятся только временные APK-копии и подготовленные отчёты самого AnimaDev. Git, Gradle, установленные приложения, задания и история не затрагиваются.")},
        confirmButton={TextButton(onClick={
            val count=AppCache.clear(context)
            cacheNotice="Удалено временных файлов: $count"
            confirmCacheClear=false
        }) {Text("Очистить")}},
        dismissButton={TextButton(onClick={confirmCacheClear=false}) {Text("Отмена")}}
    )
    if(showReleaseJournal && releaseState.optString("id").isNotBlank())
        ReleaseJournalDialog(context,releaseState.optString("id")) {showReleaseJournal=false}
    if(confirmReleaseStop)AlertDialog(
        onDismissRequest={confirmReleaseStop=false},
        title={Text("Остановить Release AAB?")},
        text={Text("Termux получит Ctrl+C. Готового AAB может не быть; исходники и Git не изменятся. Другая сборка останется заблокированной до подтверждения завершения.")},
        confirmButton={com.animacode.animadev.projects.ActionButton(compact=true,onClick={confirmReleaseStop=false;ReleaseCancel.request(context)}){Text("Остановить")}},
        dismissButton={com.animacode.animadev.projects.ActionButton(compact=true,onClick={confirmReleaseStop=false}){Text("Продолжить")}}
    )
}
