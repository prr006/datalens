package com.datalens.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * All period/boundary math for DataLens.
 *
 * Everything uses the device's local timezone so that "today", month boundaries and
 * billing-cycle boundaries match what the user sees on their clock.
 */
object TimeUtils {

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun now(): Long = System.currentTimeMillis()

    fun today(zone: ZoneId = zone()): LocalDate = LocalDate.now(zone)

    fun startOfDay(date: LocalDate, zone: ZoneId = zone()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Exclusive end (midnight of the following day). */
    fun endOfDayExclusive(date: LocalDate, zone: ZoneId = zone()): Long =
        startOfDay(date.plusDays(1), zone)

    fun startOfDay(millis: Long, zone: ZoneId = zone()): Long =
        startOfDay(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(), zone)

    fun localDateOf(millis: Long, zone: ZoneId = zone()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** Clamps a requested day-of-month (1..31) to a real day in the given month. */
    fun clampDayOfMonth(month: YearMonth, day: Int): Int = day.coerceIn(1, month.lengthOfMonth())

    /**
     * Returns the billing-cycle range (start inclusive, end exclusive) that contains
     * [reference]. A cycle configured to start on the 15th runs 15th -> 14th of the
     * next month. Month lengths are handled: a start day of 31 becomes Feb 28/29.
     */
    fun billingCycleRange(
        cycleStartDay: Int,
        reference: LocalDate = today(),
        zone: ZoneId = zone(),
    ): com.datalens.app.domain.model.DateRange {
        val currentMonth = YearMonth.from(reference)
        val effectiveDayThisMonth = clampDayOfMonth(currentMonth, cycleStartDay)
        val startMonth = if (reference.dayOfMonth >= effectiveDayThisMonth) currentMonth else currentMonth.minusMonths(1)
        return cycleRangeForMonth(startMonth, cycleStartDay, zone)
    }

    /** The cycle immediately before the one containing [reference]. */
    fun previousBillingCycleRange(
        cycleStartDay: Int,
        reference: LocalDate = today(),
        zone: ZoneId = zone(),
    ): com.datalens.app.domain.model.DateRange {
        val current = billingCycleRange(cycleStartDay, reference, zone)
        val startMonth = YearMonth.from(localDateOf(current.start, zone)).minusMonths(1)
        return cycleRangeForMonth(startMonth, cycleStartDay, zone)
    }

    private fun cycleRangeForMonth(startMonth: YearMonth, cycleStartDay: Int, zone: ZoneId): com.datalens.app.domain.model.DateRange {
        val start = startMonth.atDay(clampDayOfMonth(startMonth, cycleStartDay))
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val nextMonth = startMonth.plusMonths(1)
        val end = nextMonth.atDay(clampDayOfMonth(nextMonth, cycleStartDay))
            .atStartOfDay(zone).toInstant().toEpochMilli()
        return com.datalens.app.domain.model.DateRange(start, end)
    }

    /** "15 Oct 2026" style short date. */
    fun formatShortDate(millis: Long, zone: ZoneId = zone()): String {
        val date = localDateOf(millis, zone)
        val month = date.month.let { m ->
            m.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        }
        return "${date.dayOfMonth} $month ${date.year}"
    }

    /** "15 Oct" without the year (used for ranges in the current year). */
    fun formatDayMonth(millis: Long, zone: ZoneId = zone()): String {
        val date = localDateOf(millis, zone)
        val month = date.month.let { m ->
            m.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        }
        return "${date.dayOfMonth} $month"
    }

    /** Hour label like "14:00" or "2 PM" depending on locale. */
    fun formatHour(millis: Long, zone: ZoneId = zone()): String {
        val time = Instant.ofEpochMilli(millis).atZone(zone)
        return time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.getDefault()))
    }

    fun isoDate(millis: Long, zone: ZoneId = zone()): String =
        localDateOf(millis, zone).toString() // yyyy-MM-dd

    fun ordinal(day: Int): String {
        val suffix = when {
            day % 100 in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$day$suffix"
    }
}
