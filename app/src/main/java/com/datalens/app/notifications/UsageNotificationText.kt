package com.datalens.app.notifications

import com.datalens.app.util.ByteFormatter

/**
 * Builds the text lines shown in the persistent usage-tracking notification.
 *
 * These are pure functions with no Android dependencies, so they are
 * unit-testable. The byte values always come from
 * `UsageRepository.totals()` — the same real `NetworkStatsManager` numbers the
 * in-app Overview screen shows. Nothing here invents, estimates or simulates
 * usage; zero usage renders as "0 B".
 */
object UsageNotificationText {

    /** e.g. "Mobile data: 1.24 GB today" */
    fun title(totalBytes: Long): String =
        "Mobile data: ${ByteFormatter.format(totalBytes)} today"

    /** e.g. "↑ 312 MB    ↓ 928 MB" (upload first, download second) */
    fun detail(uploadedBytes: Long, downloadedBytes: Long): String =
        "↑ ${ByteFormatter.format(uploadedBytes)}    ↓ ${ByteFormatter.format(downloadedBytes)}"
}
