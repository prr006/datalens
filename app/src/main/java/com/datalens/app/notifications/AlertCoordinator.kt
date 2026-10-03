package com.datalens.app.notifications

import android.content.Context
import android.util.Log
import com.datalens.app.data.repository.SettingsRepository
import com.datalens.app.data.repository.UsageRepository
import com.datalens.app.domain.model.LimitConfig
import com.datalens.app.domain.usecase.AlertsInput
import com.datalens.app.domain.usecase.EvaluateAlertsUseCase
import com.datalens.app.util.TimeUtils
import com.datalens.app.util.UsageAccess
import kotlinx.coroutines.flow.first

/**
 * Runs one alert-evaluation pass against REAL data and posts the resulting
 * notifications. Shared by the periodic worker and the usage-tracking service
 * so alert logic exists exactly once.
 *
 * Anti-spam is enforced by [EvaluateAlertsUseCase] keys persisted in DataStore:
 * once per threshold per cycle, once per day for the daily threshold, once per
 * app per day for per-app thresholds. Everything re-arms automatically.
 */
class AlertCoordinator(
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
    private val evaluateAlerts: EvaluateAlertsUseCase,
) {

    suspend fun run(context: Context) {
        try {
            runInternal(context)
        } catch (e: SecurityException) {
            // Usage Access missing — nothing we can honestly alert about.
        } catch (e: Exception) {
            Log.w(TAG, "Alert evaluation failed", e)
        }
    }

    private suspend fun runInternal(context: Context) {
        if (!UsageAccess.isGranted(context)) return
        if (!NotificationHelper.canPost(context)) return

        val prefs = settingsRepository.rawPreferences.first()
        if (!prefs.notifications.enabled) return

        val config: LimitConfig = settingsRepository.uiSettings.first().limit
        val hasCycleAlerts = prefs.notifications.limitWarnings && config.isAllowanceConfigured
        val hasUsageAlerts = prefs.notifications.highUsageAlerts &&
            (config.dailyAlertThresholdBytes > 0L || config.perAppAlertThresholdBytes > 0L)
        if (!hasCycleAlerts && !hasUsageAlerts) return

        val cycleRange = TimeUtils.billingCycleRange(config.billingCycleStartDay)
        val todayRange = com.datalens.app.domain.model.UsagePeriod.Today
            .resolveRange(config.billingCycleStartDay, TimeUtils.now())

        val todayUsage = usageRepository.totals(todayRange.start, todayRange.end)
        val cycleUsage = usageRepository.totals(cycleRange.start, cycleRange.end)

        // Top apps today — the period query reuses the cached per-UID data that
        // the totals call above just fetched, so this adds no extra stats query.
        val topApps = usageRepository.periodUsage(todayRange)
            .apps
            .filter { it.totalBytes > 0L }
            .take(5)
            .map { it.packageName to it.totalBytes }

        val outcome = evaluateAlerts(
            AlertsInput(
                todayTotalBytes = todayUsage.totalBytes,
                cycleUsedBytes = cycleUsage.totalBytes,
                cycleStartIso = TimeUtils.isoDate(cycleRange.start),
                todayIso = TimeUtils.isoDate(todayRange.start),
                topAppsToday = topApps,
            ),
            config,
            storedCycleKey = prefs.lastLimitNotifKey,
            storedDailyKey = prefs.lastDailyThresholdAlertKey,
            storedAppKeys = prefs.postedAppAlertKeys,
        )

        var posted = false

        if (outcome.cycleThresholdCrossed != null && hasCycleAlerts) {
            val percentUsed = if (config.monthlyAllowanceBytes > 0L) {
                ((cycleUsage.totalBytes * 100L) / config.monthlyAllowanceBytes).toInt()
            } else {
                outcome.cycleThresholdCrossed
            }
            NotificationHelper.showLimitWarning(
                context,
                percentUsed = percentUsed,
                usedBytes = cycleUsage.totalBytes,
                allowanceBytes = config.monthlyAllowanceBytes,
            )
            outcome.cycleThresholdKey?.let { settingsRepository.markLimitNotifPosted(it) }
            posted = true
        }

        if (outcome.dailyThresholdCrossed && hasUsageAlerts) {
            NotificationHelper.showDailyThresholdAlert(
                context,
                usedBytes = todayUsage.totalBytes,
                thresholdBytes = config.dailyAlertThresholdBytes,
            )
            outcome.dailyThresholdKey?.let { settingsRepository.markDailyThresholdAlertPosted(it) }
            posted = true
        }

        if (outcome.appsCrossedThreshold.isNotEmpty() && hasUsageAlerts) {
            NotificationHelper.showAppThresholdAlert(
                context,
                apps = outcome.appsCrossedThreshold,
                thresholdBytes = config.perAppAlertThresholdBytes,
            )
            settingsRepository.markAppAlertsPosted(
                outcome.appThresholdKeys,
                keepFromIsoDate = TimeUtils.isoDate(todayRange.start),
            )
            posted = true
        }

        if (posted) Log.i(TAG, "Alerts posted: cycle=${outcome.cycleThresholdCrossed} daily=${outcome.dailyThresholdCrossed} apps=${outcome.appsCrossedThreshold.size}")
    }

    companion object {
        private const val TAG = "AlertCoordinator"
    }
}
