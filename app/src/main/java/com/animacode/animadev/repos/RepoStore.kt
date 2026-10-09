package com.animacode.animadev.repos

import android.content.Context
import org.json.JSONObject

/** One durable operation, separate from short-lived transport callbacks. No automatic START retry. */
class RepoStore(context: Context) {
    val prefs = context.getSharedPreferences("repos_v2", Context.MODE_PRIVATE)
    fun read(): JSONObject = try { JSONObject(prefs.getString("record", "{}") ?: "{}") }
        catch (_: Exception) { JSONObject().put("state", "UNKNOWN").put("message", "Локальная запись повреждена. Нужна проверка состояния runner.") }
    fun write(record: JSONObject): Boolean = prefs.edit().putString("record", record.toString()).commit()
    fun edit(block: (JSONObject) -> Unit): Boolean { val r = read(); block(r); return write(r) }
    fun blocked(): Boolean { val r = read(); return RepoPolicy.blocked(r.optString("job_id"), r.optString("state")) }
    fun expire() {
        val r = read()
        if (r.optBoolean("pending") && System.currentTimeMillis() - r.optLong("sent_at") > 15_000L) {
            r.put("pending", false)
            if (r.optString("job_id").isNotEmpty()) r.put("state", "UNKNOWN")
            r.put("message", "Ответ Termux задерживается. Результат неизвестен; повторный запуск заблокирован, уточняем состояние по ID.")
            write(r)
        }
    }
}
