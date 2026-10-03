package com.datalens.app.domain.usecase

import com.datalens.app.domain.model.LimitConfig
import com.datalens.app.domain.model.LimitStatus

/** Pure comparison of used bytes against the configured allowance. Unit-testable. */
class ComputeLimitStatusUseCase {

    operator fun invoke(usedBytes: Long, config: LimitConfig): LimitStatus {
        if (!config.isAllowanceConfigured) return LimitStatus.NotConfigured
        val allowance = config.monthlyAllowanceBytes
        val percentUsed = if (allowance > 0) usedBytes * 100.0 / allowance else 0.0
        return LimitStatus.Active(
            usedBytes = usedBytes,
            allowanceBytes = allowance,
            remainingBytes = allowance - usedBytes,
            percentUsed = percentUsed,
            warningThresholdPercent = config.warningThresholdPercent,
            exceeded = usedBytes >= allowance,
        )
    }
}
