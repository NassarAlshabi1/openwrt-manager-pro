package com.openwrt.managerpro.data.repository

import com.openwrt.managerpro.data.prefs.ProfileStore
import com.openwrt.managerpro.data.rpc.NotConnectedException
import com.openwrt.managerpro.data.rpc.RpcTransportException
import com.openwrt.managerpro.data.rpc.UbusClient
import com.openwrt.managerpro.data.rpc.UbusException
import com.openwrt.managerpro.data.rpc.asObj
import com.openwrt.managerpro.data.rpc.arr
import com.openwrt.managerpro.data.rpc.bool
import com.openwrt.managerpro.data.rpc.long
import com.openwrt.managerpro.data.rpc.obj
import com.openwrt.managerpro.data.rpc.str
import com.openwrt.managerpro.data.rpc.strOrNull
import com.openwrt.managerpro.domain.NetInterface
import com.openwrt.managerpro.domain.RouterProfile
import com.openwrt.managerpro.domain.RouterRepository
import com.openwrt.managerpro.domain.SystemBoard
import com.openwrt.managerpro.domain.SystemInfo
import com.openwrt.managerpro.domain.TestResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouterRepositoryImpl @Inject constructor(
    private val ubus: UbusClient,
    private val profileStore: ProfileStore,
) : RouterRepository {

    override suspend fun systemBoard(): SystemBoard {
        val b = ubus.call("system", "board")
        val release = b.obj("release")
        return SystemBoard(
            hostname = b.str("hostname"),
            model = b.str("model"),
            boardName = b.str("board_name"),
            kernel = b.str("kernel"),
            distribution = release.str("distribution"),
            version = release.str("version"),
            revision = release.str("revision"),
            target = release.str("target"),
        )
    }

    override suspend fun systemInfo(): SystemInfo {
        val i = ubus.call("system", "info")
        val load = i.arr("loadavg")
        fun ld(idx: Int): Double =
            (load.getOrNull(idx) as? JsonPrimitive)?.doubleOrNull ?: 0.0
        val memory = i.obj("memory")
        return SystemInfo(
            uptimeSec = i.long("uptime"),
            localTimeSec = i.long("localtime"),
            memTotalKb = memory.long("total"),
            memFreeKb = memory.long("free"),
            memBufferedKb = memory.long("buffered"),
            memSharedKb = memory.long("shared"),
            load1 = ld(0),
            load5 = ld(1),
            load15 = ld(2),
        )
    }

    override suspend fun networkDump(): List<NetInterface> {
        val dump = ubus.call("network.interface", "dump")
        return dump.arr("interface").mapNotNull { el ->
            val o = el.asObj()
            val name = o.str("interface")
            if (name.isEmpty()) return@mapNotNull null
            val ipv4 = o.arr("ipv4-address").firstOrNull()?.asObj()?.str("address")
            val routes = o.arr("route").map { it.asObj() }
            val gw = routes.firstOrNull { it.str("target") == "0.0.0.0" }?.strOrNull("nexthop")
            val dns = o.arr("dns-server").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            NetInterface(
                name = name,
                up = o.bool("up"),
                proto = o.str("protocol"),
                ipv4 = ipv4,
                gateway = gw,
                dnsServers = dns,
            )
        }
    }

    override suspend fun temperatureC(): Int? = try {
        val raw = ubus.fileExec("sh", listOf("-c", "cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null")).stdout
        raw.lines()
            .mapNotNull { it.trim().toIntOrNull() }
            .maxOrNull()
            ?.let { it / 1000 }
            ?.takeIf { it in 1..150 }
    } catch (e: Exception) {
        null
    }

    override suspend fun testConnection(profile: RouterProfile): TestResult {
        return try {
            ubus.login(profile)
            val board = systemBoard()
            profileStore.save(profile)
            TestResult(ok = true, detail = "", hostname = board.hostname, model = board.model, version = board.version)
        } catch (e: NotConnectedException) {
            TestResult(false, "no profile", null, null, null)
        } catch (e: RpcTransportException) {
            TestResult(false, "cannot reach router: ${e.message}", null, null, null)
        } catch (e: UbusException) {
            TestResult(false, "login failed (ubus ${e.code}) - check username/password", null, null, null)
        } catch (e: Exception) {
            TestResult(false, e.message ?: "unknown error", null, null, null)
        }
    }

    override suspend fun hasProfile(): Boolean = profileStore.profile() != null

    override suspend fun clearProfile() {
        profileStore.clear()
        ubus.invalidate()
    }
}
