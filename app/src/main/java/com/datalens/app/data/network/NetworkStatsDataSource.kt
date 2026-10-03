package com.datalens.app.data.network

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import com.datalens.app.domain.model.ByteTotals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thrown when Android's network-statistics service fails for reasons other than a
 * missing Usage Access permission (bad range, binder failure, OEM quirks, …).
 */
class NetworkStatsQueryException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * The single source of truth for real mobile-data statistics.
 *
 * Uses [NetworkStatsManager] with [ConnectivityManager.TYPE_MOBILE] only — Wi-Fi
 * traffic is never mixed into these numbers.
 *
 * Notes on the API (also documented in the README):
 *  - `subscriberId` is passed as null, which aggregates across all mobile
 *    subscriptions (SIMs). Third-party apps cannot reliably split usage per SIM;
 *    DataLens does not claim per-SIM precision.
 *  - `querySummary` returns one aggregated bucket per UID for the requested window,
 *    which is exactly the shape we need: one binder call per period, no per-app loop.
 */
class NetworkStatsDataSource(context: Context) {

    private val networkStatsManager = context.getSystemService(NetworkStatsManager::class.java)

    /**
     * Queries mobile-network usage in `[start, end)` aggregated per UID.
     *
     * @throws SecurityException when Usage Access (PACKAGE_USAGE_STATS) is not granted.
     * @throws NetworkStatsQueryException for other Android-side failures.
     */
    suspend fun queryUidUsage(start: Long, end: Long): Map<Int, ByteTotals> =
        withContext(Dispatchers.IO) {
            if (start >= end) return@withContext emptyMap()
            val manager = networkStatsManager ?: return@withContext emptyMap()

            val stats: NetworkStats = try {
                @Suppress("DEPRECATION")
                manager.querySummary(
                    ConnectivityManager.TYPE_MOBILE,
                    /* subscriberId = */ null,
                    start,
                    end,
                )
            } catch (e: SecurityException) {
                throw e
            } catch (e: IllegalArgumentException) {
                throw NetworkStatsQueryException("Android rejected the requested statistics range.", e)
            } catch (e: Exception) {
                throw NetworkStatsQueryException("Android's network statistics service failed.", e)
            }

            val byUid = HashMap<Int, ByteTotals>()
            try {
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    if (!stats.getNextBucket(bucket)) break
                    val uid = bucket.uid
                    if (uid == NetworkStats.Bucket.UID_ALL) continue
                    val current = byUid[uid] ?: ByteTotals.ZERO
                    byUid[uid] = ByteTotals(
                        receivedBytes = current.receivedBytes + bucket.rxBytes,
                        transmittedBytes = current.transmittedBytes + bucket.txBytes,
                    )
                }
            } finally {
                // NetworkStats wraps a binder object; always release it.
                stats.close()
            }
            byUid
        }
}
