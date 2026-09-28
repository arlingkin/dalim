package com.dalim.datalimit.core

import com.dalim.datalimit.core.HistoryBuckets.Bucket
import com.dalim.datalimit.core.HistoryBuckets.Granularity
import com.dalim.datalimit.core.HistoryBuckets.Row
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryBucketsTest {

    private fun day(iso: String): Long = LocalDate.parse(iso).toEpochDay()

    /** 2026-09-28 is a Monday. */
    private val monday = day("2026-09-28")
    private val tuesday = day("2026-09-22")
    private val saturday = day("2026-09-26")
    private val sunday = day("2026-09-27")
    private val september1 = day("2026-09-01")

    @Test
    fun computeDelta_returnsUsageSinceLastSample() {
        assertEquals(1_500L, HistoryBuckets.computeDelta(1_000L, 2_500L))
        assertEquals(1L, HistoryBuckets.computeDelta(0L, 1L))
    }

    @Test
    fun computeDelta_exactReuseIsZero() {
        assertEquals(0L, HistoryBuckets.computeDelta(0L, 0L))
        assertEquals(0L, HistoryBuckets.computeDelta(7L, 7L))
        assertEquals(0L, HistoryBuckets.computeDelta(4_000_000_000L, 4_000_000_000L))
    }

    @Test
    fun computeDelta_rebootResetIsZero() {
        assertEquals(0L, HistoryBuckets.computeDelta(4_000_000_000L, 12L))
        assertEquals(0L, HistoryBuckets.computeDelta(9_000L, 8_999L))
    }

    @Test
    fun bucketStart_dailyKeepsExactDay() {
        assertEquals(monday, HistoryBuckets.bucketStartEpochDay(Granularity.DAILY, monday))
        assertEquals(saturday, HistoryBuckets.bucketStartEpochDay(Granularity.DAILY, saturday))
    }

    @Test
    fun bucketStart_weeklyIsMondayOfThatWeek() {
        assertEquals(monday, HistoryBuckets.bucketStartEpochDay(Granularity.WEEKLY, monday))
        assertEquals(day("2026-09-21"), HistoryBuckets.bucketStartEpochDay(Granularity.WEEKLY, tuesday))
        assertEquals(day("2026-09-21"), HistoryBuckets.bucketStartEpochDay(Granularity.WEEKLY, saturday))
        assertEquals(day("2026-09-21"), HistoryBuckets.bucketStartEpochDay(Granularity.WEEKLY, sunday))
        assertEquals(monday, HistoryBuckets.bucketStartEpochDay(Granularity.WEEKLY, day("2026-10-04")))
        assertEquals(day("2026-10-05"), HistoryBuckets.bucketStartEpochDay(Granularity.WEEKLY, day("2026-10-05")))
    }

    @Test
    fun bucketStart_monthlyIsFirstOfMonth() {
        assertEquals(september1, HistoryBuckets.bucketStartEpochDay(Granularity.MONTHLY, monday))
        assertEquals(september1, HistoryBuckets.bucketStartEpochDay(Granularity.MONTHLY, september1))
        assertEquals(day("2026-08-01"), HistoryBuckets.bucketStartEpochDay(Granularity.MONTHLY, day("2026-08-31")))
    }

    @Test
    fun series_dailyIsAscendingAndEndsAtNow() {
        val days = HistoryBuckets.seriesEpochDays(Granularity.DAILY, 12, monday)
        assertEquals(12, days.size)
        assertEquals(monday, days.last())
        assertEquals(monday - 11, days.first())
        assertEquals((monday - 11..monday).toList(), days)
    }

    @Test
    fun series_weeklyEndsAtMondayOfCurrentWeek() {
        val fromMonday = HistoryBuckets.seriesEpochDays(Granularity.WEEKLY, 12, monday)
        assertEquals(12, fromMonday.size)
        assertEquals(monday, fromMonday.last())
        assertEquals(monday - 11 * 7, fromMonday.first())
        assertEquals(fromMonday.sorted(), fromMonday)

        val prevMonday = day("2026-09-21")
        val fromTuesday = HistoryBuckets.seriesEpochDays(Granularity.WEEKLY, 12, tuesday)
        assertEquals(prevMonday, fromTuesday.last())
        assertEquals(prevMonday - 11 * 7, fromTuesday.first())
        assertEquals(fromTuesday, HistoryBuckets.seriesEpochDays(Granularity.WEEKLY, 12, saturday))
        assertEquals(fromTuesday, HistoryBuckets.seriesEpochDays(Granularity.WEEKLY, 12, sunday))
    }

    @Test
    fun series_monthlyEndsAtFirstOfMonth() {
        val months = HistoryBuckets.seriesEpochDays(Granularity.MONTHLY, 12, monday)
        assertEquals(12, months.size)
        assertEquals(september1, months.last())
        assertEquals(day("2025-10-01"), months.first())
        assertEquals(months.sorted(), months)
    }

    @Test
    fun series_nonPositiveCountIsEmpty() {
        assertEquals(emptyList<Long>(), HistoryBuckets.seriesEpochDays(Granularity.DAILY, 0, monday))
        assertEquals(emptyList<Long>(), HistoryBuckets.seriesEpochDays(Granularity.WEEKLY, -1, monday))
    }

    @Test
    fun aggregate_emptyRowsYieldsZeroFilledSeries() {
        val buckets = HistoryBuckets.aggregate(Granularity.DAILY, 5, monday, emptyList())
        assertEquals(5, buckets.size)
        assertEquals((monday - 4..monday).toList(), buckets.map { it.epochDay })
        assertTrue(buckets.all { it.rxBytes == 0L && it.txBytes == 0L })
    }

    @Test
    fun aggregate_dailySumsRowsAndZeroFillsGaps() {
        val rows = listOf(
            Row(monday - 4, 10L, 1L),
            Row(monday - 4, 5L, 2L),
            Row(monday - 3, 20L, 3L),
            Row(monday, 40L, 4L)
        )
        val buckets = HistoryBuckets.aggregate(Granularity.DAILY, 5, monday, rows)
        assertEquals(5, buckets.size)
        assertEquals((monday - 4..monday).toList(), buckets.map { it.epochDay })
        assertEquals(listOf(15L, 20L, 0L, 0L, 40L), buckets.map { it.rxBytes })
        assertEquals(listOf(3L, 3L, 0L, 0L, 4L), buckets.map { it.txBytes })
        assertEquals(Bucket(monday - 4, 15L, 3L), buckets.first())
    }

    @Test
    fun aggregate_weeklyCollapsesDaysIntoMondayBuckets() {
        val rows = listOf(
            Row(tuesday, 5L, 1L),
            Row(saturday, 7L, 2L),
            Row(day("2026-08-31"), 999L, 999L)
        )
        val buckets = HistoryBuckets.aggregate(Granularity.WEEKLY, 3, monday, rows)
        assertEquals(listOf(monday - 14, monday - 7, monday), buckets.map { it.epochDay })
        assertEquals(listOf(0L, 12L, 0L), buckets.map { it.rxBytes })
        assertEquals(listOf(0L, 3L, 0L), buckets.map { it.txBytes })
    }

    @Test
    fun aggregate_monthlySumsTheWholeMonth() {
        val rows = listOf(
            Row(september1, 1L, 10L),
            Row(monday, 2L, 20L),
            Row(day("2026-08-10"), 3L, 30L)
        )
        val buckets = HistoryBuckets.aggregate(Granularity.MONTHLY, 3, monday, rows)
        assertEquals(listOf(day("2026-07-01"), day("2026-08-01"), september1), buckets.map { it.epochDay })
        assertEquals(listOf(0L, 3L, 3L), buckets.map { it.rxBytes })
        assertEquals(listOf(0L, 30L, 30L), buckets.map { it.txBytes })
    }

    @Test
    fun aggregate_ignoresRowsOutsideTheWindow() {
        val rows = listOf(Row(monday - 5, 1_000L, 1_000L), Row(monday, 7L, 8L))
        val buckets = HistoryBuckets.aggregate(Granularity.DAILY, 3, monday, rows)
        assertEquals(listOf(0L, 0L, 7L), buckets.map { it.rxBytes })
    }
}
