package com.openwrt.managerpro.data.diagnostics

import com.openwrt.managerpro.domain.Analysis
import com.openwrt.managerpro.domain.DiagnosticSeverity

/**
 * Pure output analyzers for diagnostic commands. Unit-tested in AnalyzersTest.
 */
object Analyzers {

    // ---- CPU ----

    fun cpuLoad(loadavgText: String?, cores: Int): Analysis {
        val first = loadavgText?.trim()?.split(Regex("\\s+"))?.getOrNull(0)?.toDoubleOrNull()
            ?: return Analysis(DiagnosticSeverity.Skipped, "loadavg unavailable")
        val c = cores.coerceAtLeast(1)
        val ratio = first / c
        val pct = (ratio * 100).toInt()
        return when {
            ratio >= 1.0 -> Analysis(DiagnosticSeverity.Error, "load $first on $c cores (${pct}%) - system overloaded")
            ratio >= 0.7 -> Analysis(DiagnosticSeverity.Warning, "load $first on $c cores (${pct}%) - high")
            ratio >= 0.4 -> Analysis(DiagnosticSeverity.Info, "load $first on $c cores (${pct}%)")
            else -> Analysis(DiagnosticSeverity.Ok, "load $first on $c cores (${pct}%)")
        }
    }

    // ---- Memory ----

    fun memory(meminfoText: String?): Analysis {
        if (meminfoText.isNullOrBlank()) return Analysis(DiagnosticSeverity.Skipped, "meminfo unavailable")
        val values = mutableMapOf<String, Long>()
        for (line in meminfoText.lines()) {
            val parts = line.split(":")
            if (parts.size == 2) {
                val kb = parts[1].trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull()
                if (kb != null) values[parts[0].trim()] = kb
            }
        }
        val total = values["MemTotal"] ?: return Analysis(DiagnosticSeverity.Skipped, "MemTotal missing")
        val avail = values["MemAvailable"] ?: values["MemFree"] ?: return Analysis(DiagnosticSeverity.Skipped, "MemAvailable missing")
        if (total <= 0) return Analysis(DiagnosticSeverity.Skipped, "invalid meminfo")
        val usedPct = ((total - avail) * 100 / total).toInt()
        return when {
            usedPct >= 90 -> Analysis(DiagnosticSeverity.Error, "RAM ${usedPct}% used (${avail / 1024} MB free of ${total / 1024} MB) - OOM risk")
            usedPct >= 75 -> Analysis(DiagnosticSeverity.Warning, "RAM ${usedPct}% used (${avail / 1024} MB free of ${total / 1024} MB)")
            usedPct >= 60 -> Analysis(DiagnosticSeverity.Info, "RAM ${usedPct}% used (${avail / 1024} MB free of ${total / 1024} MB)")
            else -> Analysis(DiagnosticSeverity.Ok, "RAM ${usedPct}% used (${avail / 1024} MB free of ${total / 1024} MB)")
        }
    }

    // ---- Storage ----

    fun storage(dfText: String?): Analysis {
        if (dfText.isNullOrBlank()) return Analysis(DiagnosticSeverity.Skipped, "df unavailable")
        val line = dfText.lines().firstOrNull { it.contains("/overlay") || it.contains("/") }
            ?: return Analysis(DiagnosticSeverity.Skipped, "no mount found")
        val usePct = Regex("(\\d+)%").find(line)?.groupValues?.get(1)?.toIntOrNull()
            ?: return Analysis(DiagnosticSeverity.Skipped, "cannot parse df")
        return when {
            usePct >= 95 -> Analysis(DiagnosticSeverity.Error, "overlay storage ${usePct}% full")
            usePct >= 85 -> Analysis(DiagnosticSeverity.Warning, "overlay storage ${usePct}% full")
            usePct >= 70 -> Analysis(DiagnosticSeverity.Info, "overlay storage ${usePct}% full")
            else -> Analysis(DiagnosticSeverity.Ok, "overlay storage ${usePct}% full")
        }
    }

    // ---- Temperature ----

    fun temperature(rawText: String?): Analysis {
        val temps = rawText?.lines()
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.map { it / 1000 }
            .orEmpty()
            .filter { it in 1..150 }
        if (temps.isEmpty()) return Analysis(DiagnosticSeverity.Skipped, "no thermal sensor")
        val max = temps.max()
        return when {
            max >= 90 -> Analysis(DiagnosticSeverity.Error, "temperature ${max}C - too hot")
            max >= 75 -> Analysis(DiagnosticSeverity.Warning, "temperature ${max}C - warm")
            max >= 60 -> Analysis(DiagnosticSeverity.Info, "temperature ${max}C")
            else -> Analysis(DiagnosticSeverity.Ok, "temperature ${max}C")
        }
    }

    // ---- WAN / ping ----

    fun ping(text: String?): Analysis {
        if (text.isNullOrBlank()) return Analysis(DiagnosticSeverity.Skipped, "ping unavailable")
        val loss = Regex("(\\d+)% packet loss").find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: return Analysis(DiagnosticSeverity.Skipped, "cannot parse ping output")
        val avg = Regex("=\\s*[\\d.]+/([\\d.]+)/").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        return when {
            loss >= 100 -> Analysis(DiagnosticSeverity.Error, "no internet access (100% packet loss)")
            loss > 0 -> Analysis(DiagnosticSeverity.Warning, "packet loss ${loss}%${avg?.let { ", avg ${it}ms" } ?: ""}")
            avg != null && avg > 150 -> Analysis(DiagnosticSeverity.Warning, "high latency avg ${avg}ms")
            avg != null -> Analysis(DiagnosticSeverity.Ok, "internet reachable, avg ${avg}ms")
            else -> Analysis(DiagnosticSeverity.Ok, "internet reachable, 0% loss")
        }
    }

