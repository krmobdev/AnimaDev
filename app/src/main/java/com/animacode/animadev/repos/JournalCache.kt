package com.animacode.animadev.repos

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/** Private cache; the authoritative history and logs remain in Termux. No megabyte preferences. */
class JournalCache(context: Context) : SQLiteOpenHelper(context, "journal_cache.sqlite", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE pages(job TEXT,view TEXT,offset INTEGER,next INTEGER,text TEXT,truncated INTEGER,legacy INTEGER,PRIMARY KEY(job,view,offset))")
        db.execSQL("CREATE TABLE history(job TEXT PRIMARY KEY,data TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    @Synchronized fun receive(page: JSONObject, job: String, view: String, offset: Long, expectedProject:String=RepoGateway.PROJECT) {
        require(page.getString("job_id") == job && page.getLong("offset") == offset)
        val next = page.getLong("next_offset"); val text = page.getString("text")
        require(next >= offset && text.length <= 12_000 && view in setOf("ALL","IMPORTANT"))
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(cursor(job,view) == offset)
            db.execSQL("INSERT OR REPLACE INTO pages VALUES(?,?,?,?,?,?,?)", arrayOf<Any>(job,view,offset,next,RepoResultReceiver.redact(text),if(page.optBoolean("truncated"))1 else 0,if(page.optBoolean("legacy"))1 else 0))
            page.optJSONObject("metadata")?.let { metadata ->
                require(metadata.getString("job_id")==job && metadata.getString("project_id")==expectedProject)
                db.execSQL("INSERT OR REPLACE INTO history VALUES(?,?)",arrayOf(job,metadata.toString()))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun rememberJob(job:JSONObject,expectedProject:String=RepoGateway.PROJECT) {
        require(job.getString("job_id").matches(Regex("[a-f0-9]{32}")) &&
            job.getString("project_id")==expectedProject)
        val id=job.getString("job_id")
        val previous=readableDatabase.rawQuery("SELECT data FROM history WHERE job=?",arrayOf(id)).use {
            if(it.moveToFirst())JSONObject(it.getString(0)) else JSONObject()
        }
        job.keys().forEach { key -> previous.put(key,job.get(key)) }
        writableDatabase.execSQL("INSERT OR REPLACE INTO history VALUES(?,?)",arrayOf(id,previous.toString()))
    }
    @Synchronized fun history(page: JSONObject,expectedProject:String=RepoGateway.PROJECT) {
        val items = page.getJSONArray("items")
        require(items.length() <= 20)
        for(i in 0 until items.length()) {
            val j=items.getJSONObject(i)
            require(j.getString("job_id").matches(Regex("[a-f0-9]{32}")) && j.getString("project_id")==expectedProject)
            val previous=readableDatabase.rawQuery("SELECT data FROM history WHERE job=?",arrayOf(j.getString("job_id"))).use { if(it.moveToFirst())JSONObject(it.getString(0)) else JSONObject() }
            j.keys().forEach { key -> previous.put(key,j.get(key)) }
            writableDatabase.execSQL("INSERT OR REPLACE INTO history VALUES(?,?)",arrayOf(j.getString("job_id"),previous.toString()))
        }
    }
    fun jobs(): List<JSONObject> = readableDatabase.rawQuery("SELECT data FROM history",null).use { c ->
        buildList { while(c.moveToNext()) add(JSONObject(c.getString(0))) }.sortedByDescending { it.optDouble("created_at") }
    }
    fun cursor(job: String, view: String): Long = readableDatabase.rawQuery("SELECT MAX(next) FROM pages WHERE job=? AND view=?",arrayOf(job,view)).use { if(it.moveToFirst()) it.getLong(0) else 0 }
    fun header(job: String): String = readableDatabase.rawQuery("SELECT data FROM history WHERE job=?",arrayOf(job)).use { c ->
        if(!c.moveToFirst()) "Задание · Job ID: $job\n" else JSONObject(c.getString(0)).let { j ->
            val start=j.optDouble("started_at",j.optDouble("created_at",0.0));val end=j.optDouble("ended_at",0.0)
            buildString {
                append("Задание · ${j.optString("action")}\nJob ID: $job\nПроект: ${j.optString("project_id")}\n")
                val snapshot=j.optJSONObject("snapshot")
                append("Ветка: ${snapshot?.optString("branch")?.ifEmpty { "не подтверждена" } ?: "не сохранена"}\n")
                append("Коммит: ${snapshot?.optString("local_commit")?.ifEmpty { "не подтверждён" } ?: "не сохранён"}\n")
                append("Ошибка: ${j.optString("error_code").ifEmpty { "нет подтверждённой ошибки" }}\n")
                append("Начало: ${java.util.Date((start*1000).toLong())}\n")
                append("Итог: ${MonitorPolicy.label(j.optString("state"),false,false)} · Exit code: ${j.optString("exit_code")}\n")
                if(end>0 && start>0) append("Длительность: ${((end-start)*1000).toLong()} мс\n")
                if(j.has("tests") && !j.isNull("tests"))append(TestSummary.format(j.getJSONObject("tests")))
                append(TestResultPresentation.message(j.optString("action"),j.optString("error_code"),j.optString("message")));append("\n\n")
            }
        }
    }
    fun text(job: String, view: String): String = header(job) + fallback(job,view) + readableDatabase.rawQuery("SELECT text FROM pages WHERE job=? AND view=? ORDER BY offset",arrayOf(job,view)).use { c -> buildString { while(c.moveToNext()) append(c.getString(0)) } }
    private fun fallback(job:String,view:String):String {
        if(cursor(job,view)>0L)return ""
        return readableDatabase.rawQuery("SELECT data FROM history WHERE job=?",arrayOf(job)).use { c ->
            if(!c.moveToFirst())"" else JSONObject(c.getString(0)).let { j ->
                listOf(j.optString("stdout"),j.optString("stderr")).filter { it.isNotBlank() }
                    .joinToString("\n") { RepoResultReceiver.redact(it).takeLast(4096) }
            }
        }
    }
    fun notice(job: String, view: String): String = readableDatabase.rawQuery("SELECT MAX(truncated),MAX(legacy) FROM pages WHERE job=? AND view=?",arrayOf(job,view)).use {
        if(!it.moveToFirst()) "" else listOfNotNull(if(it.getInt(0)==1) "Журнал усечён по лимиту 5 MiB. Показаны все доступные сохранённые данные." else null,if(it.getInt(1)==1) "Старый runner сохранил только ограниченный фрагмент вывода." else null).joinToString("\n")
    }
}
