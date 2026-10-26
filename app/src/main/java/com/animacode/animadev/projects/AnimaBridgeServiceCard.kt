package com.animacode.animadev.projects

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    LaunchedEffect(termuxReady) {
        if (termuxReady && record.phase != "REQUESTED") gateway.request("status")
        while (true) {
            delay(700L)
            record = store.read()
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
        "running" -> if (english) "Service running" else "Служба работает"
        "stopped" -> if (english) "Service stopped" else "Служба остановлена"
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
            if (record.message.isNotEmpty()) {
                Text(record.message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