    // ---- DNS ----

    fun nslookup(text: String?): Analysis {
        if (text.isNullOrBlank()) return Analysis(DiagnosticSeverity.Skipped, "nslookup unavailable")
        val addresses = Regex("Address(?:es)?\\s*:?\\s*([\\d.a-fA-F:]+)").findAll(text).toList()
        return if (addresses.size >= 2) {
            Analysis(DiagnosticSeverity.Ok, "DNS resolves openwrt.org -> ${addresses.last().groupValues[1]}")
        } else {
            Analysis(DiagnosticSeverity.Error, "DNS resolution failed")
        }
    }

    // ---- WiFi channels ----

    fun wifiChannels(radios: List<Pair<String, Int>>): Analysis {
        if (radios.isEmpty()) return Analysis(DiagnosticSeverity.Skipped, "no wifi radios detected")
        val by2g = radios.filter { it.second <= 14 }
        val duplicates = by2g.groupBy { it.second }.filter { it.value.size > 1 }
        if (duplicates.isNotEmpty()) {
            val d = duplicates.entries.joinToString(", ") { "channel ${it.key} (${it.value.joinToString("&") { it.first }})" }
            return Analysis(DiagnosticSeverity.Warning, "2.4GHz co-channel interference: $d")
        }
        val nonStandard = by2g.filter { it.second !in listOf(1, 6, 11) }
        if (nonStandard.isNotEmpty()) {
            val d = nonStandard.joinToString(", ") { "${it.first}: ch${it.second}" }
            return Analysis(DiagnosticSeverity.Info, "non-standard 2.4GHz channels (prefer 1/6/11): $d")
        }
        val desc = radios.joinToString(", ") { "${it.first}: ch${it.second}" }
        return Analysis(DiagnosticSeverity.Ok, "channels OK ($desc)")
    }

    // ---- Mesh ----

    fun parseMeshInterfaces(iwDevText: String): List<String> {
        val out = mutableListOf<String>()
        var current: String? = null
        for (line in iwDevText.lines()) {
            val iface = Regex("Interface\\s+(\\S+)").find(line)?.groupValues?.get(1)
            if (iface != null) current = iface
            if (Regex("type\\s+mesh").containsMatchIn(line) && current != null) {
                out.add(current)
                current = null
            }
        }
        return out
    }

    // ---- Wireless security ----

    fun wirelessConf(confText: String?): Analysis {
        if (confText.isNullOrBlank()) return Analysis(DiagnosticSeverity.Skipped, "wireless config unavailable")
        val ifaces = confText.split("config wifi-iface").drop(1)
        val open = mutableListOf<String>()
        val weak = mutableListOf<String>()
        for (block in ifaces) {
            val ssid = Regex("option\\s+ssid\\s+'([^']*)'").find(block)?.groupValues?.get(1) ?: "?"
            val enc = Regex("option\\s+encryption\\s+'([^']*)'").find(block)?.groupValues?.get(1)?.lowercase() ?: ""
            when {
                enc == "none" || enc.isEmpty() -> open.add(ssid)
                enc.contains("wep") -> weak.add(ssid)
                else -> Unit
            }
        }
        return when {
            open.isNotEmpty() -> Analysis(DiagnosticSeverity.Error, "open (unencrypted) WiFi network: ${open.joinToString(", ")}")
            weak.isNotEmpty() -> Analysis(DiagnosticSeverity.Warning, "weak WEP encryption on: ${weak.joinToString(", ")}")
            else -> Analysis(DiagnosticSeverity.Ok, "all WiFi networks use WPA/WPA2/WPA3")
        }
    }

    // ---- Firewall security ----

    fun firewallConf(confText: String?): Analysis {
        if (confText.isNullOrBlank()) return Analysis(DiagnosticSeverity.Skipped, "firewall config unavailable")
        val zones = confText.split("config zone").drop(1)
        var wanFound = false
        for (zone in zones) {
            val name = Regex("name\\s+'([^']*)'").find(zone)?.groupValues?.get(1)?.lowercase() ?: continue
            if (name == "wan" || name.contains("wan")) {
                wanFound = true
                val input = Regex("option\\s+input\\s+'([^']*)'").find(zone)?.groupValues?.get(1)?.uppercase()
                if (input == "ACCEPT") {
                    return Analysis(DiagnosticSeverity.Error, "WAN zone input is ACCEPT - firewall disabled on WAN!")
                }
            }
        }
        return if (wanFound) {
            Analysis(DiagnosticSeverity.Ok, "WAN zone properly rejects unsolicited input")
        } else {
            Analysis(DiagnosticSeverity.Info, "no explicit WAN zone found in firewall config")
        }
    }

    // ---- System log ----

    fun scanLogs(text: String): Analysis {
        if (text.isBlank()) return Analysis(DiagnosticSeverity.Ok, "system log clean")
        val pattern = Regex("(?i)(error|fail|deauth|timeout|segfault|oom|out of memory)")
        val lines = text.lines()
        val suspicious = lines.filter { pattern.containsMatchIn(it) }
        val count = suspicious.size
        val samples = suspicious.take(3).joinToString(" | ") { it.trim().take(90) }
        return when {
            count >= 10 -> Analysis(DiagnosticSeverity.Error, "$count suspicious log lines: $samples")
            count >= 4 -> Analysis(DiagnosticSeverity.Warning, "$count suspicious log lines: $samples")
            count >= 1 -> Analysis(DiagnosticSeverity.Info, "$count suspicious log lines: $samples")
            else -> Analysis(DiagnosticSeverity.Ok, "system log clean")
        }
    }
}
