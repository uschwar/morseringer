package com.uschwar.morseringer.data.repo

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.uschwar.morseringer.domain.repo.ContactRepository

private const val TAG = "MorseRingerRepo"

/**
 * Accesses the Android Contacts Provider to resolve phone numbers to names.
 * 
 * This repository handles the low-level query logic and provides a safe 
 * way to retrieve display names while handling permission or query failures.
 */
class ContactRepositoryImpl(private val context: Context) : ContactRepository {

    override fun getContactName(phoneNumber: String): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber),
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "getContactName: READ_CONTACTS not granted", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "getContactName: query failed", e)
            null
        }
    }
}
