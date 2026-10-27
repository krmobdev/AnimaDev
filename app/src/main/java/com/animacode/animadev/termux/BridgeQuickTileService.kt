package com.animacode.animadev.termux

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** A one-tap local runit controller. "Active" means Python process, not server connectivity. */
class BridgeQuickTileService : TileService() {
    override fun onTileAdded() {
        super.onTileAdded()
        BridgeSurfaceSync.checkIfStale(this, 0L)
        showState()
    }

    override fun onStartListening() {
        super.onStartListening()
        showState()
        BridgeSurfaceSync.checkIfStale(this)
    }

    override fun onClick() {
        super.onClick()
        val toggle = Runnable {
            val record = BridgeServiceStore(this).read()
            val next = when (record.serviceState) {
                "running", "ephemeral" -> "down"
                "stopped", "supervisor_off" -> "up"
                else -> "status"  // Never blindly toggle while status is unknown/conflicted.
            }
            BridgeSurfaceSync.dispatch(this, next)
            showState()
        }
        if (isLocked) unlockAndRun(toggle) else toggle.run()
    }

    private fun showState() {
        val record = BridgeServiceStore(this).read()
        val pending = record.phase == "REQUESTED"
        val state = when {
            pending -> Tile.STATE_UNAVAILABLE
            record.serviceState in setOf("running", "ephemeral") -> Tile.STATE_ACTIVE
            record.serviceState in setOf("stopped", "supervisor_off") -> Tile.STATE_INACTIVE
            else -> Tile.STATE_UNAVAILABLE
        }
        qsTile?.apply {
            label = getString(R.string.bridge_tile_label)
            this.state = state
            subtitle = when {
                pending -> getString(R.string.bridge_checking)
                record.serviceState == "running" -> getString(R.string.bridge_local_running)
                record.serviceState == "ephemeral" -> getString(R.string.bridge_autostart_off)
                record.serviceState == "stopped" -> getString(R.string.bridge_stopped)
                record.serviceState == "supervisor_off" -> getString(R.string.bridge_supervisor_off)
                record.serviceState == "conflict" -> getString(R.string.bridge_conflict)
                else -> getString(R.string.bridge_unknown)
            }
            contentDescription = label.toString() + ": " + subtitle.toString()
            updateTile()
        }
    }
}
