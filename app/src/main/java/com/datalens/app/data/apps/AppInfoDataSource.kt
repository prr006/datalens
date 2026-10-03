package com.datalens.app.data.apps

import android.app.usage.NetworkStats
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.SystemClock
import com.datalens.app.domain.model.AppCategory
import com.datalens.app.domain.model.AppEntry
import com.datalens.app.domain.model.AppUsageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** All applications DataLens can resolve, indexed for fast UID/package lookup. */
data class AppDirectory(
    val byUid: Map<Int, List<AppEntry>>,
    val byPackage: Map<String, AppEntry>,
    val allEntries: List<AppEntry>,
)

/** The result of attributing a NetworkStats UID to an app. */
data class UidResolution(
    val packageName: String,
    val appName: String,
    val icon: Bitmap?,
    val isSystem: Boolean,
    val category: AppCategory,
    /** Additional packages sharing this UID, if any (shared UIDs). */
    val sharedPackages: List<String>,
)

/**
 * Resolves UIDs reported by NetworkStatsManager into apps the user can recognize.
 *
 * Package visibility: DataLens declares only a MAIN/LAUNCHER `<queries>` filter plus a
 * handful of well-known non-launchable system packages — deliberately *not*
 * QUERY_ALL_PACKAGES. UIDs outside that set are shown with their package name, or as
 * "Unknown / System process" — never with a wrong guess.
 *
 * All data comes from PackageManager; everything is cached and cached icons are
 * pre-rasterized on a background thread.
 */
class AppInfoDataSource(private val context: Context) {

    @Volatile
    private var cachedDirectory: AppDirectory? = null

    @Volatile
    private var lastBuiltAtElapsed: Long = 0L

    private val buildMutex = Mutex()

    /** Called when packages are added/removed/replaced so the cache is rebuilt. */
    fun invalidate() {
        cachedDirectory = null
    }

    suspend fun directory(forceRefresh: Boolean = false): AppDirectory {
        cachedDirectory?.let { dir ->
            if (!forceRefresh && SystemClock.elapsedRealtime() - lastBuiltAtElapsed < CACHE_TTL_MS) {
                return dir
            }
        }
        return buildMutex.withLock {
            val cached = cachedDirectory
            if (!forceRefresh && cached != null &&
                SystemClock.elapsedRealtime() - lastBuiltAtElapsed < CACHE_TTL_MS
            ) {
                return cached
            }
            val dir = buildDirectory()
            cachedDirectory = dir
            lastBuiltAtElapsed = SystemClock.elapsedRealtime()
            dir
        }
    }

