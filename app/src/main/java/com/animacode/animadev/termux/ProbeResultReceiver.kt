package com.animacode.animadev.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** Explicit, non-exported receiver, callable by Termux solely via our one-shot capability. */
class ProbeResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = ProbeStore(context)
        val previous = store.read()
        val uri = intent.data ?: return
        if (intent.action != "com.animacode.animadev.PROBE_RESULT" ||
            uri.scheme != "animadev" || uri.host != "probe") return
        val id = uri.lastPathSegment ?: return
        if (!ProbePolicy.acceptsCallback(previous.id, id, previous.state)) return
        try {
            val bundle = intent.getBundleExtra("result") ?: error("missing result")
            val stdout = bundle.getString("stdout", "") ?: ""
            val stderr = bundle.getString("stderr", "") ?: ""
            val exit = if (bundle.containsKey("exitCode")) bundle.getInt("exitCode") else null
            val err = if (bundle.containsKey("err")) bundle.getInt("err") else null
            val bounded = stdout.length <= ProbePolicy.MAX_OUTPUT && stderr.length <= ProbePolicy.MAX_OUTPUT
            val valid = bounded && try {
                val j = JSONObject(stdout)
                j.getInt("protocol_version") == 1 && j.getString("request_id") == id &&
                    j.getString("cwd") == TermuxGateway.HOME && j.getInt("uid") >= 10000 &&
                    j.getString("python_version").isNotBlank() && j.getString("platform") in setOf("linux", "android")
            } catch (_: Exception) { false }
            val success = ProbePolicy.succeeded(exit, err, valid)
            val internal = (bundle.getString("errmsg", "") ?: "").take(2048)
            val truncated = !bounded || length(bundle.get("stdout_original_length")) > stdout.length ||
                length(bundle.get("stderr_original_length")) > stderr.length
            store.write(previous.copy(state = if (success && !truncated) "SUCCESS" else "ERROR",
                ended = System.currentTimeMillis(), stdout = stdout.take(ProbePolicy.MAX_OUTPUT),
                stderr = stderr.take(ProbePolicy.MAX_OUTPUT), exitCode = exit, termuxError = err,
                truncated = truncated, message = when {
                    success && !truncated -> "Termux подключён. Настоящая команда завершилась с exit=0; ID ответа проверен."
                    internal.isNotEmpty() -> "Termux сообщил об ошибке: $internal"
                    exit != null && exit != 0 -> "Команда завершилась с ошибкой. Код выхода: $exit."
                    else -> "RESULT_MALFORMED: ответ неполный, усечён или не соответствует запросу. Успех не подтверждён."
                }))
        } catch (_: Exception) {
            store.write(previous.copy(state = "ERROR", ended = System.currentTimeMillis(),
                message = "RESULT_MALFORMED: не удалось прочитать ответ Termux."))
        }
    }
    private fun length(value: Any?): Long = value?.toString()?.toLongOrNull() ?: 0
}
