package com.datalens.app.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datalens.app.R
import com.datalens.app.ServiceLocator
import com.datalens.app.domain.model.AppUsageInfo
import com.datalens.app.domain.model.UsagePeriod
import com.datalens.app.domain.usecase.ReportFormat
import com.datalens.app.ui.components.AppUsageRow
import com.datalens.app.ui.components.DataLensPullToRefresh
import com.datalens.app.ui.components.DisclaimerCard
import com.datalens.app.ui.components.EmptyState
import com.datalens.app.ui.components.ErrorState
import com.datalens.app.ui.components.LimitCard
import com.datalens.app.ui.components.LoadingState
import com.datalens.app.ui.components.PeriodSelector
import com.datalens.app.ui.components.PermissionRequiredCard
import com.datalens.app.ui.components.SectionHeader
import com.datalens.app.ui.components.SummaryCard
import com.datalens.app.ui.components.UsageChartCard
import com.datalens.app.ui.components.rememberReportExporter
import com.datalens.app.util.ByteFormatter
import com.datalens.app.util.RelativeTimeFormat
import com.datalens.app.util.TimeUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onAppClick: (uid: Int, packageName: String, period: UsagePeriod) -> Unit,
    onOpenAppsTab: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: OverviewViewModel = viewModel(
        initializer = {
            OverviewViewModel(
                appContext = ServiceLocator.appContext,
                usageRepository = ServiceLocator.usageRepository,
                settingsRepository = ServiceLocator.settingsRepository,
                getOverviewData = ServiceLocator.getOverviewData,
                computeLimitStatus = ServiceLocator.computeLimitStatus,
                buildReport = ServiceLocator.buildReport,
            )
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val exporter = rememberReportExporter(snackbarHostState, scope)
    val context = LocalContext.current

    // Automatic refresh when the app/screen resumes (e.g. returning from Usage Access
    // settings), throttled by staleness inside the ViewModel.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("DataLens", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Mobile data statistics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh(force = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    ExportOverflowMenu(
                        onExport = { format ->
                            scope.launch {
                                val report = viewModel.buildReport(format)
                                if (report == null) {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.export_failed_message),
                                    )
                                } else {
                                    exporter.saveToDisk(report)
                                }
                            }
                        },
                        onShare = {
                            scope.launch {
                                val report = viewModel.buildReport(ReportFormat.CSV)
                                if (report == null) {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.export_failed_message),
                                    )
                                } else {
                                    exporter.share(report)
                                }
                            }
                        },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        DataLensPullToRefresh(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(force = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                !state.permissionGranted -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        PermissionRequiredCard()
                        DisclaimerCard()
                    }
                }

                state.isLoading && state.data == null -> LoadingState(
                    modifier = Modifier.fillMaxSize(),
                )

                state.error != null && state.data == null -> ErrorState(
                    message = state.error ?: "",
                    detail = state.errorDetail,
                    onRetry = { viewModel.refresh(force = true) },
                    modifier = Modifier.fillMaxSize(),
                )

                else -> OverviewContent(
                    state = state,
                    onAppClick = onAppClick,
                    onOpenAppsTab = onOpenAppsTab,
                    onOpenSettings = onOpenSettings,
                    onSelectPeriod = viewModel::selectPeriod,
                )
            }
        }
    }
}

@Composable
private fun ExportOverflowMenu(
    onExport: (ReportFormat) -> Unit,
    onShare: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    IconButton(onClick = { menuOpen = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Export & more")
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            text = { Text("Export as CSV") },
            leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
            onClick = { menuOpen = false; onExport(ReportFormat.CSV) },
        )
        DropdownMenuItem(
            text = { Text("Export as JSON") },
            leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
            onClick = { menuOpen = false; onExport(ReportFormat.JSON) },
        )
        DropdownMenuItem(
            text = { Text("Share usage report") },
            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
            onClick = { menuOpen = false; onShare() },
        )
    }
}

