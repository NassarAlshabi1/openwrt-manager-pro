package com.openwrt.managerpro.data.repository

import com.openwrt.managerpro.data.oui.MacUtils
import com.openwrt.managerpro.data.oui.OuiDatabase
import com.openwrt.managerpro.data.prefs.DeviceStore
import com.openwrt.managerpro.data.rpc.UbusClient
import com.openwrt.managerpro.data.rpc.asObj
import com.openwrt.managerpro.data.rpc.arr
import com.openwrt.managerpro.data.rpc.intOrNull
import com.openwrt.managerpro.data.rpc.longOrNull
import com.openwrt.managerpro.data.rpc.obj
import com.openwrt.managerpro.data.rpc.str
import com.openwrt.managerpro.data.rpc.strOrNull
import com.openwrt.managerpro.domain.ArpEntry
import com.openwrt.managerpro.domain.DeviceRecord
import com.openwrt.managerpro.domain.DeviceRepository
import com.openwrt.managerpro.domain.LeaseEntry
import com.openwrt.managerpro.domain.WifiClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val ubus: UbusClient,
    private val oui: OuiDatabase,
    private val store: DeviceStore,
) : DeviceRepository {

    override suspend fun devices(): List<DeviceRecord> {
        val now = System.currentTimeMillis()

        val leases = parseLeases(runCatching { ubus.fileRead("/tmp/dhcp.leases") }.getOrNull())
        val arp = parseArp(runCatching { ubus.fileRead("/proc/net/arp") }.getOrNull())
        val wifi = wifiClients()
        val hints = runCatching { hostHints() }.getOrDefault(emptyMap())

        val allMacs = (leases.map { it.mac } + arp.map { it.mac } + wifi.map { it.mac })
            .map { MacUtils.normalizeMac(it) }
            .filter { MacUtils.isValidMac(it) }
            .toSet()

        val seen = runCatching { store.seenMap() }.getOrDefault(emptyMap())
        runCatching { store.recordSeen(allMacs, now) }

        return allMacs.map { mac ->
            val lease = leases.firstOrNull { it.mac == mac }
            val arpE = arp.firstOrNull { it.mac == mac }
            val wl = wifi.firstOrNull { it.mac == mac }
            val hostHint = hints[mac]
            val hostname = lease?.hostname?.takeIf { it.isNotBlank() && it != "*" }
                ?: hostHint?.takeIf { it.isNotBlank() }
            DeviceRecord(
                mac = mac,
                ip = lease?.ip ?: arpE?.ip,
                hostname = hostname,
                vendor = oui.lookup(mac) ?: MacUtils.vendorFromHostname(hostname),
                isWireless = wl != null,
                signalDbm = wl?.signalDbm,
                rxKbps = wl?.rxKbps,
                txKbps = wl?.txKbps,
                firstSeenEpochMs = seen[mac]?.first?.takeIf { it > 0 },
                lastSeenEpochMs = seen[mac]?.second ?: now,
            )
        }.sortedByDescending { it.isWireless }
    }

    override suspend fun devicesOnline(): Int = devices().size

    override suspend fun knownMacs(): Set<String> =
        runCatching { store.knownMacs() }.getOrDefault(emptySet())

    override suspend fun updateKnownMacs(macs: Set<String>) {
        runCatching { store.updateKnownMacs(macs) }
    }

    override suspend fun recordSeen(macs: Set<String>) {
        runCatching { store.recordSeen(macs, System.currentTimeMillis()) }
    }

    private suspend fun wifiClients(): List<WifiClient> {
        val clients = mutableListOf<WifiClient>()
        val devices = runCatching { ubus.call("iwinfo", "devices").arr("devices") }.getOrDefault(emptyList())
        for (d in devices) {
            val name = d.asObj().str("name")
            if (name.isBlank()) continue
            val assoc = runCatching {
                ubus.call("iwinfo", "assoclist", buildJsonObject { put("device", name) }).arr("results")
            }.getOrDefault(emptyList())
            for (c in assoc) {
                val o = c.asObj()
                val mac = MacUtils.normalizeMac(o.str("mac"))
                if (!MacUtils.isValidMac(mac)) continue
                clients += WifiClient(
                    mac = mac,
                    signalDbm = o.intOrNull("signal"),
                    noiseDbm = o.intOrNull("noise"),
                    rxKbps = o.obj("rx").longOrNull("rate"),
                    txKbps = o.obj("tx").longOrNull("rate"),
                )
            }
        }
        return clients
    }

    private suspend fun hostHints(): Map<String, String> {
        val hints = runCatching { ubus.call("luci-rpc", "getHostHints") }.getOrNull()
            ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        for ((key, value) in hints) {
            val mac = MacUtils.normalizeMac(key)
            if (!MacUtils.isValidMac(mac)) continue
            val o = value as? JsonObject ?: continue
            val name = o.strOrNull("name") ?: o.strOrNull("hostname") ?: continue
            out[mac] = name
        }
        return out
    }

    companion object {
        /** /tmp/dhcp.leases lines: "&lt;expires&gt; &lt;mac&gt; &lt;ip&gt; &lt;hostname&gt; [clientid]" */
        fun parseLeases(text: String?): List<LeaseEntry> {
            if (text.isNullOrBlank()) return emptyList()
            return text.lines().mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 3) return@mapNotNull null
                val mac = MacUtils.normalizeMac(parts[1])
                if (!MacUtils.isValidMac(mac)) return@mapNotNull null
                LeaseEntry(mac = mac, ip = parts[2], hostname = parts.getOrNull(3) ?: "")
            }
        }

        /** /proc/net/arp lines: "IP addr HW type Flags HW address Mask Device" */
        fun parseArp(text: String?): List<ArpEntry> {
            if (text.isNullOrBlank()) return emptyList()
            return text.lines().drop(1).mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 4) return@mapNotNull null
                val mac = MacUtils.normalizeMac(parts[3])
                if (!MacUtils.isValidMac(mac)) return@mapNotNull null
                ArpEntry(mac = mac, ip = parts[0])
            }
        }
    }
}
