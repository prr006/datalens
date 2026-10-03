package com.datalens.app

import com.datalens.app.domain.model.LimitConfig
import com.datalens.app.domain.usecase.AlertsInput
import com.datalens.app.domain.usecase.EvaluateAlertsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The alert policy must be spam-free and honest: each threshold fires at most
 * once per cycle/day, re-arms automatically, and never fires without a
 * configured threshold.
 */
class EvaluateAlertsTest {

    private val useCase = EvaluateAlertsUseCase()
    private val gb = 1024L * 1024L * 1024L
    private val mb = 1024L * 1024L

    private fun input(
        today: Long = 0L,
        cycle: Long = 0L,
        cycleStartIso: String = "2025-09-10",
        todayIso: String = "2025-09-19",
        apps: List<Pair<String, Long>> = emptyList(),
    ) = AlertsInput(today, cycle, cycleStartIso, todayIso, apps)

    @Test
    fun `no alerts without any configured threshold`() {
        val outcome = useCase(input(cycle = 10 * gb), LimitConfig(), storedCycleKey = "", storedDailyKey = "", storedAppKeys = emptySet())
        assertNull(outcome.cycleThresholdCrossed)
        assertFalse(outcome.dailyThresholdCrossed)
        assertTrue(outcome.appsCrossedThreshold.isEmpty())
    }

    @Test
    fun `unlimited plans never produce cycle threshold alerts`() {
        val config = LimitConfig(isUnlimited = true)
        val outcome = useCase(input(cycle = 500 * gb), config, "", "", emptySet())
        assertNull(outcome.cycleThresholdCrossed)
    }

    @Test
    fun `crossing 50 percent posts the 50 level once`() {
        val config = LimitConfig(monthlyAllowanceBytes = 10 * gb)
        val outcome = useCase(input(cycle = 5 * gb), config, "", "", emptySet())
        assertEquals(50, outcome.cycleThresholdCrossed)
        assertEquals("cycle:2025-09-10:50", outcome.cycleThresholdKey)
    }

    @Test
    fun `exact threshold boundary counts as crossed`() {
        val config = LimitConfig(monthlyAllowanceBytes = 10 * gb)
        // exactly 75 % — 7.5 GiB
        val outcome = useCase(input(cycle = 7 * gb + 512 * mb), config, "", "", emptySet())
        assertEquals(75, outcome.cycleThresholdCrossed)
    }

    @Test
    fun `highest crossed level wins`() {
        val config = LimitConfig(monthlyAllowanceBytes = 10 * gb)
        val outcome = useCase(input(cycle = 10 * gb), config, "", "", emptySet())
        assertEquals(100, outcome.cycleThresholdCrossed)
    }

    @Test
    fun `same level does not re-fire within a cycle`() {
        val config = LimitConfig(monthlyAllowanceBytes = 10 * gb)
        val outcome = useCase(
            input(cycle = 5 * gb),
            config,
            storedCycleKey = "cycle:2025-09-10:50",
            storedDailyKey = "",
            storedAppKeys = emptySet(),
        )
        assertNull(outcome.cycleThresholdCrossed)
    }

    @Test
    fun `higher level still fires after lower level was stored`() {
        val config = LimitConfig(monthlyAllowanceBytes = 10 * gb)
        val outcome = useCase(
            input(cycle = 8 * gb), // 80 %
            config,
            storedCycleKey = "cycle:2025-09-10:50",
            storedDailyKey = "",
            storedAppKeys = emptySet(),
        )
        assertEquals(75, outcome.cycleThresholdCrossed)
    }

    @Test
    fun `new cycle re-arms all levels`() {
        val config = LimitConfig(monthlyAllowanceBytes = 10 * gb)
        val outcome = useCase(
            AlertsInput(
                todayTotalBytes = 0,
                cycleUsedBytes = 5 * gb,
                cycleStartIso = "2025-10-10",
                todayIso = "2025-10-11",
                topAppsToday = emptyList(),
            ),
            config,
            storedCycleKey = "cycle:2025-09-10:100",
            storedDailyKey = "daily:2025-09-19",
            storedAppKeys = setOf("app:2025-09-19:com.foo"),
        )
        assertEquals(50, outcome.cycleThresholdCrossed)
        assertEquals("cycle:2025-10-10:50", outcome.cycleThresholdKey)
    }

