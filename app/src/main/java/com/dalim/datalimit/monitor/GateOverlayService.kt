package com.dalim.datalimit.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.dalim.datalimit.R
import com.dalim.datalimit.core.LocaleHelper
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.core.UsageReport

class GateOverlayService : Service() {

    private lateinit var prefs: UsagePrefs
    private lateinit var matcher: UsageMatcher
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.resolve(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        prefs = UsagePrefs(this)
        matcher = UsageMatcher(prefs, LocaleHelper.resolve(applicationContext))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val report = matcher.compute(System.currentTimeMillis())

        NotificationHelper.createChannels(this)
        startForeground(
            NotificationHelper.NOTIF_GATE,
            NotificationHelper.gateNotification(this, getString(R.string.monitoring_bg))
        )
        showOverlay(report)
        return START_NOT_STICKY
    }

    private fun showOverlay(report: UsageReport) {
        if (overlayView != null) return
        val added = View.inflate(this, R.layout.gate_overlay, null)
        bind(added, report)
        // Full-screen, on-top-of-everything overlay: it blocks touch interaction
        // with whatever app the user has open until they pick an action.
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )
        try {
            @Suppress("UNCHECKED_CAST")
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(added, lp)
            overlayView = added
        } catch (_: Exception) {
            // Missing SYSTEM_ALERT_WINDOW; the activity gate is still launched via notification.
        }
    }

    private fun bind(view: View, report: UsageReport) {
        view.findViewById<TextView>(R.id.gateInfo).text = getString(
            R.string.gate_stats_fmt,
            TrafficReader.formatBytes(report.consumedBytes),
            TrafficReader.formatBytes(report.effectiveLimitBytes)
        )
        view.findViewById<View>(R.id.btnAllow).setOnClickListener {
            prefs.extraAllowanceMb += 100L
            dismiss()
        }
        view.findViewById<View>(R.id.btnReset).setOnClickListener {
            prefs.resetCounter()
            dismiss()
        }
        view.findViewById<View>(R.id.btnDismiss).setOnClickListener {
            TrafficMonitorService.stop(this)
            dismiss()
        }
    }

    private fun dismiss() {
        overlayView?.let { v ->
            try {
                @Suppress("UNCHECKED_CAST")
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(v)
            } catch (_: Exception) { }
        }
        overlayView = null
        stopSelf()
    }

    override fun onDestroy() {
        dismiss()
        super.onDestroy()
    }

    companion object {
        fun ensureRunning(context: Context) {
            try {
                context.startForegroundService(Intent(context, GateOverlayService::class.java))
            } catch (_: Exception) {
                // overlay permission missing / FGS restrictions; the activity
                // gate (DataGateActivity) still takes over
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GateOverlayService::class.java))
        }
    }
}