package com.dalim.datalimit.ui

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.dalim.datalimit.R
import com.dalim.datalimit.core.HistoryBuckets
import com.dalim.datalimit.core.util.ByteFormat
import java.util.Locale

/**
 * Lightweight vertical-bar chart for the usage-history screen. Plots one bar
 * per bucket (day / week / month), highlights the peak, scales to the maximum
 * total, labels the axis below. Zero dependencies; same Canvas approach as
 * [BatteryLevelChartView].
 */
class UsageBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.accent)
        style = Paint.Style.FILL
    }
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.primary)
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8EAED.toInt()
        strokeWidth = dp(1)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9AA0A6.toInt()
        textSize = sp(10)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5F6368.toInt()
        textSize = sp(9)
    }

    private var buckets: List<HistoryBuckets.Bucket> = emptyList()

    /** 0f..1f multiplier for bar heights; 1f instantly when reduce-motion is on. */
    private var reveal = 1f
    private var animatedOnce = false

    /**
     * Reveal the bars on first real data (M4): grow bar heights 0 -> 1. Paints
     * instantly when [enabled] is false (reduce-motion) and is idempotent.
     */
    fun revealBars(enabled: Boolean) {
        if (!enabled || animatedOnce) {
            reveal = 1f
            invalidate()
            return
        }
        animatedOnce = true
        reveal = 0f
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                reveal = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** Feed a new series (oldest first); renders on the next draw pass. */
    fun setSeries(newBuckets: List<HistoryBuckets.Bucket>) {
        if (newBuckets == buckets) return
        buckets = newBuckets
        invalidate()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        if (buckets.size < 2) return

        val pad = dp(12)
        val left = pad
        val right = width.toFloat() - pad
        val top = pad
        val bottom = height.toFloat() - (dp(18) + labelPaint.textSize * 2f + dp(4))
        val plotW = right - left
        val plotH = bottom - top

        // Grid lines every 25 %.
        for (g in 1..3) {
            val y = top + plotH * g / 4f
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        val max = (buckets.maxOfOrNull { it.rxBytes + it.txBytes } ?: 0L).coerceAtLeast(1L)
        val slot = plotW / buckets.size
        val barW = slot * 0.6f

        for ((index, bucket) in buckets.withIndex()) {
            val total = (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
            val barH = plotH * (total.toFloat() / max).coerceIn(0f, 1f) * reveal
            val cx = left + slot * index + slot / 2f
            val x = cx - barW / 2f
            val y = bottom - barH
            val paint = if (total >= max) peakPaint else barPaint
            canvas.drawRect(x, y, x + barW, bottom, paint)

            val value = ByteFormat.format(total)
            val vw = valuePaint.measureText(value)
            canvas.drawText(value, cx - vw / 2f, y - dp(3), valuePaint)

            val label = axisLabel(bucket)
            val lw = labelPaint.measureText(label)
            canvas.drawText(label, cx - lw / 2f, bottom + labelPaint.textSize + dp(6), labelPaint)
        }
    }

    private fun axisLabel(bucket: HistoryBuckets.Bucket): String {
        val date = java.time.LocalDate.ofEpochDay(bucket.epochDay)
        val fmt = java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        return date.format(fmt)
    }

    private fun dp(value: Int): Float = value * resources.displayMetrics.density
    private fun sp(value: Int): Float = value * resources.displayMetrics.scaledDensity
}