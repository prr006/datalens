package com.datalens.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.datalens.app.domain.model.Granularity
import com.datalens.app.domain.model.UsagePoint
import com.datalens.app.util.ByteFormatter
import com.datalens.app.util.TimeUtils
import kotlin.math.roundToInt

enum class ChartMode(val label: String) {
    TOTAL("Total"),
    DOWNLOAD("Download"),
    UPLOAD("Upload"),
}

/**
 * Bar chart of real NetworkStats data. Every bar is an exact per-bucket summary
 * query — nothing is interpolated or fabricated. If Android reported no usage,
 * the card says so instead of drawing made-up values.
 */
@Composable
fun UsageChartCard(
    title: String,
    points: List<UsagePoint>,
    granularity: Granularity,
    modifier: Modifier = Modifier,
) {
    var mode by rememberSaveable { mutableStateOf(ChartMode.TOTAL) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (granularity == Granularity.HOURLY) "hourly" else "daily",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ChartMode.entries.forEachIndexed { index, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = {
                            mode = m
                            selectedIndex = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ChartMode.entries.size,
                        ),
                        label = { Text(m.label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            val values = remember(points, mode) {
                points.map {
                    when (mode) {
                        ChartMode.TOTAL -> it.totalBytes
                        ChartMode.DOWNLOAD -> it.receivedBytes
                        ChartMode.UPLOAD -> it.transmittedBytes
                    }
                }
            }
            val maxValue = values.maxOrNull() ?: 0L

            if (points.isEmpty() || maxValue <= 0L) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No usage recorded for this period.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                val selected = selectedIndex?.let { points.getOrNull(it) }
                Text(
                    text = if (selected != null) {
                        "${bucketLabel(selected, granularity)} · " +
                            "↓ ${ByteFormatter.format(selected.receivedBytes)} " +
                            "↑ ${ByteFormatter.format(selected.transmittedBytes)}"
                    } else {
                        "Tap a bar for details · peak ${ByteFormatter.format(maxValue)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                ChartCanvas(
                    points = points,
                    values = values,
                    maxValue = maxValue,
                    selectedIndex = selectedIndex,
                    onSelect = { selectedIndex = it },
                )
                Spacer(Modifier.height(6.dp))
                ChartLabels(points = points, granularity = granularity)
            }
        }
    }
}

@Composable
private fun ChartCanvas(
    points: List<UsagePoint>,
    values: List<Long>,
    maxValue: Long,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val appear = remember(points, values) { Animatable(0f) }
    LaunchedEffect(points, values) {
        appear.snapTo(0f)
        appear.animateTo(1f, tween(durationMillis = 420))
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(points.size, selectedIndex) {
                detectTapGestures { offset ->
                    if (points.isEmpty()) return@detectTapGestures
                    val step = size.width.toFloat() / points.size
                    val index = (offset.x / step).toInt().coerceIn(0, points.size - 1)
                    onSelect(if (selectedIndex == index) null else index)
                }
            },
    ) {
        val chartHeight = size.height
        val step = size.width / points.size
        val barWidth = (step * 0.62f).coerceAtLeast(2f)

        // Mid-height gridline.
        drawLine(
            color = gridColor,
            start = Offset(0f, chartHeight / 2f),
            end = Offset(size.width, chartHeight / 2f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f)),
        )

        values.forEachIndexed { index, value ->
            if (value > 0L) {
                val h = (value.toFloat() / maxValue) * (chartHeight - 2.dp.toPx()) * appear.value
                val x = index * step + (step - barWidth) / 2f
                drawRoundRect(
                    color = if (index == selectedIndex) selectedColor else barColor,
                    topLeft = Offset(x, chartHeight - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }

        // Baseline.
        drawLine(
            color = gridColor,
            start = Offset(0f, chartHeight - 0.5f),
            end = Offset(size.width, chartHeight - 0.5f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

@Composable
private fun ChartLabels(points: List<UsagePoint>, granularity: Granularity) {
    val indices = remember(points.size, granularity) { labelIndices(points.size, granularity) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (i in indices) {
            val point = points.getOrNull(i) ?: continue
            Text(
                text = if (granularity == Granularity.HOURLY) {
                    TimeUtils.formatHour(point.start)
                } else {
                    TimeUtils.formatDayMonth(point.start)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun labelIndices(count: Int, granularity: Granularity): List<Int> {
    if (count <= 0) return emptyList()
    return when (granularity) {
        Granularity.HOURLY -> {
            val targets = listOf(0, 6, 12, 18)
            val clamped = targets.map { it.coerceAtMost(count - 1) }.distinct()
            clamped
        }
        Granularity.DAILY -> {
            if (count <= 7) {
                (0 until count).toList()
            } else {
                (0..6).map { step ->
                    (step.toDouble() * (count - 1) / 6.0).roundToInt()
                }.distinct()
            }
        }
    }
}

private fun bucketLabel(point: UsagePoint, granularity: Granularity): String {
    return if (granularity == Granularity.HOURLY) {
        "${TimeUtils.formatHour(point.start)}–${TimeUtils.formatHour(point.end)}"
    } else {
        TimeUtils.formatDayMonth(point.start)
    }
}
