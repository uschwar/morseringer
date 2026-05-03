package com.uschwar.morseringer

import android.app.Application
import com.uschwar.morseringer.data.audio.MorseCodeAudioPlayer
import com.uschwar.morseringer.data.repo.ContactRepositoryImpl
import com.uschwar.morseringer.data.repo.SettingsRepository
import com.uschwar.morseringer.domain.repo.ContactRepository
import com.uschwar.morseringer.domain.usecase.ProcessIncomingCallUseCase
import com.uschwar.morseringer.domain.usecase.TextToMorseUseCase

/**
 * The main application entry point for MorseRinger.
 * 
 * It initializes the global [AppContainer] during [onCreate] to provide 
 * shared dependencies throughout the application lifecycle.
 */
class MorseRingerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * A centralized dependency container for the application.
 * 
 * Provides lazy-initialized singletons for repositories, use cases, and 
 * infrastructure services, ensuring a consistent state across different 
 * components like Activities and Services.
 */
class AppContainer(private val context: Application) {

    val settingsRepository by lazy { SettingsRepository(context) }

    val contactRepository: ContactRepository by lazy { ContactRepositoryImpl(context) }

    val morseCodeAudioPlayer by lazy { MorseCodeAudioPlayer(context) }

    val textToMorseUseCase by lazy { TextToMorseUseCase() }

    val processIncomingCallUseCase by lazy {
        ProcessIncomingCallUseCase(contactRepository, textToMorseUseCase)
    }
}
