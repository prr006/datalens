package com.datalens.app.domain.usecase

import com.datalens.app.data.repository.UsageRepository
import com.datalens.app.domain.model.AppUsageInfo
import com.datalens.app.domain.model.ReportData
import com.datalens.app.domain.model.UsagePeriod
import com.datalens.app.util.TimeUtils
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ReportFormat(val mimeType: String, val extension: String) {
    CSV("text/csv", "csv"),
    JSON("application/json", "json"),
}

/**
 * Builds a local usage report (CSV or JSON) from real NetworkStats data for the
 * given period. Reports are generated on-device and only leave the device if the
 * user explicitly exports/shares them.
 */
class BuildUsageReportUseCase(private val repository: UsageRepository) {

    suspend operator fun invoke(
        period: UsagePeriod,
        cycleStartDay: Int,
        format: ReportFormat,
        now: Long = System.currentTimeMillis(),
    ): ReportData {
        val range = period.resolveRange(cycleStartDay, now)
        val daily = repository.dailyAppUsage(range)
        val dateStamp = DateTimeFormatter.ofPattern("yyyyMMdd").format(TimeUtils.localDateOf(now))

        return when (format) {
            ReportFormat.CSV -> ReportData(
                suggestedFileName = "datalens-usage-${period.id}-$dateStamp.csv",
                mimeType = format.mimeType,
                bytes = buildUsageCsv(daily).toByteArray(Charsets.UTF_8),
            )
            ReportFormat.JSON -> ReportData(
                suggestedFileName = "datalens-usage-${period.id}-$dateStamp.json",
                mimeType = format.mimeType,
                bytes = buildJson(period, range, daily, now).toByteArray(Charsets.UTF_8),
            )
        }
    }

    private fun buildJson(
        period: UsagePeriod,
        range: com.datalens.app.domain.model.DateRange,
        daily: List<Pair<LocalDate, List<AppUsageInfo>>>,
        now: Long,
    ): String {
        val root = JSONObject()
        root.put("app", "DataLens")
        root.put("generatedAt", TimeUtils.isoDate(now) + "T" +
            java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString())
        root.put("dataSource", "Android NetworkStatsManager (mobile network only)")

        val periodJson = JSONObject()
        periodJson.put("id", period.id)
        periodJson.put("label", period.displayName())
        periodJson.put("startMs", range.start)
        periodJson.put("endMs", range.end)
        periodJson.put("startIso", TimeUtils.isoDate(range.start))
        periodJson.put("endIso", TimeUtils.isoDate(range.end - 1))
        root.put("period", periodJson)

        var totalRx = 0L
        var totalTx = 0L
        val daysArray = JSONArray()
        for ((date, apps) in daily) {
            val dayJson = JSONObject()
            dayJson.put("date", date.toString())
            val appsArray = JSONArray()
            var dayRx = 0L
            var dayTx = 0L
            for (app in apps) {
                val appJson = JSONObject()
                appJson.put("app", app.appName)
                appJson.put("package", app.packageName)
                appJson.put("uid", app.uid)
                appJson.put("downloadBytes", app.receivedBytes)
                appJson.put("uploadBytes", app.transmittedBytes)
                appJson.put("totalBytes", app.totalBytes)
                appsArray.put(appJson)
                dayRx += app.receivedBytes
                dayTx += app.transmittedBytes
            }
            dayJson.put("apps", appsArray)
            dayJson.put("dayDownloadBytes", dayRx)
            dayJson.put("dayUploadBytes", dayTx)
            dayJson.put("dayTotalBytes", dayRx + dayTx)
            daysArray.put(dayJson)
            totalRx += dayRx
            totalTx += dayTx
        }
        root.put("days", daysArray)

        val totals = JSONObject()
        totals.put("downloadBytes", totalRx)
        totals.put("uploadBytes", totalTx)
        totals.put("totalBytes", totalRx + totalTx)
        root.put("totals", totals)

        return root.toString(2)
    }
}

/**
 * Renders per-day/per-app usage as CSV with the columns
 * Date, App, Package, Download, Upload, Total.
 *
 * Top-level and internal so it can be unit-tested on the JVM (org.json is
 * stubbed in JVM unit tests, so only the CSV builder is covered there).
 */
internal fun buildUsageCsv(daily: List<Pair<LocalDate, List<AppUsageInfo>>>): String {
    val sb = StringBuilder()
    sb.append("Date,App,Package,Download,Upload,Total\n")
    for ((date, apps) in daily) {
        for (app in apps) {
            sb.append(csvField(date.toString()))
                .append(',')
                .append(csvField(app.appName))
                .append(',')
                .append(csvField(app.packageName))
                .append(',')
                .append(app.receivedBytes)
                .append(',')
                .append(app.transmittedBytes)
                .append(',')
                .append(app.totalBytes)
                .append('\n')
        }
    }
    return sb.toString()
}

/** Escapes a CSV field (RFC 4180-style quoting). */
internal fun csvField(field: String): String =
    if (field.contains(',') || field.contains('"') || field.contains('\n')) {
        "\"" + field.replace("\"", "\"\"") + "\""
    } else {
        field
    }
