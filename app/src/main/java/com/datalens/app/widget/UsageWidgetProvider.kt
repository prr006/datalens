package com.datalens.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.datalens.app.MainActivity
import com.datalens.app.R
import com.datalens.app.notifications.UsageNotificationText
import com.datalens.app.notifications.UsageTrackerCache

/**
 * Home-screen widget: today's mobile data + the current-cycle line, from the
 * same real numbers the app and the tracking notification show.
 *
 * Updates:
 *  - on the system's APPWIDGET_UPDATE tick (~30 min, system-batched),
 *  - pushed directly by the tracking service after each of its refreshes.
 *
 * If tracking has never run there is no snapshot yet — the widget honestly says
 * "Open DataLens" instead of showing invented zeros.
 */
class UsageWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        pushUpdate(context)
    }

    companion object {

        /** Re-renders every placed widget from the latest cached snapshot. */
        fun pushUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, UsageWidgetProvider::class.java))
            if (ids.isNullOrEmpty()) return
            manager.updateAppWidget(ids, buildViews(context))
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_usage)
            val snapshot = UsageTrackerCache.snapshot
            if (snapshot == null) {
                views.setTextViewText(R.id.widget_today, context.getString(R.string.tile_open_app))
                views.setTextViewText(R.id.widget_cycle, "Usage loads after DataLens opens once.")
                views.setTextViewText(R.id.widget_tracking, "")
            } else {
                views.setTextViewText(
                    R.id.widget_today,
                    UsageNotificationText.title(snapshot.todayTotalBytes),
                )
                views.setTextViewText(R.id.widget_cycle, snapshot.cycleLine)
                views.setTextViewText(
                    R.id.widget_tracking,
                    if (snapshot.trackingEnabled) "Tracking on" else "Tracking off",
                )
            }

            val openApp = PendingIntent.getActivity(
                context,
                WIDGET_REQUEST_CODE,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            return views
        }

        private const val WIDGET_REQUEST_CODE = 4242
    }
}
