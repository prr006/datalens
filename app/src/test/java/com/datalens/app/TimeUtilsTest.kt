package com.datalens.app

import com.datalens.app.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class TimeUtilsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun millisOf(date: LocalDate, hour: Int = 0): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `billing cycle 15th runs 15th to 14th`() {
        val today = LocalDate.of(2026, 10, 3)
        val range = TimeUtils.billingCycleRange(15, today, zone)
        assertEquals(millisOf(LocalDate.of(2026, 9, 15)), range.start)
        assertEquals(millisOf(LocalDate.of(2026, 10, 15)), range.end)
    }

    @Test
    fun `billing cycle on the start day itself belongs to the new cycle`() {
        val today = LocalDate.of(2026, 10, 15)
        val range = TimeUtils.billingCycleRange(15, today, zone)
        assertEquals(millisOf(LocalDate.of(2026, 10, 15)), range.start)
        assertEquals(millisOf(LocalDate.of(2026, 11, 15)), range.end)
    }

    @Test
    fun `billing cycle day 31 clamps in February`() {
        // Cycle starting Jan 31; February has 28 days in 2026.
        val today = LocalDate.of(2026, 2, 10)
        val range = TimeUtils.billingCycleRange(31, today, zone)
        assertEquals(millisOf(LocalDate.of(2025, 12, 31)), range.start)
        assertEquals(millisOf(LocalDate.of(2026, 1, 31)), range.end)

        val today2 = LocalDate.of(2026, 3, 5)
        val range2 = TimeUtils.billingCycleRange(31, today2, zone)
        // Feb start clamps to Feb 28; end clamps to Mar 31.
        assertEquals(millisOf(LocalDate.of(2026, 2, 28)), range2.start)
        assertEquals(millisOf(LocalDate.of(2026, 3, 31)), range2.end)
    }

    @Test
    fun `cycle day 1 equals calendar month`() {
        val today = LocalDate.of(2026, 10, 3)
        val range = TimeUtils.billingCycleRange(1, today, zone)
        assertEquals(millisOf(LocalDate.of(2026, 10, 1)), range.start)
        assertEquals(millisOf(LocalDate.of(2026, 11, 1)), range.end)
    }

    @Test
    fun `previous cycle precedes current cycle`() {
        val today = LocalDate.of(2026, 10, 3)
        val current = TimeUtils.billingCycleRange(15, today, zone)
        val previous = TimeUtils.previousBillingCycleRange(15, today, zone)
        // Current: Sep 15 – Oct 15. Previous: Aug 15 – Sep 15.
        assertEquals(millisOf(LocalDate.of(2026, 8, 15)), previous.start)
        assertEquals(current.start, previous.end)
    }

    @Test
    fun `day boundaries are local midnight`() {
        val date = LocalDate.of(2026, 10, 3)
        assertEquals(
            millisOf(LocalDate.of(2026, 10, 3)),
            TimeUtils.startOfDay(date, zone),
        )
        assertEquals(
            millisOf(LocalDate.of(2026, 10, 4)),
            TimeUtils.endOfDayExclusive(date, zone),
        )
    }

    @Test
    fun `clampDayOfMonth respects month length`() {
        assertEquals(31, TimeUtils.clampDayOfMonth(YearMonth.of(2026, 1), 31))
        assertEquals(28, TimeUtils.clampDayOfMonth(YearMonth.of(2026, 2), 31))
        assertEquals(29, TimeUtils.clampDayOfMonth(YearMonth.of(2028, 2), 31))
        assertEquals(30, TimeUtils.clampDayOfMonth(YearMonth.of(2026, 4), 31))
        assertEquals(1, TimeUtils.clampDayOfMonth(YearMonth.of(2026, 4), 1))
    }

    @Test
    fun `ordinal suffixes`() {
        assertEquals("1st", TimeUtils.ordinal(1))
        assertEquals("2nd", TimeUtils.ordinal(2))
        assertEquals("3rd", TimeUtils.ordinal(3))
        assertEquals("11th", TimeUtils.ordinal(11))
        assertEquals("12th", TimeUtils.ordinal(12))
        assertEquals("13th", TimeUtils.ordinal(13))
        assertEquals("21st", TimeUtils.ordinal(21))
        assertEquals("31st", TimeUtils.ordinal(31))
    }
}
