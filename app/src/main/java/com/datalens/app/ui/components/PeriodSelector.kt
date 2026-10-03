package com.datalens.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.datalens.app.domain.model.UsagePeriod
import com.datalens.app.util.TimeUtils

/**
 * Period chips: Today · Yesterday · 7 days · 30 days · this/previous billing cycle ·
 * custom range (Material date-range picker, device-local timezone).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PeriodSelector(
    selected: UsagePeriod,
    onSelect: (UsagePeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustomPicker by remember { mutableStateOf(false) }

    val chips: List<Pair<String, UsagePeriod>> = listOf(
        "Today" to UsagePeriod.Today,
        "Yesterday" to UsagePeriod.Yesterday,
        "Last 7 days" to UsagePeriod.LastSevenDays,
        "Last 30 days" to UsagePeriod.LastThirtyDays,
        "This cycle" to UsagePeriod.CurrentCycle,
        "Previous cycle" to UsagePeriod.PreviousCycle,
    )

    if (showCustomPicker) {
        CustomRangeDialog(
            onDismiss = { showCustomPicker = false },
            onConfirm = { start, end ->
                showCustomPicker = false
                onSelect(UsagePeriod.Custom(start, end))
            },
        )
    }

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((label, period) in chips) {
            FilterChip(
                selected = selected == period,
                onClick = { onSelect(period) },
                label = { Text(label) },
            )
        }
        FilterChip(
            selected = selected is UsagePeriod.Custom,
            onClick = { showCustomPicker = true },
            label = { Text(customChipLabel(selected)) },
        )
    }
}

private fun customChipLabel(selected: UsagePeriod): String {
    return if (selected is UsagePeriod.Custom) {
        val start = TimeUtils.formatDayMonth(TimeUtils.startOfDay(selected.startDate))
        val end = TimeUtils.formatDayMonth(TimeUtils.startOfDay(selected.endDateInclusive))
        "Custom: $start – $end"
    } else {
        "Custom range"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (start: java.time.LocalDate, endInclusive: java.time.LocalDate) -> Unit,
) {
    val state = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = state.selectedStartDateMillis
                    val end = state.selectedEndDateMillis
                    if (start != null && end != null && end >= start) {
                        // Material date pickers report UTC milliseconds.
                        val startDay = java.time.Instant.ofEpochMilli(start)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        val endDay = java.time.Instant.ofEpochMilli(end)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        onConfirm(startDay, endDay)
                    }
                },
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DateRangePicker(
            state = state,
            showModeToggle = false,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
