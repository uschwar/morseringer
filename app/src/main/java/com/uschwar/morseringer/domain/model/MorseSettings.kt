package com.uschwar.morseringer.domain.model

/**
 * Audio configuration for Morse code playback.
 *
 * Timing follows the standard Morse formula where the duration of one "unit"
 * (a single dot) is `1200 / WPM` milliseconds. All gap/length multipliers are
 * expressed as multiples of that unit.
 */
data class MorseSettings(
    val wpm: Int = DEFAULT_WPM,
    val frequencyHz: Int = DEFAULT_FREQUENCY_HZ,
    val dashMultiplier: Float = 3.0f,
    val interCharGapMultiplier: Float = 3.0f,
    val wordGapMultiplier: Float = 7.0f,
) {
    val unitDurationMs: Long
        get() = if (wpm > 0) MS_PER_MINUTE_PER_WPM_FACTOR / wpm else FALLBACK_UNIT_DURATION_MS

    companion object {
        const val DEFAULT_WPM = 20
        const val DEFAULT_FREQUENCY_HZ = 600

        const val MIN_WPM = 10
        const val MAX_WPM = 40
        const val MIN_FREQUENCY_HZ = 200
        const val MAX_FREQUENCY_HZ = 1500

        private const val MS_PER_MINUTE_PER_WPM_FACTOR = 1200L
        private const val FALLBACK_UNIT_DURATION_MS = 60L
    }
}
