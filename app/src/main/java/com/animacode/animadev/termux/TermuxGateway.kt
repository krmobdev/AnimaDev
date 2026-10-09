package com.animacode.animadev.termux

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.util.UUID

/** Constants verified against termux-app v0.118.3 TermuxConstants.java, lines 974–1044.
 * https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
 * No command text, paths or environment are accepted from UI or network.
 */
class TermuxGateway(private val context: Context) {
    companion object {
        const val PERMISSION = "com.termux.permission.RUN_COMMAND"
        const val HOME = "/data/data/com.termux/files/home"
        const val PYTHON = "/data/data/com.termux/files/usr/bin/python"
        val SCRIPT = """
            import json, os, sys, platform
            request_id = sys.argv[1]
            print(json.dumps({"protocol_version": 1, "request_id": request_id,
                "python_version": platform.python_version(), "cwd": os.getcwd(),
                "uid": os.getuid(), "platform": sys.platform}, ensure_ascii=False))
            print("AnimaDev: read-only probe completed in Termux", file=sys.stderr)
        """.trimIndent()
    }
    fun installedVersion(): String? = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo("com.termux", 0).versionName
    } catch (_: PackageManager.NameNotFoundException) { null }

    fun permitted(): Boolean = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun probe() {
        val store = ProbeStore(context)
        if (store.expire().state == "REQUESTED") return
        if (installedVersion() == null || !permitted()) {
            store.write(ProbeState(state = "ERROR", message = "Termux не установлен или не выдано разрешение RUN_COMMAND."))
            return
        }
        val id = UUID.randomUUID().toString()
        val state = ProbeState(id = id, state = "REQUESTED", started = System.currentTimeMillis(),
            message = "Запрос отправляется. Ожидаем stdout, stderr и код выхода от Termux.")
        if (!store.write(state)) return // Never execute if durable identity could not be saved.
        val resultIntent = Intent(context, ProbeResultReceiver::class.java).apply {
            action = "com.animacode.animadev.PROBE_RESULT"
            data = Uri.parse("animadev://probe/$id")
        }
        val flags = PendingIntent.FLAG_ONE_SHOT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getBroadcast(context, id.hashCode(), resultIntent, flags)
        val command = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", PYTHON)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", SCRIPT, id))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "AnimaDev · проверка связи")
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback)
        }
        try {
            if (context.startService(command) == null) {
                callback.cancel()
                store.write(state.copy(state = "ERROR", message = "Служба Termux не найдена; команда не запущена."))
            }
        } catch (e: Exception) {
            callback.cancel()
            store.write(state.copy(state = "ERROR", ended = System.currentTimeMillis(), message = when (e) {
                is SecurityException -> "Android отказал в RUN_COMMAND. Выдайте разрешение в настройках AnimaDev."
                is IllegalStateException -> "Android запретил запуск службы. Откройте Termux и повторите проверку из AnimaDev."
                else -> "Не удалось отправить запрос: ${e.javaClass.simpleName}."
            }))
        }
    }
}
