package com.uschwar.morseringer.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import com.uschwar.morseringer.MorseRingerApp
import com.uschwar.morseringer.domain.model.MorseSettings
import com.uschwar.morseringer.service.MorseForegroundService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Orchestrates Morse code audio playback for incoming calls.
 *
 * Two playback modes are used:
 * - Looping ringtone (`playMorse`): streaming `AudioTrack` so iterations can
 *   be cancelled mid-buffer when the user answers/rejects. Buffer is drained
 *   via `playbackHeadPosition` polling before each iteration ends.
 * - One-shot preview (`playPreview`): static `AudioTrack` with the entire
 *   sequence pre-rendered, so the buffer is never truncated by `release()`.
 */
class MorseCodeAudioPlayer(private val context: Context) {

    private val tag = "MorseCodeAudioPlayer"
    private val generator = MorseCodeGenerator()
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private var playbackJob: Job? = null
    private val playerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var telephonyCallback: TelephonyCallback? = null
    private var audioTrack: AudioTrack? = null
    private val trackLock = Any()

    /** Soft cap (~30 s of audio) above which preview falls back to streaming. */
    private val maxStaticPreviewSamples = MorseCodeGenerator.SAMPLE_RATE_HZ * 30

    @SuppressLint("MissingPermission")
    fun playMorse(phoneNumber: String) {
        stopPlaybackOnly()
        registerTelephonyObserver()

        playbackJob = playerScope.launch {
            val focusRequest = requestAudioFocus()

            try {
                val container = (context.applicationContext as MorseRingerApp).container
                val morseSequence = container.processIncomingCallUseCase(phoneNumber)
                val settings = container.settingsRepository.settingsFlow.first()

                playContinuousLoop(morseSequence, settings)
            } catch (_: CancellationException) {
                // Playback stopped intentionally
            } catch (e: Exception) {
                Log.e(tag, "Unexpected error during playback", e)
            } finally {
                forceReleaseHardwareResources()
                abandonAudioFocus(focusRequest)
            }
        }
    }

    private fun requestAudioFocus(): AudioFocusRequest? {
        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { }
            .build()

        val result = audioManager.requestAudioFocus(focusRequest)
        return if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focusRequest else null
    }

