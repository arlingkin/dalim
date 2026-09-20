package com.dalim.datalimit.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.dalim.datalimit.R
import com.dalim.datalimit.core.LocaleHelper
import com.dalim.datalimit.core.PackageTotal
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.core.util.ByteFormat
import com.dalim.datalimit.core.util.PeriodRange
import com.dalim.datalimit.data.NetworkStatsReader
import com.dalim.datalimit.service.FirewallVpnService
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class AppControlActivity : AppCompatActivity() {

    private lateinit var prefs: UsagePrefs
    private lateinit var reader: NetworkStatsReader

    private lateinit var appList: LinearLayout
    private lateinit var loadingText: TextView
    private lateinit var appDataEmpty: TextView
    private lateinit var usagePermText: TextView
    private lateinit var btnUsagePermission: MaterialButton
    private lateinit var firewallStatus: TextView
    private lateinit var vpnPermText: TextView
    private lateinit var btnVpnAllow: MaterialButton

    private var apps: List<PackageTotal> = emptyList()

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            FirewallVpnService.start(this)
        } else {
            prefs.firewallEnabled = false
            findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchFirewall).isChecked = false
        }
        refreshFirewallStatus()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.resolve(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appcontrol)

        prefs = UsagePrefs(this)
        reader = NetworkStatsReader(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        appList = findViewById(R.id.appList)
        loadingText = findViewById(R.id.loadingText)
        appDataEmpty = findViewById(R.id.appDataEmpty)
        usagePermText = findViewById(R.id.usagePermText)
        btnUsagePermission = findViewById(R.id.btnUsagePermission)
        firewallStatus = findViewById(R.id.firewallStatus)
        vpnPermText = findViewById(R.id.vpnPermText)
        btnVpnAllow = findViewById(R.id.btnVpnAllow)

        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchFirewall).isChecked = prefs.firewallEnabled
        wire()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun wire() {
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchFirewall)
            .setOnCheckedChangeListener { _, checked ->
                prefs.firewallEnabled = checked
                if (checked) {
                    if (!FirewallVpnService.prepared(this)) {
                        FirewallVpnService.requestIntent(this)?.let { vpnPermission.launch(it) }
                    } else {
                        FirewallVpnService.start(this)
                    }
                } else {
                    FirewallVpnService.stop(this)
                }
                refreshFirewallStatus()
            }

        btnVpnAllow.setOnClickListener {
            FirewallVpnService.requestIntent(this)?.let { vpnPermission.launch(it) }
        }

        btnUsagePermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshFirewallStatus()
        refreshApps()
    }

    private fun refreshFirewallStatus() {
        val firewallRunning = prefs.firewallEnabled
        val vpnRunning = FirewallVpnService.running(this)
        firewallStatus.text = getString(
            when {
                firewallRunning && vpnRunning -> R.string.firewall_running
                firewallRunning -> R.string.firewall_idle
                else -> R.string.firewall_off
            }
        )
        val needsPerm = firewallRunning && !FirewallVpnService.prepared(this)
        vpnPermText.visibility = if (needsPerm) View.VISIBLE else View.GONE
        btnVpnAllow.visibility = if (needsPerm) View.VISIBLE else View.GONE
    }

    private fun refreshApps() {
        if (!NetworkStatsReader.collectionEnabled(this)) {
            usagePermText.visibility = View.VISIBLE
            btnUsagePermission.visibility = View.VISIBLE
            loadingText.visibility = View.GONE
            appDataEmpty.visibility = View.GONE
            appList.removeAllViews()
            return
        }
        usagePermText.visibility = View.GONE
        btnUsagePermission.visibility = View.GONE
        loadingText.visibility = View.VISIBLE
        appDataEmpty.visibility = View.GONE

        val start = PeriodRange.startOfMonthMillis(System.currentTimeMillis())
        val end = System.currentTimeMillis()
        Thread {
            val result = try {
                reader.fetchAll(start, end)
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                loadingText.visibility = View.GONE
                apps = result.sortedByDescending { it.rxBytes + it.txBytes }
                renderApps()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun renderApps() {
        appList.removeAllViews()
        if (apps.isEmpty()) {
            appDataEmpty.visibility = View.VISIBLE
            return
        }
        appDataEmpty.visibility = View.GONE
        for (total in apps) {
            appList.addView(appRow(total), ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun appRow(total: PackageTotal): LinearLayout {
        val pkg = total.packageName
        val usedBytes = total.rxBytes + total.txBytes
        val blocked = prefs.blockedApps.contains(pkg)
        val budgetMb = prefs.appBudgetMb(pkg)
        val budgetExceeded = budgetMb > 0L && usedBytes >= budgetMb * 1024L * 1024L

        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setPadding(0, dp(10), 0, dp(10))

        val nameRow = LinearLayout(this)
        nameRow.orientation = LinearLayout.HORIZONTAL
        nameRow.gravity = android.view.Gravity.CENTER_VERTICAL

        val name = TextView(this)
        name.text = reader.appLabel(pkg)
        name.setTextColor(0xFF202124.toInt())
        name.textSize = 15f
        name.setTypeface(null, android.graphics.Typeface.BOLD)
        name.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        nameRow.addView(name)

        val used = TextView(this)
        used.text =
            if (budgetExceeded) getString(R.string.budget_exceeded) + " · " + getString(R.string.used_fmt, ByteFormat.format(usedBytes))
            else getString(R.string.used_fmt, ByteFormat.format(usedBytes))
        used.setTextColor(if (budgetExceeded) 0xFFD32F2F.toInt() else 0xFF5F6368.toInt())
        used.textSize = 12f
        nameRow.addView(used)

        wrap.addView(nameRow)

        if (blocked) {
            val until = prefs.temporaryAllowUntil(pkg)
            if (until > System.currentTimeMillis()) {
                val status = TextView(this)
                status.text = getString(R.string.temp_until_fmt, timeLabel(until))
                status.setTextColor(0xFFF9A825.toInt())
                status.textSize = 11f
                wrap.addView(status)
            }
        }

        val controls = LinearLayout(this)
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = android.view.Gravity.CENTER_VERTICAL
        controls.setPadding(0, dp(2), 0, 0)

        val budgetInput = EditText(this)
        budgetInput.hint = getString(R.string.budget_hint)
        budgetInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        budgetInput.setText(if (budgetMb > 0L) budgetMb.toString() else "")
        budgetInput.setTextColor(0xFF202124.toInt())
        budgetInput.textSize = 12f
        budgetInput.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        controls.addView(budgetInput)

        val applyBtn = smallButton(getString(R.string.apply), 0xFF0D47A1.toInt()) {
            val mb = budgetInput.text.toString().toLongOrNull()
            prefs.setAppBudgetMb(pkg, mb ?: 0L)
            refreshApps()
        }
        controls.addView(applyBtn)

        val toggleBtn = smallButton(
            if (blocked) getString(R.string.allow_data) else getString(R.string.block_data),
            if (blocked) 0xFF0D47A1.toInt() else 0xFFD32F2F.toInt()
        ) {
            if (blocked) prefs.unblockApp(pkg) else prefs.blockApp(pkg)
            FirewallVpnService.requestRebuild(this)
            refreshApps()
        }
        controls.addView(toggleBtn)

        if (blocked) {
            val tempBtn = smallButton(getString(R.string.allow_temp), 0xFF5F6368.toInt()) {
                prefs.setTemporaryAllow(pkg, System.currentTimeMillis() + 30L * 60_000L)
                FirewallVpnService.requestRebuild(this)
                refreshApps()
            }
            controls.addView(tempBtn)
        }

        wrap.addView(controls)
        return wrap
    }

    private fun smallButton(label: String, color: Int, onClick: () -> Unit): MaterialButton {
        val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btn.text = label
        btn.minHeight = 0
        btn.setTextColor(color)
        btn.setPadding(dp(6), 0, dp(6), 0)
        btn.textSize = 12f
        btn.setOnClickListener { onClick() }
        return btn
    }

    private fun timeLabel(millis: Long): String {
        val time = android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(millis))
        return time
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}