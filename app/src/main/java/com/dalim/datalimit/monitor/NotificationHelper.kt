package com.dalim.datalimit.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dalim.datalimit.MainActivity
import com.dalim.datalimit.R
import com.dalim.datalimit.ui.DataGateActivity

object NotificationHelper {

    const val CHANNEL_MONITOR = "dalim_monitor"
    const val CHANNEL_ALERT = "dalim_alert"
    const val CHANNEL_BATTERY = "dalim_battery"
    const val NOTIF_MONITOR = 1001
    const val NOTIF_ALERT = 1002
    const val NOTIF_GATE = 1003
    const val NOTIF_BATTERY = 1004

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val monitor = NotificationChannel(
            CHANNEL_MONITOR,
            context.getString(R.string.channel_monitor_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply { description = context.getString(R.string.channel_monitor_desc) }
        nm.createNotificationChannel(monitor)

        val alert = NotificationChannel(
            CHANNEL_ALERT,
            context.getString(R.string.channel_alert_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alert_desc)
            enableVibration(true)
        }
        nm.createNotificationChannel(alert)

        val battery = NotificationChannel(
            CHANNEL_BATTERY,
            context.getString(R.string.channel_battery_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_battery_desc)
            enableVibration(true)
        }
        nm.createNotificationChannel(battery)
    }

    fun monitoringNotification(context: Context, text: String): Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_watching))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    fun alertNotification(context: Context): Notification {
        val openGate = PendingIntent.getActivity(
            context, 1,
            Intent(context, DataGateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openApp = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_limit_reached))
            .setContentText(context.getString(R.string.notif_limit_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setContentIntent(openGate)
            .addAction(0, context.getString(R.string.action_open_gate), openGate)
            .addAction(0, context.getString(R.string.action_app_settings), openApp)

        builder.setFullScreenIntent(openGate, true)
        return builder.build()
    }

    fun gateNotification(context: Context, text: String): Notification {
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, DataGateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_gate_active))
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
    }

    fun batteryAlertNotification(context: Context, levelPercent: Int, floorPercent: Int): Notification {
        val open = PendingIntent.getActivity(
            context, 4,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_BATTERY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_battery_floor))
            .setContentText(context.getString(R.string.notif_battery_body, floorPercent, levelPercent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }
}