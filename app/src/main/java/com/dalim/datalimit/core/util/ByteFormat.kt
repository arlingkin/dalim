package com.dalim.datalimit.core.util

object ByteFormat {
    fun format(bytes: Long): String {
        if (bytes < 0L) return "0 B"
        val b = bytes.toDouble()
        return when {
            b < 1024 -> "$bytes B"
            b < 1024 * 1024 -> String.format("%.1f KB", b / 1024)
            b < 1024 * 1024 * 1024 -> String.format("%.2f MB", b / 1024 / 1024)
            else -> String.format("%.2f GB", b / 1024 / 1024 / 1024)
        }
    }
}