package com.animacode.animadev.termux

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.animacode.animadev.MainActivity
import com.animacode.animadev.R
import java.text.DateFormat
import java.util.Date

/**
 * Resizable RemoteViews widget. Android owns its scheduling; actions use explicit
 * immutable PendingIntents and the same gateway/policy as the in-app buttons.
 */
class BridgeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
        BridgeSurfaceSync.checkIfStale(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, manager, id, newOptions)
        render(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_COMMAND || intent.component?.className != javaClass.name) return
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        if (action !in BridgeServicePolicy.actions) return
        BridgeSurfaceSync.dispatch(context, action)
        renderAll(context)
    }

    companion object {
        private const val ACTION_COMMAND = "com.animacode.animadev.BRIDGE_WIDGET_COMMAND"
        private const val EXTRA_ACTION = "bridge_command"

        fun renderAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BridgeWidgetProvider::class.java))
            ids.forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val record = BridgeServiceStore(context).read()
            val pending = record.phase == "REQUESTED"
            val active = record.serviceState in setOf("running", "ephemeral")
            val options = manager.getAppWidgetOptions(id)
            val expanded = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) >= 175
            val views = RemoteViews(context.packageName, R.layout.bridge_widget)
            val stateText = when {
                pending -> R.string.bridge_checking
                record.serviceState == "running" -> R.string.bridge_local_running
                record.serviceState == "ephemeral" -> R.string.bridge_autostart_off
                record.serviceState == "stopped" -> R.string.bridge_stopped
                record.serviceState == "supervisor_off" -> R.string.bridge_supervisor_off
                record.serviceState == "conflict" -> R.string.bridge_conflict
                else -> R.string.bridge_unknown
            }
            views.setTextViewText(R.id.bridge_widget_state, context.getString(stateText))
            views.setTextColor(
                R.id.bridge_widget_state,
                if (active) Color.rgb(113, 240, 176) else Color.rgb(245, 205, 142),
            )
            views.setTextViewText(
                R.id.bridge_widget_autostart,
                context.getString(R.string.bridge_autostart_label) + ": " +
                    when (record.serviceState) {
                        "running" -> context.getString(R.string.bridge_enabled)
                        "ephemeral", "stopped" -> context.getString(R.string.bridge_disabled)
                        else -> context.getString(R.string.bridge_unknown)
                    },
            )
            val checked = if (record.action == "status" && record.phase == "DONE" && record.requestedAt > 0L) {
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(record.requestedAt))
            } else context.getString(R.string.bridge_unknown)
            views.setTextViewText(
                R.id.bridge_widget_checked,
                context.getString(R.string.bridge_checked_label) + ": " + checked,
            )
            views.setTextViewText(
                R.id.bridge_widget_feedback,
                if (record.phase == "ERROR" || record.phase == "UNKNOWN") record.message
                else context.getString(R.string.bridge_server_unverified),
            )
            views.setViewVisibility(
                R.id.bridge_widget_details,
                if (expanded) View.VISIBLE else View.GONE,
            )
            views.setViewVisibility(
                R.id.bridge_widget_restart,
                if (expanded) View.VISIBLE else View.GONE,
            )

            val canStart = BridgeServicePolicy.permits("up", record.serviceState, pending)
            val canStop = BridgeServicePolicy.permits("down", record.serviceState, pending) &&
                record.serviceState in setOf("running", "ephemeral")
            val canRestart = BridgeServicePolicy.permits("restart", record.serviceState, pending)
            bind(context, views, id, R.id.bridge_widget_start, "up", canStart)
            bind(context, views, id, R.id.bridge_widget_stop, "down", canStop)
            bind(context, views, id, R.id.bridge_widget_restart, "restart", canRestart)
            bind(context, views, id, R.id.bridge_widget_check, "status", !pending)
            val open = Intent(context, MainActivity::class.java)
            val openPi = PendingIntent.getActivity(
                context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.bridge_widget_title, openPi)
            manager.updateAppWidget(id, views)
        }

        private fun bind(
            context: Context, views: RemoteViews, id: Int,
            viewId: Int, action: String, enabled: Boolean,
        ) {
            views.setBoolean(viewId, "setEnabled", enabled)
            views.setTextColor(viewId, if (enabled) Color.WHITE else Color.GRAY)
            val intent = Intent(context, BridgeWidgetProvider::class.java).apply {
                this.action = ACTION_COMMAND
                data = Uri.parse("animadev://widget/$id/$action")
                putExtra(EXTRA_ACTION, action)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            val pi = PendingIntent.getBroadcast(
                context, id * 8 + action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(viewId, pi)
        }
    }
}
