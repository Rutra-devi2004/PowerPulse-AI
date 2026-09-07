package com.example.powerpulse

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.*
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var listView: ListView
    private var isAppWise = true

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val tempC = tempRaw / 10.0f
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            if (isCharging && level >= 90) {
                BatteryNotificationHelper.sendNotification(
                    applicationContext,
                    id = 103,
                    title = "Battery Almost Full",
                    message = "Battery almost full. Consider unplugging to protect battery health."
                )
            }

            if (tempC > 45f) {
                BatteryNotificationHelper.sendNotification(
                    applicationContext,
                    id = 104,
                    title = "Device Overheating",
                    message = "Device is overheating (${tempC}°C). Close apps to cool down."
                )
            }

            updateBatteryAndLife()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("PowerPulsePrefs", Context.MODE_PRIVATE)
        listView = findViewById(R.id.mainListView)

        if (!hasUsagePermission()) {
            showPermissionDialog()
            return
        }

        initApp()
    }

    override fun onResume() {
        super.onResume()
        if (!hasUsagePermission()) {
            showPermissionDialog()
            return
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        updateBatteryAndLife()
        refreshListData()
        loadPersistence()
        runSmartGuardian()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
            // Safe to ignore
        }
    }

    private fun initApp() {
        updateBatteryAndLife()
        refreshListData()
        loadPersistence()

        findViewById<TextView>(R.id.tabAppWise).setOnClickListener {
            isAppWise = true
            toggleTabs()
        }
        findViewById<TextView>(R.id.tabWeekly).setOnClickListener {
            isAppWise = false
            toggleTabs()
        }

        setupRadioGroupListener()

        findViewById<Button>(R.id.btnAdvancedDashboard).setOnClickListener {
            startActivity(Intent(this, AdvancedDashboardActivity::class.java))
        }

        runSmartGuardian()
    }

    private fun runSmartGuardian() {
        val result = SmartGuardianManager.analyze(this)
        findViewById<TextView>(R.id.tvGuardianStatus).text = result
    }

    private fun setupRadioGroupListener() {
        findViewById<RadioGroup>(R.id.radioGroupAlert)
            .setOnCheckedChangeListener { _, id ->
                val mins = radioIdToMins(id)
                saveConfig(id, mins)
                // ✅ FIXED: Use AlarmManager for 3m, WorkManager for rest
                scheduleAlert(mins)
            }
    }

    private fun radioIdToMins(id: Int): Long = when (id) {
        R.id.radio3m  -> 3L
        R.id.radio15m -> 15L
        R.id.radio1h  -> 60L
        R.id.radio2h  -> 120L
        else          -> 15L
    }

    // ✅ FIXED: Smart scheduling — AlarmManager for short, WorkManager for long
    private fun scheduleAlert(mins: Long) {
        if (mins < 15L) {
            // Use AlarmManager for intervals shorter than WorkManager minimum
            scheduleWithAlarmManager(mins)
        } else {
            // Cancel any existing AlarmManager schedule
            cancelAlarmManager()
            // Use WorkManager for 15m, 1h, 2h
            setupWorkManager(mins)
        }
    }

    // ✅ AlarmManager for 3-minute interval
    private fun scheduleWithAlarmManager(mins: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalMs = mins * 60 * 1000L
        val triggerAt = SystemClock.elapsedRealtime() + intervalMs

        // Cancel WorkManager when using AlarmManager
        WorkManager.getInstance(this).cancelUniqueWork("PowerGuard")

        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            intervalMs,
            pendingIntent
        )
    }

    private fun cancelAlarmManager() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage(
                "PowerPulse needs Usage Access permission to show real app names " +
                        "and battery usage.\n\nPlease enable it in the next screen."
            )
            .setCancelable(false)
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .show()
    }

    private fun hasUsagePermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun toggleTabs() {
        findViewById<TextView>(R.id.tabAppWise).setTextColor(
            if (isAppWise) 0xFFBB86FC.toInt() else 0xFF888888.toInt()
        )
        findViewById<TextView>(R.id.tabWeekly).setTextColor(
            if (!isAppWise) 0xFFBB86FC.toInt() else 0xFF888888.toInt()
        )
        refreshListData()
    }

    private fun refreshListData() {
        val pm = packageManager
        val displayItems = mutableListOf<BatteryUsageItem>()

        if (isAppWise) {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 86400000L,
                now
            )

            if (!stats.isNullOrEmpty()) {
                stats.filter { it.totalTimeInForeground > 0 }
                    .groupBy { it.packageName }
                    .mapValues { e -> e.value.sumOf { it.totalTimeInForeground } }
                    .toList()
                    .sortedByDescending { it.second }
                    .take(15)
                    .forEach { (pkg, time) ->
                        val label = getRealAppName(pm, pkg)
                        val percent = ((time * 100) / 86400000L)
                            .toInt().coerceIn(1, 100)
                        displayItems.add(
                            BatteryUsageItem(label, percent, "$percent% Usage")
                        )
                    }
            } else {
                displayItems.add(
                    BatteryUsageItem("No data available", 0, "Grant Usage Access")
                )
            }

        } else {
            // ✅ Real weekly data using UsageStatsManager
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val dayMs = 86400000L

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val weekStart = calendar.timeInMillis

            val weekDays = listOf(
                "Monday", "Tuesday", "Wednesday",
                "Thursday", "Friday", "Saturday", "Sunday"
            )

            weekDays.forEachIndexed { index, day ->
                val dayStart = weekStart + (index * dayMs)
                val dayEnd = (dayStart + dayMs).coerceAtMost(now)

                if (dayStart > now) {
                    displayItems.add(BatteryUsageItem(day, 0, "0h 0m"))
                    return@forEachIndexed
                }

                val dayStats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    dayStart,
                    dayEnd
                )

                val totalMs = dayStats
                    ?.filter { it.totalTimeInForeground > 0 }
                    ?.sumOf { it.totalTimeInForeground } ?: 0L

                val totalMins = totalMs / 60000L
                val hours = totalMins / 60
                val mins = totalMins % 60
                val timeStr = "${hours}h ${mins}m"

                val percent = ((totalMs * 100) / (12 * 60 * 60 * 1000L))
                    .toInt().coerceIn(0, 100)

                displayItems.add(BatteryUsageItem(day, percent, timeStr))
            }
        }

        listView.adapter = object : ArrayAdapter<BatteryUsageItem>(this, 0, displayItems) {
            override fun getView(pos: Int, conv: View?, parent: ViewGroup): View {
                val v = conv ?: LayoutInflater.from(context)
                    .inflate(R.layout.item_app_usage, parent, false)
                val item = getItem(pos)!!
                v.findViewById<TextView>(R.id.appName).text = item.name
                v.findViewById<TextView>(R.id.appPercent).text = item.displayText
                v.findViewById<ProgressBar>(R.id.appProgress).progress = item.percent
                return v
            }
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

    data class BatteryUsageItem(
        val name: String,
        val percent: Int,
        val displayText: String
    )

    private fun updateBatteryAndLife() {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        findViewById<TextView>(R.id.tvBatteryStatus).text = "$level%"
        val totalMins = level * 8
        val h = totalMins / 60
        val m = totalMins % 60
        findViewById<TextView>(R.id.tvEstimatedTime).text = "Estimated Life: ${h}h ${m}m"
    }

    private fun saveConfig(id: Int, mins: Long) =
        prefs.edit().putInt("sid", id).putLong("smins", mins).apply()

    private fun loadPersistence() {
        val savedId = prefs.getInt("sid", -1)
        if (savedId != -1) {
            val rg = findViewById<RadioGroup>(R.id.radioGroupAlert)
            rg.setOnCheckedChangeListener(null)
            rg.check(savedId)
            // ✅ Restore scheduling on reopen
            val savedMins = prefs.getLong("smins", 15L)
            scheduleAlert(savedMins)
            setupRadioGroupListener()
        }
    }

    private fun setupWorkManager(mins: Long) {
        val safeInterval = mins.coerceAtLeast(15L)
        val request = PeriodicWorkRequestBuilder<BatteryWorker>(
            safeInterval, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PowerGuard",
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }
}