    /**
     * Attributes [uid] to an app. Order of preference:
     *  1. well-known special UIDs (tethering, removed apps, …)
     *  2. an installed package visible to DataLens sharing that UID
     *  3. any package name PackageManager still reports for the UID (label = package name)
     *  4. "Unknown / System process" with a synthetic package name
     */
    suspend fun resolveUid(uid: Int): UidResolution = withContext(Dispatchers.IO) {
        specialUidResolution(uid)?.let { return@withContext it }

        val directory = directory()
        val entries = directory.byUid[uid]
        if (!entries.isNullOrEmpty()) {
            val primary = pickPrimary(entries)
            return@withContext UidResolution(
                packageName = primary.packageName,
                appName = primary.label,
                icon = primary.icon,
                isSystem = primary.isSystem,
                category = primary.category,
                sharedPackages = entries.map { it.packageName }.filter { it != primary.packageName },
            )
        }

        // UID not in our directory: ask PackageManager for any package names it still
        // reports for this UID (covers apps that are not visible or were removed).
        val pm = context.packageManager
        val names: Array<String> = try {
            pm.getPackagesForUid(uid) ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
        if (names.isNotEmpty()) {
            for (name in names) {
                try {
                    val ai = pm.getApplicationInfo(name, 0)
                    val entry = buildEntry(pm, ai, isLaunchable = false)
                    return@withContext UidResolution(
                        packageName = entry.packageName,
                        appName = entry.label,
                        icon = entry.icon,
                        isSystem = entry.isSystem,
                        category = entry.category,
                        sharedPackages = names.filter { it != entry.packageName },
                    )
                } catch (_: Exception) {
                    // Not visible to us — try the next name.
                }
            }
            // Package names known but no ApplicationInfo visible: use the name as label.
            val first = names.first()
            return@withContext UidResolution(
                packageName = first,
                appName = first,
                icon = null,
                isSystem = true,
                category = AppCategory.SYSTEM,
                sharedPackages = names.filter { it != first },
            )
        }

        UidResolution(
            packageName = syntheticPackage(uid),
            appName = AppUsageInfo.UNKNOWN_LABEL,
            icon = null,
            isSystem = true,
            category = AppCategory.SYSTEM,
            sharedPackages = emptyList(),
        )
    }

    private fun pickPrimary(entries: List<AppEntry>): AppEntry {
        // For the shared system UID prefer the platform package ("android").
        entries.firstOrNull { it.packageName == PLATFORM_PACKAGE }?.let { return it }
        // Then prefer a launchable, non-system app (the thing the user actually opened).
        entries.firstOrNull { it.isLaunchable && !it.isSystem }?.let { return it }
        entries.firstOrNull { it.isLaunchable }?.let { return it }
        return entries.first()
    }

    private fun specialUidResolution(uid: Int): UidResolution? {
        return when (uid) {
            NetworkStats.Bucket.UID_TETHERING -> UidResolution(
                packageName = TETHERING_PACKAGE,
                appName = "Tethering & hotspot",
                icon = null,
                isSystem = true,
                category = AppCategory.SYSTEM,
                sharedPackages = emptyList(),
            )
            NetworkStats.Bucket.UID_REMOVED -> UidResolution(
                packageName = REMOVED_PACKAGE,
                appName = "Removed apps",
                icon = null,
                isSystem = true,
                category = AppCategory.SYSTEM,
                sharedPackages = emptyList(),
            )
            else -> if (uid < 0) {
                UidResolution(
                    packageName = syntheticPackage(uid),
                    appName = "System process (uid $uid)",
                    icon = null,
                    isSystem = true,
                    category = AppCategory.SYSTEM,
                    sharedPackages = emptyList(),
                )
            } else {
                null
            }
        }
    }

    private suspend fun buildDirectory(): AppDirectory = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val byPackage = LinkedHashMap<String, AppEntry>()

        // 1) Everything with a launcher entry — visible thanks to the <queries> filter.
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            pm.queryIntentActivities(launcherIntent, 0)
        } catch (_: Exception) {
            emptyList()
        }
        for (info in resolved) {
            val ai = info.activityInfo?.applicationInfo ?: continue
            if (!byPackage.containsKey(ai.packageName)) {
                runCatching { byPackage[ai.packageName] = buildEntry(pm, ai, isLaunchable = true) }
            }
        }

