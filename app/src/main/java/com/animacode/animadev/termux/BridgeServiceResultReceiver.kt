package com.animacode.animadev.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** One-shot callback, bound to an exact pending request identifier. */
class BridgeServiceResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        if (intent.action != "com.animacode.animadev.BRIDGE_SERVICE_RESULT" ||
            uri.scheme != "animadev" || uri.host != "bridge-service") return
        val store = BridgeServiceStore(context)
        val current = store.read()
        if (current.phase != "REQUESTED" || current.id != uri.lastPathSegment) return
        val b = intent.getBundleExtra("result")
        if (b == null) {
            store.write(current.copy(phase = "ERROR", serviceState = "unknown",
                message = "Termux returned no result."))
            return
        }
        val stdout = b.getString("stdout", "") ?: ""
        val stderr = b.getString("stderr", "") ?: ""
        val exit = if (b.containsKey("exitCode")) b.getInt("exitCode") else null
        val err = if (b.containsKey("err")) b.getInt("err") else null
        val oversize = stdout.length > 2048 || stderr.length > 2048
        val reportedTruncation = (b.get("stdout_original_length")?.toString()?.toLongOrNull() ?: 0L) > stdout.length ||
            (b.get("stderr_original_length")?.toString()?.toLongOrNull() ?: 0L) > stderr.length
        val ok = exit == 0 && (err == null || err == -1) && !oversize && !reportedTruncation
        val status = if (ok && current.action == "status") BridgeServicePolicy.parseStatus(stdout.trim()) else "unknown"
        store.write(current.copy(
            phase = if (ok) "DONE" else "ERROR",
            serviceState = status,
            exitCode = exit,
            message = when {
                !ok -> "Service command not confirmed (exit: ${exit ?: "?"}); verify termux-services registration."
                current.action == "status" && status == "running" -> "Agent process is running; server connection not verified."
                current.action == "status" && status == "ephemeral" -> "Agent is running, but supervisor autostart is disabled. Tap Start to enable persistent service."
                current.action == "status" && status == "stopped" -> "AnimaBridge service is stopped."
                current.action == "status" && status == "supervisor_off" -> "Runit is not active. Start will initialize it in the background."
                current.action == "status" && status == "conflict" -> "A manual AnimaBridge agent conflicts with the supervised service."
                current.action == "status" && status == "starting" -> "Service wrapper is starting, but agent is not yet verified. Check again."
                current.action == "status" -> "Could not verify the registered service."
                else -> "Runit action verified; checking current service status. Server connection must be checked separately."
            }
        ))
    }
}
