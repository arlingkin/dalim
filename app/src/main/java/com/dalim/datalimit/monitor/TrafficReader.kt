package com.dalim.datalimit.monitor

import android.net.TrafficStats

class TrafficReader {

    data class Snapshot(val rxBytes: Long, val txBytes: Long) {
        val totalBytes: Long get() = rxBytes + txBytes
    }

    val isSupported: Boolean get() = TrafficStats.getTotalRxBytes() != TrafficStats.UNSUPPORTED.toLong()

    fun read(): Snapshot {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val rxSafe = if (rx == TrafficStats.UNSUPPORTED.toLong()) 0L else rx
        val txSafe = if (tx == TrafficStats.UNSUPPORTED.toLong()) 0L else tx
        return Snapshot(rxSafe, txSafe)
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 0) return "0 B"
            val b = bytes.toDouble()
            return when {
                b < 1024 -> "${bytes} B"
                b < 1024 * 1024 -> String.format("%.1f KB", b / 1024)
                b < 1024 * 1024 * 1024 -> String.format("%.2f MB", b / 1024 / 1024)
                else -> String.format("%.2f GB", b / 1024 / 1024 / 1024)
            }
        }
    }
}