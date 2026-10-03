package com.datalens.app.domain.usecase

import com.datalens.app.domain.model.LimitConfig

/** Inputs for one alert evaluation pass — all values are real measured bytes. */
data class AlertsInput(
    val todayTotalBytes: Long,
    val cycleUsedBytes: Long,
    /** ISO date of the cycle start — scopes the anti-spam key to one cycle. */
    val cycleStartIso: String,
    /** ISO date of "today" — scopes daily/per-app anti-spam keys to one day. */
    val todayIso: String,
    /** Today's top apps (package name, today bytes), sorted descending. */
    val topAppsToday: List<Pair<String, Long>>,
)

/** Which alerts should fire now, and which keys to persist after posting. */
data class AlertOutcome(
    /** Highest newly-crossed cycle threshold (50/75/90/100), or null. */
    val cycleThresholdCrossed: Int? = null,
    /** Key to persist when the cycle alert is posted. */
    val cycleThresholdKey: String? = null,
    val dailyThresholdCrossed: Boolean = false,
    val dailyThresholdKey: String? = null,
    /** Newly crossed per-app thresholds, capped at [MAX_APP_ALERTS] apps. */
    val appsCrossedThreshold: List<Pair<String, Long>> = emptyList(),
    val appThresholdKeys: List<String> = emptyList(),
)

/**
 * Pure, unit-testable alert policy.
 *
 * Anti-spam / re-arm rules:
 *  - Cycle thresholds: the key is "cycle:{cycleStart}:{level}". Usage within a
 *    cycle is monotonic, so each level fires at most once per cycle; a new cycle
 *    changes the key and everything re-arms automatically.
 *  - Daily threshold: key "daily:{today}" — once per day.
 *  - Per-app threshold: key "app:{today}:{package}" — once per app per day.
 */
class EvaluateAlertsUseCase {

    operator fun invoke(
        input: AlertsInput,
        config: LimitConfig,
        storedCycleKey: String,
        storedDailyKey: String,
        storedAppKeys: Set<String>,
    ): AlertOutcome {
        var outcome = AlertOutcome()

        // --- Cycle percentage thresholds (finite plans only) ---
        if (config.isAllowanceConfigured) {
            val allowance = config.monthlyAllowanceBytes
            val percent = input.cycleUsedBytes * 100.0 / allowance
            val crossed = LimitConfig.STANDARD_THRESHOLDS.lastOrNull { percent >= it }
            if (crossed != null) {
                val key = "cycle:${input.cycleStartIso}:$crossed"
                if (key != storedCycleKey) {
                    outcome = outcome.copy(cycleThresholdCrossed = crossed, cycleThresholdKey = key)
                }
            }
        }

        // --- Daily usage threshold ---
        if (config.dailyAlertThresholdBytes > 0L &&
            input.todayTotalBytes >= config.dailyAlertThresholdBytes
        ) {
            val key = "daily:${input.todayIso}"
            if (key != storedDailyKey) {
                outcome = outcome.copy(dailyThresholdCrossed = true, dailyThresholdKey = key)
            }
        }

        // --- Per-app usage threshold (top offenders only, once per app per day) ---
        if (config.perAppAlertThresholdBytes > 0L) {
            val threshold = config.perAppAlertThresholdBytes
            val crossed = input.topAppsToday
                .filter { (_, bytes) -> bytes >= threshold }
                .filter { (pkg, _) -> !storedAppKeys.contains("app:${input.todayIso}:$pkg") }
                .take(MAX_APP_ALERTS)
            if (crossed.isNotEmpty()) {
                outcome = outcome.copy(
                    appsCrossedThreshold = crossed,
                    appThresholdKeys = crossed.map { (pkg, _) -> "app:${input.todayIso}:$pkg" },
                )
            }
        }

        return outcome
    }

    companion object {
        /** Never list more than this many apps in one alert. */
        const val MAX_APP_ALERTS = 3
    }
}
