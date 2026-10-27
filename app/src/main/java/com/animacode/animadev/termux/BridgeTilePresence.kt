package com.animacode.animadev.termux

import android.content.Context

/**
 * SystemUI's tile lifecycle is the only reliable enrollment signal; Android does
 * not offer apps a public global "is this QS tile pinned?" query.
 * Never mark a tile added just because the request dialog was displayed.
 */
object BridgeTilePresence {
    private const val PREFS = "animadev_bridge_tile_state"
    private const val KEY = "tile_added"

    fun isAdded(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setAdded(context: Context, present: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, present).apply()
    }
}
