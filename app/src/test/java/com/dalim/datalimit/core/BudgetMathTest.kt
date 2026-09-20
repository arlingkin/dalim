package com.dalim.datalimit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetMathTest {

    @Test
    fun usedBelowLimit_isNotExceeded_andShowsRemaining() {
        val b = BudgetMath.compute(consumedBytes = 50 * BudgetMath.MB, limitMb = 100)
        assertFalse(b.exceeded)
        assertTrue(b.limitActive)
        assertEquals(50, b.usedPercent)
        assertEquals(50 * BudgetMath.MB, b.remainingBytes)
        assertEquals(100 * BudgetMath.MB, b.effectiveLimitBytes)
    }

    @Test
    fun usedExactlyAtLimit_isExceeded_withZeroRemaining() {
        val b = BudgetMath.compute(consumedBytes = 100 * BudgetMath.MB, limitMb = 100)
        assertTrue(b.exceeded)
        assertEquals(100, b.usedPercent)
        assertEquals(0L, b.remainingBytes)
    }

    @Test
    fun usedOverLimit_isExceeded_andPercentCaps() {
        val b = BudgetMath.compute(consumedBytes = 120 * BudgetMath.MB, limitMb = 100)
        assertTrue(b.exceeded)
        assertEquals(120, b.usedPercent)
        assertEquals(0L, b.remainingBytes)
    }

    @Test
    fun remainingCalculation() {
        val b = BudgetMath.compute(consumedBytes = 700 * BudgetMath.MB, limitMb = 1024)
        assertEquals(324 * BudgetMath.MB, b.remainingBytes)
    }

    @Test
    fun percentageCalculation() {
        val b = BudgetMath.compute(consumedBytes = 60 * BudgetMath.MB, limitMb = 100)
        assertEquals(60, b.usedPercent)
        val c = BudgetMath.compute(consumedBytes = 1 * BudgetMath.MB, limitMb = 1024)
        assertEquals(0, c.usedPercent)
    }

    @Test
    fun zeroLimitIsUnlimited() {
        val b = BudgetMath.compute(consumedBytes = 12345L, limitMb = 0)
        assertFalse(b.limitActive)
        assertFalse(b.exceeded)
        assertEquals(0, b.usedPercent)
        assertEquals(0L, b.effectiveLimitBytes)
        assertEquals(0L, b.remainingBytes)
    }

    @Test
    fun extraAllowanceRaisesEffectiveLimit() {
        val b = BudgetMath.compute(consumedBytes = 90 * BudgetMath.MB, limitMb = 100, extraAllowanceMb = 100)
        assertEquals(200 * BudgetMath.MB, b.effectiveLimitBytes)
        assertEquals(45, b.usedPercent)
        assertFalse(b.exceeded)
        assertEquals(110 * BudgetMath.MB, b.remainingBytes)
    }

    @Test
    fun negativeConsumedIsClampedToZeroPercent() {
        val b = BudgetMath.compute(consumedBytes = -50, limitMb = 100)
        assertEquals(0, b.usedPercent)
        assertFalse(b.exceeded)
    }

    @Test
    fun megaByteConstant() {
        assertEquals(1024L * 1024L, BudgetMath.MB)
    }
}