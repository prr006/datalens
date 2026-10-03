package com.datalens.app.domain.model

/**
 * User-configured data limits.
 * A monthlyAllowanceBytes of 0 means "not configured".
 */
data class LimitConfig(
    val monthlyAllowanceBytes: Long = 0L,
    val dailyTargetBytes: Long = 0L,
    val billingCycleStartDay: Int = 1,
    val warningThresholdPercent: Int = 75,
) {
    val isAllowanceConfigured: Boolean get() = monthlyAllowanceBytes > 0L
    val isDailyTargetConfigured: Boolean get() = dailyTargetBytes > 0L

    companion object {
        val DEFAULT = LimitConfig()
        val STANDARD_THRESHOLDS = listOf(50, 75, 90, 100)
    }
}

/** Result of comparing used bytes against the configured allowance. */
sealed interface LimitStatus {
    data object NotConfigured : LimitStatus

    data class Active(
        val usedBytes: Long,
        val allowanceBytes: Long,
        val remainingBytes: Long,
        /** 0..n — may exceed 100 when over the allowance. */
        val percentUsed: Double,
        val warningThresholdPercent: Int,
        val exceeded: Boolean,
    ) : LimitStatus
}
