package com.example.powerpulse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class ChargingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED) return

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // Night window: 10 PM (22) to 4 AM (3)
        if (hour >= 22 || hour < 4) {

            // ✅ KEY FIX: Start ForegroundService instead of Activity
            // Android 10+ blocks startActivity() from background
            // but startForegroundService() always works
            val serviceIntent = Intent(context, NightChargingService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}