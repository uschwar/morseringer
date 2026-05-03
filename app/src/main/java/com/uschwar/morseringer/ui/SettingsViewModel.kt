package com.uschwar.morseringer.ui

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uschwar.morseringer.MorseRingerApp
import com.uschwar.morseringer.domain.MorseEncoder
import com.uschwar.morseringer.domain.model.MorseSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val wpm: Int = MorseSettings.DEFAULT_WPM,
    val frequencyHz: Int = MorseSettings.DEFAULT_FREQUENCY_HZ,
    val hasContactsPermission: Boolean = false,
    val hasPhoneStatePermission: Boolean = false,
    val isCallScreeningRoleHeld: Boolean = false,
    val previewText: String = "73",
    val previewMorse: String = "",
    val isPlaying: Boolean = false,
    val isSilentMode: Boolean = false,
)

/**
 * Bridges the UI state with the underlying settings and audio logic.
 * 
 * This ViewModel manages the user's Morse code configuration (WPM, Pitch),
 * handles permission and role status tracking, and coordinates ringer mode 
 * monitoring to alert the user if the phone is muted.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as MorseRingerApp).container
    private val settingsRepository = container.settingsRepository
    private val morseCodeAudioPlayer = container.morseCodeAudioPlayer

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val ringerModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                refreshPermissions()
            }
        }
    }

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update { it.copy(wpm = settings.wpm, frequencyHz = settings.frequencyHz) }
            }
        }
        val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        application.registerReceiver(ringerModeReceiver, filter)
        refreshPermissions()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(ringerModeReceiver)
        } catch (_: Exception) {
            // Already unregistered or never registered
        }
    }

    fun updateWpm(wpm: Int) {
        viewModelScope.launch { settingsRepository.saveWpm(wpm) }
    }

    fun updateFrequency(frequencyHz: Int) {
        viewModelScope.launch { settingsRepository.saveFrequency(frequencyHz) }
    }

    fun confirmChange(){
        playPreview("r")
    }

    fun updatePreviewText(text: String) {
        _uiState.update {
            it.copy(
                previewText = text,
                previewMorse = if (text.isNotBlank()) MorseEncoder.encode(text) else "",
            )
        }
    }

    fun playPreview(text : String = _uiState.value.previewText) {
        val state = _uiState.value
        if (text.isBlank() || state.isPlaying) return

        _uiState.update { it.copy(isPlaying = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val morse = MorseEncoder.encode(text)
                morseCodeAudioPlayer.playPreview(morse)
            } finally {
                _uiState.update { it.copy(isPlaying = false) }
            }
        }
    }

    fun stopPreview() {
        morseCodeAudioPlayer.stop()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()

        val hasContacts = isGranted(context, Manifest.permission.READ_CONTACTS)
        val hasPhoneState = isGranted(context, Manifest.permission.READ_PHONE_STATE)

        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        val isCallScreening = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val isSilent = audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL

        _uiState.update {
            it.copy(
                hasContactsPermission = hasContacts,
                hasPhoneStatePermission = hasPhoneState,
                isCallScreeningRoleHeld = isCallScreening,
                isSilentMode = isSilent,
            )
        }
    }

    fun openSoundSettings() {
        val context = getApplication<Application>()
        val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
