package com.dalim.datalimit.monitor

import android.content.Context
import com.dalim.datalimit.core.BudgetMath
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

        val budget = BudgetMath.compute(
            consumedBytes = consumed,
            limitMb = settings.limitMb,
            extraAllowanceMb = settings.extraAllowanceMb
        )

        return UsageReport(
            consumedBytes = consumed,
            effectiveLimitBytes = budget.effectiveLimitBytes,
            usedPercent = budget.usedPercent,
            radiosRxBytes = snapshot.rxBytes,
            radiosTxBytes = snapshot.txBytes,
            windowLabel = WindowResolver.windowLabel(appContext, settings.period, settings.windowStyle),
            exceeded = budget.exceeded,
            limitActive = budget.limitActive,
            remainingBytes = budget.remainingBytes
        )
    }
}