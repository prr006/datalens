package com.datalens.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.datalens.app.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manifest receiver for the tracking notification's lifecycle events:
 *
 *  - `ACTION_TURN_OFF` (sent by the notification's quick action) disables
 *    tracking via [TrackingController.setEnabled].
 *  - `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` restart the service when the user
 *    has tracking enabled, so the notification survives reboots and app updates.
 *
 * On Android 15, `specialUse` foreground services may still be started from
 * BOOT_COMPLETED (only dataSync/camera/mediaPlayback/phoneCall/mediaProjection/
 * microphone are restricted). If an OEM build refuses anyway, the receiver falls
 * back to posting one direct notification update and lets MainActivity re-anchor
 * the service the next time the app is opened.
 */
class TrackingEventsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            TrackingController.ACTION_TURN_OFF -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        try {
                            TrackingController.setEnabled(appContext, false)
                        } catch (_: Exception) {
                            // DataStore write failed; the service's own flag observer
                            // is the backstop once preferences become readable again.
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }

            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        val enabled = try {
                            ServiceLocator.settingsRepository.uiSettings.first().usageTrackingEnabled
                        } catch (_: Exception) {
                            false
                        }
                        if (enabled) {
                            TrackingController.start(appContext)
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
