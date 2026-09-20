package com.dalim.datalimit.service

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dalim.datalimit.core.LocaleHelper
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.core.VaultEntry
import com.dalim.datalimit.data.INotificationStore
import com.dalim.datalimit.data.SqliteNotificationStore

class VaultListenerService : NotificationListenerService() {

    private lateinit var store: INotificationStore
    private lateinit var prefs: UsagePrefs

    override fun onCreate() {
        super.onCreate()
        val app = LocaleHelper.resolve(applicationContext)
        prefs = UsagePrefs(app)
        store = SqliteNotificationStore(app)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        val n = sbn.notification ?: return
        if (!prefs.vaultEnabled) return

        if (n.isGroupSummary()) return
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if (sbn.isOngoing()) return

        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?: extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim() ?: ""
        if (title.isEmpty() && text.isEmpty()) return

        store.insert(
            VaultEntry(
                packageName = sbn.packageName,
                appName = appNameFor(sbn.packageName),
                title = title.take(200),
                text = text.take(400),
                channel = n.channelId.orEmpty(),
                postedAtMillis = sbn.postTime,
                importance = n.importance
            )
        )
        runRetentionIfDue()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        if (!prefs.vaultEnabled) return
        store.markRemoved(sbn.packageName, sbn.postTime)
    }

    private fun appNameFor(packageName: String): String {
        val pm = packageManager
        return try {
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun runRetentionIfDue() {
        val retentionDays = prefs.vaultRetentionDays
        val cutoffMillis = System.currentTimeMillis() - retentionDays * 86_400_000L
        val lastRun = prefs.vaultLastCleanup
        val now = System.currentTimeMillis()
        if (lastRun <= 0L || now - lastRun >= CLEANUP_INTERVAL_MS) {
            store.cleanupBefore(cutoffMillis)
            prefs.vaultLastCleanup = now
        }
    }

    companion object {
        private const val CLEANUP_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}