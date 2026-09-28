package com.dalim.datalimit.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
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
        val batteryMode = intent?.getStringExtra(EXTRA_REASON) == REASON_BATTERY

        NotificationHelper.createChannels(this)
        startForeground(
            NotificationHelper.NOTIF_GATE,
            NotificationHelper.gateNotification(this, getString(R.string.monitoring_bg))
        )
        showOverlay(report, batteryMode)
        return START_NOT_STICKY
    }

    private fun showOverlay(report: UsageReport, batteryMode: Boolean) {
        if (overlayView != null) return
        val added = View.inflate(this, R.layout.gate_overlay, null)
        bind(added, report, batteryMode)
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

    private fun bind(view: View, report: UsageReport, batteryMode: Boolean) {
        val heading = view.findViewById<TextView>(R.id.gateTitle)
        val big = view.findViewById<TextView>(R.id.gateLimitText)
        val allow = view.findViewById<View>(R.id.btnAllow)
        val reset = view.findViewById<View>(R.id.btnReset)
        val dismiss = view.findViewById<View>(R.id.btnDismiss)

        if (batteryMode) {
            heading.setText(R.string.battery_gate_title)
            val (level, floor) = batteryLevels()
            big.text = if (level >= 0) "$level%" else "--"
            view.findViewById<TextView>(R.id.gateInfo).text =
                getString(R.string.battery_gate_info_fmt, level.coerceAtLeast(0), floor)
            allow.visibility = View.GONE
            reset.visibility = View.GONE
            dismiss.text = getString(R.string.battery_gate_snooze)
            dismiss.setOnClickListener {
                prefs.batteryFloorGateSnoozed = true
                dismiss()
            }
            return
        }

        view.findViewById<TextView>(R.id.gateInfo).text = getString(
            R.string.gate_stats_fmt,
            TrafficReader.formatBytes(report.consumedBytes),
            TrafficReader.formatBytes(report.effectiveLimitBytes)
        )
        allow.setOnClickListener {
            prefs.extraAllowanceMb += 100L
            dismiss()
        }
        reset.setOnClickListener {
            prefs.resetCounter()
            dismiss()
        }
        dismiss.setOnClickListener {
            TrafficMonitorService.stop(this)
            dismiss()
        }
    }

    private fun batteryLevels(): Pair<Int, Int> {
        val sticky = ContextCompat.registerReceiver(
            this,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        val read = sticky?.let { BatteryMonitor.readFromIntent(it) }
        val level = read?.takeIf { it.level >= 0 }?.level ?: -1
        val floor = prefs.batteryBudgetFloor
        return if (level >= 0) level to floor else prefs.batteryBudgetStart to floor
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
        private const val EXTRA_REASON = "reason"
        const val REASON_DATA = "data"
        const val REASON_BATTERY = "battery"

        fun ensureRunning(context: Context, battery: Boolean = false) {
            try {
                val i = Intent(context, GateOverlayService::class.java)
                if (battery) i.putExtra(EXTRA_REASON, REASON_BATTERY)
                context.startForegroundService(i)
            } catch (_: Exception) {
                // overlay permission missing / FGS restrictions; the activity
                // gate (DataGateActivity) still takes over for data
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GateOverlayService::class.java))
        }
    }
}