package com.heywood8.telegramnews.data.local.entity

import androidx.room.Entity

@Entity(tableName = "keywords", primaryKeys = ["userId", "channel", "keyword"])
data class KeywordEntity(
    val userId: Long,
    val channel: String,
    val keyword: String
)
