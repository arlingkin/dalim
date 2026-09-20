package com.dalim.datalimit.monitor

import android.net.TrafficStats
import com.dalim.datalimit.core.util.ByteFormat

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
        fun formatBytes(bytes: Long): String = ByteFormat.format(bytes)
    }
}