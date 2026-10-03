package com.datalens.app

import android.app.Application
import com.datalens.app.domain.model.UnitsMode
import com.datalens.app.notifications.NotificationHelper
import com.datalens.app.util.ByteFormatter
import com.datalens.app.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DataLensApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        NotificationHelper.createChannels(this)
        // Periodic (WorkManager) checks only — no long-running service.
        WorkScheduler.ensureScheduled(this)
        // Apply the user's display units (binary/decimal) to all formatting.
        appScope.launch {
            try {
                ServiceLocator.settingsRepository.uiSettings.collect { settings ->
                    ByteFormatter.useDecimalUnits = settings.units == UnitsMode.DECIMAL
                }
            } catch (_: Exception) {
                // Preferences unreadable — binary units stay as default.
            }
        }
    }
}
