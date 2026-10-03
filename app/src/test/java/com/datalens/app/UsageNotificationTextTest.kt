package com.datalens.app

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
}
