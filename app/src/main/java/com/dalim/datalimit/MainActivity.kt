package com.dalim.datalimit

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dalim.datalimit.core.BatterySnapshot
import com.dalim.datalimit.core.LocaleHelper
import com.dalim.datalimit.core.Schedule
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.core.util.ByteFormat
import com.dalim.datalimit.data.SqliteNotificationStore
import com.dalim.datalimit.monitor.BatteryMonitor
import com.dalim.datalimit.monitor.BatteryReceiver
import com.dalim.datalimit.monitor.UsageMatcher
import com.dalim.datalimit.ui.AppControlActivity
import com.dalim.datalimit.ui.Anim
import com.dalim.datalimit.ui.BatteryActivity
import com.dalim.datalimit.ui.DataActivity
import com.dalim.datalimit.ui.HistoryActivity
import com.dalim.datalimit.ui.NotificationsActivity
import com.dalim.datalimit.ui.SettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: UsagePrefs
    private lateinit var matcher: UsageMatcher
    private var lastUsedBytes = -1L
    private var lastUsedPercent = -1
    private var lastBatteryLevel = -1
    private var entranceAnimated = false

    private val handler = Handler(Looper.getMainLooper())
    private val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    @Volatile
    private var lastBatterySnapshot: BatterySnapshot? = null
    @Volatile
    private var lastBatteryAtMillis = 0L

    private val batteryReceiver = BatteryReceiver().apply {
        onSnapshot = { snap ->
            lastBatterySnapshot = snap
            lastBatteryAtMillis = SystemClock.elapsedRealtime()
            renderBattery(snap)
        }
    }

    private val refreshTick = object : Runnable {
        override fun run() {
            try {
                renderData()
            } catch (_: Throwable) {
            }
            try {
                renderVault()
            } catch (_: Throwable) {
            }
            renderBattery()
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
        findViewById<android.view.View>(R.id.historyLinkText).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
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
        if (!entranceAnimated) {
            entranceAnimated = true
            animateEntrance()
        }
        // Kick the ticker immediately on first appearance (self-rescheduling);
        // do not post a second copy here.
        refreshTick.run()
    }

    private fun animateEntrance() {
        val animate = Anim.enabled(prefs)
        val data = findViewById<android.view.View>(R.id.cardData)
        val battery = findViewById<android.view.View>(R.id.cardBattery)
        val notifications = findViewById<android.view.View>(R.id.cardNotifications)
        Anim.fadeSlideIn(data, animate, delayMs = 0)
        Anim.fadeSlideIn(battery, animate, delayMs = 90)
        Anim.fadeSlideIn(notifications, animate, delayMs = 180)
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
        val animate = Anim.enabled(prefs)

        Anim.countTo(used, lastUsedBytes, report.consumedBytes, { ByteFormat.format(it) }, animate)
        if (lastUsedBytes != report.consumedBytes) lastUsedBytes = report.consumedBytes
        status.text = getString(if (prefs.monitoringEnabled) R.string.monitor_on else R.string.monitor_off)
        renderScheduleStatus()

        val sub = findViewById<android.widget.TextView>(R.id.cardDataSub)
        if (report.limitActive) {
            val pct = report.usedPercent.coerceAtMost(999)
            Anim.countTo(percent, lastUsedPercent, pct, { "$it%" }, animate)
            lastUsedPercent = pct
            progress.max = 100
            Anim.animateProgress(progress, report.usedPercent.coerceAtMost(100), animate)
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
            Anim.animateProgress(progress, 0, animate)
            progress.progressTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.accent))
            sub.text = getString(R.string.card_data_off)
        }
    }

    private fun renderBattery() {
        // The receiver delivers near-real-time snapshots; reuse the freshest one
        // instead of re-reading the sticky intent on every tick.
        val fresh = SystemClock.elapsedRealtime() - lastBatteryAtMillis < 30_000L
        renderBattery(if (fresh) lastBatterySnapshot else batterySnapshot())
    }

    private fun renderBattery(snapshot: BatterySnapshot?) {
        val text = findViewById<android.widget.TextView>(R.id.cardBatteryText)
        val sub = findViewById<android.widget.TextView>(R.id.cardBatterySub)
        if (snapshot == null) {
            text.text = "--"
            sub.text = getString(R.string.battery_no_estimate)
            return
        }
        Anim.countTo(text, lastBatteryLevel, snapshot.levelPercent, { "$it%" }, Anim.enabled(prefs))
        lastBatteryLevel = snapshot.levelPercent
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
        val snap = BatteryMonitor(prefs).update(
            level = read.level,
            status = read.status,
            plugged = read.plugged,
            temperatureTenthsC = read.temperatureTenthsC,
            voltageMv = read.voltageMv
        )
        lastBatterySnapshot = snap
        lastBatteryAtMillis = SystemClock.elapsedRealtime()
        return snap
    }

    private fun renderScheduleStatus() {
        val line = findViewById<android.widget.TextView>(R.id.scheduleStatusText)
        val style = prefs.scheduleWindowStyle
        if (style == Schedule.WindowStyle.OFF) {
            line.visibility = android.view.View.GONE
            return
        }
        val dayLabel = when (style) {
            Schedule.WindowStyle.WEEKDAYS -> getString(R.string.schedule_weekdays)
            Schedule.WindowStyle.WEEKEND -> getString(R.string.schedule_weekend)
            Schedule.WindowStyle.EVERYDAY -> getString(R.string.schedule_everyday)
            else -> getString(R.string.schedule_custom)
        }
        val start = scheduleClock(prefs.scheduleStartMin)
        val end = scheduleClock(prefs.scheduleEndMin)
        line.text = getString(R.string.schedule_status_fmt, dayLabel, start, end)
        line.visibility = android.view.View.VISIBLE
    }

    private fun scheduleClock(minutes: Int): String =
        String.format(
            java.util.Locale.ROOT,
            "%02d:%02d",
            (minutes / 60).coerceIn(0, 23),
            minutes % 60
        )

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