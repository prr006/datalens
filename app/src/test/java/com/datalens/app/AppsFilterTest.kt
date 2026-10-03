package com.datalens.app

import com.datalens.app.domain.model.AppCategory
import com.datalens.app.domain.model.AppListSort
import com.datalens.app.domain.model.AppTypeFilter
import com.datalens.app.domain.model.AppUsageInfo
import com.datalens.app.domain.model.AppsFilter
import com.datalens.app.domain.model.AppsFilterState
import org.junit.Assert.assertEquals
import org.junit.Test

class AppsFilterTest {

    private fun app(
        name: String,
        pkg: String,
        rx: Long,
        tx: Long,
        system: Boolean = false,
        category: AppCategory = AppCategory.OTHER,
    ) = AppUsageInfo(
        uid = 1000,
        packageName = pkg,
        appName = name,
        icon = null,
        isSystem = system,
        category = category,
        receivedBytes = rx,
        transmittedBytes = tx,
    )

    private val apps = listOf(
        app("YouTube", "com.google.android.youtube", 1300, 100, category = AppCategory.VIDEO),
        app("Instagram", "com.instagram.android", 790, 53, category = AppCategory.SOCIAL),
        app("Chrome", "com.android.chrome", 500, 112, system = true, category = AppCategory.BROWSER),
        app("Notes", "com.example.notes", 0, 0, category = AppCategory.PRODUCTIVITY),
    )

    @Test
    fun `default sorts by total descending and hides zero usage`() {
        val result = AppsFilter.apply(apps, AppsFilterState(), emptySet())
        assertEquals(
            listOf("com.google.android.youtube", "com.instagram.android", "com.android.chrome"),
            result.map { it.packageName },
        )
    }

    @Test
    fun `zero usage apps appear when enabled`() {
        val result = AppsFilter.apply(apps, AppsFilterState(showZeroUsage = true), emptySet())
        assertEquals(4, result.size)
        assertEquals("com.example.notes", result.last().packageName)
    }

    @Test
    fun `search matches name case-insensitively`() {
        val result = AppsFilter.apply(
            apps,
            AppsFilterState(query = "insta", showZeroUsage = true),
            emptySet(),
        )
        assertEquals(1, result.size)
        assertEquals("Instagram", result[0].appName)
    }

    @Test
    fun `search matches package name`() {
        val result = AppsFilter.apply(
            apps,
            AppsFilterState(query = "youtube", showZeroUsage = true),
            emptySet(),
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `sort by download ascending`() {
        val result = AppsFilter.apply(
            apps,
            AppsFilterState(sort = AppListSort.DOWNLOAD, sortAscending = true, showZeroUsage = true),
            emptySet(),
        )
        assertEquals(0L, result.first().receivedBytes)
        assertEquals(1300L, result.last().receivedBytes)
    }

    @Test
    fun `sort by upload descending`() {
        val result = AppsFilter.apply(
            apps,
            AppsFilterState(sort = AppListSort.UPLOAD),
            emptySet(),
        )
        assertEquals(112L, result.first().transmittedBytes)
    }

    @Test
    fun `sort by name ascending`() {
        val result = AppsFilter.apply(
            apps,
            AppsFilterState(sort = AppListSort.NAME, sortAscending = true, showZeroUsage = true),
            emptySet(),
        )
        assertEquals("Chrome", result.first().appName)
        assertEquals("YouTube", result.last().appName)
    }

    @Test
    fun `system app filter`() {
        val result = AppsFilter.apply(
            apps,
            AppsFilterState(typeFilter = AppTypeFilter.SYSTEM, showZeroUsage = true),
            emptySet(),
        )
        assertEquals(listOf("com.android.chrome"), result.map { it.packageName })
    }

    @Test
    fun `user app filter excludes system apps`() {
        val result = AppsFilter.apply(
            apps,
            AppsFilterState(typeFilter = AppTypeFilter.USER, showZeroUsage = true),
            emptySet(),
        )
        assertEquals(3, result.size)
        assertEquals(0, result.count { it.isSystem })
    }

    @Test
    fun `hidden packages are excluded from all`() {
        val result = AppsFilter.apply(
            apps,
            AppsFilterState(showZeroUsage = true),
            setOf("com.instagram.android", "com.android.chrome"),
        )
        assertEquals(listOf("com.google.android.youtube", "com.example.notes"), result.map { it.packageName })
    }

    @Test
    fun `hidden filter shows only hidden`() {
        val result = AppsFilter.apply(
            apps,
            AppsFilterState(typeFilter = AppTypeFilter.HIDDEN, showZeroUsage = true),
            setOf("com.instagram.android"),
        )
        assertEquals(listOf("com.instagram.android"), result.map { it.packageName })
    }

    @Test
    fun `category filter`() {
        val result = AppsFilter.apply(
            apps,
            AppsFilterState(category = AppCategory.SOCIAL, showZeroUsage = true),
            emptySet(),
        )
        assertEquals(listOf("com.instagram.android"), result.map { it.packageName })
    }

    @Test
    fun `pinned filter shows only pinned apps`() {
        val pinned = setOf("com.instagram.android", "com.example.notes")
        val result = AppsFilter.apply(
            apps,
            AppsFilterState(typeFilter = AppTypeFilter.PINNED),
            emptySet(),
            pinnedPackages = pinned,
        )
        // Notes is pinned but has zero usage — the zero-usage rule still applies.
        assertEquals(listOf("com.instagram.android"), result.map { it.packageName })
    }

    @Test
    fun `pinned filter shows zero-usage pinned apps when enabled`() {
        val pinned = setOf("com.instagram.android", "com.example.notes")
        val result = AppsFilter.apply(
            apps,
            AppsFilterState(typeFilter = AppTypeFilter.PINNED, showZeroUsage = true),
            emptySet(),
            pinnedPackages = pinned,
        )
        assertEquals(
            listOf("com.instagram.android", "com.example.notes"),
            result.map { it.packageName },
        )
    }

    @Test
    fun `pinned filter excludes hidden apps even when pinned`() {
        val hidden = setOf("com.instagram.android")
        val result = AppsFilter.apply(
            apps,
            AppsFilterState(typeFilter = AppTypeFilter.PINNED),
            hiddenPackages = hidden,
            pinnedPackages = setOf("com.instagram.android"),
        )
        assertEquals(0, result.size)
    }

    @Test
    fun `pinned filter without pinned apps is empty`() {
        val result = AppsFilter.apply(
            apps,
            AppsFilterState(typeFilter = AppTypeFilter.PINNED),
            emptySet(),
            pinnedPackages = emptySet(),
        )
        assertEquals(0, result.size)
    }
}
