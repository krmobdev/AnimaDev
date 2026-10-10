package com.animacode.animadev.projects

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.animacode.animadev.repos.*

@Composable fun ProjectTools(context:Context,project:LocalProject,tool:String,permitted:Boolean,termuxVersion:String?,onPermissionUpdated:()->Unit,onJournal:(String)->Unit,onWait:()->Unit) {
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
    val available=HelperPolicy.mayRun(permitted,termuxVersion!=null,ready,pending,blocked)
    var confirmSync by rememberSaveable {mutableStateOf(false)};var changedFiles by rememberSaveable {mutableStateOf(false)}
    val grant=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {onPermissionUpdated()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text(project.name,style=MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            ActiveBranchHero(project.branch,project.commit,project.dirty)
        }
        if(!permitted)item {Button(onClick={grant.launch(com.animacode.animadev.termux.TermuxGateway.PERMISSION)}){Text("Разрешить Termux")} }
        if(termuxVersion==null)item {Text("Termux не найден. Действия недоступны.",color=MaterialTheme.colorScheme.error)}
        if(!ready)item {Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)) {Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            val installed=r.optString("installed_sha").isNotEmpty()
            Text(if(installed)"Доступно обновление локального помощника" else "Нужен локальный помощник Termux",style=MaterialTheme.typography.titleSmall)
            Text(if(blocked)"Сначала уточните прежнее задание по Job ID. Обновление сейчас заблокировано." else "Результаты и история сохранятся. После подключения обновите состояние Git.",style=MaterialTheme.typography.bodySmall)
            FilledTonalButton(onClick={gateway.install()},enabled=HelperPolicy.mayConnect(permitted,termuxVersion!=null,pending,blocked)){Text(if(pending)"Подключаем…" else HelperPolicy.label(installed))}
        }} }
        if(tool=="git") {
            item {Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                val state=when {snap==null->"Состояние не проверено";!snap.optBoolean("remote_ok") || !snap.optBoolean("branch_allowed")->"Требуется проверка профиля";snap.optBoolean("dirty")->"Есть локальные изменения";snap.optInt("ahead")>0->"Есть локальные коммиты";snap.optInt("behind")>0->"Есть обновления";else->"Рабочее дерево чистое"}
                Text(state,style=MaterialTheme.typography.titleMedium)
                Text("${snap?.optString("local_commit")?.take(8)?.ifEmpty {"—"} ?: "—"} · ${if(snap?.optBoolean("remote_ok")==true)"origin подтверждён" else "origin не проверен"}",style=MaterialTheme.typography.bodyMedium)
                if(snap?.optBoolean("checked")==true)Text("GitHub: +${snap.optInt("behind")} · локально: +${snap.optInt("ahead")}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }} }
            item {Button(onClick={gateway.start("CHECK",project.id,project.path)},enabled=available && (snap==null || (snap.optBoolean("remote_ok") && snap.optBoolean("branch_allowed"))),modifier=Modifier.fillMaxWidth()){Icon(AppIcons.Refresh,null);Spacer(Modifier.width(8.dp));Text("Проверить GitHub")} }
            item {OutlinedButton(onClick={gateway.start("STATUS",project.id,project.path)},enabled=available,modifier=Modifier.fillMaxWidth()){Text("Обновить локальное состояние")} }
            item {FilledTonalButton(onClick={confirmSync=true},enabled=permitted && termuxVersion!=null && gateway.maySync(r,project.id),modifier=Modifier.fillMaxWidth()){Text("Получить изменения")} }
            item {Text(when {blocked->"Новое действие заблокировано текущим заданием.";!ready->"Операции доступны после подключения помощника.";snap==null->"Обновите локальное состояние.";!snap.optBoolean("branch_allowed")->"Ветка не разрешена профилем. Переключение не выполняется.";!snap.optBoolean("remote_ok")->"Origin не соответствует профилю проекта.";snap.optBoolean("dirty")->"Получение изменений заблокировано: есть локальные правки.";snap.optInt("ahead")>0->"Есть локальные коммиты. Объединение запрещено.";snap.optInt("behind")==0->"Проверка GitHub не изменяет исходники.";else->"Обновление — только fast-forward после подтверждения."},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(snap?.optBoolean("dirty")==true)item {TextButton(onClick={changedFiles=true}){Text("Посмотреть изменённые файлы")}}
        } else {
            val prepared=belongs && snap!=null && gateway.mayTest(r,project.id)
            val canRun=available && prepared
            val needStatus=r.optString("project_id","animaquiz")!=project.id || snap==null
            item {
                OutlinedCard(shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("Unit-тесты",style=MaterialTheme.typography.titleLarge)
                        Text("Gradle · :app:testDebugUnitTest",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(!ready) {
                            Text("Сначала обновите локальный помощник Termux (кнопка выше).",
                                color=MaterialTheme.colorScheme.error)
                        } else if(needStatus) {
                            Text("Для этого проекта ещё нет свежей проверки ветки и origin.",
                                style=MaterialTheme.typography.bodyMedium)
                        } else if(!prepared) {
                            Text(when {
                                blocked->"Предыдущее задание ещё выполняется или его статус неизвестен."
                                !snap.optBoolean("remote_ok")->"Origin не подтверждён для этого проекта."
                                else->"Состояние проекта изменилось. Обновите локальный снимок."
                            },style=MaterialTheme.typography.bodyMedium)
                        }
                        if(!prepared || needStatus) {
                            OutlinedButton(onClick={gateway.start("STATUS",project.id,project.path)},
                                enabled=available,modifier=Modifier.fillMaxWidth()) {
                                Icon(AppIcons.Refresh,null)
                                Spacer(Modifier.width(8.dp))
                                Text("Подготовить тесты · проверить проект")
                            }
                        }
                        Button(onClick={gateway.start("TEST",project.id,project.path)},
                            enabled=canRun,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                            Icon(AppIcons.Tests,null)
                            Spacer(Modifier.width(8.dp))
                            Text("Запустить unit-тесты")
                        }
                    }
                }
            }
            item {
                OutlinedCard(shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("Инструментальные тесты",style=MaterialTheme.typography.titleLarge)
                        Text("Реальное устройство · ADB · :app:connectedDebugAndroidTest",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(adbState,style=MaterialTheme.typography.bodyMedium,
                            color=if(deviceReady)MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick={adb.refresh()},
                            enabled=permitted && termuxVersion!=null && !adbPending,
                            modifier=Modifier.fillMaxWidth()) {
                            Icon(AppIcons.Refresh,null)
                            Spacer(Modifier.width(8.dp))
                            Text(if(adbPending)"Проверяем ADB…" else "Проверить устройства · adb devices")
                        }
                        Button(onClick={gateway.start("TEST_DEVICE",project.id,project.path)},
                            enabled=canRun && deviceReady,
                            modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                            Icon(AppIcons.Tests,null)
                            Spacer(Modifier.width(8.dp))
                            Text("Запустить тесты на устройстве")
                        }
                        if(!deviceReady)Text("После adb pair выполните adb connect IP:ПОРТ из основного экрана беспроводной отладки Pixel. "+
                            "При USB-подключении разрешите отладку на устройстве.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(!prepared && ready)Text("Сначала нажмите «Подготовить тесты» в разделе Unit-тесты.",
                            style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            listOf("test_job" to "Unit-тесты","device_test_job" to "Тесты на устройстве").forEach { (field,title) ->
                val test=if(belongs)r.optJSONObject(field) else null
                if(test!=null && test.optString("state") in RepoPolicy.terminalStates)item {
                    OutlinedCard(modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(title+" · "+MonitorPolicy.label(test.optString("state"),false,false),
                                style=MaterialTheme.typography.titleMedium)
                            test.optJSONObject("tests")?.let {counts->
                                Text("${counts.optLong("passed")} из ${counts.optLong("total")} успешно",
                                    style=MaterialTheme.typography.titleLarge)
                                Text("Ошибок ${counts.optLong("failed")} · пропущено ${counts.optLong("skipped")}",
                                    style=MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick={onJournal(test.optString("job_id"))},
                                modifier=Modifier.fillMaxWidth()) {Text("Открыть журнал")}
                        }
                    }
                }
            }
        }
        if(belongs && r.optString("job_id").isNotEmpty() && (blocked || (tool=="git" && r.optString("action")!="TEST")))item {JobMonitor(context,r,permitted,onJournal)}
    }
    if(confirmSync)AlertDialog(onDismissRequest={confirmSync=false},title={Text("Получить изменения?")},text={Text("${project.name}: ветка, origin и чистота проверятся повторно. Только fast-forward, без отправки на GitHub.")},confirmButton={TextButton(onClick={confirmSync=false;gateway.start("SYNC",project.id,project.path)}){Text("Получить")}},dismissButton={TextButton(onClick={confirmSync=false}){Text("Отложить")}})
    if(changedFiles)AlertDialog(onDismissRequest={changedFiles=false},title={Text("Изменённые файлы")},text={Column {Text(snap?.optJSONArray("changed_files")?.let {a->(0 until a.length()).joinToString("\n"){a.optString(it)}} ?: "Список недоступен");Text("Просмотр содержимого diff пока не подключён.",style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={changedFiles=false}){Text("Закрыть")}})
}
