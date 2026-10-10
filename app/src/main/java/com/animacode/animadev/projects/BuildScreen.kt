package com.animacode.animadev.projects

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                           onPermissionUpdated:()->Unit,onJournal:(String)->Unit) {
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
    val canDebug=project.supported && ready && !releaseBusy && !catalogRefresh &&
        snapshotMatches && gateway.mayBuild(record,project.id)
    val canCheck=project.supported && ready && !releaseBusy
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(releaseOrDebugBusy) {
        if(releaseOrDebugBusy)while(true) { delay(1000);now=System.currentTimeMillis() }
    }
    val blockReason=when {
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
    val releaseScope=rememberCoroutineScope()
    val canRelease=canDebug && hasSigningKey && signing.alias(project.id).isNotBlank() && !releaseOrDebugBusy
    val effectivePasswordPresent=releasePassword.isNotEmpty() || storedCredentials?.storePassword?.isNotEmpty()==true
    val grant=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){onPermissionUpdated()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=24.dp,bottom=40.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("BUILD STUDIO",style=MaterialTheme.typography.labelLarge,
                    color=AnimaPulse.green)
                Text(project.name,style=MaterialTheme.typography.headlineLarge)
                ActiveBranchHero(project.branch,project.commit,project.dirty)
                if(catalogRefresh)Text("Сверяем активную ветку с Termux…",
                    style=MaterialTheme.typography.bodySmall)
            }
        }
        if(busyOwner!=null)item {
            Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp).animateContentSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Сборочный слот занят",style=MaterialTheme.typography.titleMedium)
                    Text("Проект: "+busyOwner+" · "+(if(releaseBusy)"Release AAB" else if(record.optString("action")=="TEST")"Unit-тесты" else "Debug APK"))
                    Text("Состояние: "+(if(releaseBusy)releaseState.optString("state") else record.optString("state")))
                    val start=if(releaseBusy)releaseState.optLong("started_at") else
                        ((record.optJSONObject("job")?.optDouble("started_at",0.0) ?: 0.0)*1000).toLong()
                    if(start>0)Text("Выполняется: "+(maxOf(0L,now-start)/60_000)+" мин "+(maxOf(0L,now-start)/1000%60)+" с")
                    LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                    Text("Другие сборки заблокированы до завершения. Индикатор не показывает проценты Gradle.",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        if(!permitted)item {Button(onClick={grant.launch(TermuxGateway.PERMISSION)}){Text("Разрешить Termux")}}
        if(!record.optBoolean("ready"))item {Card {Column(Modifier.padding(16.dp)) {
            Text("Локальный помощник необходимо обновить")
            Button(onClick={gateway.install()},enabled=permitted && termuxVersion!=null && !blocked && !record.optBoolean("pending")){Text("Обновить помощник")}
        }}}
        item {OutlinedCard(shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(20.dp).animateContentSize(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(AppIcons.Build,null,tint=MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Debug APK",style=MaterialTheme.typography.titleLarge)
                        Text("Для установки и тестирования",style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(onClick={tap();gateway.start("BUILD_DEBUG",project.id,project.path)},enabled=canDebug,
                    modifier=Modifier.fillMaxWidth()){Icon(AppIcons.Build,null);Spacer(Modifier.width(8.dp));Text("Собрать Debug APK")}
                if(!canDebug) {
                    Text(blockReason,style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick={tap();gateway.start("CHECK",project.id,project.path)},
                        enabled=canCheck,modifier=Modifier.fillMaxWidth()) {
                        Text("Проверить GitHub SHA")
                    }
                }
            }
        }}
        item {
            OutlinedCard(shape=RoundedCornerShape(22.dp),
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(20.dp).animateContentSize(),
                    verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Icon(AppIcons.Build,null,tint=MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Release AAB",style=MaterialTheme.typography.titleLarge)
                            Text("Подписанный пакет для публикации",
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
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
                    if(storedCredentials!=null && !showPasswordFields) {
                        Text("Пароль готов к использованию",style=MaterialTheme.typography.bodyMedium,
                            color=MaterialTheme.colorScheme.primary)
                        TextButton(onClick={tap();showPasswordFields=true},
                            enabled=!releaseOrDebugBusy) {Text("Использовать другой пароль")}
                    }
                    AnimatedVisibility(storedCredentials==null || showPasswordFields) {
                        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(releasePassword,{releasePassword=it},
                                label={Text("Пароль хранилища JKS")},
                                visualTransformation=PasswordVisualTransformation(),singleLine=true,
                                enabled=!releaseOrDebugBusy,modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(releaseKeyPassword,{releaseKeyPassword=it},
                                label={Text("Пароль ключа, если отличается")},
                                visualTransformation=PasswordVisualTransformation(),singleLine=true,
                                enabled=!releaseOrDebugBusy,modifier=Modifier.fillMaxWidth())
                            if(storedCredentials==null) {
                                TextButton(onClick={
                                    tap()
                                    val ok=vault.save(project.id,signing.documentUri(project.id),signing.alias(project.id),
                                        releasePassword,releaseKeyPassword)
                                    vaultMessage=if(ok)"Пароль зашифрован и сохранён только на телефоне."
                                        else "Не удалось сохранить секрет. Проверьте JKS и alias."
                                    if(ok) {
                                        releasePassword="";releaseKeyPassword=""
                                        showPasswordFields=false;vaultGeneration++
                                    }
                                },enabled=!releaseOrDebugBusy && hasSigningKey &&
                                    signing.alias(project.id).isNotBlank() && releasePassword.isNotEmpty()) {
                                    Text("Сохранить пароль защищённо")
                                }
                            } else {
                                TextButton(onClick={tap();showPasswordFields=false;releasePassword="";releaseKeyPassword=""}) {
                                    Text("Отмена")
                                }
                            }
                        }
                    }
                    if(vaultMessage.isNotBlank())Text(vaultMessage,style=MaterialTheme.typography.bodySmall)
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
                        Text("Собрать Release AAB")
                    }
                    if(!canRelease || !effectivePasswordPresent)Text(when {
                        !canDebug -> blockReason
                        !hasSigningKey -> "Выберите файл JKS в настройках."
                        signing.alias(project.id).isBlank() -> "Укажите alias ключа."
                        else -> "Введите пароль JKS или сохраните его на телефоне."
                    },style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick={tap();showSecurityDetails=!showSecurityDetails}) {
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
        if(releaseForProject)item {
            val state=releaseState.optString("state")
            OutlinedCard(shape=RoundedCornerShape(22.dp),
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Последняя Release-сборка",style=MaterialTheme.typography.titleMedium)
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
                        Text("Другие сборки заблокированы до подтверждения результата.",
                            style=MaterialTheme.typography.bodySmall)
                        if(releaseState.optBoolean("cancel_requested"))
                            Text("Отправлен Ctrl+C · ожидаем подтверждение остановки",color=MaterialTheme.colorScheme.error,
                                style=MaterialTheme.typography.bodySmall)
                        else OutlinedButton(onClick={confirmReleaseStop=true},
                            modifier=Modifier.fillMaxWidth(),
                            colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)) {
                            Text("Остановить Release · Ctrl+C")
                        }
                    }
                    Text(releaseState.optString("message"),style=MaterialTheme.typography.bodyMedium)
                    if(releaseMessage.isNotBlank())Text(releaseMessage,
                        style=MaterialTheme.typography.bodySmall)
                    releaseState.optJSONObject("artifact")?.let {artifact->
                        HorizontalDivider()
                        Text(artifact.optString("path"),style=MaterialTheme.typography.bodyMedium)
                        Text("SHA-256: "+artifact.optString("sha256"),
                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if(belongs && record.optString("action")=="BUILD_DEBUG" && record.optString("job_id").isNotEmpty())item {
            JobMonitor(context,record,permitted,onJournal)
        }
        val job=if(belongs)record.optJSONObject("job") else null
        if(job?.optString("action")=="BUILD_DEBUG" && record.optString("state") in RepoPolicy.terminalStates)item {
            Text("Задание: "+MonitorPolicy.label(job.optString("state"),false,false),style=MaterialTheme.typography.titleSmall)
            Text(job.optString("message"),style=MaterialTheme.typography.bodySmall)
            job.optJSONObject("artifact")?.let {a->Card {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("Debug APK в Загрузках",style=MaterialTheme.typography.titleMedium)
                Text(a.optString("path"),style=MaterialTheme.typography.bodySmall)
                Text("SHA-256: "+a.optString("sha256"),style=MaterialTheme.typography.bodySmall)
                Text("Размер: "+a.optLong("size")+" байт · Git "+a.optString("commit").take(12),style=MaterialTheme.typography.bodySmall)
            }}}
            TextButton(onClick={onJournal(job.optString("job_id"))}){Text("Журнал задания")}
        }
    }
    if(confirmReleaseStop)AlertDialog(
        onDismissRequest={confirmReleaseStop=false},
        title={Text("Остановить Release AAB?")},
        text={Text("Termux получит Ctrl+C. Готового AAB может не быть; исходники и Git не изменятся. Другая сборка останется заблокированной до подтверждения завершения.")},
        confirmButton={TextButton(onClick={confirmReleaseStop=false;ReleaseCancel.request(context)}){Text("Остановить")}},
        dismissButton={TextButton(onClick={confirmReleaseStop=false}){Text("Продолжить")}}
    )
}
