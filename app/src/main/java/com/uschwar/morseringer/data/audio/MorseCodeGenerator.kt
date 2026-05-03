package com.uschwar.morseringer.data.audio

import com.uschwar.morseringer.domain.model.MorseSettings
import kotlin.math.sin

/**
 * Generates raw PCM audio data for Morse code elements.
 *
 * This class isolates the signal processing logic, specifically the sine wave 
 * synthesis and anti-click fading, from the audio playback hardware management.
 */
class MorseCodeGenerator {

    companion object {
        const val SAMPLE_RATE_HZ = 44_100
        private const val FADE_DURATION_MS = 5
    }

    /**
     * Synthesizes a sine wave tone for a specific number of Morse units.
     */
    fun generateTone(units: Int, settings: MorseSettings): ShortArray {
        val numSamples = units * calculateSamplesPerUnit(settings.unitDurationMs)
        return generateToneSamples(numSamples, settings.frequencyHz.toDouble())
    }

    /**
     * Generates a period of silence for a specific number of Morse units.
     */
    fun generateSilence(units: Int, settings: MorseSettings): ShortArray {
        val numSamples = units * calculateSamplesPerUnit(settings.unitDurationMs)
        return ShortArray(numSamples)
    }

    /**
     * Renders a complete Morse [sequence] into a single contiguous PCM buffer.
     *
     * Symbol semantics mirror the streaming path:
     * - `.` : 1 unit tone + 1 unit silence
     * - `-` : [MorseSettings.dashMultiplier] units tone + 1 unit silence
     * - ` ` : ([MorseSettings.interCharGapMultiplier] - 1) units silence
     * - `/` : ([MorseSettings.wordGapMultiplier] - 1) units silence
     */
    fun renderSequence(sequence: String, settings: MorseSettings): ShortArray {
        val samplesPerUnit = calculateSamplesPerUnit(settings.unitDurationMs)
        val dashUnits = settings.dashMultiplier.toInt()
        val interCharUnits = (settings.interCharGapMultiplier - 1).toInt().coerceAtLeast(0)
        val wordUnits = (settings.wordGapMultiplier - 1).toInt().coerceAtLeast(0)

        // Pre-compute total length to avoid intermediate allocations.
        var totalUnits = 0
        for (s in sequence) {
            totalUnits += when (s) {
                '.' -> 2
                '-' -> dashUnits + 1
                ' ' -> interCharUnits
                '/' -> wordUnits
                else -> 0
            }
        }
        val pcm = ShortArray(totalUnits * samplesPerUnit)

        var offset = 0
        for (s in sequence) {
            when (s) {
                '.' -> {
                    offset = appendTone(pcm, offset, 1, samplesPerUnit, settings.frequencyHz.toDouble())
                    offset += samplesPerUnit // silence (zeros already)
                }
                '-' -> {
                    offset = appendTone(pcm, offset, dashUnits, samplesPerUnit, settings.frequencyHz.toDouble())
                    offset += samplesPerUnit
                }
                ' ' -> offset += interCharUnits * samplesPerUnit
                '/' -> offset += wordUnits * samplesPerUnit
            }
        }
        return pcm
    }

    private fun calculateSamplesPerUnit(unitDurationMs: Long): Int {
        return ((unitDurationMs * SAMPLE_RATE_HZ) / 1000).toInt()
    }

    private fun generateToneSamples(numSamples: Int, frequencyHz: Double): ShortArray {
        val pcm = ShortArray(numSamples)
        val fadeSamples = (FADE_DURATION_MS * SAMPLE_RATE_HZ / 1000).coerceAtMost(numSamples / 2)
        val maxAmplitude = Short.MAX_VALUE.toDouble()

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE_HZ
            val envelope = calculateFadeEnvelope(i, numSamples, fadeSamples)
            pcm[i] = (maxAmplitude * envelope * sin(2.0 * Math.PI * frequencyHz * t)).toInt().toShort()
        }
        return pcm
    }

    private fun calculateFadeEnvelope(index: Int, totalSamples: Int, fadeSamples: Int): Double {
        return when {
            index < fadeSamples -> index.toDouble() / fadeSamples
            index >= totalSamples - fadeSamples -> (totalSamples - index).toDouble() / fadeSamples
            else -> 1.0
        }
    }

    private fun appendTone(
        out: ShortArray,
        offset: Int,
        units: Int,
        samplesPerUnit: Int,
        frequencyHz: Double,
    ): Int {
        val numSamples = units * samplesPerUnit
        val fadeSamples = (FADE_DURATION_MS * SAMPLE_RATE_HZ / 1000).coerceAtMost(numSamples / 2)
        val maxAmplitude = Short.MAX_VALUE.toDouble()
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE_HZ
            val envelope = calculateFadeEnvelope(i, numSamples, fadeSamples)
            out[offset + i] = (maxAmplitude * envelope * sin(2.0 * Math.PI * frequencyHz * t)).toInt().toShort()
        }
        return offset + numSamples
    }
}
