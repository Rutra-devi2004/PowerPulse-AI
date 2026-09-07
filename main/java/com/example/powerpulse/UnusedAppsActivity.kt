package com.example.powerpulse

import android.app.Activity
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class UnusedAppsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.BLACK)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(32, 48, 32, 48)

        // Header
        val title = TextView(this)
        title.text = "Unused Background Apps"
        title.textSize = 20f
        title.setTextColor(Color.WHITE)
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setPadding(0, 0, 0, 4)
        root.addView(title)

        val subtitle = TextView(this)
        subtitle.text = "Apps not used for 5+ minutes"
        subtitle.textSize = 12f
        subtitle.setTextColor(0xFF888888.toInt())
        subtitle.setPadding(0, 0, 0, 8)
        root.addView(subtitle)

        val tip = TextView(this)
        tip.text = "Tap 'Stop App' to open its settings and force stop manually."
        tip.textSize = 11f
        tip.setTextColor(0xFFBB86FC.toInt())
        tip.setPadding(0, 0, 0, 20)
        root.addView(tip)

        // Top divider
        val topDivider = View(this)
        topDivider.setBackgroundColor(0xFF333333.toInt())
        val divParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        )
        divParams.bottomMargin = 16
        root.addView(topDivider, divParams)

        // Load all unused apps (system + user)
        val unusedApps = getUnusedApps()

        if (unusedApps.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No unused background apps found.\nYour battery is safe!"
            empty.textSize = 14f
            empty.setTextColor(0xFF4CAF50.toInt())
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, 48, 0, 0)
            root.addView(empty)
        } else {
            val countTv = TextView(this)
            countTv.text = "${unusedApps.size} apps running unused"
            countTv.textSize = 13f
            countTv.setTextColor(0xFFFF9800.toInt())
            countTv.setTypeface(countTv.typeface, Typeface.BOLD)
            countTv.setPadding(0, 0, 0, 16)
            root.addView(countTv)

            unusedApps.forEach { app ->
                root.addView(makeAppCard(app))
            }
        }

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun makeAppCard(app: UnusedAppInfo): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundColor(0xFF1A1A1A.toInt())
        card.setPadding(24, 20, 24, 20)

        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cardParams.bottomMargin = 12
        card.layoutParams = cardParams

        // Top row: app name + last used
        val topRow = LinearLayout(this)
        topRow.orientation = LinearLayout.HORIZONTAL

        val nameTv = TextView(this)
        nameTv.text = app.appName
        nameTv.textSize = 14f
        nameTv.setTextColor(Color.WHITE)
        nameTv.setTypeface(nameTv.typeface, Typeface.BOLD)
        nameTv.layoutParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        )
        topRow.addView(nameTv)

        val lastUsedTv = TextView(this)
        lastUsedTv.text = app.lastUsedText
        lastUsedTv.textSize = 11f
        lastUsedTv.setTextColor(0xFF888888.toInt())
        lastUsedTv.gravity = Gravity.END
        topRow.addView(lastUsedTv)

        card.addView(topRow)

        // Usage time
        val usageTv = TextView(this)
        usageTv.text = "Used today: ${app.usageTime}"
        usageTv.textSize = 12f
        usageTv.setTextColor(0xFF4CAF50.toInt())
        usageTv.setPadding(0, 6, 0, 12)
        card.addView(usageTv)

        // Stop button
        val stopBtn = Button(this)
        stopBtn.text = "Stop App (Open Settings)"
        stopBtn.textSize = 12f
        stopBtn.setTextColor(Color.BLACK)
        stopBtn.setBackgroundColor(0xFFBB86FC.toInt())

        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        btnParams.topMargin = 4
        stopBtn.layoutParams = btnParams

        stopBtn.setOnClickListener {
            try {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                )
                intent.data = Uri.parse("package:${app.packageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                val fallback = Intent(Settings.ACTION_APPLICATION_SETTINGS)
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(fallback)
            }
        }
        card.addView(stopBtn)

        return card
    }

    // ✅ FIXED: Show ALL apps (system + user) — no FLAG_SYSTEM filtering
    private fun getUnusedApps(): List<UnusedAppInfo> {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = packageManager
        val now = System.currentTimeMillis()
        val fiveMinutesAgo = now - (5 * 60 * 1000L)
        val oneDayAgo = now - 86400000L

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            oneDayAgo,
            now
        ) ?: return emptyList()

        // ✅ groupBy packageName — eliminates duplicates
        val grouped = stats
            .filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .mapValues { entry ->
                val totalTime = entry.value.sumOf { it.totalTimeInForeground }
                val lastUsed = entry.value.maxOf { it.lastTimeUsed }
                Pair(totalTime, lastUsed)
            }

        return grouped
            .filter { (pkg, data) ->
                // ✅ Unused for 5+ minutes
                data.second < fiveMinutesAgo &&
                        // ✅ Not this app itself
                        pkg != packageName
                // ✅ NO system app filter — show ALL apps
            }
            .toList()
            .sortedByDescending { it.second.second }
            .take(30)
            .mapNotNull { (pkg, data) ->
                val totalTime = data.first
                val lastUsed = data.second

                // Get app name — use package name as fallback
                val appName = getAppName(pm, pkg)

                val usageMins = totalTime / 60000L
                val usageHours = usageMins / 60
                val usageMinutes = usageMins % 60
                val usageStr = if (usageHours > 0)
                    "${usageHours}h ${usageMinutes}m"
                else
                    "${usageMinutes}m"

                val agoMs = now - lastUsed
                val agoMins = agoMs / 60000L
                val agoHours = agoMins / 60
                val lastUsedStr = when {
                    agoHours >= 1 -> "Last used ${agoHours}h ago"
                    agoMins >= 1  -> "Last used ${agoMins}m ago"
                    else          -> "Last used just now"
                }

                UnusedAppInfo(
                    packageName = pkg,
                    appName = appName,
                    usageTime = usageStr,
                    lastUsedText = lastUsedStr
                )
            }
    }

    // ✅ Always returns a name — never null, never crashes
    private fun getAppName(pm: PackageManager, packageName: String): String {
        return try {
            pm.getApplicationLabel(
                pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            ).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            // Fallback: clean up package name
            packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        }
    }

    data class UnusedAppInfo(
        val packageName: String,
        val appName: String,
        val usageTime: String,
        val lastUsedText: String
    )
}