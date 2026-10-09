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
    if(!project.supported) { UnavailableTool(if(tool=="git")"Git" else "Тесты","Для ${project.name} ещё нет согласованного профиля runner. Действие не будет перенаправлено в AnimaQuiz.");return }
    val r=rememberRepoRecord(context);val gateway=remember { RepoGateway(context) };val store=remember { RepoStore(context) }
    val snap=r.optJSONObject("snapshot");val pending=r.optBoolean("pending");val blocked=store.blocked()
    val ready=r.optBoolean("ready");val available=ready && permitted && termuxVersion!=null && !pending && !blocked
    var confirmSync by rememberSaveable { mutableStateOf(false) };var changedFiles by rememberSaveable { mutableStateOf(false) }
    val grant=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { onPermissionUpdated() }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text(project.name,style=MaterialTheme.typography.titleLarge);Text(snap?.optString("branch")?.ifEmpty { "Ветка не проверена" } ?: "Ветка не проверена",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        if(!permitted)item { Button(onClick={grant.launch(com.animacode.animadev.termux.TermuxGateway.PERMISSION)}){Text("Разрешить Termux")} }
        if(termuxVersion==null)item { Text("Termux не найден. Действия недоступны.",color=MaterialTheme.colorScheme.error) }
        if(!ready)item { FilledTonalButton(onClick={gateway.install()},enabled=permitted && termuxVersion!=null && !pending && !blocked){Text(if(pending)"Подключаем…" else "Подключить помощник")};Text("История и выполняющееся задание сохраняются.",style=MaterialTheme.typography.bodySmall) }
        if(tool=="git") {
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                val status=when { snap==null->"Не проверено";!snap.optBoolean("remote_ok") || !snap.optBoolean("branch_allowed")->"Требуется проверка";snap.optBoolean("dirty")->"Есть локальные изменения";snap.optInt("ahead")>0->"Есть локальные коммиты";snap.optInt("behind")>0->"Есть обновления";else->"Чисто" }
                Text(status,style=MaterialTheme.typography.titleMedium)
                Text("Коммит ${snap?.optString("local_commit")?.take(8)?.ifEmpty { "—" } ?: "—"}")
                Text(if(snap?.optBoolean("remote_ok")==true)"Origin: krmobdev/quizapp" else "Origin не подтверждён",style=MaterialTheme.typography.bodySmall)
                if(snap?.optBoolean("checked")==true)Text("GitHub: +${snap.optInt("behind")} · локально: +${snap.optInt("ahead")}")
            } } }
            item { OutlinedButton(onClick={gateway.start("STATUS",project.id,project.path)},enabled=available,modifier=Modifier.fillMaxWidth()){Text("Обновить состояние")} }
            item { Button(onClick={gateway.start("CHECK",project.id,project.path)},enabled=available && (snap==null || (snap.optBoolean("remote_ok") && snap.optBoolean("branch_allowed"))),modifier=Modifier.fillMaxWidth()){Text("Проверить GitHub")} }
            item { FilledTonalButton(onClick={confirmSync=true},enabled=permitted && termuxVersion!=null && gateway.maySync(r),modifier=Modifier.fillMaxWidth()){Text("Получить изменения")} }
            item { Text(when { blocked->"Новое действие заблокировано: задание выполняется или итог неизвестен.";!ready->"Сначала подключите локального помощника.";snap==null->"Сначала обновите состояние.";!snap.optBoolean("branch_allowed")->"Эта ветка не разрешена профилем. Автоматического переключения нет.";!snap.optBoolean("remote_ok")->"Origin не соответствует профилю AnimaQuiz.";snap.optBoolean("dirty")->"Fast-forward заблокирован: сохранены локальные изменения.";snap.optInt("ahead")>0->"Есть локальные коммиты. Автоматическое объединение запрещено.";snap.optInt("behind")==0->"Проверьте GitHub, чтобы узнать о новых коммитах.";else->"Только подтверждённый fast-forward; без stash/reset/rebase." },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            if(snap?.optBoolean("dirty")==true)item { TextButton(onClick={changedFiles=true}){Text("Изменённые файлы")};Text("Просмотр содержимого diff — следующий этап. Обновление можно отложить, просто вернувшись к проекту.",style=MaterialTheme.typography.bodySmall) }
        } else {
            item { Text(":app:testDebugUnitTest",style=MaterialTheme.typography.titleMedium);Text("Offline · без daemon · повтор задач",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Button(onClick={gateway.start("TEST",project.id,project.path)},enabled=available && gateway.mayTest(r),modifier=Modifier.fillMaxWidth()){Text("Запустить тесты")} }
            if(!available || !gateway.mayTest(r))item { Text(if(blocked)"Задание уже выполняется или результат неизвестен. Повторный запуск закрыт." else "Нужны разрешение Termux, готовый помощник и подтверждённая ветка/origin.",style=MaterialTheme.typography.bodySmall) }
            item { TextButton(onClick=onWait){Text("Настройки ожидания")} }
            val test=r.optJSONObject("test_job")
            if(test!=null)item { Card { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Последний результат TEST",style=MaterialTheme.typography.titleMedium);Text(MonitorPolicy.label(test.optString("state"),false,false))
                val counts=test.optJSONObject("tests")
                if(counts!=null) { Text("Всего ${counts.optLong("total")} · успешно ${counts.optLong("passed")}");Text("Провалено ${counts.optLong("failed")} · пропущено ${counts.optLong("skipped")}");Text("JUnit: ${counts.optLong("duration_ms")} мс",style=MaterialTheme.typography.bodySmall) } else Text("Статистика JUnit пока не подтверждена.")
                TextButton(onClick={onJournal(test.optString("job_id"))}){Text("Результат и журнал")}
            } } }
        }
        if(r.optString("job_id").isNotEmpty() && (blocked || (tool=="tests")== (r.optString("action")=="TEST")))item { JobMonitor(context,r,permitted,onJournal) }
        if(r.optString("job_id").isNotEmpty())item { TextButton(onClick={onJournal(r.optString("job_id"))}){Text("Журнал / поделиться диагностикой")} }
    }
    if(confirmSync)AlertDialog(onDismissRequest={confirmSync=false},title={Text("Получить изменения ${project.name}?")},text={Text("Ветка, origin и чистота проверятся повторно. Только fast-forward, без изменения main и без отправки коммитов.")},confirmButton={TextButton(onClick={confirmSync=false;gateway.start("SYNC",project.id,project.path)}){Text("Получить")}},dismissButton={TextButton(onClick={confirmSync=false}){Text("Отложить")}})
    if(changedFiles)AlertDialog(onDismissRequest={changedFiles=false},title={Text("Локальные изменения")},text={Text(snap?.optJSONArray("changed_files")?.let { a->(0 until a.length()).joinToString("\n"){a.optString(it)} } ?: "Список пока недоступен")},confirmButton={TextButton(onClick={changedFiles=false}){Text("Закрыть")}})
}
