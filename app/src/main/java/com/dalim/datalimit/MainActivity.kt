package com.dalim.datalimit

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.dalim.datalimit.core.LocaleHelper
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.monitor.GateOverlayService
import com.dalim.datalimit.monitor.TrafficMonitorService
import com.dalim.datalimit.monitor.TrafficReader
import com.dalim.datalimit.monitor.UsageMatcher
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: UsagePrefs
    private lateinit var matcher: UsageMatcher

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

    private val handler = Handler(Looper.getMainLooper())
    private val refreshTick = object : Runnable {
        override fun run() {
            render()
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
        setContentView(R.layout.activity_main)

        prefs = UsagePrefs(this)
        matcher = UsageMatcher(prefs, LocaleHelper.resolve(applicationContext))

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

        loadSettingsIntoUi()
        wireListeners()
    }

    override fun onResume() {
        super.onResume()
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

    private fun loadSettingsIntoUi() {
        val s = prefs.settings()

        applyingLimit = true
        limitInput.setText(s.limitMb.toString())
        applyingLimit = false

        findViewById<MaterialButtonToggleGroup>(R.id.periodGroup).check(
            when (s.period) {
                com.dalim.datalimit.core.Period.DAILY -> R.id.btnDaily
                com.dalim.datalimit.core.Period.WEEKLY -> R.id.btnWeekly
                com.dalim.datalimit.core.Period.MONTHLY -> R.id.btnMonthly
            }
        )
        findViewById<MaterialButtonToggleGroup>(R.id.windowGroup).check(
            if (s.windowStyle == com.dalim.datalimit.core.WindowStyle.FIXED) R.id.btnFixed else R.id.btnRolling
        )
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchGate).isChecked = s.gateEnabled
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchNotify).isChecked = s.notificationsEnabled
        findViewById<MaterialButtonToggleGroup>(R.id.languageGroup).check(
            if (prefs.language == LocaleHelper.LANG_ID) R.id.btnIndonesia else R.id.btnEnglish
        )
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

        findViewById<MaterialButtonToggleGroup>(R.id.languageGroup)
            .addOnButtonCheckedListener { group, checkedId, isChecked ->
                if (isChecked) {
                    val lang = if (checkedId == R.id.btnIndonesia) LocaleHelper.LANG_ID else LocaleHelper.LANG_EN
                    if (lang != prefs.language) {
                        prefs.language = lang
                        Snackbar.make(
                            findViewById(R.id.toolbar),
                            getString(R.string.snack_language_changed),
                            Snackbar.LENGTH_SHORT
                        ).show()
                        if (prefs.monitoringEnabled) {
                            TrafficMonitorService.stop(this)
                            TrafficMonitorService.start(this)
                        }
                        handler.removeCallbacks(refreshTick)
                        recreate()
                    }
                }
            }

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

    private fun currentPeriod(): com.dalim.datalimit.core.Period = when (
        findViewById<MaterialButtonToggleGroup>(R.id.periodGroup).checkedButtonId
    ) {
        R.id.btnWeekly -> com.dalim.datalimit.core.Period.WEEKLY
        R.id.btnMonthly -> com.dalim.datalimit.core.Period.MONTHLY
        else -> com.dalim.datalimit.core.Period.DAILY
    }

    private fun currentStyle(): com.dalim.datalimit.core.WindowStyle = when (
        findViewById<MaterialButtonToggleGroup>(R.id.windowGroup).checkedButtonId
    ) {
        R.id.btnRolling -> com.dalim.datalimit.core.WindowStyle.ROLLING
        else -> com.dalim.datalimit.core.WindowStyle.FIXED
    }

    private fun refreshSettings() {
        // Re-anchor the counting window when the period/style changes.
        prefs.resetCounter()
        render()
    }

    private fun render() {
        val report = matcher.compute(System.currentTimeMillis())
        val settings = prefs.settings()

        usedText.text = TrafficReader.formatBytes(report.consumedBytes)
        limitText.text = getString(R.string.limit_label, TrafficReader.formatBytes(report.effectiveLimitBytes))
        remainingText.text =
            if (report.limitActive) getString(R.string.left_label, TrafficReader.formatBytes(report.remainingBytes))
            else getString(R.string.no_limit_set)

        if (report.limitActive) {
            percentText.text = "${report.usedPercent.coerceAtMost(999)}%"
            gateProgress.max = 100
            gateProgress.progress = report.usedPercent.coerceAtMost(100)
            val exceeded = report.exceeded
            gateProgress.progressTintList = android.content.res.ColorStateList.valueOf(
                if (exceeded) resources.getColor(R.color.danger)
                else if (report.usedPercent >= 80) resources.getColor(R.color.warn)
                else resources.getColor(R.color.accent)
            )
        } else {
            percentText.text = getString(R.string.no_limit)
            gateProgress.progress = 0
            gateProgress.progressTintList =
                android.content.res.ColorStateList.valueOf(resources.getColor(R.color.accent))
        }

        windowText.text = getString(R.string.window_label, report.windowLabel)
        rxText.text = "▼ " + TrafficReader.formatBytes(report.radiosRxBytes)
        txText.text = "▲ " + TrafficReader.formatBytes(report.radiosTxBytes)

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

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestPermissionsIfNeeded() {
        ensureNotificationPermission()

        val root = findViewById<android.view.View>(R.id.toolbar)

        if (!isUsageAccessGranted()) {
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

    private fun isUsageAccessGranted(): Boolean {
        if (Build.VERSION.SDK_INT < 21) return true
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}