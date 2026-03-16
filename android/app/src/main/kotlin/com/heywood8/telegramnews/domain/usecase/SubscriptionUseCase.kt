package com.heywood8.telegramnews.domain.usecase

import com.heywood8.telegramnews.domain.repository.LocalRepository
import javax.inject.Inject

class SubscriptionUseCase @Inject constructor(
    private val localRepo: LocalRepository,
    private val filterUseCase: FilterUseCase
) {
    suspend fun addSubscription(userId: Long, channel: String) {
        localRepo.addSubscription(userId, channel)
    }

    suspend fun removeSubscription(userId: Long, channel: String) {
        localRepo.removeSubscription(userId, channel)
    }

    suspend fun addKeyword(userId: Long, channel: String, keyword: String) {
        localRepo.addKeyword(userId, channel, keyword.lowercase())
    }

    suspend fun removeKeyword(userId: Long, channel: String, keyword: String) {
        localRepo.removeKeyword(userId, channel, keyword.lowercase())
        if (localRepo.getKeywords(userId, channel).isEmpty()) {
            localRepo.setMode(userId, channel, "all")
        }
    }

    suspend fun setMode(userId: Long, channel: String, mode: String): Result<Unit> {
        if (mode in listOf("include", "exclude")) {
            val keywords = localRepo.getKeywords(userId, channel)
            if (keywords.isEmpty()) {
                return Result.failure(IllegalStateException("Cannot set mode '$mode' without keywords"))
            }
        }
        localRepo.setMode(userId, channel, mode)
        return Result.success(Unit)
    }
}
