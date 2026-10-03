package com.datalens.app.notifications

/**
 * Tiny in-memory snapshot of the last real usage numbers, shared with the
 * home-screen widget and the Quick Settings tile.
 *
 * It is only ever written with values obtained from the usage repository —
 * never fabricated. When no snapshot exists yet (tracking never ran), the
 * widget/tile show an honest "open DataLens" state instead of zeros.
 */
object UsageTrackerCache {

    data class Snapshot(
        val todayTotalBytes: Long,
        val todayReceivedBytes: Long,
        val todayTransmittedBytes: Long,
        val cycleUsedBytes: Long,
        /** Pre-rendered cycle line (includes allowance / unlimited wording). */
        val cycleLine: String,
        val trackingEnabled: Boolean,
        val updatedAtElapsed: Long,
    )

    @Volatile
    var snapshot: Snapshot? = null
        private set

    fun update(value: Snapshot) {
        snapshot = value
    }

    /** Keeps the last numbers but flips the tracking flag (used on stop). */
    fun setTrackingEnabled(enabled: Boolean) {
        snapshot = snapshot?.copy(trackingEnabled = enabled)
    }
}
