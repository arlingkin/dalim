package com.dalim.datalimit.monitor

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.dalim.datalimit.R
import com.dalim.datalimit.core.BatterySnapshot
import com.dalim.datalimit.core.HistoryBuckets
import com.dalim.datalimit.core.LocaleHelper
import com.dalim.datalimit.core.Schedule
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.core.UsageReport
import com.dalim.datalimit.data.BatteryHistoryStore
import com.dalim.datalimit.data.UsageHistoryStore
import com.dalim.datalimit.ui.DataGateActivity
import java.time.LocalDate

class TrafficMonitorService : Service() {

    private lateinit var prefs: UsagePrefs
    private lateinit var matcher: UsageMatcher
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            try {
                checkAndEnforce()
                consecutiveFailures = 0
            } catch (t: Throwable) {
                // A failure in any tick path must never kill the process: the
                // START_STICKY restart would re-crash in a loop. Log and slow
                // down so the device stays usable while the issue persists.
                consecutiveFailures++
                Log.e(TAG, "monitor poll tick failed (${consecutiveFailures}x)", t)
            }
            handler.postDelayed(this, if (consecutiveFailures > 0) BASE_POLL_MS else pollInterval)
        }
    }

    private var stalled = false
    private var pollInterval = BASE_POLL_MS
    private var consecutiveFailures = 0
    private val nm: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private var lastSnapshotRx = 0L
    private var lastSnapshotTx = 0L
    private var historySeeded = false
    private var lastHistoryPurgeAt = 0L
    private val usageHistory by lazy { UsageHistoryStore(applicationContext) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.resolve(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        prefs = UsagePrefs(this)
        matcher = UsageMatcher(prefs, LocaleHelper.resolve(applicationContext))
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESET -> prefs.resetCounter()
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (prefs.rollingAnchorMillis == 0L) {
            prefs.rollingAnchorMillis = System.currentTimeMillis()
        }

        startForeground(
            NotificationHelper.NOTIF_MONITOR,
            NotificationHelper.monitoringNotification(this, getString(R.string.counting_data))
        )
        prefs.monitoringEnabled = true
        if (!stalled) {
            stalled = true
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
        return START_STICKY
    }

    private fun checkAndEnforce() {
        val now = System.currentTimeMillis()
        val report = matcher.compute(now)
        val batteryHalt = batteryFloorActive()
        val scheduleHalt = scheduleHaltActive()

        recordHistory(now, report)

        pollInterval = if (report.exceeded || batteryHalt || scheduleHalt || report.usedPercent >= 80) {
            FAST_POLL_MS
        } else {
            BASE_POLL_MS
        }

        if (!report.exceeded && !batteryHalt && !scheduleHalt) {
            nm.cancel(NotificationHelper.NOTIF_ALERT)
            nm.cancel(NotificationHelper.NOTIF_BATTERY)
            nm.cancel(NotificationHelper.NOTIF_SCHEDULE)
            GateOverlayService.stop(this)
            prefs.gatePopped = false
            prefs.lastPopMillis = 0L
        } else if (batteryHalt) {
            // Battery drained to the floor: show the battery gate overlay (no
            // data "allow more / reset" actions) and re-post the battery alert
            // on every fast poll until the device is charging again.
            GateOverlayService.ensureRunning(this, battery = true)
            nm.cancel(NotificationHelper.NOTIF_BATTERY)
            nm.notify(
                NotificationHelper.NOTIF_BATTERY,
                NotificationHelper.batteryAlertNotification(this, batterySnapshot()?.levelPercent ?: -1, prefs.batteryBudgetFloor)
            )
        } else if (scheduleHalt) {
            // Outside the active schedule window: show the schedule gate
            // overlay (no data actions) and re-post the schedule alert on every
            // fast poll until the window opens again.
            GateOverlayService.ensureRunning(this, schedule = true)
            nm.cancel(NotificationHelper.NOTIF_SCHEDULE)
            nm.notify(NotificationHelper.NOTIF_SCHEDULE, NotificationHelper.scheduleAlertNotification(this))
        } else if (prefs.gateEnabled) {
            // Halt: hit the limit, so open the red gate (data-limit screen)
            // directly. It re-pops on every fast poll tick — with no throttle
            // — so it comes back even after the user backs out or dismisses
            // it, until they pick an action (allow more / reset / stop).
            GateOverlayService.ensureRunning(this)
            popGateActivity()
            if (prefs.notificationsEnabled) {
                // Cancel + re-post so the full-screen alert pops again over
                // whatever app the user opened, even after they dismissed it.
                nm.cancel(NotificationHelper.NOTIF_ALERT)
                nm.notify(NotificationHelper.NOTIF_ALERT, NotificationHelper.alertNotification(this))
            }
        } else if (prefs.notificationsEnabled) {
            if (batteryHalt) {
                nm.notify(NotificationHelper.NOTIF_BATTERY, NotificationHelper.batteryAlertNotification(this, batterySnapshot()?.levelPercent ?: -1, prefs.batteryBudgetFloor))
            } else {
                nm.notify(NotificationHelper.NOTIF_ALERT, NotificationHelper.alertNotification(this))
            }
        }

        val text = getString(
            R.string.monitor_status_fmt,
            TrafficReader.formatBytes(report.consumedBytes),
            report.usedPercent.coerceAtMost(999)
        )
        nm.notify(NotificationHelper.NOTIF_MONITOR, NotificationHelper.monitoringNotification(this, text))
    }

    /** Battery read for the monitor loop (sticky intent; throttled sampling). */
    private fun batterySnapshot(): BatterySnapshot? {
        val sticky = ContextCompat.registerReceiver(
            this,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        ) ?: return null
        val read = BatteryMonitor.readFromIntent(sticky)
        if (read.level < 0) return null
        val snap = BatteryMonitor(prefs).update(
            level = read.level,
            status = read.status,
            plugged = read.plugged,
            temperatureTenthsC = read.temperatureTenthsC,
            voltageMv = read.voltageMv
        )
        BatteryHistoryStore(applicationContext).append(System.currentTimeMillis(), read.level)
        return snap
    }

    /**
     * True while discharging (not plugged) at/below the battery floor with the
     * floor gate enabled and not snoozed. Charging clears any snooze.
     */
    private fun batteryFloorActive(): Boolean {
        val snap = batterySnapshot() ?: return false
        if (!prefs.batteryFloorGateEnabled) return false
        if (snap.plugged != 0) {
            if (prefs.batteryFloorGateSnoozed) prefs.batteryFloorGateSnoozed = false
            return false
        }
        return !prefs.batteryFloorGateSnoozed && snap.levelPercent <= prefs.batteryBudgetFloor
    }

    /**
     * True while `now` falls outside the configured schedule window and the
     * snooze (set from the schedule gate overlay) has not covered it yet.
     * Entering the window clears the snooze.
     */
    private fun scheduleHaltActive(): Boolean {
        val now = System.currentTimeMillis()
        val outside = Schedule.outsideSchedule(
            prefs.scheduleWindowStyle,
            prefs.scheduleStartMin,
            prefs.scheduleEndMin,
            now
        )
        if (!outside || prefs.scheduleWindowStyle == Schedule.WindowStyle.OFF) {
            if (prefs.scheduleSnoozeUntilMillis != 0L) prefs.scheduleSnoozeUntilMillis = 0L
            return false
        }
        val snoozeUntil = prefs.scheduleSnoozeUntilMillis
        return snoozeUntil == 0L || now >= snoozeUntil
    }

    /**
     * Accumulate daily radio deltas into the usage-history store. The first
     * tick anchors on the current snapshot (so nothing is counted before the
     * service starts); afterwards every fast/regular poll records the positive
     * delta since the previous tick, re-anchoring after a reboot reset. History
     * rows are daily buckets only, which the chart aggregates further.
     */
    private fun recordHistory(now: Long, report: UsageReport) {
        if (!historySeeded) {
            historySeeded = true
            lastSnapshotRx = report.radiosRxBytes
            lastSnapshotTx = report.radiosTxBytes
            usageHistory.backfillFromCurrent(now, report.radiosRxBytes, report.radiosTxBytes)
            usageHistory.purgeOlderThan(UsageHistoryStore.RETENTION_DAYS + UsageHistoryStore.MAX_BUCKETS, now)
            lastHistoryPurgeAt = now
            return
        }
        val rx = HistoryBuckets.computeDelta(lastSnapshotRx, report.radiosRxBytes)
        val tx = HistoryBuckets.computeDelta(lastSnapshotTx, report.radiosTxBytes)
        if (rx > 0L || tx > 0L) {
            usageHistory.insert(LocalDate.now().toEpochDay(), rx, tx)
        }
        lastSnapshotRx = report.radiosRxBytes
        lastSnapshotTx = report.radiosTxBytes
        if (now - lastHistoryPurgeAt >= DAY_MS) {
            usageHistory.purgeOlderThan(UsageHistoryStore.RETENTION_DAYS, now)
            lastHistoryPurgeAt = now
        }
    }

    /**
     * Launches the full-screen DataGateActivity on top of whatever app is open.
     * Called on every fast poll tick while the limit stays exceeded, so the red
     * gate always opens/re-opens directly. Works from the background while the
     * app holds the SYSTEM_ALERT_WINDOW permission (which exempts us from
     * background-activity-start limits). CLEAR_TOP prevents stacking when the
     * activity is already shown.
     */
    private fun popGateActivity() {
        val intent = Intent(this, DataGateActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // Background activity launch restricted without overlay permission;
            // the overlay service (and fullScreenIntent below) still applies.
        }
    }

    override fun onDestroy() {
        stalled = false
        handler.removeCallbacks(tick)
        if (prefs.monitoringEnabled) {
            prefs.monitoringEnabled = false
        }
        GateOverlayService.stop(this)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DataLimitMonitor"

        private const val BASE_POLL_MS = 60_000L
        private const val FAST_POLL_MS = 10_000L
        private const val DAY_MS = 86_400_000L

        const val ACTION_RESET = "com.dalim.datalimit.action.RESET"
        const val ACTION_STOP = "com.dalim.datalimit.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, TrafficMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TrafficMonitorService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun reset(context: Context) {
            val intent = Intent(context, TrafficMonitorService::class.java).setAction(ACTION_RESET)
            context.startService(intent)
        }
    }
}