package com.datalens.app.ui.overview

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datalens.app.data.repository.SettingsRepository
import com.datalens.app.data.repository.UsageRepository
import com.datalens.app.domain.model.AppUsageInfo
import com.datalens.app.domain.model.LimitStatus
import com.datalens.app.domain.model.OverviewData
import com.datalens.app.domain.model.ReportData
import com.datalens.app.domain.model.UsagePeriod
import com.datalens.app.domain.usecase.BuildUsageReportUseCase
import com.datalens.app.domain.usecase.ComputeLimitStatusUseCase
import com.datalens.app.domain.usecase.GetOverviewDataUseCase
import com.datalens.app.domain.usecase.ReportFormat
import com.datalens.app.util.UsageAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class OverviewUiState(
    val permissionGranted: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val period: UsagePeriod = UsagePeriod.Today,
    val data: OverviewData? = null,
    val allApps: List<AppUsageInfo> = emptyList(),
    val pinnedPackages: List<String> = emptyList(),
    val hiddenPackages: Set<String> = emptySet(),
    val hideSystemApps: Boolean = false,
    val limitStatus: LimitStatus = LimitStatus.NotConfigured,
    val error: String? = null,
    val errorDetail: String? = null,
    val lastUpdated: Long? = null,
) {
    val periodApps: List<AppUsageInfo> get() = data?.usage?.apps ?: emptyList()
}

class OverviewViewModel(
    appContext: Context,
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
    private val getOverviewData: GetOverviewDataUseCase,
    private val computeLimitStatus: ComputeLimitStatusUseCase,
    private val buildReport: BuildUsageReportUseCase,
) : ViewModel() {

    private val appContext = appContext.applicationContext

    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    private val refreshMutex = Mutex()

    init {
        refresh()

        viewModelScope.launch {
            settingsRepository.pinnedPackages.collect { list ->
                _uiState.update { it.copy(pinnedPackages = list) }
            }
        }
        viewModelScope.launch {
            settingsRepository.hiddenPackages.collect { set ->
                _uiState.update { it.copy(hiddenPackages = set) }
            }
        }
        viewModelScope.launch {
            var lastCycleDay: Int? = null
            settingsRepository.uiSettings.collect { settings ->
                val cycleDayChanged =
                    lastCycleDay != null && lastCycleDay != settings.limit.billingCycleStartDay
                lastCycleDay = settings.limit.billingCycleStartDay
                val data = _uiState.value.data
                val status = if (data != null) {
                    computeLimitStatus(data.currentCycleSummary.bytes, settings.limit)
                } else {
                    LimitStatus.NotConfigured
                }
                _uiState.update {
                    it.copy(limitStatus = status, hideSystemApps = settings.hideSystemAppsByDefault)
                }
                if (cycleDayChanged) refresh()
            }
        }
    }

    fun selectPeriod(period: UsagePeriod) {
        if (_uiState.value.period == period) return
        _uiState.update { it.copy(period = period, data = null, isLoading = true, error = null, errorDetail = null) }
        refresh()
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            if (!refreshMutex.tryLock()) return@launch
            try {
                if (!UsageAccess.isGranted(appContext)) {
                    _uiState.update {
                        it.copy(permissionGranted = false, isLoading = false, isRefreshing = false)
                    }
                    return@launch
                }
                _uiState.update { it.copy(permissionGranted = true, isRefreshing = true) }
                if (force) usageRepository.invalidateCaches()
                try {
                    val settings = settingsRepository.uiSettings.first()
                    val period = _uiState.value.period
                    val data = getOverviewData(period, settings.limit.billingCycleStartDay)
                    val allApps = usageRepository.fullAppList(data.range)
                    val status = computeLimitStatus(data.currentCycleSummary.bytes, settings.limit)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            data = data,
                            allApps = allApps,
                            limitStatus = status,
                            lastUpdated = System.currentTimeMillis(),
                            error = null,
                            errorDetail = null,
                        )
                    }
                } catch (e: SecurityException) {
                    // Usage Access was revoked mid-session — show the permission card.
                    _uiState.update {
                        it.copy(permissionGranted = false, isLoading = false, isRefreshing = false)
                    }
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

    /** Refresh when returning to the app/screen if data is stale (> 60 s). */
    fun onResumed() {
        val state = _uiState.value
        if (!state.permissionGranted) {
            refresh()
            return
        }
        val stale = state.lastUpdated == null ||
            System.currentTimeMillis() - state.lastUpdated >= STALE_MS
        if (stale || state.data == null) refresh()
    }

    suspend fun buildReport(format: ReportFormat): ReportData? {
        return try {
            val settings = settingsRepository.uiSettings.first()
            val period = _uiState.value.period
            buildReport(period, settings.limit.billingCycleStartDay, format)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val STALE_MS = 60L * 1000L
    }
}
