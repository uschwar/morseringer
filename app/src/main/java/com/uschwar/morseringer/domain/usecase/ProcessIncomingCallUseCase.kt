package com.uschwar.morseringer.domain.usecase

import com.uschwar.morseringer.domain.repo.ContactRepository

/**
 * Coordinates the workflow for an incoming call.
 * 
 * It identifies the caller via the [ContactRepository] and translates the 
 * resolved name into a Morse code sequence using the [TextToMorseUseCase].
 */
class ProcessIncomingCallUseCase(
    private val contactRepository: ContactRepository,
    private val textToMorseUseCase: TextToMorseUseCase
) {
    operator fun invoke(phoneNumber: String): String {
        val name = contactRepository.getContactName(phoneNumber) ?: "Unknown"
        return textToMorseUseCase(name)
    }
}
