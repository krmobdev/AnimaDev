package com.animacode.animadev.repos

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.animacode.animadev.termux.TermuxGateway
import kotlinx.coroutines.delay

@Composable
fun RepositoriesScreen(context: Context, permitted: Boolean, termuxVersion: String?, onPermissionUpdated: () -> Unit, openJournal: (String)->Unit) {
    val store = remember { RepoStore(context) }
    val gateway = remember { RepoGateway(context) }
    var record by remember { mutableStateOf(store.read()) }
    var details by remember { mutableStateOf(false) }
    var confirmSync by remember { mutableStateOf(false) }
    var confirmInstall by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> record = store.read() }
        store.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { owner.lifecycle.removeObserver(observer); store.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val grant = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onPermissionUpdated() }
    val snapshot = record.optJSONObject("snapshot")
    val job = record.optJSONObject("job")
    val blocked = store.blocked()
    val pending = record.optBoolean("pending")
    val ready = record.optBoolean("ready")
    val controls = permitted && termuxVersion != null && ready && !blocked && !pending
    val dirty = snapshot?.optBoolean("dirty", true) ?: true
    val branchAllowed = snapshot?.optBoolean("branch_allowed") ?: false
    val remoteOk = snapshot?.optBoolean("remote_ok") ?: false
    val ahead = snapshot?.optInt("ahead") ?: 0
    val behind = snapshot?.optInt("behind") ?: 0
    val label = when {
        snapshot == null -> "Не проверено"
        !branchAllowed || !remoteOk -> "Ошибка"
        dirty -> "Есть локальные изменения"
        ahead > 0 -> "Есть локальные коммиты"
        behind > 0 -> "Есть обновления"
        else -> "Чисто"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Репозитории", style = MaterialTheme.typography.headlineMedium)
        JobMonitor(context,record,permitted,openJournal)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AnimaQuiz", style = MaterialTheme.typography.headlineSmall)
                Text("krmobdev/quizapp", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(color = Color(0xFFEBEFFC), shape = MaterialTheme.shapes.small) {
                    Text(label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                }
                Text("Ветка", style = MaterialTheme.typography.labelMedium)
                SelectionContainer { Text(snapshot?.optString("branch")?.ifEmpty { "Не проверено" } ?: "Не проверено", fontFamily = FontFamily.Monospace) }
                Text("Последний коммит: ${snapshot?.optString("local_commit")?.take(8)?.ifEmpty { "—" } ?: "—"}")
                Text("Origin: ${when { snapshot == null -> "не проверен"; remoteOk -> "совпадает с ожидаемым"; else -> "не совпадает" }}")
                if (snapshot?.optBoolean("checked") == true) Text("Удалённых новых: $behind · локальных новых: $ahead")
                if (termuxVersion == null) Text("Termux не найден. Восстановите установленный Termux.")
                else if (!permitted) Button(onClick = { grant.launch(TermuxGateway.PERMISSION) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Разрешить доступ к Termux")
                }
                if (!ready && permitted && termuxVersion != null) {
                    Text("Для Git нужен локальный помощник. Он устанавливается из этого APK вне QuizApp и не зависит от серверов.")
                    Button(onClick = { confirmInstall = true }, enabled = !pending && !blocked,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Настроить локальный помощник") }
                }
                OutlinedButton(onClick = { gateway.start("STATUS", RepoGateway.PROJECT, com.animacode.animadev.projects.ProjectPolicy.QUIZ) }, enabled = controls,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Обновить состояние") }
                Button(onClick = { gateway.start("CHECK", RepoGateway.PROJECT, com.animacode.animadev.projects.ProjectPolicy.QUIZ) }, enabled = controls && (snapshot == null || (branchAllowed && remoteOk)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Проверить обновления") }
                OutlinedButton(onClick = { confirmSync = true }, enabled = gateway.maySync(record),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Синхронизировать") }
                Text("CHECK проверяет GitHub. SYNC получает коммиты после подтверждения.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { gateway.start("TEST", RepoGateway.PROJECT, com.animacode.animadev.projects.ProjectPolicy.QUIZ) }, enabled = controls && gateway.mayTest(record),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Запустить тесты") }
                Text("Тесты проверяют проект без изменения исходников.", style = MaterialTheme.typography.bodySmall)
                val reason = when {
                    blocked -> "Новый запуск заблокирован до подтверждения результата текущей операции."
                    snapshot == null -> "Сначала обновите состояние репозитория."
                    !branchAllowed -> "Разрешена только test/decomposition-v1. Ветку автоматически не переключаем."
                    !remoteOk -> "Проверьте origin: ожидается git@github.com:krmobdev/quizapp.git."
                    dirty -> "Синхронизация недоступна: есть локальные изменения. Они останутся на месте."
                    ahead > 0 -> "Есть локальные коммиты; автоматическое объединение запрещено."
                    snapshot.optBoolean("checked") && behind == 0 -> "Новых удалённых коммитов нет."
                    else -> "Сначала проверьте обновления. Перед синхронизацией состояние будет проверено повторно."
                }
                Text(reason, style = MaterialTheme.typography.bodySmall)

            }
        }
        val testJob = if (record.optString("action") == "TEST" && blocked) org.json.JSONObject(job?.toString() ?: "{}").put("state", record.optString("state")).put("job_id", record.optString("job_id")) else record.optJSONObject("test_job")
        if (testJob != null) {
            Text("Последние unit-тесты", style = MaterialTheme.typography.titleMedium)
            Text(RepoPolicy.testState(testJob.optString("state")))
            val counts = testJob.optJSONObject("tests")
            if (counts != null) {
                Text("Всего: ${counts.optLong("total")} · успешно: ${counts.optLong("passed")} · провалено: ${counts.optLong("failed")} · пропущено: ${counts.optLong("skipped")}")
                Text("Время тестов по JUnit: ${counts.optLong("duration_ms")} мс")
            } else Text("Статистика ещё не подтверждена JUnit XML.")
        }
        Text("AnimaDev 0.3.1 · Git и unit-тесты в Termux", style = MaterialTheme.typography.bodySmall)
    }
    if (confirmInstall) AlertDialog(onDismissRequest = { confirmInstall = false }, title = { Text("Настроить локальный помощник?") },
        text = { Text("AnimaDev сохранит встроенный проверенный runner в ~/.local/share/animadev. Настройки Termux и файлы QuizApp не меняются.") },
        confirmButton = { com.animacode.animadev.projects.ActionButton(compact=true,onClick = { confirmInstall = false; gateway.install() }) { Text("Настроить") } },
        dismissButton = { com.animacode.animadev.projects.ActionButton(compact=true,onClick = { confirmInstall = false }) { Text("Отмена") } })
    if (confirmSync) AlertDialog(onDismissRequest = { confirmSync = false }, title = { Text("Получить изменения AnimaQuiz?") },
        text = { Text("Только test/decomposition-v1 и fast-forward. Runner повторно проверит ветку, origin и локальные изменения. При любом расхождении остановится.") },
        confirmButton = { com.animacode.animadev.projects.ActionButton(compact=true,onClick = { confirmSync = false; gateway.start("SYNC", RepoGateway.PROJECT, com.animacode.animadev.projects.ProjectPolicy.QUIZ) }) { Text("Синхронизировать") } },
        dismissButton = { com.animacode.animadev.projects.ActionButton(compact=true,onClick = { confirmSync = false }) { Text("Отмена") } })
}
private fun Sequence<String>.takeLastLines(count: Int) = toList().takeLast(count).joinToString("\n")
