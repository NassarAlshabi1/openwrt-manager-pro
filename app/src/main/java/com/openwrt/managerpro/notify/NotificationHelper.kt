package com.openwrt.managerpro.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.openwrt.managerpro.MainActivity
import com.openwrt.managerpro.R
import com.openwrt.managerpro.domain.DeviceRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    init {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MONITOR,
                    context.getString(R.string.notif_channel_monitor),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.notif_channel_monitor_desc) }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_FIRMWARE,
                    context.getString(R.string.notif_channel_fw),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.notif_channel_fw_desc) }
            )
        }
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        try {
            val nm = NotificationManagerCompat.from(context)
            if (nm.areNotificationsEnabled()) nm.notify(id, notification)
        } catch (e: SecurityException) {
            // notifications permission not granted - ignore
        }
    }

    fun notifyRouterOffline() {
        val n = NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(context.getString(R.string.notif_offline_title))
            .setContentText(context.getString(R.string.notif_offline_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        notifySafely(NOTIFY_OFFLINE, n)
    }

    fun notifyNewDevices(devices: List<DeviceRecord>) {
        if (devices.isEmpty()) return
        val first = devices.first()
        val name = first.hostname ?: first.vendor ?: first.mac
        val text = if (devices.size == 1) {
            context.getString(R.string.notif_new_text_one, name)
        } else {
            context.getString(R.string.notif_new_text_many, name, devices.size - 1)
        }
        val n = NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(context.getString(R.string.notif_new_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        notifySafely(NOTIFY_NEW_DEVICE_BASE + (first.mac.hashCode() and 0x7fffffff) % 1000, n)
    }

    fun notifyFirmwareUpdate(current: String, latest: String) {
        val text = context.getString(R.string.notif_fw_text, current, latest)
        val n = NotificationCompat.Builder(context, CHANNEL_FIRMWARE)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(context.getString(R.string.notif_fw_title, latest))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        notifySafely(NOTIFY_FIRMWARE, n)
    }

    companion object {
        const val CHANNEL_MONITOR = "router_monitor"
        const val CHANNEL_FIRMWARE = "firmware_updates"
        const val NOTIFY_OFFLINE = 1001
        const val NOTIFY_NEW_DEVICE_BASE = 2000
        const val NOTIFY_FIRMWARE = 3001
    }
}
