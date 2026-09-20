package com.dalim.datalimit.monitor

import com.dalim.datalimit.core.Period
import com.dalim.datalimit.core.WindowStyle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowResolverTest {

    private val dayMillis = 86_400_000L

    @Test
    fun dailyFixedKey_isTodayDayNumber() {
        val now = System.currentTimeMillis()
        assertEquals(LocalDate.now().toEpochDay(), WindowResolver.windowKey(Period.DAILY, WindowStyle.FIXED, now))
    }

    @Test
    fun weeklyKey_isMondayBoundary() {
        val now = System.currentTimeMillis()
        val expected = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
        assertEquals(expected, WindowResolver.windowKey(Period.WEEKLY, WindowStyle.FIXED, now))
    }

    @Test
    fun monthlyKey_isFirstOfMonth() {
        val now = System.currentTimeMillis()
        val expected = LocalDate.now().withDayOfMonth(1).toEpochDay()
        assertEquals(expected, WindowResolver.windowKey(Period.MONTHLY, WindowStyle.FIXED, now))
    }

    @Test
    fun rollingKey_countsFullDaysSinceAnchor() {
        WindowResolver.withRollingAnchor(1_000_000L)
        assertEquals(0L, WindowResolver.windowKey(Period.DAILY, WindowStyle.ROLLING, 1_000_000L))
        assertEquals(0L, WindowResolver.windowKey(Period.DAILY, WindowStyle.ROLLING, 1_000_000L + dayMillis - 1))
        assertEquals(1L, WindowResolver.windowKey(Period.DAILY, WindowStyle.ROLLING, 1_000_000L + dayMillis))
        assertEquals(2L, WindowResolver.windowKey(Period.DAILY, WindowStyle.ROLLING, 1_000_000L + 2 * dayMillis))
    }

    @Test
    fun rollingKey_zeroAnchorFallsBackToNow() {
        // With no stored anchor the resolver anchors at the current instant,
        // so the key stays 0 until a full window elapses.
        WindowResolver.withRollingAnchor(0L)
        val now = 5_000_000L
        assertEquals(0L, WindowResolver.windowKey(Period.DAILY, WindowStyle.ROLLING, now))
        assertEquals(0L, WindowResolver.windowKey(Period.DAILY, WindowStyle.ROLLING, now + dayMillis - 1))
    }
}