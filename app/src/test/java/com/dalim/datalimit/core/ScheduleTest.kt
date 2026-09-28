package com.dalim.datalimit.core

import com.dalim.datalimit.core.Schedule.WindowStyle
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTest {

    private val utc = ZoneId.of("UTC")

    /** 2026-09-28 is a Monday, 2026-09-26 is a Saturday. */
    private val monday3am = Instant.parse("2026-09-28T03:00:00Z").toEpochMilli()
    private val saturday3am = Instant.parse("2026-09-26T03:00:00Z").toEpochMilli()

    private val threeAm = 3 * 60
    private val noon = 12 * 60
    private val workStart = 9 * 60
    private val workEnd = 17 * 60
    private val nightStart = 22 * 60
    private val nightEnd = 6 * 60

    @Test
    fun styleFrom_mapsCodesAndFallsBackToOff() {
        assertEquals(WindowStyle.OFF, WindowStyle.from("off"))
        assertEquals(WindowStyle.WEEKDAYS, WindowStyle.from("weekdays"))
        assertEquals(WindowStyle.WEEKEND, WindowStyle.from("weekend"))
        assertEquals(WindowStyle.EVERYDAY, WindowStyle.from("everyday"))
        assertEquals(WindowStyle.CUSTOM, WindowStyle.from("custom"))
        assertEquals(WindowStyle.OFF, WindowStyle.from(null))
        assertEquals(WindowStyle.OFF, WindowStyle.from("nonsense"))
    }

    @Test
    fun minutesPerDayConstant() {
        assertEquals(1440, Schedule.MINUTES_PER_DAY)
    }

    @Test
    fun inRange_dayWindowIncludesBothEdges() {
        assertTrue(Schedule.inRange(workStart, workStart, workEnd))
        assertTrue(Schedule.inRange(workEnd, workStart, workEnd))
        assertTrue(Schedule.inRange(noon, workStart, workEnd))
        assertFalse(Schedule.inRange(workStart - 1, workStart, workEnd))
        assertFalse(Schedule.inRange(workEnd + 1, workStart, workEnd))
        assertFalse(Schedule.inRange(threeAm, workStart, workEnd))
    }

    @Test
    fun inRange_overnightWindowWrapsMidnight() {
        assertTrue(Schedule.inRange(23 * 60, nightStart, nightEnd))
        assertTrue(Schedule.inRange(5 * 60, nightStart, nightEnd))
        assertTrue(Schedule.inRange(6 * 60, nightStart, nightEnd))
        assertTrue(Schedule.inRange(22 * 60, nightStart, nightEnd))
        assertTrue(Schedule.inRange(0, nightStart, nightEnd))
        assertFalse(Schedule.inRange(noon, nightStart, nightEnd))
        assertFalse(Schedule.inRange(nightEnd + 1, nightStart, nightEnd))
        assertFalse(Schedule.inRange(nightStart - 1, nightStart, nightEnd))
    }

    @Test
    fun clockHelpers_useTheGivenZone() {
        assertEquals(DayOfWeek.MONDAY, Schedule.dayOfWeek(monday3am, utc))
        assertEquals(DayOfWeek.SATURDAY, Schedule.dayOfWeek(saturday3am, utc))
        assertEquals(threeAm, Schedule.minutesOfDay(monday3am, utc))
        assertEquals(threeAm, Schedule.minutesOfDay(saturday3am, utc))
    }

    @Test
    fun outsideSchedule_offIsNeverOutside() {
        assertFalse(Schedule.outsideSchedule(WindowStyle.OFF, workStart, workEnd, monday3am, threeAm, utc))
        assertFalse(Schedule.outsideSchedule(WindowStyle.OFF, workStart, workEnd, saturday3am, threeAm, utc))
    }

    @Test
    fun outsideSchedule_weekdaysAllowsWeekendsUnconditionally() {
        assertFalse(Schedule.outsideSchedule(WindowStyle.WEEKDAYS, workStart, workEnd, saturday3am, threeAm, utc))
        assertFalse(Schedule.outsideSchedule(WindowStyle.WEEKDAYS, nightStart, nightEnd, saturday3am, noon, utc))
    }

    @Test
    fun outsideSchedule_weekdaysRestrictsOnWeekdays() {
        assertTrue(Schedule.outsideSchedule(WindowStyle.WEEKDAYS, workStart, workEnd, monday3am, threeAm, utc))
        assertTrue(Schedule.outsideSchedule(WindowStyle.WEEKDAYS, nightStart, nightEnd, monday3am, noon, utc))
        assertFalse(Schedule.outsideSchedule(WindowStyle.WEEKDAYS, workStart, workEnd, monday3am, noon, utc))
        assertFalse(Schedule.outsideSchedule(WindowStyle.WEEKDAYS, nightStart, nightEnd, monday3am, 23 * 60, utc))
    }

    @Test
    fun outsideSchedule_weekendRestrictsOnlyOnWeekends() {
        assertTrue(Schedule.outsideSchedule(WindowStyle.WEEKEND, workStart, workEnd, saturday3am, threeAm, utc))
        assertFalse(Schedule.outsideSchedule(WindowStyle.WEEKEND, workStart, workEnd, saturday3am, noon, utc))
        assertFalse(Schedule.outsideSchedule(WindowStyle.WEEKEND, workStart, workEnd, monday3am, threeAm, utc))
        assertFalse(Schedule.outsideSchedule(WindowStyle.WEEKEND, workStart, workEnd, monday3am, noon, utc))
    }

    @Test
    fun outsideSchedule_everydayAndCustomRestrictEveryDay() {
        for (style in listOf(WindowStyle.EVERYDAY, WindowStyle.CUSTOM)) {
            assertTrue(Schedule.outsideSchedule(style, workStart, workEnd, monday3am, threeAm, utc))
            assertTrue(Schedule.outsideSchedule(style, workStart, workEnd, saturday3am, threeAm, utc))
            assertFalse(Schedule.outsideSchedule(style, workStart, workEnd, monday3am, noon, utc))
            assertFalse(Schedule.outsideSchedule(style, workStart, workEnd, saturday3am, noon, utc))
        }
    }

    @Test
    fun outsideSchedule_derivesMinutesFromTheInstantWhenOmitted() {
        assertTrue(Schedule.outsideSchedule(WindowStyle.EVERYDAY, workStart, workEnd, monday3am, zone = utc))
        assertFalse(Schedule.outsideSchedule(WindowStyle.EVERYDAY, nightStart, nightEnd, monday3am, zone = utc))
    }

    @Test
    fun nextAllowedMinute_insideWindowKeepsNow() {
        assertEquals(noon, Schedule.nextAllowedMinute(noon, workStart, workEnd))
        assertEquals(workStart, Schedule.nextAllowedMinute(workStart, workStart, workEnd))
        assertEquals(workEnd, Schedule.nextAllowedMinute(workEnd, workStart, workEnd))
        assertEquals(5 * 60, Schedule.nextAllowedMinute(5 * 60, nightStart, nightEnd))
    }

    @Test
    fun nextAllowedMinute_overnightOpensSameDay() {
        assertEquals(nightStart, Schedule.nextAllowedMinute(noon, nightStart, nightEnd))
        assertEquals(nightStart, Schedule.nextAllowedMinute(nightStart - 1, nightStart, nightEnd))
        assertEquals(nightStart, Schedule.nextAllowedMinute(nightEnd + 1, nightStart, nightEnd))
        assertEquals(3 * 60, Schedule.nextAllowedMinute(3 * 60, nightStart, nightEnd))
    }

    @Test
    fun nextAllowedMinute_dayWindowAfterEndRollsOverADay() {
        assertEquals(workStart + Schedule.MINUTES_PER_DAY, Schedule.nextAllowedMinute(workEnd + 1, workStart, workEnd))
        assertEquals(workStart + Schedule.MINUTES_PER_DAY, Schedule.nextAllowedMinute(23 * 60, workStart, workEnd))
    }

    @Test
    fun nextAllowedMinute_dayWindowBeforeStartOpensSameDay() {
        assertEquals(workStart, Schedule.nextAllowedMinute(workStart - 1, workStart, workEnd))
        assertEquals(workStart, Schedule.nextAllowedMinute(threeAm, workStart, workEnd))
    }
}
