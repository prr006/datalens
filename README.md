# DataLens

**DataLens** is a privacy-first Android app that shows which apps are consuming your
**mobile/cellular data**, how much each one uses, download vs upload, historical
usage, data limits, alerts and usage trends — entirely on-device.

* No account, no backend, no cloud sync
* No analytics, no ads, no tracking
* The app does not even request the **INTERNET** permission — it physically cannot upload anything
* All statistics come from Android's own `NetworkStatsManager`; nothing is simulated

---

## Screens

| Tab | What it shows |
|---|---|
| **Overview** | Selected period, total mobile data, download/upload, top consumers with usage bars, hourly/daily chart, mobile-data summary (today / yesterday / 7 days / billing cycle) with honest comparisons, data-limit progress, pinned apps |
| **Apps** | Every app with mobile usage (and all launchable apps, zero-filled): search, sort (total/download/upload/name, asc/desc), user/system/hidden filters, zero-usage toggle, category filter |
| **App detail** | Large icon, package, UID, totals, download/upload, share of period, hourly/daily history chart, pin/hide, link to the system App Info screen |
| **Alerts** | Data-limit status, apps using unusually high data vs their recent 7-day average, apps dominating today's traffic, notification toggles |
| **Settings** | Appearance (system/light/dark + dynamic colors), data limits (allowance, billing-cycle start day, daily target, warning threshold), notifications, pinned/hidden app management, CSV/JSON export & sharing, privacy notes, about |

Supported periods: **Today · Yesterday · Last 7 days · Last 30 days · This billing
cycle · Previous billing cycle · Custom range** (Material date-range picker).
All boundaries use the device's local timezone.

---

## Architecture

MVVM with Kotlin, Jetpack Compose, Material 3, Coroutines + StateFlow, Navigation
Compose, Room, DataStore and WorkManager.

```
app/src/main/java/com/datalens/app/
├── DataLensApplication.kt      # channels + WorkManager scheduling
├── MainActivity.kt             # theme + navigation root
├── ServiceLocator.kt           # hand-rolled dependency graph (no DI framework)
├── data/
│   ├── network/NetworkStatsDataSource.kt   # NetworkStatsManager wrapper (mobile only)
│   ├── apps/AppInfoDataSource.kt           # UID → package → label/icon resolution + cache
│   ├── db/                                  # Room: pinned, hidden, limit config
│   ├── prefs/UserPreferencesDataSource.kt   # DataStore: theme, notifications, toggles
│   └── repository/                          # UsageRepository, SettingsRepository
├── domain/
│   ├── model/                                # ByteTotals, UsagePeriod, AppUsageInfo, …
│   └── usecase/                              # overview data, anomalies, limits, reports
├── notifications/NotificationHelper.kt
├── work/                                     # DailySummaryWorker, LimitCheckWorker
├── ui/
│   ├── navigation/ · theme/ · components/
│   ├── overview/ · apps/ · appdetail/ · alerts/ · settings/ · onboarding/
└── util/                                     # ByteFormatter, TimeUtils, UsageAccess, …
```

Unit tests live in `app/src/test/` and cover byte formatting, date/billing-cycle
math, period resolution, limit computation, anomaly detection and app filtering.

## How the data is obtained (real statistics, no mock values)

1. `NetworkStatsManager.querySummary(ConnectivityManager.TYPE_MOBILE, null, start, end)`
   returns **one aggregated bucket per UID** for the window — a single binder call per
   period, never one call per app.
2. Buckets are summed into `receivedBytes` (download) + `transmittedBytes` (upload) per UID.
3. UIDs are mapped to packages with `PackageManager` (`getPackagesForUid`), then to
   labels/icons where Android's package visibility allows it.
4. Chart series are built from **one exact summary query per hour/day boundary** —
   DataLens never interpolates or invents intra-bucket values.
5. Results are cached briefly (60 s) and shared across screens; explicit refresh
   invalidates everything. All queries run on `Dispatchers.IO`.

`subscriberId` is passed as `null`, which is the only way third-party apps can query
without `READ_PHONE_STATE`. Consequently usage is **aggregated across all mobile
subscriptions (SIMs)** — see *Limitations*.

## Permissions

| Permission | Type | Why |
|---|---|---|
| `android.permission.PACKAGE_USAGE_STATS` | Special app access ("Usage Access") | Required by Android to read per-app `NetworkStats`. Granted via **Settings → Apps → Special app access → Usage access**. DataLens guides you there on first launch and detects the grant automatically. |
| `android.permission.POST_NOTIFICATIONS` | Runtime (Android 13+) | Optional; only for the daily summary / limit warnings / high-usage alerts. The app works fully without it. |

