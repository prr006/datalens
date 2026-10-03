package com.datalens.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.datalens.app.domain.model.ReportData
import com.datalens.app.domain.usecase.ReportFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/**
 * Local export (Storage Access Framework) and sharing (standard Android share
 * sheet) of usage reports. Reports never leave the device unless the user
 * explicitly exports or shares them.
 */
class ReportActions internal constructor(
    private val appContext: Context,
    private val showMessage: (String) -> Unit,
) {
    internal var pending: ReportData? = null
        private set

    internal var csvLauncher: ActivityResultLauncher<String>? = null
    internal var jsonLauncher: ActivityResultLauncher<String>? = null

    internal fun attach(
        csv: ActivityResultLauncher<String>,
        json: ActivityResultLauncher<String>,
    ) {
        csvLauncher = csv
        jsonLauncher = json
    }

    /** Saves a report to a location the user picks (Storage Access Framework). */
    fun saveToDisk(report: ReportData) {
        pending = report
        val launcher = if (report.mimeType == ReportFormat.CSV.mimeType) csvLauncher else jsonLauncher
        if (launcher == null) {
            pending = null
            showMessage("Export is not ready yet — please try again.")
            return
        }
        launcher.launch(report.suggestedFileName)
    }

    /** Writes the report to a cache file and opens the Android share sheet. */
    fun share(report: ReportData) {
        try {
            val dir = File(appContext.cacheDir, "reports").apply { mkdirs() }
            val file = File(dir, report.suggestedFileName)
            file.writeBytes(report.bytes)
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = report.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, report.suggestedFileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            appContext.startActivity(Intent.createChooser(intent, "Share usage report"))
        } catch (_: Exception) {
            showMessage("Could not share the report.")
        }
    }

    internal fun handleSaveResult(uri: Uri?, context: Context) {
        val report = pending
        pending = null
        if (uri == null || report == null) return
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(report.bytes)
                output.flush()
            }
            showMessage("Report saved: ${report.suggestedFileName}")
        } catch (_: Exception) {
            showMessage("Could not save the report.")
        }
    }
}

/** Creates a [ReportActions] wired to SAF launchers and a snackbar. */
@Composable
fun rememberReportExporter(
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
): ReportActions {
    val context = LocalContext.current
    val currentContext by rememberUpdatedState(context)

    val actions = remember {
        ReportActions(
            appContext = context.applicationContext,
            showMessage = { message ->
                coroutineScope.launch { snackbarHostState.showSnackbar(message) }
            },
        )
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ReportFormat.CSV.mimeType),
    ) { uri -> actions.handleSaveResult(uri, currentContext) }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ReportFormat.JSON.mimeType),
    ) { uri -> actions.handleSaveResult(uri, currentContext) }

    actions.attach(csvLauncher, jsonLauncher)
    return actions
}

/** Which range an export should cover. */
sealed interface ExportSelection {
    data object CurrentCycle : ExportSelection
    data object LastThirtyDays : ExportSelection
    data class Custom(val start: LocalDate, val endInclusive: LocalDate) : ExportSelection
}

private enum class ExportFormatOption(val label: String) {
    CSV("CSV"), JSON("JSON"),
}

private enum class ExportRangeOption(val label: String) {
    CURRENT_CYCLE("Current billing cycle"),
    LAST_30_DAYS("Last 30 days"),
    CUSTOM("Custom range"),
}

/**
 * Export sheet: pick a format (CSV/JSON) and a range (current cycle, last 30
 * days or a custom date range via the Material date-range picker), then save
 * via SAF or share via the standard share sheet.
 */
@Composable
fun ExportDialog(
    share: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (format: ReportFormat, selection: ExportSelection) -> Unit,
) {
    var formatOption by remember { mutableStateOf(ExportFormatOption.CSV) }
    var rangeOption by remember { mutableStateOf(ExportRangeOption.CURRENT_CYCLE) }
    var customStartEnd by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = customStartEnd?.first?.atStartOfDay()
                ?.toInstant(java.time.ZoneOffset.UTC)?.toEpochMilli(),
            initialSelectedEndDateMillis = customStartEnd?.second?.atStartOfDay()
                ?.toInstant(java.time.ZoneOffset.UTC)?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        val end = pickerState.selectedEndDateMillis
                        if (start != null && end != null && end >= start) {
                            // Material date pickers report UTC milliseconds.
                            customStartEnd = java.time.Instant.ofEpochMilli(start)
                                .atZone(java.time.ZoneOffset.UTC).toLocalDate() to
                                java.time.Instant.ofEpochMilli(end)
                                    .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    },
                    enabled = pickerState.selectedStartDateMillis != null &&
                        pickerState.selectedEndDateMillis != null,
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DateRangePicker(
                state = pickerState,
                showModeToggle = false,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (share) "Share usage report" else "Export usage report") },
        text = {
            Column {
                Text("Format", style = MaterialTheme.typography.labelMedium)
                ExportFormatOption.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = formatOption == option,
                                onClick = { formatOption = option },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = formatOption == option, onClick = { formatOption = option })
                        Text(option.label, Modifier.padding(start = 4.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Range", style = MaterialTheme.typography.labelMedium)
                ExportRangeOption.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = rangeOption == option,
                                onClick = {
                                    rangeOption = option
                                    if (option == ExportRangeOption.CUSTOM && customStartEnd == null) {
                                        showDatePicker = true
                                    }
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = rangeOption == option, onClick = {
                            rangeOption = option
                            if (option == ExportRangeOption.CUSTOM && customStartEnd == null) {
                                showDatePicker = true
                            }
                        })
                        Text(option.label, Modifier.padding(start = 4.dp))
                    }
                }
                if (rangeOption == ExportRangeOption.CUSTOM) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(
                            customStartEnd?.let { (s, e) ->
                                "Dates: $s – $e"
                            } ?: "Pick dates",
                        )
                    }
                }
                Text(
                    "Reports contain per-app usage straight from Android's mobile-data " +
                        "statistics. They are generated on-device and never uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = rangeOption != ExportRangeOption.CUSTOM || customStartEnd != null,
                onClick = {
                    val format = when (formatOption) {
                        ExportFormatOption.CSV -> ReportFormat.CSV
                        ExportFormatOption.JSON -> ReportFormat.JSON
                    }
                    val selection = when (rangeOption) {
                        ExportRangeOption.CURRENT_CYCLE -> ExportSelection.CurrentCycle
                        ExportRangeOption.LAST_30_DAYS -> ExportSelection.LastThirtyDays
                        ExportRangeOption.CUSTOM -> customStartEnd!!
                    }
                    onConfirm(format, selection)
                },
            ) { Text(if (share) "Share" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
