package com.uschwar.morseringer.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.uschwar.morseringer.domain.model.MorseSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATA_STORE_NAME = "morse_settings"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)

/**
 * Persists and retrieves user-defined audio settings using Jetpack DataStore.
 * 
 * This class provides a reactive stream of [MorseSettings] and allows 
 * safe updates for words-per-minute and tone frequency.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val WPM = intPreferencesKey("wpm")
        val FREQUENCY_HZ = intPreferencesKey("frequency_hz")
    }

    val settingsFlow: Flow<MorseSettings> = context.dataStore.data.map { prefs ->
        MorseSettings(
            wpm = prefs[Keys.WPM] ?: MorseSettings.DEFAULT_WPM,
            frequencyHz = prefs[Keys.FREQUENCY_HZ] ?: MorseSettings.DEFAULT_FREQUENCY_HZ,
        )
    }

    suspend fun saveWpm(wpm: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WPM] = wpm.coerceIn(MorseSettings.MIN_WPM, MorseSettings.MAX_WPM)
        }
    }

    suspend fun saveFrequency(frequencyHz: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FREQUENCY_HZ] =
                frequencyHz.coerceIn(MorseSettings.MIN_FREQUENCY_HZ, MorseSettings.MAX_FREQUENCY_HZ)
        }
    }
}
