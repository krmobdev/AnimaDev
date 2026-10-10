package com.animacode.animadev.projects

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
            OutlinedButton(onClick={onSelect(index)},modifier=Modifier.weight(1f).heightIn(min=48.dp),
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

@Composable fun BuildArtifactCard(context:Context,artifact:JSONObject) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            val apk=artifact.optString("path").endsWith(".apk")
            Text(if(apk)"APK готов к установке" else "AAB готов к публикации",
                style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            Text(artifact.optString("path").substringAfterLast('/'),style=MaterialTheme.typography.bodyMedium)
            Text("Размер: ${artifact.optLong("size")/1024/1024} МБ · Git ${artifact.optString("commit").take(12)}",
                style=MaterialTheme.typography.bodySmall)
            ActionButton(onClick={ArtifactGateway.open(context,artifact)}) {
                Text(if(apk)"Открыть · установить приложение" else "Открыть AAB")
            }
            if(!apk)Text("AAB предназначен для публикации. Для установки на телефон соберите Debug APK.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
