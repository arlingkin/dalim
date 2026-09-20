package com.dalim.datalimit.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryCalculatorTest {

    @Test
    fun clampLevel() {
        assertEquals(67, BatteryCalculator.clampLevel(67, 100))
        assertEquals(50, BatteryCalculator.clampLevel(50, 100))
        assertEquals(100, BatteryCalculator.clampLevel(100, 100))
    }

    @Test
    fun clampLevel_respectsScale() {
        assertEquals(50, BatteryCalculator.clampLevel(500, 1000))
    }

    @Test
    fun usagePercent_fromStartToFloor() {
        // Start 80, floor 20: span 60. Current 34 → used 46/60 = 77%.
        assertEquals(77, BatteryCalculator.usagePercent(34, 80, 20))
    }

    @Test
    fun usagePercent_zeroSpanIsZero() {
        assertEquals(0, BatteryCalculator.usagePercent(50, 80, 80))
    }

    @Test
    fun usagePercent_clamps() {
        assertEquals(0, BatteryCalculator.usagePercent(90, 80, 20))
        assertEquals(100, BatteryCalculator.usagePercent(20, 80, 20))
    }

    @Test
    fun remainingPercent_isCurrentFloorGap() {
        assertEquals(14, BatteryCalculator.remainingPercent(34, 20))
        assertEquals(0, BatteryCalculator.remainingPercent(15, 20))
    }

    @Test
    fun isAlertDue_atOrBelowFloor() {
        assertTrue(BatteryCalculator.isAlertDue(19, 20))
        assertTrue(BatteryCalculator.isAlertDue(20, 20))
        assertFalse(BatteryCalculator.isAlertDue(21, 20))
    }

    @Test
    fun isAlertDue_floorDisabled() {
        assertFalse(BatteryCalculator.isAlertDue(10, 0))
    }
}