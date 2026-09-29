package com.dalim.datalimit.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.dalim.datalimit.R
import com.dalim.datalimit.core.HistoryBuckets
import com.dalim.datalimit.core.LocaleHelper
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.core.util.ByteFormat
import com.dalim.datalimit.data.UsageHistoryStore
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * Usage-history screen: daily granularity is stored by the monitor; weekly and
 * monthly series are aggregated on the fly by the pure HistoryBuckets helper
 * and drawn as a lightweight Canvas bar chart (no chart library).
 */
class HistoryActivity : AppCompatActivity() {

    private var granularity: HistoryBuckets.Granularity = HistoryBuckets.Granularity.DAILY
    private var series: List<HistoryBuckets.Bucket> = emptyList()

    private val store by lazy { UsageHistoryStore(applicationContext) }
    private val prefs by lazy { UsagePrefs(applicationContext) }
    private val background = Background()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.resolve(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        granularity = savedInstanceState
            ?.getSerializable(KEY_GRANULARITY)
            ?.let { restoreGranularity(it) }
            ?: HistoryBuckets.Granularity.DAILY

        findViewById<MaterialButtonToggleGroup>(R.id.granularityGroup).check(
            when (granularity) {
                HistoryBuckets.Granularity.DAILY -> R.id.btnHistoryDaily
                HistoryBuckets.Granularity.WEEKLY -> R.id.btnHistoryWeekly
                HistoryBuckets.Granularity.MONTHLY -> R.id.btnHistoryMonthly
            }
        )
        findViewById<MaterialButtonToggleGroup>(R.id.granularityGroup)
            .addOnButtonCheckedListener { _, _, isChecked ->
                if (isChecked) {
                    granularity = currentGranularity()
                    load()
                }
            }

        load()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(KEY_GRANULARITY, granularity)
        super.onSaveInstanceState(outState)
    }

    private fun load() {
        val now = System.currentTimeMillis()
        val wanted = granularity
        background.run(
            then = { if (!isFinishing && !isDestroyed) render(series) }
        ) { series = store.series(wanted, UsageHistoryStore.MAX_BUCKETS, now) }
    }

    private fun render(series: List<HistoryBuckets.Bucket>) {
        val chart = findViewById<UsageBarChartView>(R.id.historyChart)
        val empty = findViewById<View>(R.id.historyEmpty)
        val totals = findViewById<android.widget.TextView>(R.id.historyTotalsText)

        val anyUsage = series.any { it.rxBytes > 0L || it.txBytes > 0L }
        chart.visibility = if (anyUsage) View.VISIBLE else View.GONE
        empty.visibility = if (anyUsage) View.GONE else View.VISIBLE
        if (anyUsage) {
            chart.setSeries(series)
            chart.revealBars(Anim.enabled(prefs))
        }

        val rx = series.sumOf { it.rxBytes.coerceAtLeast(0L) }
        val tx = series.sumOf { it.txBytes.coerceAtLeast(0L) }
        totals.text = getString(
            R.string.history_totals_fmt,
            ByteFormat.format(rx),
            ByteFormat.format(tx)
        )
    }

    private fun currentGranularity(): HistoryBuckets.Granularity = when (
        findViewById<MaterialButtonToggleGroup>(R.id.granularityGroup).checkedButtonId
    ) {
        R.id.btnHistoryWeekly -> HistoryBuckets.Granularity.WEEKLY
        R.id.btnHistoryMonthly -> HistoryBuckets.Granularity.MONTHLY
        else -> HistoryBuckets.Granularity.DAILY
    }

    @Suppress("DEPRECATION")
    private fun restoreGranularity(value: Any): HistoryBuckets.Granularity =
        (value as? HistoryBuckets.Granularity)
            ?: HistoryBuckets.Granularity.DAILY

    companion object {
        private const val KEY_GRANULARITY = "granularity"
    }
}