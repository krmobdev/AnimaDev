package com.animacode.animadev

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.animacode.animadev.termux.*
import com.animacode.animadev.repos.RepositoriesScreen
import com.animacode.animadev.repos.RepoStore
import com.animacode.animadev.repos.RepoPolicy
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var permitted by mutableStateOf(false)
    private var version by mutableStateOf<String?>(null)
    private val gateway by lazy { TermuxGateway(this) }

    override fun onResume() {
        super.onResume()
        permitted = gateway.permitted()
        version = gateway.installedVersion()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF3559D5), background = Color(0xFFF5F6FA),
                surface = Color.White, onSurface = Color(0xFF192033))) {
                var repositoryTab by rememberSaveable { mutableStateOf(true) }
                var confirmExit by rememberSaveable { mutableStateOf(false) }
                var operationMayContinue by rememberSaveable { mutableStateOf(false) }
                BackHandler {
                    val record = RepoStore(this@MainActivity).read()
                    operationMayContinue = RepoPolicy.blocked(record.optString("job_id"), record.optString("state")) || record.optBoolean("pending")
                    confirmExit = true
                }
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { repositoryTab = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Репозитории") }
                        TextButton(onClick = { repositoryTab = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Связь Termux") }
                    }
                    HorizontalDivider()
                    Box(Modifier.weight(1f)) {
                        if (repositoryTab) RepositoriesScreen(this@MainActivity, permitted, version) { permitted = gateway.permitted() }
                        else ProbeScreen()
                    }
                }
                if (confirmExit) AlertDialog(
                    onDismissRequest = { confirmExit = false },
                    title = { Text("Закрыть AnimaDev?") },
                    text = { Text(if (operationMayContinue)
                        "Задание проекта может ещё выполняться. Оно продолжится в Termux, а результат сохранится для следующего открытия приложения."
                        else "Состояние и результаты сохранятся для следующего открытия приложения.") },
                    confirmButton = { TextButton(onClick = { confirmExit = false; finishAndRemoveTask() }) { Text("Выйти") } },
                    dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Отмена") } }
                )
            }
        }
    }

    @Composable private fun ProbeScreen() {
        val store = remember { ProbeStore(this) }
        var state by remember { mutableStateOf(store.expire()) }
        val prefs = remember { getSharedPreferences("probe_v1", MODE_PRIVATE) }
        DisposableEffect(Unit) {
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                state = store.read()
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        LaunchedEffect(state.id, state.state) {
            while (state.state == "REQUESTED") {
                delay(1000)
                state = store.expire()
            }
        }
        val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permitted = gateway.permitted()
        }
        val running = state.state == "REQUESTED"
        val title = when {
            version == null -> "Termux не найден"
            !permitted -> "Нужно разрешение"
            running -> "Проверяем связь…"
            state.state == "SUCCESS" -> "Termux подключён"
            state.state == "UNKNOWN" -> "Ответ пока не получен"
            state.state == "ERROR" -> "Проверка не подтверждена"
            else -> "Связь не проверена"
        }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("AnimaDev", style = MaterialTheme.typography.headlineMedium)
                    Text("ПРОБА 01", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
                }
                Text("Диагностика Termux: не обязательна перед каждым действием. Проверку можно повторить после завершения предыдущей.", style = MaterialTheme.typography.bodyLarge)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge,
                            color = if (state.state == "SUCCESS" && permitted) Color(0xFF157347) else MaterialTheme.colorScheme.onSurface)
                        Text("Termux ${version ?: "—"} · RUN_COMMAND: ${if (permitted) "разрешён" else "не разрешён"}",
                            style = MaterialTheme.typography.bodyMedium)
                        if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { gateway.probe(); state = store.read() },
                            enabled = !running && permitted && version != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text(if (running) "Ожидаем ответ" else "Проверить")
                        }
                        if (version != null && !permitted) {
                            Button(onClick = { permissionRequest.launch(TermuxGateway.PERMISSION) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Разрешить доступ к Termux") }
                            Text("Это системное разрешение на запуск команд в Termux. AnimaDev выполняет только встроенную проверку.")
                        }
                        OutlinedButton(onClick = {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName")))
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Настройки разрешений") }
                    }
                }
                if (state.id.isNotEmpty()) {
                    Text("Последний запрос", style = MaterialTheme.typography.titleMedium)
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(state.id, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            Text("Exit code: ${state.exitCode?.toString() ?: "не получен"}  ·  Termux err: ${state.termuxError?.toString() ?: "не получен"}")
                            if (state.ended > 0) Text("Длительность: ${state.ended - state.started} мс")
                            if (state.truncated) Text("Ответ усечён; успех не подтверждён.")
                        }
                    }
                    OutputBlock("stdout", state.stdout)
                    OutputBlock("stderr", state.stderr)
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEBEFFC))) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Что делает проверка", style = MaterialTheme.typography.titleMedium)
                        Text("Запускает установленный Python в Termux. Он возвращает свой UID, рабочий каталог, версию и ID запроса. Команда не меняет файлы.")
                        Text("Если доступ разрешён, но ответ не приходит: в настройках Termux должна быть включена allow-external-apps = true. AnimaDev не может изменить её через Android sandbox.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("0.3.0 · Без серверов", style = MaterialTheme.typography.bodySmall)
            }
    }
    @Composable private fun OutputBlock(label: String, output: String) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            SelectionContainer {
                Text(output.ifEmpty { "(пусто / ещё не получено)" }, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth()
                        .background(Color.White, MaterialTheme.shapes.medium).padding(16.dp))
            }
        }
    }
}
