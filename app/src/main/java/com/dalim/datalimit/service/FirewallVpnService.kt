package com.dalim.datalimit.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.dalim.datalimit.MainActivity
import com.dalim.datalimit.R
import com.dalim.datalimit.core.LocaleHelper
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.core.util.PeriodRange
import com.dalim.datalimit.data.NetworkStatsReader
import com.dalim.datalimit.monitor.NotificationHelper
import java.util.concurrent.Executors

/**
 * Legitimate network control via a real VpnService.
 *
 * Model: the VPN only routes applications that are on the blocklist
 * (addAllowedApplication = allowlist, but fed with the blocklist). Those apps
 * are pulled into the tunnel whose file descriptor is never read, so their
 * traffic is dropped. Every other app bypasses the tunnel entirely and is
 * untouched. This is the only way to genuinely stop a non-root app's data.
 */
class FirewallVpnService : VpnService() {

    private lateinit var prefs: UsagePrefs
    private var vpnFd: ParcelFileDescriptor? = null
    private var lastBlockSignature: Int = Int.MIN_VALUE
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.resolve(newBase))
    }

    @SuppressLint("WrongThread")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_VPN, firewallNotification())
        rebuild()
        return START_STICKY
    }

    override fun onRevoke() {
        teardown()
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        vpnFd?.let { runCatching { it.close() } }
        vpnFd = null
        super.onDestroy()
    }

    private fun rebuild() {
        handler.removeCallbacks(budgetTick)
        if (!prefs.firewallEnabled) {
            teardown()
            stopSelf()
            return
        }
        val allowedNow = effectiveAllowedNow()
        if (allowedNow.isEmpty()) {
            // Nothing to filter: no tunnel needed, but stay ready for changes.
            scheduleBudgetCheck()
            return
        }
        val installed = installedPackages()
        val allowed = allowedNow.filter { it in installed }
        if (allowed.isEmpty()) {
            scheduleBudgetCheck()
            return
        }
        val signature = allowed.hashCode()
        if (vpnFd != null && signature == lastBlockSignature) {
            scheduleBudgetCheck()
            return
        }
        vpnFd?.let { runCatching { it.close() } }
        vpnFd = null
        vpnFd = try {
            val builder = Builder()
                .setSession(getString(R.string.vpn_session))
                .setMtu(1280)
                .addAddress("10.10.10.14", 32)
                .addRoute("0.0.0.0", 0)
            builder.addRoute("::", 0)
            for (pkg in allowed) builder.addAllowedApplication(pkg)
            builder.establish()
        } catch (_: Exception) {
            null
        }
        lastBlockSignature = signature
        if (vpnFd == null) {
            // Tunnel could not be established (e.g. authorization revoked).
            teardown()
            return
        }
        scheduleBudgetCheck()
    }

    /** Blocklist minus temporary-allowances whose window has expired/did not start. */
    private fun effectiveAllowedNow(): List<String> {
        val now = System.currentTimeMillis()
        return prefs.blockedApps.filter { prefs.temporaryAllowUntil(it) <= now }
            .toList()
    }

    private fun installedPackages(): Set<String> {
        val pm = packageManager
        return try {
            pm.getInstalledApplications(0).map { it.packageName }.toSet()
        } catch (_: RuntimeException) {
            emptySet()
        }
    }

    private fun teardown() {
        handler.removeCallbacks(budgetTick)
        vpnFd?.let { runCatching { it.close() } }
        vpnFd = null
        lastBlockSignature = Int.MIN_VALUE
    }

    // ---- per-app budgets: auto-block when usage passes the budget ----

    private val budgetTick = object : Runnable {
        override fun run() {
            executor.execute {
                checkBudgets()
                handler.post(Runnable { scheduleBudgetCheck() })
            }
        }
    }

    private fun scheduleBudgetCheck() {
        handler.removeCallbacks(budgetTick)
        handler.postDelayed(budgetTick, BUDGET_CHECK_MILLIS)
    }

    private fun checkBudgets() {
        val budgeted = prefs.budgetedPackages()
        if (budgeted.isNotEmpty()) {
            val start = PeriodRange.startOfMonthMillis(System.currentTimeMillis())
            val end = System.currentTimeMillis()
            val reader = NetworkStatsReader(this)
            val newlyBlocked = budgeted.filter { (pkg, mb) ->
                mb > 0L && reader.totalBytesForPackage(pkg, start, end) >= mb * BYTES_PER_MB
            }.keys
            val before = prefs.blockedApps
            val after = before + newlyBlocked
            if (after != before) {
                prefs.blockedApps = after
                handler.post(Runnable { rebuild() })
            }
        }
        purgeExpiredTemporaryAllows()
    }

    private fun purgeExpiredTemporaryAllows() {
        val now = System.currentTimeMillis()
        for (pkg in prefs.blockedApps) {
            val until = prefs.temporaryAllowUntil(pkg)
            if (until in 1..now) prefs.setTemporaryAllow(pkg, 0L)
        }
    }

    private fun firewallNotification() =
        NotificationCompat.Builder(this, NotificationHelper.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.vpn_active))
            .setContentText(getString(R.string.notif_watching))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 5,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    companion object {
        const val NOTIF_VPN = 1006
        private const val BUDGET_CHECK_MILLIS = 60_000L
        private const val BYTES_PER_MB = 1024L * 1024L

        fun prepared(context: Context): Boolean =
            VpnService.prepare(context) == null

        fun requestIntent(context: Context): Intent? =
            VpnService.prepare(context)

        fun start(context: Context) {
            val i = Intent(context, FirewallVpnService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FirewallVpnService::class.java))
        }

        fun running(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }
}