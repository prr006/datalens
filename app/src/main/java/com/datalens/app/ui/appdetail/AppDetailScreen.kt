package com.datalens.app.ui.appdetail

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datalens.app.ServiceLocator
import com.datalens.app.domain.model.UsagePeriod
import com.datalens.app.ui.components.AppIcon
import com.datalens.app.ui.components.DisclaimerCard
import com.datalens.app.ui.components.ErrorState
import com.datalens.app.ui.components.LoadingState
import com.datalens.app.ui.components.PeriodSelector
import com.datalens.app.ui.components.PermissionRequiredCard
import com.datalens.app.ui.components.StatusChip
import com.datalens.app.ui.components.UsageChartCard
import com.datalens.app.util.ByteFormatter
import com.datalens.app.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    uid: Int,
    packageName: String,
    period: UsagePeriod,
    onBack: () -> Unit,
    viewModel: AppDetailViewModel = viewModel(
        initializer = {
            AppDetailViewModel(
                appContext = ServiceLocator.appContext,
                savedStateHandle = this.createSavedStateHandle(),
                usageRepository = ServiceLocator.usageRepository,
                settingsRepository = ServiceLocator.settingsRepository,
                appInfoDataSource = ServiceLocator.appInfoDataSource,
            )
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !state.permissionGranted) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.appName.ifEmpty { "App details" }, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.hasRealPackage) {
                        IconButton(onClick = { viewModel.togglePinned() }) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = if (state.isPinned) "Unpin" else "Pin to dashboard",
                                tint = if (state.isPinned) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) { Text("⋮") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (state.isHidden) "Show in lists" else "Hide from lists") },
                                onClick = {
                                    menuOpen = false
                                    viewModel.setHidden(!state.isHidden)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Open app info in system settings") },
                                onClick = {
                                    menuOpen = false
                                    try {
                                        val intent = Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", packageName, null),
                                        )
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        // Not resolvable — ignore.
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.permissionGranted -> {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    PermissionRequiredCard()
                }
            }
            state.isLoading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            state.error != null -> ErrorState(
                message = state.error ?: "",
                detail = state.errorDetail,
                onRetry = { viewModel.refresh(force = true) },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HeaderSection(state)
                UsageSection(state)
                PeriodSelector(selected = state.period, onSelect = viewModel::selectPeriod)
                UsageChartCard(
                    title = "Usage history",
                    points = state.series,
                    granularity = state.granularity,
                )
                ActionsSection(state = state, onTogglePin = viewModel::togglePinned, onSetHidden = viewModel::setHidden)
                AttributionSection(state)
                DisclaimerCard()
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HeaderSection(state: AppDetailUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(bitmap = state.icon, contentDescription = state.appName, size = 72.dp)
            Spacer(Modifier.height(12.dp))
            Text(state.appName, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.hasRealPackage) state.packageName else "unresolvable UID",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("UID ${state.uid}")
                if (state.isSystem) StatusChip("System app")
                state.category?.let { StatusChip(it.displayName) }
                if (state.isHidden) StatusChip("Hidden")
            }
        }
    }
}

@Composable
private fun UsageSection(state: AppDetailUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Total mobile data",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                ByteFormatter.format(state.totalBytes),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = buildString {
                    append(state.period.displayName().lowercase())
                    state.range?.let {
                        append(" · ")
                        append(com.datalens.app.util.TimeUtils.formatDayMonth(it.start))
                        append(" – ")
                        append(com.datalens.app.util.TimeUtils.formatDayMonth(it.end - 1))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text(
                        "↓ Download",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        ByteFormatter.format(state.receivedBytes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "↑ Upload",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        ByteFormatter.format(state.transmittedBytes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (state.periodTotalBytes > 0) {
                    "${Formatters.percent(state.totalBytes.toDouble() / state.periodTotalBytes)} of all mobile data in this period"
                } else {
                    "No mobile data was used by any app in this period."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun ActionsSection(
    state: AppDetailUiState,
    onTogglePin: () -> Unit,
    onSetHidden: (Boolean) -> Unit,
) {
    if (!state.hasRealPackage) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onTogglePin, modifier = Modifier.weight(1f)) {
            Text(if (state.isPinned) "Unpin from dashboard" else "Pin to dashboard")
        }
        OutlinedButton(onClick = { onSetHidden(!state.isHidden) }, modifier = Modifier.weight(1f)) {
            Text(if (state.isHidden) "Show in lists" else "Hide from lists")
        }
    }
}

@Composable
private fun AttributionSection(state: AppDetailUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("How this usage is attributed", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Android attributes mobile traffic to app UIDs. DataLens maps UID " +
                    "${state.uid} to this app using PackageManager.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.sharedPackages.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "This UID is shared with: ${state.sharedPackages.joinToString(", ")}. " +
                        "Usage shown here covers the whole UID and cannot be split further.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!state.hasRealPackage) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "This entry could not be resolved to an installed app; it may be a " +
                        "system process or an app that was removed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
