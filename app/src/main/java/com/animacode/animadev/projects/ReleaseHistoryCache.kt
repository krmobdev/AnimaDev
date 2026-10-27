package com.animacode.animadev.projects

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Non-secret Release events and completed build metadata. Separate from the
 * current build card and from the Termux job database; no stdout or credentials.
 */
class ReleaseHistoryCache(context:Context) {
    private val prefs=context.getSharedPreferences("release_history_v1",Context.MODE_PRIVATE)
    private val terminal=setOf("SUCCEEDED","FAILED","CANCELLED","UNKNOWN")
    private fun id(value:String)=value.matches(Regex("[a-f0-9]{32}"))

    @Synchronized fun observe(record:JSONObject) {
        val jobId=record.optString("id")
        if(!id(jobId))return
        val status=record.optString("state").take(40)
        val phase=record.optString("phase").take(40)
        val message=record.optString("message").take(400)
        val key="events_"+jobId
        val events=runCatching {JSONArray(prefs.getString(key,"[]"))}.getOrDefault(JSONArray())
        val last=events.optJSONObject(events.length()-1)
        if(last?.optString("state")!=status || last?.optString("phase")!=phase ||
            last?.optString("message")!=message) {
            val added=JSONArray()
            for(i in maxOf(0,events.length()-63) until events.length())added.put(events.optJSONObject(i))
            added.put(JSONObject().put("at",System.currentTimeMillis())
                .put("state",status).put("phase",phase).put("message",message)
                .put("error_code",record.optString("error_code").take(60)))
            prefs.edit().putString(key,added.toString()).apply()
        }
        if(status !in terminal || record.optLong("ended_at")<=0L)return
        val entry=JSONObject().put("action",record.optString("kind","RELEASE_AAB"))
            .put("project_id",record.optString("project"))
            .put("job_id",jobId).put("id",jobId)
            .put("state",status).put("phase",phase).put("message",message)
            .put("sha",record.optString("sha")).put("error_code",record.optString("error_code").take(60))
            .put("progress_lines",record.optString("progress_lines").takeLast(9500))
            .put("started_at",record.optLong("started_at")/1000.0)
            .put("created_at",record.optLong("started_at")/1000.0)
            .put("ended_at",record.optLong("ended_at")/1000.0)
        record.optJSONObject("artifact")?.let {a->
            entry.put("artifact",JSONObject(a.toString()))
        }
        val old=runCatching {JSONArray(prefs.getString("history","[]"))}.getOrDefault(JSONArray())
        val list=JSONArray().put(entry)
        for(i in 0 until old.length()) {
            val item=old.optJSONObject(i) ?:continue
            if(item.optString("job_id")!=jobId && list.length()<100)list.put(item)
        }
        prefs.edit().putString("history",list.toString()).apply()
    }

    fun events(jobId:String):List<JSONObject> {
        if(!id(jobId))return emptyList()
        val array=runCatching {JSONArray(prefs.getString("events_"+jobId,"[]"))}.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull {array.optJSONObject(it)}
    }

    fun history(projectId:String):List<JSONObject> {
        val a=runCatching {JSONArray(prefs.getString("history","[]"))}.getOrDefault(JSONArray())
        return (0 until a.length()).mapNotNull {a.optJSONObject(it)}
            .filter {it.optString("project_id")==projectId}
    }

    fun get(jobId:String):JSONObject?=if(id(jobId))
        runCatching {JSONArray(prefs.getString("history","[]"))}.getOrDefault(JSONArray()).let {array->
            (0 until array.length()).mapNotNull {array.optJSONObject(it)}
                .firstOrNull {it.optString("job_id")==jobId}
        } else null
}
