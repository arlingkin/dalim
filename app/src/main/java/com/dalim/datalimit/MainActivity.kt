package com.dalim.datalimit

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dalim.datalimit.core.BatterySnapshot
import com.dalim.datalimit.core.LocaleHelper
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.core.util.ByteFormat
import com.dalim.datalimit.data.SqliteNotificationStore
import com.dalim.datalimit.monitor.BatteryMonitor
import com.dalim.datalimit.monitor.BatteryReceiver
import com.dalim.datalimit.monitor.UsageMatcher
import com.dalim.datalimit.ui.AppControlActivity
import com.dalim.datalimit.ui.BatteryActivity
import com.dalim.datalimit.ui.DataActivity
import com.dalim.datalimit.ui.NotificationsActivity
import com.dalim.datalimit.ui.SettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: UsagePrefs
    private lateinit var matcher: UsageMatcher

    private val handler = Handler(Looper.getMainLooper())
    private val batteryReceiver = BatteryReceiver()
    private val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    private val refreshTick = object : Runnable {
        override fun run() {
            renderData()
            renderBattery()
            renderVault()
            handler.postDelayed(this, 5_000L)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.resolve(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = UsagePrefs(this)
        matcher = UsageMatcher(prefs, LocaleHelper.resolve(applicationContext))

        findViewById<android.view.View>(R.id.cardData).setOnClickListener {
            startActivity(Intent(this, DataActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardBattery).setOnClickListener {
            startActivity(Intent(this, BatteryActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardNotifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setOnMenuItemClickListener {
                if (it.itemId == R.id.menuSettings) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                } else false
            }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            batteryFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(batteryReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshTick.run()
        handler.postDelayed(refreshTick, 5_000L)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshTick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshTick)
        super.onDestroy()
    }

    private fun renderData() {
        val report = matcher.compute(System.currentTimeMillis())
        val used = findViewById<android.widget.TextView>(R.id.usedText)
        val percent = findViewById<android.widget.TextView>(R.id.percentText)
        val progress = findViewById<android.widget.ProgressBar>(R.id.gateProgress)
        val status = findViewById<android.widget.TextView>(R.id.dataStatusText)

        used.text = ByteFormat.format(report.consumedBytes)
        status.text = getString(if (prefs.monitoringEnabled) R.string.monitor_on else R.string.monitor_off)

        val sub = findViewById<android.widget.TextView>(R.id.cardDataSub)
        if (report.limitActive) {
            val pct = report.usedPercent.coerceAtMost(999)
            percent.text = "$pct%"
            progress.max = 100
            progress.progress = report.usedPercent.coerceAtMost(100)
            progress.progressTintList = android.content.res.ColorStateList.valueOf(
                if (report.exceeded) resources.getColor(R.color.danger)
                else if (report.usedPercent >= 80) resources.getColor(R.color.warn)
                else resources.getColor(R.color.accent)
            )
            sub.text = getString(
                R.string.card_data_sub,
                ByteFormat.format(report.remainingBytes),
                report.usedPercent.coerceAtMost(999)
            )
        } else {
            percent.text = getString(R.string.no_limit)
            progress.progress = 0
            progress.progressTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.accent))
            sub.text = getString(R.string.card_data_off)
        }
    }

    private fun renderBattery() {
        val snapshot = batterySnapshot()
        val text = findViewById<android.widget.TextView>(R.id.cardBatteryText)
        val sub = findViewById<android.widget.TextView>(R.id.cardBatterySub)
        if (snapshot == null) {
            text.text = "--"
            sub.text = getString(R.string.battery_no_estimate)
            return
        }
        text.text = "${snapshot.levelPercent}%"
        sub.text = when {
            snapshot.drainCalculated ->
                getString(R.string.battery_estimate_fmt, formattedHours(snapshot.estimateHours))
            snapshot.plugged != 0 -> getString(R.string.battery_charging_short)
            else -> getString(R.string.battery_no_estimate)
        }
    }

    private fun batterySnapshot(): BatterySnapshot? {
        val sticky = ContextCompat.registerReceiver(
            this,
            null,
            batteryFilter,
            ContextCompat.RECEIVER_EXPORTED
        ) ?: return null
        val read = BatteryMonitor.readFromIntent(sticky)
        if (read.level < 0) return null
        return BatteryMonitor(prefs).update(
            level = read.level,
            status = read.status,
            plugged = read.plugged,
            temperatureTenthsC = read.temperatureTenthsC,
            voltageMv = read.voltageMv
        )
    }

    private fun renderVault() {
        val enabled = prefs.vaultEnabled
        val big = findViewById<android.widget.TextView>(R.id.cardVaultText)
        if (!enabled) {
            big.text = getString(R.string.card_vault_off)
            return
        }
        Thread {
            val count = SqliteNotificationStore(applicationContext).totalCount()
            runOnUiThread {
                big.text = getString(R.string.card_vault_fmt, count)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun formattedHours(hours: Double): String {
        if (hours.isNaN() || hours <= 0.0) return getString(R.string.battery_no_estimate)
        val totalMinutes = (hours * 60.0).toLong()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0L) "${h}h ${m}m" else "${m}m"
    }
}