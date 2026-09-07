package com.example.powerpulse

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// SQLiteOpenHelper class-ah extend panni namma database logic ezhudhuvom
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "powerpulse_db", null, 1) {

    override fun onCreate(db: SQLiteDatabase?) {
        // PPT source 10-la sonna maari powerpulse_db-la table create panrom
        val createTable = "CREATE TABLE usage_table (id INTEGER PRIMARY KEY AUTOINCREMENT, battery_level INTEGER, screen_time TEXT, timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)"
        db?.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS usage_table")
        onCreate(db)
    }

    // Battery and screen time data-va save panna indha function use aagum [cite: 5, 10]
    fun insertData(battery: Int, time: String) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put("battery_level", battery)
        values.put("screen_time", time)
        db.insert("usage_table", null, values)
        db.close()
    }
}