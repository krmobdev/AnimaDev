package com.animacode.animadev.projects

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.animacode.animadev.repos.RepoStore
import com.animacode.animadev.termux.TermuxGateway
import org.json.JSONObject
import java.util.UUID

/** Explicit, guarded branch switch. Never merge, reset, force, delete or push. */
class BranchSwitchGateway(private val context:Context) {
    val prefs=context.getSharedPreferences("branch_switch_v1",Context.MODE_PRIVATE)
    fun pending()=prefs.getBoolean("pending",false)
    fun message()=prefs.getString("message","") ?: ""

    fun request(project:LocalProject,target:BranchRow,currentSha:String) {
        if(pending() || RepoStore(context).blocked() || ReleaseStore(context).busy()
           || CatalogGateway(context).prefs.getBoolean("pending",false))return
        if(!project.supported || project.worktree || !ProjectPolicy.validId(project.id,project.path)
           || target.active || target.local==null || target.remote==null
           || target.local.sha!=target.remote.sha || !currentSha.matches(Regex("[a-f0-9]{40,64}"))) {
            prefs.edit().putString("message","Нужна локальная ветка с одинаковыми SHA у local и origin. Сначала обновите список.").apply()
            return
        }
        val id=UUID.randomUUID().toString().replace("-","")
        val code=runCatching {context.assets.open("catalog/branch_switch.py").bufferedReader().use{it.readText()}}.getOrNull()
        if(code.isNullOrEmpty()) {
            prefs.edit().putString("message","Модуль переключения веток отсутствует.").apply()
            return
        }
        if(!prefs.edit().putBoolean("pending",true).putString("id",id)
                .putString("project",project.id).putString("path",project.path)
                .putString("branch",target.name).putString("sha",target.local.sha)
                .putString("message","Проверяю GitHub и чистоту рабочего дерева…").commit())return
        val callback=PendingIntent.getBroadcast(context,id.hashCode(),
            Intent(context,BranchSwitchReceiver::class.java).apply {
                action="com.animacode.animadev.BRANCH_SWITCH"
                data=Uri.parse("animadev://branch-switch/$id")
            },PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        val intent=Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux","com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH",TermuxGateway.PYTHON)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS",arrayOf("-c",code,id,project.path,project.remote,target.name,currentSha,target.local.sha))
            putExtra("com.termux.RUN_COMMAND_WORKDIR",TermuxGateway.HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback)
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL","AnimaDev · безопасное переключение ветки")
        }
        try {check(context.startService(intent)!=null)}
        catch(_:Exception) {
            callback.cancel()
            prefs.edit().putBoolean("pending",false).remove("id")
                .putString("message","Android не подтвердил команду. Обновите локальное Git-состояние.").commit()
        }
    }
}

class BranchSwitchReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val gateway=BranchSwitchGateway(context);val prefs=gateway.prefs
        val id=intent.data?.lastPathSegment ?: return
        if(intent.action!="com.animacode.animadev.BRANCH_SWITCH"
           || intent.data?.scheme!="animadev" || intent.data?.host!="branch-switch"
           || id!=prefs.getString("id","") || !id.matches(Regex("[a-f0-9]{32}")))return
        try {
            val result=intent.getBundleExtra("result") ?: error("no result")
            val text=result.getString("stdout","") ?: ""
            check(text.length<3000 && result.getInt("err")==-1 && result.getInt("exitCode")==0)
            val response=JSONObject(text);check(response.getString("request_id")==id)
            if(!response.getBoolean("ok")) {
                val code=response.getString("error_code")
                check(code.matches(Regex("[A-Z_]{3,60}")))
                prefs.edit().putBoolean("pending",false).remove("id").putString("message",when(code) {
                    "WORKTREE_DIRTY"->"Есть локальные изменения. Переключение отменено без удаления файлов."
                    "SOURCE_CHANGED"->"Активная ветка изменилась после снимка. Обновите список."
                    "LOCAL_BRANCH_CHANGED"->"Выбранная ветка изменилась. Обновите список."
                    "GITHUB_SHA_MISMATCH"->"SHA GitHub отличается от локального. Переключение отменено."
                    "REMOTE_DENIED"->"Origin не соответствует доверенному GitHub-репозиторию."
                    "GIT_REJECTED"->"Git отказал в переключении. Возможно, ветка открыта в другом worktree."
                    else->"Не удалось подтвердить переключение ("+code+"). Обновите список и Git-состояние."
                }).commit()
                return
            }
            val branch=response.getString("branch");val sha=response.getString("commit")
            check(branch==prefs.getString("branch","") && sha==prefs.getString("sha","") && sha.matches(Regex("[a-f0-9]{40,64}")))
            val project=ProjectStore(context)
            val selectedId=prefs.getString("project","") ?: ""
            val path=prefs.getString("path","") ?: ""
            project.update(listOfNotNull(project.all().firstOrNull{it.id==selectedId && it.path==path}
                ?.copy(branch=branch,commit=sha,dirty=false,error="")))
            RepoStore(context).edit {
                if(it.optString("project_id","animaquiz")==selectedId) {
                    it.remove("snapshot")
                    it.put("message","Ветка изменена. Проверьте GitHub SHA перед тестами и сборкой.")
                }
            }
            CatalogGateway(context).prefs.edit().remove("branches").commit()
            prefs.edit().putBoolean("pending",false).remove("id")
                .putString("message","Активная ветка: "+branch+" · "+sha.take(12)+". Обновите список и проверьте GitHub SHA в разделе Git.").commit()
        } catch(_:Exception) {
            prefs.edit().putBoolean("pending",false).remove("id").putString("message",
                "Результат переключения не подтверждён. Проверьте ветку в Git перед сборкой.").commit()
        }
    }
}
