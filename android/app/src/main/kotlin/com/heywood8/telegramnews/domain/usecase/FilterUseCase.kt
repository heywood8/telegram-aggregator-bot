package com.heywood8.telegramnews.domain.usecase

import javax.inject.Inject

class FilterUseCase @Inject constructor() {

    fun shouldForward(text: String?, mode: String, keywords: List<String>): Boolean {
        if (mode == "all") return true

        val normalizedText = (text ?: "").lowercase()
        val normalizedKeywords = keywords.map { it.lowercase() }

        return when (mode) {
            "include" -> normalizedKeywords.isNotEmpty() &&
                    normalizedKeywords.any { kw -> kw in normalizedText }
            "exclude" -> normalizedKeywords.isEmpty() ||
                    normalizedKeywords.none { kw -> kw in normalizedText }
            else -> false
        }
    }
}
