package com.dalim.datalimit.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.dalim.datalimit.R
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.core.UsageReport

class GateOverlayService : Service() {

    private lateinit var prefs: UsagePrefs
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = UsagePrefs(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val report = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_REPORT, UsageReport::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_REPORT)
        }
        startForeground(
            NotificationHelper.NOTIF_GATE,
            NotificationHelper.gateNotification(this, "Monitoring continues in background")
        )
        if (report != null) showOverlay(report)
        return START_NOT_STICKY
    }

    private fun showOverlay(report: UsageReport) {
        if (overlayView != null) return
        val added = View.inflate(this, R.layout.gate_overlay, null)
        bind(added, report)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }
        try {
            getSystemService(Context.WINDOW_SERVICE).let {
                (it as WindowManager).addView(added, lp)
            }
            overlayView = added
        } catch (_: Exception) {
            // Missing SYSTEM_ALERT_WINDOW; the activity gate is still launched via notification.
        }
    }

    private fun bind(view: View, report: UsageReport) {
        view.findViewById<TextView>(R.id.gateInfo).text =
            "${TrafficReader.formatBytes(report.consumedBytes)} / ${TrafficReader.formatBytes(report.effectiveLimitBytes)}"
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
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
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
        private const val EXTRA_REPORT = "report"

        fun ensureRunning(context: Context, report: UsageReport) {
            val intent = Intent(context, GateOverlayService::class.java)
                .putExtra(EXTRA_REPORT, report)
            try {
                context.startForegroundService(intent)
            } catch (_: SecurityException) {
                // overlay permission missing / FGS restrictions; skip overlay.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GateOverlayService::class.java))
        }
    }
}