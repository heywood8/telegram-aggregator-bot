package com.heywood8.telegramnews.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.heywood8.telegramnews.data.local.dao.*
import com.heywood8.telegramnews.data.local.entity.*

@Database(
    entities = [
        SubscriptionEntity::class,
        KeywordEntity::class,
        MessageEntity::class,
        LastSeenEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun keywordDao(): KeywordDao
    abstract fun messageDao(): MessageDao
    abstract fun lastSeenDao(): LastSeenDao
}
