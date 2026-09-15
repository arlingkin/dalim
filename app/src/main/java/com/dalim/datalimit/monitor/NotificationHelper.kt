package com.dalim.datalimit.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dalim.datalimit.MainActivity
import com.dalim.datalimit.R
import com.dalim.datalimit.ui.DataGateActivity

object NotificationHelper {

    const val CHANNEL_MONITOR = "dalim_monitor"
    const val CHANNEL_ALERT = "dalim_alert"
    const val NOTIF_MONITOR = 1001
    const val NOTIF_ALERT = 1002

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val monitor = NotificationChannel(
            CHANNEL_MONITOR,
            "Monitoring status",
            NotificationManager.IMPORTANCE_MIN
        ).apply { description = "Live data usage status" }
        nm.createNotificationChannel(monitor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alert = NotificationChannel(
                CHANNEL_ALERT,
                "Data limit alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Fired when the configured data limit is reached"
                enableVibration(true)
            }
            nm.createNotificationChannel(alert)
        }
    }

    fun monitoringNotification(context: Context, text: String): Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Data Limit is watching")
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
            .setContentTitle("Data limit reached")
            .setContentText("Your configured data budget is used up.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setContentIntent(openGate)
            .addAction(0, "Open gate", openGate)
            .addAction(0, "App settings", openApp)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setFullScreenIntent(openGate, true)
        }
        return builder.build()
    }
}