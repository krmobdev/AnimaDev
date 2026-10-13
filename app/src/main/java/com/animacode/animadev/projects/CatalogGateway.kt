package com.animacode.animadev.projects

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.animacode.animadev.termux.TermuxGateway
import org.json.JSONObject
import java.util.UUID

class CatalogGateway(private val context:Context) {
    val prefs=context.getSharedPreferences("catalog_v1",Context.MODE_PRIVATE)
    fun expire() { if(prefs.getBoolean("pending",false) && System.currentTimeMillis()-prefs.getLong("sent",0)>75_000)prefs.edit().putBoolean("pending",false).putString("message","Ответ задержался. Повторите безопасное чтение.").remove("request").commit() }
    fun read(mode:String,path:String="",branchOffset:Long=0) {
        expire();if(branchOffset<0)return
        if(mode !in setOf("CATALOG","ENV","PATH","BRANCHES","BRANCHES_FETCH") || prefs.getBoolean("pending",false))return
        if(mode in setOf("PATH","BRANCHES","BRANCHES_FETCH") && !ProjectPolicy.permittedPath(path.replace("~/",ProjectPolicy.HOME+"/"))) { prefs.edit().putString("message","Путь запрещён политикой безопасности.").apply();return }
        val id=UUID.randomUUID().toString().replace("-","")
        val source=context.assets.open("catalog/catalog.py").bufferedReader().use { it.readText() }
        if(!prefs.edit().putString("request",id).putString("mode",mode).putString("path",path.replace("~/",ProjectPolicy.HOME+"/")).putLong("branch_offset",branchOffset).putLong("sent",System.currentTimeMillis()).putBoolean("pending",true).putString("message","Читаем Termux…").commit())return
        val target=Intent(context,CatalogReceiver::class.java).setAction("com.animacode.animadev.CATALOG").setData(Uri.parse("animadev://catalog/$id"))
        val callback=PendingIntent.getBroadcast(context,id.hashCode(),target,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        val intent=Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux","com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH",TermuxGateway.PYTHON)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS",if(mode=="BRANCHES")arrayOf("-c",source,id,mode,path,branchOffset.toString()) else if(mode in setOf("PATH","BRANCHES_FETCH"))arrayOf("-c",source,id,mode,path) else arrayOf("-c",source,id,mode))
            putExtra("com.termux.RUN_COMMAND_WORKDIR",TermuxGateway.HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback)
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL","AnimaDev · обзор Termux")
        }
        try { check(context.startService(intent)!=null) }
        catch(_:Exception) { callback.cancel();prefs.edit().putBoolean("pending",false).remove("request").putString("message","Нет подтверждённой связи. Проверьте разрешение RUN_COMMAND.").commit() }
    }
}
class CatalogReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val gateway=CatalogGateway(context);val prefs=gateway.prefs
        val id=intent.data?.lastPathSegment ?: return
        if(intent.action!="com.animacode.animadev.CATALOG" || intent.data?.host!="catalog" || id!=prefs.getString("request","") || !id.matches(Regex("[a-f0-9]{32}")))return
        try {
            val result=intent.getBundleExtra("result") ?: error("callback")
            val out=result.getString("stdout","") ?: "";require(out.length<=40_000)
            val j=JSONObject(out);require(j.getString("request_id")==id && j.getString("mode")==prefs.getString("mode","") && result.getInt("exitCode")==0 && result.getInt("err")==-1 && j.getBoolean("ok"))
            val data=j.getJSONObject("data");val editor=prefs.edit().putBoolean("pending",false).remove("request").putString("message","Обзор обновлён")
            if(j.getString("mode") in setOf("BRANCHES","BRANCHES_FETCH")) {
                val a=data.getJSONArray("projects");require(a.length()<=1)
                if(a.length()==1) {
                    val page=a.getJSONObject(0);val offset=prefs.getLong("branch_offset",0)
                    require(page.getString("path")==prefs.getString("path","") && (page.optJSONArray("branches")?.length() ?: 0)<=80)
                    if(page.optString("error").isNotEmpty() ||
                       (j.getString("mode")=="BRANCHES_FETCH" && !page.optBoolean("refs_refreshed"))) {
                        editor.putString("message",page.optString("error").ifBlank {
                            "Не удалось подтвердить обновление GitHub. Прежние ветки сохранены."
                        }.take(180))
                        if(offset==0L && j.getString("mode")=="BRANCHES")
                            editor.putString("branches",data.toString())
                    } else {
                        if(j.getString("mode")=="BRANCHES_FETCH")
                            editor.putString("message","GitHub origin обновлён. SHA веток перечитаны в Termux.")
                        require(page.getLong("branches_offset")==offset && page.getLong("branches_next")>=offset)
                        if(offset>0) {
                            val previous=JSONObject(prefs.getString("branches","{}") ?: "{}").getJSONArray("projects").getJSONObject(0)
                            require(previous.getString("path")==page.getString("path") && previous.getLong("branches_next")==offset)
                            val merged=org.json.JSONArray();val seen=mutableSetOf<String>()
                            listOf(previous.getJSONArray("branches"),page.getJSONArray("branches")).forEach {rows->for(i in 0 until rows.length()) {val row=rows.getJSONObject(i);if(seen.add(row.getString("name")+"/"+row.getBoolean("local")))merged.put(row)}}
                            page.put("branches",merged)
                        }
                        editor.putString("branches",data.toString())
                    }
                } else editor.putString("message","Репозиторий не подтверждён. Список веток не изменён.")
            } else if(j.getString("mode") in setOf("CATALOG","PATH")) {
                val a=data.getJSONArray("projects");require(a.length()<=40)
                val projects=buildList { for(i in 0 until a.length())add(LocalProject.parse(a.getJSONObject(i))) }
                if(j.getString("mode")=="PATH")require(projects.all {it.path==prefs.getString("path","")})
                ProjectStore(context).update(projects)
                // A catalog PATH read reports the real local HEAD, not a historical
                // build snapshot. Never let an old checked SHA masquerade as current.
                val repo=com.animacode.animadev.repos.RepoStore(context)
                val existing=repo.read()
                val active=projects.firstOrNull {it.id==existing.optString("project_id","animaquiz")}
                val saved=existing.optJSONObject("snapshot")
                if(active!=null && saved!=null &&
                    (saved.optString("branch")!=active.branch ||
                     saved.optString("local_commit")!=active.commit)) {
                    repo.edit {
                        it.remove("snapshot")
                        it.put("message","Ветка изменилась: "+active.branch+
                            ". Старый GitHub SHA сброшен; выполните проверку перед сборкой.")
                    }
                }
                editor.putString("catalog",data.toString())
            } else editor.putString("environment",data.toString())
            editor.commit()
        } catch(_:Exception) { prefs.edit().putBoolean("pending",false).remove("request").putString("message","Обзор не подтверждён. Git/TEST не запускались; повторите чтение.").commit() }
    }
}
