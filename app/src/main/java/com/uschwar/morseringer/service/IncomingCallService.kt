package com.uschwar.morseringer.service

import android.content.Intent
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/**
 * Android service that intercepts incoming calls.
 * 
 * This service allows the system to continue with the call while triggering 
 * the Morse code audio playback in parallel.
 */
class IncomingCallService : CallScreeningService() {

    private val tag = "IncomingCallService"

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return

        Log.d(tag, "Incoming call detected - preparing response and audio")

        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: ""
        triggerMorsePlayback(phoneNumber)

        respondToCall(callDetails, createDefaultResponse())
    }

    private fun triggerMorsePlayback(phoneNumber: String) {
        val intent = Intent(this, MorseForegroundService::class.java).apply {
            putExtra(MorseForegroundService.EXTRA_PHONE_NUMBER, phoneNumber)
        }
        startForegroundService(intent)
    }

    private fun createDefaultResponse(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
}
