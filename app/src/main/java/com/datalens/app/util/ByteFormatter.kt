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

    fun format(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return oneDecimal(kb) + " KB"
        val mb = kb / 1024.0
        if (mb < 100.0) return oneDecimal(mb) + " MB"
        if (mb < 1024.0) return zeroDecimal(mb) + " MB"
        val gb = mb / 1024.0
        if (gb < 10.0) return twoDecimals(gb) + " GB"
        if (gb < 100.0) return oneDecimal(gb) + " GB"
        if (gb < 1024.0) return zeroDecimal(gb) + " GB"
        val tb = gb / 1024.0
        return twoDecimals(tb) + " TB"
    }

    /** Compact form used in tight chart labels (e.g. "1.4 GB", "512 MB"). */
    fun formatCompact(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val mb = bytes / (1024.0 * 1024.0)
        if (mb < 1.0) return format(bytes)
        return format(bytes)
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
        return if (kotlin.math.abs(pct) >= 10.0) {
            String.format(Locale.US, "%.0f%%", pct)
        } else {
            String.format(Locale.US, "%.1f%%", pct)
        }
    }

    /** 3.14 -> "3.1×". */
    fun ratio(r: Double): String = String.format(Locale.US, "%.1f×", r)

    /** Sign-prefixed difference, e.g. "+32%" / "-12%". */
    fun signedPercent(fraction: Double): String {
        val pct = fraction * 100.0
        val sign = if (pct >= 0) "+" else "-"
        return if (kotlin.math.abs(pct) >= 10.0) {
            String.format(Locale.US, "%s%.0f%%", sign, kotlin.math.abs(pct))
        } else {
            String.format(Locale.US, "%s%.1f%%", sign, kotlin.math.abs(pct))
        }
    }
}
