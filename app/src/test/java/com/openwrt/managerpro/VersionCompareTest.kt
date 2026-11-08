package com.openwrt.managerpro

import com.openwrt.managerpro.domain.VersionCompare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun `older vs newer`() {
        assertTrue(VersionCompare.compare("23.05.5", "24.10.2") < 0)
        assertTrue(VersionCompare.compare("24.10.2", "23.05.5") > 0)
    }

    @Test
    fun `equal versions`() {
        assertEquals(0, VersionCompare.compare("24.10.2", "24.10.2"))
    }

    @Test
    fun `patch level comparison`() {
        assertTrue(VersionCompare.compare("24.10.1", "24.10.2") < 0)
    }

    @Test
    fun `handles v prefix and suffixes`() {
        assertTrue(VersionCompare.compare("v23.05.5", "24.10.0") < 0)
        assertTrue(VersionCompare.compare("23.05.5", "23.05.5-rc4") == 0)
    }

    @Test
    fun `isUpdateAvailable logic`() {
        assertTrue(VersionCompare.isUpdateAvailable("23.05.5", "24.10.2"))
        assertFalse(VersionCompare.isUpdateAvailable("24.10.2", "24.10.2"))
        assertFalse(VersionCompare.isUpdateAvailable("24.10.2", "23.05.5"))
    }

    @Test
    fun `different length versions`() {
        assertTrue(VersionCompare.compare("23.05", "23.05.1") < 0)
        assertTrue(VersionCompare.compare("24.10.0.1", "24.10") > 0)
    }
}
