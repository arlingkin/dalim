package com.dalim.datalimit.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.app.TimePickerDialog
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.dalim.datalimit.R
import com.dalim.datalimit.core.LocaleHelper
import com.dalim.datalimit.core.PackageTotal
import com.dalim.datalimit.core.Period
import com.dalim.datalimit.core.Schedule
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.core.WindowStyle
import com.dalim.datalimit.core.util.ByteFormat
import com.dalim.datalimit.data.NetworkStatsReader
import com.dalim.datalimit.monitor.GateOverlayService
import com.dalim.datalimit.monitor.TrafficMonitorService
import com.dalim.datalimit.monitor.UsageMatcher
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class DataActivity : AppCompatActivity() {

    private lateinit var prefs: UsagePrefs
    private lateinit var matcher: UsageMatcher
    private var lastConsumedBytes = -1L
    private var lastUsedPercent = -1

    private lateinit var usedText: TextView
    private lateinit var percentText: TextView
    private lateinit var remainingText: TextView
    private lateinit var limitText: TextView
    private lateinit var gateProgress: ProgressBar
    private lateinit var windowText: TextView
    private lateinit var rxText: TextView
    private lateinit var txText: TextView
    private lateinit var statusText: TextView
    private lateinit var lastCheckText: TextView
    private lateinit var limitInput: EditText
    private lateinit var appChartList: LinearLayout
    private lateinit var appChartEmpty: TextView

    private val reader = NetworkStatsReader(applicationContext)
    private var lastChartAtMillis = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val refreshTick = object : Runnable {
        override fun run() {
            try {
                render()
                if (System.currentTimeMillis() - lastChartAtMillis >= 30_000L) {
                    refreshAppChart()
                }
            } catch (_: Throwable) {
                // Render failures must never take the screen down; recover on
                // the next 5 s tick.
            }
            handler.postDelayed(this, 5_000L)
        }
    }

    private var applyingLimit = false

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.resolve(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data)

        prefs = UsagePrefs(this)
        matcher = UsageMatcher(prefs, LocaleHelper.resolve(applicationContext))

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        usedText = findViewById(R.id.usedText)
        percentText = findViewById(R.id.percentText)
        remainingText = findViewById(R.id.remainingText)
        limitText = findViewById(R.id.limitText)
        gateProgress = findViewById(R.id.gateProgress)
        windowText = findViewById(R.id.windowText)
        rxText = findViewById(R.id.rxText)
        txText = findViewById(R.id.txText)
        statusText = findViewById(R.id.statusText)
        lastCheckText = findViewById(R.id.lastCheckText)
        limitInput = findViewById(R.id.limitInput)
        appChartList = findViewById(R.id.appChartList)
        appChartEmpty = findViewById(R.id.appChartEmpty)

        loadSettingsIntoUi()
        wireListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        render()
        refreshAppChart()
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

    private fun loadSettingsIntoUi() {
        val s = prefs.settings()

        applyingLimit = true
        limitInput.setText(s.limitMb.toString())
        applyingLimit = false

        findViewById<MaterialButtonToggleGroup>(R.id.periodGroup).check(
            when (s.period) {
                Period.DAILY -> R.id.btnDaily
                Period.WEEKLY -> R.id.btnWeekly
                Period.MONTHLY -> R.id.btnMonthly
            }
        )
        findViewById<MaterialButtonToggleGroup>(R.id.windowGroup).check(
            if (s.windowStyle == WindowStyle.FIXED) R.id.btnFixed else R.id.btnRolling
        )
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchGate).isChecked = s.gateEnabled
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchNotify).isChecked = s.notificationsEnabled

        loadScheduleIntoUi()
    }

    private fun wireListeners() {
        findViewById<MaterialButtonToggleGroup>(R.id.periodGroup).addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) {
                prefs.period = currentPeriod()
                refreshSettings()
            }
        }
        findViewById<MaterialButtonToggleGroup>(R.id.windowGroup).addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) {
                prefs.windowStyle = currentStyle()
                refreshSettings()
            }
        }
        limitInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (applyingLimit) return
                val v = s?.toString()?.toLongOrNull()
                prefs.limitMb = (v ?: 0L).coerceAtLeast(0L)
                render()
            }
        })
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchGate)
            .setOnCheckedChangeListener { _, checked -> prefs.gateEnabled = checked }
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchNotify)
            .setOnCheckedChangeListener { _, checked -> prefs.notificationsEnabled = checked }

        findViewById<android.view.View>(R.id.btnAppControl).setOnClickListener {
            startActivity(Intent(this, AppControlActivity::class.java))
        }

        findViewById<android.view.View>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        wireSchedule()

        findViewById<android.view.View>(R.id.btnPermissions).setOnClickListener {
            requestPermissionsIfNeeded()
        }
        findViewById<android.view.View>(R.id.btnStart).setOnClickListener {
            ensureNotificationPermission()
            if (Settings.canDrawOverlays(this).not()) {
                Snackbar.make(
                    findViewById(R.id.toolbar),
                    getString(R.string.snack_overlay_rationale),
                    Snackbar.LENGTH_LONG
                ).setAction(getString(R.string.snack_allow_now)) {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }.show()
            } else {
                Snackbar.make(findViewById(R.id.toolbar), getString(R.string.snack_started), Snackbar.LENGTH_SHORT).show()
            }
            TrafficMonitorService.start(this)
        }
        findViewById<android.view.View>(R.id.btnStop).setOnClickListener {
            TrafficMonitorService.stop(this)
            GateOverlayService.stop(this)
            Snackbar.make(findViewById(R.id.toolbar), getString(R.string.snack_stopped), Snackbar.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.btnReset).setOnClickListener {
            TrafficMonitorService.reset(this)
            prefs.resetCounter()
            render()
            Snackbar.make(findViewById(R.id.toolbar), getString(R.string.snack_reset), Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun loadScheduleIntoUi() {
        val style = prefs.scheduleWindowStyle
        findViewById<MaterialButtonToggleGroup>(R.id.scheduleGroup).check(
            when (style) {
                Schedule.WindowStyle.WEEKDAYS -> R.id.btnScheduleWeekdays
                Schedule.WindowStyle.WEEKEND -> R.id.btnScheduleWeekend
                Schedule.WindowStyle.EVERYDAY -> R.id.btnScheduleEveryday
                Schedule.WindowStyle.CUSTOM -> R.id.btnScheduleCustom
                else -> R.id.btnScheduleOff
            }
        )
        renderSchedule()
    }

    private fun wireSchedule() {
        findViewById<MaterialButtonToggleGroup>(R.id.scheduleGroup).addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) {
                prefs.scheduleWindowStyle = currentScheduleStyle()
                prefs.scheduleSnoozeUntilMillis = 0L
                renderSchedule()
            }
        }
        findViewById<android.view.View>(R.id.btnScheduleStart).setOnClickListener {
            showTimePicker(persistStart = true)
        }
        findViewById<android.view.View>(R.id.btnScheduleEnd).setOnClickListener {
            showTimePicker(persistStart = false)
        }
    }

    private fun currentScheduleStyle(): Schedule.WindowStyle = when (
        findViewById<MaterialButtonToggleGroup>(R.id.scheduleGroup).checkedButtonId
    ) {
        R.id.btnScheduleWeekdays -> Schedule.WindowStyle.WEEKDAYS
        R.id.btnScheduleWeekend -> Schedule.WindowStyle.WEEKEND
        R.id.btnScheduleEveryday -> Schedule.WindowStyle.EVERYDAY
        R.id.btnScheduleCustom -> Schedule.WindowStyle.CUSTOM
        else -> Schedule.WindowStyle.OFF
    }

    private fun renderSchedule() {
        val style = prefs.scheduleWindowStyle
        findViewById<View>(R.id.scheduleCustomRow).visibility =
            if (style == Schedule.WindowStyle.CUSTOM) View.VISIBLE else View.GONE
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnScheduleStart).text =
            getString(R.string.schedule_custom_start, scheduleClock(prefs.scheduleStartMin))
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnScheduleEnd).text =
            getString(R.string.schedule_custom_end, scheduleClock(prefs.scheduleEndMin))
    }

    private fun scheduleClock(minutes: Int): String {
        val h = (minutes / 60).coerceIn(0, 23)
        val m = minutes % 60
        return String.format(java.util.Locale.ROOT, "%02d:%02d", h, m)
    }

    private fun showTimePicker(persistStart: Boolean) {
        val current = if (persistStart) prefs.scheduleStartMin else prefs.scheduleEndMin
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val value = hourOfDay * 60 + minute
                if (persistStart) prefs.scheduleStartMin = value else prefs.scheduleEndMin = value
                prefs.scheduleSnoozeUntilMillis = 0L
                renderSchedule()
            },
            current / 60,
            current % 60,
            android.text.format.DateFormat.is24HourFormat(this)
        ).show()
    }

    private fun currentPeriod(): Period = when (
        findViewById<MaterialButtonToggleGroup>(R.id.periodGroup).checkedButtonId
    ) {
        R.id.btnWeekly -> Period.WEEKLY
        R.id.btnMonthly -> Period.MONTHLY
        else -> Period.DAILY
    }

    private fun currentStyle(): WindowStyle = when (
        findViewById<MaterialButtonToggleGroup>(R.id.windowGroup).checkedButtonId
    ) {
        R.id.btnRolling -> WindowStyle.ROLLING
        else -> WindowStyle.FIXED
    }

    private fun refreshSettings() {
        prefs.resetCounter()
        render()
    }

    private fun render() {
        val report = matcher.compute(System.currentTimeMillis())
        val animate = Anim.enabled(prefs)

        Anim.countTo(usedText, lastConsumedBytes, report.consumedBytes, { ByteFormat.format(it) }, animate)
        if (lastConsumedBytes != report.consumedBytes) lastConsumedBytes = report.consumedBytes
        limitText.text = getString(R.string.limit_label, ByteFormat.format(report.effectiveLimitBytes))
        remainingText.text =
            if (report.limitActive) getString(R.string.left_label, ByteFormat.format(report.remainingBytes))
            else getString(R.string.no_limit_set)

        if (report.limitActive) {
            Anim.countTo(percentText, lastUsedPercent, report.usedPercent.coerceAtMost(999), { "$it%" }, animate)
            lastUsedPercent = report.usedPercent.coerceAtMost(999)
            gateProgress.max = 100
            Anim.animateProgress(gateProgress, report.usedPercent.coerceAtMost(100), animate)
            val exceeded = report.exceeded
            gateProgress.progressTintList = android.content.res.ColorStateList.valueOf(
                if (exceeded) resources.getColor(R.color.danger)
                else if (report.usedPercent >= 80) resources.getColor(R.color.warn)
                else resources.getColor(R.color.accent)
            )
        } else {
            percentText.text = getString(R.string.no_limit)
            Anim.animateProgress(gateProgress, 0, animate)
            gateProgress.progressTintList =
                android.content.res.ColorStateList.valueOf(resources.getColor(R.color.accent))
        }

        windowText.text = getString(R.string.window_label, report.windowLabel)
        rxText.text = "▼ " + ByteFormat.format(report.radiosRxBytes)
        txText.text = "▲ " + ByteFormat.format(report.radiosTxBytes)

        val overlayOk = Settings.canDrawOverlays(this)
        statusText.text = when {
            !prefs.monitoringEnabled -> getString(R.string.status_off)
            !overlayOk -> getString(R.string.status_active_no_overlay)
            else -> getString(R.string.status_active)
        }
        statusText.setTextColor(
            resources.getColor(
                when {
                    !prefs.monitoringEnabled -> R.color.danger
                    !overlayOk -> R.color.warn
                    else -> R.color.accent
                }
            )
        )

        val last = prefs.lastCheckMillis
        lastCheckText.text = if (last > 0L) getString(
            R.string.last_check_fmt,
            android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(last))
        ) else getString(R.string.last_check_empty)
    }

    private fun refreshAppChart() {
        lastChartAtMillis = System.currentTimeMillis()
        val start = periodStartMillis(lastChartAtMillis)
        Thread {
            val data = reader.fetchAll(start, lastChartAtMillis)
                .sortedByDescending { it.totalBytes }
                .take(MAX_APP_ROWS)
            runOnUiThread { renderAppChart(data) }
        }.apply { isDaemon = true }.start()
    }

    private fun periodStartMillis(now: Long): Long {
        val zone = ZoneId.systemDefault()
        return when (prefs.period) {
            Period.WEEKLY -> LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(zone).toInstant().toEpochMilli()
            Period.MONTHLY -> LocalDate.now()
                .withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
            Period.DAILY ->
                if (prefs.windowStyle == WindowStyle.FIXED) {
                    LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
                } else {
                    val anchor = prefs.rollingAnchorMillis
                    if (anchor > 0L) anchor else now - 86_400_000L
                }
        }
    }

    private fun renderAppChart(data: List<PackageTotal>) {
        appChartList.removeAllViews()
        if (data.isEmpty()) {
            appChartEmpty.visibility = View.VISIBLE
            return
        }
        appChartEmpty.visibility = View.GONE
        val density = resources.displayMetrics.density
        val trackWidth = (96f * density).toInt()
        val max = data.first().totalBytes.coerceAtLeast(1L)
        val animate = Anim.enabled(prefs)

        for (item in data) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            row.setPadding(0, (5f * density).toInt(), 0, (5f * density).toInt())
            row.alpha = 0f

            val label = TextView(this)
            label.text = reader.appLabel(item.packageName)
            label.setTextColor(0xFF202124.toInt())
            label.textSize = 13f
            label.maxLines = 1
            label.ellipsize = TextUtils.TruncateAt.END
            row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val value = TextView(this)
            value.text = ByteFormat.format(item.totalBytes)
            value.setTextColor(0xFF0D47A1.toInt())
            value.textSize = 12f
            row.addView(value, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (10f * density).toInt() })

            val track = FrameLayout(this)
            track.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 3f * density
                setColor(0xFFE8EAED.toInt())
            }
            val percent = (item.totalBytes.toFloat() / max)
            val inner = View(this)
            inner.setBackgroundColor(resources.getColor(R.color.accent))
            val targetWidth = (trackWidth * percent).coerceAtLeast(1f).toInt()
            track.addView(inner, FrameLayout.LayoutParams(
                0,
                (8f * density).toInt()
            ))
            row.addView(track, LinearLayout.LayoutParams(
                trackWidth, (10f * density).toInt()
            ).apply { marginStart = (10f * density).toInt() })

            appChartList.addView(row)

            row.animate()
                .alpha(1f)
                .setDuration(220L)
                .start()
            Anim.growWidth(inner, targetWidth, animate)
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestPermissionsIfNeeded() {
        ensureNotificationPermission()

        val root = findViewById<android.view.View>(R.id.toolbar)

        if (!NetworkStatsReaderPermission.check(this)) {
            Snackbar.make(
                root,
                getString(R.string.snack_usage_rationale),
                Snackbar.LENGTH_LONG
            ).setAction(getString(R.string.snack_grant)) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }.show()
        }

        if (Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(this).not()) {
            Snackbar.make(
                root,
                getString(R.string.snack_overlay_grant),
                Snackbar.LENGTH_LONG
            ).setAction(getString(R.string.snack_grant)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }.show()
        }
    }
companion object {
        private const val MAX_APP_ROWS = 6
    }
}

object NetworkStatsReaderPermission {
    fun check(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 21) return true
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}