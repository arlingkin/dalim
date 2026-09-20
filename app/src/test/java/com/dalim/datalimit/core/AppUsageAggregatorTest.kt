package com.dalim.datalimit.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUsageAggregatorTest {

    @Test
    fun mergesPackagesAndSumsRxTx() {
        val totals = listOf(
            PackageTotal("com.tiktok", rxBytes = 800 * 1024L * 1024, txBytes = 440 * 1024L * 1024),
            PackageTotal("com.tiktok", rxBytes = 100 * 1024L * 1024, txBytes = 60 * 1024L * 1024),
            PackageTotal("com.youtube", rxBytes = 700 * 1024L * 1024, txBytes = 142 * 1024L * 1024)
        )
        val out = AppUsageAggregator.aggregate(totals)
        assertEquals(2, out.size)
        val tiktok = out.first { it.packageName == "com.tiktok" }
        assertEquals(1400L * 1024 * 1024, tiktok.totalBytes)
        assertEquals(900L * 1024 * 1024, tiktok.rxBytes)
        assertEquals(500L * 1024 * 1024, tiktok.txBytes)
    }

    @Test
    fun sortsByTotalDescending() {
        val totals = listOf(
            PackageTotal("com.a", 0L, 10L),
            PackageTotal("com.b", 500L, 0L),
            PackageTotal("com.c", 100L, 100L)
        )
        val out = AppUsageAggregator.aggregate(totals).map { it.packageName }
        assertEquals(listOf("com.b", "com.c", "com.a"), out)
    }

    @Test
    fun emptyInputIsEmpty() {
        assertEquals(0, AppUsageAggregator.aggregate(emptyList()).size)
    }

    @Test
    fun withLabelsMapsEveryEntry() {
        val totals = listOf(PackageTotal("com.tiktok", 100L, 50L))
        val labeled = AppUsageAggregator.withLabels(
            AppUsageAggregator.aggregate(totals),
            labelFor = { if (it == "com.tiktok") "TikTok" else it }
        )
        assertEquals("TikTok", labeled.first().appLabel)
    }
}