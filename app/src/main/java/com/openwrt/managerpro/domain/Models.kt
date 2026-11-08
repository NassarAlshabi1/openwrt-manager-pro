package com.openwrt.managerpro.domain

// ---------- Connection ----------

data class RouterProfile(
    val baseUrl: String,
    val username: String,
    val password: String,
)

data class TestResult(
    val ok: Boolean,
    val detail: String,
    val hostname: String?,
    val model: String?,
    val version: String?,
)

// ---------- Router ----------

data class SystemBoard(
    val hostname: String,
    val model: String,
    val boardName: String,
    val kernel: String,
    val distribution: String,
    val version: String,
    val revision: String,
    val target: String,
)

data class SystemInfo(
    val uptimeSec: Long,
    val localTimeSec: Long,
    val memTotalKb: Long,
    val memFreeKb: Long,
    val memBufferedKb: Long,
    val memSharedKb: Long,
    val load1: Double,
    val load5: Double,
    val load15: Double,
)

data class NetInterface(
    val name: String,
    val up: Boolean,
    val proto: String,
    val ipv4: String?,
    val gateway: String?,
    val dnsServers: List<String>,
)

data class DashboardData(
    val hostname: String,
    val model: String,
    val distribution: String,
    val firmwareVersion: String,
    val kernel: String,
    val uptimeSec: Long,
    val load1: Double,
    val load5: Double,
    val load15: Double,
    val cores: Int,
    val memTotalKb: Long,
    val memUsedKb: Long,
    val temperatureC: Int?,
    val devicesOnline: Int,
    val wanIp: String?,
    val wanGateway: String?,
    val wanUp: Boolean,
    val dnsServers: List<String>,
)

// ---------- Devices ----------

data class DeviceRecord(
    val mac: String,
    val ip: String?,
    val hostname: String?,
    val vendor: String?,
    val isWireless: Boolean,
    val signalDbm: Int? = null,
    val rxKbps: Long? = null,
    val txKbps: Long? = null,
    val firstSeenEpochMs: Long? = null,
    val lastSeenEpochMs: Long = 0L,
)

data class WifiClient(
    val mac: String,
    val signalDbm: Int?,
    val noiseDbm: Int?,
    val rxKbps: Long?,
    val txKbps: Long?,
)

data class LeaseEntry(val mac: String, val ip: String, val hostname: String)

data class ArpEntry(val mac: String, val ip: String)

object DeviceDiff {
    data class Result(val added: Set<String>, val removed: Set<String>)

    fun diff(previous: Set<String>, current: Set<String>): Result = Result(
        added = current - previous,
        removed = previous - current,
    )
}

// ---------- Firmware ----------

data class FirmwareStatus(
    val currentVersion: String,
    val latestVersion: String?,
    val updateAvailable: Boolean,
    val target: String?,
    val model: String?,
    val releaseUrl: String?,
    val checkedAtEpochMs: Long,
)

object VersionCompare {
    fun normalize(v: String): List<Int> = v.trim().lowercase().removePrefix("v")
        .split('.', '-', '+', ' ')
        .mapNotNull { seg ->
            val digits = seg.takeWhile { it.isDigit() }
            if (digits.isEmpty()) null else digits.toIntOrNull()
        }

    fun compare(a: String, b: String): Int {
        val x = normalize(a)
        val y = normalize(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val xi = x.getOrNull(i) ?: 0
            val yi = y.getOrNull(i) ?: 0
            if (xi != yi) return xi.compareTo(yi)
        }
        return 0
    }

    fun isUpdateAvailable(current: String, latest: String): Boolean = compare(latest, current) > 0
}

// ---------- Diagnostics ----------

sealed class DiagnosticSeverity {
    object Ok : DiagnosticSeverity()
    object Info : DiagnosticSeverity()
    object Warning : DiagnosticSeverity()
    object Error : DiagnosticSeverity()
    object Skipped : DiagnosticSeverity()
}

data class Analysis(val severity: DiagnosticSeverity, val details: String)

data class Finding(
    val checkId: String,
    val severity: DiagnosticSeverity,
    val details: String,
)

data class DiagnosticReport(
    val score: Int,
    val findings: List<Finding>,
    val startedAtEpochMs: Long,
    val durationMs: Long,
)

object ScoreCalculator {
    fun score(findings: List<Finding>): Int {
        var s = 100
        for (f in findings) {
            when (f.severity) {
                is DiagnosticSeverity.Error -> s -= 20
                is DiagnosticSeverity.Warning -> s -= 8
                is DiagnosticSeverity.Info -> s -= 2
                else -> Unit
            }
        }
        return s.coerceIn(0, 100)
    }
}

// ---------- Real-time events ----------

sealed class RouterEvent {
    data class DeviceConnected(val mac: String, val hostname: String?, val vendor: String?) : RouterEvent()
    data class DeviceDisconnected(val mac: String) : RouterEvent()
}

// ---------- Misc pure helpers ----------

object Format {
    fun uptime(seconds: Long): String {
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        return when {
            d > 0 -> "${d}d ${h}h ${m}m"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    fun signalQuality(dbm: Int): Int = when {
        dbm >= -50 -> 4
        dbm >= -60 -> 3
        dbm >= -70 -> 2
        dbm >= -80 -> 1
        else -> 0
    }
}
