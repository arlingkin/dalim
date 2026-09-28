package com.dalim.datalimit.core

import java.time.DayOfWeek
import java.time.ZoneId

/**
 * Pure scheduling window for "when data may be used". No Android imports so
 * the whole grid is unit-testable on the JVM.
 *
 * Semantics: a window is "active" when the configured time range is *inside*
 * the allowed interval for the current day. Weekends are unconditional allow
 * for the `weekdays` preset ("weekend unlimited"); `weekend` restricts only
 * weekends; `everyday`/`custom` restrict every day.
 */
object Schedule {

    enum class WindowStyle(val code: String) {
        OFF("off"),
        WEEKDAYS("weekdays"),
        WEEKEND("weekend"),
        EVERYDAY("everyday"),
        CUSTOM("custom");

        companion object {
            fun from(code: String?): WindowStyle =
                entries.firstOrNull { it.code == code } ?: OFF
        }
    }

    const val MINUTES_PER_DAY = 1440

    /** True while [minutes] sits inside [start]..[end], handling overnight wrap. */
    fun inRange(minutes: Int, start: Int, end: Int): Boolean {
        val s = start.mod(MINUTES_PER_DAY)
        val e = end.mod(MINUTES_PER_DAY)
        return if (s <= e) minutes in s..e else minutes >= s || minutes <= e
    }

    /** Current day-of-week for `now` in the device-local zone. */
    fun dayOfWeek(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): DayOfWeek =
        java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().dayOfWeek

    private fun isWeekend(day: DayOfWeek): Boolean =
        day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY

    /**
     * True when `now` falls outside the configured window and the schedule is
     * not [WindowStyle.OFF]. [minutesOfDay] lets callers test a fixed instant.
     */
    fun outsideSchedule(
        style: WindowStyle,
        startMin: Int,
        endMin: Int,
        nowMillis: Long,
        minutesOfDay: Int = minutesOfDay(nowMillis),
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (style == WindowStyle.OFF) return false
        val weekend = isWeekend(dayOfWeek(nowMillis, zone))
        val relevant = when (style) {
            WindowStyle.WEEKDAYS -> !weekend
            WindowStyle.WEEKEND -> weekend
            else -> true
        }
        return relevant && !inRange(minutesOfDay, startMin, endMin)
    }

    fun minutesOfDay(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalTime().toSecondOfDay() / 60

    /**
     * Next minute-of-day (same day or +1440) at which the time window opens for
     * a given [startMin]/[endMin]; returns [nowMin] when already inside the
     * window. Used by the schedule-gate snooze to resume exactly when data is
     * allowed again.
     */
    fun nextAllowedMinute(nowMin: Int, startMin: Int, endMin: Int): Int {
        val s = startMin.mod(MINUTES_PER_DAY)
        val e = endMin.mod(MINUTES_PER_DAY)
        if (inRange(nowMin, s, e)) return nowMin
        if (s > e) return s
        return if (nowMin < s) s else s + MINUTES_PER_DAY
    }
}