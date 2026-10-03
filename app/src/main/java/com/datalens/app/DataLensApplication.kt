package com.datalens.app

import android.app.Application
import com.datalens.app.notifications.NotificationHelper
import com.datalens.app.work.WorkScheduler

class DataLensApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        NotificationHelper.createChannels(this)
        // Periodic (WorkManager) checks only — no long-running service.
        WorkScheduler.ensureScheduled(this)
    }
}
