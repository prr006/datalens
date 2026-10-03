package com.datalens.app.ui.appdetail

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datalens.app.data.apps.AppInfoDataSource
import com.datalens.app.data.repository.SettingsRepository
import com.datalens.app.data.repository.UsageRepository
import com.datalens.app.domain.model.AppCategory
import com.datalens.app.domain.model.ByteTotals
import com.datalens.app.domain.model.DateRange
import com.datalens.app.domain.model.Granularity
import com.datalens.app.domain.model.UsagePeriod
import com.datalens.app.domain.model.UsagePoint
import com.datalens.app.ui.navigation.Routes
import com.datalens.app.util.UsageAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class AppDetailUiState(
    val permissionGranted: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val uid: Int = -1,
    val packageName: String = "",
    val appName: String = "",
    val icon: Bitmap? = null,
    val isSystem: Boolean = false,
    val category: AppCategory? = null,
    val sharedPackages: List<String> = emptyList(),
    val period: UsagePeriod = UsagePeriod.Today,
    val range: DateRange? = null,
    val receivedBytes: Long = 0L,
    val transmittedBytes: Long = 0L,
    val periodTotalBytes: Long = 0L,
    val todayUsage: ByteTotals? = null,
    val cycleUsage: ByteTotals? = null,
    val series: List<UsagePoint> = emptyList(),
    val granularity: Granularity = Granularity.HOURLY,
    val isPinned: Boolean = false,
    val isHidden: Boolean = false,
    val error: String? = null,
    val errorDetail: String? = null,
    val lastUpdated: Long? = null,
) {
    val totalBytes: Long get() = receivedBytes + transmittedBytes
    val hasRealPackage: Boolean get() = !packageName.startsWith("uid:")
    val todayTotalBytes: Long get() = todayUsage?.totalBytes ?: 0L
    val cycleTotalBytes: Long get() = cycleUsage?.totalBytes ?: 0L
}

class AppDetailViewModel(
    appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
    private val appInfoDataSource: AppInfoDataSource,
) : ViewModel() {

    private val appContext = appContext.applicationContext

    private val uidArg: Int = savedStateHandle.get<Int>("uid") ?: -1
    private val packageNameArg: String = savedStateHandle.get<String>("pkg") ?: ""

    private val _uiState = MutableStateFlow(
        AppDetailUiState(uid = uidArg, packageName = packageNameArg, period = initialPeriod(savedStateHandle)),
    )
    val uiState: StateFlow<AppDetailUiState> = _uiState.asStateFlow()

    private val refreshMutex = Mutex()

    init {
        refresh()
        viewModelScope.launch {
            settingsRepository.pinnedPackages.collect { list ->
                _uiState.update { it.copy(isPinned = list.contains(packageNameArg)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.hiddenPackages.collect { set ->
                _uiState.update { it.copy(isHidden = set.contains(packageNameArg)) }
            }
        }
    }

    fun selectPeriod(period: UsagePeriod) {
        if (_uiState.value.period == period) return
        _uiState.update { it.copy(period = period, isLoading = true, series = emptyList(), error = null, errorDetail = null) }
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

                    val appUsage = usageRepository.periodUsage(range, uidFilter = uidArg).apps.firstOrNull()
                    val allTotals = usageRepository.totals(range.start, range.end)
                    val series = usageRepository.usageSeries(range, period.chartGranularity(), uidFilter = uidArg)

                    // Quick stats: today and the current cycle, regardless of the
                    // selected period. Same repository, same real numbers.
                    val todayRange = UsagePeriod.Today.resolveRange(settings.limit.billingCycleStartDay)
                    val cycleRange = UsagePeriod.CurrentCycle.resolveRange(settings.limit.billingCycleStartDay)
                    val todayUsage = try {
                        usageRepository.periodUsage(todayRange, uidFilter = uidArg).totals
                    } catch (_: Exception) {
                        null
                    }
                    val cycleUsage = try {
                        usageRepository.periodUsage(cycleRange, uidFilter = uidArg).totals
                    } catch (_: Exception) {
                        null
                    }

                    val resolution = try {
                        appInfoDataSource.resolveUid(uidArg)
                    } catch (_: Exception) {
                        null
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            appName = appUsage?.appName ?: resolution?.appName ?: packageNameArg,
                            icon = appUsage?.icon ?: resolution?.icon,
                            isSystem = appUsage?.isSystem ?: resolution?.isSystem ?: true,
                            category = appUsage?.category ?: resolution?.category,
                            sharedPackages = resolution?.sharedPackages ?: emptyList(),
                            range = range,
                            receivedBytes = appUsage?.receivedBytes ?: 0L,
                            transmittedBytes = appUsage?.transmittedBytes ?: 0L,
                            periodTotalBytes = allTotals.totalBytes,
                            todayUsage = todayUsage,
                            cycleUsage = cycleUsage,
                            series = series,
                            granularity = period.chartGranularity(),
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

    fun togglePinned() {
        val state = _uiState.value
        if (!state.hasRealPackage) return
        viewModelScope.launch {
            settingsRepository.setPinned(packageNameArg, !state.isPinned)
        }
    }

    fun setHidden(hidden: Boolean) {
        val state = _uiState.value
        if (!state.hasRealPackage) return
        viewModelScope.launch {
            settingsRepository.setHidden(packageNameArg, hidden)
        }
    }

    companion object {
        fun initialPeriod(savedStateHandle: SavedStateHandle): UsagePeriod {
            val id = savedStateHandle.get<String>("periodId") ?: UsagePeriod.Today.id
            val start = savedStateHandle.get<Long>("periodStart") ?: 0L
            val end = savedStateHandle.get<Long>("periodEnd") ?: 0L
            return Routes.periodFromArgs(id, start, end)
        }
    }
}
