package com.datalens.app.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.datalens.app.util.TimeUtils
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Schedules the two periodic background checks. WorkManager persists them across
 * reboots, so DataLens never needs a permanently running service for monitoring —
 * usage statistics come from Android itself on demand.
 */
object WorkScheduler {

    private const val DAILY_SUMMARY_WORK = "datalens_daily_summary"
    private const val LIMIT_CHECK_WORK = "datalens_limit_check"

    /** Local hour (24h) at which the daily summary is posted. */
    private const val DAILY_SUMMARY_HOUR = 21

    fun ensureScheduled(context: Context) {
        val workManager = WorkManager.getInstance(context)

        workManager.enqueueUniquePeriodicWork(
            DAILY_SUMMARY_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayUntil(DAILY_SUMMARY_HOUR), TimeUnit.MILLISECONDS)
                .build(),
        )

        workManager.enqueueUniquePeriodicWork(
            LIMIT_CHECK_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<LimitCheckWorker>(6, TimeUnit.HOURS)
                .build(),
        )
    }

    /** Millis until the next local occurrence of [hour]:00 (at least 1 minute). */
    internal fun initialDelayUntil(hour: Int, now: Long = System.currentTimeMillis()): Long {
        val zone: ZoneId = ZoneId.systemDefault()
        val nowDateTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
        var next = nowDateTime.toLocalDate().atTime(hour, 0)
        if (!next.isAfter(nowDateTime)) {
            next = next.plusDays(1)
        }
        val delay = Duration.between(nowDateTime, next).toMillis()
        return delay.coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
    }
}
