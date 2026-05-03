package com.uschwar.morseringer.domain.usecase

import com.uschwar.morseringer.domain.MorseEncoder

/**
 * A simple domain use case that wraps the [MorseEncoder] logic.
 * 
 * Provides a standard interface for translating plain text into a Morse 
 * code symbol sequence.
 */
class TextToMorseUseCase {
    operator fun invoke(text: String): String {
        return MorseEncoder.encode(text)
    }
}