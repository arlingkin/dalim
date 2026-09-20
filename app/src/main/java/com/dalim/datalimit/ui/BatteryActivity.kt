package com.dalim.datalimit.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dalim.datalimit.R
import com.dalim.datalimit.core.BatterySnapshot
import com.dalim.datalimit.core.LocaleHelper
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.monitor.BatteryMonitor
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.slider.Slider

class BatteryActivity : AppCompatActivity() {

    private lateinit var prefs: UsagePrefs

    private lateinit var batteryLevelText: TextView
    private lateinit var batteryStatusText: TextView
    private lateinit var batteryEstimateText: TextView
    private lateinit var batteryDrainText: TextView
    private lateinit var batteryBudgetUsageText: TextView
    private lateinit var batteryTempVoltageText: TextView
    private lateinit var floorLabel: TextView
    private lateinit var startLabel: TextView
    private lateinit var floorSlider: Slider
    private lateinit var startSlider: Slider

    private val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    private val handler = Handler(Looper.getMainLooper())

    private val refreshTick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 5_000L)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.resolve(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battery)

        prefs = UsagePrefs(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        batteryLevelText = findViewById(R.id.batteryLevelText)
        batteryStatusText = findViewById(R.id.batteryStatusText)
        batteryEstimateText = findViewById(R.id.batteryEstimateText)
        batteryDrainText = findViewById(R.id.batteryDrainText)
        batteryBudgetUsageText = findViewById(R.id.batteryBudgetUsageText)
        batteryTempVoltageText = findViewById(R.id.batteryTempVoltageText)
        floorLabel = findViewById(R.id.floorLabel)
        startLabel = findViewById(R.id.startLabel)
        floorSlider = findViewById(R.id.floorSlider)
        startSlider = findViewById(R.id.startSlider)

        floorSlider.valueFrom = 0f
        floorSlider.valueTo = 90f
        floorSlider.stepSize = 1f
        startSlider.valueFrom = 11f
        startSlider.valueTo = 100f
        startSlider.stepSize = 1f

        loadBudget()
        wireBudgetListeners()
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchBatteryAlerts)
            .setOnCheckedChangeListener { _, checked -> prefs.batteryAlertsEnabled = checked }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        loadBudget()
        render()
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

    private fun loadBudget() {
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchBatteryAlerts)
            .isChecked = prefs.batteryAlertsEnabled
        floorSlider.value = prefs.batteryBudgetFloor.toFloat().coerceIn(floorSlider.valueFrom, floorSlider.valueTo)
        val start = prefs.batteryBudgetStart.toFloat()
        startSlider.value = start.coerceIn(startSlider.valueFrom, startSlider.valueTo)
        updateBudgetLabels()
    }

    private fun wireBudgetListeners() {
        floorSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val floor = value.toInt()
            prefs.batteryBudgetFloor = floor.coerceIn(0, 90)
            if (prefs.batteryBudgetStart <= floor) {
                val newStart = (floor + 1).coerceAtMost(100)
                prefs.batteryBudgetStart = newStart
                startSlider.value = newStart.toFloat()
            }
            updateBudgetLabels()
            render()
        }
        startSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val start = value.toInt()
            prefs.batteryBudgetStart = start.coerceIn(11, 100)
            if (start <= prefs.batteryBudgetFloor) {
                val newFloor = (start - 1).coerceAtLeast(0)
                prefs.batteryBudgetFloor = newFloor
                floorSlider.value = newFloor.toFloat()
            }
            updateBudgetLabels()
            render()
        }
    }

    private fun updateBudgetLabels() {
        floorLabel.text = getString(R.string.battery_floor_label, prefs.batteryBudgetFloor)
        startLabel.text = getString(R.string.battery_start_label, prefs.batteryBudgetStart)
    }

    private fun render() {
        val sticky = ContextCompat.registerReceiver(
            this,
            null,
            batteryFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
        if (sticky == null) {
            batteryLevelText.text = "--"
            return
        }
        val read = BatteryMonitor.readFromIntent(sticky)
        val snapshot = BatteryMonitor(prefs).update(
            level = read.level,
            status = read.status,
            plugged = read.plugged,
            temperatureTenthsC = read.temperatureTenthsC,
            voltageMv = read.voltageMv
        )
        renderSnapshot(snapshot)
    }

    private fun renderSnapshot(snapshot: BatterySnapshot) {
        batteryLevelText.text = "${snapshot.levelPercent}%"
        batteryStatusText.text = getString(statusString(snapshot.status))

        batteryEstimateText.text = when {
            snapshot.drainCalculated ->
                getString(R.string.battery_estimate_fmt, formattedHours(snapshot.estimateHours))
            else -> getString(R.string.battery_no_estimate)
        }

        if (snapshot.drainCalculated) {
            batteryDrainText.text = getString(
                R.string.battery_drain_fmt,
                String.format(java.util.Locale.US, "%.1f%%", snapshot.drainPerHour)
            )
            batteryDrainText.visibility = android.view.View.VISIBLE
        } else {
            batteryDrainText.text = ""
            batteryDrainText.visibility = android.view.View.GONE
        }

        val budgetActive = prefs.batteryBudgetStart > prefs.batteryBudgetFloor
        batteryBudgetUsageText.text = if (budgetActive) {
            getString(R.string.battery_usage_fmt, snapshot.usagePercent)
        } else {
            getString(R.string.battery_remaining_fmt, snapshot.remainingBudgetPercent)
        }

        if (snapshot.hasTemperature || snapshot.hasVoltage) {
            batteryTempVoltageText.text = getString(
                R.string.battery_temp_voltage_fmt,
                snapshot.temperatureTenthsC / 10.0,
                snapshot.voltageMv
            )
            batteryTempVoltageText.visibility = android.view.View.VISIBLE
        } else {
            batteryTempVoltageText.text = ""
            batteryTempVoltageText.visibility = android.view.View.GONE
        }
    }

    private fun statusString(status: Int): Int = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> R.string.battery_status_charging
        BatteryManager.BATTERY_STATUS_DISCHARGING -> R.string.battery_status_discharging
        BatteryManager.BATTERY_STATUS_FULL -> R.string.battery_status_full
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> R.string.battery_status_not_charging
        else -> R.string.battery_status_unknown
    }

    private fun formattedHours(hours: Double): String {
        if (hours.isNaN() || hours <= 0.0) return getString(R.string.battery_no_estimate)
        val totalMinutes = (hours * 60.0).toLong()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0L) "${h}h ${m}m" else "${m}m"
    }
}