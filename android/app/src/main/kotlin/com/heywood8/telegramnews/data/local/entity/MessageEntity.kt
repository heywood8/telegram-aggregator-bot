package com.heywood8.telegramnews.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: Long,
    val channel: String,
    val text: String,
    val timestamp: Long,
    val mediaType: String? = null,
    val mediaUrl: String? = null
)
