package com.animacode.animadev.shizuku

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import rikka.shizuku.Shizuku

/** Direct Shizuku Binder access. Never starts the Android service or launches an ADB shell. */
enum class ShizukuState {
    NOT_INSTALLED, SERVICE_STOPPED, PERMISSION_REQUIRED, PERMISSION_DENIED,
    CONNECTED, UNSUPPORTED, ERROR
}

data class ShizukuInfo(
    val state: ShizukuState,
    val uid: Int? = null,
    val apiVersion: Int? = null
)

object ShizukuPolicy {
    fun resolve(installed: Boolean, alive: Boolean, supported: Boolean,
                granted: Boolean, denied: Boolean): ShizukuState = when {
        !installed -> ShizukuState.NOT_INSTALLED
        !alive -> ShizukuState.SERVICE_STOPPED
        !supported -> ShizukuState.UNSUPPORTED
        granted -> ShizukuState.CONNECTED
        denied -> ShizukuState.PERMISSION_DENIED
        else -> ShizukuState.PERMISSION_REQUIRED
    }
}

/** Scoped to the visible Termux screen; listeners are removed on navigation. */
class ShizukuConnection(private val context: Context) {
    companion object {
        const val PACKAGE = "moe.shizuku.privileged.api"
        private const val PERMISSION_REQUEST = 0x51A7
    }

    var info by mutableStateOf(ShizukuInfo(ShizukuState.SERVICE_STOPPED))
        private set

    private var observing = false
    private val received = Shizuku.OnBinderReceivedListener { refresh() }
    private val dead = Shizuku.OnBinderDeadListener { refresh() }
    private val permission = Shizuku.OnRequestPermissionResultListener { code, _ ->
        if (code == PERMISSION_REQUEST) refresh()
    }

    fun start() {
        if (observing) return
        observing = true
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        refresh()
    }

    fun stop() {
        if (!observing) return
        observing = false
        Shizuku.removeBinderReceivedListener(received)
        Shizuku.removeBinderDeadListener(dead)
        Shizuku.removeRequestPermissionResultListener(permission)
    }

    fun refresh() {
        val installed = try {
            context.packageManager.getPackageInfo(PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        if (!installed) {
            info = ShizukuInfo(ShizukuState.NOT_INSTALLED)
            return
        }
        try {
            val alive = Shizuku.pingBinder()
            if (!alive) {
                info = ShizukuInfo(ShizukuState.SERVICE_STOPPED)
                return
            }
            val supported = !Shizuku.isPreV11()
            if (!supported) {
                info = ShizukuInfo(ShizukuState.UNSUPPORTED)
                return
            }
            val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            val denied = !granted && Shizuku.shouldShowRequestPermissionRationale()
            val state = ShizukuPolicy.resolve(installed, alive, supported, granted, denied)
            info = if (state == ShizukuState.CONNECTED)
                ShizukuInfo(state, Shizuku.getUid(), Shizuku.getVersion())
            else ShizukuInfo(state)
        } catch (_: RuntimeException) {
            info = ShizukuInfo(ShizukuState.ERROR)
        }
    }

    fun requestPermission() {
        refresh()
        if (info.state != ShizukuState.PERMISSION_REQUIRED) return
        try {
            Shizuku.requestPermission(PERMISSION_REQUEST)
        } catch (_: RuntimeException) {
            info = ShizukuInfo(ShizukuState.ERROR)
        }
    }

    fun managerIntent() = context.packageManager.getLaunchIntentForPackage(PACKAGE)
}
