package com.dalim.datalimit.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dalim.datalimit.core.UsagePrefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = UsagePrefs(context)
            if (prefs.monitoringEnabled) {
                TrafficMonitorService.start(context)
            }
        }
    }
}