package com.openwrt.managerpro.data.diagnostics

import com.openwrt.managerpro.data.rpc.UbusClient
import com.openwrt.managerpro.data.rpc.asObj
import com.openwrt.managerpro.data.rpc.arr
import com.openwrt.managerpro.data.rpc.int
import com.openwrt.managerpro.data.rpc.str
import com.openwrt.managerpro.domain.Analysis
import com.openwrt.managerpro.domain.DiagnosticReport
import com.openwrt.managerpro.domain.DiagnosticSeverity
import com.openwrt.managerpro.domain.Finding
import com.openwrt.managerpro.domain.ScoreCalculator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rule-based "smart doctor" for OpenWrt routers.
 * Runs a set of diagnostic commands via ubus (file exec/read) and analyzes
 * the outputs: CPU load, memory, storage, temperature, WAN, DNS, WiFi
 * channels/interference, mesh health, wireless & firewall security, logs.
 */
@Singleton
class DiagnosticsEngine @Inject constructor(
    private val ubus: UbusClient,
) {
    suspend fun run(): DiagnosticReport {
        val startedAt = System.currentTimeMillis()
        val findings = mutableListOf<Finding>()

        suspend fun add(checkId: String, block: suspend () -> Finding) {
            findings += try {
                block()
            } catch (e: Exception) {
                Finding(checkId, DiagnosticSeverity.Skipped, "")
            }
        }

        add("cpu") { cpuCheck() }
        add("memory") { memoryCheck() }
        add("storage") { storageCheck() }
        add("temperature") { temperatureCheck() }
        add("wan") { wanCheck() }
        add("dns") { dnsCheck() }
        add("wifi") { wifiChannelsCheck() }
        add("mesh") { meshCheck() }
        add("wifi_security") { wirelessSecurityCheck() }
        add("firewall") { firewallSecurityCheck() }
        add("logs") { logsCheck() }

        val duration = System.currentTimeMillis() - startedAt
        return DiagnosticReport(ScoreCalculator.score(findings), findings, startedAt, duration)
    }

    private fun a(checkId: String, analysis: Analysis) = Finding(checkId, analysis.severity, analysis.details)

    private suspend fun cpuCheck(): Finding {
        val loadavg = ubus.fileRead("/proc/loadavg")
        val cores = ubus.fileExec("nproc").stdout.trim().toIntOrNull() ?: 1
        return a("cpu", Analyzers.cpuLoad(loadavg, cores))
    }

    private suspend fun memoryCheck(): Finding =
        a("memory", Analyzers.memory(ubus.fileRead("/proc/meminfo")))

    private suspend fun storageCheck(): Finding {
        val df = ubus.fileExec("df", listOf("-h", "/overlay")).stdout
        return a("storage", Analyzers.storage(df))
    }

    private suspend fun temperatureCheck(): Finding {
        val raw = ubus.fileExec("sh", listOf("-c", "cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null")).stdout
        return a("temperature", Analyzers.temperature(raw))
    }

    private suspend fun wanCheck(): Finding {
        val ping = ubus.fileExec("ping", listOf("-c", "3", "-W", "2", "8.8.8.8")).stdout
        return a("wan", Analyzers.ping(ping))
    }

    private suspend fun dnsCheck(): Finding {
        val out = ubus.fileExec("nslookup", listOf("openwrt.org")).stdout
        return a("dns", Analyzers.nslookup(out))
    }

    private suspend fun wifiChannelsCheck(): Finding {
        val devList = ubus.call("iwinfo", "devices").arr("devices").mapNotNull { el ->
            when (el) {
                is JsonPrimitive -> el.contentOrNull
                is JsonObject -> el.str("name")
                else -> null
            }
        }.filter { it.isNotBlank() }

        val radios = devList.mapNotNull { name ->
            val info = ubus.call("iwinfo", "info", buildJsonObject { put("device", name) })
            val channel = info.int("channel")
            if (channel <= 0) null else name to channel
        }
        return a("wifi", Analyzers.wifiChannels(radios))
    }

    private suspend fun meshCheck(): Finding {
        val iwDev = ubus.fileExec("iw", listOf("dev")).stdout
        val meshIfs = Analyzers.parseMeshInterfaces(iwDev)
        if (meshIfs.isEmpty()) {
            return Finding("mesh", DiagnosticSeverity.Skipped, "no mesh interface configured")
        }
        val parts = meshIfs.map { name ->
            val dump = runCatching { ubus.fileExec("iw", listOf("dev", name, "station", "dump")).stdout }.getOrDefault("")
            val peers = dump.split("Station ").size - 1
            "$name: $peers peers"
        }
        val zeroPeers = parts.any { it.endsWith("0 peers") }
        val batman = runCatching {
            ubus.fileExec("sh", listOf("-c", "test -d /sys/class/net/bat0 && echo yes || echo no")).stdout.trim()
        }.getOrDefault("no")
        val details = parts.joinToString("; ") + if (batman == "yes") "; batman-adv active" else ""
        return Finding(
            "mesh",
            if (zeroPeers) DiagnosticSeverity.Warning else DiagnosticSeverity.Ok,
            details,
        )
    }

    private suspend fun wirelessSecurityCheck(): Finding =
        a("wifi_security", Analyzers.wirelessConf(ubus.fileRead("/etc/config/wireless")))

    private suspend fun firewallSecurityCheck(): Finding =
        a("firewall", Analyzers.firewallConf(ubus.fileRead("/etc/config/firewall")))

    private suspend fun logsCheck(): Finding =
        a("logs", Analyzers.scanLogs(runCatching { ubus.fileExec("logread", listOf("-l", "150")).stdout }.getOrDefault("")))
}
