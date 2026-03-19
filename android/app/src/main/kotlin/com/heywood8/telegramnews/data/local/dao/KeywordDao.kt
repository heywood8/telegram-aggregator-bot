package com.heywood8.telegramnews.data.local.dao

import androidx.room.*
import com.heywood8.telegramnews.data.local.entity.KeywordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordDao {
    @Query("SELECT keyword FROM keywords WHERE userId = :userId AND channel = :channel")
    suspend fun getKeywords(userId: Long, channel: String): List<String>

    @Query("SELECT * FROM keywords WHERE userId = :userId")
    fun observeByUser(userId: Long): Flow<List<KeywordEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(keyword: KeywordEntity)

    @Query("DELETE FROM keywords WHERE userId = :userId AND channel = :channel AND keyword = :keyword")
    suspend fun delete(userId: Long, channel: String, keyword: String)

    @Query("DELETE FROM keywords WHERE userId = :userId AND channel = :channel")
    suspend fun deleteAll(userId: Long, channel: String)

    @Query("SELECT COUNT(*) FROM keywords WHERE userId = :userId AND channel = :channel")
    suspend fun count(userId: Long, channel: String): Int
}