    private fun abandonAudioFocus(focusRequest: AudioFocusRequest?) {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    fun playPreview(morseSequence: String) {
        stopPlaybackOnly()

        playbackJob = playerScope.launch {
            try {
                val container = (context.applicationContext as MorseRingerApp).container
                val settings = container.settingsRepository.settingsFlow.first()

                val pcm = generator.renderSequence(morseSequence, settings)
                if (pcm.isEmpty()) return@launch

                if (pcm.size <= maxStaticPreviewSamples) {
                    playStaticBuffer(pcm)
                } else {
                    playStreamingIteration(morseSequence, settings)
                }
            } catch (_: CancellationException) {
                // Preview cancelled
            } catch (e: Exception) {
                Log.e(tag, "Preview playback failed", e)
            } finally {
                forceReleaseHardwareResources()
            }
        }
    }

    fun stop() {
        stopPlaybackOnly()

        // Also stop the foreground service if it's running
        context.stopService(Intent(context, MorseForegroundService::class.java))
    }

    /**
     * Clears active playback and releases hardware resources WITHOUT 
     * stopping the Android Foreground Service.
     */
    fun stopPlaybackOnly() {
        playbackJob?.cancel()
        playbackJob = null

        unregisterTelephonyObserver()
        forceReleaseHardwareResources()
    }

    // --- Static (preview) playback ---------------------------------------

    private suspend fun playStaticBuffer(pcm: ShortArray) {
        val track = createStaticAudioTrack(pcm.size) ?: return
        synchronized(trackLock) { audioTrack = track }

        val written = track.write(pcm, 0, pcm.size)
        if (written <= 0) {
            Log.w(tag, "Static write failed: $written")
            return
        }
        track.play()
        awaitPlaybackDrained(track, written)
        // No explicit stop(): static buffer plays once and the outer finally
        // releases the track only after the drain completes.
    }

    private fun createStaticAudioTrack(totalSamples: Int): AudioTrack? {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(MorseCodeGenerator.SAMPLE_RATE_HZ)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        return try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(totalSamples * 2) // 16-bit PCM
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (e: Exception) {
            Log.e(tag, "Static AudioTrack initialization failed", e)
            null
        }
    }

    // --- Streaming (looping ringtone) playback ---------------------------

    /**
     * Plays the Morse [sequence] repeatedly into a SINGLE long-lived
     * `AudioTrack`. This should improve audio playback (no cut offs)
     * when the app is not running.
     */
    private fun playContinuousLoop(sequence: String, settings: MorseSettings) {
        val samplesPerUnit =
            ((settings.unitDurationMs * MorseCodeGenerator.SAMPLE_RATE_HZ) / 1000).toInt()
        val track = createStreamingAudioTrack(samplesPerUnit) ?: return

        synchronized(trackLock) { audioTrack = track }
        track.play()

        // Pre-render the gap once: 1 second of silence between repetitions.
        val gapSilence = ShortArray(MorseCodeGenerator.SAMPLE_RATE_HZ)

        while (playbackJob?.isActive == true) {
            streamMorseToHardware(sequence, settings, track)
            if (playbackJob?.isActive != true) break
            // Active silence keeps the AudioTrack in PLAYING state.
            track.write(gapSilence, 0, gapSilence.size)
        }
        // Track is released by the outer coroutine `finally` (or `stop()`).
    }

    private suspend fun playStreamingIteration(sequence: String, settings: MorseSettings) {
        val samplesPerUnit = ((settings.unitDurationMs * MorseCodeGenerator.SAMPLE_RATE_HZ) / 1000).toInt()
        val track = createStreamingAudioTrack(samplesPerUnit) ?: return

        synchronized(trackLock) { audioTrack = track }

        track.play()
        val framesWritten = streamMorseToHardware(sequence, settings, track)
        if (playbackJob?.isActive == true && framesWritten > 0) {
            awaitPlaybackDrained(track, framesWritten)
        }
        // Track stays referenced; the outer coroutine finally (or stop())
        // performs the single release. Skipping track.stop() here avoids
        // truncating the buffered tail.
    }

    private fun streamMorseToHardware(
        sequence: String,
        settings: MorseSettings,
        track: AudioTrack,
    ): Int {
        var totalFrames = 0
        for (symbol in sequence) {
            if (playbackJob?.isActive != true) return totalFrames

            totalFrames += when (symbol) {
                '.' -> writeTone(track, 1, settings) + writeSilence(track, 1, settings)
                '-' -> writeTone(track, settings.dashMultiplier.toInt(), settings) +
                        writeSilence(track, 1, settings)
                ' ' -> writeSilence(
                    track,
                    (settings.interCharGapMultiplier - 1).toInt().coerceAtLeast(0),
                    settings,
                )
                '/' -> writeSilence(
                    track,
                    (settings.wordGapMultiplier - 1).toInt().coerceAtLeast(0),
                    settings,
                )
                else -> 0
            }
        }
        return totalFrames
    }

    private fun writeTone(track: AudioTrack, units: Int, settings: MorseSettings): Int {
        val data = generator.generateTone(units, settings)
        track.write(data, 0, data.size)
        return data.size
    }

    private fun writeSilence(track: AudioTrack, units: Int, settings: MorseSettings): Int {
        val data = generator.generateSilence(units, settings)
        if (data.isEmpty()) return 0
        track.write(data, 0, data.size)
        return data.size
    }

    private fun createStreamingAudioTrack(unitSamples: Int): AudioTrack? {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(MorseCodeGenerator.SAMPLE_RATE_HZ)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        // Buffer kept small (~4 units) so cancellation drops at most a few
        // hundred ms of audio when the user answers the call.
        val minBuffer = AudioTrack.getMinBufferSize(
            MorseCodeGenerator.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = maxOf(minBuffer, unitSamples * 4 * 2) // *2 for 16-bit bytes

        return try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(tag, "Streaming AudioTrack initialization failed", e)
            null
        }
    }

    // --- Drain & release helpers -----------------------------------------

    /**
     * Polls [AudioTrack.getPlaybackHeadPosition] until [totalFrames] have been
     * played, the coroutine is cancelled, or a safety timeout elapses.
     */
    private suspend fun awaitPlaybackDrained(track: AudioTrack, totalFrames: Int) {
        val expectedMs = (totalFrames * 1000L) / MorseCodeGenerator.SAMPLE_RATE_HZ
        val timeoutMs = expectedMs * 2 + 250
        val startedAt = System.currentTimeMillis()

        while (currentCoroutineContext()[Job]?.isActive == true) {
            val head = try {
                track.playbackHeadPosition
            } catch (e: IllegalStateException) {
                Log.w(tag, "playbackHeadPosition unavailable", e)
                return
            }
            if (head >= totalFrames) return
            if (System.currentTimeMillis() - startedAt > timeoutMs) {
                Log.w(tag, "Drain timeout: head=$head expected=$totalFrames")
                return
            }
            delay(20)
        }
    }

    /**
     * Forcefully tears down the current [AudioTrack]. Used on cancellation,
     * `stop()`, and as the outer coroutine cleanup. Safe to call multiple times.
     */
    private fun forceReleaseHardwareResources() {
        val track = synchronized(trackLock) {
            val t = audioTrack
            audioTrack = null
            t
        } ?: return

        try {
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                }
                track.flush()
            }
            track.release()
        } catch (e: Exception) {
            Log.w(tag, "Error during hardware release", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerTelephonyObserver() {
        if (telephonyCallback != null) return

        telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                if (state == TelephonyManager.CALL_STATE_OFFHOOK || state == TelephonyManager.CALL_STATE_IDLE) {
                    stop()
                }
            }
        }.also {
            try {
                telephonyManager?.registerTelephonyCallback(context.mainExecutor, it)
            } catch (e: Exception) {
                Log.e(tag, "Telephony registration failed", e)
                telephonyCallback = null
            }
        }
    }

    private fun unregisterTelephonyObserver() {
        telephonyCallback?.let {
            try {
                telephonyManager?.unregisterTelephonyCallback(it)
            } catch (e: Exception) {
                Log.w(tag, "Telephony unregistration failed", e)
            }
        }
        telephonyCallback = null
    }
}
