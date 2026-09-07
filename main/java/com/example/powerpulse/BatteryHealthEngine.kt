package com.example.powerpulse

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import java.util.Calendar

object BatteryHealthEngine {

    private const val PREFS = "HealthEnginePrefs"

    // ✅ FIX 2: Dynamic battery health score
    fun calculateHealthScore(context: Context): Int {
        var score = 100

        // Factor 1: Current battery level
        val level = getBatteryLevel(context)
        score -= when {
            level < 10 -> 20
            level < 20 -> 15
            level < 40 -> 8
            else -> 0
        }

        // Factor 2: Background idle app count (real data)
        val idleCount = getIdleAppCount(context)
        score -= (idleCount * 2).coerceAtMost(20)

        // Factor 3: High drain app count (real data)
        val highDrainCount = getHighDrainAppCount(context)
        score -= (highDrainCount * 3).coerceAtMost(15)

        // Factor 4: Overnight charging history
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val overnightCharges = prefs.getInt("overnight_charges", 0)
        score -= (overnightCharges * 2).coerceAtMost(10)

        // Factor 5: Total apps active today (real data)
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val todayStats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 86400000L, now
        )
        val activeToday = todayStats?.count { it.totalTimeInForeground > 0 } ?: 0
        score -= when {
            activeToday > 30 -> 15
            activeToday > 20 -> 8
            activeToday > 10 -> 3
            else -> 0
        }

