package com.dalim.datalimit.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Rolling battery-level history (level percent vs realtime) used by the
 * battery screen chart. Samples are appended at low frequency (monitor poll
 * tick / screen refresh); rows older than [RETENTION_HOURS] are purged on
 * insert.
 */
class BatteryHistoryStore(context: Context) {

    private val helper = Helper(context.applicationContext)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    /** Append a sample, throttled: skips if one was written in the last [THROTTLE_MILLIS]. */
    fun append(nowMillis: Long, levelPercent: Int) {
        val last = helper.readableDatabase.rawQuery(
            "SELECT $COL_TS FROM $TABLE ORDER BY $COL_TS DESC LIMIT 1",
            null
        )
        val skip = last.use { it.moveToFirst() && nowMillis - it.getLong(0) < THROTTLE_MILLIS }
        if (skip) return
        val values = ContentValues().apply {
            put(COL_TS, nowMillis)
            put(COL_LEVEL, levelPercent.coerceIn(0, 100))
        }
        db.insert(TABLE, null, values)
        db.delete(TABLE, "$COL_TS < ?", arrayOf((nowMillis - RETENTION_MILLIS).toString()))
    }

    /** Samples within the last [hours], oldest first. */
    fun series(hours: Int, nowMillis: Long): List<BatteryPoint> {
        val from = nowMillis - hours * 3_600_000L
        val c = db.rawQuery(
            "SELECT $COL_TS, $COL_LEVEL FROM $TABLE WHERE $COL_TS >= ? ORDER BY $COL_TS",
            arrayOf(from.toString())
        )
        return c.use { cur ->
            buildList {
                while (cur.moveToNext()) {
                    add(BatteryPoint(cur.getLong(0), cur.getInt(1)))
                }
            }
        }
    }

    data class BatteryPoint(val tsMillis: Long, val levelPercent: Int)

    private class Helper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(CREATE_TABLE)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE")
            onCreate(db)
        }
    }

    companion object {
        private const val DB_NAME = "dalim_battery.db"
        private const val DB_VERSION = 1
        private const val TABLE = "battery_samples"
        private const val COL_TS = "ts"
        private const val COL_LEVEL = "level"

        // Aligned window used by both the chart and the scanner.
        const val RETENTION_HOURS = 48L
        private const val THROTTLE_MILLIS = 60_000L
        private const val RETENTION_MILLIS = RETENTION_HOURS * 3_600_000L

        private const val CREATE_TABLE = """
            CREATE TABLE $TABLE (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TS INTEGER NOT NULL,
                $COL_LEVEL INTEGER NOT NULL
            )
        """
    }
}