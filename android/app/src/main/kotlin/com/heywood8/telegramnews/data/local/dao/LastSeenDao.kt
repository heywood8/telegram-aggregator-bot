package com.heywood8.telegramnews.data.local.dao

import androidx.room.*
import com.heywood8.telegramnews.data.local.entity.LastSeenEntity

@Dao
interface LastSeenDao {
    @Query("SELECT messageId FROM last_seen WHERE channel = :channel")
    suspend fun getLastSeen(channel: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lastSeen: LastSeenEntity)
}
