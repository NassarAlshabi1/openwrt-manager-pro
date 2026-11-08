package com.openwrt.managerpro

import com.openwrt.managerpro.data.rpc.EMPTY_SESSION
import com.openwrt.managerpro.data.rpc.UbusCodes
import com.openwrt.managerpro.data.rpc.UbusProtocol
import com.openwrt.managerpro.data.rpc.asObj
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UbusProtocolTest {

    @Test
    fun `buildRequest produces ubus jsonrpc call`() {
        val args = buildJsonObject { put("path", "/tmp/dhcp.leases") }
        val req = UbusProtocol.buildRequest(7, EMPTY_SESSION, "file", "read", args)

        assertEquals(JsonPrimitive("2.0"), req["jsonrpc"])
        assertEquals(JsonPrimitive(7), req["id"])
        assertEquals(JsonPrimitive("call"), req["method"])
        val params = req["params"]!!.asObj() // just to ensure presence
        assertTrue(req.toString().contains("ubus_rpc_session") || params != null)
        val expected =
            """{"jsonrpc":"2.0","id":7,"method":"call","params":["$EMPTY_SESSION","file","read",{"path":"/tmp/dhcp.leases"}]}"""
        assertEquals(expected, req.toString())
    }

    @Test
    fun `parseResponse extracts status and session payload`() {
        val body = """{"jsonrpc":"2.0","id":1,"result":[0,{"ubus_rpc_session":"abc123","timeout":300,"acls":{}}]}"""
        val resp = UbusProtocol.parseResponse(body)
        assertEquals(UbusCodes.OK, resp.status)
        assertEquals("abc123", UbusProtocol.extractSession(resp.payload!!))
    }

    @Test
    fun `parseResponse handles error status without payload`() {
        val body = """{"jsonrpc":"2.0","id":2,"result":[6]}"""
        val resp = UbusProtocol.parseResponse(body)
        assertEquals(UbusCodes.PERMISSION_DENIED, resp.status)
        assertEquals(null, resp.payload)
    }

    @Test
    fun `parseResponse handles malformed body`() {
        val resp = UbusProtocol.parseResponse("not json at all")
        assertEquals(UbusCodes.UNKNOWN, resp.status)
    }

    @Test
    fun `parseResponse board call`() {
        val body = """{"jsonrpc":"2.0","id":9,"result":[0,{"hostname":"OpenWrt","model":"Xiaomi Mi Router","board_name":"xiaomi","kernel":"5.15.0","release":{"distribution":"OpenWrt","version":"23.05.5","revision":"r24106","target":"ramips/mt7621"}}]}"""
        val resp = UbusProtocol.parseResponse(body)
        val board = resp.payload!!
        assertEquals("OpenWrt", board.str("hostname"))
        assertEquals("23.05.5", board.asObj()["release"]!!.asObj().str("version"))
    }
}
