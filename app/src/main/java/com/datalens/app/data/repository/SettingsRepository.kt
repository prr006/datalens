package com.datalens.app.data.repository

import com.datalens.app.data.db.AppDatabase
import com.datalens.app.data.db.LimitConfigEntity
import com.datalens.app.data.db.HiddenAppEntity
import com.datalens.app.data.db.PinnedAppEntity
import com.datalens.app.data.prefs.UserPreferences
import com.datalens.app.data.prefs.UserPreferencesDataSource
import com.datalens.app.domain.model.LimitConfig
import com.datalens.app.domain.model.NotificationSettings
import com.datalens.app.domain.model.ThemeMode
import com.datalens.app.domain.model.UiSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Single façade over the small amount of state DataLens persists:
 *  - Room: pinned apps, hidden apps, data-limit configuration
 *  - DataStore: appearance, notification toggles, anti-spam keys
 */
class SettingsRepository(
    private val db: AppDatabase,
    private val prefs: UserPreferencesDataSource,
) {

    val uiSettings: Flow<UiSettings> =
        combine(prefs.preferences, db.limitConfigDao().config()) { p, limitEntity ->
            UiSettings(
                theme = p.theme,
                dynamicColors = p.dynamicColors,
                notifications = p.notifications,
                hideSystemAppsByDefault = p.hideSystemAppsByDefault,
                usageTrackingEnabled = p.usageTrackingEnabled,
                units = p.units,
                frequentRefresh = p.frequentRefresh,
                limit = limitEntity?.toDomain() ?: LimitConfig.DEFAULT,
            )
        }

    val rawPreferences: Flow<UserPreferences> = prefs.preferences

    val pinnedPackages: Flow<List<String>> = db.appCustomizationDao().pinnedPackages()

    val hiddenPackages: Flow<Set<String>> =
        db.appCustomizationDao().hiddenPackages().map { it.toSet() }

    suspend fun setTheme(mode: ThemeMode) = prefs.setTheme(mode)

    suspend fun setDynamicColors(enabled: Boolean) = prefs.setDynamicColors(enabled)

    suspend fun setNotifications(settings: NotificationSettings) = prefs.setNotifications(settings)

    suspend fun setHideSystemAppsByDefault(hide: Boolean) = prefs.setHideSystemApps(hide)

    suspend fun setUsageTrackingEnabled(enabled: Boolean) =
        prefs.setUsageTrackingEnabled(enabled)

    suspend fun setUnits(mode: com.datalens.app.domain.model.UnitsMode) = prefs.setUnits(mode)

    suspend fun setFrequentRefresh(enabled: Boolean) = prefs.setFrequentRefresh(enabled)

    suspend fun markDailyThresholdAlertPosted(key: String) =
        prefs.markDailyThresholdAlertPosted(key)

    suspend fun markAppAlertsPosted(newKeys: List<String>, keepFromIsoDate: String) =
        prefs.markAppAlertsPosted(newKeys, keepFromIsoDate)

    suspend fun saveLimitConfig(config: LimitConfig) {
        db.limitConfigDao().save(
            LimitConfigEntity(
                id = 0,
                monthlyAllowanceBytes = config.monthlyAllowanceBytes,
                dailyTargetBytes = config.dailyTargetBytes,
                billingCycleStartDay = config.billingCycleStartDay,
                warningThresholdPercent = config.warningThresholdPercent,
                isUnlimited = config.isUnlimited,
                dailyAlertThresholdBytes = config.dailyAlertThresholdBytes,
                perAppAlertThresholdBytes = config.perAppAlertThresholdBytes,
            ),
        )
    }

    suspend fun setPinned(packageName: String, pinned: Boolean) {
        if (pinned) {
            db.appCustomizationDao().pin(PinnedAppEntity(packageName))
        } else {
            db.appCustomizationDao().unpin(packageName)
        }
    }

    suspend fun setHidden(packageName: String, hidden: Boolean) {
        if (hidden) {
            db.appCustomizationDao().hide(HiddenAppEntity(packageName))
        } else {
            db.appCustomizationDao().unhide(packageName)
        }
    }

    suspend fun markDailyNotifPosted(date: String) = prefs.setLastDailyNotifDate(date)

    suspend fun markLimitNotifPosted(key: String) = prefs.setLastLimitNotifKey(key)

    suspend fun markHighNotifPosted(key: String) = prefs.setLastHighNotifKey(key)

    private fun LimitConfigEntity.toDomain() = LimitConfig(
        monthlyAllowanceBytes = monthlyAllowanceBytes,
        dailyTargetBytes = dailyTargetBytes,
        billingCycleStartDay = billingCycleStartDay,
        warningThresholdPercent = warningThresholdPercent,
        isUnlimited = isUnlimited,
        dailyAlertThresholdBytes = dailyAlertThresholdBytes,
        perAppAlertThresholdBytes = perAppAlertThresholdBytes,
    )
}
