package com.uschwar.morseringer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.uschwar.morseringer.MorseRingerApp
import com.uschwar.morseringer.R

/**
 * A foreground service that manages Morse code audio playback for incoming calls.
 * 
 * This service ensures the process remains active while the phone is ringing, 
 * preventing the Android system from suspending or killing the app during 
 * background playback.
 */
class MorseForegroundService : Service() {

    private val notificationId = 1001
    private val channelId = "morse_ringer_playback_channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification()
        
        ServiceCompat.startForeground(
            this,
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        val phoneNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER)
        
        if (phoneNumber == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val container = (application as MorseRingerApp).container
        container.morseCodeAudioPlayer.playMorse(phoneNumber)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        val container = (application as MorseRingerApp).container
        container.morseCodeAudioPlayer.stopPlaybackOnly()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
    }

    private fun createNotificationChannel() {
        val name = getString(R.string.notification_channel_name)
        val descriptionText = getString(R.string.notification_channel_description)
        // High importance is required for USAGE_NOTIFICATION_RINGTONE to be 
        // properly enqueued by the system on some Android 14 builds.
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
            setSound(null, null)
            enableVibration(false)
        }
        val notificationManager: NotificationManager =
            getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_morse_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_playing))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
