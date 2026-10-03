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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.datalens.app.domain.model.DateRange
import com.datalens.app.domain.model.LimitStatus
import com.datalens.app.util.ByteFormatter
import com.datalens.app.util.Formatters
import com.datalens.app.util.TimeUtils

/**
 * "Monthly allowance" progress card: used / remaining / percentage, with the
 * billing-cycle date range.
 */
@Composable
fun LimitCard(
    status: LimitStatus,
    cycleRange: DateRange,
    modifier: Modifier = Modifier,
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
                        "Not configured. Set a monthly allowance in Settings to track " +
                            "how much of your plan you have used.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (onConfigure != null) {
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.TextButton(onClick = onConfigure) {
                            Text("Open settings")
                        }
                    }
                }

                is LimitStatus.Active -> {
                    val warning = !status.exceeded &&
                        status.percentUsed >= status.warningThresholdPercent
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Monthly data allowance",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (status.exceeded) {
                                "Over limit"
                            } else {
                                "${Formatters.percent(status.percentUsed / 100.0)} used"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                status.exceeded -> MaterialTheme.colorScheme.error
                                warning -> MaterialTheme.colorScheme.tertiary
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
                }
            }
        }
    }
}
