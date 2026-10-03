package com.datalens.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.datalens.app.domain.model.NotificationSettings
import com.datalens.app.domain.model.ThemeMode
import com.datalens.app.domain.model.UnitsMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "datalens_settings")

/** Simple, on-device user preferences (DataStore). No sync, no backup. */
class UserPreferencesDataSource(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val NOTIF_MASTER = booleanPreferencesKey("notif_master")
        val NOTIF_DAILY = booleanPreferencesKey("notif_daily")
        val NOTIF_LIMIT = booleanPreferencesKey("notif_limit")
        val NOTIF_HIGH = booleanPreferencesKey("notif_high")
        val HIDE_SYSTEM_APPS = booleanPreferencesKey("hide_system_apps")
        // Persistent usage-tracking notification (foreground service).
        val USAGE_TRACKING_ENABLED = booleanPreferencesKey("usage_tracking_enabled")
        // Units ("binary" = 1 KB = 1024 B, "decimal" = 1 KB = 1000 B).
        val UNITS = stringPreferencesKey("units")
        // Notification refresh behaviour: fast (~45 s) while mobile data is on.
        val FREQUENT_REFRESH = booleanPreferencesKey("notif_frequent_refresh")
        // Anti-spam bookkeeping for notifications.
        val LAST_DAILY_NOTIF_DATE = stringPreferencesKey("last_daily_notif_date")
        val LAST_LIMIT_NOTIF_KEY = stringPreferencesKey("last_limit_notif_key")
        val LAST_HIGH_NOTIF_KEY = stringPreferencesKey("last_high_notif_key")
        // Anti-spam keys for the threshold alerts (once per day / cycle / app+day).
        val LAST_DAILY_THRESHOLD_ALERT = stringPreferencesKey("last_daily_threshold_alert")
        val POSTED_APP_ALERTS = stringSetPreferencesKey("posted_app_alerts")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            theme = when (prefs[Keys.THEME]) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            },
            dynamicColors = prefs[Keys.DYNAMIC_COLORS] ?: true,
            notifications = NotificationSettings(
                enabled = prefs[Keys.NOTIF_MASTER] ?: true,
                dailySummary = prefs[Keys.NOTIF_DAILY] ?: true,
                limitWarnings = prefs[Keys.NOTIF_LIMIT] ?: true,
                highUsageAlerts = prefs[Keys.NOTIF_HIGH] ?: true,
            ),
            hideSystemAppsByDefault = prefs[Keys.HIDE_SYSTEM_APPS] ?: false,
            usageTrackingEnabled = prefs[Keys.USAGE_TRACKING_ENABLED] ?: false,
            units = if (prefs[Keys.UNITS] == "decimal") UnitsMode.DECIMAL else UnitsMode.BINARY,
            frequentRefresh = prefs[Keys.FREQUENT_REFRESH] ?: true,
            lastDailyNotifDate = prefs[Keys.LAST_DAILY_NOTIF_DATE] ?: "",
            lastLimitNotifKey = prefs[Keys.LAST_LIMIT_NOTIF_KEY] ?: "",
            lastHighNotifKey = prefs[Keys.LAST_HIGH_NOTIF_KEY] ?: "",
            lastDailyThresholdAlertKey = prefs[Keys.LAST_DAILY_THRESHOLD_ALERT] ?: "",
            postedAppAlertKeys = prefs[Keys.POSTED_APP_ALERTS] ?: emptySet(),
        )
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit {
        it[Keys.THEME] = when (mode) {
            ThemeMode.SYSTEM -> "system"
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
        }
    }

    suspend fun setDynamicColors(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DYNAMIC_COLORS] = enabled }

    suspend fun setNotifications(settings: NotificationSettings) = context.dataStore.edit {
        it[Keys.NOTIF_MASTER] = settings.enabled
        it[Keys.NOTIF_DAILY] = settings.dailySummary
        it[Keys.NOTIF_LIMIT] = settings.limitWarnings
        it[Keys.NOTIF_HIGH] = settings.highUsageAlerts
    }

    suspend fun setHideSystemApps(hide: Boolean) =
        context.dataStore.edit { it[Keys.HIDE_SYSTEM_APPS] = hide }

    suspend fun setUsageTrackingEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.USAGE_TRACKING_ENABLED] = enabled }

    suspend fun setUnits(mode: UnitsMode) = context.dataStore.edit {
        it[Keys.UNITS] = if (mode == UnitsMode.DECIMAL) "decimal" else "binary"
    }

    suspend fun setFrequentRefresh(enabled: Boolean) =
        context.dataStore.edit { it[Keys.FREQUENT_REFRESH] = enabled }

    suspend fun markDailyThresholdAlertPosted(key: String) =
        context.dataStore.edit { it[Keys.LAST_DAILY_THRESHOLD_ALERT] = key }

    /**
     * Records newly posted per-app alerts and prunes keys from days older than
     * [keepFromIsoDate] (ISO dates compare correctly as strings).
     */
    suspend fun markAppAlertsPosted(newKeys: List<String>, keepFromIsoDate: String) =
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.POSTED_APP_ALERTS] ?: emptySet()
            val kept = existing.filter { key ->
                val date = key.removePrefix("app:").substringBefore(':')
                date >= keepFromIsoDate
            }.toSet()
            prefs[Keys.POSTED_APP_ALERTS] = kept + newKeys.toSet()
        }

    suspend fun setLastDailyNotifDate(date: String) =
        context.dataStore.edit { it[Keys.LAST_DAILY_NOTIF_DATE] = date }

    suspend fun setLastLimitNotifKey(key: String) =
        context.dataStore.edit { it[Keys.LAST_LIMIT_NOTIF_KEY] = key }

    suspend fun setLastHighNotifKey(key: String) =
        context.dataStore.edit { it[Keys.LAST_HIGH_NOTIF_KEY] = key }
}

data class UserPreferences(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColors: Boolean = true,
    val notifications: NotificationSettings = NotificationSettings(),
    val hideSystemAppsByDefault: Boolean = false,
    val usageTrackingEnabled: Boolean = false,
    val units: UnitsMode = UnitsMode.BINARY,
    val frequentRefresh: Boolean = true,
    val lastDailyNotifDate: String = "",
    val lastLimitNotifKey: String = "",
    val lastHighNotifKey: String = "",
    val lastDailyThresholdAlertKey: String = "",
    val postedAppAlertKeys: Set<String> = emptySet(),
)
