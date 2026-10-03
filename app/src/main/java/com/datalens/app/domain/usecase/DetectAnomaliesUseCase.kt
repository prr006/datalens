package com.datalens.app.domain.usecase

import com.datalens.app.domain.model.AlertKind
import com.datalens.app.domain.model.AppUsageInfo
import com.datalens.app.domain.model.UsageAlert
import com.datalens.app.util.ByteFormatter
import com.datalens.app.util.Formatters
import kotlin.math.roundToLong

/**
 * Simple, transparent, local anomaly detection — no machine learning, no predictions.
 *
 * Two comparisons are made, both clearly described in the alert text:
 *  1. An app's usage today vs. its own average daily usage over the previous N days.
 *  2. An app's share of today's total traffic.
 *
 * When there is no baseline (previous days had zero usage), no multiple is computed —
 * the alert says so explicitly instead of inventing a ratio.
 */
class DetectAnomaliesUseCase {

    operator fun invoke(
        todayApps: List<AppUsageInfo>,
        previousPeriodApps: List<AppUsageInfo>,
        previousDays: Int = 7,
    ): List<UsageAlert> {
        val alerts = mutableListOf<UsageAlert>()
        if (previousDays <= 0) return alerts

        val prevByPackage = previousPeriodApps.associateBy { it.packageName }

        for (app in todayApps.sortedByDescending { it.totalBytes }) {
            if (app.totalBytes < MIN_ANOMALY_BYTES) continue

            val prevTotal = prevByPackage[app.packageName]?.totalBytes ?: 0L
            val avgPerDay = prevTotal.toDouble() / previousDays

            if (avgPerDay >= MIN_BASELINE_BYTES) {
                val ratio = app.totalBytes / avgPerDay
                if (ratio >= RATIO_THRESHOLD && (app.totalBytes - avgPerDay) >= MIN_DELTA_BYTES) {
                    alerts += UsageAlert(
                        kind = AlertKind.HIGH_USAGE_VS_AVERAGE,
                        uid = app.uid,
                        packageName = app.packageName,
                        appName = app.appName,
                        icon = app.icon,
                        title = "${app.appName} is using much more data than usual",
                        description = "${app.appName} used ${ByteFormatter.format(app.totalBytes)} " +
                            "of mobile data today — ${Formatters.ratio(ratio)} its recent daily average " +
                            "(${ByteFormatter.format(avgPerDay.roundToLong())}/day over the previous " +
                            "$previousDays days).",
                        severity = (ratio * 10).toInt(),
                    )
                }
            } else if (app.totalBytes >= NEW_USAGE_MIN_BYTES) {
                alerts += UsageAlert(
                    kind = AlertKind.NEW_SIGNIFICANT_USAGE,
                    uid = app.uid,
                    packageName = app.packageName,
                    appName = app.appName,
                    icon = app.icon,
                    title = "${app.appName} used ${ByteFormatter.format(app.totalBytes)} today",
                    description = "No mobile data was recorded for this app during the previous " +
                        "$previousDays days, so no average could be computed.",
                    severity = 5,
                )
            }
        }

        val totalToday = todayApps.sumOf { it.totalBytes }
        val top = todayApps.maxByOrNull { it.totalBytes }
        if (totalToday > 0 && top != null &&
            top.totalBytes >= DOMINANT_MIN_BYTES &&
            top.totalBytes.toDouble() / totalToday >= DOMINANT_SHARE
        ) {
            alerts += UsageAlert(
                kind = AlertKind.DOMINANT_SHARE,
                uid = top.uid,
                packageName = top.packageName,
                appName = top.appName,
                icon = top.icon,
                title = "${top.appName} dominates today's mobile traffic",
                description = "It consumed ${Formatters.percent(top.totalBytes.toDouble() / totalToday)} " +
                    "of today's mobile data (${ByteFormatter.format(top.totalBytes)} of " +
                    "${ByteFormatter.format(totalToday)}).",
                severity = 4,
            )
        }

        return alerts.sortedByDescending { it.severity }
    }

    companion object {
        /** Apps below this usage are never flagged (20 MB). */
        const val MIN_ANOMALY_BYTES = 20L * 1024L * 1024L

        /** Flag when today's usage is at least 2× the recent daily average. */
        const val RATIO_THRESHOLD = 2.0

        /** …and the absolute difference is at least 20 MB. */
        const val MIN_DELTA_BYTES = 20L * 1024L * 1024L

        /** A baseline average below 2 MB/day is treated as "no baseline". */
        const val MIN_BASELINE_BYTES = 2L * 1024L * 1024L

        /** Usage that appears with no recent history must be at least 50 MB. */
        const val NEW_USAGE_MIN_BYTES = 50L * 1024L * 1024L

        /** An app consuming ≥ 35% of today's traffic (min 50 MB) is "dominant". */
        const val DOMINANT_SHARE = 0.35
        const val DOMINANT_MIN_BYTES = 50L * 1024L * 1024L
    }
}
