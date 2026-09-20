package com.dalim.datalimit.core

data class BatterySnapshot(
    val levelPercent: Int,
    val status: Int,
    val plugged: Int,
    val temperatureTenthsC: Int,
    val voltageMv: Int,
    val drainPerHour: Double,
    val estimateHours: Double,
    val usagePercent: Int,
    val remainingBudgetPercent: Int,
    val alertTriggered: Boolean = false
) {
    val drainCalculated: Boolean get() = !drainPerHour.isNaN()
    val estimateCalculated: Boolean get() = !estimateHours.isNaN()
    val hasTemperature: Boolean get() = temperatureTenthsC > 0
    val hasVoltage: Boolean get() = voltageMv > 0

    companion object {
        fun unavailable() = BatterySnapshot(
            levelPercent = -1, status = 0, plugged = 0,
            temperatureTenthsC = -1, voltageMv = -1,
            drainPerHour = Double.NaN, estimateHours = Double.NaN,
            usagePercent = 0, remainingBudgetPercent = 0
        )
    }
}