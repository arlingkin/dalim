package com.dalim.datalimit.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.dalim.datalimit.BuildConfig
import com.dalim.datalimit.R
import com.dalim.datalimit.core.LocaleHelper
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.data.ConfigExchange
import com.dalim.datalimit.data.SqliteNotificationStore
import com.dalim.datalimit.monitor.TrafficMonitorService
import com.dalim.datalimit.service.FirewallVpnService
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: UsagePrefs

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) doExport(uri)
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) doImport(uri)
    }

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
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchReduceMotion)
            .isChecked = prefs.reduceMotion
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

        findViewById<android.view.View>(R.id.btnExportConfig).setOnClickListener {
            exportLauncher.launch("dalim-config.json")
        }

        findViewById<android.view.View>(R.id.btnImportConfig).setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
        }

        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchReduceMotion)
            .setOnCheckedChangeListener { _, checked ->
                prefs.reduceMotion = checked
            }
    }

    /** Write the current prefs as a JSON config to the user-chosen [uri]. */
    private fun doExport(uri: Uri) {
        Thread {
            val json = ConfigExchange.toJson(prefs)
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                runOnUiThread {
                    Snackbar.make(
                        findViewById(R.id.toolbar),
                        getString(R.string.snack_config_exported),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    Snackbar.make(
                        findViewById(R.id.toolbar),
                        getString(R.string.snack_config_export_failed),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }.apply { isDaemon = true }.start()
    }

    /**
     * Read + validate a config file, then apply it atomically and restart the
     * monitor (mirrors the language-change restart) and the firewall tunnel.
     */
    private fun doImport(uri: Uri) {
        Thread {
            val raw = try {
                contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (_: Exception) {
                null
            }
            if (raw.isNullOrBlank()) {
                runOnUiThread {
                    Snackbar.make(
                        findViewById(R.id.toolbar),
                        getString(R.string.snack_config_invalid),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                return@Thread
            }

            val patch = ConfigExchange.fromJson(raw).getOrNull()
            if (patch == null) {
                runOnUiThread {
                    Snackbar.make(
                        findViewById(R.id.toolbar),
                        getString(R.string.snack_config_invalid),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                return@Thread
            }

            val wasMonitoring = prefs.monitoringEnabled
            prefs.apply(patch)
            if (wasMonitoring) {
                TrafficMonitorService.stop(this)
                TrafficMonitorService.start(this)
            }
            if (patch.firewallEnabled || prefs.firewallEnabled) {
                // Rebuild so the tunnel reflects the imported blocklist/budgets.
                FirewallVpnService.requestRebuild(this)
            }
            runOnUiThread {
                Snackbar.make(
                    findViewById(R.id.toolbar),
                    getString(R.string.snack_config_imported),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }.apply { isDaemon = true }.start()
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