package com.dalim.datalimit.monitor

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dalim.datalimit.core.LocaleHelper
import com.dalim.datalimit.core.UsagePrefs

class BatteryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
        val app = LocaleHelper.resolve(context.applicationContext)
        val prefs = UsagePrefs(app)
        val read = BatteryMonitor.readFromIntent(intent)
        if (read.level < 0) return

        val snap = BatteryMonitor(prefs).update(
            level = read.level,
            status = read.status,
            plugged = read.plugged,
            temperatureTenthsC = read.temperatureTenthsC,
            voltageMv = read.voltageMv
        )
        if (snap.alertTriggered && prefs.batteryAlertsEnabled) {
            NotificationHelper.createChannels(app)
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(
                NotificationHelper.NOTIF_BATTERY,
                NotificationHelper.batteryAlertNotification(
                    app,
                    snap.levelPercent,
                    prefs.batteryBudgetFloor
                )
            )
        }
    }
}