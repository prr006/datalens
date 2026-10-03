package com.datalens.app

import com.datalens.app.domain.model.AlertKind
import com.datalens.app.domain.model.AppCategory
import com.datalens.app.domain.model.AppUsageInfo
import com.datalens.app.domain.usecase.DetectAnomaliesUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectAnomaliesTest {

    private val useCase = DetectAnomaliesUseCase()
    private val mb = 1024L * 1024L

    private fun app(
        name: String = "App",
        pkg: String = "com.example.app",
        todayBytes: Long = 0,
    ) = AppUsageInfo(
        uid = 10123,
        packageName = pkg,
        appName = name,
        icon = null,
        isSystem = false,
        category = AppCategory.OTHER,
        receivedBytes = todayBytes,
        transmittedBytes = 0L,
    )

    @Test
    fun `flags app at 3x its recent average`() {
        val today = listOf(app(pkg = "com.youtube", todayBytes = 1200L * mb))
        val previous = listOf(app(pkg = "com.youtube", todayBytes = 7 * 387L * mb))
        val alerts = useCase(today, previous, 7)
        val high = alerts.firstOrNull { it.kind == AlertKind.HIGH_USAGE_VS_AVERAGE }
        assertNotNull(high)
        assertTrue(high!!.description.contains("previous 7 days"))
        assertTrue(high.description.contains("3.1×"))
    }

    @Test
    fun `does not flag app within normal range`() {
        val today = listOf(app(pkg = "com.youtube", todayBytes = 200L * mb))
        val previous = listOf(app(pkg = "com.youtube", todayBytes = 7 * 180L * mb))
        val alerts = useCase(today, previous, 7)
        assertNull(alerts.firstOrNull { it.kind == AlertKind.HIGH_USAGE_VS_AVERAGE })
    }

    @Test
    fun `does not compute ratios without baseline`() {
        val today = listOf(app(pkg = "com.newapp", todayBytes = 300L * mb))
        val alerts = useCase(today, emptyList(), 7)
        val newUsage = alerts.firstOrNull { it.kind == AlertKind.NEW_SIGNIFICANT_USAGE }
        assertNotNull(newUsage)
        // No HIGH_USAGE_VS_AVERAGE alert should exist (no misleading ratio).
        assertNull(alerts.firstOrNull { it.kind == AlertKind.HIGH_USAGE_VS_AVERAGE })
        assertTrue(newUsage!!.description.contains("No mobile data was recorded"))
    }

    @Test
    fun `small new usage is not flagged`() {
        val today = listOf(app(pkg = "com.tiny", todayBytes = 30L * mb))
        val alerts = useCase(today, emptyList(), 7)
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `flags dominant share of today's traffic`() {
        val today = listOf(
            app(pkg = "com.instagram", todayBytes = 380L * mb),
            app(pkg = "com.chrome", todayBytes = 100L * mb),
            app(pkg = "com.whatsapp", todayBytes = 50L * mb),
        )
        val alerts = useCase(today, emptyList(), 7)
        val dominant = alerts.firstOrNull { it.kind == AlertKind.DOMINANT_SHARE }
        assertNotNull(dominant)
        assertEquals("com.instagram", dominant!!.packageName)
        assertTrue(dominant.description.contains("of today's mobile data"))
    }

    @Test
    fun `no alerts when nothing happened`() {
        assertTrue(useCase(emptyList(), emptyList(), 7).isEmpty())
    }

    @Test
    fun `apps below the minimum threshold are ignored`() {
        val today = listOf(app(pkg = "com.small", todayBytes = 15L * mb))
        val previous = listOf(app(pkg = "com.small", todayBytes = 0))
        assertTrue(useCase(today, previous, 7).isEmpty())
    }
}
