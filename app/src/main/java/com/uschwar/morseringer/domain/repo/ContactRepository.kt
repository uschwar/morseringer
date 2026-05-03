package com.uschwar.morseringer.domain.repo

/**
 * Port for retrieving contact information based on phone numbers.
 */
interface ContactRepository {
    fun getContactName(phoneNumber: String): String?
}
