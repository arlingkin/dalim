package com.dalim.datalimit.monitor

import android.content.Context
import com.dalim.datalimit.core.CountingState
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.core.UsageReport

class UsageMatcher(private val prefs: UsagePrefs, private val appContext: Context) {

    private val reader = TrafficReader()

    /**
     * Returns the current usage report, updating persisted counting state as needed.
     */
    fun compute(nowMillis: Long): UsageReport {
        val settings = prefs.settings()
        WindowResolver.withRollingAnchor(prefs.rollingAnchorMillis)

        val snapshot = reader.read()
        val windowKey = WindowResolver.windowKey(settings.period, settings.windowStyle, nowMillis)
        val stored = prefs.countingState

        val consumed: Long
        if (stored == null || stored.windowKey != windowKey) {
            consumed = 0L
            prefs.countingState = CountingState(windowKey, snapshot.totalBytes, 0L)
        } else {
            val delta = snapshot.totalBytes - stored.baselineBytes
            consumed = if (delta >= 0L) delta else stored.consumedBytes.coerceAtLeast(0L)
            if (delta < 0L && stored.consumedBytes >= 0L) {
                // Device rebooted: the counters reset. Re-anchor at the last known amount.
                prefs.countingState = CountingState(
                    windowKey = windowKey,
                    baselineBytes = (snapshot.totalBytes - consumed).coerceAtLeast(0L),
                    consumedBytes = consumed
                )
            }
        }
        prefs.lastConsumedBytes = consumed
        prefs.lastCheckMillis = nowMillis

        val limitBytes = settings.limitMb * 1024L * 1024L
        val effectiveLimit = if (limitBytes > 0L) limitBytes + settings.extraAllowanceMb * 1024L * 1024L else 0L
        val limitActive = settings.limitMb > 0L
        val usedPercent = if (limitActive && effectiveLimit > 0L) {
            ((consumed * 100L) / effectiveLimit).toInt().coerceIn(0, 1000)
        } else {
            0
        }
        val exceeded = limitActive && effectiveLimit > 0L && consumed >= effectiveLimit

        return UsageReport(
            consumedBytes = consumed,
            effectiveLimitBytes = effectiveLimit,
            usedPercent = usedPercent,
            radiosRxBytes = snapshot.rxBytes,
            radiosTxBytes = snapshot.txBytes,
            windowLabel = WindowResolver.windowLabel(appContext, settings.period, settings.windowStyle),
            exceeded = exceeded,
            limitActive = limitActive,
            remainingBytes = (effectiveLimit - consumed).coerceAtLeast(0L)
        )
    }
}