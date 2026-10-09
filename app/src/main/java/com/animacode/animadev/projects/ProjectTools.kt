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
    val snap=r.optJSONObject("snapshot");val pending=r.optBoolean("pending");val blocked=store.blocked();val ready=r.optBoolean("ready")
    val available=HelperPolicy.mayRun(permitted,termuxVersion!=null,ready,pending,blocked)
    var confirmSync by rememberSaveable {mutableStateOf(false)};var changedFiles by rememberSaveable {mutableStateOf(false)};var showTask by rememberSaveable {mutableStateOf(false)}
    val grant=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {onPermissionUpdated()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(project.name,style=MaterialTheme.typography.titleMedium);Text(snap?.optString("branch")?.ifEmpty {"Ветка не проверена"} ?: "Ветка не проверена",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
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
            item {FilledTonalButton(onClick={confirmSync=true},enabled=permitted && termuxVersion!=null && gateway.maySync(r),modifier=Modifier.fillMaxWidth()){Text("Получить изменения")} }
            item {Text(when {blocked->"Новое действие заблокировано текущим заданием.";!ready->"Операции доступны после подключения помощника.";snap==null->"Обновите локальное состояние.";!snap.optBoolean("branch_allowed")->"Ветка не разрешена профилем. Переключение не выполняется.";!snap.optBoolean("remote_ok")->"Origin не соответствует профилю AnimaQuiz.";snap.optBoolean("dirty")->"Получение изменений заблокировано: есть локальные правки.";snap.optInt("ahead")>0->"Есть локальные коммиты. Объединение запрещено.";snap.optInt("behind")==0->"Проверка GitHub не изменяет исходники.";else->"Обновление — только fast-forward после подтверждения."},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(snap?.optBoolean("dirty")==true)item {TextButton(onClick={changedFiles=true}){Text("Посмотреть изменённые файлы")}}
        } else {
            item {Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Unit-тесты · Debug",style=MaterialTheme.typography.titleMedium)
                Text("Фиксированный профиль AnimaQuiz",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick={showTask=!showTask}){Text(if(showTask)"Скрыть команду" else "Команда запуска")}
                if(showTask)Text(":app:testDebugUnitTest\n--offline --no-daemon --rerun-tasks --console=plain",style=MaterialTheme.typography.bodySmall)
                Button(onClick={gateway.start("TEST",project.id,project.path)},enabled=available && gateway.mayTest(r),modifier=Modifier.fillMaxWidth()){Icon(AppIcons.Tests,null);Spacer(Modifier.width(8.dp));Text("Запустить тесты")}
            }} }
            if(!available || !gateway.mayTest(r))item {Text(if(blocked)"Повторный запуск заблокирован: задание выполняется или итог неизвестен." else "Для запуска нужны готовый помощник и проверенные ветка/origin.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            val test=r.optJSONObject("test_job")
            if(test!=null && test.optString("state") in RepoPolicy.terminalStates)item {SectionLabel("Последний результат");Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(MonitorPolicy.label(test.optString("state"),false,false),style=MaterialTheme.typography.titleMedium)
                test.optJSONObject("tests")?.let {counts->Text("${counts.optLong("passed")} из ${counts.optLong("total")} успешно",style=MaterialTheme.typography.headlineSmall);Text("Ошибок ${counts.optLong("failed")} · пропущено ${counts.optLong("skipped")}",style=MaterialTheme.typography.bodyMedium)}
                val started=test.optDouble("started_at");val ended=test.optDouble("ended_at")
                if(ended>started && started>0)Text("${((ended-started)/60).toInt()} мин ${((ended-started)%60).toInt()} с · exit ${test.optString("exit_code")}",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={onJournal(test.optString("job_id"))}){Text("Открыть результат и журнал")}
            }} }
            item {TextButton(onClick=onWait){Text("Настройки ожидания")}}
        }
        if(r.optString("job_id").isNotEmpty() && (blocked || (tool=="git" && r.optString("action")!="TEST")))item {JobMonitor(context,r,permitted,onJournal)}
    }
    if(confirmSync)AlertDialog(onDismissRequest={confirmSync=false},title={Text("Получить изменения?")},text={Text("${project.name}: ветка, origin и чистота проверятся повторно. Только fast-forward, без отправки на GitHub.")},confirmButton={TextButton(onClick={confirmSync=false;gateway.start("SYNC",project.id,project.path)}){Text("Получить")}},dismissButton={TextButton(onClick={confirmSync=false}){Text("Отложить")}})
    if(changedFiles)AlertDialog(onDismissRequest={changedFiles=false},title={Text("Изменённые файлы")},text={Column {Text(snap?.optJSONArray("changed_files")?.let {a->(0 until a.length()).joinToString("\n"){a.optString(it)}} ?: "Список недоступен");Text("Просмотр содержимого diff пока не подключён.",style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={changedFiles=false}){Text("Закрыть")}})
}
