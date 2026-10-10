package com.animacode.animadev.projects

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import com.animacode.animadev.repos.*
import com.animacode.animadev.termux.TermuxGateway

@Composable fun BuildScreen(context:Context,project:LocalProject,permitted:Boolean,termuxVersion:String?,
                           onPermissionUpdated:()->Unit,onJournal:(String)->Unit) {
    val record=rememberRepoRecord(context)
    val gateway=remember {RepoGateway(context)}
    val belongs=record.optString("project_id","animaquiz")==project.id
    val snapshot=if(belongs)record.optJSONObject("snapshot") else null
    val blocked=RepoPolicy.blocked(record.optString("job_id"),record.optString("state"))
    val ready=HelperPolicy.mayRun(permitted,termuxVersion!=null,record.optBoolean("ready"),record.optBoolean("pending"),blocked)
    val canDebug=project.supported && ready && gateway.mayBuild(record,project.id)
    val canCheck=project.supported && ready && !com.animacode.animadev.projects.ReleaseStore(context).busy()
    val signing=remember {SigningStore(context)}
    val hasSigningKey=signing.hasDocument(project.id)
    val releaseStore=remember {ReleaseStore(context)}
    val releaseGateway=remember {ReleaseGateway(context)}
    val releaseGeneration=ReleaseStore.generation.intValue
    val releaseState=remember(releaseGeneration) {releaseStore.read()}
    val releaseForProject=releaseState.optString("project")==project.id
    val releaseBusy=releaseStore.busy()
    var releasePassword by remember(project.id){mutableStateOf("")}
    var releaseKeyPassword by remember(project.id){mutableStateOf("")}
    var releaseMessage by remember(project.id){mutableStateOf("")}
    val releaseScope=rememberCoroutineScope()
    val canRelease=canDebug && hasSigningKey && signing.alias(project.id).isNotBlank() && !releaseBusy
    val grant=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){onPermissionUpdated()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(project.name,style=MaterialTheme.typography.titleMedium)
            Text("Ветка: "+(snapshot?.optString("branch") ?: "не проверена")+" · "+(snapshot?.optString("local_commit")?.take(12) ?: "SHA неизвестен"),
                style=MaterialTheme.typography.bodySmall)
        }
        if(!permitted)item {Button(onClick={grant.launch(TermuxGateway.PERMISSION)}){Text("Разрешить Termux")}}
        if(!record.optBoolean("ready"))item {Card {Column(Modifier.padding(16.dp)) {
            Text("Локальный помощник необходимо обновить")
            Button(onClick={gateway.install()},enabled=permitted && termuxVersion!=null && !blocked && !record.optBoolean("pending")){Text("Обновить помощник")}
        }}}
        item {Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Debug APK",style=MaterialTheme.typography.titleMedium)
                Text("Офлайн Gradle; перед запуском повторно сверяем GitHub SHA",style=MaterialTheme.typography.bodySmall)
                Button(onClick={gateway.start("BUILD_DEBUG",project.id,project.path)},enabled=canDebug,
                    modifier=Modifier.fillMaxWidth()){Icon(AppIcons.Build,null);Spacer(Modifier.width(8.dp));Text("Собрать Debug APK")}
                if(!canDebug) {
                    Text(when {
                        blocked -> "Предыдущее задание ещё активно или его результат неизвестен. Сначала уточните статус."
                        !ready -> "Termux или локальный помощник не готов. Проверьте подключение."
                        snapshot==null || !snapshot.optBoolean("checked") -> "Не выполнена актуальная проверка GitHub."
                        snapshot.optBoolean("dirty") -> "Есть локальные изменения. Сборка заблокирована."
                        !snapshot.optBoolean("remote_ok") -> "GitHub origin не подтверждён."
                        snapshot.optInt("ahead",-1)!=0 || snapshot.optInt("behind",-1)!=0 -> "Коммиты GitHub и Termux различаются. Нужна безопасная синхронизация в Git."
                        else -> "Ветка или SHA не соответствует разрешённому профилю сборки."
                    },style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick={gateway.start("CHECK",project.id,project.path)},
                        enabled=canCheck,modifier=Modifier.fillMaxWidth()) {
                        Text("Проверить GitHub SHA")
                    }
                    Text("Проверка не меняет исходники. После успешной проверки кнопка сборки станет доступна; при расхождении откройте Git и выполните разрешённый fast-forward.",
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }}
        item {Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Release AAB",style=MaterialTheme.typography.titleMedium)
                Text(if(hasSigningKey)"Файл: "+signing.fileLabel(project.id)+" · alias: "+signing.alias(project.id)
                     else "Выберите JKS и alias в настройках подписи.",
                    style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(releasePassword,{releasePassword=it},
                    label={Text("Пароль хранилища JKS")},
                    visualTransformation=PasswordVisualTransformation(),singleLine=true,
                    enabled=!releaseBusy,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(releaseKeyPassword,{releaseKeyPassword=it},
                    label={Text("Пароль ключа, если отличается")},
                    visualTransformation=PasswordVisualTransformation(),singleLine=true,
                    enabled=!releaseBusy,modifier=Modifier.fillMaxWidth())
                Button(onClick={
                    val storePass=releasePassword
                    val keyPass=releaseKeyPassword
                    releasePassword=""
                    releaseKeyPassword=""
                    releaseMessage="Подготавливаю защищённое задание…"
                    releaseScope.launch {
                        releaseMessage=releaseGateway.launch(project,storePass,keyPass)
                    }
                },enabled=canRelease && releasePassword.isNotEmpty(),
                  modifier=Modifier.fillMaxWidth()) {Text("Собрать подписанный Release AAB")}
                Text("Ключ и пароли передаются только по локальному Unix-сокету с проверкой UID приложений. В Intent, аргументах и настройках секретов нет. Для подписи используются анонимные файлы оперативной памяти.",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(!canDebug)Text("Нужна предварительная проверка GitHub SHA и чистого проекта.",
                    style=MaterialTheme.typography.bodySmall)
                if(releaseMessage.isNotBlank())Text(releaseMessage,style=MaterialTheme.typography.bodySmall)
                if(releaseForProject) {
                    Text("Release: "+releaseState.optString("state"),style=MaterialTheme.typography.titleSmall)
                    Text(releaseState.optString("message"),style=MaterialTheme.typography.bodySmall)
                    releaseState.optJSONObject("artifact")?.let {artifact->
                        Text("Файл: "+artifact.optString("path"),style=MaterialTheme.typography.bodySmall)
                        Text("SHA-256: "+artifact.optString("sha256"),style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }}
        val job=if(belongs)record.optJSONObject("job") else null
        if(job?.optString("action")=="BUILD_DEBUG")item {
            Text("Задание: "+MonitorPolicy.label(job.optString("state"),false,false),style=MaterialTheme.typography.titleSmall)
            Text(job.optString("message"),style=MaterialTheme.typography.bodySmall)
            job.optJSONObject("artifact")?.let {a->Card {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("Debug APK в Загрузках",style=MaterialTheme.typography.titleMedium)
                Text(a.optString("path"),style=MaterialTheme.typography.bodySmall)
                Text("SHA-256: "+a.optString("sha256"),style=MaterialTheme.typography.bodySmall)
                Text("Размер: "+a.optLong("size")+" байт · Git "+a.optString("commit").take(12),style=MaterialTheme.typography.bodySmall)
            }}}
            TextButton(onClick={onJournal(job.optString("job_id"))}){Text("Журнал задания")}
        }
    }
}
