package com.datalens.app

import com.datalens.app.util.ByteFormatter
import com.datalens.app.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatterTest {

    private val KB = 1024L
    private val MB = 1024L * 1024L
    private val GB = 1024L * 1024L * 1024L

    @Test
    fun `zero and negative values render safely`() {
        assertEquals("0 B", ByteFormatter.format(0))
        assertEquals("0 B", ByteFormatter.format(-5))
        assertEquals("0 B", ByteFormatter.format(Long.MIN_VALUE))
    }

    @Test
    fun `bytes render without units conversion`() {
        assertEquals("1 B", ByteFormatter.format(1))
        assertEquals("500 B", ByteFormatter.format(500))
        assertEquals("1023 B", ByteFormatter.format(1023))
    }

    @Test
    fun `kilobytes render with one decimal`() {
        assertEquals("1.0 KB", ByteFormatter.format(KB))
        assertEquals("4.2 KB", ByteFormatter.format(4_304))
    }

    @Test
    fun `megabytes follow spec examples`() {
        assertEquals("12.7 MB", ByteFormatter.format(13_316_966))
        assertEquals("843 MB", ByteFormatter.format(843 * MB))
        assertEquals("391 MB", ByteFormatter.format(391 * MB))
        assertEquals("99.9 MB", ByteFormatter.format(104_752_742))
        assertEquals("100 MB", ByteFormatter.format(104_806_400))
    }

    @Test
    fun `gigabytes follow spec examples`() {
        assertEquals("1.42 GB", ByteFormatter.format(1_524_552_484))
        assertEquals("8.72 GB", ByteFormatter.format(9_362_001_068))
        assertEquals("23.4 GB", ByteFormatter.format(25_125_558_682))
        assertEquals("512 GB", ByteFormatter.format(512 * GB))
    }

    @Test
    fun `terabytes render for huge values`() {
        assertEquals("1.50 TB", ByteFormatter.format(1_649_267_441_664))
    }

    @Test
    fun `parseQuantityToBytes handles input`() {
        assertEquals(GB, ByteFormatter.parseQuantityToBytes("1", GB))
        assertEquals(20 * GB, ByteFormatter.parseQuantityToBytes("20", GB))
        assertEquals(1_500_000_000L, ByteFormatter.parseQuantityToBytes("1.5", 1_000_000_000L))
        assertEquals(512 * MB, ByteFormatter.parseQuantityToBytes("512", MB))
        assertEquals(null, ByteFormatter.parseQuantityToBytes("", MB))
        assertEquals(null, ByteFormatter.parseQuantityToBytes("abc", MB))
        assertEquals(null, ByteFormatter.parseQuantityToBytes("-4", MB))
    }

    @Test
    fun `bytesToUnitString round trips`() {
        assertEquals("20", ByteFormatter.bytesToUnitString(20 * GB, GB))
        assertEquals("1.5", ByteFormatter.bytesToUnitString(1_500_000_000L, 1_000_000_000L))
    }

    @Test
    fun `percent formatting`() {
        assertEquals("43.6%", Formatters.percent(0.436))
        assertEquals("38%", Formatters.percent(0.38))
        assertEquals("8.1%", Formatters.percent(0.081))
        assertEquals("100%", Formatters.percent(1.0))
    }

    @Test
    fun `ratio formatting`() {
        assertEquals("3.1×", Formatters.ratio(3.14))
        assertEquals("2.0×", Formatters.ratio(2.0))
    }

    @Test
    fun `signed percent formatting`() {
        assertEquals("+32%", Formatters.signedPercent(0.32))
        assertEquals("-12%", Formatters.signedPercent(-0.12))
        assertEquals("+8.5%", Formatters.signedPercent(0.085))
    }
}
