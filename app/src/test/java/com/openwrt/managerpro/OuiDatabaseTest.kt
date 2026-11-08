package com.openwrt.managerpro

import com.openwrt.managerpro.data.oui.MacUtils
import com.openwrt.managerpro.data.oui.OuiDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OuiDatabaseTest {

    private val db = OuiDatabase(
        OuiDatabase.parse(
            listOf(
                "# comment line",
                "",
                "b8:27:eb,Raspberry Pi",
                "00:1B:63,Apple",
                "5c:cf:7f,Espressif",
            )
        )
    )

    @Test
    fun `lookup ignores case and separators`() {
        assertEquals("Raspberry Pi", db.lookup("B8:27:EB:12:34:56"))
        assertEquals("Apple", db.lookup("00-1b-63-aa-bb-cc"))
        assertEquals("Espressif", db.lookup("5CCF7F001122"))
    }

    @Test
    fun `unknown mac returns null`() {
        assertNull(db.lookup("11:22:33:44:55:66"))
        assertNull(db.lookup("garbage"))
    }

    @Test
    fun `normalize mac formats`() {
        assertEquals("b8:27:eb:12:34:56", MacUtils.normalizeMac("B8-27-EB-12-34-56"))
        assertEquals("b8:27:eb:12:34:56", MacUtils.normalizeMac("b827eb123456"))
        assertTrue(MacUtils.isValidMac("00:1B:63:AA:BB:CC"))
        assertFalse(MacUtils.isValidMac("00:1B:63:AA:BB"))
    }

    @Test
    fun `vendor guess from hostname`() {
        assertEquals("Apple", MacUtils.vendorFromHostname("iPhone-of-Ahmed"))
        assertEquals("Samsung", MacUtils.vendorFromHostname("Galaxy-S24"))
        assertEquals("Xiaomi", MacUtils.vendorFromHostname("Redmi-Note-12"))
        assertNull(MacUtils.vendorFromHostname("random-pc"))
        assertNull(MacUtils.vendorFromHostname(null))
    }
}
