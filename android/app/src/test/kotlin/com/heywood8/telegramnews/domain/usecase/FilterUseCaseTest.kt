package com.heywood8.telegramnews.domain.usecase

import org.junit.Assert.*
import org.junit.Test

class FilterUseCaseTest {

    private val useCase = FilterUseCase()

    @Test
    fun `mode all forwards everything`() {
        assertTrue(useCase.shouldForward("hello world", mode = "all", keywords = emptyList()))
        assertTrue(useCase.shouldForward("crypto news", mode = "all", keywords = listOf("crypto")))
    }

    @Test
    fun `mode include matches keyword`() {
        assertTrue(useCase.shouldForward("Bitcoin price up", mode = "include", keywords = listOf("bitcoin")))
    }

    @Test
    fun `mode include no match returns false`() {
        assertFalse(useCase.shouldForward("weather report", mode = "include", keywords = listOf("bitcoin")))
    }

    @Test
    fun `mode include empty keywords returns false`() {
        assertFalse(useCase.shouldForward("any text", mode = "include", keywords = emptyList()))
    }

    @Test
    fun `mode exclude no match forwards`() {
        assertTrue(useCase.shouldForward("weather report", mode = "exclude", keywords = listOf("bitcoin")))
    }

    @Test
    fun `mode exclude match blocks`() {
        assertFalse(useCase.shouldForward("Bitcoin price up", mode = "exclude", keywords = listOf("bitcoin")))
    }

    @Test
    fun `mode exclude empty keywords forwards everything`() {
        assertTrue(useCase.shouldForward("any text", mode = "exclude", keywords = emptyList()))
    }

    @Test
    fun `matching is case insensitive`() {
        assertTrue(useCase.shouldForward("BITCOIN is rising", mode = "include", keywords = listOf("bitcoin")))
        assertFalse(useCase.shouldForward("Bitcoin news", mode = "exclude", keywords = listOf("BITCOIN")))
    }

    @Test
    fun `partial word match works`() {
        assertTrue(useCase.shouldForward("cryptocurrency market", mode = "include", keywords = listOf("crypto")))
    }

    @Test
    fun `null text treated as empty`() {
        assertFalse(useCase.shouldForward(null, mode = "include", keywords = listOf("crypto")))
        assertTrue(useCase.shouldForward(null, mode = "all", keywords = emptyList()))
    }
}
