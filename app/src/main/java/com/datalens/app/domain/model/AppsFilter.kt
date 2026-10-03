package com.datalens.app.domain.model

enum class AppListSort(val label: String) {
    TOTAL("Total usage"),
    DOWNLOAD("Download"),
    UPLOAD("Upload"),
    NAME("App name"),
}

enum class AppTypeFilter(val label: String) {
    ALL("All"),
    USER("User apps"),
    SYSTEM("System"),
    HIDDEN("Hidden"),
}

data class AppsFilterState(
    val query: String = "",
    val sort: AppListSort = AppListSort.TOTAL,
    val sortAscending: Boolean = false,
    val typeFilter: AppTypeFilter = AppTypeFilter.ALL,
    val showZeroUsage: Boolean = false,
    val category: AppCategory? = null,
)

/** Pure, unit-testable filter/sort pipeline for the All-Apps list. */
object AppsFilter {

    fun apply(
        apps: List<AppUsageInfo>,
        state: AppsFilterState,
        hiddenPackages: Set<String>,
    ): List<AppUsageInfo> {
        var result = apps.asSequence()

        result = result.filter { app ->
            when (state.typeFilter) {
                AppTypeFilter.ALL -> !hiddenPackages.contains(app.packageName)
                AppTypeFilter.USER -> !app.isSystem && !hiddenPackages.contains(app.packageName)
                AppTypeFilter.SYSTEM -> app.isSystem && !hiddenPackages.contains(app.packageName)
                AppTypeFilter.HIDDEN -> hiddenPackages.contains(app.packageName)
            }
        }

        if (!state.showZeroUsage) {
            result = result.filter { it.totalBytes > 0L }
        }

        state.category?.let { category ->
            result = result.filter { it.category == category }
        }

        val query = state.query.trim()
        if (query.isNotEmpty()) {
            val q = query.lowercase()
            result = result.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }

        val ascending = state.sortAscending
        val comparator = when (state.sort) {
            AppListSort.TOTAL -> compareBy<AppUsageInfo> { it.totalBytes }
                .thenBy { it.appName.lowercase() }
            AppListSort.DOWNLOAD -> compareBy<AppUsageInfo> { it.receivedBytes }
                .thenBy { it.appName.lowercase() }
            AppListSort.UPLOAD -> compareBy<AppUsageInfo> { it.transmittedBytes }
                .thenBy { it.appName.lowercase() }
            AppListSort.NAME -> compareBy { it.appName.lowercase() }
        }
        val effective = if (ascending) comparator else comparator.reversed()
        return result.sortedWith(effective).toList()
    }
}
