package com.uschwar.morseringer.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MorseSettingsTest {

    @Test
    fun `unitDurationMs follows 1200 over WPM formula`() {
        assertEquals(60L, MorseSettings(wpm = 20).unitDurationMs)
        assertEquals(120L, MorseSettings(wpm = 10).unitDurationMs)
        assertEquals(30L, MorseSettings(wpm = 40).unitDurationMs)
    }

    @Test
    fun `unitDurationMs falls back to safe value when WPM is zero or negative`() {
        assertEquals(60L, MorseSettings(wpm = 0).unitDurationMs)
        assertEquals(60L, MorseSettings(wpm = -5).unitDurationMs)
    }

    @Test
    fun `default settings use documented constants`() {
        val defaults = MorseSettings()
        assertEquals(MorseSettings.DEFAULT_WPM, defaults.wpm)
        assertEquals(MorseSettings.DEFAULT_FREQUENCY_HZ, defaults.frequencyHz)
        assertEquals(3.0f, defaults.dashMultiplier)
        assertEquals(3.0f, defaults.interCharGapMultiplier)
        assertEquals(7.0f, defaults.wordGapMultiplier)
    }
}
