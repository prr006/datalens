package com.datalens.app.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.datalens.app.MainActivity
import com.datalens.app.R
import com.datalens.app.widget.UsageWidgetProvider
import com.datalens.app.ServiceLocator
import kotlinx.coroutines.flow.first

/**
 * Single switch for the persistent usage-tracking notification
 * (see [UsageTrackingService]).
 *
 * All enable/disable paths go through [setEnabled] so the persisted DataStore
 * flag and the service lifecycle can never disagree:
 *  - Settings toggle          -> ViewModel -> [setEnabled]
 *  - Notification "Turn off"  -> TrackingEventsReceiver -> [setEnabled]
 *  - The service itself also observes the flag and stops when it flips to off.
 */
object TrackingController {

    private const val TAG = "TrackingController"

    /** Broadcast action for the notification's "Turn off" quick action. */
    const val ACTION_TURN_OFF = "com.datalens.app.action.TRACKING_TURN_OFF"

    /** Starts (or re-anchors) the foreground tracking service. Safe to call repeatedly. */
    fun start(context: Context) {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, UsageTrackingService::class.java),
            )
        } catch (e: Exception) {
            // Expected on some OEM builds when invoked from the background without
            // an exemption; MainActivity re-aligns the service the next time the
            // app comes to the foreground.
            Log.w(TAG, "Could not start usage tracking service", e)
        }
    }

    /** Stops the tracking service and removes any leftover tracking notification. */
    fun stop(context: Context) {
        context.stopService(Intent(context, UsageTrackingService::class.java))
        UsageTrackerCache.setTrackingEnabled(false)
        // Refresh the widget so it honestly shows "Tracking off".
        UsageWidgetProvider.pushUpdate(context)
        // Stopping a foreground service removes its notification automatically; the
        // explicit cancel only covers the rare boot-fallback post (see
        // TrackingEventsReceiver) when the service was never running.
        NotificationManagerCompat.from(context).cancel(NotificationHelper.NOTIF_ID_TRACKING)
    }

    /**
     * Persists the tracking preference and aligns the service with it.
     * This is the only place the preference is written together with the service.
     */
    suspend fun setEnabled(context: Context, enabled: Boolean) {
        ServiceLocator.settingsRepository.setUsageTrackingEnabled(enabled)
        if (enabled) start(context) else stop(context)
    }

    /**
     * Starts the service only when the user has tracking enabled — used from
     * MainActivity.onStart() to recover from system/user kills.
     */
    suspend fun ensureStartedIfEnabled(context: Context) {
        val enabled = try {
            ServiceLocator.settingsRepository.uiSettings.first().usageTrackingEnabled
        } catch (_: Exception) {
            false
        }
        if (enabled) start(context)
    }

    /**
     * Builds the tracking notification. Both the foreground-service start and the
     * periodic refreshes post this notification under the same id.
     */
    fun buildNotification(context: Context, title: String, text: String): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val turnOff = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, TrackingEventsReceiver::class.java).setAction(ACTION_TURN_OFF),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, NotificationHelper.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_stat_datalens)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            // Persistent while tracking is enabled; never rings or vibrates
            // (the channel is IMPORTANCE_LOW and silent as well).
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp)
            .addAction(0, context.getString(R.string.notif_tracking_turn_off), turnOff)
            .build()
    }
}
