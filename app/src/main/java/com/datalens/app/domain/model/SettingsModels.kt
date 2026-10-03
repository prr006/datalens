package com.datalens.app.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class NotificationSettings(
    val enabled: Boolean = true,
    val dailySummary: Boolean = true,
    val limitWarnings: Boolean = true,
    val highUsageAlerts: Boolean = true,
)

/** Everything the Settings UI needs, combined. */
data class UiSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColors: Boolean = true,
    val notifications: NotificationSettings = NotificationSettings(),
    val hideSystemAppsByDefault: Boolean = false,
    /** Persistent usage-tracking notification (foreground service). */
    val usageTrackingEnabled: Boolean = false,
    val limit: LimitConfig = LimitConfig.DEFAULT,
)

/** A generated export report ready to be written to a SAF location or shared. */
class ReportData(
    val suggestedFileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)
