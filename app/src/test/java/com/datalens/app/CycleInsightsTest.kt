package com.datalens.app

import com.datalens.app.domain.model.DateRange
import com.datalens.app.domain.model.LimitConfig
import com.datalens.app.domain.usecase.ComputeCycleInsightsUseCase
import com.datalens.app.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Cycle analytics must be derived from real recorded usage with honest, clearly
 * bounded day math: clamped boundaries, rounded averages, null (not fake)
 * values when a recommendation cannot be computed.
 */
class CycleInsightsTest {

    private val useCase = ComputeCycleInsightsUseCase()
    private val zone: ZoneId = ZoneId.of("UTC")
    private val gb = 1024L * 1024L * 1024L

    /** A 30-day cycle: 2025-09-10 .. 2025-10-09, "now" = 2025-09-19 (day 10). */
    private val cycleStart = LocalDate.of(2025, 9, 10)
    private val cycleRange = DateRange(
        start = TimeUtils.startOfDay(cycleStart, zone),
        end = TimeUtils.startOfDay(LocalDate.of(2025, 10, 10), zone), // exclusive
    )
    private val now = TimeUtils.startOfDay(LocalDate.of(2025, 9, 19), zone) + 12 * 3_600_000L
    private val config = LimitConfig(monthlyAllowanceBytes = 30L * gb)

    @Test
    fun `day counts for a mid-cycle date`() {
        val insights = useCase(9L * gb, 500 * 1024 * 1024, config, cycleRange, now, zone)
        assertEquals(30, insights.daysTotal)
        assertEquals(10, insights.daysElapsed)
        assertEquals(20, insights.daysRemaining)
    }

    @Test
    fun `average is total divided by elapsed days`() {
        val insights = useCase(9L * gb, 0L, config, cycleRange, now, zone)
        // 9 GiB over 10 days, rounded to nearest (implementation adds daysElapsed/2)
        assertEquals((9L * gb + 5) / 10, insights.averageDailyBytes)
    }

    @Test
    fun `today vs average ratio`() {
        val avg = (9L * gb) / 10
        val insights = useCase(9L * gb, avg * 2, config, cycleRange, now, zone)
        assertEquals(2.0, insights.todayVsAverageRatio!!, 0.001)
    }

    @Test
    fun `ratio is null when average is zero`() {
        val insights = useCase(0L, 0L, config, cycleRange, now, zone)
        assertNull(insights.todayVsAverageRatio)
    }

    @Test
    fun `recommended daily allowance splits remaining bytes over remaining days`() {
        // 30 GiB allowance, 9 used on day 10 → 21 GiB over 20 days
        val insights = useCase(9L * gb, 0L, config, cycleRange, now, zone)
        assertEquals((21L * gb) / 20, insights.recommendedDailyBytes)
    }

    @Test
    fun `no recommendation once the allowance is exhausted`() {
        val insights = useCase(31L * gb, 0L, config, cycleRange, now, zone)
        assertNull(insights.recommendedDailyBytes)
    }

    @Test
    fun `no recommendation on the last day of the cycle`() {
        val lastDay = TimeUtils.startOfDay(LocalDate.of(2025, 10, 9), zone) + 3_600_000L
        val insights = useCase(5L * gb, 0L, config, cycleRange, lastDay, zone)
        assertEquals(30, insights.daysElapsed)
        assertEquals(0, insights.daysRemaining)
        assertNull(insights.recommendedDailyBytes)
    }

    @Test
    fun `projection is average times cycle length and flags over-pace`() {
        // 10 GiB in 10 days → exactly 1 GiB/day → 30 GiB projected; not over 30 GiB
        val ok = useCase(10L * gb, 0L, config, cycleRange, now, zone)
        assertEquals(30L * gb, ok.projectedCycleBytes)
        assertFalse(ok.projectedExceedsLimit!!)

        // 11 GiB in 10 days → 1181116006 B/day → 35433480180 B projected > 30 GiB
        val over = useCase(11L * gb, 0L, config, cycleRange, now, zone)
        assertEquals(35433480180L, over.projectedCycleBytes)
        assertTrue(over.projectedExceedsLimit!!)
    }

    @Test
    fun `unlimited plans have no allowance math but keep usage analytics`() {
        val unlimited = LimitConfig(isUnlimited = true)
        val insights = useCase(9L * gb, 1L * gb, unlimited, cycleRange, now, zone)
        assertEquals(10, insights.daysElapsed)
        assertEquals((9L * gb + 5) / 10, insights.averageDailyBytes)
        assertNull(insights.recommendedDailyBytes)
        assertNull(insights.projectedCycleBytes)
        assertNull(insights.projectedExceedsLimit)
    }

    @Test
    fun `date before cycle start is clamped to day one`() {
        val before = TimeUtils.startOfDay(LocalDate.of(2025, 9, 1), zone)
        val insights = useCase(100L, 100L, config, cycleRange, before, zone)
        assertEquals(1, insights.daysElapsed)
        assertEquals(29, insights.daysRemaining)
    }

    @Test
    fun `date after cycle end is clamped to the last day`() {
        val after = TimeUtils.startOfDay(LocalDate.of(2025, 11, 1), zone)
        val insights = useCase(100L, 100L, config, cycleRange, after, zone)
        assertEquals(30, insights.daysElapsed)
        assertEquals(0, insights.daysRemaining)
    }

    @Test
    fun `short cycles across a month boundary count real calendar days`() {
        // Cycle 2025-01-30 .. 2025-02-28 (30 days incl. both ends)
        val start = LocalDate.of(2025, 1, 30)
        val range = DateRange(
            start = TimeUtils.startOfDay(start, zone),
            end = TimeUtils.startOfDay(LocalDate.of(2025, 3, 1), zone),
        )
        val midCycle = TimeUtils.startOfDay(LocalDate.of(2025, 2, 13), zone)
        val insights = useCase(0L, 0L, config, range, midCycle, zone)
        assertEquals(30, insights.daysTotal)
        assertEquals(15, insights.daysElapsed)
        assertEquals(15, insights.daysRemaining)
    }

    @Test
    fun `zero usage yields zero average and no over-pace`() {
        val insights = useCase(0L, 0L, config, cycleRange, now, zone)
        assertEquals(0L, insights.averageDailyBytes)
        assertFalse(insights.projectedExceedsLimit!!)
    }
}
