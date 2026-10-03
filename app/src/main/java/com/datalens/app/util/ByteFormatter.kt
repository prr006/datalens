package com.datalens.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Formats byte counts the way a data-usage app should:
 * 500 B · 4.2 KB · 12.7 MB · 843 MB · 1.42 GB · 8.72 GB
 *
 * All values are clamped at zero; invalid/negative inputs render as "0 B".
 */
object ByteFormatter {

    /**
     * Unit system for display. Default is binary (1 KB = 1024 B, the convention
     * Android itself uses). When the user picks decimal units in Settings, the
     * app switches to SI (1 KB = 1000 B). Applied once at app start from
     * DataStore; toggling in Settings applies immediately.
     */
    @Volatile
    var useDecimalUnits: Boolean = false

    fun format(bytes: Long): String =
        if (useDecimalUnits) formatDecimal(bytes) else formatBinary(bytes)

    private fun formatBinary(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1023.95) return oneDecimal(kb) + " KB"
        val mb = bytes / (1024.0 * 1024.0)
        if (mb < 99.95) return oneDecimal(mb) + " MB"
        if (mb < 1023.95) return zeroDecimal(mb) + " MB"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        if (gb < 9.995) return twoDecimals(gb) + " GB"
        if (gb < 99.95) return oneDecimal(gb) + " GB"
        if (gb < 1023.95) return zeroDecimal(gb) + " GB"
        val tb = gb / 1024.0
        return twoDecimals(tb) + " TB"
    }

    private fun formatDecimal(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        if (bytes < 1000L) return "$bytes B"
        val kb = bytes / 1_000.0
        if (kb < 999.5) return oneDecimal(kb) + " KB"
        val mb = bytes / 1_000_000.0
        if (mb < 99.95) return oneDecimal(mb) + " MB"
        if (mb < 999.5) return zeroDecimal(mb) + " MB"
        val gb = bytes / 1_000_000_000.0
        if (gb < 9.995) return twoDecimals(gb) + " GB"
        if (gb < 99.95) return oneDecimal(gb) + " GB"
        if (gb < 999.5) return zeroDecimal(gb) + " GB"
        val tb = gb / 1_000.0
        return twoDecimals(tb) + " TB"
    }

    /** Compact form used in tight chart labels (e.g. "1.4 GB", "512 MB"). */
    fun formatCompact(bytes: Long): String =
        if (useDecimalUnits) formatCompactDecimal(bytes) else formatCompactBinary(bytes)

    private fun formatCompactBinary(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1023.95) return if (kb < 10.0) oneDecimal(kb) + " KB" else zeroDecimal(kb) + " KB"
        val mb = bytes / (1024.0 * 1024.0)
        if (mb < 1023.95) return if (mb < 10.0) oneDecimal(mb) + " MB" else zeroDecimal(mb) + " MB"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        if (gb < 1023.95) return oneDecimal(gb) + " GB"
        return zeroDecimal(gb / 1024.0) + " TB"
    }

    private fun formatCompactDecimal(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        if (bytes < 1000L) return "$bytes B"
        val kb = bytes / 1_000.0
        if (kb < 999.5) return if (kb < 10.0) oneDecimal(kb) + " KB" else zeroDecimal(kb) + " KB"
        val mb = bytes / 1_000_000.0
        if (mb < 999.5) return if (mb < 10.0) oneDecimal(mb) + " MB" else zeroDecimal(mb) + " MB"
        val gb = bytes / 1_000_000_000.0
        if (gb < 999.5) return oneDecimal(gb) + " GB"
        return zeroDecimal(gb / 1_000.0) + " TB"
    }

    /** Parses a user-entered quantity (e.g. "20" or "1.5") in the given unit into bytes. */
    fun parseQuantityToBytes(text: String, unitBytes: Long): Long? {
        val cleaned = text.trim().replace(',', '.')
        if (cleaned.isEmpty()) return null
        return try {
            val value = BigDecimal(cleaned)
            if (value.signum() < 0) null
            else value.multiply(BigDecimal(unitBytes)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    /** Renders a byte count as a plain whole number in the given unit, e.g. (2.5 GiB -> "2.5"). */
    fun bytesToUnitString(bytes: Long, unitBytes: Long): String {
        val value = BigDecimal(bytes).divide(BigDecimal(unitBytes), 3, RoundingMode.HALF_UP)
        return value.stripTrailingZeros().toPlainString()
    }

    private fun oneDecimal(v: Double): String = String.format(Locale.US, "%.1f", v)
    private fun twoDecimals(v: Double): String = String.format(Locale.US, "%.2f", v)
    private fun zeroDecimal(v: Double): String = String.format(Locale.US, "%.0f", v)
}

/** Small numeric formatting helpers for percentages and ratios. */
object Formatters {

    /** 0.436 -> "43.6%", 0.38 -> "38%", 0.081 -> "8.1%". */
    fun percent(fraction: Double): String {
        val pct = fraction * 100.0
        val rounded = kotlin.math.round(pct * 10.0) / 10.0
        return if (rounded == kotlin.math.floor(rounded)) {
            String.format(Locale.US, "%.0f%%", rounded)
        } else {
            String.format(Locale.US, "%.1f%%", rounded)
        }
    }

    /** 3.14 -> "3.1×". */
    fun ratio(r: Double): String = String.format(Locale.US, "%.1f×", r)

    /** Sign-prefixed difference, e.g. "+32%" / "-12%". */
    fun signedPercent(fraction: Double): String {
        val pct = fraction * 100.0
        val sign = if (pct >= 0) "+" else "-"
        val rounded = kotlin.math.round(kotlin.math.abs(pct) * 10.0) / 10.0
        return if (rounded == kotlin.math.floor(rounded)) {
            String.format(Locale.US, "%s%.0f%%", sign, rounded)
        } else {
            String.format(Locale.US, "%s%.1f%%", sign, rounded)
        }
    }
}
