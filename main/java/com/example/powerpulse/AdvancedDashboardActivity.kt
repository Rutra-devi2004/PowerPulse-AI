package com.example.powerpulse

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar

import android.widget.ScrollView
import android.widget.TextView

class AdvancedDashboardActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.BLACK)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(40, 60, 40, 60)

        root.addView(makeLabel("PowerPulse Advanced", 22f, Color.WHITE, true))
        root.addView(makeLabel("AI-Powered Battery Intelligence", 13f, 0xFFBB86FC.toInt(), false))
        root.addDivider()

        val score = BatteryHealthEngine.calculateHealthScore(this)
        val healthLabel = BatteryHealthEngine.getHealthLabel(score)
        val healthColor = BatteryHealthEngine.getHealthColor(score)

        root.addView(makeSectionTitle("Battery Health Score"))
        root.addView(makeScoreCard(score, healthLabel, healthColor))
        root.addDivider()

        root.addView(makeSectionTitle("Intelligent Battery Saver"))
        root.addView(
            makeInfoCard(
                BatteryHealthEngine.getBatterySaverAdvice(this),
                0xFF1A1A2E.toInt()
            )
        )
        root.addDivider()

        root.addView(makeSectionTitle("Smart Usage Pattern"))
        root.addView(
            makeInfoCard(
                BatteryHealthEngine.getUsagePatternInsight(this),
                0xFF1A2E1A.toInt()
            )
        )
        root.addDivider()

        root.addView(makeSectionTitle("Real Battery Drain Analyzer"))
        root.addView(
            makeInfoCard(
                BatteryHealthEngine.getDrainAnalysis(this),
                0xFF2E1A1A.toInt()
            )
        )
        root.addDivider()

        root.addView(makeSectionTitle("Idle App Detection"))
        root.addView(
            makeInfoCard(
                BatteryHealthEngine.getIdleAppWarning(this),
                0xFF1A1A1A.toInt()
            )
        )
        root.addDivider()

        root.addView(makeSectionTitle("Weekly Smart Report"))
        val report = BatteryHealthEngine.getWeeklyReport(this)
        root.addView(makeWeeklyReportCard(report))

        scroll.addView(root)
        setContentView(scroll)
    }

    // ─── UI Helpers ───────────────────────────────────────────────

    private fun makeLabel(
        text: String,
        size: Float,
        color: Int,
        bold: Boolean
    ): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = size
        tv.setTextColor(color)
        if (bold) tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setPadding(0, 8, 0, 4)
        return tv
    }

    private fun makeSectionTitle(text: String): TextView {
        val tv = TextView(this)
        tv.text = text.uppercase()
        tv.textSize = 11f
        tv.setTextColor(0xFF888888.toInt())
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setPadding(0, 20, 0, 8)
        tv.letterSpacing = 0.1f
        return tv
    }

    private fun makeScoreCard(score: Int, label: String, color: Int): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundColor(0xFF1A1A1A.toInt())
        card.setPadding(32, 32, 32, 32)
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        // Score number
        val scoreTv = TextView(this)
        scoreTv.text = getString(R.string.score_format, score)
        scoreTv.textSize = 42f
        scoreTv.setTextColor(color)
        scoreTv.setTypeface(scoreTv.typeface, Typeface.BOLD)
        scoreTv.gravity = Gravity.CENTER
        card.addView(scoreTv)

        // Health status label
        val statusTv = TextView(this)
        statusTv.text = getString(R.string.health_status_format, label)
        statusTv.textSize = 15f
        statusTv.setTextColor(Color.WHITE)
        statusTv.gravity = Gravity.CENTER
        statusTv.setPadding(0, 8, 0, 16)
        card.addView(statusTv)

        // Progress bar — params passed into addView to avoid val reassign error
        val pbParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            16
        )
        val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        pb.max = 100
        pb.progress = score
        pb.progressTintList = ColorStateList.valueOf(color)
        card.addView(pb, pbParams)

        // Health tip
        val tipText = when {
            score >= 85 -> getString(R.string.health_tip_excellent)
            score >= 70 -> getString(R.string.health_tip_good)
            score >= 50 -> getString(R.string.health_tip_fair)
            else        -> getString(R.string.health_tip_poor)
        }
        val tipTv = TextView(this)
        tipTv.text = tipText
        tipTv.textSize = 12f
        tipTv.setTextColor(0xFFAAAAAA.toInt())
        tipTv.setPadding(0, 16, 0, 0)
        card.addView(tipTv)

        return card
    }

    // ✅ FIXED: lineSpacingMultiplier replaced with setLineSpacing()
    private fun makeInfoCard(content: String, bgColor: Int): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundColor(bgColor)
        card.setPadding(28, 24, 28, 24)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = 4
        card.layoutParams = params

        val tv = TextView(this)
        tv.text = content
        tv.textSize = 13f
        tv.setTextColor(Color.WHITE)
        // ✅ FIX: setLineSpacing(add, multiplier) instead of lineSpacingMultiplier
        tv.setLineSpacing(0f, 1.4f)
        card.addView(tv)

        return card
    }

    private fun makeWeeklyReportCard(
        report: BatteryHealthEngine.WeeklyReport
    ): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundColor(0xFF1A1A1A.toInt())
        card.setPadding(28, 24, 28, 24)

        val hours = report.mostUsedMinutes / 60
        val mins = report.mostUsedMinutes % 60
        val timeStr = if (hours > 0)
            getString(R.string.time_format_hm, hours, mins)
        else
            getString(R.string.time_format_m, mins)

        val rows = listOf(
            getString(R.string.report_most_used)     to report.mostUsedApp,
            getString(R.string.report_usage_week)    to timeStr,
            getString(R.string.report_highest_drain) to report.highestDrainApp,
            getString(R.string.report_best_day)      to report.bestBatteryDay,
            getString(R.string.report_worst_day)     to report.worstBatteryDay,
            getString(R.string.report_total_apps)    to
                    getString(R.string.report_apps_count, report.totalAppsUsed)
        )

        rows.forEach { (key, value) ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, 10, 0, 10)

            val keyTv = TextView(this)
            keyTv.text = key
            keyTv.textSize = 12f
            keyTv.setTextColor(0xFF888888.toInt())
            row.addView(
                keyTv,
                LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            )

            val valTv = TextView(this)
            valTv.text = value
            valTv.textSize = 12f
            valTv.setTextColor(0xFFBB86FC.toInt())
            valTv.setTypeface(valTv.typeface, Typeface.BOLD)
            valTv.gravity = Gravity.END
            row.addView(
                valTv,
                LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            )

            card.addView(row)

            // Thin divider between rows
            val divider = View(this)
            divider.setBackgroundColor(0xFF333333.toInt())
            card.addView(
                divider,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
            )
        }

        return card
    }

    private fun LinearLayout.addDivider() {
        val divider = View(context)
        divider.setBackgroundColor(0xFF333333.toInt())
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        )
        params.topMargin = 8
        params.bottomMargin = 8
        addView(divider, params)
    }
}