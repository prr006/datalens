package com.datalens.app

import com.datalens.app.domain.model.Granularity
import com.datalens.app.domain.model.UsagePeriod
import com.datalens.app.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class UsagePeriodTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun millisOf(date: LocalDate, hour: Int = 0): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val now: Long = LocalDate.of(2026, 10, 3).atTime(14, 30)
        .atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `today starts at local midnight`() {
        val range = UsagePeriod.Today.resolveRange(1, now)
        assertEquals(millisOf(LocalDate.of(2026, 10, 3)), range.start)
    }

    @Test
    fun `yesterday is the previous local day`() {
        val range = UsagePeriod.Yesterday.resolveRange(1, now)
        assertEquals(millisOf(LocalDate.of(2026, 10, 2)), range.start)
        assertEquals(millisOf(LocalDate.of(2026, 10, 3)), range.end)
    }

    @Test
    fun `last 7 days includes today plus six previous days`() {
        val range = UsagePeriod.LastSevenDays.resolveRange(1, now)
        assertEquals(millisOf(LocalDate.of(2026, 9, 27)), range.start)
        assertEquals(millisOf(LocalDate.of(2026, 10, 4)), range.end)
        assertEquals(7, TimeUtils.localDateOf(range.end - 1).toEpochDay() -
            TimeUtils.localDateOf(range.start).toEpochDay() + 1)
    }

    @Test
    fun `last 30 days includes today plus twenty-nine days`() {
        val range = UsagePeriod.LastThirtyDays.resolveRange(1, now)
        assertEquals(millisOf(LocalDate.of(2026, 9, 4)), range.start)
    }

    @Test
    fun `current cycle follows billing day`() {
        val range = UsagePeriod.CurrentCycle.resolveRange(15, now)
        assertEquals(millisOf(LocalDate.of(2026, 9, 15)), range.start)
        assertEquals(millisOf(LocalDate.of(2026, 10, 15)), range.end)
    }

    @Test
    fun `previous cycle precedes current cycle`() {
        val range = UsagePeriod.PreviousCycle.resolveRange(15, now)
        assertEquals(millisOf(LocalDate.of(2026, 8, 15)), range.start)
        assertEquals(millisOf(LocalDate.of(2026, 9, 15)), range.end)
    }

    @Test
    fun `custom range is inclusive on both ends`() {
        val period = UsagePeriod.Custom(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 2))
        val range = period.resolveRange(1, now)
        assertEquals(millisOf(LocalDate.of(2026, 9, 28)), range.start)
        assertEquals(millisOf(LocalDate.of(2026, 10, 3)), range.end)
    }

    @Test
    fun `granularity is hourly for single days and daily otherwise`() {
        assertEquals(Granularity.HOURLY, UsagePeriod.Today.chartGranularity())
        assertEquals(Granularity.HOURLY, UsagePeriod.Yesterday.chartGranularity())
        assertEquals(
            Granularity.HOURLY,
            UsagePeriod.Custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1)).chartGranularity(),
        )
        assertEquals(Granularity.DAILY, UsagePeriod.LastSevenDays.chartGranularity())
        assertEquals(Granularity.DAILY, UsagePeriod.LastThirtyDays.chartGranularity())
        assertEquals(Granularity.DAILY, UsagePeriod.CurrentCycle.chartGranularity())
        assertEquals(
            Granularity.DAILY,
            UsagePeriod.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)).chartGranularity(),
        )
    }
}
