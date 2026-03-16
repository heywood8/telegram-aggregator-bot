package com.heywood8.telegramnews.domain.repository

import com.heywood8.telegramnews.domain.model.Channel
import com.heywood8.telegramnews.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface TelegramRepository {
    fun observeNewMessages(channels: List<String>): Flow<Message>
    suspend fun fetchMessagesSince(channel: String, afterMessageId: Long): List<Message>
    suspend fun searchChannel(query: String): List<Channel>
    suspend fun isLoggedIn(): Boolean
}
