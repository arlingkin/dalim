package com.dalim.datalimit.core.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object PeriodRange {

    const val DAY_MILLIS = 86_400_000L

    fun startOfDayMillis(nowMillis: Long): Long =
        LocalDate.now().toEpochDay() * DAY_MILLIS

    fun startOfWeekMillis(nowMillis: Long): Long =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay() * DAY_MILLIS

    fun startOfMonthMillis(nowMillis: Long): Long =
        LocalDate.now().withDayOfMonth(1).toEpochDay() * DAY_MILLIS
}