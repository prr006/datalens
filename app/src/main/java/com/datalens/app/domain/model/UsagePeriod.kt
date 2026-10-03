package com.datalens.app.domain.model

import com.datalens.app.util.TimeUtils
import java.time.LocalDate

/**
 * The selectable usage periods. "Cycle" periods follow the user-configured
 * billing-cycle start day rather than calendar months.
 */
sealed interface UsagePeriod {
    val id: String

    data object Today : UsagePeriod {
        override val id: String = "today"
    }

    data object Yesterday : UsagePeriod {
        override val id: String = "yesterday"
    }

    data object LastSevenDays : UsagePeriod {
        override val id: String = "last7"
    }

    data object LastThirtyDays : UsagePeriod {
        override val id: String = "last30"
    }

    data object CurrentCycle : UsagePeriod {
        override val id: String = "cycle"
    }

    data object PreviousCycle : UsagePeriod {
        override val id: String = "prev_cycle"
    }

    data class Custom(val startDate: LocalDate, val endDateInclusive: LocalDate) : UsagePeriod {
        override val id: String = "custom"

        init {
            require(!startDate.isAfter(endDateInclusive)) { "Custom range start after end" }
        }
    }

    fun resolveRange(cycleStartDay: Int, now: Long = System.currentTimeMillis()): DateRange {
        val today = TimeUtils.localDateOf(now)
        return when (this) {
            Today -> DateRange(
                TimeUtils.startOfDay(today),
                minOf(TimeUtils.endOfDayExclusive(today), now + DAY_MS),
            )
            Yesterday -> DateRange(
                TimeUtils.startOfDay(today.minusDays(1)),
                TimeUtils.startOfDay(today),
            )
            LastSevenDays -> DateRange(
                TimeUtils.startOfDay(today.minusDays(6)),
                minOf(TimeUtils.endOfDayExclusive(today), now + DAY_MS),
            )
            LastThirtyDays -> DateRange(
                TimeUtils.startOfDay(today.minusDays(29)),
                minOf(TimeUtils.endOfDayExclusive(today), now + DAY_MS),
            )
            CurrentCycle -> TimeUtils.billingCycleRange(cycleStartDay, today)
            PreviousCycle -> TimeUtils.previousBillingCycleRange(cycleStartDay, today)
            is Custom -> {
                val start = TimeUtils.startOfDay(startDate)
                val end = TimeUtils.endOfDayExclusive(endDateInclusive)
                DateRange(start, end)
            }
        }
    }

    /** How the chart for this period should be bucketed. */
    fun chartGranularity(): Granularity = when (this) {
        Today, Yesterday -> Granularity.HOURLY
        is Custom -> if (startDate == endDateInclusive) Granularity.HOURLY else Granularity.DAILY
        else -> Granularity.DAILY
    }

    fun displayName(): String = when (this) {
        Today -> "Today"
        Yesterday -> "Yesterday"
        LastSevenDays -> "Last 7 days"
        LastThirtyDays -> "Last 30 days"
        CurrentCycle -> "This cycle"
        PreviousCycle -> "Previous cycle"
        is Custom -> "Custom range"
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L

        /** Restores a period from navigation arguments. */
        fun fromArgs(id: String, startMillis: Long, endMillis: Long): UsagePeriod {
            return when (id) {
                Today.id -> Today
                Yesterday.id -> Yesterday
                LastSevenDays.id -> LastSevenDays
                LastThirtyDays.id -> LastThirtyDays
                CurrentCycle.id -> CurrentCycle
                PreviousCycle.id -> PreviousCycle
                Custom.id -> {
                    var start = TimeUtils.localDateOf(startMillis)
                    var endIncl = TimeUtils.localDateOf(endMillis - 1)
                    if (endIncl.isBefore(start)) endIncl = start
                    if (start.isAfter(LocalDate.now())) start = LocalDate.now()
                    Custom(start, endIncl)
                }
                else -> Today
            }
        }
    }
}
