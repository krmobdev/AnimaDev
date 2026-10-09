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
fun RepositoriesScreen(context: Context, permitted: Boolean, termuxVersion: String?, onPermissionUpdated: () -> Unit) {
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
    LaunchedEffect(resumed, permitted, record.optString("job_id"), record.optString("state"), record.optBoolean("pending")) {
        while (resumed && permitted) {
            delay(3_000)
            store.expire()
            val current = store.read()
            if (RepoPolicy.blocked(current.optString("job_id"), current.optString("state")) && !current.optBoolean("pending")) gateway.query()
            record = store.read()
            if (!current.optBoolean("pending") && !store.blocked()) break
        }
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
        blocked -> if (record.optString("state") == "UNKNOWN") "Результат неизвестен" else "Git-операция выполняется"
        snapshot == null -> "Не проверено"
        !branchAllowed || !remoteOk || record.optString("state") in setOf("FAILED", "TIMED_OUT", "INTERRUPTED") -> "Ошибка"
        dirty -> "Есть локальные изменения"
        ahead > 0 -> "Есть локальные коммиты"
        behind > 0 -> "Есть обновления"
        else -> "Чисто"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Репозитории", style = MaterialTheme.typography.headlineMedium)
        Text("Код на телефоне. Обновления под вашим контролем.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                if (blocked || pending) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (termuxVersion == null) Text("Termux не найден. Восстановите установленный Termux.")
                else if (!permitted) Button(onClick = { grant.launch(TermuxGateway.PERMISSION) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Разрешить доступ к Termux")
                }
                if (!ready && permitted && termuxVersion != null) {
                    Text("Для Git нужен локальный помощник. Он устанавливается из этого APK вне QuizApp и не зависит от серверов.")
                    Button(onClick = { confirmInstall = true }, enabled = !pending && !blocked,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Настроить локальный помощник") }
                }
                OutlinedButton(onClick = { gateway.start("STATUS") }, enabled = controls,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Обновить состояние") }
                Button(onClick = { gateway.start("CHECK") }, enabled = controls && (snapshot == null || (branchAllowed && remoteOk)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Проверить обновления") }
                OutlinedButton(onClick = { confirmSync = true }, enabled = gateway.maySync(record),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Синхронизировать") }
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
                if (blocked) OutlinedButton(onClick = { gateway.query() }, enabled = !pending && permitted && ready,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Уточнить состояние") }
            }
        }
        Text("Последняя операция", style = MaterialTheme.typography.titleMedium)
        Text(record.optString("message", "Пока не запускалась."))
        if (job != null) {
            val start = job.optDouble("started_at", Double.NaN)
            val end = job.optDouble("ended_at", Double.NaN)
            if (!start.isNaN() && !end.isNaN()) Text("Длительность: ${((end - start) * 1000).toLong()} мс")
            if (job.has("exit_code") && !job.isNull("exit_code")) Text("Код Git-операции: ${job.optInt("exit_code")}")
            else if (job.optString("state") in RepoPolicy.terminalStates) Text("Код Git не получен; успех не подтверждён.")
            if (job.optString("stdout").isNotBlank()) Text(job.optString("stdout").lineSequence().takeLastLines(5), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { details = !details }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (details) "Скрыть технические подробности" else "Технические подробности") }
        if (details) SelectionContainer {
            Column(Modifier.fillMaxWidth().background(Color.White, MaterialTheme.shapes.medium).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Runner ${record.optString("runner_version", if (ready) RepoGateway.VERSION else "не установлен")}")
                Text("Код причины: ${job?.optString("error_code")?.ifEmpty { "—" } ?: "—"}")
                // Git diagnostics are deliberately not rendered: show fixed classified messages instead.
                Text("Job ID: ${record.optString("job_id", "—")}", fontFamily = FontFamily.Monospace)
                Text("State: ${record.optString("state", "—")} · Action: ${record.optString("action", "—")}")
                Text("Короткий запрос Termux: exit=${record.optString("callback_exit", "—")}, err=${record.optString("termux_err", "—")}")
                Text("Рабочая папка: ~/quizapp\nЛокальное состояние: ~/.local/share/animadev")
                snapshot?.optJSONArray("changed_files")?.let { Text("Локальные изменения:\n$it", style = MaterialTheme.typography.bodySmall) }
                Text("stdout Git (журнал шагов):\n${job?.optString("stdout") ?: "—"}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text("stderr Termux: ${if (record.optString("transport_stderr").isBlank()) "пусто" else "Получена диагностика; используйте сообщение об ошибке выше."}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("AnimaDev 0.2.1 · Git через существующий SSH Termux", style = MaterialTheme.typography.bodySmall)
    }
    if (confirmInstall) AlertDialog(onDismissRequest = { confirmInstall = false }, title = { Text("Настроить локальный помощник?") },
        text = { Text("AnimaDev сохранит встроенный проверенный runner в ~/.local/share/animadev. Настройки Termux и файлы QuizApp не меняются.") },
        confirmButton = { TextButton(onClick = { confirmInstall = false; gateway.install() }) { Text("Настроить") } },
        dismissButton = { TextButton(onClick = { confirmInstall = false }) { Text("Отмена") } })
    if (confirmSync) AlertDialog(onDismissRequest = { confirmSync = false }, title = { Text("Получить изменения AnimaQuiz?") },
        text = { Text("Только test/decomposition-v1 и fast-forward. Runner повторно проверит ветку, origin и локальные изменения. При любом расхождении остановится.") },
        confirmButton = { TextButton(onClick = { confirmSync = false; gateway.start("SYNC") }) { Text("Синхронизировать") } },
        dismissButton = { TextButton(onClick = { confirmSync = false }) { Text("Отмена") } })
}
private fun Sequence<String>.takeLastLines(count: Int) = toList().takeLast(count).joinToString("\n")
