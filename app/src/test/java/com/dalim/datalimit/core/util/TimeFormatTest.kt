package com.dalim.datalimit.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeFormatTest {

    @Test
    fun durationShort_hoursAndMinutes() {
        assertEquals("5h 42m", TimeFormat.durationShort(342))
    }

    @Test
    fun durationShort_minutesOnly() {
        assertEquals("45m", TimeFormat.durationShort(45))
        assertEquals("1h 0m", TimeFormat.durationShort(60))
    }

    @Test
    fun durationShort_negativeUnknown() {
        assertEquals("—", TimeFormat.durationShort(-1))
    }

    @Test
    fun drainPerHourFormulas() {
        assertEquals(8.0, TimeFormat.drainPerHour(8, 60), 0.001)
        assertEquals(4.0, TimeFormat.drainPerHour(4, 60), 0.001)
    }

    @Test
    fun drainPerHour_invalidElapsedIsNaN() {
        assertTrue(TimeFormat.drainPerHour(4, 0).isNaN())
        assertTrue(TimeFormat.drainPerHour(4, -5).isNaN())
    }

    @Test
    fun estimateRemainingHours() {
        // 67% at 8%/hour → ~8.4h
        assertEquals(8.375, TimeFormat.estimateRemainingHours(67, 8.0), 0.001)
    }

    @Test
    fun estimateRemainingHours_unavailableWhenNoDrain() {
        assertTrue(TimeFormat.estimateRemainingHours(67, 0.0).isNaN())
        assertTrue(TimeFormat.estimateRemainingHours(67, Double.NaN).isNaN())
    }
}