package com.dalim.datalimit.data

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import com.dalim.datalimit.core.PackageTotal

/**
 * Per-app network usage via NetworkStatsManager. Provides aggregated,
 * "estimated" totals (the OS reports measured aggregates, not a live stream).
 * Guards every query: without PACKAGE_USAGE_STATS this throws and we fall
 * back to an empty result so the UI can show the permission state.
 */
class NetworkStatsReader(private val context: Context) {

    private val nsm: NetworkStatsManager
        get() = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

    fun collectionEnabled(): Boolean = NetworkStatsReader.collectionEnabled(context)

    fun fetchAll(startMillis: Long, endMillis: Long): List<PackageTotal> {
        val uids = installedUids()
        val out = ArrayList<PackageTotal>(uids.size)
        for (uid in uids) {
            val traffic = queryUid(uid, startMillis, endMillis)
            if (traffic == null) continue
            val pkg = uidToPackage(uid) ?: continue
            if (pkg == context.packageName && traffic.rx + traffic.tx <= 0L) continue
            out.add(PackageTotal(pkg, traffic.rx, traffic.tx))
        }
        return out
    }

    fun totalBytesForPackage(packageName: String, startMillis: Long, endMillis: Long): Long {
        val uid = try {
            context.packageManager.getApplicationInfo(packageName, 0).uid
        } catch (_: PackageManager.NameNotFoundException) {
            return 0L
        }
        if (uid <= 0) return 0L
        return queryUid(uid, startMillis, endMillis)?.let { it.rx + it.tx } ?: 0L
    }

    private fun queryUid(uid: Int, startMillis: Long, endMillis: Long): Traffic? {
        if (!collectionEnabled()) return null
        var rx = 0L
        var tx = 0L
        for (networkType in networkTypes()) {
            try {
                val s = nsm.querySummaryForUid(
                    networkType,
                    subscriberId(networkType),
                    startMillis,
                    endMillis,
                    uid
                )
                try {
                    val bucket = NetworkStats.Bucket()
                    while (s.getNextBucket(bucket)) {
                        rx += bucket.rxBytes
                        tx += bucket.txBytes
                    }
                } finally {
                    s.close()
                }
            } catch (_: Exception) {
                // SecurityException (no usage access), IllegalArgumentException
                // (bad subscriber id), RemoteException: skip this network type.
            }
        }
        return Traffic(rx, tx)
    }

    private fun installedUids(): List<Int> {
        val pm = context.packageManager
        return try {
            pm.getInstalledApplications(0)
                .map { it.uid }
                .filter { it > 0 }
                .distinct()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    fun uidToPackage(uid: Int): String? {
        val pm = context.packageManager
        return try {
            pm.getPackagesForUid(uid)?.firstOrNull()
        } catch (_: RuntimeException) {
            null
        }
    }

    fun appLabel(packageName: String): String {
        val pm = context.packageManager
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun networkTypes(): IntArray {
        val types = ArrayList<Int>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val hasCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: true
        val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: true
        if (hasCellular) types.add(NetworkCapabilities.TRANSPORT_CELLULAR)
        if (hasWifi) types.add(NetworkCapabilities.TRANSPORT_WIFI)
        return types.toIntArray()
    }

    private fun subscriberId(networkType: Int): String? {
        if (networkType == NetworkCapabilities.TRANSPORT_CELLULAR && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            return try {
                tm?.subscriberId
            } catch (_: SecurityException) {
                null
            }
        }
        return null
    }

    data class Traffic(val rx: Long, val tx: Long)

    companion object {
        fun collectionEnabled(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE)
                as? android.app.AppOpsManager ?: return false
            val mode = try {
                appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } catch (_: RuntimeException) {
                android.app.AppOpsManager.MODE_IGNORED
            }
            return mode == android.app.AppOpsManager.MODE_ALLOWED
        }
    }
}