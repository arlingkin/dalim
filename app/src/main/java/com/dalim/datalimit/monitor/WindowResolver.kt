package com.dalim.datalimit.monitor

import android.content.Context
import com.dalim.datalimit.R
import com.dalim.datalimit.core.Period
import com.dalim.datalimit.core.WindowStyle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object WindowResolver {

    fun windowKey(period: Period, style: WindowStyle, nowMillis: Long): Long = when (period) {
        Period.WEEKLY ->
            LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
        Period.MONTHLY ->
            LocalDate.now().withDayOfMonth(1).toEpochDay()
        Period.DAILY ->
            if (style == WindowStyle.FIXED) {
                LocalDate.now().toEpochDay()
            } else {
                rollingWindowKey(nowMillis)
            }
    }

    fun withRollingAnchor(anchorMillis: Long) {
        rollingAnchor = anchorMillis
    }

    private fun rollingWindowKey(nowMillis: Long): Long {
        val anchor = if (rollingAnchor > 0L) rollingAnchor else nowMillis
        return (nowMillis - anchor) / WINDOW_MILLIS
    }

    fun windowLabel(context: Context, period: Period, style: WindowStyle): String {
        val locale = Locale.getDefault()
        return when (period) {
            Period.DAILY -> if (style == WindowStyle.FIXED) context.getString(R.string.window_daily_fixed) else context.getString(R.string.window_rolling24)
            Period.WEEKLY -> context.getString(
                R.string.window_weekly,
                LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
            )
            Period.MONTHLY -> context.getString(
                R.string.window_monthly,
                LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ofPattern("MMM d", locale))
            )
        }
    }

    private const val WINDOW_MILLIS = 86_400_000L
    private var rollingAnchor: Long = -1L
}