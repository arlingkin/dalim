package com.dalim.datalimit.ui

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import com.dalim.datalimit.core.UsagePrefs
import kotlin.math.roundToLong

/**
 * Tiny pure-Android animation helpers (M4). No third-party libs; everything is
 * `ViewPropertyAnimator` / [ValueAnimator]. Every entry point takes the
 * `reduceMotion` flag (from [UsagePrefs]) so the caller decides once whether
 * animations run — when disabled the final value is applied instantly.
 *
 * Crash proofing rule from this codebase applies: helpers never throw from a
 * tick path (they clamp and fall back to setting the end state).
 */
object Anim {

    private const val DURATION_MS = 450L

    /** TRUE when the user has not enabled "reduce motion". */
    fun enabled(prefs: UsagePrefs): Boolean = !prefs.reduceMotion

    /**
     * Fade + slide a card/view into place on first appearance. Skipped when
     * [enabled] is false (instant paint, per the reduced-motion rule) or when
     * the view is hidden or detached.
     */
    fun fadeSlideIn(view: View, enabled: Boolean, delayMs: Long = 0L) {
        if (!enabled) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        if (view.visibility != View.VISIBLE || view.parent == null) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        val dy = view.resources.displayMetrics.density * 18f
        view.alpha = 0f
        view.translationY = dy
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delayMs)
            .setDuration(DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * Animate a [ProgressBar] to [to] (0..max). Applies the end value straight
     * away when disabled or when the value did not actually change.
     */
    fun animateProgress(bar: ProgressBar, to: Int, enabled: Boolean = true) {
        val target = to.coerceIn(0, bar.max)
        if (!enabled || bar.progress == target) {
            if (bar.progress != target) bar.progress = target
            return
        }
        val from = bar.progress
        ValueAnimator.ofInt(from, target).apply {
            duration = DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { bar.progress = (it.animatedValue as Int).coerceIn(0, bar.max) }
            start()
        }
    }

    /** Animate a TextView from one integer (>=0) to another, formatting each frame. */
    fun countTo(
        view: android.widget.TextView,
        from: Long,
        to: Long,
        format: (Long) -> String,
        enabled: Boolean = true
    ) {
        if (!enabled || from < 0 || from == to) {
            view.text = format(to)
            return
        }
        val animator = ValueAnimator.ofFloat(from.toFloat(), to.toFloat())
        animator.duration = 600L
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            val v = it.animatedValue as Float
            // Snap to the target on the last frames to avoid float drift.
            val value = if (kotlin.math.abs(v - to) < 0.5f) to else v.roundToLong()
            view.text = format(value)
        }
        animator.start()
    }

    /** Animate a numeric TextView from its current parsed value to `to`. */
    fun countTo(
        view: android.widget.TextView,
        from: Int,
        to: Int,
        format: (Int) -> String,
        enabled: Boolean = true
    ) {
        if (!enabled || from < 0 || from == to) {
            view.text = format(to)
            return
        }
        val animator = ValueAnimator.ofInt(from, to)
        animator.duration = 600L
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { view.text = format(animator.animatedValue as Int) }
        animator.start()
    }

    /**
     * Grow a view's width from its current layout width to [toWidth]
     * (px). Applies [toWidth] instantly when [enabled] is false. The target
     * view is expected inside a [android.widget.FrameLayout] so that changing
     * the width behaves like a bar fill; falls back to a no-op when the view
     * is not laid out that way.
     */
    fun growWidth(view: View, toWidth: Int, enabled: Boolean = true) {
        view.layoutParams = view.layoutParams ?: return
        val start = view.layoutParams.width
        if (!enabled || start == toWidth) {
            if (start != toWidth) {
                view.layoutParams = view.layoutParams.apply { width = toWidth }
                view.requestLayout()
            }
            return
        }
        val params = view.layoutParams
        ValueAnimator.ofInt(start.coerceAtLeast(0), toWidth.coerceAtLeast(0)).apply {
            duration = 400L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                params.width = (it.animatedValue as Int)
                view.requestLayout()
            }
            start()
        }
    }
}