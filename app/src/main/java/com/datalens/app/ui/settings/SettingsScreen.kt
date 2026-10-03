package com.datalens.app.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datalens.app.R
import com.datalens.app.ServiceLocator
import com.datalens.app.domain.model.LimitConfig
import com.datalens.app.domain.model.ReportData
import com.datalens.app.domain.model.ThemeMode
import com.datalens.app.domain.usecase.ReportFormat
import com.datalens.app.ui.components.LimitCard
import com.datalens.app.ui.components.rememberReportExporter
import com.datalens.app.util.ByteFormatter
import com.datalens.app.util.TimeUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onManagePinned: () -> Unit,
    onManageHidden: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        initializer = {
            SettingsViewModel(
                appContext = ServiceLocator.appContext,
                settingsRepository = ServiceLocator.settingsRepository,
                usageRepository = ServiceLocator.usageRepository,
                computeLimitStatus = ServiceLocator.computeLimitStatus,
                buildReport = ServiceLocator.buildReport,
            )
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val exporter = rememberReportExporter(snackbarHostState, scope)

    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshNotificationPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshNotificationPermission() }

    fun exportReport(format: ReportFormat) {
        scope.launch {
            val report: ReportData? = viewModel.buildCycleReport(format)
            if (report == null) {
                snackbarHostState.showSnackbar(context.getString(R.string.export_failed_message))
            } else {
                exporter.saveToDisk(report)
            }
        }
    }

    fun shareReport() {
        scope.launch {
            val report = viewModel.buildCycleReport(ReportFormat.CSV)
            if (report == null) {
                snackbarHostState.showSnackbar(context.getString(R.string.export_failed_message))
            } else {
                exporter.share(report)
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            SettingsSection(title = "Appearance") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val modes = listOf(
                            "System default" to ThemeMode.SYSTEM,
                            "Light" to ThemeMode.LIGHT,
                            "Dark" to ThemeMode.DARK,
                        )
                        modes.forEachIndexed { index, (label, mode) ->
                            SegmentedButton(
                                selected = state.uiSettings.theme == mode,
                                onClick = { viewModel.setTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                label = { Text(label, maxLines = 1) },
                            )
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ToggleRow(
                            label = "Dynamic colors (Material You)",
                            checked = state.uiSettings.dynamicColors,
                            onChecked = viewModel::setDynamicColors,
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Dynamic colors require Android 12+.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SettingsSection(title = "Data") {
                val cycleRange = state.cycleRange
                if (cycleRange != null) {
                    LimitCard(
                        status = state.limitStatus,
                        cycleRange = cycleRange,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                SettingsRow(
                    title = "Monthly allowance",
                    value = if (state.uiSettings.limit.isAllowanceConfigured) {
                        ByteFormatter.format(state.uiSettings.limit.monthlyAllowanceBytes)
                    } else {
                        "Not set"
                    },
                    onClick = { dialog = SettingsDialog.Allowance },
                )
                SettingsRow(
                    title = "Billing cycle starts on",
                    value = "the ${TimeUtils.ordinal(state.uiSettings.limit.billingCycleStartDay)}",
                    onClick = { dialog = SettingsDialog.BillingDay },
                )
                SettingsRow(
                    title = "Daily target",
                    value = if (state.uiSettings.limit.isDailyTargetConfigured) {
                        ByteFormatter.format(state.uiSettings.limit.dailyTargetBytes)
                    } else {
                        "Not set"
                    },
                    supporting = if (state.uiSettings.limit.isDailyTargetConfigured) {
                        "Today: ${ByteFormatter.format(state.todayUsedBytes)}"
                    } else {
                        null
                    },
                    onClick = { dialog = SettingsDialog.DailyTarget },
                )
                SettingsRow(
                    title = "Warning threshold",
                    value = "${state.uiSettings.limit.warningThresholdPercent}%",
                    onClick = { dialog = SettingsDialog.Threshold },
                )
            }

            SettingsSection(title = "Notifications") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (!state.notificationsPermissionGranted) {
                        Text(
                            "Android is blocking notifications for DataLens.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                TextButton(onClick = {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }) { Text("Allow") }
                            }
                            TextButton(onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        },
                                    )
                                } catch (_: Exception) {
                                }
                            }) { Text("Open settings") }
                        }
                    }
                    val notif = state.uiSettings.notifications
                    ToggleRow(
                        label = "Enable notifications",
                        checked = notif.enabled,
                        onChecked = { checked ->
                            if (checked && !state.notificationsPermissionGranted &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.setNotifications(notif.copy(enabled = checked))
                        },
                    )
                    ToggleRow(
                        label = "Daily summary",
                        checked = notif.dailySummary,
                        enabled = notif.enabled,
                        onChecked = { viewModel.setNotifications(notif.copy(dailySummary = it)) },
                    )
                    ToggleRow(
                        label = "Data limit warnings",
                        checked = notif.limitWarnings,
                        enabled = notif.enabled,
                        onChecked = { viewModel.setNotifications(notif.copy(limitWarnings = it)) },
                    )
                    ToggleRow(
                        label = "High-usage alerts",
                        checked = notif.highUsageAlerts,
                        enabled = notif.enabled,
                        onChecked = { viewModel.setNotifications(notif.copy(highUsageAlerts = it)) },
                    )
                }
            }

            SettingsSection(title = "Apps") {
                SettingsRow(
                    title = "Pinned apps",
                    value = "Manage",
                    onClick = onManagePinned,
                )
                SettingsRow(
                    title = "Hidden apps",
                    value = "Manage",
                    onClick = onManageHidden,
                )
                ToggleRow(
                    label = "Hide system apps by default",
                    checked = state.uiSettings.hideSystemAppsByDefault,
                    onChecked = viewModel::setHideSystemApps,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            SettingsSection(title = "Export & share") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Export the current billing cycle (${state.cycleRangeText ?: "…"}) as a " +
                            "report. Files are saved locally — DataLens never uploads anything.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { exportReport(ReportFormat.CSV) }) {
                            Icon(Icons.Filled.DateRange, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save CSV")
                        }
                        OutlinedButton(onClick = { exportReport(ReportFormat.JSON) }) {
                            Icon(Icons.Filled.DateRange, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save JSON")
                        }
                        OutlinedButton(onClick = { shareReport() }) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }

            SettingsSection(title = "Privacy") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "DataLens works entirely on this device:\n\n" +
                            "•  Usage statistics stay on your phone\n" +
                            "•  No account, no sign-in\n" +
                            "•  No network access — the app does not even request the " +
                            "INTERNET permission\n" +
                            "•  No packet contents are inspected\n" +
                            "•  No messages, passwords or browsing contents are read\n" +
                            "•  Nothing is uploaded, synced or tracked\n\n" +
                            "Exports are only created when you ask for them, and only leave " +
                            "the device if you explicitly share them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AboutSection(state = state)

            Spacer(Modifier.height(24.dp))
        }
    }

    when (val current = dialog) {
        SettingsDialog.Allowance -> ByteQuantityDialog(
            title = "Monthly allowance",
            initialBytes = state.uiSettings.limit.monthlyAllowanceBytes,
            onDismiss = { dialog = null },
            onSave = { bytes ->
                viewModel.saveLimitConfig(state.uiSettings.limit.copy(monthlyAllowanceBytes = bytes))
                dialog = null
            },
            allowRemove = true,
        )

        SettingsDialog.DailyTarget -> ByteQuantityDialog(
            title = "Daily target",
            initialBytes = state.uiSettings.limit.dailyTargetBytes,
            onDismiss = { dialog = null },
            onSave = { bytes ->
                viewModel.saveLimitConfig(state.uiSettings.limit.copy(dailyTargetBytes = bytes))
                dialog = null
            },
            allowRemove = true,
        )

        SettingsDialog.BillingDay -> BillingDayDialog(
            current = state.uiSettings.limit.billingCycleStartDay,
            onDismiss = { dialog = null },
            onSave = { day ->
                viewModel.saveLimitConfig(state.uiSettings.limit.copy(billingCycleStartDay = day))
                dialog = null
            },
        )

        SettingsDialog.Threshold -> ThresholdDialog(
            current = state.uiSettings.limit.warningThresholdPercent,
            onDismiss = { dialog = null },
            onSave = { threshold ->
                viewModel.saveLimitConfig(state.uiSettings.limit.copy(warningThresholdPercent = threshold))
                dialog = null
            },
        )

        null -> Unit
    }
}

private enum class SettingsDialog { Allowance, DailyTarget, BillingDay, Threshold }

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    SectionLabel(title)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) { content() }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    supporting: String? = null,
    icon: ImageVector? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = checked, onCheckedChange = if (enabled) onChecked else null)
    }
}

