package com.dalim.datalimit.core

import com.dalim.datalimit.core.util.ByteFormat

data class PackageTotal(
    val packageName: String,
    val rxBytes: Long,
    val txBytes: Long
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

data class AppUsage(
    val packageName: String,
    val appLabel: String,
    val rxBytes: Long,
    val txBytes: Long,
    val totalBytes: Long
) {
    val formatted: String get() = ByteFormat.format(totalBytes)
}

object AppUsageAggregator {

    fun aggregate(totals: List<PackageTotal>): List<AppUsage> {
        val merged = LinkedHashMap<String, PackageTotal>()
        for (t in totals) {
            val acc = merged[t.packageName]
            merged[t.packageName] = if (acc == null) {
                t
            } else {
                acc.copy(
                    rxBytes = acc.rxBytes + t.rxBytes,
                    txBytes = acc.txBytes + t.txBytes
                )
            }
        }
        return merged.values
            .map { AppUsage(it.packageName, "", it.rxBytes, it.txBytes, it.totalBytes) }
            .sortedWith(
                compareByDescending<AppUsage> { it.totalBytes }
                    .thenBy { it.packageName }
            )
    }

    fun withLabels(usage: List<AppUsage>, labelFor: (String) -> String): List<AppUsage> =
        usage.map { it.copy(appLabel = labelFor(it.packageName)) }
}