    @Test
    fun `daily threshold fires once per day`() {
        val config = LimitConfig(dailyAlertThresholdBytes = 500 * mb)
        val first = useCase(input(today = 600 * mb), config, "", "", emptySet())
        assertTrue(first.dailyThresholdCrossed)
        assertEquals("daily:2025-09-19", first.dailyThresholdKey)

        val again = useCase(
            input(today = 700 * mb),
            config,
            storedCycleKey = "",
            storedDailyKey = "daily:2025-09-19",
            storedAppKeys = emptySet(),
        )
        assertFalse(again.dailyThresholdCrossed)

        val nextDay = useCase(
            input(today = 600 * mb, todayIso = "2025-09-20"),
            config,
            storedCycleKey = "",
            storedDailyKey = "daily:2025-09-19",
            storedAppKeys = emptySet(),
        )
        assertTrue(nextDay.dailyThresholdCrossed)
        assertEquals("daily:2025-09-20", nextDay.dailyThresholdKey)
    }

    @Test
    fun `daily threshold not reached produces nothing`() {
        val config = LimitConfig(dailyAlertThresholdBytes = 500 * mb)
        val outcome = useCase(input(today = 499 * mb), config, "", "", emptySet())
        assertFalse(outcome.dailyThresholdCrossed)
        assertNull(outcome.dailyThresholdKey)
    }

    @Test
    fun `per-app threshold lists crossing apps once per app per day`() {
        val config = LimitConfig(perAppAlertThresholdBytes = 100 * mb)
        val apps = listOf(
            "com.a" to 300 * mb,
            "com.b" to 50 * mb,
            "com.c" to 150 * mb,
        )
        val outcome = useCase(input(apps = apps), config, "", "", emptySet())
        assertEquals(listOf("com.a" to 300 * mb, "com.c" to 150 * mb), outcome.appsCrossedThreshold)
        assertEquals(
            listOf("app:2025-09-19:com.a", "app:2025-09-19:com.c"),
            outcome.appThresholdKeys,
        )

        val again = useCase(
            input(apps = apps),
            config,
            "",
            "",
            storedAppKeys = setOf("app:2025-09-19:com.a", "app:2025-09-19:com.c"),
        )
        assertTrue(again.appsCrossedThreshold.isEmpty())
    }

    @Test
    fun `per-app alerts are capped at three apps`() {
        val config = LimitConfig(perAppAlertThresholdBytes = 100 * mb)
        val apps = listOf(
            "com.a" to 900 * mb,
            "com.b" to 800 * mb,
            "com.c" to 700 * mb,
            "com.d" to 600 * mb,
            "com.e" to 500 * mb,
        )
        val outcome = useCase(input(apps = apps), config, "", "", emptySet())
        assertEquals(3, outcome.appsCrossedThreshold.size)
        assertEquals(listOf("com.a", "com.b", "com.c"), outcome.appsCrossedThreshold.map { it.first })
    }

    @Test
    fun `no per-app alert when threshold is disabled`() {
        val config = LimitConfig(perAppAlertThresholdBytes = 0L)
        val outcome = useCase(input(apps = listOf("com.a" to 900 * mb)), config, "", "", emptySet())
        assertTrue(outcome.appsCrossedThreshold.isEmpty())
    }

    @Test
    fun `all three alert kinds can fire in one pass`() {
        val config = LimitConfig(
            monthlyAllowanceBytes = 10 * gb,
            dailyAlertThresholdBytes = 500 * mb,
            perAppAlertThresholdBytes = 100 * mb,
        )
        val outcome = useCase(
            input(today = 600 * mb, cycle = 8 * gb, apps = listOf("com.a" to 600 * mb)),
            config,
            "", "", emptySet(),
        )
        assertEquals(75, outcome.cycleThresholdCrossed)
        assertTrue(outcome.dailyThresholdCrossed)
        assertEquals(1, outcome.appsCrossedThreshold.size)
    }
}
