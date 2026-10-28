package com.animacode.animadev.projects

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Manually sign a chosen APK with the same secure Termux channel as Release. */
@Composable
fun ManualApkSigningPanel(
    context: Context, project: LocalProject, permitted: Boolean, termuxReady: Boolean,
) {
    val keyring = remember(context) { SigningKeyring(context) }
    val gateway = remember(context) { ManualApkSignGateway(context) }
    val store = remember(context) { ManualApkSignStore(context) }
    val stateGeneration = ManualApkSignStore.generation.intValue
    val job = remember(stateGeneration) { store.read() }
    val scope = rememberCoroutineScope()
    var keyRevision by remember(project.id) { mutableIntStateOf(0) }
    LaunchedEffect(project.id) {
        keyring.importLegacy(project.id)
        keyRevision++
    }
    val keygenGeneration = KeyGeneratorStore.generation.intValue
    LaunchedEffect(keygenGeneration) {
        if (KeyGeneratorStore(context).read().optString("state") == "SUCCEEDED") keyRevision++
    }
    val profiles = remember(keyRevision) { keyring.profiles() }
    var selectedId by remember(project.id) { mutableStateOf("") }
    val chosen = profiles.firstOrNull { it.id == selectedId }
        ?: profiles.firstOrNull { it.id == keyring.defaultId(project.id) }
        ?: profiles.firstOrNull()
    var source by remember(project.id) { mutableStateOf<Uri?>(null) }
    var sourceLabel by remember(project.id) { mutableStateOf("") }
    var destination by remember(project.id) { mutableStateOf<Uri?>(null) }
    var destinationLabel by remember(project.id) { mutableStateOf("") }
    var keySource by remember(project.id) { mutableStateOf<Uri?>(null) }
    var keyFilename by remember(project.id) { mutableStateOf("") }
    var title by remember(project.id) { mutableStateOf("") }
    var alias by remember(project.id) { mutableStateOf("") }
    var storePassword by remember(project.id, chosen?.id) { mutableStateOf("") }
    var keyPassword by remember(project.id, chosen?.id) { mutableStateOf("") }
    var notice by remember(project.id) { mutableStateOf("") }
    var submitting by remember(project.id) { mutableStateOf(false) }
    val saved = chosen?.let { profile ->
        remember(project.id, profile.id, keyRevision) {
            keyring.credentials(profile, project.id)
        }
    }

    fun label(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else ""
        }.orEmpty()
    }.getOrDefault("")

    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = label(uri)
            if (!name.endsWith(".apk", true)) notice = "Нужен файл .apk."
            else {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                source = uri
                sourceLabel = name
                notice = ""
            }
        }
    }
    val destPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            destination = uri
            destinationLabel = label(uri).ifBlank { "Выбранный APK" }
            notice = ""
        }
    }
    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = label(uri)
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (!granted || !SigningPolicy.isKeyName(name))
                notice = "Выберите доступный JKS или .keystore через Android."
            else {
                keySource = uri
                keyFilename = name
                title = name
                notice = "Введите alias и сохраните новый профиль."
            }
        }
    }
    val running = submitting || job.optString("state") in ManualApkSignStore.running
    val releaseBusy = ReleaseStore(context).busy()

    var showGenerator by remember(project.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = { showGenerator = !showGenerator },
            modifier = Modifier.fillMaxWidth()) {
            Text(if (showGenerator) "Скрыть генератор JKS" else "Создать новый JKS + резервную копию 7z")
        }
        if (showGenerator) KeyGeneratorPanel(context, project, permitted, termuxReady)
        Card(colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Подпись готового APK", style = MaterialTheme.typography.titleLarge)
                Text("Без Gradle: выберите неподписанный APK, ключ и файл сохранения. Оригинал останется неизменным.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { sourcePicker.launch(arrayOf("*/*")) },
                    enabled = !running, modifier = Modifier.fillMaxWidth()) {
                    Text(if (source == null) "Выбрать исходный APK" else "Заменить исходный APK")
                }
                Text(if (source == null) "Исходный файл не выбран" else sourceLabel,
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    val stem = sourceLabel.removeSuffix(".apk").removeSuffix(".APK")
                        .take(70).ifBlank { "application" }
                    destPicker.launch(stem + "-signed.apk")
                }, enabled = source != null && !running,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Выбрать куда сохранить")
                }
                Text(if (destination == null) "Файл для сохранения не выбран"
                    else "Результат: " + destinationLabel,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        Card(colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ключи подписи", style = MaterialTheme.typography.titleLarge)
                if (profiles.isEmpty()) Text("Добавьте ключ JKS или используйте уже настроенный ключ Release.")
                profiles.forEach { profile ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = profile.id == chosen?.id,
                            onClick = { selectedId = profile.id },
                            enabled = !running && keyring.hasAccess(profile))
                        Column(Modifier.weight(1f)) {
                            Text(profile.title, style = MaterialTheme.typography.titleSmall)
                            Text(profile.fileName + " · " + profile.alias +
                                if (profile.id == keyring.defaultId(project.id)) " · Основной" else "",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (chosen != null) {
                    OutlinedButton(onClick = {
                        notice = if (keyring.setDefault(project.id, chosen)) {
                            keyRevision++
                            "Основной ключ проекта изменён; Release использует его автоматически."
                        } else "Не удалось изменить основной ключ."
                    }, enabled = !running && chosen.id != keyring.defaultId(project.id),
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Сделать выбранный ключ основным")
                    }
                    TextButton(onClick = {
                        if (keyring.remove(chosen.id)) {
                            keyRevision++
                            selectedId = ""
                            notice = "Профиль удалён из списка. Сам файл JKS не удалён."
                        }
                    }, enabled = !running && chosen.id != keyring.defaultId(project.id)) {
                        Text("Убрать выбранный ключ из списка")
                    }
                }
                HorizontalDivider()
                OutlinedButton(onClick = { keyPicker.launch(arrayOf("*/*")) },
                    enabled = !running, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Добавить JKS в библиотеку")
                }
                if (keySource != null) {
                    OutlinedTextField(value = title, onValueChange = { title = it },
                        label = { Text("Название ключа") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = alias, onValueChange = { alias = it },
                        label = { Text("Alias внутри JKS") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        val added = keyring.add(title, keySource!!, keyFilename, alias)
                        if (added == null) notice = "Не удалось сохранить профиль. Проверьте alias."
                        else {
                            if (profiles.isEmpty()) keyring.setDefault(project.id, added)
                            selectedId = added.id
                            keyRevision++
                            keySource = null
                            notice = "Ключ добавлен. Пароли не сохранены."
                        }
                    }, enabled = !running && alias.isNotBlank() && title.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()) { Text("Сохранить профиль ключа") }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Пароль и подпись", style = MaterialTheme.typography.titleLarge)
                Text(if (saved != null) "Пароль для этого ключа сохранён в Android Keystore."
                    else "Пароль вводится для операции. Можно сохранить его защищённо.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(storePassword, { storePassword = it },
                    label = { Text("Пароль JKS" ) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !running && chosen != null, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(keyPassword, { keyPassword = it },
                    label = { Text("Пароль ключа (если другой)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !running && chosen != null, modifier = Modifier.fillMaxWidth())
                if (chosen != null) {
                    if (saved == null) {
                        OutlinedButton(onClick = {
                            notice = if (keyring.saveCredentials(chosen, storePassword, keyPassword))
                                "Пароль зашифрован и сохранён. Правильность проверяется при подписи."
                            else "Не удалось сохранить пароль."
                            keyRevision++
                        }, enabled = !running && storePassword.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Сохранить пароль защищённо (необязательно)")
                        }
                    } else {
                        TextButton(onClick = {
                            keyring.forgetCredentials(chosen)
                            keyRevision++
                            notice = "Сохранённые пароли профиля удалены."
                        }, enabled = !running) { Text("Удалить сохранённые пароли") }
                    }
                }
                Button(onClick = {
                    val profile = chosen ?: return@Button
                    val from = source ?: return@Button
                    val to = destination ?: return@Button
                    val password = storePassword.ifBlank { saved?.storePassword.orEmpty() }
                    val keyPass = if (storePassword.isNotEmpty())
                        keyPassword else keyPassword.ifEmpty { saved?.keyPassword.orEmpty() }
                    storePassword = ""
                    keyPassword = ""
                    submitting = true
                    notice = "Готовим безопасную подпись..."
                    scope.launch {
                        try { notice = gateway.launch(project.id, from, to, profile, password, keyPass) }
                        finally { submitting = false }
                    }
                }, enabled = !running && chosen != null && source != null && destination != null &&
                    (storePassword.isNotEmpty() || saved != null) && permitted && termuxReady && !releaseBusy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                    Text("Подписать и проверить APK")
                }
                Text("Если APK уже подписан (например, Debug), повторная подпись будет отклонена. Смена сертификата может помешать обновлению установленного приложения.",
                    style = MaterialTheme.typography.bodySmall)
                if (job.optString("state") != "") {
                    HorizontalDivider()
                    Text("Последняя операция: " + job.optString("state") + " · " + job.optString("phase"))
                    Text(job.optString("message"), style = MaterialTheme.typography.bodySmall)
                    if (job.optString("state") == "SUCCEEDED") {
                        Text("SHA-256: " + job.optString("sha256"),
                            style = MaterialTheme.typography.bodySmall)
                        Text("Подписант: " + job.optString("signer"),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (job.optString("state") == "UNKNOWN") {
                        Text("Результат неизвестен. Проверьте Загрузки и назначенный файл; повторная подпись пока заблокирована.",
                            color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = {
                            notice = if (store.acknowledgeUncertain())
                                "Неопределённый результат принят. Убедитесь, что Termux больше не подписывает предыдущий APK."
                            else "Не удалось изменить состояние."
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Я проверил результат · разблокировать новую попытку")
                        }
                    }
                }
                if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
