package com.datalens.app.domain.model

/** One row of the overall mobile-data summary card. */
data class SummaryEntry(
    val label: String,
    val bytes: Long,
    val range: DateRange,
    /** e.g. "32% more than yesterday" — null when a comparison would be misleading. */
    val comparison: String?,
)

/** Everything the Overview screen renders for the selected period. */
data class OverviewData(
    val period: UsagePeriod,
    val range: DateRange,
    val usage: PeriodUsage,
    val series: List<UsagePoint>,
    val granularity: Granularity,
    val todaySummary: SummaryEntry,
    val yesterdaySummary: SummaryEntry,
    val lastSevenDaysSummary: SummaryEntry,
    val currentCycleSummary: SummaryEntry,
    val cycleRange: DateRange,
)
