package com.datalens.app.notifications

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.datalens.app.R
import com.datalens.app.ServiceLocator
import com.datalens.app.domain.model.LimitConfig
import com.datalens.app.util.TimeUtils
import com.datalens.app.widget.UsageWidgetProvider
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
 * Refresh cadence (adaptive, battery friendly, no wakelocks):
 *  - immediately on start and whenever the default network changes,
 *  - **~45 seconds** while the default network is cellular and the user enabled
 *    "fast updates" in Settings (mobile data actively in use),
 *  - **~5 minutes** otherwise (Wi-Fi/no data),
 *  - when the screen turns on, so the shade is fresh right after waking the device,
 *  - whenever MainActivity comes to the foreground (it re-anchors the service).
 * During deep sleep the coroutine timer simply pauses — by design, so the device
 * can rest; the notification catches up as soon as the CPU wakes.
 *
 * The heavier current-cycle query is throttled to once every 5 minutes (it spans
 * a whole month); today's total is refreshed at the full cadence.
 *
 * The service is a `specialUse` foreground service (the only honest type for a
 * usage-notification tracker) and stops itself the moment the user disables
 * tracking, including via the notification's "Turn off" action.
 */
class UsageTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var observeJob: Job? = null
    private var configJob: Job? = null
    private val refreshMutex = Mutex()
    private var lastTitle: String? = null
    private var lastText: String? = null

    /** Current limit config + refresh preference, kept current via DataStore. */
    @Volatile
    private var limitConfig: LimitConfig = LimitConfig.DEFAULT

    @Volatile
    private var frequentRefresh: Boolean = true

    /** True while the system's default network is cellular (mobile data active). */
    @Volatile
    private var mobileDataActive: Boolean = false

    private var lastCycleFetchElapsed: Long = 0L
    @Volatile
    private var cachedCycleUsedBytes: Long = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        registerScreenOnReceiver()
        registerNetworkCallback()
        // Keep the cached config in sync; safe to run before startForeground.
        configJob = serviceScope.launch {
            try {
                ServiceLocator.settingsRepository.uiSettings.collect { settings ->
                    limitConfig = settings.limit
                    frequentRefresh = settings.frequentRefresh
                }
            } catch (_: Exception) {
                // Preferences unreadable — defaults stay in place.
            }
        }
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
        // Any start intent (app opened, boot restore, settings toggle) triggers an
        // immediate refresh; concurrent refreshes are serialized by the mutex.
        serviceScope.launch { refreshNow() }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try {
            unregisterReceiver(screenOnReceiver)
        } catch (_: Exception) {
            // Receiver was not registered — nothing to do.
        }
        try {
            (getSystemService(ConnectivityManager::class.java))?.unregisterNetworkCallback(
                defaultNetworkCallback,
            )
        } catch (_: Exception) {
            // Callback was not registered — nothing to do.
        }
        super.onDestroy()
    }

    /**
     * Periodic refresh loop with an adaptive interval. It exits via
     * cancellation (serviceScope.cancel makes the next [delay] throw), and the
     * delay itself does not advance during deep sleep.
     */
    private suspend fun refreshLoop() {
        while (true) {
            refreshNow()
            delay(currentIntervalMs())
        }
    }

    private fun currentIntervalMs(): Long =
        if (mobileDataActive && frequentRefresh) ACTIVE_INTERVAL_MS else IDLE_INTERVAL_MS

    /** One refresh cycle: query today's real totals and update the notification. */
    private suspend fun refreshNow() = refreshMutex.withLock {
        val zone = TimeUtils.zone()
        val today = TimeUtils.today(zone)
        val start = TimeUtils.startOfDay(today, zone)
        val end = TimeUtils.endOfDayExclusive(today, zone)

        val notification: Notification = try {
            val usage = ServiceLocator.usageRepository.totals(start, end)

            // Heavier month-long query, throttled to every 5 minutes.
            val nowElapsed = SystemClock.elapsedRealtime()
            if (cachedCycleUsedBytes < 0L ||
                nowElapsed - lastCycleFetchElapsed >= CYCLE_FETCH_INTERVAL_MS
            ) {
                val cycleRange = TimeUtils.billingCycleRange(limitConfig.billingCycleStartDay, today, zone)
                cachedCycleUsedBytes = try {
                    ServiceLocator.usageRepository.totals(cycleRange.start, cycleRange.end).totalBytes
                } catch (e: Exception) {
                    Log.w(TAG, "Cycle fetch failed; keeping previous value", e)
                    cachedCycleUsedBytes.coerceAtLeast(0L)
                }
                lastCycleFetchElapsed = nowElapsed
                // Alert evaluation piggybacks on the cycle fetch (cheap: today's
                // per-UID data is already cached by the totals call above).
                ServiceLocator.alertCoordinator.run(this)
            }

            val title = UsageNotificationText.title(usage.totalBytes)
            val detail = UsageNotificationText.detail(usage.transmittedBytes, usage.receivedBytes)
            val cycleLine = UsageNotificationText.cycleLine(cachedCycleUsedBytes, limitConfig)
            val text = "$detail\n$cycleLine"

            UsageTrackerCache.update(
                UsageTrackerCache.Snapshot(
                    todayTotalBytes = usage.totalBytes,
                    todayReceivedBytes = usage.receivedBytes,
                    todayTransmittedBytes = usage.transmittedBytes,
                    cycleUsedBytes = cachedCycleUsedBytes,
                    cycleLine = cycleLine,
                    trackingEnabled = true,
                    updatedAtElapsed = nowElapsed,
                ),
            )
            UsageWidgetProvider.pushUpdate(applicationContext)

            if (title == lastTitle && text == lastText) return@withLock
            lastTitle = title
            lastText = text
            TrackingController.buildNotification(this@UsageTrackingService, title, text)
        } catch (_: SecurityException) {
            // Usage Access was revoked while tracking — show an honest hint instead
            // of stale numbers. The hint is de-duplicated; the next cycle picks up
            // real data once access is granted again.
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

    /**
     * Event-driven "is mobile data the active network" signal — no polling.
     * When the default network changes (cellular ↔ Wi-Fi) we refresh immediately
     * and switch the loop between the active/idle intervals.
     */
    private val defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val active = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (active != mobileDataActive) {
                mobileDataActive = active
                serviceScope.launch { refreshNow() }
            }
        }

        override fun onLost(network: Network) {
            if (mobileDataActive) {
                mobileDataActive = false
                serviceScope.launch { refreshNow() }
            }
        }
    }

    private fun registerNetworkCallback() {
        try {
            getSystemService(ConnectivityManager::class.java)
                ?.registerDefaultNetworkCallback(defaultNetworkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback", e)
        }
    }

    companion object {
        private const val TAG = "UsageTrackingService"

        /** Fast cadence while mobile data is the active network (~45 s). */
        const val ACTIVE_INTERVAL_MS = 45_000L

        /** Back-off cadence when mobile data is idle (~5 min). */
        const val IDLE_INTERVAL_MS = 300_000L

        /** The month-long cycle query runs at most this often. */
        const val CYCLE_FETCH_INTERVAL_MS = 300_000L
    }
}
