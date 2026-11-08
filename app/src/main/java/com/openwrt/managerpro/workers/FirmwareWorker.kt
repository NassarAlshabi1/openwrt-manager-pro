package com.openwrt.managerpro.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.openwrt.managerpro.data.prefs.SettingsStore
import com.openwrt.managerpro.domain.FirmwareRepository
import com.openwrt.managerpro.notify.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/** Daily firmware update check against sysupgrade.openwrt.org. */
@HiltWorker
class FirmwareWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val firmwareRepository: FirmwareRepository,
    private val settingsStore: SettingsStore,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val notify = runCatching { settingsStore.notifyFirmware.first() }.getOrDefault(true)
        if (notify) {
            val status = firmwareRepository.checkUpdate()
            if (status.updateAvailable && status.latestVersion != null) {
                notificationHelper.notifyFirmwareUpdate(status.currentVersion, status.latestVersion)
            }
        }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val WORK_NAME = "firmware_check"
    }
}
