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
import androidx.work.Worker
import androidx.work.WorkerParameters

class BatteryWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        checkBatteryUsage()
        checkIdleBackgroundApps()
        return Result.success()
    }

    private fun checkBatteryUsage() {
        val usm = applicationContext.getSystemService(
            Context.USAGE_STATS_SERVICE
        ) as UsageStatsManager
        val pm = applicationContext.packageManager
        val now = System.currentTimeMillis()

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 86400000L,
            now
        ) ?: return

        // ✅ groupBy to deduplicate, pick top draining app
        val top = stats
            .filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .mapValues { entry ->
                entry.value.sumOf { it.totalTimeInForeground }
            }
            .filter { (pkg, _) ->
                pkg != applicationContext.packageName
            }
            .toList()
            .sortedByDescending { it.second }
            .firstOrNull() ?: return

        val appName = getAppName(pm, top.first)
        val percent = ((top.second * 100) / 86400000L)
            .toInt().coerceIn(1, 100)

        if (percent >= 10) {
            BatteryNotificationHelper.sendNotification(
                context = applicationContext,
                id = 101,
                title = "Battery Alert – $appName",
                message = "$appName is consuming $percent% of battery"
            )
        }
    }

    private fun checkIdleBackgroundApps() {
        val usm = applicationContext.getSystemService(
            Context.USAGE_STATS_SERVICE
        ) as UsageStatsManager
        val pm = applicationContext.packageManager
        val now = System.currentTimeMillis()
        val fiveMinutesAgo = now - (5 * 60 * 1000L)

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 86400000L,
            now
        ) ?: return

        // ✅ groupBy first — then filter unused
        // ✅ No system app filter — counts ALL unused apps
        val unusedApps = stats
            .filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .mapValues { entry ->
                entry.value.maxOf { it.lastTimeUsed }
            }
            .filter { (pkg, lastUsed) ->
                lastUsed < fiveMinutesAgo &&
                        pkg != applicationContext.packageName
            }

        if (unusedApps.size > 1) {
            val intent = Intent(
                applicationContext,
                UnusedAppsActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            sendNotificationWithIntent(
                id = 102,
                title = "Unused Apps Detected",
                message = "${unusedApps.size} apps are running unused. " +
                        "Tap to close them and save battery.",
                pendingIntent = pendingIntent
            )
        }
    }

    private fun sendNotificationWithIntent(
        id: Int,
        title: String,
        message: String,
        pendingIntent: PendingIntent
    ) {
        val nm = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    "alert_ch",
                    "Power Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val notification = NotificationCompat.Builder(applicationContext, "alert_ch")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(id, notification)
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