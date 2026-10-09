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
    @Synchronized fun receive(page: JSONObject, job: String, view: String, offset: Long) {
        require(page.getString("job_id") == job && page.getLong("offset") == offset)
        val next = page.getLong("next_offset"); val text = page.getString("text")
        require(next >= offset && text.length <= 12_000 && view in setOf("ALL","IMPORTANT"))
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(cursor(job,view) == offset)
            db.execSQL("INSERT OR REPLACE INTO pages VALUES(?,?,?,?,?,?,?)", arrayOf<Any>(job,view,offset,next,RepoResultReceiver.redact(text),if(page.optBoolean("truncated"))1 else 0,if(page.optBoolean("legacy"))1 else 0))
            page.optJSONObject("metadata")?.let { metadata ->
                require(metadata.getString("job_id")==job && metadata.getString("project_id")==RepoGateway.PROJECT)
                db.execSQL("INSERT OR REPLACE INTO history VALUES(?,?)",arrayOf(job,metadata.toString()))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun history(page: JSONObject) {
        val items = page.getJSONArray("items")
        require(items.length() <= 20)
        for(i in 0 until items.length()) {
            val j=items.getJSONObject(i)
            require(j.getString("job_id").matches(Regex("[a-f0-9]{32}")) && j.getString("project_id")==RepoGateway.PROJECT)
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
        if(!c.moveToFirst()) "AnimaQuiz · Job ID: $job\n" else JSONObject(c.getString(0)).let { j ->
            val start=j.optDouble("started_at",j.optDouble("created_at",0.0));val end=j.optDouble("ended_at",0.0)
            buildString {
                append("AnimaQuiz · ${j.optString("action")}\nJob ID: $job\nРабочий каталог: ~/quizapp\n")
                val snapshot=j.optJSONObject("snapshot")
                append("Ветка: ${snapshot?.optString("branch")?.ifEmpty { "не подтверждена" } ?: "не сохранена"}\n")
                append("Коммит: ${snapshot?.optString("local_commit")?.ifEmpty { "не подтверждён" } ?: "не сохранён"}\n")
                append("Ошибка: ${j.optString("error_code").ifEmpty { "нет подтверждённой ошибки" }}\n")
                append("Начало: ${java.util.Date((start*1000).toLong())}\n")
                append("Итог: ${MonitorPolicy.label(j.optString("state"),false,false)} · Exit code: ${j.optString("exit_code")}\n")
                if(end>0 && start>0) append("Длительность: ${((end-start)*1000).toLong()} мс\n")
                if(j.has("tests") && !j.isNull("tests"))append(TestSummary.format(j.getJSONObject("tests")))
                append(j.optString("message"));append("\n\n")
            }
        }
    }
    fun text(job: String, view: String): String = header(job) + readableDatabase.rawQuery("SELECT text FROM pages WHERE job=? AND view=? ORDER BY offset",arrayOf(job,view)).use { c -> buildString { while(c.moveToNext()) append(c.getString(0)) } }
    fun notice(job: String, view: String): String = readableDatabase.rawQuery("SELECT MAX(truncated),MAX(legacy) FROM pages WHERE job=? AND view=?",arrayOf(job,view)).use {
        if(!it.moveToFirst()) "" else listOfNotNull(if(it.getInt(0)==1) "Журнал усечён по лимиту 5 MiB. Показаны все доступные сохранённые данные." else null,if(it.getInt(1)==1) "Старый runner сохранил только ограниченный фрагмент вывода." else null).joinToString("\n")
    }
}
