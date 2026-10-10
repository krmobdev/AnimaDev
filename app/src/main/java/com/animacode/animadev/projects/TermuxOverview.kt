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

@Composable fun TermuxOverview(context:Context,permitted:Boolean,version:String?,onPermissionUpdated:()->Unit,
    onOpenSetup:()->Unit={}) {
    val gateway=remember { CatalogGateway(context) };val data=rememberCatalogRecord(context);val env=data.getJSONObject("environment")
    val grant=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {onPermissionUpdated()}
    var notifications by remember {mutableStateOf(context.getSystemService(NotificationManager::class.java).areNotificationsEnabled())}
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner) {val l=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME)notifications=context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()};owner.lifecycle.addObserver(l);onDispose {owner.lifecycle.removeObserver(l)}}
    val notify=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {notifications=context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Icon(AppIcons.Terminal,null,tint=MaterialTheme.colorScheme.onPrimaryContainer);Spacer(Modifier.width(12.dp));Text(if(version==null)"Termux не найден" else if(!permitted)"Требуется доступ" else "Termux установлен",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)}
            Text("${version ?: "Версия недоступна"} · RUN_COMMAND ${if(permitted)"разрешён" else "запрещён"}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)
            if(!permitted && version!=null)Button(onClick={grant.launch(TermuxGateway.PERMISSION)}){Text("Разрешить доступ")}
            else com.animacode.animadev.projects.ActionButton(onClick={gateway.read("ENV")},enabled=permitted && version!=null && !data.optBoolean("pending")){Icon(AppIcons.Refresh,null);Spacer(Modifier.width(8.dp));Text("Проверить окружение")}
        }} }
        if(data.optBoolean("pending"))item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(data.optString("message").isNotEmpty())item {Text(data.optString("message"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item {SectionLabel("Инструменты");Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
            listOf("python" to "Python","git" to "Git","java" to "Java").forEachIndexed {i,(key,label)->DetailRow(label,env.optString(key).ifEmpty {"Не проверено"});if(i<2)HorizontalDivider(Modifier.padding(horizontal=16.dp))}
        } }
        item {SectionLabel("Android и Gradle");Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {DetailRow("Android SDK",env.optString("sdk").ifEmpty {"Не проверено"});HorizontalDivider(Modifier.padding(horizontal=16.dp));DetailRow("Сохранённые Gradle",env.optString("gradle").ifEmpty {"Не проверено"});DetailRow("Платформа",listOf(env.optString("platform"),env.optString("architecture")).filter {it.isNotBlank()}.joinToString(" · ").ifEmpty {"Не проверено"})} }
        item {SectionLabel("Уведомления");ListItem(headlineContent={Text(if(notifications)"Разрешены" else "Отключены")},supportingContent={Text("Не влияют на доступность Git и тестов")},trailingContent={IconButton(onClick={if(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)) else notify.launch(Manifest.permission.POST_NOTIFICATIONS)}){Icon(AppIcons.Settings,"Разрешение и настройки уведомлений")}}) }
        item {
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Локальная Android-сборка",style=MaterialTheme.typography.titleMedium)
                    Text("Пошаговая установка Java 21, Python, Git и Android SDK, настройка Gradle и AAPT2. Команды запускаются вручную в Termux после проверки.",
                        style=MaterialTheme.typography.bodySmall)
                    Button(onClick=onOpenSetup,modifier=Modifier.fillMaxWidth()) {
                        Text("Подготовка сборки · подробная инструкция")
                    }
                    Text("Автоматическая установка не запускается без подтверждения и проверки уже настроенного окружения.",
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {Text("Обновления пакетов и Wake Lock не выполняются автоматически.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
