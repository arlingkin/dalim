package com.dalim.datalimit.core.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodRangeTest {

    @Test
    fun startOfDayIsMidnightOfToday() {
        val now = System.currentTimeMillis()
        assertEquals(LocalDate.now().toEpochDay() * PeriodRange.DAY_MILLIS, PeriodRange.startOfDayMillis(now))
    }

    @Test
    fun startOfWeekIsMonday() {
        val now = System.currentTimeMillis()
        val expected = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay() *
            PeriodRange.DAY_MILLIS
        assertEquals(expected, PeriodRange.startOfWeekMillis(now))
    }

    @Test
    fun startOfMonthIsFirst() {
        val now = System.currentTimeMillis()
        assertEquals(LocalDate.now().withDayOfMonth(1).toEpochDay() * PeriodRange.DAY_MILLIS, PeriodRange.startOfMonthMillis(now))
    }

    @Test
    fun dayConstant() {
        assertEquals(86_400_000L, PeriodRange.DAY_MILLIS)
    }
}