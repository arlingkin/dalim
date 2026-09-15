package com.dalim.datalimit.core

enum class Period(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

enum class WindowStyle(val label: String) {
    FIXED("Midnight reset"),
    ROLLING("Rolling 24h")
}

data class DataLimitSettings(
    val limitMb: Long = 200L,
    val period: Period = Period.DAILY,
    val windowStyle: WindowStyle = WindowStyle.FIXED,
    val gateEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val extraAllowanceMb: Long = 0L
)

data class CountingState(
    val windowKey: Long,
    val baselineBytes: Long,
    val consumedBytes: Long
)

data class UsageReport(
    val consumedBytes: Long,
    val effectiveLimitBytes: Long,
    val usedPercent: Int,
    val radiosRxBytes: Long,
    val radiosTxBytes: Long,
    val windowLabel: String,
    val exceeded: Boolean,
    val limitActive: Boolean,
    val remainingBytes: Long
)