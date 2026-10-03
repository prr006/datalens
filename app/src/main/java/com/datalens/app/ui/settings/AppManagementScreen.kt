package com.datalens.app.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datalens.app.ServiceLocator
import com.datalens.app.data.apps.AppInfoDataSource
import com.datalens.app.data.repository.SettingsRepository
import com.datalens.app.domain.model.AppEntry
import com.datalens.app.ui.components.AppIcon
import com.datalens.app.ui.components.EmptyState
import com.datalens.app.ui.components.LoadingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

enum class AppManagementMode { PINNED, HIDDEN }

data class AppManagementUiState(
    val entries: List<AppEntry> = emptyList(),
    val isLoading: Boolean = true,
)

class AppManagementViewModel(
    private val mode: AppManagementMode,
    private val appInfoDataSource: AppInfoDataSource,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppManagementUiState())
    val uiState: StateFlow<AppManagementUiState> = _uiState.asStateFlow()

    init {
        val packagesFlow = when (mode) {
            AppManagementMode.PINNED -> settingsRepository.pinnedPackages
            AppManagementMode.HIDDEN -> settingsRepository.hiddenPackages
        }
        combine(packagesFlow, appInfoDataSourceDirectory()) { packages, directory ->
            packages.mapNotNull { directory.byPackage[it] }
        }.onEach { entries ->
            _uiState.value = AppManagementUiState(entries = entries, isLoading = false)
        }.launchIn(viewModelScope)
    }

    /** Directory as a cold flow that refreshes when packages change. */
    private fun appInfoDataSourceDirectory() = kotlinx.coroutines.flow.flow {
        emit(appInfoDataSource.directory())
    }

    fun remove(packageName: String) {
        viewModelScope.launch {
            when (mode) {
                AppManagementMode.PINNED -> settingsRepository.setPinned(packageName, false)
                AppManagementMode.HIDDEN -> settingsRepository.setHidden(packageName, false)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagementScreen(
    mode: AppManagementMode,
    onBack: () -> Unit,
    viewModel: AppManagementViewModel = viewModel(
        key = "app-management-${mode.name}",
        initializer = {
            AppManagementViewModel(
                mode = mode,
                appInfoDataSource = ServiceLocator.appInfoDataSource,
                settingsRepository = ServiceLocator.settingsRepository,
            )
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (mode) {
                            AppManagementMode.PINNED -> "Pinned apps"
                            AppManagementMode.HIDDEN -> "Hidden apps"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            state.entries.isEmpty() -> EmptyState(
                title = when (mode) {
                    AppManagementMode.PINNED ->
                        "No pinned apps yet. Pin apps from their detail screen."
                    AppManagementMode.HIDDEN ->
                        "No hidden apps. Apps you hide will appear here and can be restored."
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(count = state.entries.size, key = { it }) { index ->
                    val entry = state.entries[index]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(bitmap = entry.icon, contentDescription = entry.label, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                entry.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { viewModel.remove(entry.packageName) }) {
                            Text(
                                when (mode) {
                                    AppManagementMode.PINNED -> "Unpin"
                                    AppManagementMode.HIDDEN -> "Restore"
                                },
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
