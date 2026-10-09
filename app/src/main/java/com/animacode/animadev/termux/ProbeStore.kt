package com.animacode.animadev.termux

import android.content.Context
import org.json.JSONObject

data class ProbeState(
    val id: String = "", val state: String = "IDLE", val started: Long = 0,
    val ended: Long = 0, val stdout: String = "", val stderr: String = "",
    val exitCode: Int? = null, val termuxError: Int? = null,
    val message: String = "Нажмите «Проверить», чтобы получить настоящий ответ Termux.",
    val truncated: Boolean = false
)

/** Small persisted single-request store. Callback and activity run in one process. */
class ProbeStore(context: Context) {
    private val prefs = context.getSharedPreferences("probe_v1", Context.MODE_PRIVATE)
    fun read(): ProbeState = try {
        val j = JSONObject(prefs.getString("state", "{}") ?: "{}")
        ProbeState(j.optString("id"), j.optString("state", "IDLE"), j.optLong("started"),
            j.optLong("ended"), j.optString("stdout"), j.optString("stderr"),
            if (j.has("exitCode")) j.getInt("exitCode") else null,
            if (j.has("termuxError")) j.getInt("termuxError") else null,
            j.optString("message", ProbeState().message), j.optBoolean("truncated"))
    } catch (_: Exception) {
        ProbeState(state = "ERROR", message = "Локальная запись повреждена. Выполните новую проверку.")
    }

    fun write(s: ProbeState): Boolean {
        val j = JSONObject().put("id", s.id).put("state", s.state).put("started", s.started)
            .put("ended", s.ended).put("stdout", s.stdout).put("stderr", s.stderr)
            .put("message", s.message).put("truncated", s.truncated)
        s.exitCode?.let { j.put("exitCode", it) }
        s.termuxError?.let { j.put("termuxError", it) }
        return prefs.edit().putString("state", j.toString()).commit()
    }
    fun expire(): ProbeState {
        val s = read()
        if (!ProbePolicy.expired(s.state, s.started, System.currentTimeMillis())) return s
        val unknown = s.copy(state = "UNKNOWN", message =
            "Ответ не получен за 30 секунд. Результат неизвестен; поздний ответ будет сохранён. Проверьте доступ Termux и настройку allow-external-apps.")
        write(unknown)
        return unknown
    }
}
