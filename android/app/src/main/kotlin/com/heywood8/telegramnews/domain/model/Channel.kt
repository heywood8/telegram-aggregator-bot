package com.heywood8.telegramnews.domain.model

data class Channel(
    val username: String,
    val title: String,
    val memberCount: Int = 0
)
