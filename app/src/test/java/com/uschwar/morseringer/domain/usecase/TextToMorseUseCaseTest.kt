package com.uschwar.morseringer.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextToMorseUseCaseTest {

    private val useCase = TextToMorseUseCase()

    @Test
    fun `invoke delegates to MorseEncoder for a single word`() {
        assertEquals(".... .. / - .... . .-. .", useCase("Hi there"))
    }

    @Test
    fun `invoke encodes empty input as empty string`() {
        assertEquals("", useCase(""))
    }

    @Test
    fun `invoke handles mixed letters and digits`() {
        assertEquals(".- -... -.-. / .---- ..--- ...--", useCase("ABC 123"))
    }
}
