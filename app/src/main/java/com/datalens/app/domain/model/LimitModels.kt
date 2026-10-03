package com.datalens.app.domain.model

/**
 * User-configured data limits.
 *
 *  - `isUnlimited = true` means the user is on an unlimited plan: usage is shown
 *    everywhere, but there is no allowance, no percentage and no limit warnings.
 *  - Otherwise a monthlyAllowanceBytes of 0 means "not configured".
 *
 * The two alert thresholds (0 = disabled) trigger once per day via the alerts
 * pipeline — see EvaluateAlertsUseCase.
 */
data class LimitConfig(
    val monthlyAllowanceBytes: Long = 0L,
    val dailyTargetBytes: Long = 0L,
    val billingCycleStartDay: Int = 1,
    val warningThresholdPercent: Int = 75,
    val isUnlimited: Boolean = false,
    val dailyAlertThresholdBytes: Long = 0L,
    val perAppAlertThresholdBytes: Long = 0L,
) {
    /** True when a finite monthly allowance is configured (percentage math applies). */
    val isAllowanceConfigured: Boolean get() = !isUnlimited && monthlyAllowanceBytes > 0L

    /** True when the user explicitly configured a plan (limited or unlimited). */
    val isPlanConfigured: Boolean get() = isUnlimited || monthlyAllowanceBytes > 0L

    val isDailyTargetConfigured: Boolean get() = dailyTargetBytes > 0L

    companion object {
        val DEFAULT = LimitConfig()
        val STANDARD_THRESHOLDS = listOf(50, 75, 90, 100)
    }
}

/** Result of comparing used bytes against the configured allowance. */
sealed interface LimitStatus {
    data object NotConfigured : LimitStatus

    /** Unlimited plan: usage is tracked, no allowance math applies. */
    data class Unlimited(val usedBytes: Long) : LimitStatus

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

/**
 * Cycle analytics derived from REAL recorded usage. Everything is computed from
 * actual NetworkStats numbers; [projectedCycleBytes] is explicitly an estimate
 * (elapsed-average × full cycle length) and is always labelled as such in the UI.
 */
data class CycleInsights(
    /** Full days in the cycle (inclusive), from the configured start day. */
    val daysTotal: Int,
    /** Days fully or partially elapsed, including today (>= 1). */
    val daysElapsed: Int,
    val daysRemaining: Int,
    /** cycleUsed / daysElapsed — real historical average. */
    val averageDailyBytes: Long,
    val todayBytes: Long,
    /** today / averageDaily — null before a meaningful average exists. */
    val todayVsAverageRatio: Double?,
    /** remaining / daysRemaining — the "safe daily pace". Null when N/A. */
    val recommendedDailyBytes: Long?,
    /** averageDaily × daysTotal — ESTIMATE, null when unlimited/not configured. */
    val projectedCycleBytes: Long?,
    /** Whether the projection exceeds the allowance (null when N/A). */
    val projectedExceedsLimit: Boolean?,
)
