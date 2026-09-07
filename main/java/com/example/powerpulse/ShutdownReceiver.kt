package com.example.powerpulse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator

class ShutdownReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SHUTDOWN) return

        // Play alert sound — try ToneGenerator first, fallback to Ringtone
        playAlertSound(context)

        // Send notification before shutdown completes
        BatteryNotificationHelper.sendNotification(
            context = context,
            id = 201,
            title = "Phone Shutting Down",
            message = "Apps are still running in the background",
            channelId = "shutdown_ch"
        )
    }

    private fun playAlertSound(context: Context) {
        // Method 1: ToneGenerator (fastest, no file needed)
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
            return
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Method 2: Fallback to system ringtone
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}