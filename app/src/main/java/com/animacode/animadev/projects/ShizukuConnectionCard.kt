package com.animacode.animadev.projects

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle.State
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.animacode.animadev.shizuku.ShizukuConnection
import com.animacode.animadev.shizuku.ShizukuState

@Composable
fun ShizukuConnectionCard(context: Context) {
    val english = AppLanguage.english(context)
    val connection = remember(context.applicationContext) {
        ShizukuConnection(context.applicationContext)
    }
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var active by remember(owner) {mutableStateOf(owner.lifecycle.currentState.isAtLeast(State.RESUMED))}
    var checking by remember {mutableStateOf(false)}
    var pingMs by remember {mutableStateOf<Long?>(null)}
    var checked by remember {mutableStateOf(false)}
    suspend fun checkService() {
        if(checking)return
        checking=true
        try {
            pingMs=withTimeoutOrNull(2500L) {connection.pingMillis()}
            checked=true
            connection.refresh()
        } finally {checking=false}
    }
    LaunchedEffect(connection,active) {
        while(active) {
            checkService()
            delay(5000L)
        }
    }
    DisposableEffect(connection, owner) {
        connection.start()
        val watcher = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) active=true
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) active=false
        }
        owner.lifecycle.addObserver(watcher)
        onDispose {
            owner.lifecycle.removeObserver(watcher)
            active=false
            connection.stop()
        }
    }
    val info = connection.info
    val connected = info.state == ShizukuState.CONNECTED
    val stateText = when (info.state) {
        ShizukuState.NOT_INSTALLED -> if (english) "Shizuku is not installed" else "Shizuku не установлен"
        ShizukuState.SERVICE_STOPPED -> if (english) "Service not running" else "Служба не запущена"
        ShizukuState.PERMISSION_REQUIRED -> if (english) "Permission required" else "Нужно разрешение"
        ShizukuState.PERMISSION_DENIED -> if (english) "Permission denied in Shizuku" else "Доступ запрещён в Shizuku"
        ShizukuState.CONNECTED -> if (english) "Connected" else "Подключено"
        ShizukuState.UNSUPPORTED -> if (english) "Shizuku version unsupported" else "Версия Shizuku не поддерживается"
        ShizukuState.ERROR -> if (english) "Connection error" else "Ошибка подключения"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("shizuku_connection_card"),
        shape = RoundedCornerShape(22.dp),
        color = if (connected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Shizuku", style = MaterialTheme.typography.titleLarge)
            Text(stateText, style = MaterialTheme.typography.titleMedium)
            Text(
                if (connected) {
                    val user = when (info.uid) {
                        2000 -> "shell"
                        0 -> "root"
                        else -> "UID " + info.uid
                    }
                    val detail = "API " + info.apiVersion + " · " + user
                    if (english) "Service available · " + detail else "Служба доступна · " + detail
                } else if (english) {
                    "Connect to the running Shizuku service directly, without Wi-Fi ADB or Termux."
                } else {
                    "Подключение к работающей службе напрямую, без Wi-Fi ADB и Termux."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (info.state == ShizukuState.PERMISSION_REQUIRED) {
                    Button(onClick = connection::requestPermission) {
                        Text(if (english) "Allow access" else "Разрешить доступ")
                    }
                }
                OutlinedButton(
                    onClick = { scope.launch {checkService()} },
                    enabled = !checking,
                    modifier = Modifier.testTag("shizuku_refresh")
                ) {
                    Text(if (english) "Check" else "Проверить")
                }
                if(checking) CircularProgressIndicator(
                    modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(when {
                    !checked -> if(english) "Waiting for ping" else "Ожидаем пинг"
                    pingMs != null -> "Пинг: ${pingMs} мс"
                    else -> if(english) "No response" else "Нет ответа"
                },style=MaterialTheme.typography.labelMedium,
                  modifier=Modifier.testTag("shizuku_ping"))
            }
            if (info.state == ShizukuState.NOT_INSTALLED ||
                info.state == ShizukuState.SERVICE_STOPPED ||
                info.state == ShizukuState.PERMISSION_DENIED) {
                val manager = connection.managerIntent()
                if (manager != null) {
                    OutlinedButton(onClick = { context.startActivity(manager) }) {
                        Text(if (english) "Open Shizuku" else "Открыть Shizuku")
                    }
                }
            }
            Text(
                if (english) "AnimaDev requests access only when you tap Allow; no privileges are granted automatically."
                else "AnimaDev запрашивает доступ только по нажатию. Разрешения автоматически не выдаются.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
