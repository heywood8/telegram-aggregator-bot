package com.heywood8.telegramnews.data.local.dao

import androidx.room.*
import com.heywood8.telegramnews.data.local.entity.KeywordEntity

@Dao
interface KeywordDao {
    @Query("SELECT keyword FROM keywords WHERE userId = :userId AND channel = :channel")
    suspend fun getKeywords(userId: Long, channel: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(keyword: KeywordEntity)

    @Query("DELETE FROM keywords WHERE userId = :userId AND channel = :channel AND keyword = :keyword")
    suspend fun delete(userId: Long, channel: String, keyword: String)

    @Query("DELETE FROM keywords WHERE userId = :userId AND channel = :channel")
    suspend fun deleteAll(userId: Long, channel: String)

    @Query("SELECT COUNT(*) FROM keywords WHERE userId = :userId AND channel = :channel")
    suspend fun count(userId: Long, channel: String): Int
}