@Composable
private fun OverviewContent(
    state: OverviewUiState,
    onAppClick: (uid: Int, packageName: String, period: UsagePeriod) -> Unit,
    onOpenAppsTab: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectPeriod: (UsagePeriod) -> Unit,
) {
    val data = state.data ?: return
    val period = state.period

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "period") {
            PeriodSelector(selected = period, onSelect = onSelectPeriod)
        }

        item(key = "main-card") {
            MainUsageCard(state = state)
        }

        item(key = "limit") {
            LimitCard(
                status = state.limitStatus,
                cycleRange = data.cycleRange,
                onConfigure = onOpenSettings,
            )
        }

        val hidden = state.hiddenPackages
        val pinned = state.pinnedPackages
            .mapNotNull { pkg -> state.allApps.find { it.packageName == pkg } }
            .filter { !hidden.contains(it.packageName) }
        if (pinned.isNotEmpty()) {
            item(key = "pinned-header") {
                SectionHeader(title = "Pinned Apps")
            }
            items(count = pinned.size, key = { "pinned-$it" }) { index ->
                val app = pinned[index]
                AppUsageRow(
                    app = app,
                    periodTotalBytes = data.usage.totals.totalBytes,
                    compact = true,
                    onClick = { onAppClick(app.uid, app.packageName, period) },
                )
            }
        }

        item(key = "top-header") {
            SectionHeader(
                title = "Top Consumers",
                trailing = {
                    TextButton(onClick = onOpenAppsTab) { Text("View all") }
                },
            )
        }

        val topConsumers = data.usage.apps
            .filter { !hidden.contains(it.packageName) }
            .filter { if (state.hideSystemApps) !it.isSystem else true }
            .take(5)
        if (topConsumers.isEmpty()) {
            item(key = "top-empty") {
                EmptyState(
                    title = "No mobile data usage found for this period.",
                    message = "Apps that used mobile data will appear here.",
                )
            }
        } else {
            items(count = topConsumers.size, key = { "top-$it" }) { index ->
                val app = topConsumers[index]
                AppUsageRow(
                    app = app,
                    periodTotalBytes = data.usage.totals.totalBytes,
                    onClick = { onAppClick(app.uid, app.packageName, period) },
                )
            }
        }

        item(key = "chart") {
            UsageChartCard(
                title = "Usage over time",
                points = data.series,
                granularity = data.granularity,
            )
        }

        item(key = "summary") {
            SummaryCard(
                today = data.todaySummary,
                yesterday = data.yesterdaySummary,
                lastSevenDays = data.lastSevenDaysSummary,
                currentCycle = data.currentCycleSummary,
            )
        }

        item(key = "disclaimer") {
            DisclaimerCard()
        }

        item(key = "footer-space") {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MainUsageCard(state: OverviewUiState) {
    val data = state.data ?: return
    val totals = data.usage.totals
    val period = state.period

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = period.displayName(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                state.lastUpdated?.let {
                    Text(
                        text = "Updated ${RelativeTimeFormat.format(it)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = ByteFormatter.format(totals.totalBytes),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = usedLabel(period),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text(
                        "↓ Received",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        ByteFormatter.format(totals.receivedBytes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "↑ Sent",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        ByteFormatter.format(totals.transmittedBytes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = rangeText(period, data.range),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

private fun usedLabel(period: UsagePeriod): String = when (period) {
    UsagePeriod.Today -> "Used today"
    UsagePeriod.Yesterday -> "Used yesterday"
    UsagePeriod.LastSevenDays -> "Used in the last 7 days"
    UsagePeriod.LastThirtyDays -> "Used in the last 30 days"
    UsagePeriod.CurrentCycle -> "Used this cycle"
    UsagePeriod.PreviousCycle -> "Used last cycle"
    is UsagePeriod.Custom -> "Used in this range"
}

private fun rangeText(period: UsagePeriod, range: com.datalens.app.domain.model.DateRange): String {
    return when (period) {
        UsagePeriod.Today -> "Today, ${TimeUtils.formatDayMonth(range.start)} · so far"
        UsagePeriod.Yesterday -> TimeUtils.formatDayMonth(range.start)
        is UsagePeriod.Custom ->
            "${TimeUtils.formatDayMonth(range.start)} – ${TimeUtils.formatDayMonth(range.end - 1)}"
        else ->
            "${TimeUtils.formatDayMonth(range.start)} – ${TimeUtils.formatDayMonth(range.end - 1)}" +
                " (${TimeUtils.localDateOf(range.end - 1).year})"
    }
}
