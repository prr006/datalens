package com.datalens.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datalens.app.data.repository.SettingsRepository
import com.datalens.app.data.repository.UsageRepository
import com.datalens.app.domain.model.DateRange
import com.datalens.app.domain.model.LimitConfig
import com.datalens.app.domain.model.LimitStatus
import com.datalens.app.domain.model.NotificationSettings
import com.datalens.app.domain.model.ReportData
import com.datalens.app.domain.model.ThemeMode
import com.datalens.app.domain.model.UiSettings
import com.datalens.app.domain.model.UsagePeriod
import com.datalens.app.domain.usecase.BuildUsageReportUseCase
import com.datalens.app.domain.usecase.ComputeLimitStatusUseCase
import com.datalens.app.domain.usecase.ReportFormat
import com.datalens.app.notifications.NotificationHelper
import com.datalens.app.util.TimeUtils
import com.datalens.app.util.UsageAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val uiSettings: UiSettings = UiSettings(),
    val statsPermissionGranted: Boolean = false,
    val cycleRange: DateRange? = null,
    val cycleUsedBytes: Long = 0L,
    val todayUsedBytes: Long = 0L,
    val limitStatus: LimitStatus = LimitStatus.NotConfigured,
    val notificationsPermissionGranted: Boolean = true,
    val appVersion: String = "",
    val appVersionCode: Long = 0L,
) {
    val cycleRangeText: String?
        get() = cycleRange?.let {
            "${TimeUtils.formatDayMonth(it.start)} – ${TimeUtils.formatDayMonth(it.end - 1)} " +
                "(${TimeUtils.localDateOf(it.end - 1).year})"
        }
}

class SettingsViewModel(
    appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val usageRepository: UsageRepository,
    private val computeLimitStatus: ComputeLimitStatusUseCase,
    private val buildReport: BuildUsageReportUseCase,
) : ViewModel() {

    private val appContext = appContext.applicationContext

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.uiSettings.collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        uiSettings = settings,
                        limitStatus = if (state.cycleRange != null) {
                            computeLimitStatus(state.cycleUsedBytes, settings.limit)
                        } else {
                            LimitStatus.NotConfigured
                        },
                    )
                }
                refreshUsageNumbers()
            }
        }
        viewModelScope.launch {
            val pm = appContext.packageManager
            val versionName = try {
                pm.getPackageInfo(appContext.packageName, 0).versionName ?: "1.0"
            } catch (_: Exception) {
                "?"
            }
            val versionCode = try {
                val info = pm.getPackageInfo(appContext.packageName, 0)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
            } catch (_: Exception) {
                0L
            }
            _uiState.update { it.copy(appVersion = versionName, appVersionCode = versionCode) }
        }
    }

    private suspend fun refreshUsageNumbers() {
        if (!UsageAccess.isGranted(appContext)) {
            _uiState.update { it.copy(statsPermissionGranted = false) }
            return
        }
        try {
            val settings = settingsRepository.uiSettings.first()
            val cycleRange = TimeUtils.billingCycleRange(settings.limit.billingCycleStartDay)
            val cycleUsed = usageRepository.totals(cycleRange.start, cycleRange.end)
            val now = System.currentTimeMillis()
            val todayRange = UsagePeriod.Today.resolveRange(settings.limit.billingCycleStartDay, now)
            val todayUsed = usageRepository.totals(todayRange.start, todayRange.end)
            _uiState.update {
                it.copy(
                    statsPermissionGranted = true,
                    cycleRange = cycleRange,
                    cycleUsedBytes = cycleUsed.totalBytes,
                    todayUsedBytes = todayUsed.totalBytes,
                    limitStatus = computeLimitStatus(cycleUsed.totalBytes, settings.limit),
                )
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(statsPermissionGranted = true) }
        }
    }

    fun refreshNotificationPermission() {
        _uiState.update { it.copy(notificationsPermissionGranted = NotificationHelper.canPost(appContext)) }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setTheme(mode) }

    fun setDynamicColors(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setDynamicColors(enabled) }

    fun setNotifications(settings: NotificationSettings) =
        viewModelScope.launch { settingsRepository.setNotifications(settings) }

    fun setHideSystemApps(hide: Boolean) =
        viewModelScope.launch { settingsRepository.setHideSystemAppsByDefault(hide) }

    fun saveLimitConfig(config: LimitConfig) = viewModelScope.launch {
        settingsRepository.saveLimitConfig(config)
    }

    suspend fun buildCycleReport(format: ReportFormat): ReportData? {
        return try {
            val settings = settingsRepository.uiSettings.first()
            buildReport(UsagePeriod.CurrentCycle, settings.limit.billingCycleStartDay, format)
        } catch (_: Exception) {
            null
        }
    }
}
