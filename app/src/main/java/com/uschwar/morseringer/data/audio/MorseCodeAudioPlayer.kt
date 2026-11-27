package com.uschwar.morseringer.data.audio

import android.annotation.SuppressLint
import android.app.ActivityManager
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

    @Volatile
    private var playbackJob: Job? = null
    private val playerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var telephonyCallback: TelephonyCallback? = null
    private var audioTrack: AudioTrack? = null
    private val trackLock = Any()

    /** Soft cap (~30 s of audio) above which preview falls back to streaming. */
    private val maxStaticPreviewSamples = MorseCodeGenerator.SAMPLE_RATE_HZ * 30

    @SuppressLint("MissingPermission")
    fun playMorse(phoneNumber: String) {
        Log.i(tag, "playMorse: start (importance=${processImportance()})")
        stopPlaybackOnly("new incoming call")
        registerTelephonyObserver()

        // LAZY start: `playbackJob` must be assigned before the body runs,
        // because the streaming loop checks `playbackJob?.isActive`.
        val job = playerScope.launch(start = CoroutineStart.LAZY) {
            val thisJob = coroutineContext[Job]
            // Safety net: never ring longer than MAX_RINGING_MS, e.g. when the
            // telephony observer could not be registered or missed a state.
            launch {
                delay(MAX_RINGING_MS)
                Log.w(tag, "Safety timeout (${MAX_RINGING_MS} ms) reached - stopping playback")
                thisJob?.cancel()
            }

            val focusRequest = requestAudioFocus()

            try {
                val container = (context.applicationContext as MorseRingerApp).container
                val morseSequence = container.processIncomingCallUseCase(phoneNumber)
                val settings = container.settingsRepository.settingsFlow.first()

                Log.d(tag, "playMorse: ${morseSequence.length} symbols, ${settings.wpm} WPM, ${settings.frequencyHz} Hz")

                playContinuousLoop(morseSequence, settings)
            } catch (_: CancellationException) {
                // Playback stopped intentionally or by the safety timeout
                Log.i(tag, "playMorse: cancelled")
            } catch (e: Exception) {
                Log.e(tag, "Unexpected error during playback", e)
            } finally {
                forceReleaseHardwareResources()
                abandonAudioFocus(focusRequest)
                // Still the current job => playback ended on its own (timeout,
                // error, no AudioTrack) rather than via stop()/a newer request.
                // Tear down the foreground service so its notification goes away.
                if (playbackJob === thisJob) stop("playback ended on its own")
            }
        }
        playbackJob = job
        job.start()
    }

    private fun requestAudioFocus(): AudioFocusRequest? {
        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { change ->
                Log.i(tag, "Audio focus changed: ${focusChangeName(change)} (importance=${processImportance()})")
            }
            .build()

        val result = audioManager.requestAudioFocus(focusRequest)
        Log.i(tag, "Audio focus request result=$result (1=granted, 0=failed, 2=delayed)")
        return if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focusRequest else null
    }

    private fun abandonAudioFocus(focusRequest: AudioFocusRequest?) {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    fun playPreview(morseSequence: String) {
        stopPlaybackOnly("preview")

        val job = playerScope.launch(start = CoroutineStart.LAZY) {
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
        playbackJob = job
        job.start()
    }

    /** [reason] is logged only; it says who stopped playback. */
    fun stop(reason: String = "unspecified") {
        stopPlaybackOnly(reason)

        // Also stop the foreground service if it's running
        context.stopService(Intent(context, MorseForegroundService::class.java))
    }

    /**
     * Clears active playback and releases hardware resources WITHOUT 
     * stopping the Android Foreground Service.
     */
    fun stopPlaybackOnly(reason: String = "unspecified") {
        if (playbackJob != null) Log.i(tag, "Stopping playback: $reason")
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

        val startedAt = System.currentTimeMillis()
        var iteration = 0
        while (playbackJob?.isActive == true) {
            iteration++
            Log.d(
                tag,
                "Loop #$iteration at ${System.currentTimeMillis() - startedAt} ms: " +
                    "playState=${track.playState} (3=playing), " +
                    "head=${track.playbackHeadPosition}, " +
                    "underruns=${track.underrunCount}, " +
                    "importance=${processImportance()}",
            )
            streamMorseToHardware(sequence, settings, track)
            if (playbackJob?.isActive != true) break
            // Active silence keeps the AudioTrack in PLAYING state.
            checkedWrite(track, gapSilence)
        }
        Log.i(tag, "Loop finished after $iteration iteration(s), ${System.currentTimeMillis() - startedAt} ms")
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
        checkedWrite(track, data)
        return data.size
    }

    private fun writeSilence(track: AudioTrack, units: Int, settings: MorseSettings): Int {
        val data = generator.generateSilence(units, settings)
        if (data.isEmpty()) return 0
        checkedWrite(track, data)
        return data.size
    }

    /**
     * Writes [data] and fails loudly on an error code. Without this, a dead
     * track (e.g. ERROR_DEAD_OBJECT = -6) returns instantly and the loop spins
     * silently with no audio and no log.
     */
    private fun checkedWrite(track: AudioTrack, data: ShortArray) {
        val result = track.write(data, 0, data.size)
        if (result < 0) {
            throw IllegalStateException("AudioTrack.write failed: $result (playState=${track.playState})")
        }
        if (result < data.size) {
            Log.w(tag, "AudioTrack.write short: $result of ${data.size} (playState=${track.playState})")
        }
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
            // Call screening runs before the phone starts ringing, and the
            // callback reports the current state right after registration.
            // That initial state can still be IDLE, so IDLE only counts as
            // "call ended" once RINGING has been seen.
            private var hasRung = false

            override fun onCallStateChanged(state: Int) {
                Log.i(tag, "Call state: ${callStateName(state)} (hasRung=$hasRung)")
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> hasRung = true
                    TelephonyManager.CALL_STATE_OFFHOOK -> stop("call answered (OFFHOOK)")
                    TelephonyManager.CALL_STATE_IDLE -> if (hasRung) stop("call ended (IDLE after RINGING)")
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

    companion object {
        /** Hard upper bound for incoming-call playback. */
        const val MAX_RINGING_MS = 60_000L

        /** e.g. 100 = foreground, 125 = foreground service, 400 = cached. */
        private fun processImportance(): Int =
            ActivityManager.RunningAppProcessInfo()
                .also { ActivityManager.getMyMemoryState(it) }
                .importance

        private fun callStateName(state: Int) = when (state) {
            TelephonyManager.CALL_STATE_IDLE -> "IDLE"
            TelephonyManager.CALL_STATE_RINGING -> "RINGING"
            TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
            else -> "UNKNOWN($state)"
        }

        private fun focusChangeName(change: Int) = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
            AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
            else -> "UNKNOWN($change)"
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
