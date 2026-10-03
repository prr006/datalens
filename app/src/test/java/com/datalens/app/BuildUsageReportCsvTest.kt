package com.datalens.app

import com.datalens.app.domain.model.AppCategory
import com.datalens.app.domain.model.AppUsageInfo
import com.datalens.app.domain.usecase.buildUsageCsv
import com.datalens.app.domain.usecase.csvField
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The CSV export must contain one row per app per day with the real measured
 * byte counts, and must quote fields that contain commas or quotes.
 */
class BuildUsageReportCsvTest {

    private fun app(name: String, pkg: String, rx: Long, tx: Long) = AppUsageInfo(
        uid = 10042,
        packageName = pkg,
        appName = name,
        icon = null,
        isSystem = false,
        category = AppCategory.OTHER,
        receivedBytes = rx,
        transmittedBytes = tx,
    )

    @Test
    fun `header plus one row per app per day`() {
        val daily = listOf(
            LocalDate.of(2025, 9, 18) to listOf(
                app("YouTube", "com.google.android.youtube", 1000, 100),
                app("Chrome", "com.android.chrome", 500, 50),
            ),
            LocalDate.of(2025, 9, 19) to listOf(
                app("YouTube", "com.google.android.youtube", 2000, 200),
            ),
        )
        val csv = buildUsageCsv(daily)
        val lines = csv.trim().lines()
        assertEquals(4, lines.size)
        assertEquals("Date,App,Package,Download,Upload,Total", lines[0])
        assertEquals("2025-09-18,YouTube,com.google.android.youtube,1000,100,1100", lines[1])
        assertEquals("2025-09-18,Chrome,com.android.chrome,500,50,550", lines[2])
        assertEquals("2025-09-19,YouTube,com.google.android.youtube,2000,200,2200", lines[3])
    }

    @Test
    fun `zero usage days export real zeros`() {
        val csv = buildUsageCsv(
            listOf(LocalDate.of(2025, 9, 18) to listOf(app("Idle", "com.idle", 0, 0))),
        )
        assertEquals("2025-09-18,Idle,com.idle,0,0,0", csv.trim().lines()[1])
    }

    @Test
    fun `empty usage exports header only`() {
        assertEquals("Date,App,Package,Download,Upload,Total\n", buildUsageCsv(emptyList()))
    }

    @Test
    fun `fields with commas and quotes are escaped`() {
        assertEquals("plain", csvField("plain"))
        assertEquals("\"a,b\"", csvField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", csvField("say \"hi\""))
        assertEquals("\"line1\nline2\"", csvField("line1\nline2"))
    }

    @Test
    fun `app names with commas are quoted in the csv`() {
        val csv = buildUsageCsv(
            listOf(LocalDate.of(2025, 9, 18) to listOf(app("My App, Inc", "com.myapp", 10, 5))),
        )
        assertEquals("2025-09-18,\"My App, Inc\",com.myapp,10,5,15", csv.trim().lines()[1])
    }
}
