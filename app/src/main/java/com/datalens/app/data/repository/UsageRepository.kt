package com.datalens.app.data.repository

import android.os.SystemClock
import android.util.Log
import com.datalens.app.data.apps.AppInfoDataSource
import com.datalens.app.data.network.NetworkStatsDataSource
import com.datalens.app.domain.model.AppUsageInfo
import com.datalens.app.domain.model.ByteTotals
import com.datalens.app.domain.model.DateRange
import com.datalens.app.domain.model.Granularity
import com.datalens.app.domain.model.PeriodUsage
import com.datalens.app.domain.model.UsagePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns raw per-UID mobile-data statistics into attributed, UI-ready usage data.
 *
 * Query strategy (performance):
 *  - one aggregated `querySummary` call per time window — never one call per app;
 *  - raw per-window results are cached briefly (60 s) so the Overview chart,
 *    summary card and other screens reuse the same data instead of re-querying;
 *  - a force-refresh drops all caches.
 *
 * Chart bucketing is done with one summary query per hour/day boundary. This is
 * exact — DataLens does not interpolate or estimate intra-bucket distribution.
 */
class UsageRepository(
    private val networkStats: NetworkStatsDataSource,
    private val appInfo: AppInfoDataSource,
) {

    data class DailyRawUsage(val date: LocalDate, val usage: Map<Int, ByteTotals>)

    private class RawEntry(val usage: Map<Int, ByteTotals>, val cachedAtElapsed: Long)

    private val rawCache = ConcurrentHashMap<String, RawEntry>()
    private val queryMutex = Mutex()

    /** Drops all cached statistics (used by explicit refresh actions). */
    fun invalidateCaches() {
        rawCache.clear()
        appInfo.invalidate()
    }

    /** Per-UID mobile usage for `[start, end)`. */
    suspend fun rawUidUsage(start: Long, end: Long): Map<Int, ByteTotals> {
        if (end <= start) return emptyMap()
        val key = "$start:$end"
        rawCache[key]?.let { entry ->
            if (isFresh(entry)) return entry.usage
        }
        return queryMutex.withLock {
            rawCache[key]?.let { entry ->
                if (isFresh(entry)) return entry.usage
            }
            val usage = networkStats.queryUidUsage(start, end)
            evictStale()
            rawCache[key] = RawEntry(usage, SystemClock.elapsedRealtime())
            usage
        }
    }

    private fun isFresh(entry: RawEntry): Boolean =
        SystemClock.elapsedRealtime() - entry.cachedAtElapsed < RAW_TTL_MS

    private fun evictStale() {
        if (rawCache.size > MAX_CACHE_ENTRIES) {
            rawCache.entries.removeIf { !isFresh(it.value) }
        }
    }

    /** Overall mobile totals for `[start, end)` — no app resolution. */
    suspend fun totals(start: Long, end: Long): ByteTotals =
        rawUidUsage(start, end).values.fold(ByteTotals.ZERO) { acc, v -> acc + v }

    /** Attributed per-app usage for a period, sorted by total descending. */
    suspend fun periodUsage(range: DateRange, uidFilter: Int? = null): PeriodUsage =
        withContext(Dispatchers.IO) {
            val raw = rawUidUsage(range.start, range.end)
                .filterKeys { uidFilter == null || it == uidFilter }
            val apps = raw.map { (uid, bytes) -> toAppUsage(uid, bytes) }
                .sortedByDescending { it.totalBytes }
            val totals = raw.values.fold(ByteTotals.ZERO) { acc, v -> acc + v }
            PeriodUsage(range = range, apps = apps, totals = totals)
        }

    /**
     * Union of apps that used mobile data in [range] and every app DataLens can see,
     * zero-filled for the latter. Zero rows are *absence of recorded statistics*,
     * not invented values.
     */
    suspend fun fullAppList(range: DateRange): List<AppUsageInfo> =
        withContext(Dispatchers.IO) {
            val usage = periodUsage(range)
            val usedPackages = usage.apps.map { it.packageName }.toHashSet()
            val zeroApps = appInfo.directory().allEntries
                .filter { !usedPackages.contains(it.packageName) }
                .map {
                    AppUsageInfo(
                        uid = it.uid,
                        packageName = it.packageName,
                        appName = it.label,
                        icon = it.icon,
                        isSystem = it.isSystem,
                        category = it.category,
                        receivedBytes = 0L,
                        transmittedBytes = 0L,
                    )
                }
            (usage.apps + zeroApps).sortedByDescending { it.totalBytes }
        }

    /**
     * Chart series for [range] bucketed by [granularity] (hourly or daily).
     * One exact summary query per bucket boundary.
     */
    suspend fun usageSeries(
        range: DateRange,
        granularity: Granularity,
        uidFilter: Int? = null,
    ): List<UsagePoint> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        bucketBoundaries(range, granularity)
            .filter { it.first < it.second }
            .map { (bucketStart, bucketEnd) ->
                // Skip buckets that lie entirely in the future — no stats can exist.
                if (bucketStart >= now) {
                    UsagePoint(bucketStart, bucketEnd, 0, 0)
                } else {
                    val raw = rawUidUsage(bucketStart, bucketEnd)
                    val agg = if (uidFilter != null) {
                        raw[uidFilter] ?: ByteTotals.ZERO
                    } else {
                        raw.values.fold(ByteTotals.ZERO) { acc, v -> acc + v }
                    }
                    UsagePoint(bucketStart, bucketEnd, agg.receivedBytes, agg.transmittedBytes)
                }
            }
    }

    /** Raw per-day usage maps over `[start, end)` — used for exports. */
    suspend fun dailyRawUsage(start: Long, end: Long, zone: ZoneId = ZoneId.systemDefault()): List<DailyRawUsage> =
        withContext(Dispatchers.IO) {
            var date = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
            val lastDate = Instant.ofEpochMilli(end - 1).atZone(zone).toLocalDate()
            val result = mutableListOf<DailyRawUsage>()
            while (!date.isAfter(lastDate)) {
                val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                result += DailyRawUsage(date, rawUidUsage(dayStart, minOf(dayEnd, end)))
                date = date.plusDays(1)
            }
            result
        }

    /** Attributed per-day app usage for exports. */
    suspend fun dailyAppUsage(range: DateRange): List<Pair<LocalDate, List<AppUsageInfo>>> =
        withContext(Dispatchers.IO) {
            val daily = dailyRawUsage(range.start, range.end)
            val uids = daily.flatMap { it.usage.keys }.toHashSet()
            val cache = HashMap<Int, AppUsageInfo>()
            for (uid in uids) {
                cache[uid] = toAppUsage(uid, ByteTotals.ZERO)
            }
            daily.map { day ->
                val apps = day.usage.entries
                    .map { (uid, bytes) ->
                        val template = cache[uid] ?: toAppUsage(uid, ByteTotals.ZERO)
                        template.copy(receivedBytes = bytes.receivedBytes, transmittedBytes = bytes.transmittedBytes)
                    }
                    .sortedByDescending { it.totalBytes }
                day.date to apps
            }
        }

    private suspend fun toAppUsage(uid: Int, bytes: ByteTotals): AppUsageInfo {
        val resolution = try {
            appInfo.resolveUid(uid)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve uid $uid", e)
            return AppUsageInfo(
                uid = uid,
                packageName = AppUsageInfo.SYNTHETIC_PACKAGE_PREFIX + uid,
                appName = AppUsageInfo.UNKNOWN_LABEL,
                icon = null,
                isSystem = true,
                category = com.datalens.app.domain.model.AppCategory.SYSTEM,
                receivedBytes = bytes.receivedBytes,
                transmittedBytes = bytes.transmittedBytes,
            )
        }
        return AppUsageInfo(
            uid = uid,
            packageName = resolution.packageName,
            appName = resolution.appName,
            icon = resolution.icon,
            isSystem = resolution.isSystem,
            category = resolution.category,
            receivedBytes = bytes.receivedBytes,
            transmittedBytes = bytes.transmittedBytes,
        )
    }

    private fun bucketBoundaries(range: DateRange, granularity: Granularity): List<Pair<Long, Long>> {
        val zone = ZoneId.systemDefault()
        val buckets = mutableListOf<Pair<Long, Long>>()
        when (granularity) {
            Granularity.HOURLY -> {
                var cursor = Instant.ofEpochMilli(range.start).atZone(zone)
                while (cursor.toInstant().toEpochMilli() < range.end) {
                    val next = cursor.plusHours(1)
                    val s = cursor.toInstant().toEpochMilli()
                    val e = next.toInstant().toEpochMilli()
                    buckets += s to minOf(e, range.end)
                    cursor = next
                }
            }
            Granularity.DAILY -> {
                var date = Instant.ofEpochMilli(range.start).atZone(zone).toLocalDate()
                val lastDate = Instant.ofEpochMilli(range.end - 1).atZone(zone).toLocalDate()
                while (!date.isAfter(lastDate)) {
                    val s = date.atStartOfDay(zone).toInstant().toEpochMilli()
                    val e = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    buckets += s to minOf(e, range.end)
                    date = date.plusDays(1)
                }
            }
        }
        return buckets
    }

    companion object {
        private const val TAG = "UsageRepository"
        private const val RAW_TTL_MS = 60L * 1000L
        private const val MAX_CACHE_ENTRIES = 256
    }
}
