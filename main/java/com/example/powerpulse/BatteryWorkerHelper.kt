package com.example.powerpulse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat

// ✅ Shared logic used by both BatteryWorker and AlarmReceiver
class BatteryWorkerHelper(private val context: Context) {

    fun checkAndNotify() {
        checkBatteryUsage()
        checkIdleApps()
    }

    private fun checkBatteryUsage() {
        val usm = context.getSystemService(
            Context.USAGE_STATS_SERVICE
        ) as UsageStatsManager
        val pm = context.packageManager
        val now = System.currentTimeMillis()

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 86400000L, now
        ) ?: return

        val top = stats
            .filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .mapValues { e -> e.value.sumOf { it.totalTimeInForeground } }
            .filter { (pkg, _) -> pkg != context.packageName }
            .toList()
            .sortedByDescending { it.second }
            .firstOrNull() ?: return

        val appName = getAppName(pm, top.first)
        val percent = ((top.second * 100) / 86400000L).toInt().coerceIn(1, 100)

        if (percent >= 10) {
            BatteryNotificationHelper.sendNotification(
                context = context,
                id = 101,
                title = "Battery Alert – $appName",
                message = "$appName is consuming $percent% of battery"
            )
        }
    }

    private fun checkIdleApps() {
        val usm = context.getSystemService(
            Context.USAGE_STATS_SERVICE
        ) as UsageStatsManager
        val now = System.currentTimeMillis()
        val fiveMinutesAgo = now - (5 * 60 * 1000L)

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 86400000L, now
        ) ?: return

        val unusedApps = stats
            .filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .mapValues { e -> e.value.maxOf { it.lastTimeUsed } }
            .filter { (pkg, lastUsed) ->
                lastUsed < fiveMinutesAgo &&
                        pkg != context.packageName
            }

        if (unusedApps.size > 1) {
            val intent = Intent(context, UnusedAppsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nm = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        "alert_ch", "Power Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }

            val notification = NotificationCompat.Builder(context, "alert_ch")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Unused Apps Detected")
                .setContentText(
                    "${unusedApps.size} apps are running unused. " +
                            "Tap to close them and save battery."
                )
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "${unusedApps.size} apps are running unused. " +
                                "Tap to close them and save battery."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(102, notification)
        }
    }

    private fun getAppName(pm: PackageManager, packageName: String): String {
        return try {
            pm.getApplicationLabel(
                pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            ).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        }
    }
}