package com.dalim.datalimit.monitor

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.dalim.datalimit.core.UsagePrefs

class TrafficMonitorService : Service() {

    private lateinit var prefs: UsagePrefs
    private lateinit var matcher: UsageMatcher
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            checkAndEnforce()
            handler.postDelayed(this, pollInterval)
        }
    }

    private var stalled = false
    private var pollInterval = BASE_POLL_MS
    private val nm: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = UsagePrefs(this)
        matcher = UsageMatcher(prefs)
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
            NotificationHelper.monitoringNotification(this, "Counting data…")
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

        pollInterval = if (report.exceeded || report.usedPercent >= 80) FAST_POLL_MS else BASE_POLL_MS

        if (!report.exceeded) {
            nm.cancel(NotificationHelper.NOTIF_ALERT)
            GateOverlayService.stop(this)
        } else if (prefs.gateEnabled) {
            GateOverlayService.ensureRunning(this)
            if (prefs.notificationsEnabled) {
                // Cancel + re-post so the full-screen alert pops again over
                // whatever app the user opened, even after they dismissed it.
                nm.cancel(NotificationHelper.NOTIF_ALERT)
                nm.notify(NotificationHelper.NOTIF_ALERT, NotificationHelper.alertNotification(this))
            }
        } else if (prefs.notificationsEnabled) {
            nm.notify(NotificationHelper.NOTIF_ALERT, NotificationHelper.alertNotification(this))
        }

        val text = TrafficReader.formatBytes(report.consumedBytes) +
            " used · " + report.usedPercent + "% of limit"
        nm.notify(NotificationHelper.NOTIF_MONITOR, NotificationHelper.monitoringNotification(this, text))
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
        private const val BASE_POLL_MS = 60_000L
        private const val FAST_POLL_MS = 10_000L

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