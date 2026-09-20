package com.dalim.datalimit.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dalim.datalimit.BuildConfig
import com.dalim.datalimit.R
import com.dalim.datalimit.core.LocaleHelper
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.data.SqliteNotificationStore
import com.dalim.datalimit.monitor.TrafficMonitorService
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: UsagePrefs

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.resolve(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = UsagePrefs(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<TextView>(R.id.versionText).text = getString(R.string.version_fmt, BuildConfig.VERSION_NAME)

        findViewById<MaterialButtonToggleGroup>(R.id.languageGroup).check(
            if (prefs.language == LocaleHelper.LANG_ID) R.id.btnIndonesia else R.id.btnEnglish
        )
        wire()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionRows()
    }

    private fun wire() {
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
                        recreate()
                    }
                }
            }

        findViewById<android.view.View>(R.id.btnGrantPermissions).setOnClickListener {
            requestPermissionsIfNeeded()
        }

        findViewById<android.view.View>(R.id.btnResetData).setOnClickListener {
            TrafficMonitorService.reset(this)
            prefs.resetCounter()
            Snackbar.make(findViewById(R.id.toolbar), getString(R.string.snack_reset), Snackbar.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.btnResetVault).setOnClickListener {
            Thread {
                SqliteNotificationStore(applicationContext).clearAll()
                runOnUiThread {
                    Snackbar.make(findViewById(R.id.toolbar), getString(R.string.vault_cleared), Snackbar.LENGTH_SHORT).show()
                }
            }.start()
        }
    }

    private fun refreshPermissionRows() {
        val usage = findViewById<TextView>(R.id.usageGrantStatus)
        if (NetworkStatsReaderPermission.check(this)) {
            usage.text = getString(R.string.granted)
            usage.setTextColor(resources.getColor(R.color.accent))
        } else {
            usage.text = getString(R.string.grant)
            usage.setTextColor(resources.getColor(R.color.danger))
        }

        val overlay = findViewById<TextView>(R.id.overlayGrantStatus)
        if (Settings.canDrawOverlays(this)) {
            overlay.text = getString(R.string.granted)
            overlay.setTextColor(resources.getColor(R.color.accent))
        } else {
            overlay.text = getString(R.string.grant)
            overlay.setTextColor(resources.getColor(R.color.danger))
        }
    }

    private fun requestPermissionsIfNeeded() {
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
        refreshPermissionRows()
    }
}