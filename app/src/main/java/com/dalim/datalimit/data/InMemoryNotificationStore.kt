package com.dalim.datalimit.data

import com.dalim.datalimit.core.IMPORTANT_IMPORTANCE
import com.dalim.datalimit.core.VaultEntry

class InMemoryNotificationStore : INotificationStore {

    private val entries = ArrayList<VaultEntry>()
    private var nextId = 1L

    override fun insert(entry: VaultEntry): Long {
        val id = nextId++
        entries.add(entry.copy(id = id))
        return id
    }

    override fun markRemoved(packageName: String, postedAtMillis: Long) {
        val idx = entries.indexOfLast {
            it.packageName == packageName && it.postedAtMillis == postedAtMillis && !it.removed
        }
        if (idx >= 0) entries[idx] = entries[idx].copy(removed = true)
    }

    override fun markRead(id: Long) {
        val idx = entries.indexOfFirst { it.id == id }
        if (idx >= 0) entries[idx] = entries[idx].copy(read = true)
    }

    override fun delete(id: Long) {
        entries.removeAll { it.id == id }
    }

    override fun clearAll() {
        entries.clear()
    }

    override fun countSince(fromMillis: Long): Int =
        entries.count { it.postedAtMillis >= fromMillis }

    override fun countImportantSince(fromMillis: Long): Int =
        entries.count { it.postedAtMillis >= fromMillis && it.importance >= IMPORTANT_IMPORTANCE }

    override fun totalCount(): Int = entries.size

    override fun query(
        search: String?,
        importantOnly: Boolean,
        packageFilter: String?,
        limit: Int,
        offset: Long
    ): List<VaultEntry> {
        var result: List<VaultEntry> = entries
        if (importantOnly) result = result.filter { it.importance >= IMPORTANT_IMPORTANCE }
        if (!packageFilter.isNullOrBlank()) result = result.filter { it.packageName == packageFilter }
        if (!search.isNullOrBlank()) {
            val s = search.trim()
            result = result.filter {
                it.title.contains(s, ignoreCase = true) ||
                    it.text.contains(s, ignoreCase = true) ||
                    it.appName.contains(s, ignoreCase = true)
            }
        }
        val sorted = result.sortedByDescending { it.postedAtMillis }
        if (limit <= 0) return emptyList()
        return sorted.drop(offset.toInt()).take(limit)
    }

    override fun distinctPackages(): List<String> =
        entries.map { it.packageName }.distinct().sorted()

    override fun cleanupBefore(beforeMillis: Long): Int {
        val before = entries.size
        entries.removeAll { it.postedAtMillis < beforeMillis }
        return before - entries.size
    }
}