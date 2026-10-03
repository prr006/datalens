package com.datalens.app

import com.datalens.app.domain.model.LimitConfig
import com.datalens.app.domain.model.LimitStatus
import com.datalens.app.domain.usecase.ComputeLimitStatusUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeLimitStatusTest {

    private val useCase = ComputeLimitStatusUseCase()
    private val gb = 1024L * 1024L * 1024L

    @Test
    fun `not configured when allowance is zero`() {
        val status = useCase(0L, LimitConfig(monthlyAllowanceBytes = 0))
        assertTrue(status is LimitStatus.NotConfigured)
    }

    @Test
    fun `computes remaining and percentage`() {
        val allowance = 20L * gb
        val used = 8_722_864_128L // ~8.12 GiB
        val status = useCase(used, LimitConfig(monthlyAllowanceBytes = allowance)) as LimitStatus.Active
        assertEquals(allowance - used, status.remainingBytes)
        assertEquals(43.6, status.percentUsed, 0.1)
        assertEquals(false, status.exceeded)
    }

    @Test
    fun `zero usage reports zero percent`() {
        val status = useCase(0L, LimitConfig(monthlyAllowanceBytes = 10 * gb)) as LimitStatus.Active
        assertEquals(0.0, status.percentUsed, 0.0)
        assertEquals(10 * gb, status.remainingBytes)
    }

    @Test
    fun `exceeded when usage reaches allowance`() {
        val status = useCase(10 * gb, LimitConfig(monthlyAllowanceBytes = 10 * gb)) as LimitStatus.Active
        assertTrue(status.exceeded)
        assertEquals(100.0, status.percentUsed, 0.01)
    }

    @Test
    fun `over the limit reports negative remaining`() {
        val status = useCase(11 * gb, LimitConfig(monthlyAllowanceBytes = 10 * gb)) as LimitStatus.Active
        assertTrue(status.exceeded)
        assertEquals(-1 * gb, status.remainingBytes)
        assertEquals(110.0, status.percentUsed, 0.01)
    }
}
