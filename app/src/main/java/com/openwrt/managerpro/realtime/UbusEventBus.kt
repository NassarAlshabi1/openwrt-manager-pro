package com.openwrt.managerpro.realtime

import com.openwrt.managerpro.domain.DeviceDiff
import com.openwrt.managerpro.domain.DeviceRepository
import com.openwrt.managerpro.domain.RouterEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time device event stream.
 *
 * ubus over HTTP JSON-RPC has no persistent subscribe channel, so this bus
 * emulates subscriptions with a fast, diff-based poll loop while the app is
 * in the foreground (3s) and hands over to WorkManager while in background.
 * UI layers collect [events] instead of manual polling.
 */
@Singleton
class UbusEventBus @Inject constructor(
    private val deviceRepository: DeviceRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<RouterEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RouterEvent> = _events

    private var job: Job? = null

    @Volatile
    private var current: Set<String> = emptySet()

    fun start(intervalMs: Long = 3000L) {
        if (job?.isActive == true) return
        job = scope.launch {
            current = runCatching { deviceRepository.knownMacs() }.getOrDefault(emptySet())
            while (isActive) {
                runCatching {
                    val devices = deviceRepository.devices()
                    val now = devices.map { it.mac }.toSet()
                    val diff = DeviceDiff.diff(current, now)
                    for (mac in diff.added) {
                        val rec = devices.firstOrNull { it.mac == mac }
                        _events.emit(RouterEvent.DeviceConnected(mac, rec?.hostname, rec?.vendor))
                    }
                    for (mac in diff.removed) {
                        _events.emit(RouterEvent.DeviceDisconnected(mac))
                    }
                    current = now
                    if (now.isNotEmpty()) deviceRepository.updateKnownMacs(now)
                }
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
