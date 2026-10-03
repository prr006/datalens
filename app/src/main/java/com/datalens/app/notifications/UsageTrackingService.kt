package com.datalens.app.notifications

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.datalens.app.R
import com.datalens.app.ServiceLocator
import com.datalens.app.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Foreground service that keeps the persistent "today's mobile data" notification
 * in the notification shade.
 *
 * Data source: the app's one and only [com.datalens.app.data.repository.UsageRepository]
 * (backed by Android's NetworkStatsManager with TYPE_MOBILE) — exactly the same
 * real numbers the Overview screen shows. There is no separate counter.
 *
 * Update cadence (deliberately low-frequency, battery friendly):
 *  - immediately on start,
 *  - every [UPDATE_INTERVAL_MINUTES] minutes,
 *  - when the screen turns on (so the shade is fresh right after the user wakes
 *    the device),
 *  - whenever MainActivity comes to the foreground (it re-anchors the service).
 * No wakelocks are held: during deep sleep the timer simply pauses and the
 * notification is refreshed as soon as the CPU wakes up. Nothing here is
 * real-time packet monitoring — Android batches NetworkStats itself (see README).
 *
 * The service is a `specialUse` foreground service (the only honest type for a
 * usage-notification tracker) and stops itself the moment the user disables
 * tracking, including via the notification's "Turn off" action.
 */
class UsageTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var observeJob: Job? = null
    private val refreshMutex = Mutex()
    private var lastTitle: String? = null
    private var lastText: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        registerScreenOnReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires startForeground() within five seconds of
        // startForegroundService() — even when the notification permission is
        // missing (the service then runs with its notification hidden).
        // Never show placeholder numbers: an honest "Updating…" until the first
        // real query lands.
        startAsForeground(
            TrackingController.buildNotification(
                this,
                getString(R.string.notif_tracking_updating_title),
                getString(R.string.notif_tracking_updating_text),
            ),
        )

        if (loopJob == null) {
            loopJob = serviceScope.launch { refreshLoop() }
        }
        if (observeJob == null) {
            observeJob = serviceScope.launch { observeDisableFlag() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try {
            unregisterReceiver(screenOnReceiver)
        } catch (_: Exception) {
            // Receiver was not registered — nothing to do.
        }
        super.onDestroy()
    }

    /**
     * Periodic refresh loop. It exits via cancellation: when the service is
     * destroyed, serviceScope.cancel() makes the next [delay] throw
     * CancellationException, which unwinds the loop. The delay uses the
     * coroutine scheduler, which does not advance during deep sleep — by
     * design, so the device can rest.
     */
    private suspend fun refreshLoop() {
        while (true) {
            refreshNow()
            delay(UPDATE_INTERVAL_MS)
        }
    }

    /** One refresh cycle: query today's real totals and update the notification. */
    private suspend fun refreshNow() = refreshMutex.withLock {
        val zone = TimeUtils.zone()
        val today = TimeUtils.today(zone)
        val start = TimeUtils.startOfDay(today, zone)
        val end = TimeUtils.endOfDayExclusive(today, zone)

        val notification: Notification = try {
            val usage = ServiceLocator.usageRepository.totals(start, end)
            val title = UsageNotificationText.title(usage.totalBytes)
            val text = UsageNotificationText.detail(usage.transmittedBytes, usage.receivedBytes)
            if (title == lastTitle && text == lastText) return@withLock
            lastTitle = title
            lastText = text
            TrackingController.buildNotification(this@UsageTrackingService, title, text)
        } catch (_: SecurityException) {
            // Usage Access was revoked while tracking — show an honest hint instead
            // of stale numbers. The hint is de-duplicated like normal updates; the
            // next cycle picks up real data once access is granted again.
            val title = getString(R.string.notif_tracking_no_access_title)
            val text = getString(R.string.notif_tracking_no_access_text)
            if (title == lastTitle && text == lastText) return@withLock
            lastTitle = title
            lastText = text
            TrackingController.buildNotification(this@UsageTrackingService, title, text)
        } catch (e: Exception) {
            // Android's statistics service hiccuped (OEM quirk, binder failure).
            // Keep the previous notification and retry on the next cycle rather
            // than showing anything made up.
            Log.w(TAG, "Usage refresh failed; keeping previous notification", e)
            return@withLock
        }

        try {
            NotificationManagerCompat.from(this)
                .notify(NotificationHelper.NOTIF_ID_TRACKING, notification)
        } catch (_: SecurityException) {
            // Notification permission missing (Android 13+): the service keeps
            // running; the shade stays hidden until the user allows notifications.
        }
    }

    /** Stops the service as soon as tracking is disabled from anywhere. */
    private suspend fun observeDisableFlag() {
        try {
            ServiceLocator.settingsRepository.uiSettings.collect { settings ->
                if (!settings.usageTrackingEnabled) {
                    stopSelf()
                    return@collect
                }
            }
        } catch (_: Exception) {
            // If the preferences flow fails, stop rather than tracking against
            // the user's will.
            stopSelf()
        }
    }

    private fun startAsForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NotificationHelper.NOTIF_ID_TRACKING, notification, type)
    }

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                serviceScope.launch { refreshNow() }
            }
        }
    }

    private fun registerScreenOnReceiver() {
        // ACTION_SCREEN_ON is a protected system broadcast; NOT_EXPORTED keeps
        // any other app from triggering it.
        ContextCompat.registerReceiver(
            this,
            screenOnReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    companion object {
        private const val TAG = "UsageTrackingService"

        /**
         * How often the notification refreshes while the device is awake.
         * 15 minutes: far below any high-frequency polling, and matched to the
         * granularity at which Android itself batches NetworkStats data.
         */
        const val UPDATE_INTERVAL_MINUTES = 15L
        const val UPDATE_INTERVAL_MS = UPDATE_INTERVAL_MINUTES * 60_000L
    }
}