@Composable
private fun ByteQuantityDialog(
    title: String,
    initialBytes: Long,
    allowRemove: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    val isGbInitial = initialBytes == 0L || initialBytes >= 1024L * 1024L * 1024L
    var unitBytes by remember { mutableStateOf(if (isGbInitial) 1024L * 1024L * 1024L else 1024L * 1024L) }
    var text by remember {
        mutableStateOf(
            if (initialBytes > 0) ByteFormatter.bytesToUnitString(initialBytes, if (isGbInitial) 1024L * 1024L * 1024L else 1024L * 1024L) else "",
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Amount") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = unitBytes == 1024L * 1024L,
                        onClick = { unitBytes = 1024L * 1024L },
                        label = { Text("MB") },
                    )
                    FilterChip(
                        selected = unitBytes == 1024L * 1024L * 1024L,
                        onClick = { unitBytes = 1024L * 1024L * 1024L },
                        label = { Text("GB") },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (initialBytes > 0) "Currently: ${ByteFormatter.format(initialBytes)}" else "Currently not set",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    ByteFormatter.parseQuantityToBytes(text, unitBytes)?.let(onSave)
                },
                enabled = ByteFormatter.parseQuantityToBytes(text, unitBytes) != null,
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (allowRemove) {
                    TextButton(onClick = { onSave(0L) }) { Text("Remove") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun BillingDayDialog(
    current: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var day by remember { mutableStateOf(current.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Billing cycle start day") },
        text = {
            Column {
                Text(
                    "Your cycle runs from the ${TimeUtils.ordinal(day.toInt())} of each month to " +
                        "the day before the ${TimeUtils.ordinal(day.toInt())} of the next month.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Starts on the ${TimeUtils.ordinal(day.toInt())}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = day,
                    onValueChange = { day = it },
                    valueRange = 1f..31f,
                    steps = 29,
                )
                Text(
                    "Months with fewer days use the last day of the month.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(day.toInt()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ThresholdDialog(
    current: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var selection by remember { mutableStateOf(current) }
    var customText by remember { mutableStateOf(if (current !in LimitConfig.STANDARD_THRESHOLDS) current.toString() else "") }
    val customValue = customText.toIntOrNull()?.takeIf { it in 1..200 }
    val effectiveSelection = if (customValue != null && selection == -1) customValue else selection

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Warning threshold") },
        text = {
            Column {
                Text(
                    "Warn me when this percentage of the monthly allowance is used.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                LimitConfig.STANDARD_THRESHOLDS.forEach { threshold ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selection = threshold },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selection == threshold, onClick = { selection = threshold })
                        Text("$threshold%", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selection = -1 },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selection == -1, onClick = { selection = -1 })
                    Text("Custom:", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it.filter { c -> c.isDigit() }.take(3) },
                        singleLine = true,
                        modifier = Modifier.width(96.dp),
                        label = { Text("%") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(effectiveSelection) },
                enabled = selection in LimitConfig.STANDARD_THRESHOLDS || (selection == -1 && customValue != null),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AboutSection(state: SettingsUiState) {
    var expanded by remember { mutableStateOf(false) }
    SettingsSection(title = "About") {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("DataLens ${state.appVersion} (${state.appVersionCode})",
                    style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "A local-only mobile-data usage dashboard built with Kotlin, Jetpack " +
                    "Compose and Material 3. Data comes from Android's NetworkStatsManager.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Known Android limitations",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = "•  Statistics update when Android records them — there may be a " +
                        "short delay; DataLens is not packet-level real-time monitoring.\n" +
                        "•  Usage may differ slightly from your carrier's billing.\n" +
                        "•  Usage is aggregated across all mobile subscriptions (SIMs); Android " +
                        "does not expose reliable per-SIM usage to third-party apps.\n" +
                        "•  Apps hidden from package visibility show as \"Unknown / System " +
                        "process\"; DataLens deliberately avoids QUERY_ALL_PACKAGES.\n" +
                        "•  Some devices restrict statistics queries when Usage Access is " +
                        "granted but the device blocks subscriber queries.\n" +
                        "•  Shared UIDs (rare, mostly system components) report combined usage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Open-source components: AndroidX, Jetpack Compose, Room, WorkManager, " +
                    "Navigation (Apache License 2.0).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
