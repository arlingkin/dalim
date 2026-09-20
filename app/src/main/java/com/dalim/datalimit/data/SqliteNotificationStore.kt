package com.dalim.datalimit.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.dalim.datalimit.core.VaultEntry

class SqliteNotificationStore(context: Context) : INotificationStore {

    private val helper = Helper(context.applicationContext)

    private val db: SQLiteDatabase get() = helper.writableDatabase

    override fun insert(entry: VaultEntry): Long {
        val values = ContentValues().apply {
            put(COL_PACKAGE, entry.packageName)
            put(COL_APP, entry.appName)
            put(COL_TITLE, entry.title)
            put(COL_TEXT, entry.text)
            put(COL_CHANNEL, entry.channel)
            put(COL_POSTED, entry.postedAtMillis)
            put(COL_IMPORTANCE, entry.importance)
            put(COL_REMOVED, if (entry.removed) 1 else 0)
            put(COL_READ, if (entry.read) 1 else 0)
        }
        return db.insertOrThrow(TABLE, null, values)
    }

    override fun markRemoved(packageName: String, postedAtMillis: Long) {
        db.execSQL(
            "UPDATE $TABLE SET $COL_REMOVED = 1 WHERE _id IN " +
                "(SELECT _id FROM $TABLE WHERE $COL_PACKAGE = ? AND $COL_POSTED = ? " +
                "AND $COL_REMOVED = 0 ORDER BY _id DESC LIMIT 1)",
            arrayOf(packageName, postedAtMillis.toString())
        )
    }

    override fun markRead(id: Long) {
        db.execSQL("UPDATE $TABLE SET $COL_READ = 1 WHERE _id = ?", arrayOf(id.toString()))
    }

    override fun delete(id: Long) {
        db.delete(TABLE, "_id = ?", arrayOf(id.toString()))
    }

    override fun clearAll() {
        db.delete(TABLE, null, null)
    }

    override fun countSince(fromMillis: Long): Int =
        dbCount("$COL_POSTED >= ?", arrayOf(fromMillis.toString()))

    override fun countImportantSince(fromMillis: Long): Int =
        dbCount("$COL_POSTED >= ? AND $COL_IMPORTANCE >= $IMPORTANT_IMPORTANCE", arrayOf(fromMillis.toString()))

    override fun totalCount(): Int = dbCount(null, null)

    override fun query(
        search: String?,
        importantOnly: Boolean,
        packageFilter: String?,
        limit: Int,
        offset: Long
    ): List<VaultEntry> {
        val sb = StringBuilder()
        val args = ArrayList<String>()
        if (importantOnly) {
            sb.append("$COL_IMPORTANCE >= $IMPORTANT_IMPORTANCE")
        }
        if (!packageFilter.isNullOrBlank()) {
            if (sb.isNotEmpty()) sb.append(" AND ")
            sb.append("$COL_PACKAGE = ?")
            args.add(packageFilter)
        }
        if (!search.isNullOrBlank()) {
            val like = "%" + search.trim() + "%"
            if (sb.isNotEmpty()) sb.append(" AND ")
            sb.append("($COL_TITLE LIKE ? OR $COL_TEXT LIKE ? OR $COL_APP LIKE ?)")
            args.add(like); args.add(like); args.add(like)
        }
        if (limit <= 0) return emptyList()

        val cursor = db.query(
            TABLE,
            null,
            if (sb.isEmpty()) null else sb.toString(),
            if (args.isEmpty()) null else args.toTypedArray(),
            null, null,
            "$COL_POSTED DESC",
            "$offset,$limit"
        )
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) add(fromCursor(c))
            }
        }
    }

    override fun distinctPackages(): List<String> {
        val cursor = db.rawQuery(
            "SELECT DISTINCT $COL_PACKAGE FROM $TABLE ORDER BY $COL_PACKAGE",
            null
        )
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) add(c.getString(0))
            }
        }
    }

    override fun cleanupBefore(beforeMillis: Long): Int {
        val deleted = db.delete(TABLE, "$COL_POSTED < ?", arrayOf(beforeMillis.toString()))
        return deleted
    }

    private fun dbCount(where: String?, args: Array<String>?): Int {
        db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE" + if (where == null) "" else " WHERE $where",
            args
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun fromCursor(c: Cursor): VaultEntry = VaultEntry(
        id = c.getLong(c.getColumnIndexOrThrow("_id")),
        packageName = c.getString(c.getColumnIndexOrThrow(COL_PACKAGE)),
        appName = c.getString(c.getColumnIndexOrThrow(COL_APP)),
        title = c.getString(c.getColumnIndexOrThrow(COL_TITLE)),
        text = c.getString(c.getColumnIndexOrThrow(COL_TEXT)),
        channel = c.getString(c.getColumnIndexOrThrow(COL_CHANNEL)),
        postedAtMillis = c.getLong(c.getColumnIndexOrThrow(COL_POSTED)),
        importance = c.getInt(c.getColumnIndexOrThrow(COL_IMPORTANCE)),
        removed = c.getInt(c.getColumnIndexOrThrow(COL_REMOVED)) == 1,
        read = c.getInt(c.getColumnIndexOrThrow(COL_READ)) == 1
    )

    private class Helper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(CREATE_TABLE)
            db.execSQL("CREATE INDEX idx_notif_posted ON $TABLE($COL_POSTED)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE")
            onCreate(db)
        }
    }

    companion object {
        private const val DB_NAME = "dalim_vault.db"
        private const val DB_VERSION = 1
        private const val TABLE = "notifications"
        private const val COL_PACKAGE = "package_name"
        private const val COL_APP = "app_name"
        private const val COL_TITLE = "title"
        private const val COL_TEXT = "text"
        private const val COL_CHANNEL = "channel"
        private const val COL_POSTED = "posted_at"
        private const val COL_IMPORTANCE = "importance"
        private const val COL_REMOVED = "removed"
        private const val COL_READ = "read"
        const val IMPORTANT_IMPORTANCE = 4

        private const val CREATE_TABLE = """
            CREATE TABLE $TABLE (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PACKAGE TEXT NOT NULL,
                $COL_APP TEXT,
                $COL_TITLE TEXT,
                $COL_TEXT TEXT,
                $COL_CHANNEL TEXT,
                $COL_POSTED INTEGER NOT NULL,
                $COL_IMPORTANCE INTEGER DEFAULT 0,
                $COL_REMOVED INTEGER DEFAULT 0,
                $COL_READ INTEGER DEFAULT 0
            )
        """
    }
}