DataLens deliberately does **not** request `INTERNET`, `READ_PHONE_STATE`,
`QUERY_ALL_PACKAGES` or any contacts/messages permissions.

## Usage Access setup

1. Install the APK and open DataLens.
2. The onboarding screen explains what Usage Access is used for.
3. Tap **Grant Usage Access** → Android opens the Usage Access settings.
4. Toggle DataLens to *Allowed*.
5. Return to DataLens — it re-checks automatically (polling + on resume) and
   continues to the dashboard.

If access is missing at any time, every screen shows a permission card instead of
fake data. A `SecurityException` from the API is treated as "permission missing".

## Building

Requirements: JDK 17 and Android SDK (platform 35 / build-tools). The Gradle
wrapper downloads Gradle 8.9 automatically.

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

./gradlew testDebugUnitTest   # unit tests
./gradlew assembleRelease     # release build (R8; debug-signed unless keystore.properties exists)
```

CI: `.github/workflows/build-apk.yml` builds, tests, verifies and publishes the
debug APK on every push to `main`/`arena/**` (see *Artifacts* below).

### Artifacts

* `app/build/outputs/apk/debug/app-debug.apk` — build output
* `releases/DataLens-debug.apk` — committed copy of the debug APK (built by CI)
* `releases/DataLens-debug.apk.sha256` — checksum

## Installing on a phone

```bash
adb install -r releases/DataLens-debug.apk
# or: adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.datalens.app 1   # launch
```

Without ADB: copy the APK to the phone, tap it in Files, and allow "Install unknown
apps" for that file manager when Android asks. The debug APK is signed with the
standard debug key, which Android accepts for sideloading.

* Package id: `com.datalens.app`
* minSdk 26 (Android 8.0) · targetSdk 35 (Android 15) · versionName 1.0.0

## Testing checklist

Verified by CI + unit tests: build, unit tests (formatting, cycle math, limits,
anomalies, filters), APK contents (dex/manifest/resources), package id and signing.

Verified on device/emulator (manual): onboarding → Usage Access grant → dashboard
shows real per-app mobile data → periods → search/sort/filters → app detail &
charts → pin/hide → limits & billing cycle → alerts → notifications → CSV/JSON
export → share sheet → light/dark/dynamic themes → empty & error states.

See the repo's `docs/screenshots/` (when present) for emulator captures.

## Privacy

* All statistics stay on the device; there is no network access (no INTERNET permission).
* No account, sign-in, cloud sync, telemetry or analytics SDK.
* No packet inspection, no message/password/website-content reading — DataLens only
  reads the byte counters Android itself records per app.
* Exports (CSV/JSON) are created only on request via the Storage Access Framework,
  and only leave the device if you explicitly share them.
* Notifications are optional and generated locally by WorkManager.

## Android limitations (honest list)

* **Not real-time monitoring.** DataLens reads Android's statistics; counters can lag
  actual traffic. It is a statistics dashboard, not a packet-level monitor.
* **Carrier billing differs.** Android's counters may differ slightly from your
  carrier's measurements. DataLens never claims billing-level precision.
* **Multi-SIM.** `querySummary` with `subscriberId = null` aggregates across all
  mobile subscriptions. Android does not expose reliable per-SIM usage to third-party
  apps, so DataLens does not pretend to split per SIM.
* **Package visibility.** DataLens uses a MAIN/LAUNCHER `<queries>` filter plus a few
  well-known system packages instead of `QUERY_ALL_PACKAGES`. UIDs that cannot be
  resolved (hidden apps, some system components, removed apps) are shown as their
  package name or as "Unknown / System process" — never mis-attributed.
* **Shared UIDs.** Some system components share a UID; their usage is reported for
  the UID as a whole and DataLens says so on the detail screen.
* **OEM quirks.** A few devices throw `SecurityException` even with Usage Access
  granted (subscriber-ID restrictions). DataLens shows an error state with a retry
  instead of crashing.
* **Chart granularity.** "Today"/single days use hourly buckets; longer periods use
  daily buckets. This matches what Android can report accurately.
* **Notification timing.** The daily summary is scheduled with WorkManager
  (≈21:00 local, once per day). Android may defer it in battery-saver situations.

## Known issues

* Very long custom ranges (multi-month) produce one binder call per day — the first
  chart load can take a moment (subsequent loads are cached).
* Apps hidden from the launcher and not in the curated `<queries>` list appear with
  package names only (no icon).
* If an app is uninstalled, Android may still report its historical usage under
  "Removed apps".

## License

MIT for this repository's own code. Built on AndroidX / Jetpack Compose
(Apache License 2.0).
