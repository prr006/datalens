package com.datalens.app

import com.datalens.app.domain.model.LimitConfig
import com.datalens.app.notifications.UsageNotificationText
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The persistent notification must render the SAME real numbers the app shows —
 * in particular it must never invent a value: zero usage renders as "0 B", and
 * the formatting is exercised end-to-end through ByteFormatter.
 */
class UsageNotificationTextTest {

    private val KB = 1024L
    private val MB = 1024L * 1024L
    private val GB = 1024L * 1024L * 1024L

    @Test
    fun `title shows today's total`() {
        assertEquals("Mobile data: 0 B today", UsageNotificationText.title(0))
        assertEquals("Mobile data: 1.24 GB today", UsageNotificationText.title(1_331_439_907L))
        assertEquals("Mobile data: 843 MB today", UsageNotificationText.title(843 * MB))
    }

    @Test
    fun `detail shows upload and download separately`() {
        // upload first, download second — same convention as the Overview card
        assertEquals(
            "↑ 312 MB    ↓ 928 MB",
            UsageNotificationText.detail(uploadedBytes = 312 * MB, downloadedBytes = 928 * MB),
        )
    }

    @Test
    fun `zero usage renders zero without inventing values`() {
        assertEquals(
            "↑ 0 B    ↓ 0 B",
            UsageNotificationText.detail(uploadedBytes = 0, downloadedBytes = 0),
        )
    }

    @Test
    fun `small byte values are shown exactly`() {
        // 1024 bytes formats through the KB branch as "1.0 KB"
        assertEquals(
            "↑ 512 B    ↓ 1.0 KB",
            UsageNotificationText.detail(uploadedBytes = 512, downloadedBytes = KB),
        )
    }

    @Test
    fun `cycle line shows usage allowance and percent for configured plans`() {
        val config = LimitConfig(monthlyAllowanceBytes = 20L * GB)
        // exactly 10 GiB of 20 GiB = 50 %
        assertEquals(
            "Cycle: 10.0 GB of 20.0 GB (50%)",
            UsageNotificationText.cycleLine(10L * GB, config),
        )
    }

    @Test
    fun `cycle line marks unlimited plans`() {
        assertEquals(
            "Cycle: 10.0 GB · unlimited plan",
            UsageNotificationText.cycleLine(10L * GB, LimitConfig(isUnlimited = true)),
        )
    }

    @Test
    fun `cycle line shows plain usage when no allowance is set`() {
        assertEquals(
            "Cycle: 0 B",
            UsageNotificationText.cycleLine(0L, LimitConfig()),
        )
    }

    @Test
    fun `cycle line never exceeds 100 percent visually at the limit`() {
        val config = LimitConfig(monthlyAllowanceBytes = 10L * GB)
        assertEquals(
            "Cycle: 10.0 GB of 10.0 GB (100%)",
            UsageNotificationText.cycleLine(10L * GB, config),
        )
    }
}
