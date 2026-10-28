package com.animacode.animadev.projects

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    val scrollable=labels.size>3
    Row(Modifier.fillMaxWidth()
        .then(if(scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed {index,label->
            val isSelected=selected==index
            OutlinedButton(onClick={onSelect(index)},
                modifier=(if(scrollable)Modifier.width(132.dp) else Modifier.weight(1f))
                    .height(64.dp).semantics {this.selected=isSelected},
                colors=ButtonDefaults.outlinedButtonColors(
                    containerColor=if(selected==index)MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface,
                    contentColor=if(selected==index)MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface),
                border=androidx.compose.foundation.BorderStroke(
                    if(selected==index)2.dp else 1.dp,
                    if(selected==index)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                Text(label,maxLines=2,overflow=TextOverflow.Ellipsis)
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
    var details by remember(artifact.optString("sha256")) {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val destination=remember(context) {ExportDestination(context)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null)scope.launch {ArtifactGateway.install(context,artifact,uri)}
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            val apk=artifact.optString("path").endsWith(".apk")
            Text(if(apk)"APK готов к установке" else "AAB готов к публикации",
                style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            Text(artifact.optString("path").substringAfterLast('/'),style=MaterialTheme.typography.bodyMedium)
            Text("Размер: ${artifact.optLong("size")/1024/1024} МБ · Git ${artifact.optString("commit").take(12)}",
                style=MaterialTheme.typography.bodySmall)
            ActionButton(onClick={
                if(apk) {
                    val direct=destination.artifactUri(artifact.optString("path"))
                        ?: ArtifactGateway.rememberedUri(context,artifact)
                    if(direct!=null)scope.launch {
                        if(!ArtifactGateway.install(context,artifact,direct))
                            picker.launch(arrayOf("*/*"))
                    }
                    else picker.launch(arrayOf("*/*"))
                } else ArtifactGateway.openFolder(context,artifact)
            }) {
                Text(if(apk)"Установить APK" else "Открыть папку")
            }
            if(apk && destination.artifactUri(artifact.optString("path"))==null &&
                ArtifactGateway.rememberedUri(context,artifact)==null)
                Text("При первом нажатии выберите этот APK в системном проводнике. Затем откроется установщик Android.",
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
}
