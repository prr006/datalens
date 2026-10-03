package com.datalens.app.ui.apps

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datalens.app.data.repository.SettingsRepository
import com.datalens.app.data.repository.UsageRepository
import com.datalens.app.domain.model.AppCategory
import com.datalens.app.domain.model.AppListSort
import com.datalens.app.domain.model.AppTypeFilter
import com.datalens.app.domain.model.AppUsageInfo
import com.datalens.app.domain.model.AppsFilter
import com.datalens.app.domain.model.AppsFilterState
import com.datalens.app.domain.model.UsagePeriod
import com.datalens.app.util.UsageAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class AppsUiState(
    val permissionGranted: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val period: UsagePeriod = UsagePeriod.Today,
    val apps: List<AppUsageInfo> = emptyList(),
    val filter: AppsFilterState = AppsFilterState(),
    val hiddenPackages: Set<String> = emptySet(),
    val pinnedPackages: Set<String> = emptySet(),
    val hideSystemByDefault: Boolean = false,
    val error: String? = null,
    val errorDetail: String? = null,
    val lastUpdated: Long? = null,
) {
    private val effectiveFilter: AppsFilterState
        get() = if (filter.typeFilter == AppTypeFilter.ALL && hideSystemByDefault) {
            filter.copy(typeFilter = AppTypeFilter.USER)
        } else {
            filter
        }

    val visibleApps: List<AppUsageInfo>
        get() = AppsFilter.apply(apps, effectiveFilter, hiddenPackages, pinnedPackages)

    val periodTotalBytes: Long
        get() = apps.filter { !hiddenPackages.contains(it.packageName) }
            .sumOf { it.totalBytes }
}

class AppsViewModel(
    appContext: Context,
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val appContext = appContext.applicationContext

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    private val refreshMutex = Mutex()

    init {
        refresh()
        viewModelScope.launch {
            settingsRepository.hiddenPackages.collect { set ->
                _uiState.update { it.copy(hiddenPackages = set) }
            }
        }
        viewModelScope.launch {
            settingsRepository.pinnedPackages.collect { list ->
                _uiState.update { it.copy(pinnedPackages = list.toSet()) }
            }
        }
        viewModelScope.launch {
            settingsRepository.uiSettings.collect { settings ->
                _uiState.update { it.copy(hideSystemByDefault = settings.hideSystemAppsByDefault) }
            }
        }
    }

    fun selectPeriod(period: UsagePeriod) {
        if (_uiState.value.period == period) return
        _uiState.update { it.copy(period = period, apps = emptyList(), isLoading = true, error = null, errorDetail = null) }
        refresh()
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            if (!refreshMutex.tryLock()) return@launch
            try {
                if (!UsageAccess.isGranted(appContext)) {
                    _uiState.update { it.copy(permissionGranted = false, isLoading = false, isRefreshing = false) }
                    return@launch
                }
                _uiState.update { it.copy(permissionGranted = true, isRefreshing = true) }
                if (force) usageRepository.invalidateCaches()
                try {
                    val settings = settingsRepository.uiSettings.first()
                    val period = _uiState.value.period
                    val range = period.resolveRange(settings.limit.billingCycleStartDay)
                    val apps = usageRepository.fullAppList(range)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            apps = apps,
                            lastUpdated = System.currentTimeMillis(),
                            error = null,
                            errorDetail = null,
                        )
                    }
                } catch (e: SecurityException) {
                    _uiState.update { it.copy(permissionGranted = false, isLoading = false, isRefreshing = false) }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = "Couldn't read Android's network statistics.",
                            errorDetail = e.message ?: e.javaClass.simpleName,
                        )
                    }
                }
            } finally {
                refreshMutex.unlock()
            }
        }
    }

    fun onResumed() {
        val state = _uiState.value
        if (!state.permissionGranted) {
            refresh()
            return
        }
        val stale = state.lastUpdated == null ||
            System.currentTimeMillis() - state.lastUpdated >= STALE_MS
        if (stale) refresh()
    }

    fun setQuery(query: String) = _uiState.update { it.copy(filter = it.filter.copy(query = query)) }

    fun setSort(sort: AppListSort) = _uiState.update { it.copy(filter = it.filter.copy(sort = sort)) }

    fun toggleSortDirection() =
        _uiState.update { it.copy(filter = it.filter.copy(sortAscending = !it.filter.sortAscending)) }

    fun setTypeFilter(filter: AppTypeFilter) =
        _uiState.update { it.copy(filter = it.filter.copy(typeFilter = filter)) }

    fun toggleShowZeroUsage() =
        _uiState.update { it.copy(filter = it.filter.copy(showZeroUsage = !it.filter.showZeroUsage)) }

    fun setCategory(category: AppCategory?) =
        _uiState.update { it.copy(filter = it.filter.copy(category = category)) }

    companion object {
        private const val STALE_MS = 60L * 1000L
    }
}
