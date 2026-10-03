package com.datalens.app.domain.usecase

import com.datalens.app.domain.model.CycleInsights
import com.datalens.app.domain.model.DateRange
import com.datalens.app.domain.model.LimitConfig
import com.datalens.app.util.TimeUtils
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * Derives the dashboard's cycle analytics (pace, average, recommendation,
 * projection) from real recorded usage. Pure and unit-testable.
 *
 * Everything is honest about what it is:
 *  - average/recommended values are computed from actual recorded bytes;
 *  - the projection is a straight-line ESTIMATE (elapsed average × cycle length)
 *    and is always rendered as an estimate, never as measured data;
 *  - unlimited plans keep the usage/day numbers but have no allowance math.
 */
class ComputeCycleInsightsUseCase {

    operator fun invoke(
        cycleUsedBytes: Long,
        todayBytes: Long,
        config: LimitConfig,
        cycleRange: DateRange,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = TimeUtils.zone(),
    ): CycleInsights {
        val startDate: LocalDate = TimeUtils.localDateOf(cycleRange.start, zone)
        val lastDate: LocalDate = TimeUtils.localDateOf(cycleRange.end - 1, zone)
        val daysTotal = (lastDate.toEpochDay() - startDate.toEpochDay() + 1).toInt().coerceAtLeast(1)

        val todayRaw: LocalDate = TimeUtils.localDateOf(now, zone)
        val today = todayRaw.coerceIn(startDate, lastDate)
        val daysElapsed = (today.toEpochDay() - startDate.toEpochDay() + 1).toInt().coerceIn(1, daysTotal)
        val daysRemaining = daysTotal - daysElapsed

        val averageDailyBytes =
            ((cycleUsedBytes.coerceAtLeast(0L) + daysElapsed / 2) / daysElapsed).coerceAtLeast(0L)

        val todayVsAverageRatio =
            if (averageDailyBytes > 0L) todayBytes.toDouble() / averageDailyBytes else null

        val limited = config.isAllowanceConfigured
        val remaining = if (limited) config.monthlyAllowanceBytes - cycleUsedBytes else 0L

        val recommendedDailyBytes = if (limited && daysRemaining > 0 && remaining > 0L) {
            ((remaining + daysRemaining / 2) / daysRemaining).coerceAtLeast(0L)
        } else {
            null
        }

        val projectedCycleBytes = if (limited) averageDailyBytes * daysTotal else null
        val projectedExceedsLimit =
            if (limited && projectedCycleBytes != null) projectedCycleBytes > config.monthlyAllowanceBytes else null

        return CycleInsights(
            daysTotal = daysTotal,
            daysElapsed = daysElapsed,
            daysRemaining = daysRemaining,
            averageDailyBytes = averageDailyBytes,
            todayBytes = todayBytes,
            todayVsAverageRatio = todayVsAverageRatio,
            recommendedDailyBytes = recommendedDailyBytes,
            projectedCycleBytes = projectedCycleBytes,
            projectedExceedsLimit = projectedExceedsLimit,
        )
    }
}
