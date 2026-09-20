package com.dalim.datalimit.data

import com.dalim.datalimit.core.VaultEntry

interface INotificationStore {

    fun insert(entry: VaultEntry): Long

    fun markRemoved(packageName: String, postedAtMillis: Long)

    fun markRead(id: Long)

    fun delete(id: Long)

    fun clearAll()

    fun countSince(fromMillis: Long): Int

    fun countImportantSince(fromMillis: Long): Int

    fun totalCount(): Int

    fun query(search: String?, importantOnly: Boolean, packageFilter: String?, limit: Int, offset: Long): List<VaultEntry>

    fun distinctPackages(): List<String>

    fun cleanupBefore(beforeMillis: Long): Int
}