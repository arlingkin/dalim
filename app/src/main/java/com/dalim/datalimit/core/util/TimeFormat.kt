package com.dalim.datalimit.core.util

object TimeFormat {
    fun durationShort(totalMinutes: Long): String {
        if (totalMinutes < 0L) return "—"
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0L) "${h}h ${m}m" else "${m}m"
    }

    fun drainPerHour(levelDeltaPercent: Int, elapsedMinutes: Long): Double {
        if (elapsedMinutes <= 0L) return Double.NaN
        return levelDeltaPercent * 60.0 / elapsedMinutes
    }

    fun estimateRemainingHours(currentPercent: Int, drainPerHour: Double): Double {
        if (drainPerHour <= 0.0) return Double.NaN
        return currentPercent / drainPerHour
    }
}