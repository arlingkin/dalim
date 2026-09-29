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
        get() = Period.entries.firstOrNull { it.name == sp.getString(KEY_PERIOD, null) } ?: Period.DAILY
        set(v) = sp.edit().putString(KEY_PERIOD, v.name).apply()

    var windowStyle: WindowStyle
        get() = WindowStyle.entries.firstOrNull { it.name == sp.getString(KEY_STYLE, null) } ?: WindowStyle.FIXED
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

    /** Export a full snapshot of every user-configurable field (M2 export). */
    fun toPatch(): UsagePrefsPatch =
        UsagePrefsPatch(
            limitMb = limitMb,
            period = period,
            windowStyle = windowStyle,
            gateEnabled = gateEnabled,
            notificationsEnabled = notificationsEnabled,
            language = language,
            firewallEnabled = firewallEnabled,
            blockedApps = blockedApps,
            appBudgetsMb = budgetedPackages(),
            batteryFloor = batteryBudgetFloor,
            batteryAlertsEnabled = batteryAlertsEnabled,
            scheduleStyle = scheduleWindowStyle,
            scheduleStartMin = scheduleStartMin,
            scheduleEndMin = scheduleEndMin
        )

    /**
     * Import a snapshot from config exchange (M2 import). Applies the whole
     * patch in one SharedPreferences edit; a failed parse must never call this.
     */
    fun apply(patch: UsagePrefsPatch) {
        val edit = sp.edit()
            .putLong(KEY_LIMIT, patch.limitMb.coerceAtLeast(0L))
            .putString(KEY_PERIOD, patch.period.name)
            .putString(KEY_STYLE, patch.windowStyle.name)
            .putBoolean(KEY_GATE, patch.gateEnabled)
            .putBoolean(KEY_NOTIFY, patch.notificationsEnabled)
            .putString(KEY_LANG, patch.language)
            .putBoolean(KEY_FIREWALL, patch.firewallEnabled)
            .putInt(KEY_BAT_FLOOR, patch.batteryFloor.coerceIn(0, 100))
            .putBoolean(KEY_BAT_ALERTS, patch.batteryAlertsEnabled)
            .putString(KEY_SCHEDULE_STYLE, patch.scheduleStyle.code)
            .putInt(KEY_SCHEDULE_START, patch.scheduleStartMin.coerceIn(0, Schedule.MINUTES_PER_DAY - 1))
            .putInt(KEY_SCHEDULE_END, patch.scheduleEndMin.coerceIn(0, Schedule.MINUTES_PER_DAY - 1))
            .putStringSet(KEY_BLOCKED, patch.blockedApps)

        val budgets = patch.appBudgetsMb
        for ((pkg, mb) in budgets) edit.putLong(KEY_APP_BUDGET_PREFIX + pkg, mb.coerceAtLeast(0L))
        for (pkg in budgetedPackages().keys) {
            if (pkg !in budgets) edit.remove(KEY_APP_BUDGET_PREFIX + pkg)
        }
        edit.apply()
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

    /** Halt data (gate) while discharging below the battery floor. */
    var batteryFloorGateEnabled: Boolean
        get() = sp.getBoolean(KEY_BAT_GATE, true)
        set(v) = sp.edit().putBoolean(KEY_BAT_GATE, v).apply()

    /** Snoozed by the user until the next charge; cleared once plugged in. */
    var batteryFloorGateSnoozed: Boolean
        get() = sp.getBoolean(KEY_BAT_GATE_SNOOZE, false)
        set(v) = sp.edit().putBoolean(KEY_BAT_GATE_SNOOZE, v).apply()

    var batterySampleRealtime: Long
        get() = sp.getLong(KEY_BAT_SAMPLE_TS, 0L)
        set(v) = sp.edit().putLong(KEY_BAT_SAMPLE_TS, v).apply()

    var batterySampleLevel: Int
        get() = sp.getInt(KEY_BAT_SAMPLE_LEVEL, -1)
        set(v) = sp.edit().putInt(KEY_BAT_SAMPLE_LEVEL, v).apply()

    // ---- Schedule (active window) ----
    var scheduleWindowStyle: Schedule.WindowStyle
        get() = Schedule.WindowStyle.from(sp.getString(KEY_SCHEDULE_STYLE, null))
        set(v) = sp.edit().putString(KEY_SCHEDULE_STYLE, v.code).apply()

    var scheduleStartMin: Int
        get() = sp.getInt(KEY_SCHEDULE_START, 0)
        set(v) = sp.edit().putInt(KEY_SCHEDULE_START, v.coerceIn(0, Schedule.MINUTES_PER_DAY - 1)).apply()

    var scheduleEndMin: Int
        get() = sp.getInt(KEY_SCHEDULE_END, Schedule.MINUTES_PER_DAY - 1)
        set(v) = sp.edit().putInt(KEY_SCHEDULE_END, v.coerceIn(0, Schedule.MINUTES_PER_DAY - 1)).apply()

    /** Deadline for the schedule snooze; 0 = not snoozed. Cleared by charge/resume anyway. */
    var scheduleSnoozeUntilMillis: Long
        get() = sp.getLong(KEY_SCHEDULE_SNOOZE, 0L)
        set(v) = sp.edit().putLong(KEY_SCHEDULE_SNOOZE, v).apply()

    // ---- Notification vault ----
    var vaultEnabled: Boolean
        get() = sp.getBoolean(KEY_VAULT_ENABLED, false)
        set(v) = sp.edit().putBoolean(KEY_VAULT_ENABLED, v).apply()

    var vaultRetentionDays: Int
        get() = sp.getInt(KEY_VAULT_RETENTION, 7)
        set(v) = sp.edit().putInt(KEY_VAULT_RETENTION, v.coerceIn(1, 365)).apply()

    var vaultLastCleanup: Long
        get() = sp.getLong(KEY_VAULT_CLEANUP, 0L)
        set(v) = sp.edit().putLong(KEY_VAULT_CLEANUP, v).apply()

    // ---- Animations ----
    var reduceMotion: Boolean
        get() = sp.getBoolean(KEY_REDUCE_MOTION, false)
        set(v) = sp.edit().putBoolean(KEY_REDUCE_MOTION, v).apply()

    // ---- Network firewall (VPN blocklist) ----
    var firewallEnabled: Boolean
        get() = sp.getBoolean(KEY_FIREWALL, false)
        set(v) = sp.edit().putBoolean(KEY_FIREWALL, v).apply()

    var blockedApps: Set<String>
        get() = sp.getStringSet(KEY_BLOCKED, emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_BLOCKED, v).apply()

    fun blockApp(packageName: String) {
        blockedApps = blockedApps + packageName
    }

    fun unblockApp(packageName: String) {
        blockedApps = blockedApps - packageName
    }

    fun appBudgetMb(packageName: String): Long =
        sp.getLong(KEY_APP_BUDGET_PREFIX + packageName, 0L)

    fun setAppBudgetMb(packageName: String, mb: Long) {
        val key = KEY_APP_BUDGET_PREFIX + packageName
        val edit = sp.edit()
        if (mb <= 0L) edit.remove(key) else edit.putLong(key, mb)
        edit.apply()
    }

    fun budgetedPackages(): Map<String, Long> {
        val all = sp.all
        val out = HashMap<String, Long>()
        for ((key, value) in all) {
            if (key.startsWith(KEY_APP_BUDGET_PREFIX) && value is Long && value > 0L) {
                out[key.removePrefix(KEY_APP_BUDGET_PREFIX)] = value
            }
        }
        return out
    }

    fun temporaryAllowUntil(packageName: String): Long =
        sp.getLong(KEY_TEMP_ALLOW_PREFIX + packageName, 0L)

    fun setTemporaryAllow(packageName: String, untilMillis: Long) {
        val key = KEY_TEMP_ALLOW_PREFIX + packageName
        val edit = sp.edit()
        if (untilMillis <= 0L) edit.remove(key) else edit.putLong(key, untilMillis)
        edit.apply()
    }

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
        private const val KEY_BAT_GATE = "battery_floor_gate"
        private const val KEY_BAT_GATE_SNOOZE = "battery_floor_gate_snooze"
        private const val KEY_BAT_SAMPLE_TS = "battery_sample_ts"
        private const val KEY_BAT_SAMPLE_LEVEL = "battery_sample_level"

        private const val KEY_VAULT_ENABLED = "vault_enabled"
        private const val KEY_VAULT_RETENTION = "vault_retention_days"
        private const val KEY_VAULT_CLEANUP = "vault_last_cleanup"

        private const val KEY_SCHEDULE_STYLE = "schedule_style"
        private const val KEY_SCHEDULE_START = "schedule_start"
        private const val KEY_SCHEDULE_END = "schedule_end"
        private const val KEY_SCHEDULE_SNOOZE = "schedule_snooze_until"

        private const val KEY_FIREWALL = "firewall_enabled"
        private const val KEY_BLOCKED = "firewall_blocked"
        private const val KEY_APP_BUDGET_PREFIX = "app_budget_"
        private const val KEY_TEMP_ALLOW_PREFIX = "temp_allow_"

        private const val KEY_REDUCE_MOTION = "reduce_motion"
    }
}