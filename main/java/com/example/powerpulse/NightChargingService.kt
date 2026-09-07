package com.example.powerpulse

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

// ✅ Foreground Service — bypasses Android 10+ background activity restriction
class NightChargingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // Step 1: Start as foreground service immediately (required by Android)
        startForeground(999, buildForegroundNotification())

        // Step 2: Play alert sound
        playAlertSound()

        // Step 3: Vibrate device
        vibrateDevice()

        // Step 4: Show full-screen notification that opens ChargingDialogActivity
        showFullScreenNotification()

        // Step 5: Stop service after launching — it has done its job
        stopSelf()

        return START_NOT_STICKY
    }

    // ✅ Required foreground notification to keep service alive long enough
    private fun buildForegroundNotification(): Notification {
        val channelId = "night_charging_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Night Charging Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("PowerPulse")
            .setContentText("Night charging detected...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ✅ Full-screen notification — this is the CORRECT way to show UI
    // from background on Android 10+ without being blocked
    private fun showFullScreenNotification() {
        val channelId = "night_charging_alert"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Night Charging Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Night time charging protection alert"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        // Intent for Fast Charge button
        val fastChargeIntent = Intent(this, ChargingDialogActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("CHARGE_MODE", "FAST")
        }
        val fastChargePending = PendingIntent.getActivity(
            this, 1, fastChargeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for Slow Charge button
        val slowChargeIntent = Intent(this, ChargingDialogActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("CHARGE_MODE", "SLOW")
        }
        val slowChargePending = PendingIntent.getActivity(
            this, 2, slowChargeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full screen intent — shows even on lock screen
        val fullScreenIntent = Intent(this, ChargingDialogActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPending = PendingIntent.getActivity(
            this, 3, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Night Charging Detected")
            .setContentText("10 PM–4 AM: Choose your charging mode")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "It is night time (10 PM – 4 AM).\n" +
                                "Choose your charging mode to protect battery health."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            // ✅ This is what wakes the screen and shows on lock screen
            .setFullScreenIntent(fullScreenPending, true)
            // Action buttons in notification
            .addAction(
                android.R.drawable.ic_menu_upload,
                "Fast Charge",
                fastChargePending
            )
            .addAction(
                android.R.drawable.ic_menu_save,
                "Slow Charge",
                slowChargePending
            )
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(888, notification)
    }

    private fun playAlertSound() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun vibrateDevice() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 200, 500, 200, 500),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}