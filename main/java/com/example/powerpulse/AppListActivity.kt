package com.example.powerpulse

import android.app.Activity
import android.app.usage.UsageStatsManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

class AppListActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.BLACK)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(32, 48, 32, 48)

        // Header
        val title = TextView(this)
        title.text = "Background App Usage"
        title.textSize = 20f
        title.setTextColor(Color.WHITE)
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setPadding(0, 0, 0, 4)
        root.addView(title)

        val subtitle = TextView(this)
        subtitle.text = "User apps active in last 24 hours"
        subtitle.textSize = 12f
        subtitle.setTextColor(0xFF888888.toInt())
        subtitle.setPadding(0, 0, 0, 24)
        root.addView(subtitle)

        val appList = getActiveUserApps()

        if (appList.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No active user apps found."
            empty.textSize = 14f
            empty.setTextColor(0xFF888888.toInt())
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, 48, 0, 0)
            root.addView(empty)
        } else {
            // Column headers
            val header = LinearLayout(this)
            header.orientation = LinearLayout.HORIZONTAL
            header.setPadding(0, 0, 0, 8)

            val hApp = TextView(this)
            hApp.text = "App Name"
            hApp.textSize = 11f
            hApp.setTextColor(0xFFBB86FC.toInt())
            hApp.setTypeface(hApp.typeface, Typeface.BOLD)
            hApp.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f
            )
            header.addView(hApp)

            val hTime = TextView(this)
            hTime.text = "Usage Time"
            hTime.textSize = 11f
            hTime.setTextColor(0xFFBB86FC.toInt())
            hTime.setTypeface(hTime.typeface, Typeface.BOLD)
            hTime.gravity = Gravity.CENTER
            hTime.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            header.addView(hTime)

            val hPercent = TextView(this)
            hPercent.text = "Usage %"
            hPercent.textSize = 11f
            hPercent.setTextColor(0xFFBB86FC.toInt())
            hPercent.setTypeface(hPercent.typeface, Typeface.BOLD)
            hPercent.gravity = Gravity.END
            hPercent.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            header.addView(hPercent)

            root.addView(header)

            // Header divider
            val divider = View(this)
            divider.setBackgroundColor(0xFF333333.toInt())
            val divParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            )
            divParams.bottomMargin = 8
            root.addView(divider, divParams)

            appList.forEach { app ->
                root.addView(makeAppRow(app))

                val rowDivider = View(this)
                rowDivider.setBackgroundColor(0xFF222222.toInt())
                root.addView(
                    rowDivider,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    )
                )
            }
        }

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun makeAppRow(app: AppUsageInfo): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(0, 12, 0, 12)

        // Top row: name + time + percent
        val topRow = LinearLayout(this)
        topRow.orientation = LinearLayout.HORIZONTAL

        val nameTv = TextView(this)
        nameTv.text = app.appName
        nameTv.textSize = 13f
        nameTv.setTextColor(Color.WHITE)
        nameTv.setTypeface(nameTv.typeface, Typeface.BOLD)
        nameTv.layoutParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f
        )
        topRow.addView(nameTv)

        val timeTv = TextView(this)
        timeTv.text = app.usageTime
        timeTv.textSize = 12f
        timeTv.setTextColor(0xFF4CAF50.toInt())
        timeTv.gravity = Gravity.CENTER
        timeTv.layoutParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        )
        topRow.addView(timeTv)

        val percentTv = TextView(this)
        percentTv.text = "${app.usagePercent}%"
        percentTv.textSize = 12f
        percentTv.setTextColor(0xFFBB86FC.toInt())
        percentTv.gravity = Gravity.END
        percentTv.layoutParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        )
        topRow.addView(percentTv)

        row.addView(topRow)

        // Progress bar
        val pbParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 8
        )
        pbParams.topMargin = 6

        val pb = ProgressBar(
            this, null,
            android.R.attr.progressBarStyleHorizontal
        )
        pb.max = 100
        pb.progress = app.usagePercent
        pb.progressTintList = android.content.res.ColorStateList.valueOf(
            when {
                app.usagePercent >= 60 -> 0xFFF44336.toInt()
                app.usagePercent >= 30 -> 0xFFFF9800.toInt()
                else                   -> 0xFF4CAF50.toInt()
            }
        )
        row.addView(pb, pbParams)

        return row
    }

    private fun getActiveUserApps(): List<AppUsageInfo> {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = packageManager
        val now = System.currentTimeMillis()
        val oneDayAgo = now - 86400000L

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            oneDayAgo,
            now
        ) ?: return emptyList()

        // ✅ FIX: groupBy packageName — eliminates all duplicates
        // Sum usage time for same package across multiple entries
        return stats
            .filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .mapValues { entry ->
                entry.value.sumOf { it.totalTimeInForeground }
            }
            .filter { (pkg, _) ->
                pkg != packageName &&
                        isUserApp(pm, pkg)
            }
            .toList()
            .sortedByDescending { it.second }
            .take(20)
            .mapNotNull { (pkg, totalTime) ->
                val appName = getRealAppName(pm, pkg)
                    ?: return@mapNotNull null

                val mins = totalTime / 60000L
                val hours = mins / 60
                val minutes = mins % 60
                val timeStr = if (hours > 0)
                    "${hours}h ${minutes}m"
                else
                    "${minutes}m"

                val percent = ((totalTime * 100) / 86400000L)
                    .toInt().coerceIn(1, 100)

                AppUsageInfo(appName, timeStr, percent)
            }
    }

    // ✅ Exclude pure system apps
    private fun isUserApp(pm: PackageManager, packageName: String): Boolean {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and
                    ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            !isSystem || isUpdatedSystem
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getRealAppName(pm: PackageManager, packageName: String): String? {
        return try {
            pm.getApplicationLabel(
                pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            ).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    data class AppUsageInfo(
        val appName: String,
        val usageTime: String,
        val usagePercent: Int
    )
}