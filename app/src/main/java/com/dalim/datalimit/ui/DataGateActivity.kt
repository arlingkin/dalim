package com.dalim.datalimit.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import com.dalim.datalimit.R
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.monitor.TrafficMonitorService
import com.dalim.datalimit.monitor.TrafficReader
import com.dalim.datalimit.monitor.UsageMatcher

class DataGateActivity : Activity() {

    private lateinit var prefs: UsagePrefs
    private lateinit var matcher: UsageMatcher
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 5_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gate)
        prefs = UsagePrefs(this)
        matcher = UsageMatcher(prefs)

        findViewById<View>(R.id.btnAllow).setOnClickListener {
            prefs.extraAllowanceMb += 100L
            render()
            handler.removeCallbacks(refresh)
            finish()
        }
        findViewById<View>(R.id.btnReset).setOnClickListener {
            prefs.resetCounter()
            handler.removeCallbacks(refresh)
            finish()
        }
        findViewById<View>(R.id.btnDismiss).setOnClickListener {
            TrafficMonitorService.stop(this)
            handler.removeCallbacks(refresh)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
        handler.postDelayed(refresh, 5_000L)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }

    private fun render() {
        val report = matcher.compute(System.currentTimeMillis())
        findViewById<TextView>(R.id.gateInfo).text =
            "${TrafficReader.formatBytes(report.consumedBytes)} used · limit ${TrafficReader.formatBytes(report.effectiveLimitBytes)}"
    }
}