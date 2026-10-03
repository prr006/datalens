package com.datalens.app.ui.navigation

import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.datalens.app.domain.model.UsagePeriod

/** Route names and builders. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val OVERVIEW = "overview"
    const val APPS = "apps"
    const val ALERTS = "alerts"
    const val SETTINGS = "settings"
    const val SETTINGS_PINNED = "settings/pinned"
    const val SETTINGS_HIDDEN = "settings/hidden"

    const val APP_DETAIL = "app/{uid}/{pkg}/{periodId}/{periodStart}/{periodEnd}"

    val topLevel = setOf(OVERVIEW, APPS, ALERTS, SETTINGS)

    /**
     * App-detail route. Only the period *id* is needed for fixed periods (the detail
     * screen resolves boundaries with the current billing-cycle settings); custom
     * ranges additionally carry their dates.
     */
    fun appDetail(uid: Int, packageName: String, period: UsagePeriod): String {
        val (customStart, customEnd) = if (period is UsagePeriod.Custom) {
            period.startDate.toEpochDay() to period.endDateInclusive.toEpochDay()
        } else {
            0L to 0L
        }
        return "app/$uid/${Uri.encode(packageName)}/${period.id}/$customStart/$customEnd"
    }

    val appDetailArgs = listOf(
        navArgument("uid") { type = NavType.IntType },
        navArgument("pkg") { type = NavType.StringType },
        navArgument("periodId") { type = NavType.StringType; defaultValue = UsagePeriod.Today.id },
        navArgument("periodStart") { type = NavType.LongType; defaultValue = 0L },
        navArgument("periodEnd") { type = NavType.LongType; defaultValue = 0L },
    )

    /** Rebuilds the period carried by an app-detail route. */
    fun periodFromArgs(periodId: String, startEpochDay: Long, endEpochDay: Long): UsagePeriod {
        return if (periodId == UsagePeriod.Custom.CUSTOM_ID && startEpochDay > 0 && endEpochDay >= startEpochDay) {
            UsagePeriod.Custom(
                java.time.LocalDate.ofEpochDay(startEpochDay),
                java.time.LocalDate.ofEpochDay(endEpochDay),
            )
        } else {
            UsagePeriod.fromArgs(periodId, 0L, 0L)
        }
    }
}
