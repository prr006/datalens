package com.datalens.app.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Display unit system: binary (1 KB = 1024 B) or decimal/SI (1 KB = 1000 B). */
enum class UnitsMode(val label: String) {
    BINARY("Binary · 1 KB = 1024 B"),
    DECIMAL("Decimal · 1 KB = 1000 B"),
}

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
    val units: UnitsMode = UnitsMode.BINARY,
    /** Fast (~45 s) notification refresh while mobile data is active. */
    val frequentRefresh: Boolean = true,
    val limit: LimitConfig = LimitConfig.DEFAULT,
)

/** A generated export report ready to be written to a SAF location or shared. */
class ReportData(
    val suggestedFileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)
