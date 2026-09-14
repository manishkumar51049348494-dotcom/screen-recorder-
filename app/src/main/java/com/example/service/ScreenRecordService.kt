package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.PixelgramApp
import com.example.R
import com.example.data.MediaRepository
import com.example.model.AudioSourceMode
import com.example.model.MediaItem
import com.example.model.RecordingState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecordService : Service() {

    companion object {
        const val CHANNEL_RECORDING_ID = "pixelgram_screen_recording_channel"
        const val CHANNEL_ID = CHANNEL_RECORDING_ID
        const val CHANNEL_SAVED_ID = "pixelgram_recording_saved_channel"
        const val NOTIFICATION_ID = 9001
        const val NOTIFICATION_SAVED_ID = 9002

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.service.ACTION_RESUME"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"
        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_DPI = "extra_dpi"
        const val EXTRA_FPS = "extra_fps"
        const val EXTRA_BITRATE = "extra_bitrate"
        const val EXTRA_AUDIO_MODE = "extra_audio_mode"
        const val EXTRA_AUTO_STOP_MINUTES = "extra_auto_stop_minutes"

        private val _recordingState = MutableStateFlow(RecordingState())
        val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

        var isServiceRunning = false
            private set

        @Volatile
        var activeInstance: ScreenRecordService? = null
            private set

        fun stopRecording(context: Context) {
            val instance = activeInstance
            if (instance != null) {
                instance.stopRecording()
            } else {
                try {
                    val intent = Intent(context, ScreenRecordService::class.java).apply {
                        action = ACTION_STOP
                    }
                    context.startService(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun pauseRecording(context: Context) {
            val instance = activeInstance
            if (instance != null) {
                instance.pauseRecording()
            } else {
                try {
                    val intent = Intent(context, ScreenRecordService::class.java).apply {
                        action = ACTION_PAUSE
                    }
                    context.startService(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun resumeRecording(context: Context) {
            val instance = activeInstance
            if (instance != null) {
                instance.resumeRecording()
            } else {
                try {
                    val intent = Intent(context, ScreenRecordService::class.java).apply {
                        action = ACTION_RESUME
                    }
                    context.startService(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null

    private var currentOutputFile: File? = null
    private var startTimeMs: Long = 0L
    private var pausedDurationMs: Long = 0L
    private var pauseStartTimeMs: Long = 0L

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null

    private var width = 1080
    private var height = 1920
    private var dpi = 420
    private var fps = 30
    private var bitrate = 8_000_000
    private var audioMode = AudioSourceMode.MIC
    private var autoStopMinutes = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        isServiceRunning = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                width = intent.getIntExtra(EXTRA_WIDTH, 1080)
                height = intent.getIntExtra(EXTRA_HEIGHT, 1920)
                dpi = intent.getIntExtra(EXTRA_DPI, 420)
                fps = intent.getIntExtra(EXTRA_FPS, 30)
                bitrate = intent.getIntExtra(EXTRA_BITRATE, 8_000_000)
                val audioModeStr = intent.getStringExtra(EXTRA_AUDIO_MODE)
                audioMode = runCatching { AudioSourceMode.valueOf(audioModeStr ?: "") }.getOrDefault(AudioSourceMode.MIC)
                autoStopMinutes = intent.getIntExtra(EXTRA_AUTO_STOP_MINUTES, 0)

                if (resultCode != 0 && data != null) {
                    startForegroundServiceWithNotification()
                    initAndStartRecording(resultCode, data)
                } else {
                    stopSelf()
                }
            }
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val initialNotification = buildNotification("Starting recording...", isPaused = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (audioMode != AudioSourceMode.NONE) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
            }
            startForeground(NOTIFICATION_ID, initialNotification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
    }

    private fun initAndStartRecording(resultCode: Int, data: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    stopRecording()
                }
            }, null)

            // Setup output file in phone's public Movies directory (or fallback)
            val moviesDir = MediaRepository.getVideosDirectory(this)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            currentOutputFile = File(moviesDir, "Pixelgram_$timestamp.mp4")

            // Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                if (audioMode == AudioSourceMode.MIC || audioMode == AudioSourceMode.INTERNAL_AND_MIC) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(currentOutputFile?.absolutePath)
                setVideoSize(width, height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                if (audioMode == AudioSourceMode.MIC || audioMode == AudioSourceMode.INTERNAL_AND_MIC) {
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(192_000)
                    setAudioSamplingRate(44100)
                }
                setVideoEncodingBitRate(bitrate)
                setVideoFrameRate(fps)

                prepare()
            }

            // Create VirtualDisplay
            val surface = mediaRecorder?.surface
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "Pixelgram_VirtualDisplay",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )

            mediaRecorder?.start()
            startTimeMs = System.currentTimeMillis()
            pausedDurationMs = 0L

            _recordingState.value = RecordingState(
                isRecording = true,
                isPaused = false,
                durationMs = 0L,
                outputPath = currentOutputFile?.absolutePath,
                resolutionLabel = "${width}x${height}",
                fps = fps,
                audioMode = audioMode
            )

            startTimer()

        } catch (e: Exception) {
            e.printStackTrace()
            _recordingState.value = RecordingState(
                isRecording = false,
                isPaused = false,
                error = "Failed to start recording: ${e.message}"
            )
            stopSelf()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                if (!_recordingState.value.isPaused) {
                    val currentDuration = System.currentTimeMillis() - startTimeMs - pausedDurationMs
                    _recordingState.value = _recordingState.value.copy(durationMs = currentDuration)
                    updateNotification(formatDuration(currentDuration), isPaused = false)

                    if (autoStopMinutes > 0 && currentDuration >= autoStopMinutes * 60 * 1000L) {
                        stopRecording()
                        break
                    }
                }
            }
        }
    }

    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && _recordingState.value.isRecording && !_recordingState.value.isPaused) {
            try {
                mediaRecorder?.pause()
                pauseStartTimeMs = System.currentTimeMillis()
                _recordingState.value = _recordingState.value.copy(isPaused = true)
                updateNotification("Paused - ${formatDuration(_recordingState.value.durationMs)}", isPaused = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && _recordingState.value.isRecording && _recordingState.value.isPaused) {
            try {
                mediaRecorder?.resume()
                pausedDurationMs += (System.currentTimeMillis() - pauseStartTimeMs)
                _recordingState.value = _recordingState.value.copy(isPaused = false)
                updateNotification(formatDuration(_recordingState.value.durationMs), isPaused = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopRecording() {
        if (!_recordingState.value.isRecording && currentOutputFile == null) {
            return
        }

        timerJob?.cancel()
        timerJob = null

        val finalDuration = if (startTimeMs > 0) {
            val elapsed = System.currentTimeMillis() - startTimeMs - pausedDurationMs
            if (elapsed > 0) elapsed else _recordingState.value.durationMs
        } else {
            _recordingState.value.durationMs
        }

        val outputFile = currentOutputFile
        val savedWidth = width
        val savedHeight = height
        val savedFps = fps
        val savedAudioMode = audioMode

        // 1. Gracefully stop feeding frames from virtual display to avoid buffer overflow during stop
        try {
            virtualDisplay?.surface = null
            virtualDisplay?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        virtualDisplay = null

        // 2. Pause media recorder if currently active to flush any in-flight hardware encoder frames
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { mediaRecorder?.pause() }
            }
            // Allow a short delay for hardware encoder to flush final frames and finalize MP4 header
            try {
                Thread.sleep(150)
            } catch (_: Exception) {}

            mediaRecorder?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                mediaRecorder?.reset()
                mediaRecorder?.release()
            } catch (_: Exception) {}
            mediaRecorder = null
        }

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaProjection = null

        // 3. Save into repository using the Application Scope so it NEVER gets cancelled when service terminates!
        if (outputFile != null && outputFile.exists() && outputFile.length() > 0) {
            val app = application as PixelgramApp
            app.applicationScope.launch {
                try {
                    val savedItem = app.mediaRepository.saveRecordedVideo(
                        file = outputFile,
                        resolutionLabel = "${savedWidth}x${savedHeight}",
                        fps = savedFps,
                        audioMode = savedAudioMode.label,
                        durationMs = finalDuration
                    )
                    showSavedNotification(savedItem, finalDuration)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        _recordingState.value = RecordingState(
            isRecording = false,
            isPaused = false,
            durationMs = 0L,
            outputPath = outputFile?.absolutePath
        )

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showSavedNotification(item: MediaItem, durationMs: Long) {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_RECORDINGS, true)
            putExtra(MainActivity.EXTRA_PLAY_VIDEO_ID, item.id)
        }
        val appPendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_SAVED_ID,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appIconBitmap = runCatching {
            BitmapFactory.decodeResource(resources, R.drawable.pixelgram_icon_1789317899078)
        }.getOrNull()

        val durationFormatted = formatDuration(durationMs)

        val notification = NotificationCompat.Builder(this, CHANNEL_SAVED_ID)
            .setContentTitle("Pixelgram — Screen Recording Saved! 🎉")
            .setContentText("Duration: $durationFormatted • Saved in phone files & app library. Tap to play.")
            .setSmallIcon(R.drawable.pixelgram_icon_1789317899078)
            .apply {
                if (appIconBitmap != null) {
                    setLargeIcon(appIconBitmap)
                }
            }
            .setContentIntent(appPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_media_play, "▶️ Play in App", appPendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_SAVED_ID, notification)
    }

    private fun updateNotification(durationText: String, isPaused: Boolean) {
        val notification = buildNotification(durationText, isPaused)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(contentText: String, isPaused: Boolean): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            this, 0, appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this, 2, pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeActionText = if (isPaused) "▶️ Resume" else "⏸️ Pause"
        val pauseResumeIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause

        val appIconBitmap = runCatching {
            BitmapFactory.decodeResource(resources, R.drawable.pixelgram_icon_1789317899078)
        }.getOrNull()

        val statusText = if (isPaused) "⏸️ Paused • $contentText" else "🔴 Recording • $contentText"

        val builder = NotificationCompat.Builder(this, CHANNEL_RECORDING_ID)
            .setContentTitle("Pixelgram Screen Recorder")
            .setContentText(statusText)
            .setSubText("Pixelgram")
            .setSmallIcon(R.drawable.pixelgram_icon_1789317899078)
            .apply {
                if (appIconBitmap != null) {
                    setLargeIcon(appIconBitmap)
                }
            }
            .setContentIntent(appPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "⏹️ End Recording", stopPendingIntent)
            .addAction(pauseResumeIcon, pauseResumeActionText, pauseResumePendingIntent)

        if (!isPaused && startTimeMs > 0) {
            builder.setUsesChronometer(true)
            builder.setWhen(startTimeMs + pausedDurationMs)
            builder.setShowWhen(true)
        } else {
            builder.setUsesChronometer(false)
            builder.setShowWhen(true)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val recordingChannel = NotificationChannel(
                CHANNEL_RECORDING_ID,
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
                CHANNEL_SAVED_ID,
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

    private fun formatDuration(durationMs: Long): String {
        val totalSec = durationMs / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance == this) {
            activeInstance = null
        }
        isServiceRunning = false
        serviceScope.cancel()
    }
}
