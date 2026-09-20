package com.dalim.datalimit.core

object BudgetMath {

    const val MB: Long = 1024L * 1024L

    data class Budget(
        val consumedBytes: Long,
        val limitBytes: Long,
        val extraAllowanceBytes: Long,
        val effectiveLimitBytes: Long,
        val limitActive: Boolean,
        val usedPercent: Int,
        val exceeded: Boolean,
        val remainingBytes: Long
    )

    fun compute(consumedBytes: Long, limitMb: Long, extraAllowanceMb: Long = 0L): Budget {
        val limitBytes = limitMb * MB
        val effectiveLimit = if (limitBytes > 0L) limitBytes + extraAllowanceMb * MB else 0L
        val limitActive = limitMb > 0L
        val usedPercent = if (limitActive && effectiveLimit > 0L) {
            ((consumedBytes * 100L) / effectiveLimit).toInt().coerceIn(0, 1000)
        } else {
            0
        }
        val exceeded = consumedBytes >= 0L && limitActive && effectiveLimit > 0L && consumedBytes >= effectiveLimit
        return Budget(
            consumedBytes = consumedBytes,
            limitBytes = limitBytes,
            extraAllowanceBytes = extraAllowanceMb * MB,
            effectiveLimitBytes = effectiveLimit,
            limitActive = limitActive,
            usedPercent = usedPercent,
            exceeded = exceeded,
            remainingBytes = (effectiveLimit - consumedBytes).coerceAtLeast(0L)
        )
    }
}