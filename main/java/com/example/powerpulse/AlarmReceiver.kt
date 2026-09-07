package com.example.powerpulse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// ✅ Receives AlarmManager trigger for short intervals (e.g. 3 minutes)
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Run the same battery worker logic
        val worker = BatteryWorkerHelper(context)
        worker.checkAndNotify()
    }
}