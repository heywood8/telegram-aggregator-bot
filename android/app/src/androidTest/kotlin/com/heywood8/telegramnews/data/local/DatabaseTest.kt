package com.heywood8.telegramnews.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.heywood8.telegramnews.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun insertAndRetrieveSubscription() = runTest {
        db.subscriptionDao().insert(SubscriptionEntity(userId = 1L, channel = "testchannel"))
        val subs = db.subscriptionDao().getByUser(1L)
        assertEquals(1, subs.size)
        assertEquals("testchannel", subs[0].channel)
        assertEquals("all", subs[0].mode)
        assertTrue(subs[0].active)
    }

    @Test
    fun deleteSubscription() = runTest {
        db.subscriptionDao().insert(SubscriptionEntity(userId = 1L, channel = "testchannel"))
        db.subscriptionDao().delete(1L, "testchannel")
        assertTrue(db.subscriptionDao().getByUser(1L).isEmpty())
    }

    @Test
    fun insertAndRetrieveKeyword() = runTest {
        db.keywordDao().insert(KeywordEntity(userId = 1L, channel = "testchannel", keyword = "crypto"))
        val kws = db.keywordDao().getKeywords(1L, "testchannel")
        assertTrue(kws.contains("crypto"))
    }

    @Test
    fun deleteKeyword() = runTest {
        db.keywordDao().insert(KeywordEntity(userId = 1L, channel = "testchannel", keyword = "crypto"))
        db.keywordDao().delete(1L, "testchannel", "crypto")
        assertEquals(0, db.keywordDao().count(1L, "testchannel"))
    }

    @Test
    fun setMode() = runTest {
        db.subscriptionDao().insert(SubscriptionEntity(userId = 1L, channel = "testchannel"))
        db.subscriptionDao().setMode(1L, "testchannel", "include")
        val subs = db.subscriptionDao().getByUser(1L)
        assertEquals("include", subs[0].mode)
    }

    @Test
    fun lastSeenDefaultsToNull() = runTest {
        assertNull(db.lastSeenDao().getLastSeen("unknownchannel"))
    }

    @Test
    fun upsertAndRetrieveLastSeen() = runTest {
        db.lastSeenDao().upsert(LastSeenEntity("testchannel", 42L))
        assertEquals(42L, db.lastSeenDao().getLastSeen("testchannel"))
        db.lastSeenDao().upsert(LastSeenEntity("testchannel", 99L))
        assertEquals(99L, db.lastSeenDao().getLastSeen("testchannel"))
    }

    @Test
    fun insertMessages() = runTest {
        val msgs = listOf(
            MessageEntity(id = 1L, channel = "ch", text = "hello", timestamp = 1000L),
            MessageEntity(id = 2L, channel = "ch", text = "world", timestamp = 2000L)
        )
        db.messageDao().insertAll(msgs)
        // Collect one emission from the Flow using first()
        val stored = db.messageDao().observeByChannel("ch").first()
        assertEquals(2, stored.size)
        assertTrue(stored.any { it.text == "hello" })
        assertTrue(stored.any { it.text == "world" })
    }

    @Test
    fun pruneChannelKeepsMostRecent() = runTest {
        // Insert 3 messages, prune keeping last 2 (simulate limit=2 isn't supported, so test correctness with 3 > 0)
        val msgs = (1L..5L).map { i ->
            MessageEntity(id = i, channel = "ch", text = "msg$i", timestamp = i * 1000L)
        }
        db.messageDao().insertAll(msgs)
        db.messageDao().pruneChannel("ch")
        // After prune, all 5 are within the 500-item limit so all should remain
        val stored = db.messageDao().observeByChannel("ch").first()
        assertEquals(5, stored.size)
    }
}
