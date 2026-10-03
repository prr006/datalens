package com.datalens.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.datalens.app.ServiceLocator
import com.datalens.app.domain.model.LimitStatus
import com.datalens.app.notifications.NotificationHelper
import com.datalens.app.util.TimeUtils
import com.datalens.app.util.UsageAccess
import kotlinx.coroutines.flow.first

/**
 * Periodically compares cycle usage against the configured allowance and posts a
 * warning when the configured threshold (or 100%) is crossed.
 *
 * Anti-spam: each (cycle, level) combination notifies at most once — the key is
 * reset automatically because it contains the cycle start date.
 */
class LimitCheckWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            doWorkInternal()
            Result.success()
        } catch (_: Exception) {
            Result.success()
        }
    }

    private suspend fun doWorkInternal() {
        val context = applicationContext
        val settings = ServiceLocator.settingsRepository
        val prefs = settings.rawPreferences.first()

        if (!prefs.notifications.enabled) return
        if (!prefs.notifications.limitWarnings) return
        if (!UsageAccess.isGranted(context)) return
        if (!NotificationHelper.canPost(context)) return

        val config = settings.uiSettings.first().limit
        if (!config.isAllowanceConfigured) return

        val cycleRange = TimeUtils.billingCycleRange(config.billingCycleStartDay)
        val used = ServiceLocator.usageRepository.totals(cycleRange.start, cycleRange.end)
        val status = ServiceLocator.computeLimitStatus(used, config)
        if (status !is LimitStatus.Active) return

        val level = if (status.percentUsed >= 100.0) 100 else config.warningThresholdPercent
        if (status.percentUsed < level) return

        val key = "${TimeUtils.isoDate(cycleRange.start)}:$level"
        if (prefs.lastLimitNotifKey == key) return

        NotificationHelper.showLimitWarning(
            context,
            percentUsed = status.percentUsed.toInt(),
            usedBytes = status.usedBytes,
            allowanceBytes = status.allowanceBytes,
        )
        settings.markLimitNotifPosted(key)
    }
}
