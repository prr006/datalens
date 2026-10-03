package com.datalens.app.ui.alerts

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datalens.app.ServiceLocator
import com.datalens.app.domain.model.AlertKind
import com.datalens.app.domain.model.UsageAlert
import com.datalens.app.ui.components.AppIcon
import com.datalens.app.ui.components.EmptyState
import com.datalens.app.ui.components.ErrorState
import com.datalens.app.ui.components.LimitCard
import com.datalens.app.ui.components.LoadingState
import com.datalens.app.ui.components.PermissionRequiredCard
import com.datalens.app.ui.components.SectionHeader
import com.datalens.app.util.RelativeTimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    onOpenSettings: () -> Unit,
    viewModel: AlertsViewModel = viewModel(
        initializer = {
            AlertsViewModel(
                appContext = ServiceLocator.appContext,
                usageRepository = ServiceLocator.usageRepository,
                settingsRepository = ServiceLocator.settingsRepository,
                detectAnomalies = ServiceLocator.detectAnomalies,
                computeLimitStatus = ServiceLocator.computeLimitStatus,
            )
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResumed()
                viewModel.refreshNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Alerts", style = MaterialTheme.typography.titleLarge)
                        state.lastUpdated?.let {
                            Text(
                                "Updated ${RelativeTimeFormat.format(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh(force = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(force = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                !state.permissionGranted -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        PermissionRequiredCard()
                    }
                }

                state.isLoading -> LoadingState(modifier = Modifier.fillMaxSize())

                state.error != null -> ErrorState(
                    message = state.error ?: "",
                    detail = state.errorDetail,
                    onRetry = { viewModel.refresh(force = true) },
                    modifier = Modifier.fillMaxSize(),
                )

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                ) {
                    val cycleRange = state.cycleRange
                    if (cycleRange != null) {
                        item(key = "limit") {
                            LimitCard(
                                status = state.limitStatus,
                                cycleRange = cycleRange,
                                onConfigure = onOpenSettings,
                            )
                        }
                    }

                    item(key = "alerts-header") { SectionHeader(title = "High usage") }

                    if (state.alerts.isEmpty()) {
                        item(key = "alerts-empty") {
                            EmptyState(
                                title = "No unusual usage detected today.",
                                message = "Alerts appear when an app uses far more than its recent " +
                                    "average, or dominates today's traffic.",
                            )
                        }
                    } else {
                        items(count = state.alerts.size, key = { "alert-$it" }) { index ->
                            AlertCard(state.alerts[index])
                        }
                    }

                    item(key = "notifications") {
                        NotificationsCard(
                            state = state,
                            onToggle = viewModel::setNotifications,
                        )
                    }

                    item(key = "explanation") { HowAlertsWorkCard() }
                    item(key = "footer") { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: UsageAlert) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(bitmap = alert.icon, contentDescription = alert.appName, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(alert.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    alert.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (alert.kind) {
                        AlertKind.HIGH_USAGE_VS_AVERAGE -> "Compared with the previous 7 days"
                        AlertKind.NEW_SIGNIFICANT_USAGE -> "No recent baseline"
                        AlertKind.DOMINANT_SHARE -> "Share of today's total traffic"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun NotificationsCard(
    state: AlertsUiState,
    onToggle: (com.datalens.app.domain.model.NotificationSettings) -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Notifications", style = MaterialTheme.typography.titleMedium)

            if (!state.notificationsPermissionGranted) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android is currently blocking DataLens notifications.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                    }
                }) {
                    Text("Open notification settings")
                }
            }

            ToggleRow(
                label = "Enable notifications",
                checked = state.notifications.enabled,
                onChecked = { checked -> onToggle(state.notifications.copy(enabled = checked)) },
            )
            ToggleRow(
                label = "Daily summary",
                checked = state.notifications.dailySummary,
                enabled = state.notifications.enabled,
                onChecked = { checked -> onToggle(state.notifications.copy(dailySummary = checked)) },
            )
            ToggleRow(
                label = "Data limit warnings",
                checked = state.notifications.limitWarnings,
                enabled = state.notifications.enabled,
                onChecked = { checked -> onToggle(state.notifications.copy(limitWarnings = checked)) },
            )
            ToggleRow(
                label = "High-usage alerts",
                checked = state.notifications.highUsageAlerts,
                enabled = state.notifications.enabled,
                onChecked = { checked -> onToggle(state.notifications.copy(highUsageAlerts = checked)) },
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = checked, onCheckedChange = if (enabled) onChecked else null)
    }
}

@Composable
private fun HowAlertsWorkCard() {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "How these alerts are calculated",
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
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "DataLens compares each app's mobile-data usage today with its own " +
                            "average daily usage over the previous 7 days (measured with the same " +
                            "Android statistics). An alert appears when today's usage is at least " +
                            "double that average and at least 20 MB higher. Apps using 35% or more " +
                            "of today's traffic (minimum 50 MB) are flagged as dominant.\n\n" +
                            "This is a simple local comparison — not machine learning, not a " +
                            "prediction, and not a claim about what will happen later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
