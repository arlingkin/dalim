package com.dalim.datalimit.core

import android.content.Context
import android.content.SharedPreferences

class UsagePrefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("dalim_prefs", Context.MODE_PRIVATE)

    var limitMb: Long
        get() = sp.getLong(KEY_LIMIT, 200L)
        set(v) = sp.edit().putLong(KEY_LIMIT, v).apply()

    var period: Period
        get() = Period.valueOf(sp.getString(KEY_PERIOD, Period.DAILY.name) ?: Period.DAILY.name)
        set(v) = sp.edit().putString(KEY_PERIOD, v.name).apply()

    var windowStyle: WindowStyle
        get() = WindowStyle.valueOf(sp.getString(KEY_STYLE, WindowStyle.FIXED.name) ?: WindowStyle.FIXED.name)
        set(v) = sp.edit().putString(KEY_STYLE, v.name).apply()

    var gateEnabled: Boolean
        get() = sp.getBoolean(KEY_GATE, true)
        set(v) = sp.edit().putBoolean(KEY_GATE, v).apply()

    var notificationsEnabled: Boolean
        get() = sp.getBoolean(KEY_NOTIFY, true)
        set(v) = sp.edit().putBoolean(KEY_NOTIFY, v).apply()

    var language: String
        get() = sp.getString(KEY_LANG, LocaleHelper.LANG_EN) ?: LocaleHelper.LANG_EN
        set(v) = sp.edit().putString(KEY_LANG, v).apply()

    var extraAllowanceMb: Long
        get() = sp.getLong(KEY_ALLOWANCE, 0L)
        set(v) = sp.edit().putLong(KEY_ALLOWANCE, v).apply()

    var monitoringEnabled: Boolean
        get() = sp.getBoolean(KEY_MONITOR, false)
        set(v) = sp.edit().putBoolean(KEY_MONITOR, v).apply()

    var rollingAnchorMillis: Long
        get() = sp.getLong(KEY_ANCHOR, 0L)
        set(v) = sp.edit().putLong(KEY_ANCHOR, v).apply()

    var lastCheckMillis: Long
        get() = sp.getLong(KEY_LASTCHECK, 0L)
        set(v) = sp.edit().putLong(KEY_LASTCHECK, v).apply()

    var lastConsumedBytes: Long
        get() = sp.getLong(KEY_CONSUMED, 0L)
        set(v) = sp.edit().putLong(KEY_CONSUMED, v).apply()

    fun settings(): DataLimitSettings =
        DataLimitSettings(
            limitMb = limitMb,
            period = period,
            windowStyle = windowStyle,
            gateEnabled = gateEnabled,
            notificationsEnabled = notificationsEnabled,
            extraAllowanceMb = extraAllowanceMb
        )

    fun apply(settings: DataLimitSettings) {
        sp.edit()
            .putLong(KEY_LIMIT, settings.limitMb)
            .putString(KEY_PERIOD, settings.period.name)
            .putString(KEY_STYLE, settings.windowStyle.name)
            .putBoolean(KEY_GATE, settings.gateEnabled)
            .putBoolean(KEY_NOTIFY, settings.notificationsEnabled)
            .putLong(KEY_ALLOWANCE, settings.extraAllowanceMb)
            .apply()
    }

    var countingState: CountingState?
        get() {
            if (!sp.contains(KEY_WINDOW)) return null
            return CountingState(
                windowKey = sp.getLong(KEY_WINDOW, 0L),
                baselineBytes = sp.getLong(KEY_BASELINE, 0L),
                consumedBytes = sp.getLong(KEY_CONSUMED, 0L)
            )
        }
        set(state) {
            if (state == null) {
                sp.edit().remove(KEY_WINDOW).apply()
            } else {
                sp.edit()
                    .putLong(KEY_WINDOW, state.windowKey)
                    .putLong(KEY_BASELINE, state.baselineBytes)
                    .putLong(KEY_CONSUMED, state.consumedBytes)
                    .apply()
            }
        }

    fun resetCounter() {
        sp.edit()
            .remove(KEY_WINDOW)
            .remove(KEY_CONSUMED)
            .remove(KEY_BASELINE)
            .putLong(KEY_ALLOWANCE, 0L)
            .putBoolean(KEY_GATEPOPPED, false)
            .putLong(KEY_LASTPOP, 0L)
            .apply()
        rollingAnchorMillis = System.currentTimeMillis()
    }

    var gatePopped: Boolean
        get() = sp.getBoolean(KEY_GATEPOPPED, false)
        set(v) = sp.edit().putBoolean(KEY_GATEPOPPED, v).apply()

    var lastPopMillis: Long
        get() = sp.getLong(KEY_LASTPOP, 0L)
        set(v) = sp.edit().putLong(KEY_LASTPOP, v).apply()

    // ---- Battery module ----
    var batteryBudgetFloor: Int
        get() = sp.getInt(KEY_BAT_FLOOR, 20)
        set(v) = sp.edit().putInt(KEY_BAT_FLOOR, v.coerceIn(0, 100)).apply()

    var batteryBudgetStart: Int
        get() = sp.getInt(KEY_BAT_START, 100)
        set(v) = sp.edit().putInt(KEY_BAT_START, v.coerceIn(0, 100)).apply()

    var batteryAlertsEnabled: Boolean
        get() = sp.getBoolean(KEY_BAT_ALERTS, true)
        set(v) = sp.edit().putBoolean(KEY_BAT_ALERTS, v).apply()

    var batteryAlertSent: Boolean
        get() = sp.getBoolean(KEY_BAT_ALERT_SENT, false)
        set(v) = sp.edit().putBoolean(KEY_BAT_ALERT_SENT, v).apply()

    var batterySampleRealtime: Long
        get() = sp.getLong(KEY_BAT_SAMPLE_TS, 0L)
        set(v) = sp.edit().putLong(KEY_BAT_SAMPLE_TS, v).apply()

    var batterySampleLevel: Int
        get() = sp.getInt(KEY_BAT_SAMPLE_LEVEL, -1)
        set(v) = sp.edit().putInt(KEY_BAT_SAMPLE_LEVEL, v).apply()

    companion object {
        private const val KEY_LIMIT = "limit_mb"
        private const val KEY_PERIOD = "period"
        private const val KEY_STYLE = "window_style"
        private const val KEY_GATE = "gate_enabled"
        private const val KEY_NOTIFY = "notify_enabled"
        private const val KEY_LANG = "language"
        private const val KEY_ALLOWANCE = "extra_allowance_mb"
        private const val KEY_MONITOR = "monitoring_enabled"
        private const val KEY_ANCHOR = "rolling_anchor"
        private const val KEY_LASTCHECK = "last_check"
        private const val KEY_CONSUMED = "consumed"
        private const val KEY_WINDOW = "window"
        private const val KEY_BASELINE = "baseline"
        private const val KEY_GATEPOPPED = "gate_popped"
        private const val KEY_LASTPOP = "last_pop"

        private const val KEY_BAT_FLOOR = "battery_floor"
        private const val KEY_BAT_START = "battery_start"
        private const val KEY_BAT_ALERTS = "battery_alerts"
        private const val KEY_BAT_ALERT_SENT = "battery_alert_sent"
        private const val KEY_BAT_SAMPLE_TS = "battery_sample_ts"
        private const val KEY_BAT_SAMPLE_LEVEL = "battery_sample_level"
    }
}