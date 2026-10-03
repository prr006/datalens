package com.datalens.app.ui.apps

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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PullToRefreshBox
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datalens.app.ServiceLocator
import com.datalens.app.domain.model.AppCategory
import com.datalens.app.domain.model.AppListSort
import com.datalens.app.domain.model.AppTypeFilter
import com.datalens.app.domain.model.UsagePeriod
import com.datalens.app.ui.components.AppUsageRow
import com.datalens.app.ui.components.DisclaimerCard
import com.datalens.app.ui.components.EmptyState
import com.datalens.app.ui.components.ErrorState
import com.datalens.app.ui.components.LoadingState
import com.datalens.app.ui.components.PeriodSelector
import com.datalens.app.ui.components.PermissionRequiredCard
import com.datalens.app.util.RelativeTimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    onAppClick: (uid: Int, packageName: String, period: UsagePeriod) -> Unit,
    viewModel: AppsViewModel = viewModel(
        initializer = {
            AppsViewModel(
                appContext = ServiceLocator.appContext,
                usageRepository = ServiceLocator.usageRepository,
                settingsRepository = ServiceLocator.settingsRepository,
            )
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                        Text("Apps", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = state.lastUpdated?.let { "Mobile data · updated ${RelativeTimeFormat.format(it)}" }
                                ?: "Mobile data usage per app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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

                state.isLoading && state.apps.isEmpty() -> LoadingState(modifier = Modifier.fillMaxSize())

                state.error != null && state.apps.isEmpty() -> ErrorState(
                    message = state.error ?: "",
                    detail = state.errorDetail,
                    onRetry = { viewModel.refresh(force = true) },
                    modifier = Modifier.fillMaxSize(),
                )

                else -> AppsContent(
                    state = state,
                    onAppClick = onAppClick,
                    onSelectPeriod = viewModel::selectPeriod,
                    onQueryChange = viewModel::setQuery,
                    onSortChange = viewModel::setSort,
                    onToggleDirection = viewModel::toggleSortDirection,
                    onTypeFilterChange = viewModel::setTypeFilter,
                    onToggleZeroUsage = viewModel::toggleShowZeroUsage,
                    onCategoryChange = viewModel::setCategory,
                )
            }
        }
    }
}

@Composable
private fun AppsContent(
    state: AppsUiState,
    onAppClick: (uid: Int, packageName: String, period: UsagePeriod) -> Unit,
    onSelectPeriod: (UsagePeriod) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (AppListSort) -> Unit,
    onToggleDirection: () -> Unit,
    onTypeFilterChange: (AppTypeFilter) -> Unit,
    onToggleZeroUsage: () -> Unit,
    onCategoryChange: (AppCategory?) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item(key = "period") {
            PeriodSelector(selected = state.period, onSelect = onSelectPeriod)
        }

        item(key = "search") {
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.filter.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
            )
        }

        item(key = "controls") {
            SortAndFilterControls(
                filter = state.filter,
                onSortChange = onSortChange,
                onToggleDirection = onToggleDirection,
                onTypeFilterChange = onTypeFilterChange,
                onToggleZeroUsage = onToggleZeroUsage,
                onCategoryChange = onCategoryChange,
            )
        }

        val visible = state.visibleApps
        if (visible.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = if (state.apps.isEmpty()) {
                        "No mobile data usage found for this period."
                    } else {
                        "No apps match the current search or filters."
                    },
                    message = "Try a different period, search term or filter.",
                )
            }
        } else {
            items(count = visible.size, key = { "app-$it" }) { index ->
                val app = visible[index]
                AppUsageRow(
                    app = app,
                    periodTotalBytes = state.periodTotalBytes,
                    onClick = { onAppClick(app.uid, app.packageName, state.period) },
                )
            }
            item(key = "count") {
                Text(
                    text = "${visible.size} of ${state.apps.size} apps shown · " +
                        "sorted by ${state.filter.sort.label.lowercase()} " +
                        "(${if (state.filter.sortAscending) "ascending" else "descending"})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        item(key = "disclaimer") { DisclaimerCard() }
        item(key = "footer") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SortAndFilterControls(
    filter: com.datalens.app.domain.model.AppsFilterState,
    onSortChange: (AppListSort) -> Unit,
    onToggleDirection: () -> Unit,
    onTypeFilterChange: (AppTypeFilter) -> Unit,
    onToggleZeroUsage: () -> Unit,
    onCategoryChange: (AppCategory?) -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = { sortMenuOpen = true }) {
                Text("Sort: ${filter.sort.label}")
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                AppListSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.label) },
                        onClick = {
                            sortMenuOpen = false
                            onSortChange(sort)
                        },
                    )
                }
            }
            IconButton(onClick = onToggleDirection) {
                if (filter.sortAscending) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Sort ascending")
                } else {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Sort descending")
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { categoryMenuOpen = true }) {
                Text(filter.category?.displayName ?: "Category")
            }
            DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("All categories") },
                    onClick = {
                        categoryMenuOpen = false
                        onCategoryChange(null)
                    },
                )
                AppCategory.entries.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.displayName) },
                        onClick = {
                            categoryMenuOpen = false
                            onCategoryChange(category)
                        },
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            AppTypeFilter.entries.forEach { type ->
                FilterChip(
                    selected = filter.typeFilter == type,
                    onClick = { onTypeFilterChange(type) },
                    label = { Text(type.label) },
                )
            }
            FilterChip(
                selected = filter.showZeroUsage,
                onClick = onToggleZeroUsage,
                label = { Text("Zero usage") },
            )
        }
    }
}
