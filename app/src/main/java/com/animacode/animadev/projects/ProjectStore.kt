package com.animacode.animadev.projects

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LocalProject(val id:String,val name:String,val path:String,val branch:String="",val commit:String="",val remote:String="",val dirty:Boolean?=null,val error:String="") {
    val supported get()=ProjectPolicy.supported(id,path)
    fun json()=JSONObject().put("id",id).put("name",name).put("path",path).put("branch",branch).put("commit",commit).put("remote",remote).put("dirty",dirty ?: JSONObject.NULL).put("error",error)
    companion object {
        fun parse(j:JSONObject):LocalProject {
            val id=j.getString("id");val path=j.getString("path");require(ProjectPolicy.validId(id,path))
            return LocalProject(id,j.getString("name").take(80),path,j.optString("branch").take(200),j.optString("commit").take(64),j.optString("remote").take(200),if(j.isNull("dirty"))null else j.optBoolean("dirty"),j.optString("error").take(200))
        }
    }
}
class ProjectStore(context:Context) {
    val prefs=context.getSharedPreferences("projects_v1",Context.MODE_PRIVATE)
    init { if(!prefs.contains("projects"))prefs.edit().putString("projects",JSONArray().put(LocalProject("animaquiz","AnimaQuiz",ProjectPolicy.QUIZ).json()).toString()).commit() }
    fun all():List<LocalProject> = try {
        val a=JSONArray(prefs.getString("projects","[]"));buildList { for(i in 0 until a.length())runCatching { LocalProject.parse(a.getJSONObject(i)) }.getOrNull()?.let { add(it) } }.distinctBy { it.path }
    } catch(_:Exception) { emptyList() }
    private fun save(items:List<LocalProject>)=prefs.edit().putString("projects",JSONArray(items.map { it.json() }).toString()).commit()
    fun add(project:LocalProject):Boolean { if(all().any { it.path==project.path } || !ProjectPolicy.validId(project.id,project.path))return false;return save(all()+project) }
    fun remove(id:String):Boolean { val remaining=all().filterNot { it.id==id };val selected=ProjectPolicy.nextSelection(remaining.map { it.id },selected());return save(remaining) && prefs.edit().putString("selected",selected).commit() }
    private val selection=ProjectSelection(object:SelectionStorage {
        override fun load()=prefs.getString("selected","") ?: ""
        override fun save(id:String)=prefs.edit().putString("selected",id).commit()
    })
    fun select(id:String)=selection.choose(all().map { it.id },id)
    fun selected()=selection.read(all().map { it.id })
    fun update(found:List<LocalProject>)=save(all().map { old -> found.find { it.path==old.path && it.id==old.id } ?: old })
}
