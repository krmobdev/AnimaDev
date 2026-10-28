package com.animacode.animadev.projects

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.security.SecureRandom

/** Creates a new project JKS, separately saved JKS and 7z AES-256 recovery copy. */
@Composable fun KeyGeneratorPanel(context:Context,project:LocalProject,permitted:Boolean,termuxReady:Boolean) {
    val store=remember(context){KeyGeneratorStore(context)}
    val gateway=remember(context){KeyGeneratorGateway(context)}
    val version=KeyGeneratorStore.generation.intValue
    val last=remember(version){store.read()}
    val scope=rememberCoroutineScope()
    var name by remember(project.id){mutableStateOf(project.name.take(40)+" Release")}
    var alias by remember(project.id){mutableStateOf("release")}
    var bits by remember(project.id){mutableIntStateOf(4096)}
    var customName by remember(project.id){mutableStateOf("")}
    var secret by remember(project.id){mutableStateOf("")}
    var secretAgain by remember(project.id){mutableStateOf("")}
    var saveAsDefault by remember(project.id){mutableStateOf(true)}
    var jksFile by remember(project.id){mutableStateOf<Uri?>(null)}
    var archiveFile by remember(project.id){mutableStateOf<Uri?>(null)}
    var message by remember(project.id){mutableStateOf("")}
    var inFlight by remember(project.id){mutableStateOf(false)}
    val running=inFlight || last.optString("state") in KeyGeneratorStore.busyStates
    val keyPicker=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if(uri!=null){
            val ok=runCatching {context.contentResolver.takePersistableUriPermission(
                uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}.isSuccess
            if(ok){jksFile=uri;message="Файл JKS выбран"}
            else message="Нет постоянного доступа к выбранному файлу JKS."
        }
    }
    val archivePicker=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-7z-compressed")) { uri ->
        if(uri!=null){
            val ok=runCatching {context.contentResolver.takePersistableUriPermission(
                uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}.isSuccess
            if(ok){archiveFile=uri;message="Файл зашифрованного архива выбран"}
            else message="Нет постоянного доступа к выбранному архиву."
        }
    }
    val acceptable=secret.length in 12..256 && secret==secretAgain &&
        secret.none{it=='\n'||it=='\r'} && alias.matches(Regex("[A-Za-z0-9_.-]{1,100}")) &&
        name.isNotBlank() && name.length<=70 && jksFile!=null && archiveFile!=null &&
        jksFile!=archiveFile && permitted && termuxReady && !running && !ReleaseStore(context).busy()
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Создать ключ подписи",style=MaterialTheme.typography.titleLarge)
            Text("AnimaDev сгенерирует новый JKS. Пароль ключа будет создан случайно и сохранён в защищённом хранилище телефона. Для восстановления он также войдёт в зашифрованный 7z.",
                style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(value=name,onValueChange={name=it.take(70)},
                label={Text("Название ключа")},singleLine=true,enabled=!running,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=alias,onValueChange={alias=it.take(100)},
                label={Text("Alias")},singleLine=true,enabled=!running,modifier=Modifier.fillMaxWidth())
            Text("Надёжность RSA (бит)",style=MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                listOf(2048,4096).forEach { n ->
                    FilterChip(selected=bits==n,onClick={bits=n},enabled=!running,label={Text(n.toString())})
                }
            }
            OutlinedButton(onClick={
                val filename=name.replace(Regex("[^A-Za-z0-9_-]"),"_").take(45).ifBlank {"release"}
                keyPicker.launch("AnimaDev_"+filename+".jks")
            },enabled=!running,modifier=Modifier.fillMaxWidth()) {
                Text(if(jksFile==null)"Выбрать файл для JKS" else "JKS выбран · изменить")
            }
            OutlinedTextField(value=customName,onValueChange={customName=it.take(60)},
                label={Text("Имя архива (необязательно)")},singleLine=true,enabled=!running,
                modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick={
                val random=ByteArray(4).also {SecureRandom().nextBytes(it)}
                    .joinToString(""){"%02x".format(it)}
                val namePart=customName.replace(Regex("[^A-Za-z0-9_-]"),"_").take(60)
                    .ifBlank {"AnimaDev_keys_"+random}
                archivePicker.launch(namePart+".7z")
            },enabled=!running,modifier=Modifier.fillMaxWidth()) {
                Text(if(archiveFile==null)"Выбрать файл архива 7z" else "Архив выбран · изменить")
            }
            Text("Архив: AES-256, имена файлов зашифрованы. Пароль архива нигде не сохраняется.",
                style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(value=secret,onValueChange={secret=it.take(256)},
                label={Text("Пароль резервного архива · 12+ символов")},singleLine=true,
                visualTransformation=PasswordVisualTransformation(),enabled=!running,
                modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=secretAgain,onValueChange={secretAgain=it.take(256)},
                label={Text("Повторите пароль архива")},singleLine=true,
                visualTransformation=PasswordVisualTransformation(),enabled=!running,
                modifier=Modifier.fillMaxWidth())
            Row {
                Checkbox(checked=saveAsDefault,onCheckedChange={saveAsDefault=it},enabled=!running)
                Text("Сделать основным ключом проекта",modifier=Modifier.padding(top=12.dp))
            }
            Button(onClick={
                val archivePassword=secret
                secret="";secretAgain=""
                val jks=jksFile ?:return@Button
                val backup=archiveFile ?:return@Button
                inFlight=true
                message="Подготавливаем зашифрованную генерацию…"
                scope.launch {
                    try {message=gateway.launch(project.id,name,alias,bits,archivePassword,jks,backup,saveAsDefault)}
                    finally {inFlight=false}
                }
            },enabled=acceptable,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)) {
                Text("Сгенерировать JKS и резервную копию")
            }
            if(last.optString("state").isNotBlank()) {
                Text("Состояние: "+last.optString("state")+" · "+last.optString("phase"))
                Text(last.optString("message"),style=MaterialTheme.typography.bodySmall)
                if(last.optString("state")=="SUCCEEDED")
                    Text("SHA-256 архива: "+last.optString("archive_sha256"),
                        style=MaterialTheme.typography.bodySmall)
                if(last.optString("state")=="UNKNOWN") {
                    Text("Проверьте выбранные файлы и Загрузки. Не запускайте повторно вслепую.",
                        color=MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick={
                        if(store.acknowledge())message="Проверено: новая попытка разблокирована"
                    },modifier=Modifier.fillMaxWidth()) {
                        Text("Я проверил результат · разблокировать")
                    }
                }
            }
            if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodySmall)
        }
    }
}
