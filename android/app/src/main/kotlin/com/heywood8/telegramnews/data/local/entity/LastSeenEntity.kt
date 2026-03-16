package com.heywood8.telegramnews.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "last_seen")
data class LastSeenEntity(
    @PrimaryKey val channel: String,
    val messageId: Long = 0L
)
