package com.dalim.datalimit.data

import com.dalim.datalimit.core.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryNotificationStoreTest {

    private fun store(): InMemoryNotificationStore {
        val s = InMemoryNotificationStore()
        s.insert(entry(app = "YouTube", title = "New video uploaded", importance = 4, posted = 1000L))
        s.insert(entry(app = "YouTube", title = "Live stream", importance = 4, posted = 2000L))
        s.insert(entry(app = "Gmail", title = "Monthly report", text = "Your invoice", importance = 3, posted = 3000L))
        s.insert(entry(app = "TikTok", title = "Trending now", importance = 1, posted = 4000L))
        return s
    }

    private fun entry(
        app: String,
        title: String,
        text: String = "",
        importance: Int = 3,
        posted: Long,
        pkg: String = app.lowercase()
    ) = VaultEntry(
        packageName = pkg,
        appName = app,
        title = title,
        text = text,
        channel = "ch",
        postedAtMillis = posted,
        importance = importance
    )

    @Test
    fun insertAndCounts() {
        val s = store()
        assertEquals(4, s.totalCount())
        assertEquals(4, s.countSince(0L))
        assertEquals(3, s.countSince(1500L))
    }

    @Test
    fun importantCountUsesHighImportanceThreshold() {
        val s = store()
        // importance >= 4: only the two YouTube entries.
        assertEquals(2, s.countImportantSince(0L))
    }

    @Test
    fun searchMatchesTitleTextAndApp() {
        val s = store()
        val byTitle = s.query("video", false, null, 50, 0)
        assertEquals(listOf("New video uploaded"), byTitle.map { it.title })
        val byText = s.query("invoice", false, null, 50, 0)
        assertEquals(listOf("Monthly report"), byText.map { it.title })
        val byApp = s.query("gmail", false, null, 50, 0)
        assertEquals(listOf("Monthly report"), byApp.map { it.title })
    }

    @Test
    fun importantAndPackageFilter() {
        val s = store()
        val ytImportant = s.query(null, true, "youtube", 50, 0)
        assertEquals(2, ytImportant.size)
        val gmailImportant = s.query(null, true, "gmail", 50, 0)
        assertTrue(gmailImportant.isEmpty())
    }

    @Test
    fun queryOrdersNewestFirstAndPages() {
        val s = store()
        val firstPage = s.query(null, false, null, 2, 0)
        assertEquals(listOf("Trending now", "Monthly report"), firstPage.map { it.title })
        val secondPage = s.query(null, false, null, 2, 2)
        assertEquals(listOf("Live stream", "New video uploaded"), secondPage.map { it.title })
    }

    @Test
    fun deleteSingle() {
        val s = store()
        val id = s.insert(entry(app = "X", title = "t", posted = 9999L))
        s.delete(id)
        assertEquals(4, s.totalCount())
        assertFalse(s.query(null, false, "x", 50, 0).any { it.id == id })
    }

    @Test
    fun clearAll() {
        val s = store()
        s.clearAll()
        assertEquals(0, s.totalCount())
        assertTrue(s.query(null, false, null, 50, 0).isEmpty())
    }

    @Test
    fun retentionCleanupRemovesOldEntries() {
        val s = store()
        val deleted = s.cleanupBefore(2500L)
        assertEquals(2, deleted)
        assertEquals(2, s.totalCount())
        assertTrue(s.query(null, false, null, 50, 0).none { it.title == "New video uploaded" })
    }

    @Test
    fun markRemovedOnlyTouchesLatestMatchingEntry() {
        val s = store()
        s.markRemoved("youtube", 1000L)
        val yt = s.query(null, false, "youtube", 50, 0)
        assertEquals(2, yt.size)
        assertEquals(true, yt.first { it.postedAtMillis == 1000L }.removed)
        assertEquals(false, yt.first { it.postedAtMillis == 2000L }.removed)
    }

    @Test
    fun markRead() {
        val s = store()
        val id = s.insert(entry(app = "X", title = "r", posted = 8000L))
        s.markRead(id)
        assertTrue(s.query(null, false, "x", 50, 0).first { it.id == id }.read)
    }

    @Test
    fun distinctPackagesSorted() {
        val s = store()
        assertEquals(listOf("gmail", "tiktok", "youtube"), s.distinctPackages())
    }
}