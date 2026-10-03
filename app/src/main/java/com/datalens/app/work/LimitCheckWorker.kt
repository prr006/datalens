package com.datalens.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.datalens.app.ServiceLocator

/**
 * Periodic alert pass (kept under its historical work name so existing schedules
 * migrate cleanly). All logic lives in AlertCoordinator, which is shared with the
 * usage-tracking service: cycle thresholds (50/75/90/100%), the daily usage
 * threshold and per-app thresholds — each firing at most once per cycle/day via
 * persisted anti-spam keys that re-arm automatically.
 */
class LimitCheckWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            ServiceLocator.alertCoordinator.run(applicationContext)
            Result.success()
        } catch (_: Exception) {
            // Alerts are best-effort; never fail the worker.
            Result.success()
        }
    }
}
