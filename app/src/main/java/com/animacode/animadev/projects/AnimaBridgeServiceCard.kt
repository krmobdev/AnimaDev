package com.animacode.animadev.projects

import android.content.Context
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import com.animacode.animadev.R
import com.animacode.animadev.termux.BridgeQuickTileService
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.animacode.animadev.termux.BridgeTilePresence
import com.animacode.animadev.termux.BridgeWidgetProvider
import com.animacode.animadev.termux.BridgeServiceGateway
import com.animacode.animadev.termux.BridgeServicePolicy
import com.animacode.animadev.termux.BridgeServiceStore
import kotlinx.coroutines.delay

/** Only controls the registered runit service; never hard-codes the agent directory. */
@Composable
fun AnimaBridgeServiceCard(context: Context, termuxReady: Boolean) {
    val english = AppLanguage.english(context)
    val gateway = remember(context) { BridgeServiceGateway(context) }
    val store = remember(context) { BridgeServiceStore(context) }
    var record by remember { mutableStateOf(store.read()) }
    var tileAdded by remember { mutableStateOf(BridgeTilePresence.isAdded(context)) }
    val widgetManager = remember(context) { AppWidgetManager.getInstance(context) }
    var widgetCount by remember { mutableIntStateOf(0) }
    var pinSupported by remember { mutableStateOf(false) }
    var installNote by remember { mutableStateOf("") }
    fun refreshInstallState() {
        tileAdded = BridgeTilePresence.isAdded(context)
        widgetCount = widgetManager.getAppWidgetIds(
            ComponentName(context, BridgeWidgetProvider::class.java),
        ).size
        pinSupported = widgetManager.isRequestPinAppWidgetSupported
    }
    LaunchedEffect(termuxReady) {
        refreshInstallState()
        if (termuxReady && record.phase != "REQUESTED") gateway.request("status")
        while (true) {
            delay(700L)
            record = store.read()
            refreshInstallState()
            // 'sv up/down/restart' returning zero confirms acceptance, not health.
            // Exactly one follow-up 'sv status' confirms the observed state.
            if (termuxReady && record.phase == "DONE" && record.action != "status") {
                gateway.request("status")
            }
        }
    }
    val pending = record.phase == "REQUESTED"
    fun run(action: String) {
        if (termuxReady) {
            gateway.request(action)
            record = store.read()
        }
    }
    val status = when (record.serviceState) {
        "running" -> if (english) "Agent process running" else "Процесс агента запущен"
        "ephemeral" -> if (english) "Running, autostart disabled" else "Работает, автозапуск отключён"
        "stopped" -> if (english) "Service stopped" else "Служба остановлена"
        "supervisor_off" -> if (english) "Supervisor not running" else "Диспетчер runit не запущен"
        "conflict" -> if (english) "Agent conflict" else "Конфликт с ручным агентом"
        "starting" -> if (english) "Agent starting" else "Агент запускается"
        else -> if (english) "Status not confirmed" else "Состояние не подтверждено"
    }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AnimaBridge", style = MaterialTheme.typography.titleLarge)
            Text(status, style = MaterialTheme.typography.titleMedium)
            Text(
                if (english) "Registered Termux service · runit. The agent path is configured in Termux, not in AnimaDev."
                else "Служба Termux · runit. Путь к агенту хранится в Termux, не в AnimaDev.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (pending) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    onClick = {run("status")},
                    enabled = termuxReady && BridgeServicePolicy.permits("status", record.serviceState, pending),
                ) { Text(if (english) "Check" else "Проверить", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Button(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    onClick = {run("up")},
                    enabled = termuxReady && BridgeServicePolicy.permits("up", record.serviceState, pending),
                ) { Text(if (english) "Start" else "Запустить", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    onClick = {run("down")},
                    enabled = termuxReady && BridgeServicePolicy.permits("down", record.serviceState, pending),
                ) { Text(if (english) "Stop" else "Остановить", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Button(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    onClick = {run("restart")},
                    enabled = termuxReady && BridgeServicePolicy.permits("restart", record.serviceState, pending),
                ) { Text(if (english) "Restart" else "Перезапустить", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                if (english) "Quick access" else "Быстрый доступ",
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                enabled = !tileAdded,
                onClick = {
                    try {
                        val statusBar = context.getSystemService(StatusBarManager::class.java)
                        if (statusBar == null) {
                            installNote = context.getString(R.string.bridge_tile_unavailable)
                        } else {
                            statusBar.requestAddTileService(
                                ComponentName(context, BridgeQuickTileService::class.java),
                                context.getString(R.string.bridge_tile_label),
                                Icon.createWithResource(context, R.drawable.ic_bridge_tile),
                                context.mainExecutor,
                            ) { result ->
                                when (result) {
                                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED,
                                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> {
                                        BridgeTilePresence.setAdded(context, true)
                                        tileAdded = true
                                        installNote = ""
                                    }
                                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                                        installNote = context.getString(R.string.bridge_tile_declined)
                                    else -> installNote = context.getString(R.string.bridge_tile_unavailable)
                                }
                            }
                        }
                    } catch (_: RuntimeException) {
                        installNote = context.getString(R.string.bridge_tile_unavailable)
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7CF5AA),
                    contentColor = Color(0xFF10291D),
                    disabledContainerColor = Color(0xFF26543D),
                    disabledContentColor = Color(0xFFB1FFD0),
                ),
            ) {
                Text(
                    if (tileAdded) "✓ " + context.getString(R.string.bridge_tile_added)
                    else "＋ " + context.getString(R.string.bridge_add_tile),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (tileAdded) {
                Text(
                    context.getString(R.string.bridge_tile_remove_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                enabled = pinSupported,
                onClick = {
                    try {
                        val accepted = widgetManager.requestPinAppWidget(
                            ComponentName(context, BridgeWidgetProvider::class.java),
                            null, null,
                        )
                        if (!accepted)
                            installNote = context.getString(R.string.bridge_widget_manual_hint)
                        else installNote = ""
                    } catch (_: RuntimeException) {
                        installNote = context.getString(R.string.bridge_widget_manual_hint)
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2B5942),
                    contentColor = Color(0xFFC5FFDC),
                ),
            ) {
                Text(
                    "＋ " + context.getString(
                        if (widgetCount > 0) R.string.bridge_add_another_widget
                        else R.string.bridge_pin_widget,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (widgetCount > 0) {
                Text(
                    context.getString(R.string.bridge_widget_count, widgetCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9DFDC0),
                )
            }
            if (!pinSupported) {
                Text(
                    context.getString(R.string.bridge_widget_manual_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (installNote.isNotBlank()) {
                Text(
                    installNote, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (record.message.isNotEmpty()) {
                Text(record.message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (record.serviceState == "ephemeral") {
                Text(
                    if (english) "The service is normally down: after the runit supervisor restarts, the agent will not start automatically. Tap Start to enable persistent startup without restarting the running agent."
                    else "Служба настроена как normally down: после перезапуска runit агент не поднимется сам. Нажмите «Запустить», чтобы включить автозапуск без перезапуска текущего агента.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (record.serviceState == "running") {
                Text(
                    if (english) "Only the local Python process was checked. Connection to the AnimaBridge server was not verified."
                    else "Проверен только локальный процесс Python. Связь с сервером AnimaBridge не проверялась.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (record.serviceState == "supervisor_off") {
                Text(
                    if (english) "Tap Start: AnimaDev will initialize runit silently via Termux RUN_COMMAND. You do not need to open Termux."
                    else "Нажмите «Запустить»: AnimaDev поднимет runit через RUN_COMMAND в фоне. Открывать окно Termux не нужно.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (record.serviceState == "conflict") {
                Text(
                    if (english) "A manual agent is already active. Stop the supervised wrapper safely; do not start a duplicate."
                    else "Обнаружен ручной агент. Не запускайте второй экземпляр; можно остановить оболочку службы.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (record.serviceState == "unknown") {
                Text(
                    if (english) "First install and register the animabridge service in Termux. A manual agent cannot be adopted automatically."
                    else "Сначала зарегистрируйте службу animabridge в Termux. Запущенный вручную агент нельзя автоматически перевести под управление службы.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
