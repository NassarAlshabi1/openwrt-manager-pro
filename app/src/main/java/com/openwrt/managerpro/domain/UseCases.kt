package com.openwrt.managerpro.domain

import com.openwrt.managerpro.data.diagnostics.DiagnosticsEngine
import com.openwrt.managerpro.realtime.UbusEventBus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDashboardUseCase @Inject constructor(
    private val router: RouterRepository,
    private val devices: DeviceRepository,
) {
    suspend operator fun invoke(): DashboardData {
        val board = router.systemBoard()
        val info = router.systemInfo()
        val nets = router.networkDump()
        val wan = nets.firstOrNull { it.name == "wan" && it.up }
            ?: nets.firstOrNull { !it.gateway.isNullOrEmpty() }
        val temp = runCatching { router.temperatureC() }.getOrNull()
        val count = runCatching { devices.devicesOnline() }.getOrDefault(0)
        return DashboardData(
            hostname = board.hostname,
            model = board.model,
            distribution = board.distribution,
            firmwareVersion = board.version,
            kernel = board.kernel,
            uptimeSec = info.uptimeSec,
            load1 = info.load1,
            load5 = info.load5,
            load15 = info.load15,
            cores = 1,
            memTotalKb = info.memTotalKb,
            memUsedKb = (info.memTotalKb - info.memFreeKb).coerceAtLeast(0),
            temperatureC = temp,
            devicesOnline = count,
            wanIp = wan?.ipv4,
            wanGateway = wan?.gateway,
            wanUp = wan?.up ?: false,
            dnsServers = wan?.dnsServers ?: emptyList(),
        )
    }
}

class TestConnectionUseCase @Inject constructor(
    private val router: RouterRepository,
) {
    suspend operator fun invoke(profile: RouterProfile): TestResult = router.testConnection(profile)
}

class GetDevicesUseCase @Inject constructor(
    private val devices: DeviceRepository,
) {
    suspend operator fun invoke(): List<DeviceRecord> = devices.devices()
}

class RunDiagnosticsUseCase @Inject constructor(
    private val engine: DiagnosticsEngine,
) {
    suspend operator fun invoke(): DiagnosticReport = engine.run()
}

class CheckFirmwareUseCase @Inject constructor(
    private val firmware: FirmwareRepository,
) {
    suspend operator fun invoke(): FirmwareStatus = firmware.checkUpdate()
}

class ObserveDeviceEventsUseCase @Inject constructor(
    private val eventBus: UbusEventBus,
) {
    val events: Flow<RouterEvent> get() = eventBus.events

    fun start(intervalMs: Long = 3000L) = eventBus.start(intervalMs)

    fun stop() = eventBus.stop()
}
