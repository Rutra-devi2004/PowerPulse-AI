package com.example.powerpulse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Calendar

/**
 * PowerCheckWorker
 * ─────────────────────────────────────────────────────────────────────────────
 * Runs in the background via WorkManager even when the app is closed.
 * Handles:
 * 1. Current foreground app high-usage notification
 * 2. Unused apps alert notification
 *
 * Uses the same SharedPreferences key ("notif_*") as MainActivity so
 * notifications are never duplicated regardless of which path fires them.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class PowerCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    private val CHANNEL_ID = "PowerPulseAlerts"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("PowerPulsePrefs", Context.MODE_PRIVATE)

    override fun doWork(): Result {
        createNotificationChannel()

        if (!hasUsageStatsPermission()) return Result.success()

        checkCurrentAppUsage()
        checkUnusedApps()

        return Result.success()
    }

    // ── Current Foreground App ─────────────────────────────────────────────────

    private fun checkCurrentAppUsage() {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()

        val recent = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 3_000L, now)
        if (recent.isNullOrEmpty()) return

        val current = recent.maxByOrNull { it.lastTimeUsed } ?: return
        if (current.packageName == context.packageName) return
        if (now - current.lastTimeUsed > 3_000L) return

        val pm = context.packageManager
        val appName = try {
            val info = pm.getApplicationInfo(current.packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) { return }

        // Today's usage %
        val todayStats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST, todayMidnightMs(), now
        )
        val total   = todayStats?.sumOf { it.totalTimeInForeground }?.coerceAtLeast(1L) ?: 1L
        val appTime = todayStats?.filter { it.packageName == current.packageName }
            ?.sumOf { it.totalTimeInForeground } ?: 0L
        val pct = ((appTime * 100f) / total).toInt().coerceIn(0, 100)

        if (pct >= 5) smartNotify(
            key     = "current_app",
            title   = "📱 Current App Power",
            message = "Your current app $appName is consuming $pct% power today"
        )
    }

    // ── Unused Apps Alert ──────────────────────────────────────────────────────

    private fun checkUnusedApps() {
        val usm        = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now        = System.currentTimeMillis()
        val fiveMinMs  = 5L  * 60 * 1000
        val twoHoursMs = 2L  * 60 * 60 * 1000
        val pm         = context.packageManager

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST, now - twoHoursMs, now
        )
        if (stats.isNullOrEmpty()) return

        val unusedApps = stats.mapNotNull { stat ->
            val idle = now - stat.lastTimeUsed
            if (idle < fiveMinMs || idle > twoHoursMs) return@mapNotNull null
            if (stat.totalTimeInForeground <= 0L)       return@mapNotNull null
            if (stat.packageName == context.packageName) return@mapNotNull null
            try {
                val info = pm.getApplicationInfo(stat.packageName, 0)
                val isTarget = (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                        (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (isTarget) pm.getApplicationLabel(info).toString() else null
            } catch (e: PackageManager.NameNotFoundException) { null }
        }.distinct()

        if (unusedApps.size >= 2) {
            val preview = unusedApps.take(3).joinToString(", ")
            val extra   = if (unusedApps.size > 3) " +${unusedApps.size - 3} more" else ""
            smartNotify(
                key     = "unused_apps",
                title   = "💤 Close Unused Apps",
                message = "$preview$extra unused for 5+ min. Close to save battery."
            )
        }
    }

    // ── Smart Notify (no duplicates, respects interval) ────────────────────────

    private fun smartNotify(key: String, title: String, message: String) {
        val lastSent = prefs.getLong("notif_$key", 0L)
        val interval = getSelectedInterval()
        if (System.currentTimeMillis() - lastSent < interval) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        nm.notify(key.hashCode() and 0x0FFFFFFF, notif)
        prefs.edit().putLong("notif_$key", System.currentTimeMillis()).apply()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun getSelectedInterval(): Long {
        // Read directly from prefs — same IDs as MainActivity's RadioGroup
        return when (prefs.getInt("selected_timer_id", -1)) {
            // R.id values can't be accessed here directly, so we store the choice
            // as a plain long in "interval_ms" (set in MainActivity.setupTimerPersistence)
            else -> prefs.getLong("interval_ms", 3L * 60 * 1000)
        }
    }

    private fun todayMidnightMs() = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "PowerPulse Alerts", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Battery & power usage alerts"
                enableLights(true)
                lightColor = Color.parseColor("#BB86FC")
                setBypassDnd(false)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ops.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.packageName
            )
        else
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.packageName
            )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
}