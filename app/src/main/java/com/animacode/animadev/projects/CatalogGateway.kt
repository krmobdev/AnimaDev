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
    fun expire() { if(prefs.getBoolean("pending",false) && System.currentTimeMillis()-prefs.getLong("sent",0)>30_000)prefs.edit().putBoolean("pending",false).putString("message","Ответ задержался. Повторите безопасное чтение.").remove("request").commit() }
    fun read(mode:String) {
        expire();if(mode !in setOf("CATALOG","ENV") || prefs.getBoolean("pending",false))return
        val id=UUID.randomUUID().toString().replace("-","")
        val source=context.assets.open("catalog/catalog.py").bufferedReader().use { it.readText() }
        if(!prefs.edit().putString("request",id).putString("mode",mode).putLong("sent",System.currentTimeMillis()).putBoolean("pending",true).putString("message","Читаем Termux…").commit())return
        val target=Intent(context,CatalogReceiver::class.java).setAction("com.animacode.animadev.CATALOG").setData(Uri.parse("animadev://catalog/$id"))
        val callback=PendingIntent.getBroadcast(context,id.hashCode(),target,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        val intent=Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux","com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH",TermuxGateway.PYTHON)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS",arrayOf("-c",source,id,mode))
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
            if(j.getString("mode")=="CATALOG") {
                val a=data.getJSONArray("projects");require(a.length()<=40)
                val projects=buildList { for(i in 0 until a.length())add(LocalProject.parse(a.getJSONObject(i))) }
                ProjectStore(context).update(projects)
                editor.putString("catalog",data.toString())
            } else editor.putString("environment",data.toString())
            editor.commit()
        } catch(_:Exception) { prefs.edit().putBoolean("pending",false).remove("request").putString("message","Обзор не подтверждён. Git/TEST не запускались; повторите чтение.").commit() }
    }
}
