package com.example.powerpulse

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Toast

class ChargingDialogActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Wake screen + show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Cancel the ongoing notification when activity opens
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(888)

        // Check if launched from notification action button
        val chargeMode = intent.getStringExtra("CHARGE_MODE")
        if (chargeMode != null) {
            // User tapped action button directly — handle immediately
            handleChargeMode(chargeMode)
            return
        }

        // Show dialog for tap on notification body
        showChargingDialog()
    }

    private fun showChargingDialog() {
        AlertDialog.Builder(this)
            .setTitle("Night Charging Detected")
            .setMessage(
                "It is night time (10 PM – 4 AM).\n\n" +
                        "Choose your charging mode:\n\n" +
                        "Fast Charge  →  Charges quickly\n" +
                        "Slow Charge  →  Better for battery health"
            )
            .setCancelable(false)
            .setPositiveButton("Fast Charge") { dialog, _ ->
                handleChargeMode("FAST")
                dialog.dismiss()
            }
            .setNegativeButton("Slow Charge") { dialog, _ ->
                handleChargeMode("SLOW")
                dialog.dismiss()
            }
            .show()
    }

    private fun handleChargeMode(mode: String) {
        when (mode) {
            "FAST" -> {
                Toast.makeText(
                    this,
                    "Fast Charging selected.",
                    Toast.LENGTH_LONG
                ).show()
                // Record for battery health score
                BatteryHealthEngine.recordOvernightCharge(this)
            }
            "SLOW" -> {
                Toast.makeText(
                    this,
                    "Slow Charging selected — protecting battery health.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        finish()
    }

    // ✅ Back button disabled — user must choose
    override fun onBackPressed() {
        // Do nothing
    }
}