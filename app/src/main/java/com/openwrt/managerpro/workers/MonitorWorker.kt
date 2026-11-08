package com.openwrt.managerpro.workers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.openwrt.managerpro.data.prefs.SettingsStore
import com.openwrt.managerpro.domain.DeviceDiff
import com.openwrt.managerpro.domain.DeviceRepository
import com.openwrt.managerpro.notify.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Periodic background monitor: alerts when the router is unreachable
 * (while the phone has internet) and when new devices join the network.
 */
@HiltWorker
class MonitorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val deviceRepository: DeviceRepository,
    private val settingsStore: SettingsStore,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val enabled = runCatching { settingsStore.monitoringEnabled.first() }.getOrDefault(true)
        if (!enabled) return Result.success()
        if (!isNetworkAvailable()) return Result.success() // phone offline: stay silent

        return try {
            val devices = deviceRepository.devices()
            val known = deviceRepository.knownMacs()
            val current = devices.map { it.mac }.toSet()
            val diff = DeviceDiff.diff(known, current)

            val notifyNew = runCatching { settingsStore.notifyNewDevice.first() }.getOrDefault(true)
            if (known.isNotEmpty() && diff.added.isNotEmpty() && notifyNew) {
                notificationHelper.notifyNewDevices(devices.filter { it.mac in diff.added })
            }

            deviceRepository.updateKnownMacs(current)
            deviceRepository.recordSeen(current)
            Result.success()
        } catch (e: Exception) {
            val notifyOffline = runCatching { settingsStore.notifyOffline.first() }.getOrDefault(true)
            if (notifyOffline) notificationHelper.notifyRouterOffline()
            Result.retry()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        const val WORK_NAME = "router_monitor"
    }
}
