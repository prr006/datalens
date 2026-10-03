package com.datalens.app.domain.model

/** Received (download) + transmitted (upload) byte counts for something. */
data class ByteTotals(
    val receivedBytes: Long,
    val transmittedBytes: Long,
) {
    val totalBytes: Long get() = receivedBytes + transmittedBytes

    operator fun plus(other: ByteTotals): ByteTotals =
        ByteTotals(receivedBytes + other.receivedBytes, transmittedBytes + other.transmittedBytes)

    companion object {
        val ZERO = ByteTotals(0L, 0L)
    }
}

/** Half-open time interval: [start, end). All values are epoch milliseconds. */
data class DateRange(
    val start: Long,
    val end: Long,
) {
    init {
        require(end >= start) { "Invalid range: end ($end) before start ($start)" }
    }

    val durationMs: Long get() = end - start

    /** Milliseconds of this range that lie in the past (clamped to [0, duration]). */
    fun elapsedMs(now: Long): Long = (now - start).coerceIn(0L, durationMs)

    operator fun contains(millis: Long): Boolean = millis >= start && millis < end
}

/** Aggregation granularity used for charts. */
enum class Granularity { HOURLY, DAILY }

/** One aggregated point in a usage chart. */
data class UsagePoint(
    val start: Long,
    val end: Long,
    val receivedBytes: Long,
    val transmittedBytes: Long,
) {
    val totalBytes: Long get() = receivedBytes + transmittedBytes
}
