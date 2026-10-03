package com.datalens.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.datalens.app.domain.model.CycleInsights
import com.datalens.app.domain.model.DateRange
import com.datalens.app.domain.model.LimitStatus
import com.datalens.app.util.ByteFormatter
import com.datalens.app.util.Formatters
import com.datalens.app.util.TimeUtils

/**
 * Cycle status card: used / remaining / percentage, days elapsed and remaining,
 * real average per day, the recommended ("safe") daily allowance, and a clearly
 * labelled usage projection. Shows an honest unlimited variant when the user is
 * on an unlimited plan, and a configure prompt when nothing is set.
 */
@Composable
fun LimitCard(
    status: LimitStatus,
    cycleRange: DateRange,
    modifier: Modifier = Modifier,
    insights: CycleInsights? = null,
    onConfigure: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            when (status) {
                is LimitStatus.NotConfigured -> {
                    Text("Monthly data allowance", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Not configured. Set a monthly allowance (or mark your plan as " +
                            "unlimited) in Settings to track how much of your plan you have used.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (onConfigure != null) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onConfigure) {
                            Text("Open settings")
                        }
                    }
                }

                is LimitStatus.Unlimited -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Current cycle",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Unlimited plan",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        ByteFormatter.format(status.usedBytes),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "of mobile data · ${TimeUtils.formatDayMonth(cycleRange.start)} – " +
                            TimeUtils.formatDayMonth(cycleRange.end - 1),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    insights?.let { InsightRows(it, showPace = false) }
                }

                is LimitStatus.Active -> {
                    val warning = !status.exceeded &&
                        status.percentUsed >= status.warningThresholdPercent
                    val overPace = insights?.projectedExceedsLimit == true
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Monthly data allowance",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = when {
                                status.exceeded -> "Over limit"
                                overPace -> "Over pace"
                                warning -> "Watch usage"
                                else -> "${Formatters.percent(status.percentUsed / 100.0)} used"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                status.exceeded -> MaterialTheme.colorScheme.error
                                overPace || warning -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (status.percentUsed / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = when {
                            status.exceeded -> MaterialTheme.colorScheme.error
                            warning -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Used", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                ByteFormatter.format(status.usedBytes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(
                                if (status.remainingBytes >= 0) "Remaining" else "Over by",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                ByteFormatter.format(kotlin.math.abs(status.remainingBytes)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (status.remainingBytes < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "of ${ByteFormatter.format(status.allowanceBytes)} · " +
                            "${TimeUtils.formatDayMonth(cycleRange.start)} – " +
                            "${TimeUtils.formatDayMonth(cycleRange.end - 1)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    insights?.let { InsightRows(it, showPace = true) }
                }
            }
        }
    }
}

/** Compact rows of derived cycle analytics. Every number comes from real usage. */
@Composable
private fun InsightRows(insights: CycleInsights, showPace: Boolean) {
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Days elapsed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${insights.daysElapsed} of ${insights.daysTotal} · ${insights.daysRemaining} left",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text("Avg / day", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                ByteFormatter.format(insights.averageDailyBytes),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (insights.recommendedDailyBytes != null) {
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Safe daily use", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    ByteFormatter.format(insights.recommendedDailyBytes),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (insights.projectedCycleBytes != null) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        "Projected cycle (est.)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        ByteFormatter.format(insights.projectedCycleBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (showPace && insights.projectedExceedsLimit == true) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (showPace && insights.projectedExceedsLimit == true) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
    if (showPace && insights.projectedExceedsLimit == true) {
        Spacer(Modifier.height(4.dp))
        Text(
            "At your current pace you may exceed the allowance before the cycle ends. " +
                "Staying under the safe daily use should get you to the end.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
    if (insights.todayVsAverageRatio != null && insights.todayVsAverageRatio >= 2.0) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Today is running ${Formatters.ratio(insights.todayVsAverageRatio)} your usual daily average.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