        // 2) Well-known non-launchable packages that commonly appear in mobile stats.
        for (pkg in EXPLICIT_PACKAGES) {
            if (byPackage.containsKey(pkg)) continue
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                byPackage[pkg] = buildEntry(pm, ai, isLaunchable = false)
            } catch (_: Exception) {
                // Not installed / not visible — fine.
            }
        }

        // 3) The platform package, so the shared system UID renders as "Android System".
        try {
            val ai = pm.getApplicationInfo(PLATFORM_PACKAGE, 0)
            if (!byPackage.containsKey(PLATFORM_PACKAGE)) {
                byPackage[PLATFORM_PACKAGE] = buildEntry(pm, ai, isLaunchable = false)
            }
        } catch (_: Exception) {
            // Ignore.
        }

        val all = byPackage.values.toList()
        val byUid = all.groupBy { it.uid }
        AppDirectory(byUid = byUid, byPackage = byPackage, allEntries = all)
    }

    private fun buildEntry(pm: PackageManager, ai: ApplicationInfo, isLaunchable: Boolean): AppEntry {
        val label = try {
            pm.getApplicationLabel(ai)?.toString()?.takeIf { it.isNotBlank() } ?: ai.packageName
        } catch (_: Exception) {
            ai.packageName
        }
        val isSystem = (ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        return AppEntry(
            uid = ai.uid,
            packageName = ai.packageName,
            label = label,
            icon = rasterizeIcon(pm, ai),
            isSystem = isSystem,
            category = categorize(ai),
            isLaunchable = isLaunchable,
        )
    }

    private fun rasterizeIcon(pm: PackageManager, ai: ApplicationInfo): Bitmap? {
        return try {
            val density = context.resources.displayMetrics.density
            val sizePx = (ICON_DP * density).toInt().coerceIn(48, 192)
            val drawable = try {
                pm.getApplicationIcon(ai)
            } catch (_: Exception) {
                null
            } ?: return null
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /** Local heuristic category: first ApplicationInfo.category, then name hints. */
    private fun categorize(ai: ApplicationInfo): AppCategory {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (ai.category) {
                ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.VIDEO
                ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.MUSIC_AUDIO
                ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
                ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
                ApplicationInfo.CATEGORY_PRODUCTIVITY,
                ApplicationInfo.CATEGORY_EMAIL,
                -> return AppCategory.PRODUCTIVITY
                else -> Unit
            }
        }
        val pkg = ai.packageName.lowercase()
        return when {
            pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("signal") ||
                pkg.contains("sms") || pkg.contains("mms") || pkg.contains("messaging") ||
                pkg.contains("messenger") || pkg.contains("hike") || pkg.contains("duo") ||
                pkg.contains("meet") || pkg.contains("imo")
            -> AppCategory.MESSAGING

            pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("hotstar") ||
                pkg.contains("jiocinema") || pkg.contains("primevideo") || pkg.contains("twitch") ||
                pkg.contains("vimeo") || pkg.contains("sonyliv") || pkg.contains("zee5") ||
                pkg.contains("mxplayer") || pkg.contains("voot") || pkg.contains("player")
            -> AppCategory.VIDEO

            pkg.contains("spotify") || pkg.contains("gaana") || pkg.contains("wynk") ||
                pkg.contains("saavn") || pkg.contains("music") || pkg.contains("audio") ||
                pkg.contains("podcast") || pkg.contains("hungama") || pkg.contains("rhapsody")
            -> AppCategory.MUSIC_AUDIO

            pkg.contains("chrome") || pkg.contains("firefox") || pkg.contains("browser") ||
                pkg.contains("opera") || pkg.contains("brave") || pkg.contains("edge") ||
                pkg.contains("duckduckgo") || pkg.contains("sbrowser")
            -> AppCategory.BROWSER

            pkg.contains("facebook") || pkg.contains("instagram") || pkg.contains("tiktok") ||
                pkg.contains("snapchat") || pkg.contains("discord") || pkg.contains("twitter") ||
                pkg.contains("linkedin") || pkg.contains("threads") || pkg.contains("pinterest") ||
                pkg.contains("reddit") || pkg.contains("sharechat")
            -> AppCategory.SOCIAL

            pkg.contains("game") || pkg.contains("roblox") || pkg.contains("minecraft") ||
                pkg.contains("clash") || pkg.contains("candy") || pkg.contains("pubg") ||
                pkg.contains("bgmi") || pkg.contains("niantic") || pkg.contains("supercell")
            -> AppCategory.GAMES

            pkg.contains("office") || pkg.contains("docs") || pkg.contains("sheets") ||
                pkg.contains("slides") || pkg.contains("gmail") || pkg.contains("outlook") ||
                pkg.contains("notion") || pkg.contains("evernote") || pkg.contains("keep") ||
                pkg.contains("calendar") || pkg.contains("drive") || pkg.contains("todoist") ||
                pkg.contains("trello") || pkg.contains("zoom") || pkg.contains("camscanner")
            -> AppCategory.PRODUCTIVITY

            else -> AppCategory.OTHER
        }
    }

    companion object {
        private const val CACHE_TTL_MS = 5L * 60L * 1000L
        private const val ICON_DP = 48
        private const val PLATFORM_PACKAGE = "android"
        const val TETHERING_PACKAGE = "tethering"
        const val REMOVED_PACKAGE = "removed-apps"
        const val ANDROID_SYSTEM_PACKAGE = "android"

        fun syntheticPackage(uid: Int): String = "${AppUsageInfo.SYNTHETIC_PACKAGE_PREFIX}$uid"

        /** Non-launchable packages that frequently show up in mobile-data statistics. */
        private val EXPLICIT_PACKAGES = listOf(
            "com.google.android.gms",        // Google Play services
            "com.google.android.ims",        // Carrier services (RCS)
            "com.google.android.carrier",    // Carrier config
            "com.android.vending",           // Play Store (also launchable, kept for safety)
        )
    }
}
