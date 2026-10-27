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
    // Always runs inside the Termux app UID. Shell/Shizuku cannot access Termux private files.
    // Background RUN_COMMAND starts Termux without opening its terminal activity.
    val CONTROL_SCRIPT = """
        import os, re, subprocess, sys, time
        PREFIX = "/data/data/com.termux/files/usr"
        SERVICE = PREFIX + "/var/service/animabridge"
        SV = PREFIX + "/bin/sv"
        action = sys.argv[1]
        if action not in ("status", "up", "down", "restart"):
            sys.exit(2)
        if not os.path.isdir(SERVICE):
            print("AnimaBridge runit service is not registered.", file=sys.stderr)
            sys.exit(2)
        def call(argv, env=None):
            return subprocess.run(argv, capture_output=True, text=True, timeout=10, env=env)
        def status():
            return call([SV, "status", SERVICE])
        try:
            if action == "status":
                result = status()
                if result.returncode != 0:
                    print("supervisor-off: " + SERVICE)
                    sys.exit(0)
                line = result.stdout.strip()
                if line.startswith("run: "):
                    pid = re.search(r"\(pid (\d+)\)", line)
                    name = ""
                    if pid:
                        try:
                            name = os.path.basename(os.readlink("/proc/" + pid.group(1) + "/exe"))
                        except OSError:
                            pass
                    if not name.startswith("python"):
                        manual = call([PREFIX + "/bin/pgrep", "-f",
                                       "^python(3)? -m termux_agent run --remote$"])
                        print(("conflict: " if manual.returncode == 0 else "starting: ") + SERVICE)
                        sys.exit(0)
                print(line)
                sys.exit(0)
            if action == "up" and status().returncode != 0:
                # runit is normally initialized by an interactive Termux login.
                # Initialize it here instead, entirely in the background, only on explicit Start.
                env = os.environ.copy()
                env.update(PREFIX=PREFIX, SVDIR=PREFIX + "/var/service",
                           LOGDIR=PREFIX + "/var/log")
                boot = call([PREFIX + "/bin/service-daemon", "start"], env)
                if boot.returncode != 0:
                    print("Termux runit supervisor could not start.", file=sys.stderr)
                    sys.exit(3)
                for attempt in range(20):
                    if status().returncode == 0:
                        break
                    time.sleep(0.2)
                else:
                    print("Termux runit supervisor did not become ready.", file=sys.stderr)
                    sys.exit(4)
            result = call([SV, action, SERVICE])
            if result.returncode != 0:
                print("AnimaBridge service command was rejected.", file=sys.stderr)
                sys.exit(5)
            print("Command accepted; verify service state.")
        except (OSError, subprocess.TimeoutExpired):
            print("Termux runit command unavailable.", file=sys.stderr)
            sys.exit(6)
    """.trimIndent()

    fun parseStatus(stdout: String): String = when {
        stdout.startsWith("run: ${SERVICE}:") -> "running"
        stdout.startsWith("down: ${SERVICE}:") -> "stopped"
        stdout == "supervisor-off: ${SERVICE}" -> "supervisor_off"
        stdout == "conflict: ${SERVICE}" -> "conflict"
        stdout == "starting: ${SERVICE}" -> "starting"
        else -> "unknown"
    }
    fun permits(action: String, serviceState: String, pending: Boolean): Boolean =
        !pending && when (action) {
            "status" -> true
            "up" -> serviceState == "stopped" || serviceState == "supervisor_off"
            "down" -> serviceState in setOf("running", "starting", "conflict")
            "restart" -> serviceState == "running"
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

    /** RUN_COMMAND also launches the Termux command service without opening its UI. */
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
            putExtra("com.termux.RUN_COMMAND_PATH", TermuxGateway.PYTHON)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", BridgeServicePolicy.CONTROL_SCRIPT, action))
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
                    is IllegalStateException -> "Android blocked the Termux command service; verify background execution permission."
                    else -> "Unable to reach Termux: " + e.javaClass.simpleName
                }))
            false
        }
    }
}
