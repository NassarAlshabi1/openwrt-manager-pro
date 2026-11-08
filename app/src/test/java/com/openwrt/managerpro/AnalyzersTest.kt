package com.openwrt.managerpro

import com.openwrt.managerpro.data.diagnostics.Analyzers
import com.openwrt.managerpro.domain.DiagnosticSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzersTest {

    @Test
    fun `cpu load levels`() {
        assertEquals(DiagnosticSeverity.Error, Analyzers.cpuLoad("4.50 3.10 2.00 3/120 900", 2).severity)
        assertEquals(DiagnosticSeverity.Warning, Analyzers.cpuLoad("1.60 1.40 1.00 3/120 900", 2).severity)
        assertEquals(DiagnosticSeverity.Ok, Analyzers.cpuLoad("0.10 0.20 0.30 2/120 900", 2).severity)
        assertEquals(DiagnosticSeverity.Skipped, Analyzers.cpuLoad(null, 2).severity)
    }

    @Test
    fun `memory levels`() {
        val meminfo = """
            MemTotal:        262144 kB
            MemFree:          10240 kB
            MemAvailable:     20480 kB
            Buffers:           4096 kB
        """.trimIndent()
        val a = Analyzers.memory(meminfo)
        assertEquals(DiagnosticSeverity.Error, a.severity) // (262144-20480)/262144 = 92%
        assertTrue(a.details.contains("92%"))

        val healthy = """
            MemTotal:        262144 kB
            MemAvailable:    209715 kB
        """.trimIndent()
        assertEquals(DiagnosticSeverity.Ok, Analyzers.memory(healthy).severity)
        assertEquals(DiagnosticSeverity.Skipped, Analyzers.memory(null).severity)
    }

    @Test
    fun `df storage parsing`() {
        val df = "Filesystem                Size      Used Available Use% Mounted on\n/dev/loop0               103.3M     55.1M     42.6M  56% /overlay"
        assertEquals(DiagnosticSeverity.Ok, Analyzers.storage(df).severity)
        assertEquals(DiagnosticSeverity.Error, Analyzers.storage("dev 1M 1M 0K 96% /overlay").severity)
        assertEquals(DiagnosticSeverity.Skipped, Analyzers.storage("").severity)
    }

    @Test
    fun `temperature parsing`() {
        assertEquals(DiagnosticSeverity.Warning, Analyzers.temperature("56230\n78000\n51000").severity)
        assertEquals(DiagnosticSeverity.Ok, Analyzers.temperature("45000").severity)
        assertEquals(DiagnosticSeverity.Skipped, Analyzers.temperature("").severity)
    }

    @Test
    fun `ping analysis`() {
        val ok = "3 packets transmitted, 3 received, 0% packet loss, time 2003ms\nrtt min/avg/max/mdev = 10.1/12.5/15.2/2.0 ms"
        val a = Analyzers.ping(ok)
        assertEquals(DiagnosticSeverity.Ok, a.severity)
        assertTrue(a.details.contains("12.5ms"))

        val dead = "3 packets transmitted, 0 received, 100% packet loss, time 2003ms"
        assertEquals(DiagnosticSeverity.Error, Analyzers.ping(dead).severity)

        val lossy = "3 packets transmitted, 2 received, 33% packet loss"
        assertEquals(DiagnosticSeverity.Warning, Analyzers.ping(lossy).severity)
    }

    @Test
    fun `nslookup analysis`() {
        val ok = "Server:		127.0.0.1\nAddress:	127.0.0.1:53\n\nName:      openwrt.org\nAddress 1: 139.59.209.225"
        assertEquals(DiagnosticSeverity.Ok, Analyzers.nslookup(ok).severity)
        assertEquals(DiagnosticSeverity.Error, Analyzers.nslookup(";; connection timed out; no servers could be reached").severity)
    }

    @Test
    fun `wifi channel conflicts`() {
        val dup = Analyzers.wifiChannels(listOf("wlan0" to 6, "wlan1" to 6))
        assertEquals(DiagnosticSeverity.Warning, dup.severity)
        assertTrue(dup.details.contains("channel 6"))

        val nonStandard = Analyzers.wifiChannels(listOf("wlan0" to 3))
        assertEquals(DiagnosticSeverity.Info, nonStandard.severity)

        val good = Analyzers.wifiChannels(listOf("wlan0" to 6, "wlan1" to 36))
        assertEquals(DiagnosticSeverity.Ok, good.severity)

        assertEquals(DiagnosticSeverity.Skipped, Analyzers.wifiChannels(emptyList()).severity)
    }

    @Test
    fun `mesh interface parsing`() {
        val iwDev = """
            phy#0
            	Interface wlan0
            		ifindex 5
            		wdev 0x100000001
            		addr aa:bb:cc:dd:ee:01
            		type AP
            	Interface mesh0
            		ifindex 7
            		wdev 0x100000003
            		addr aa:bb:cc:dd:ee:03
            		type mesh
            phy#1
            	Interface wlan1
            		ifindex 6
            		type AP
        """.trimIndent()
        assertEquals(listOf("mesh0"), Analyzers.parseMeshInterfaces(iwDev))
        assertTrue(Analyzers.parseMeshInterfaces("").isEmpty())
    }

    @Test
    fun `wireless config security scan`() {
        val conf = """
            config wifi-iface 'default_radio0'
            	option device 'radio0'
            	option ssid 'HomeNet'
            	option encryption 'psk2'
            config wifi-iface 'guest'
            	option device 'radio0'
            	option ssid 'FreeWiFi'
            	option encryption 'none'
        """.trimIndent()
        val a = Analyzers.wirelessConf(conf)
        assertEquals(DiagnosticSeverity.Error, a.severity)
        assertTrue(a.details.contains("FreeWiFi"))

        val secure = "config wifi-iface 'x'\n\toption ssid 'Home'\n\toption encryption 'sae-mixed'"
        assertEquals(DiagnosticSeverity.Ok, Analyzers.wirelessConf(secure).severity)
        assertEquals(DiagnosticSeverity.Skipped, Analyzers.wirelessConf(null).severity)
    }

    @Test
    fun `firewall config security scan`() {
        val bad = """
            config zone
            	option name 'lan'
            	option input 'ACCEPT'
            config zone
            	option name 'wan'
            	option input 'ACCEPT'
        """.trimIndent()
        assertEquals(DiagnosticSeverity.Error, Analyzers.firewallConf(bad).severity)

        val good = """
            config zone
            	option name 'wan'
            	option input 'REJECT'
            	option forward 'REJECT'
        """.trimIndent()
        assertEquals(DiagnosticSeverity.Ok, Analyzers.firewallConf(good).severity)
    }

    @Test
    fun `log scan severity`() {
        assertEquals(DiagnosticSeverity.Ok, Analyzers.scanLogs("daemon.info hostapd: wlan0 STA connected").severity)
        assertEquals(DiagnosticSeverity.Info, Analyzers.scanLogs("daemon.err dnsmasq: failed to read /etc/hosts").severity)
        val many = (1..12).joinToString("\n") { "daemon.err dnsmasq[$it]: error reading" }
        assertEquals(DiagnosticSeverity.Error, Analyzers.scanLogs(many).severity)
    }
}
