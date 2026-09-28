package com.dalim.datalimit.monitor

import android.os.BatteryManager
import android.os.SystemClock
import com.dalim.datalimit.core.BatterySnapshot
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.core.util.BatteryCalculator
import com.dalim.datalimit.core.util.TimeFormat
import kotlin.math.roundToLong

/**
 * Lightweight battery tracker. Receives value-level updates (level, status,
 * plugged state, temperature, voltage) pushed from ACTION_BATTERY_CHANGED and
 * computes drain + time-to-empty estimates from persisted low-frequency
 * samples. Persists one sample per charge cycle (per state change) to keep
 * estimates honest: no aggressive polling is performed.
 */
class BatteryMonitor(private val prefs: UsagePrefs) {

    data class Sample(val levelPercent: Int, val realtimeMillis: Long)

    fun drainPerHourPercent(levelDelta: Int, elapsedMinutes: Long): Double =
        TimeFormat.drainPerHour(levelDelta, elapsedMinutes)

    fun estimateHours(currentPercent: Int, drainPerHour: Double): Double =
        TimeFormat.estimateRemainingHours(currentPercent, drainPerHour)

    fun update(
        level: Int,
        status: Int,
        plugged: Int,
        temperatureTenthsC: Int,
        voltageMv: Int
    ): BatterySnapshot {
        val now = SystemClock.elapsedRealtime()
        val levelClamped = level.coerceIn(0, 100)

        val prev = capture()
        val discharging = status == BatteryManager.BATTERY_STATUS_DISCHARGING ||
            status == BatteryManager.BATTERY_STATUS_NOT_CHARGING

        var drain = Double.NaN
        var persistSample = prev == null || !discharging
        if (prev != null) {
            val minutes = (now - prev.realtimeMillis) / 60_000.0
            val delta = prev.levelPercent - levelClamped
            if (discharging && minutes >= MIN_SAMPLE_MINUTES) {
                if (delta > 0) {
                    drain = TimeFormat.drainPerHour(delta, minutes.roundToLong())
                    persistSample = true
                }
                // Same level across the whole window: no new information, keep
                // the existing anchor so a later drop still closes the interval.
            }
        }
        // Keep the fresh "no estimate" / charge-cycle anchor from resetting the
        // window every sticky read (dashboard poll + battery-changed intents all
        // call update()); otherwise the >=5 min interval is never reached and the
        // drain never computes.
        if (persistSample) {
            prefs.batterySampleRealtime = now
            prefs.batterySampleLevel = levelClamped
        }

        val floor = prefs.batteryBudgetFloor
        val start = prefs.batteryBudgetStart

        val fired = discharging && BatteryCalculator.isAlertDue(levelClamped, floor) && !prefs.batteryAlertSent
        if (discharging && BatteryCalculator.isAlertDue(levelClamped, floor)) {
            prefs.batteryAlertSent = true
        } else if (!discharging) {
            prefs.batteryAlertSent = false
        }

        return BatterySnapshot(
            levelPercent = levelClamped,
            status = status,
            plugged = plugged,
            temperatureTenthsC = temperatureTenthsC,
            voltageMv = voltageMv,
            drainPerHour = drain,
            estimateHours = TimeFormat.estimateRemainingHours(levelClamped, drain),
            usagePercent = BatteryCalculator.usagePercent(levelClamped, start, floor),
            remainingBudgetPercent = BatteryCalculator.remainingPercent(levelClamped, floor),
            alertTriggered = fired
        )
    }

    private fun capture(): Sample? {
        val ts = prefs.batterySampleRealtime
        if (ts <= 0L) return null
        val level = prefs.batterySampleLevel
        if (level < 0) return null
        return Sample(level, ts)
    }

    companion object {
        private const val MIN_SAMPLE_MINUTES = 5L

        fun readFromIntent(intent: android.content.Intent): BatteryIntent {
            val rawLevel = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, 0)
            val plugged = intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)
            val temp = intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1)
            val voltage = intent.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, -1)
            return BatteryIntent(
                level = if (rawLevel >= 0) BatteryCalculator.clampLevel(rawLevel, scale) else -1,
                status = status,
                plugged = plugged,
                temperatureTenthsC = temp,
                voltageMv = voltage
            )
        }

        data class BatteryIntent(
            val level: Int,
            val status: Int,
            val plugged: Int,
            val temperatureTenthsC: Int,
            val voltageMv: Int
        )
    }
}