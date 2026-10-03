package com.datalens.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.datalens.app.data.apps.AppInfoDataSource
import com.datalens.app.data.db.AppDatabase
import com.datalens.app.data.network.NetworkStatsDataSource
import com.datalens.app.data.prefs.UserPreferencesDataSource
import com.datalens.app.data.repository.SettingsRepository
import com.datalens.app.data.repository.UsageRepository
import com.datalens.app.domain.usecase.BuildUsageReportUseCase
import com.datalens.app.domain.usecase.ComputeCycleInsightsUseCase
import com.datalens.app.domain.usecase.ComputeLimitStatusUseCase
import com.datalens.app.domain.usecase.DetectAnomaliesUseCase
import com.datalens.app.domain.usecase.EvaluateAlertsUseCase
import com.datalens.app.domain.usecase.GetOverviewDataUseCase
import com.datalens.app.notifications.AlertCoordinator

/**
 * Tiny hand-rolled service locator. The app is deliberately free of DI frameworks —
 * this is the entire dependency graph.
 */
object ServiceLocator {

    @Volatile
    private var initialized = false

    lateinit var appContext: Context
        private set

    val appInfoDataSource: AppInfoDataSource by lazy { AppInfoDataSource(appContext) }
    val networkStatsDataSource: NetworkStatsDataSource by lazy { NetworkStatsDataSource(appContext) }
    val usageRepository: UsageRepository by lazy { UsageRepository(networkStatsDataSource, appInfoDataSource) }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(AppDatabase.get(appContext), UserPreferencesDataSource(appContext))
    }

    val getOverviewData: GetOverviewDataUseCase by lazy { GetOverviewDataUseCase(usageRepository) }
    val detectAnomalies: DetectAnomaliesUseCase by lazy { DetectAnomaliesUseCase() }
    val computeLimitStatus: ComputeLimitStatusUseCase by lazy { ComputeLimitStatusUseCase() }
    val computeCycleInsights: ComputeCycleInsightsUseCase by lazy { ComputeCycleInsightsUseCase() }
    val evaluateAlerts: EvaluateAlertsUseCase by lazy { EvaluateAlertsUseCase() }
    val buildReport: BuildUsageReportUseCase by lazy { BuildUsageReportUseCase(usageRepository) }

    val alertCoordinator: AlertCoordinator by lazy {
        AlertCoordinator(usageRepository, settingsRepository, evaluateAlerts)
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            registerPackageChangeReceiver()
            initialized = true
        }
    }

    /** Keeps the app cache fresh when apps are installed, updated or removed. */
    private fun registerPackageChangeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                appInfoDataSource.invalidate()
            }
        }
        // PACKAGE_* are protected system broadcasts; RECEIVER_EXPORTED is required for
        // context-registered receivers that must receive them on Android 14+.
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }
}
