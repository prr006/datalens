package com.datalens.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Apps the user pinned to the dashboard. */
@Entity(tableName = "pinned_apps")
data class PinnedAppEntity(
    @PrimaryKey val packageName: String,
    val pinnedAt: Long = System.currentTimeMillis(),
)

/** Apps the user hid from dashboard/lists (presentation only). */
@Entity(tableName = "hidden_apps")
data class HiddenAppEntity(
    @PrimaryKey val packageName: String,
    val hiddenAt: Long = System.currentTimeMillis(),
)

/** Single-row table (id = 0) with the user's data-limit configuration. */
@Entity(tableName = "limit_config")
data class LimitConfigEntity(
    @PrimaryKey val id: Int = 0,
    val monthlyAllowanceBytes: Long = 0L,
    val dailyTargetBytes: Long = 0L,
    val billingCycleStartDay: Int = 1,
    val warningThresholdPercent: Int = 75,
    val isUnlimited: Boolean = false,
    val dailyAlertThresholdBytes: Long = 0L,
    val perAppAlertThresholdBytes: Long = 0L,
)
