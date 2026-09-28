package com.dalim.datalimit.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.dalim.datalimit.core.HistoryBuckets
import java.time.LocalDate
import java.time.ZoneId

/**
 * Rolling usage-history store. Rows are daily buckets (epoch day, rx, tx
 * deltas recorded while the monitor runs). Higher granularities (weekly /
 * monthly series) are aggregated from those rows on the fly by the pure
 * [com.dalim.datalimit.core.HistoryBuckets] helpers, keeping this class thin
 * over SQLite — same shape as [BatteryHistoryStore] / [SqliteNotificationStore].
 */
class UsageHistoryStore(context: Context) {

    private val helper = Helper(context.applicationContext)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    /**
     * Append a delta for a whole calendar day, or update the row if some usage
     * for that day was already recorded. Never negative: a reboot re-anchored
     * snapshot flows through [com.dalim.datalimit.core.HistoryBuckets.computeDelta],
     * which clamps to 0 and the monitor only records positive deltas.
     */
    fun insert(epochDay: Long, rxBytes: Long, txBytes: Long) {
        if (rxBytes <= 0L && txBytes <= 0L) return
        val prev = db.rawQuery(
            "SELECT $COL_RX, $COL_TX FROM $TABLE WHERE $COL_DAY = ?",
            arrayOf(epochDay.toString())
        ).use { c ->
            if (c.moveToFirst()) {
                HistoryRow(c.getLong(0), c.getLong(1))
            } else null
        }
        if (prev == null) {
            db.insert(TABLE, null, ContentValues().apply {
                put(COL_DAY, epochDay)
                put(COL_RX, rxBytes.coerceAtLeast(0L))
                put(COL_TX, txBytes.coerceAtLeast(0L))
            })
        } else {
            val values = ContentValues().apply {
                put(COL_RX, prev.rxBytes + rxBytes)
                put(COL_TX, prev.txBytes + txBytes)
            }
            db.update(TABLE, values, "$COL_DAY = ?", arrayOf(epochDay.toString()))
        }
    }

    /** Daily rows already recorded, ascending by day. */
    fun dailyRows(): List<HistoryBuckets.Row> {
        val c = db.rawQuery(
            "SELECT $COL_DAY, $COL_RX, $COL_TX FROM $TABLE ORDER BY $COL_DAY ASC",
            null
        )
        return c.use { cur ->
            buildList {
                while (cur.moveToNext()) {
                    add(HistoryBuckets.Row(cur.getLong(0), cur.getLong(1), cur.getLong(2)))
                }
            }
        }
    }

    /** Aggregated bar series for [granularity]; zero-filled, oldest first. */
    fun series(
        granularity: HistoryBuckets.Granularity,
        count: Int,
        nowMillis: Long
    ): List<HistoryBuckets.Bucket> {
        val rows = dailyRows()
        if (rows.isEmpty()) return HistoryBuckets.aggregate(granularity, count, nowEpochDay(nowMillis), emptyList())
        return HistoryBuckets.aggregate(granularity, count, nowEpochDay(nowMillis), rows)
    }

    /** Delete rows older than [days] (retention guard; month buckets need 13mo backfill). */
    fun purgeOlderThan(days: Int, nowMillis: Long) {
        val cutoff = nowEpochDay(nowMillis) - days
        db.delete(TABLE, "$COL_DAY < ?", arrayOf(cutoff.toString()))
    }

    /**
     * One-time backfill derived from the current meter snapshot. Called on
     * monitor start when history is empty after an upgrade; seeds today's
     * bucket with the already-consumed current-window bytes (rx only).
     */
    fun backfillFromCurrent(nowMillis: Long, rxBytes: Long, txBytes: Long, onlyIfEmpty: Boolean = true) {
        val isEmpty = db.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use {
            it.moveToFirst() && it.getInt(0) == 0
        }
        if (onlyIfEmpty && !isEmpty) return
        insert(nowEpochDay(nowMillis), rxBytes, txBytes)
    }

    private fun nowEpochDay(nowMillis: Long): Long =
        LocalDate.now(ZoneId.systemDefault()).toEpochDay()

    private data class HistoryRow(val rxBytes: Long, val txBytes: Long)

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
        private const val DB_NAME = "dalim_usage_history.db"
        private const val DB_VERSION = 1
        private const val TABLE = "usage_history"
        private const val COL_DAY = "bucket_day"
        private const val COL_RX = "rx_bytes"
        private const val COL_TX = "tx_bytes"

        const val MAX_BUCKETS = 12
        const val RETENTION_DAYS = 400

        private const val CREATE_TABLE = """
            CREATE TABLE $TABLE (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DAY INTEGER NOT NULL UNIQUE,
                $COL_RX INTEGER NOT NULL DEFAULT 0,
                $COL_TX INTEGER NOT NULL DEFAULT 0
            )
        """
    }
}