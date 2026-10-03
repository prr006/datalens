package com.datalens.app.domain.usecase

import com.datalens.app.data.repository.UsageRepository
import com.datalens.app.domain.model.ByteTotals
import com.datalens.app.domain.model.DateRange
import com.datalens.app.domain.model.OverviewData
import com.datalens.app.domain.model.SummaryEntry
import com.datalens.app.domain.model.UsagePeriod
import com.datalens.app.util.Formatters
import com.datalens.app.util.TimeUtils

/**
 * Assembles everything the Overview screen needs in one pass, reusing the
 * repository's short-lived raw cache so overlapping windows are queried once.
 */
class GetOverviewDataUseCase(private val repository: UsageRepository) {

    suspend operator fun invoke(
        period: UsagePeriod,
        cycleStartDay: Int,
        now: Long = System.currentTimeMillis(),
    ): OverviewData {
        val range = period.resolveRange(cycleStartDay, now)
        val usage = repository.periodUsage(range)
        val series = repository.usageSeries(range, period.chartGranularity())

        val today = TimeUtils.localDateOf(now)
        val todayRange = UsagePeriod.Today.resolveRange(cycleStartDay, now)
        val yesterdayRange = UsagePeriod.Yesterday.resolveRange(cycleStartDay, now)
        val last7Range = UsagePeriod.LastSevenDays.resolveRange(cycleStartDay, now)
        val prev7Range = DateRange(
            TimeUtils.startOfDay(today.minusDays(13)),
            TimeUtils.startOfDay(today.minusDays(6)),
        )
        val cycleRange = TimeUtils.billingCycleRange(cycleStartDay, today)
        val prevCycleRange = TimeUtils.previousBillingCycleRange(cycleStartDay, today)

        val todayTotals = repository.totals(todayRange.start, todayRange.end)
        val yesterdayTotals = repository.totals(yesterdayRange.start, yesterdayRange.end)
        val last7Totals = repository.totals(last7Range.start, last7Range.end)
        val prev7Totals = repository.totals(prev7Range.start, prev7Range.end)
        val cycleTotals = repository.totals(cycleRange.start, cycleRange.end)
        val prevCycleTotals = repository.totals(prevCycleRange.start, prevCycleRange.end)

        return OverviewData(
            period = period,
            range = range,
            usage = usage,
            series = series,
            granularity = period.chartGranularity(),
            todaySummary = SummaryEntry(
                label = "Today",
                bytes = todayTotals.totalBytes,
                range = todayRange,
                comparison = comparisonText(todayTotals, yesterdayTotals, "yesterday"),
            ),
            yesterdaySummary = SummaryEntry(
                label = "Yesterday",
                bytes = yesterdayTotals.totalBytes,
                range = yesterdayRange,
                comparison = null,
            ),
            lastSevenDaysSummary = SummaryEntry(
                label = "Last 7 days",
                bytes = last7Totals.totalBytes,
                range = last7Range,
                comparison = comparisonText(last7Totals, prev7Totals, "the previous 7 days"),
            ),
            currentCycleSummary = SummaryEntry(
                label = "This cycle",
                bytes = cycleTotals.totalBytes,
                range = cycleRange,
                comparison = comparisonText(cycleTotals, prevCycleTotals, "the previous cycle"),
            ),
            cycleRange = cycleRange,
        )
    }

    /**
     * "+32% vs yesterday"-style text, or null when the comparison would be
     * misleading (previous period had no recorded usage).
     */
    private fun comparisonText(current: ByteTotals, previous: ByteTotals, previousLabel: String): String? {
        if (previous.totalBytes <= 0L) return null
        val delta = (current.totalBytes - previous.totalBytes).toDouble() / previous.totalBytes
        return "${Formatters.signedPercent(delta)} vs $previousLabel"
    }
}
