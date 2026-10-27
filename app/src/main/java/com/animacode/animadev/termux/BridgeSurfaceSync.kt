package com.animacode.animadev.termux

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService

/**
 * Shared event-driven bridge state for AnimaDev, Quick Settings and home widgets.
 *
 * One BridgeServiceStore and one BridgeServiceGateway are used by every UI surface.
 * No recurring background polling or additional agent process is created.
 */
object BridgeSurfaceSync {
    const val CHECK_INTERVAL_MS = 30_000L

    fun checkIfStale(context: Context, intervalMs: Long = CHECK_INTERVAL_MS) {
        val record = BridgeServiceStore(context).read()
        if (record.phase == "REQUESTED") return
        val age = System.currentTimeMillis() - record.requestedAt
        if (record.action != "status" || age < 0 || age >= intervalMs) {
            BridgeServiceGateway(context).request("status")
        }
    }

    fun publish(context: Context) {
        BridgeWidgetProvider.renderAll(context)
        try {
            TileService.requestListeningState(
                context,
                ComponentName(context, BridgeQuickTileService::class.java),
            )
        } catch (_: RuntimeException) {
            // Tiles are optional and may not yet be added by the owner.
        }
    }

    fun dispatch(context: Context, action: String): Boolean {
        val record = BridgeServiceStore(context).read()
        if (!BridgeServicePolicy.permits(action, record.serviceState, record.phase == "REQUESTED")) {
            // Unknown status is never guessed into a start/stop action.
            if (action != "status") checkIfStale(context, 0L)
            return false
        }
        return BridgeServiceGateway(context).request(action)
    }
}
