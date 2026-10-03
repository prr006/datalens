package com.datalens.app.util

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * PACKAGE_USAGE_STATS is an "appop"-style permission: it cannot be requested with the
 * normal runtime-permission dialog. The user must grant "Usage access" in Android's
 * special app access settings, and we can only poll its state.
 */
object UsageAccess {

    fun isGranted(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }
}
