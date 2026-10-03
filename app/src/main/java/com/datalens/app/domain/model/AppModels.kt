package com.datalens.app.domain.model

import android.graphics.Bitmap

/** Local, heuristic app category. Derived from ApplicationInfo + package name hints. */
enum class AppCategory(val displayName: String) {
    SOCIAL("Social"),
    VIDEO("Video & Streaming"),
    MUSIC_AUDIO("Music & Audio"),
    BROWSER("Browsers"),
    MESSAGING("Messaging"),
    GAMES("Games"),
    PRODUCTIVITY("Productivity"),
    SYSTEM("System"),
    OTHER("Other");
}

/**
 * A resolved application (installed and visible to DataLens).
 * [icon] is a pre-rasterized bitmap so lists can render without per-row view work.
 */
data class AppEntry(
    val uid: Int,
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    val isSystem: Boolean,
    val category: AppCategory,
    val isLaunchable: Boolean,
)

/**
 * Per-app mobile data usage for a period. [icon] is a pre-rasterized bitmap (or null,
 * in which case the UI shows a neutral placeholder).
 *
 * UIDs that cannot be resolved to a package get a synthetic package name of the form
 * "uid:1000" — these are never pinnable/hideable and display as system/unknown.
 */
data class AppUsageInfo(
    val uid: Int,
    val packageName: String,
    val appName: String,
    val icon: Bitmap?,
    val isSystem: Boolean,
    val category: AppCategory,
    val receivedBytes: Long,
    val transmittedBytes: Long,
) {
    val totalBytes: Long get() = receivedBytes + transmittedBytes

    val hasRealPackage: Boolean get() = !packageName.startsWith(SYNTHETIC_PACKAGE_PREFIX)

    fun shareOfTotal(totalBytes: Long): Double =
        if (totalBytes > 0) this.totalBytes.toDouble() / totalBytes.toDouble() else 0.0

    companion object {
        const val SYNTHETIC_PACKAGE_PREFIX = "uid:"

        /** Display fallback for unresolvable UIDs. */
        const val UNKNOWN_LABEL = "Unknown / System process"
    }
}

/** Aggregated per-app usage for a single period. */
data class PeriodUsage(
    val range: DateRange,
    val apps: List<AppUsageInfo>,
    val totals: ByteTotals,
)