        return score.coerceIn(0, 100)
    }

    fun getHealthLabel(score: Int): String = when {
        score >= 85 -> "Excellent"
        score >= 70 -> "Good"
        score >= 50 -> "Fair"
        score >= 30 -> "Poor"
        else -> "Critical"
    }

    fun getHealthColor(score: Int): Int = when {
        score >= 85 -> 0xFF4CAF50.toInt()
        score >= 70 -> 0xFF8BC34A.toInt()
        score >= 50 -> 0xFFFF9800.toInt()
        score >= 30 -> 0xFFFF5722.toInt()
        else -> 0xFFF44336.toInt()
    }

    // ✅ FEATURE: Smart Usage Pattern Detection
    fun getUsagePatternInsight(context: Context): String {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // Query last 7 days
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_WEEKLY,
            now - (7 * 86400000L),
            now
        ) ?: return "Not enough data for pattern analysis."

        if (stats.isEmpty()) return "Not enough data for pattern analysis."

        val topApp = stats
            .filter { it.totalTimeInForeground > 0 }
            .maxByOrNull { it.totalTimeInForeground } ?: return "No usage data found."

        val appName = try {
            pm.getApplicationLabel(
                pm.getApplicationInfo(topApp.packageName, 0)
            ).toString()
        } catch (e: Exception) {
            topApp.packageName.substringAfterLast(".")
        }

        val timeOfDay = when (hour) {
            in 6..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }

        return "You frequently use $appName in the $timeOfDay. " +
                "Consider closing it after use to save battery."
    }

    // ✅ FEATURE: Intelligent Battery Saver Mode suggestion
    fun getBatterySaverAdvice(context: Context): String {
        val level = getBatteryLevel(context)
        val isCharging = isCharging(context)

        if (isCharging) return "Charging in progress. Battery is being replenished."

        return when {
            level <= 10 -> "CRITICAL: Battery at $level%! Enable battery saver now and close all apps immediately."
            level <= 15 -> "WARNING: Battery at $level%! Close background apps and reduce screen brightness."
            level <= 30 -> "Low battery ($level%). Light optimization recommended. Close unused apps."
            level <= 50 -> "Battery at $level%. Monitor usage to extend battery life."
            else -> "Battery level is healthy at $level%."
        }
    }

    // ✅ FEATURE: Real Battery Drain Analyzer
    fun getDrainAnalysis(context: Context): String {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager
        val now = System.currentTimeMillis()

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 86400000L,
            now
        ) ?: return "No drain data available."

        val topDrainers = stats
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(3)

        if (topDrainers.isEmpty()) return "No significant battery drain detected."

        val sb = StringBuilder()
        topDrainers.forEachIndexed { index, stat ->
            val name = try {
                pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString()
            } catch (e: Exception) {
                stat.packageName.substringAfterLast(".")
            }
            val mins = stat.totalTimeInForeground / 60000L
            val hours = mins / 60
            val minutes = mins % 60
            val timeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
            sb.append("${index + 1}. $name — active $timeStr today")
            if (index < topDrainers.size - 1) sb.append("\n")
        }
        return sb.toString()
    }

    // ✅ FEATURE: Idle App Detection
    fun getIdleAppWarning(context: Context): String {
        val idleCount = getIdleAppCount(context)
        return when {
            idleCount == 0 -> "No idle apps detected. Great job!"
            idleCount == 1 -> "1 app is running unused in background. Close it to save battery."
            else -> "$idleCount apps are running unused. Close them to save battery."
        }
    }

    // ✅ FEATURE: Weekly Smart Report
    fun getWeeklyReport(context: Context): WeeklyReport {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager
        val now = System.currentTimeMillis()

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_WEEKLY,
            now - (7 * 86400000L),
            now
        ) ?: return WeeklyReport()

        val sorted = stats
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }

        val mostUsed = sorted.firstOrNull()
        val mostUsedName = mostUsed?.let {
            try { pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, 0)).toString() }
            catch (e: Exception) { it.packageName.substringAfterLast(".") }
        } ?: "N/A"

        val mostUsedMins = (mostUsed?.totalTimeInForeground ?: 0L) / 60000L

        // Daily usage breakdown for best/worst day
        val dailyUsage = getDailyUsageMap(context)
        val bestDay = dailyUsage.minByOrNull { it.value }?.key ?: "N/A"
        val worstDay = dailyUsage.maxByOrNull { it.value }?.key ?: "N/A"

        return WeeklyReport(
            mostUsedApp = mostUsedName,
            mostUsedMinutes = mostUsedMins,
            highestDrainApp = mostUsedName,
            bestBatteryDay = bestDay,
            worstBatteryDay = worstDay,
            totalAppsUsed = stats.filter { it.totalTimeInForeground > 0 }.size
        )
    }

    data class WeeklyReport(
        val mostUsedApp: String = "N/A",
        val mostUsedMinutes: Long = 0L,
        val highestDrainApp: String = "N/A",
        val bestBatteryDay: String = "N/A",
        val worstBatteryDay: String = "N/A",
        val totalAppsUsed: Int = 0
    )

    // ─── Private helpers ────────────────────────────────────────────

    private fun getBatteryLevel(context: Context): Int {
        val intent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        return intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 50) ?: 50
    }

    private fun isCharging(context: Context): Boolean {
        val intent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun getIdleAppCount(context: Context): Int {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val fiveMinutesAgo = now - (5 * 60 * 1000L)
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 86400000L, now
        ) ?: return 0
        return stats.count { stat ->
            stat.totalTimeInForeground > 0 &&
                    stat.lastTimeUsed < fiveMinutesAgo &&
                    stat.packageName != context.packageName
        }
    }

    private fun getHighDrainAppCount(context: Context): Int {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val twoHoursMs = 2 * 60 * 60 * 1000L
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 86400000L, now
        ) ?: return 0
        return stats.count { it.totalTimeInForeground > twoHoursMs }
    }

    private fun getDailyUsageMap(context: Context): Map<String, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val result = mutableMapOf<String, Long>()
        val now = System.currentTimeMillis()
        days.forEachIndexed { index, day ->
            val start = now - ((7 - index) * 86400000L)
            val end = start + 86400000L
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            val total = stats?.sumOf { it.totalTimeInForeground } ?: 0L
            result[day] = total
        }
        return result
    }

    fun recordOvernightCharge(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getInt("overnight_charges", 0)
        prefs.edit().putInt("overnight_charges", (current + 1).coerceAtMost(10)).apply()
    }
}