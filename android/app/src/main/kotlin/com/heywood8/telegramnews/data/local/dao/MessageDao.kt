package com.heywood8.telegramnews.data.local.dao

import androidx.room.*
import com.heywood8.telegramnews.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE channel = :channel ORDER BY timestamp DESC")
    fun observeByChannel(channel: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("""
        DELETE FROM messages WHERE channel = :channel AND id NOT IN (
            SELECT id FROM messages WHERE channel = :channel ORDER BY timestamp DESC LIMIT 500
        )
    """)
    suspend fun pruneChannel(channel: String)
}
