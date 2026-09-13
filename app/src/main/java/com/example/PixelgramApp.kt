package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.MediaRepository
import com.example.data.PixelgramDatabase
import com.example.data.SettingsManager
import com.example.service.ScreenRecordService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PixelgramApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: PixelgramDatabase
        private set

    lateinit var mediaRepository: MediaRepository
        private set

    lateinit var settingsManager: SettingsManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = PixelgramDatabase.getInstance(this)
        mediaRepository = MediaRepository(this, database.mediaRecordDao())
        settingsManager = SettingsManager(this)

        createNotificationChannels()

        // Sync repository with local files in background
        CoroutineScope(Dispatchers.IO).launch {
            mediaRepository.syncWithStorage()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val recordingChannel = NotificationChannel(
                ScreenRecordService.CHANNEL_RECORDING_ID,
                "Pixelgram Screen Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live duration and controls for active screen recording"
                setShowBadge(true)
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(recordingChannel)

            val savedChannel = NotificationChannel(
                ScreenRecordService.CHANNEL_SAVED_ID,
                "Pixelgram Saved Recordings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notification when screen recording is finished and saved"
                setShowBadge(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(savedChannel)
        }
    }
}
