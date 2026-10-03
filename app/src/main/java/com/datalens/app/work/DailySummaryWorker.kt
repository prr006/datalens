package com.datalens.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.datalens.app.ServiceLocator
import com.datalens.app.domain.model.AlertKind
import com.datalens.app.domain.model.DateRange
import com.datalens.app.domain.model.UsagePeriod
import com.datalens.app.notifications.NotificationHelper
import com.datalens.app.util.TimeUtils
import com.datalens.app.util.UsageAccess
import kotlinx.coroutines.flow.first

/**
 * Posts the optional daily usage summary (and the optional high-usage alert).
 *
 * Anti-spam rules:
 *  - at most one daily summary per calendar day;
 *  - at most one high-usage notification per app per day;
 *  - nothing is posted unless every relevant toggle and permission is granted.
 */
class DailySummaryWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            doWorkInternal()
            Result.success()
        } catch (_: Exception) {
            // Statistics can fail (e.g. usage access revoked). Never retry-spam.
            Result.success()
        }
    }

    private suspend fun doWorkInternal() {
        val context = applicationContext
        val settings = ServiceLocator.settingsRepository
        val prefs = settings.rawPreferences.first()

        if (!prefs.notifications.enabled) return
        if (!UsageAccess.isGranted(context)) return
        if (!NotificationHelper.canPost(context)) return

        val config = settings.uiSettings.first().limit
        val now = System.currentTimeMillis()
        val todayIso = TimeUtils.isoDate(now)

        val repository = ServiceLocator.usageRepository
        val todayRange = UsagePeriod.Today.resolveRange(config.billingCycleStartDay, now)
        val usage = repository.periodUsage(todayRange)

        if (prefs.notifications.dailySummary && prefs.lastDailyNotifDate != todayIso) {
            val top = usage.apps.firstOrNull { it.totalBytes > 0L }
            NotificationHelper.showDailySummary(
                context,
                usedBytes = usage.totals.totalBytes,
                topAppName = top?.appName,
                topAppBytes = top?.totalBytes ?: 0L,
            )
            settings.markDailyNotifPosted(todayIso)
        }

        if (prefs.notifications.highUsageAlerts) {
            val today = TimeUtils.localDateOf(now)
            val previous7 = DateRange(
                TimeUtils.startOfDay(today.minusDays(7)),
                TimeUtils.startOfDay(today),
            )
            val previousUsage = repository.periodUsage(previous7)
            val alerts = ServiceLocator.detectAnomalies(usage.apps, previousUsage.apps, 7)
            val high = alerts.firstOrNull { it.kind == AlertKind.HIGH_USAGE_VS_AVERAGE }
            if (high != null) {
                val key = "$todayIso:${high.packageName}"
                if (prefs.lastHighNotifKey != key) {
                    NotificationHelper.showHighUsage(context, high.title, high.description)
                    settings.markHighNotifPosted(key)
                }
            }
        }
    }
}
