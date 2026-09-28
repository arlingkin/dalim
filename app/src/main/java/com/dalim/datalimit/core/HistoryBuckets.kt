package com.dalim.datalimit.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Pure bucket math for the usage-history series. No Android imports, so the
 * whole grid (boundaries, deltas, aggregation, zero fill) runs on the JVM
 * inside unit tests.
 */
object HistoryBuckets {

    enum class Granularity { DAILY, WEEKLY, MONTHLY }

    /** One stored row: daily totals keyed by the epoch day it was recorded on. */
    data class Row(val epochDay: Long, val rxBytes: Long, val txBytes: Long)

    /** One series bucket: totals for a single period (day/week/month). */
    data class Bucket(val epochDay: Long, val rxBytes: Long, val txBytes: Long)

    /**
     * Delta between two lifetime radio counters. Returns 0 when the device
     * rebooted (counters reset), so the monitor re-anchors instead of blowing
     * up history with a huge fake spike.
     */
    fun computeDelta(lastTotalBytes: Long, totalBytes: Long): Long =
        (totalBytes - lastTotalBytes).coerceAtLeast(0L)

    /** First epoch day belonging to the bucket that contains [epochDay]. */
    fun bucketStartEpochDay(granularity: Granularity, epochDay: Long): Long {
        val date = LocalDate.ofEpochDay(epochDay)
        return when (granularity) {
            Granularity.DAILY -> epochDay
            Granularity.WEEKLY ->
                date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
            Granularity.MONTHLY -> date.withDayOfMonth(1).toEpochDay()
        }
    }

    /** Ascending start-days of the last [count] buckets ending at [nowEpochDay]. */
    fun seriesEpochDays(granularity: Granularity, count: Int, nowEpochDay: Long): List<Long> {
        if (count <= 0) return emptyList()
        val today = bucketStartEpochDay(granularity, nowEpochDay)
        return when (granularity) {
            Granularity.DAILY -> (today - (count - 1)..today).toList()
            Granularity.WEEKLY -> (0 until count).map { today - it * 7L }.reversed()
            Granularity.MONTHLY -> (0 until count).map { shiftMonthStart(today, -it) }.reversed()
        }
    }

    private fun shiftMonthStart(startEpochDay: Long, months: Int): Long =
        LocalDate.ofEpochDay(startEpochDay).plusMonths(months.toLong()).toEpochDay()

    /**
     * Aggregates stored daily rows into a zero-filled, chronological series of
     * the last [count] periods ending at [nowEpochDay].
     */
    fun aggregate(
        granularity: Granularity,
        count: Int,
        nowEpochDay: Long,
        rows: List<Row>
    ): List<Bucket> {
        val starts = seriesEpochDays(granularity, count, nowEpochDay)
        val sums = HashMap<Long, Bucket>()
        for (row in rows) {
            val key = bucketStartEpochDay(granularity, row.epochDay)
            val current = sums[key]
            sums[key] = if (current == null) {
                Bucket(key, row.rxBytes, row.txBytes)
            } else {
                Bucket(key, current.rxBytes + row.rxBytes, current.txBytes + row.txBytes)
            }
        }
        return starts.map { sums[it] ?: Bucket(it, 0L, 0L) }
    }
}