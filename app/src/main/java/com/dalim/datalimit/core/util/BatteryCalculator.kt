package com.dalim.datalimit.core.util

import kotlin.math.roundToInt

object BatteryCalculator {

    fun clampLevel(raw: Int, scale: Int): Int {
        if (scale <= 0) return 0
        return ((raw * 100) / scale).coerceIn(0, 100)
    }

    fun usagePercent(current: Int, start: Int, floor: Int): Int {
        val span = start - floor
        if (span <= 0) return 0
        return (((start - current) * 100.0) / span).roundToInt().coerceIn(0, 100)
    }

    fun remainingPercent(current: Int, floor: Int): Int {
        return (current - floor).coerceIn(0, 100)
    }

    fun isAlertDue(current: Int, floor: Int): Boolean {
        return floor >= 0 && current <= floor
    }
}