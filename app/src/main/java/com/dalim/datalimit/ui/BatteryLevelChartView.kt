package com.dalim.datalimit.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.dalim.datalimit.R
import com.dalim.datalimit.data.BatteryHistoryStore
import java.util.Locale

/**
 * Lightweight line chart for the battery-level history. Plots the sampled
 * level (0-100 %) against elapsed time; draws a soft grid + filled area and
 * a trailing value label. No dependencies beyond android.graphics.
 */
class BatteryLevelChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.accent)
        style = Paint.Style.STROKE
        strokeWidth = dp(2)
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(context.getColor(R.color.accent), 40)
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEEEEF2.toInt()
        strokeWidth = dp(1)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9AA0A6.toInt()
        textSize = sp(10)
    }

    private var points: List<BatteryHistoryStore.BatteryPoint> = emptyList()

    /** Feed a new series (oldest first); renders on the next draw pass. */
    fun setSeries(newPoints: List<BatteryHistoryStore.BatteryPoint>) {
        if (newPoints == points) return
        points = newPoints
        invalidate()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) return

        val left = pad.toFloat()
        val right = width.toFloat() - pad
        val top = pad.toFloat()
        val bottom = height.toFloat() - pad
        val plotW = right - left
        val plotH = bottom - top

        // Grid lines every 25 %.
        for (g in 1..3) {
            val y = top + plotH * g / 4f
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        val minTs = points.first().tsMillis.toFloat()
        val maxTs = points.last().tsMillis.toFloat()
        val span = (maxTs - minTs).coerceAtLeast(1f)

        fun x(p: BatteryHistoryStore.BatteryPoint): Float = left + plotW * (p.tsMillis.toFloat() - minTs) / span
        fun y(p: BatteryHistoryStore.BatteryPoint): Float = top + plotH * (1f - p.levelPercent.coerceIn(0, 100) / 100f)

        val line = Path()
        val area = Path()
        if (points.isNotEmpty()) {
            val first = points.first()
            line.moveTo(x(first), y(first))
            area.moveTo(x(first), bottom)
            area.lineTo(x(first), y(first))
            for (p in points.drop(1)) {
                line.lineTo(x(p), y(p))
                area.lineTo(x(p), y(p))
            }
            area.lineTo(x(points.last()), bottom)
            area.close()
        }

        canvas.drawPath(area, fillPaint)
        canvas.drawPath(line, linePaint)

        val last = points.last()
        val label = String.format(Locale.US, "%d%%", last.levelPercent)
        val ly = y(last).coerceIn(top + labelPaint.textSize, bottom)
        canvas.drawText(label, right - labelPaint.measureText(label), ly - dp(3), labelPaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF.toInt()) or (alpha shl 24)

    private fun dp(value: Int): Float = value * resources.displayMetrics.density
    private fun sp(value: Int): Float = value * resources.displayMetrics.scaledDensity

    companion object {
        // Left/right padding leaves room for a peak reachable on the edges.
        private const val pad = 12
    }
}