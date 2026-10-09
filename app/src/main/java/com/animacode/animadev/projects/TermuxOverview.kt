package com.animacode.animadev.projects

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.animacode.animadev.termux.TermuxGateway

@Composable fun TermuxOverview(context:Context,permitted:Boolean,version:String?,onPermissionUpdated:()->Unit) {
    val gateway=remember { CatalogGateway(context) };val data=rememberCatalogRecord(context);val env=data.getJSONObject("environment")
    val grant=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onPermissionUpdated() }
    var notifications by remember { mutableStateOf(context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) }
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner) { val l=LifecycleEventObserver { _,event->if(event==Lifecycle.Event.ON_RESUME)notifications=context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() };owner.lifecycle.addObserver(l);onDispose { owner.lifecycle.removeObserver(l) } }
    val notify=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifications=context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(if(version==null)"Termux не найден" else if(!permitted)"Нужно разрешение" else "Termux доступен",style=MaterialTheme.typography.titleLarge)
            Text("Версия ${version ?: "—"} · RUN_COMMAND ${if(permitted)"разрешён" else "запрещён"}",style=MaterialTheme.typography.bodyMedium)
            if(!permitted && version!=null)Button(onClick={grant.launch(TermuxGateway.PERMISSION)}){Text("Разрешить доступ")}
            FilledTonalButton(onClick={gateway.read("ENV")},enabled=permitted && version!=null && !data.optBoolean("pending")){Text(if(data.optBoolean("pending"))"Проверяем…" else "Обновить обзор")}
            if(data.optString("message").isNotEmpty())Text(data.optString("message"),style=MaterialTheme.typography.bodySmall)
        } } }
        item { Text("Окружение",style=MaterialTheme.typography.titleMedium) }
        listOf("python" to "Python","git" to "Git","java" to "Java","sdk" to "Android SDK","gradle" to "Gradle (сохранённые версии)","architecture" to "Архитектура").forEach { (key,label)->item { ListItem(headlineContent={Text(label)},supportingContent={Text(env.optString(key).ifEmpty { "Ещё не проверено" })}) } }
        item { Text("Обзор не запускает Gradle и не меняет пакеты. Данные обновляются по кнопке.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        item { HorizontalDivider();Text("Уведомления",style=MaterialTheme.typography.titleMedium);Text(if(notifications)"Разрешены" else "Отключены");OutlinedButton(onClick={if(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)) else notify.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text(if(notifications)"Настройки уведомлений" else "Разрешить уведомления") } }
        item { HorizontalDivider();Text("Позднее",style=MaterialTheme.typography.titleMedium);Text("Менеджер пакетов · обновления · Wake Lock",style=MaterialTheme.typography.bodyMedium);Text("Эти функции в Beta недоступны.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
