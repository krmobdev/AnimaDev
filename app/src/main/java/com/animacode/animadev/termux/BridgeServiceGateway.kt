package com.animacode.animadev.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.util.UUID

/** Fixed runit service interface: never accept shell text, paths or commands from the UI. */
object BridgeServicePolicy {
    const val SERVICE = "/data/data/com.termux/files/usr/var/service/animabridge"
    const val SV = "/data/data/com.termux/files/usr/bin/sv"
    const val TIMEOUT_MS = 30_000L
    val actions = setOf("status", "up", "down", "restart")

    fun parseStatus(stdout: String): String = when {
        stdout.startsWith("run: ") && stdout.contains("animabridge:") -> "running"
        stdout.startsWith("down: ") && stdout.contains("animabridge:") -> "stopped"
        else -> "unknown"
    }
    fun permits(action: String, serviceState: String, pending: Boolean): Boolean =
        !pending && when (action) {
            "status" -> true
            "up" -> serviceState == "stopped"
            "down", "restart" -> serviceState == "running"
            else -> false
        }
}

data class BridgeServiceRecord(
    val id: String = "",
    val action: String = "",
    val phase: String = "IDLE",
    val serviceState: String = "unknown",
    val message: String = "",
    val requestedAt: Long = 0,
    val exitCode: Int? = null,
)

class BridgeServiceStore(context: Context) {
    private val prefs = context.getSharedPreferences("animabridge_service_v1", Context.MODE_PRIVATE)
    fun read(): BridgeServiceRecord = try {
        val j = JSONObject(prefs.getString("record", "{}") ?: "{}")
        val record = BridgeServiceRecord(
            id = j.optString("id"), action = j.optString("action"),
            phase = j.optString("phase", "IDLE"),
            serviceState = j.optString("serviceState", "unknown"),
            message = j.optString("message"), requestedAt = j.optLong("requestedAt"),
            exitCode = if (j.has("exitCode")) j.getInt("exitCode") else null,
        )
        if (record.phase == "REQUESTED" &&
            System.currentTimeMillis() - record.requestedAt > BridgeServicePolicy.TIMEOUT_MS) {
            val expired = record.copy(phase = "UNKNOWN", serviceState = "unknown",
                message = "Termux did not confirm the operation; check status before retrying.")
            write(expired)
            expired
        } else record
    } catch (_: Exception) {
        BridgeServiceRecord(phase = "ERROR", message = "Service state could not be restored.")
    }

    fun write(r: BridgeServiceRecord): Boolean {
        val j = JSONObject().put("id", r.id).put("action", r.action)
            .put("phase", r.phase).put("serviceState", r.serviceState)
            .put("message", r.message).put("requestedAt", r.requestedAt)
        r.exitCode?.let { j.put("exitCode", it) }
        return prefs.edit().putString("record", j.toString()).commit()
    }
}

class BridgeServiceGateway(private val context: Context) {
    private val store = BridgeServiceStore(context)

    /** RUN_COMMAND runs as the Termux app UID; Shizuku's shell UID cannot read Termux HOME. */
    fun request(action: String): Boolean {
        if (action !in BridgeServicePolicy.actions) return false
        val current = store.read()
        if (!BridgeServicePolicy.permits(action, current.serviceState, current.phase == "REQUESTED")) return false
        if (context.checkSelfPermission(TermuxGateway.PERMISSION) != PackageManager.PERMISSION_GRANTED) return false
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo("com.termux", 0)
        } catch (_: PackageManager.NameNotFoundException) { return false }
        val id = UUID.randomUUID().toString()
        val next = current.copy(id = id, action = action, phase = "REQUESTED",
            serviceState = if (action == "status") current.serviceState else "unknown",
            requestedAt = System.currentTimeMillis(), message = "", exitCode = null)
        if (!store.write(next)) return false
        val callbackIntent = Intent(context, BridgeServiceResultReceiver::class.java).apply {
            this.action = "com.animacode.animadev.BRIDGE_SERVICE_RESULT"
            data = Uri.parse("animadev://bridge-service/$id")
        }
        val flags = PendingIntent.FLAG_ONE_SHOT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getBroadcast(context, id.hashCode(), callbackIntent, flags)
        val command = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", BridgeServicePolicy.SV)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(action, BridgeServicePolicy.SERVICE))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", TermuxGateway.HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "AnimaDev · AnimaBridge $action")
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback)
        }
        return try {
            if (context.startService(command) == null) {
                callback.cancel()
                store.write(next.copy(phase = "ERROR", message = "Termux command service is not available."))
                false
            } else true
        } catch (e: Exception) {
            callback.cancel()
            store.write(next.copy(phase = "ERROR",
                message = when (e) {
                    is SecurityException -> "Allow Termux RUN_COMMAND for AnimaDev."
                    is IllegalStateException -> "Open Termux once, then retry."
                    else -> "Unable to reach Termux: " + e.javaClass.simpleName
                }))
            false
        }
    }
}
