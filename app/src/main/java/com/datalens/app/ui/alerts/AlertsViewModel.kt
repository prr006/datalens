package com.datalens.app.ui.alerts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datalens.app.data.repository.SettingsRepository
import com.datalens.app.data.repository.UsageRepository
import com.datalens.app.domain.model.DateRange
import com.datalens.app.domain.model.LimitStatus
import com.datalens.app.domain.model.NotificationSettings
import com.datalens.app.domain.model.UsageAlert
import com.datalens.app.domain.model.UsagePeriod
import com.datalens.app.domain.usecase.ComputeLimitStatusUseCase
import com.datalens.app.domain.usecase.DetectAnomaliesUseCase
import com.datalens.app.notifications.NotificationHelper
import com.datalens.app.util.TimeUtils
import com.datalens.app.util.UsageAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class AlertsUiState(
    val permissionGranted: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val limitStatus: LimitStatus = LimitStatus.NotConfigured,
    val cycleRange: DateRange? = null,
    val alerts: List<UsageAlert> = emptyList(),
    val notifications: NotificationSettings = NotificationSettings(),
    val notificationsPermissionGranted: Boolean = true,
    val error: String? = null,
    val errorDetail: String? = null,
    val lastUpdated: Long? = null,
)

class AlertsViewModel(
    appContext: Context,
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
    private val detectAnomalies: DetectAnomaliesUseCase,
    private val computeLimitStatus: ComputeLimitStatusUseCase,
) : ViewModel() {

    private val appContext = appContext.applicationContext

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    private val refreshMutex = Mutex()

    init {
        refresh()
        viewModelScope.launch {
            settingsRepository.uiSettings.collect { settings ->
                _uiState.update { it.copy(notifications = settings.notifications) }
            }
        }
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            if (!refreshMutex.tryLock()) return@launch
            try {
                if (!UsageAccess.isGranted(appContext)) {
                    _uiState.update {
                        it.copy(
                            permissionGranted = false,
                            isLoading = false,
                            isRefreshing = false,
                            notificationsPermissionGranted = NotificationHelper.canPost(appContext),
                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(permissionGranted = true, isRefreshing = true) }
                if (force) usageRepository.invalidateCaches()
                try {
                    val settings = settingsRepository.uiSettings.first()
                    val now = System.currentTimeMillis()
                    val today = TimeUtils.localDateOf(now)

                    val todayUsage = usageRepository.periodUsage(
                        UsagePeriod.Today.resolveRange(settings.limit.billingCycleStartDay, now),
                    )
                    val previous7 = usageRepository.periodUsage(
                        DateRange(
                            TimeUtils.startOfDay(today.minusDays(7)),
                            TimeUtils.startOfDay(today),
                        ),
                    )
                    val cycleRange = TimeUtils.billingCycleRange(settings.limit.billingCycleStartDay, today)
                    val cycleUsed = usageRepository.totals(cycleRange.start, cycleRange.end)

                    val alerts = detectAnomalies(todayUsage.apps, previous7.apps, 7)
                    val status = computeLimitStatus(cycleUsed, settings.limit)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            alerts = alerts,
                            limitStatus = status,
                            cycleRange = cycleRange,
                            notificationsPermissionGranted = NotificationHelper.canPost(appContext),
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

    fun setNotifications(settings: NotificationSettings) {
        viewModelScope.launch { settingsRepository.setNotifications(settings) }
    }

    fun refreshNotificationPermission() {
        _uiState.update { it.copy(notificationsPermissionGranted = NotificationHelper.canPost(appContext)) }
    }

    companion object {
        private const val STALE_MS = 60L * 1000L
    }
}
