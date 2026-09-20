package com.dalim.datalimit.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatTest {

    @Test
    fun bytes() {
        assertEquals("0 B", ByteFormat.format(0L))
        assertEquals("100 B", ByteFormat.format(100L))
        assertEquals("1023 B", ByteFormat.format(1023L))
    }

    @Test
    fun kilobytes() {
        assertEquals("1.0 KB", ByteFormat.format(1024L))
    }

    @Test
    fun megabytes() {
        assertEquals("5.00 MB", ByteFormat.format(5L * 1024 * 1024))
    }

    @Test
    fun gigabytes() {
        assertEquals("2.00 GB", ByteFormat.format(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun negativeIsZero() {
        assertEquals("0 B", ByteFormat.format(-5L))
    }
}