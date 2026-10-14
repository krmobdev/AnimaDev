package com.animacode.animadev.projects

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.animacode.animadev.repos.*
import org.json.JSONObject

@Composable fun OperationProjectHeader(project:LocalProject) {
    Surface(modifier=Modifier.fillMaxWidth().testTag("operation-header"),
        shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(project.name,style=MaterialTheme.typography.titleLarge)
            Text(project.branch.ifBlank {"Ветка не проверена"},maxLines=2,
                overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.primary)
            Text(project.commit.take(12).ifBlank {"SHA не проверен"}+" · "+
                when(project.dirty){true->"Есть изменения";false->"Чистое дерево";else->"Нужна проверка"},
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun OperationTabs(labels:List<String>,selected:Int,onSelect:(Int)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed {index,label->
            val isSelected=selected==index
            OutlinedButton(onClick={onSelect(index)},modifier=Modifier.weight(1f).heightIn(min=48.dp).semantics {this.selected=isSelected},
                colors=ButtonDefaults.outlinedButtonColors(
                    containerColor=if(selected==index)MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface,
                    contentColor=if(selected==index)MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface),
                border=androidx.compose.foundation.BorderStroke(
                    if(selected==index)2.dp else 1.dp,
                    if(selected==index)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                Text(label)
            }
        }
    }
}

/** A job is rendered only inside its own operation and its own project. */
object OperationResultPolicy {
    fun matches(project:String,jobProject:String,action:String,jobAction:String)=
        project==jobProject && action==jobAction
}

@Composable fun TestOperationResult(context:Context,record:JSONObject,project:LocalProject,
    action:String,permitted:Boolean,onJournal:(String)->Unit) {
    val belongs=record.optString("project_id",RepoGateway.PROJECT)==project.id
    val current=belongs && record.optString("action")==action && record.optString("job_id").isNotBlank()
    if(current && record.optString("state") !in RepoPolicy.terminalStates) {
        JobMonitor(context,record,permitted,onJournal)
        return
    }
    val saved=if(belongs)record.optJSONObject(if(action=="TEST")"test_job" else "device_test_job") else null
    val test=(if(current)record.optJSONObject("job") else null) ?: saved ?: return
    if(!OperationResultPolicy.matches(project.id,test.optString("project_id",record.optString("project_id",RepoGateway.PROJECT)),action,test.optString("action")) ||
        test.optString("state") !in RepoPolicy.terminalStates)return
    OutlinedCard(modifier=Modifier.fillMaxWidth().testTag("result-$action")) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Последний результат",style=MaterialTheme.typography.titleMedium)
            Text(MonitorPolicy.label(test.optString("state"),false,false),
                color=if(test.optString("state")=="SUCCEEDED")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            test.optJSONObject("tests")?.let {counts->
                Text("${counts.optLong("passed")} из ${counts.optLong("total")} успешно",
                    style=MaterialTheme.typography.titleLarge)
                Text("Ошибок ${counts.optLong("failed")} · пропущено ${counts.optLong("skipped")}",
                    style=MaterialTheme.typography.bodySmall)
            }
            if(test.optString("state")!="SUCCEEDED") {
                Text(TestResultPresentation.message(action,test.optString("error_code"),
                    test.optString("message").ifBlank {"Тесты не завершились успешно."}),
                    color=MaterialTheme.colorScheme.error)
                TestSummary.details(test.optJSONObject("tests")).takeIf {it.isNotBlank()}?.let {
                    Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                }
            }
            ActionButton(onClick={onJournal(test.optString("job_id"))}) {Text("Открыть журнал")}
        }
    }
}

@Composable fun DebugBuildResult(context:Context,record:JSONObject,project:LocalProject,
    permitted:Boolean,onJournal:(String)->Unit) {
    if(record.optString("project_id",RepoGateway.PROJECT)!=project.id)return
    val current=record.optString("action")=="BUILD_DEBUG" && record.optString("job_id").isNotBlank()
    if(current && record.optString("state") !in RepoPolicy.terminalStates) {
        JobMonitor(context,record,permitted,onJournal)
        return
    }
    // Older jobs belong exclusively to History, never linger on the Build screen.
    if(!current)return
    val job=record.optJSONObject("job") ?: return
    if(!OperationResultPolicy.matches(project.id,job.optString("project_id",project.id),
            "BUILD_DEBUG",job.optString("action")) || job.optString("state") !in RepoPolicy.terminalStates)return
    val success=job.optString("state")=="SUCCEEDED"
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedCard(Modifier.fillMaxWidth().testTag("result-BUILD_DEBUG")) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Последняя Debug-сборка",style=MaterialTheme.typography.titleMedium)
                Text(MonitorPolicy.label(job.optString("state"),false,false),
                    color=if(success)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Text(job.optString("message"),style=MaterialTheme.typography.bodySmall)
                if(!success)job.optString("error_code").takeIf {it.isNotBlank()}?.let {
                    Text("Код: "+it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                }
                ActionButton(onClick={onJournal(job.optString("job_id"))}) {Text("Открыть журнал сборки")}
            }
        }
        if(success)job.optJSONObject("artifact")?.let {BuildArtifactCard(context,it)}
    }
}

@Composable fun BuildArtifactCard(context:Context,artifact:JSONObject) {
    val sha=artifact.optString("sha256")
    var details by remember(sha) {mutableStateOf(false)}
    var needsInstallPermission by remember(sha) {mutableStateOf(false)}
    var installing by remember(sha) {mutableStateOf(false)}
    var installNotice by remember(sha) {mutableStateOf("")}
    val scope=rememberCoroutineScope()
    val destination=remember(context) {ExportDestination(context)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        val uri=result.data?.data
        if(uri==null)installNotice="Выбор APK отменён. Файл сборки не изменён."
        else scope.launch {
            installing=true
            installNotice="Проверяем APK перед открытием системного установщика…"
            val launched=ArtifactGateway.install(context,artifact,uri)
            installing=false
            installNotice=if(launched)"" else "Установщик не открылся. Проверьте выбранный APK или разрешение Android."
        }
    }
    fun chooseApk() {
        val direct=destination.artifactUri(artifact.optString("path"))
            ?: ArtifactGateway.rememberedUri(context,artifact)
        if(direct!=null)scope.launch {
            installing=true
            installNotice="Проверяем APK перед открытием системного установщика…"
            val ok=ArtifactGateway.install(context,artifact,direct)
            installing=false
            if(!ok) {
                installNotice="Нужно повторно выбрать APK в системном проводнике."
                picker.launch(ArtifactGateway.pickerIntent(destination.folderUri()))
            } else installNotice=""
        } else {
            installNotice="Выберите файл "+artifact.optString("path").substringAfterLast('/')+" в папке Загрузки."
            picker.launch(ArtifactGateway.pickerIntent(destination.folderUri()))
        }
    }
    val requestInstallGrant=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(Build.VERSION.SDK_INT<26 || context.packageManager.canRequestPackageInstalls()) {
            installNotice=""
            chooseApk()
        } else installNotice="Разрешение на установку APK не выдано. Нажмите «Установить APK», чтобы повторить."
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            val apk=artifact.optString("path").endsWith(".apk")
            Text(if(apk)"APK готов к установке" else "AAB готов к публикации",
                style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            Text(artifact.optString("path").substringAfterLast('/'),style=MaterialTheme.typography.bodyMedium)
            Text("Размер: ${artifact.optLong("size")/1024/1024} МБ · Git ${artifact.optString("commit").take(12)}",
                style=MaterialTheme.typography.bodySmall)
            ActionButton(enabled=!installing,onClick={
                if(apk) {
                    if(Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls())
                        needsInstallPermission=true
                    else chooseApk()
                } else ArtifactGateway.openFolder(context,artifact)
            }) {
                Text(if(installing)"Проверка APK…" else if(apk)"Установить APK" else "Открыть папку")
            }
            if(installing)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(installNotice.isNotBlank())
                Text(installNotice,style=MaterialTheme.typography.bodySmall)
            if(apk && destination.artifactUri(artifact.optString("path"))==null &&
                ArtifactGateway.rememberedUri(context,artifact)==null)
                Text("После разрешения Android выберите этот APK в Загрузках. "+
                    "Файл проверяется перед установкой; повторно нажимать кнопку не нужно.",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            ActionButton(onClick={details=!details}) {Text(if(details)"Скрыть сведения о файле" else "Сведения о файле")}
            if(details) {
                Text(artifact.optString("path"),style=MaterialTheme.typography.bodySmall)
                Text("SHA-256: "+artifact.optString("sha256"),style=MaterialTheme.typography.bodySmall)
            }
            if(!apk)Text("AAB предназначен для публикации. Для установки на телефон соберите Debug APK.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if(needsInstallPermission)AlertDialog(
        onDismissRequest={needsInstallPermission=false},
        title={Text("Разрешить установку APK")},
        text={Text("Android запрещает AnimaDev открывать установщик. Разреши установку "+
            "приложений из AnimaDev в системных настройках. После возвращения "+
            "я сразу открою выбор APK и продолжу без повторного нажатия.")},
        confirmButton={TextButton(onClick={
            needsInstallPermission=false
            requestInstallGrant.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:"+context.packageName)))
        }) {Text("Открыть настройки")}},
        dismissButton={TextButton(onClick={needsInstallPermission=false}) {Text("Не сейчас")}}
    )
}
