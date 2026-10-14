package com.animacode.animadev.projects

import android.content.Context
import org.json.JSONObject
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.animacode.animadev.repos.RepoGateway
import com.animacode.animadev.repos.RepoPolicy

/** Shared recovery, visible both in the actual Debug APK screen and Local Git.
 * Never permits START while a prior CHECK is UNKNOWN.
 */
@Composable
fun LocalCheckRecoveryCard(context:Context,project:LocalProject,record:JSONObject,
                           permitted:Boolean,termuxVersion:String?) {
    val gateway=remember { RepoGateway(context) }
    if(!gateway.isLocalCheckRecovery(record,project.id))return
    val pending=record.optBoolean("pending")
    val needsUpgrade=gateway.needsLocalRecoveryUpgrade(record,project.id)
    val canInstall=LocalCheckRecoveryPolicy.canUpdate(
        pending,permitted,termuxVersion!=null,gateway.mayUpgradeRecoveryHelper(record))
    val canQuery=LocalCheckRecoveryPolicy.canQuery(
        pending,permitted,termuxVersion!=null,record.optBoolean("ready"),
        RepoPolicy.canRecoverJob(pending,record.optString("job_id"),
            record.optString("job_runner_sha"),record.optString("installed_sha")),
        needsUpgrade)
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Восстановление прежней проверки Git",
                 style=MaterialTheme.typography.titleMedium)
            Text("Прежний CHECK: результат неизвестен. Job ID: "+
                 record.optString("job_id").take(12))
            Text("Не запускаем новый CHECK или сборку, пока не получим подтверждение "+
                 "прежнего задания. Его история и исходники не удаляются.")
            if(needsUpgrade) {
                Button(onClick={gateway.install()},enabled=canInstall,
                    modifier=Modifier.fillMaxWidth()) {
                    Text("Обновить помощник Termux")
                }
                Text("Сначала подтвердите установку помощника, затем проверьте старый Job ID.",
                    style=MaterialTheme.typography.bodySmall)
            } else {
                Button(onClick={gateway.query(true)},enabled=canQuery,
                    modifier=Modifier.fillMaxWidth()) {
                    Text("Проверить прежний Job ID")
                }
            }
            if(!permitted)Text("Разрешите доступ RUN_COMMAND в настройках AnimaDev.",
                style=MaterialTheme.typography.bodySmall)
            else if(pending)Text("Ожидаем ответа Termux; повторный запрос пока недоступен.",
                style=MaterialTheme.typography.bodySmall)
            else if(record.optString("error_code").isNotBlank())
                Text(record.optString("error_code")+": "+record.optString("message"),
                    style=MaterialTheme.typography.bodySmall)
        }
    }
}
