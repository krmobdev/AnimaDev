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
import android.widget.RemoteViews
import com.animacode.animadev.MainActivity
import com.animacode.animadev.R
import java.text.DateFormat
import java.util.Date

/** Modern resize-aware launcher widget. One gateway for app, tile, and widget. */
open class BridgeWidgetProvider : AppWidgetProvider() {
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
        if (intent.action != ACTION || intent.component?.className != javaClass.name) return
        val command = intent.getStringExtra(EXTRA) ?: return
        if (command !in BridgeServicePolicy.actions) return
        BridgeSurfaceSync.dispatch(context, command)
        renderAll(context)
    }

    companion object {
        private const val ACTION = "com.animacode.animadev.BRIDGE_WIDGET_COMMAND"
        private const val EXTRA = "bridge_command"
        private const val GREEN = 0xFF7EF5AA.toInt()
        private const val AMBER = 0xFFFFCE87.toInt()
        private const val GRAY = 0xFF8DA99A.toInt()

        // Two independent widget entries: each has its own fixed, tested cell geometry.
        // Never guess "full" from ColorOS' reported minHeight; that caused clipping.
        internal fun layoutForProvider(full: Boolean): Int =
            if (full) R.layout.bridge_widget_large else R.layout.bridge_widget_compact

        fun renderAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BridgeWidgetProvider::class.java))
            val fullIds = manager.getAppWidgetIds(ComponentName(context, BridgeFullWidgetProvider::class.java))
            (ids + fullIds).distinct().forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val record = BridgeServiceStore(context).read()
            val pending = record.phase == "REQUESTED"
            val active = record.serviceState in setOf("running", "ephemeral")
            val stopped = record.serviceState in setOf("stopped", "supervisor_off")
            val isFull = manager.getAppWidgetInfo(id)?.provider?.className ==
                BridgeFullWidgetProvider::class.java.name
            val layout = layoutForProvider(isFull)
            val expanded = isFull
            val views = RemoteViews(context.packageName, layout)
            val state = when {
                pending -> R.string.bridge_checking
                record.serviceState == "running" -> R.string.bridge_widget_process_running
                record.serviceState == "ephemeral" -> R.string.bridge_autostart_off
                record.serviceState == "stopped" -> R.string.bridge_stopped
                record.serviceState == "supervisor_off" -> R.string.bridge_supervisor_off
                record.serviceState == "conflict" -> R.string.bridge_conflict
                else -> R.string.bridge_unknown
            }
            val pill = when {
                pending -> R.string.bridge_checking
                active -> R.string.bridge_widget_local_on
                stopped -> R.string.bridge_widget_local_off
                else -> R.string.bridge_unknown
            }
            val accent = if (active && !pending) GREEN else if (stopped && !pending) GRAY else AMBER
            views.setTextViewText(R.id.bridge_widget_state, context.getString(state))
            views.setTextViewText(R.id.bridge_widget_pill, context.getString(pill))
            views.setTextColor(R.id.bridge_widget_state, accent)
            views.setTextColor(R.id.bridge_widget_pill, accent)
            views.setTextColor(R.id.bridge_widget_dot, accent)

            val checked = if (record.action == "status" && record.phase == "DONE" &&
                record.requestedAt > 0
            ) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(record.requestedAt))
              else context.getString(R.string.bridge_unknown)
            views.setTextViewText(R.id.bridge_widget_checked,
                context.getString(R.string.bridge_checked_label) + " · " + checked)
            if (!expanded) {
                val autostart = when (record.serviceState) {
                    "running" -> R.string.bridge_enabled
                    "ephemeral", "stopped" -> R.string.bridge_disabled
                    else -> R.string.bridge_unknown
                }
                views.setTextViewText(
                    R.id.bridge_widget_summary,
                    context.getString(
                        R.string.bridge_widget_compact_summary,
                        context.getString(autostart),
                    ),
                )
            }
            if (expanded) {
                val autostart = when (record.serviceState) {
                    "running" -> R.string.bridge_enabled
                    "ephemeral", "stopped" -> R.string.bridge_disabled
                    else -> R.string.bridge_unknown
                }
                views.setTextViewText(R.id.bridge_widget_autostart, context.getString(autostart))
                views.setTextViewText(
                    R.id.bridge_widget_feedback,
                    if (record.phase == "ERROR" || record.phase == "UNKNOWN")
                        record.message.take(60)
                    else context.getString(R.string.bridge_widget_server_short),
                )
            }
            bind(context, views, id, R.id.bridge_widget_start, R.id.bridge_widget_start_bg,
                R.id.bridge_widget_start_text, "up",
                BridgeServicePolicy.permits("up", record.serviceState, pending), true)
            bind(context, views, id, R.id.bridge_widget_stop, R.id.bridge_widget_stop_bg,
                R.id.bridge_widget_stop_text, "down",
                BridgeServicePolicy.permits("down", record.serviceState, pending) && active, false)
            bind(context, views, id, R.id.bridge_widget_check,
                R.id.bridge_widget_check_bg, R.id.bridge_widget_check_text, "status", !pending, false)
            if (expanded) bind(context, views, id, R.id.bridge_widget_restart,
                R.id.bridge_widget_restart_bg, R.id.bridge_widget_restart_text, "restart",
                BridgeServicePolicy.permits("restart", record.serviceState, pending), false)

            val open = PendingIntent.getActivity(
                context, id,
                Intent(context, MainActivity::class.java).apply {
                    // Reuse the already visible or background task; never stack
                    // another MainActivity when tapping the widget title.
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.bridge_widget_title, open)
            manager.updateAppWidget(id, views)
        }

        private fun bind(
            context: Context, views: RemoteViews, id: Int,
            viewId: Int, bgId: Int, textId: Int, command: String,
            enabled: Boolean, primary: Boolean,
        ) {
            // ImageView.setImageResource is safe for RemoteViews on customized launchers.
            val drawable = when {
                !enabled -> R.drawable.bridge_widget_action_disabled
                primary -> R.drawable.bridge_widget_action_primary
                command == "down" -> R.drawable.bridge_widget_action_danger
                else -> R.drawable.bridge_widget_action_secondary
            }
            views.setImageViewResource(bgId, drawable)
            views.setTextColor(textId, when {
                !enabled -> GRAY
                primary -> 0xFF143325.toInt()
                command == "down" -> 0xFFFFDBD4.toInt()
                else -> Color.WHITE
            })
            val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)
            val isFull = info?.provider?.className == BridgeFullWidgetProvider::class.java.name
            val target = if (isFull) BridgeFullWidgetProvider::class.java
                else BridgeWidgetProvider::class.java
            val intent = Intent(context, target).apply {
                action = ACTION
                data = Uri.parse("animadev://widget/$id/$command")
                putExtra(EXTRA, command)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            val click = PendingIntent.getBroadcast(
                context, id * 8 + command.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(viewId, click)
        }
    }
}

/** Large widget is independently selectable in Android's widget picker. */
class BridgeFullWidgetProvider : BridgeWidgetProvider()
