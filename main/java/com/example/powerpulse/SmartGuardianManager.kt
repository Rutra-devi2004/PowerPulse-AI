package com.example.powerpulse

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager

object SmartGuardianManager {

    // Call this from MainActivity to get the guardian status message
    fun analyze(context: Context): String {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val fiveMinutesAgo = now - (5 * 60 * 1000L)
        val oneDayAgo = now - 86400000L

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            oneDayAgo,
            now
        ) ?: return "Unable to read usage data."

        val messages = mutableListOf<String>()

        // ✅ Check 1: Too many idle background apps (not used for 5+ minutes)
        val idleApps = stats.filter { stat ->
            stat.totalTimeInForeground > 0 &&
                    stat.lastTimeUsed < fiveMinutesAgo &&
                    stat.packageName != context.packageName
        }
        if (idleApps.size > 2) {
            messages.add("${idleApps.size} apps are running unused. Close them to save battery.")
        }

        // ✅ Check 2: High battery drain apps (used more than 2 hours today)
        val twoHoursMs = 2 * 60 * 60 * 1000L
        val highDrainApps = stats
            .filter { it.totalTimeInForeground > twoHoursMs }
            .sortedByDescending { it.totalTimeInForeground }
            .take(1)

        highDrainApps.forEach { stat ->
            val appName = getRealAppName(pm, stat.packageName)
            val hours = stat.totalTimeInForeground / (60 * 60 * 1000L)
            messages.add("$appName has been active for ${hours}h. It may be draining battery.")
        }

        // ✅ Check 3: Overall too many apps active today
        val activeCount = stats.count { it.totalTimeInForeground > 0 }
        if (activeCount > 10) {
            messages.add("$activeCount apps were active today. Consider closing unused ones.")
        }

        return if (messages.isEmpty()) {
            "All good! Battery usage is normal."
        } else {
            messages.joinToString("\n\n")
        }
    }

    private fun getRealAppName(pm: PackageManager, packageName: String): String {
        return try {
            pm.getApplicationLabel(
                pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            ).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        }
    }
}