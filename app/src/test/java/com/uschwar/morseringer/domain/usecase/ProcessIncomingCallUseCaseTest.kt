package com.uschwar.morseringer.domain.usecase

import com.uschwar.morseringer.domain.repo.ContactRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProcessIncomingCallUseCaseTest {

    private val contactRepository: ContactRepository = mockk()
    private val textToMorseUseCase: TextToMorseUseCase = mockk()
    private val useCase = ProcessIncomingCallUseCase(contactRepository, textToMorseUseCase)

    @Test
    fun `invoke returns encoded name when contact is found`() {
        // Given
        val phoneNumber = "1234567890"
        val name = "Alice"
        val expectedMorse = ".- .-.. .. -.-. ."

        every { contactRepository.getContactName(phoneNumber) } returns name
        every { textToMorseUseCase(name) } returns expectedMorse

        // When
        val result = useCase(phoneNumber)

        // Then
        assertEquals(expectedMorse, result)
        verify { contactRepository.getContactName(phoneNumber) }
        verify { textToMorseUseCase(name) }
    }

    @Test
    fun `invoke returns encoded Unknown when contact is not found`() {
        // Given
        val phoneNumber = "9999999999"
        val expectedMorse = "..- -. -.- -. --- .-- -." // Unknown

        every { contactRepository.getContactName(phoneNumber) } returns null
        every { textToMorseUseCase("Unknown") } returns expectedMorse

        // When
        val result = useCase(phoneNumber)

        // Then
        assertEquals(expectedMorse, result)
        verify { contactRepository.getContactName(phoneNumber) }
        verify { textToMorseUseCase("Unknown") }
    }
}
