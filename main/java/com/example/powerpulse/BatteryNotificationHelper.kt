package com.example.powerpulse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object BatteryNotificationHelper {

    private const val CHANNEL_ID = "alert_ch"
    private const val CHANNEL_SHUTDOWN = "shutdown_ch"

    fun sendNotification(
        context: Context,
        id: Int,
        title: String,
        message: String,
        channelId: String = CHANNEL_ID
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm, channelId)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(id, notification)
    }

    private fun ensureChannel(nm: NotificationManager, channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = if (channelId == CHANNEL_SHUTDOWN) "Shutdown Alerts" else "Power Alerts"

            // ✅ FIXED: IMPORTANCE_MAX replaced with IMPORTANCE_HIGH (valid value)
            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel(channelId, name, importance).apply {
                description = if (channelId == CHANNEL_SHUTDOWN)
                    "Alerts shown when phone shuts down with background apps"
                else
                    "PowerPulse battery and usage alerts"
            }
            nm.createNotificationChannel(channel)
        }
    }
}