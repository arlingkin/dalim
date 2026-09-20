package com.dalim.datalimit.core

const val IMPORTANT_IMPORTANCE = 4

data class VaultEntry(
    val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val channel: String,
    val postedAtMillis: Long,
    val importance: Int,
    val removed: Boolean = false,
    val read: Boolean = false
)