package com.openwrt.managerpro

import com.openwrt.managerpro.domain.DeviceDiff
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceDiffTest {

    @Test
    fun `detects added and removed devices`() {
        val result = DeviceDiff.diff(
            previous = setOf("aa:bb:cc:00:00:01", "aa:bb:cc:00:00:02"),
            current = setOf("aa:bb:cc:00:00:02", "aa:bb:cc:00:00:03"),
        )
        assertEquals(setOf("aa:bb:cc:00:00:03"), result.added)
        assertEquals(setOf("aa:bb:cc:00:00:01"), result.removed)
    }

    @Test
    fun `no changes`() {
        val same = setOf("aa:bb:cc:00:00:01")
        val result = DeviceDiff.diff(same, same)
        assertEquals(0, result.added.size)
        assertEquals(0, result.removed.size)
    }

    @Test
    fun `first run adds everything`() {
        val result = DeviceDiff.diff(emptySet(), setOf("aa:bb:cc:00:00:01", "aa:bb:cc:00:00:02"))
        assertEquals(2, result.added.size)
        assertEquals(0, result.removed.size)
    }
}
