package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.model.*
import com.example.recording.ScreenshotHelper
import com.example.service.FloatingControlService
import com.example.service.ScreenRecordService
import com.example.ui.components.AnnotationCanvas
import com.example.ui.components.FacecamOverlay
import com.example.ui.components.FloatingControlsWidget
import com.example.ui.components.TouchIndicatorLayer
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RecordingsScreen
import com.example.ui.screens.ScreenshotsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.PixelgramTheme
import kotlinx.coroutines.launch

enum class ScreenTab(val title: String) {
    HOME("Home"),
    RECORDINGS("Recordings"),
    SCREENSHOTS("Screenshots"),
    SETTINGS("Settings")
}

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_RECORDINGS = "extra_open_recordings"
        const val EXTRA_PLAY_VIDEO_ID = "extra_play_video_id"
    }

    private lateinit var app: PixelgramApp
    private val pendingTabState = mutableStateOf<ScreenTab?>(null)
    private val pendingVideoIdState = mutableStateOf<Long?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_RECORDINGS, false) == true) {
            pendingTabState.value = ScreenTab.RECORDINGS
            val videoId = intent.getLongExtra(EXTRA_PLAY_VIDEO_ID, -1L)
            if (videoId != -1L) {
                pendingVideoIdState.value = videoId
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as PixelgramApp
        handleNotificationIntent(intent)
        enableEdgeToEdge()

        setContent {
            val settingsManager = app.settingsManager
            val mediaRepository = app.mediaRepository

            val themeMode by settingsManager.themeMode.collectAsStateWithLifecycle()
            val recordingConfig by settingsManager.recordingConfig.collectAsStateWithLifecycle()
            val audioConfig by settingsManager.audioConfig.collectAsStateWithLifecycle()
            val facecamConfig by settingsManager.facecamConfig.collectAsStateWithLifecycle()
            val controlConfig by settingsManager.controlConfig.collectAsStateWithLifecycle()

            val recordingState by ScreenRecordService.recordingState.collectAsStateWithLifecycle()
            val videos by mediaRepository.videosFlow.collectAsStateWithLifecycle(initialValue = emptyList())
            val screenshots by mediaRepository.screenshotsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

            var currentTab by remember { mutableStateOf(ScreenTab.HOME) }
            var autoPlayVideoId by remember { mutableStateOf<Long?>(null) }
            var facecamActive by remember { mutableStateOf(facecamConfig.enabled) }
            var annotationActive by remember { mutableStateOf(false) }

            // Handle incoming notifications / intents to jump to recordings
            LaunchedEffect(pendingTabState.value, pendingVideoIdState.value) {
                pendingTabState.value?.let { tab ->
                    currentTab = tab
                    pendingTabState.value = null
                }
                pendingVideoIdState.value?.let { vid ->
                    autoPlayVideoId = vid
                    pendingVideoIdState.value = null
                }
            }

            // Sync facecamActive with settings
            LaunchedEffect(facecamConfig.enabled) {
                facecamActive = facecamConfig.enabled
            }

            // Storage space
            val (freeBytes, totalBytes) = remember { mediaRepository.getStorageInfo() }

            // Permission Launchers
            val allPermissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                val audioGranted = results[Manifest.permission.RECORD_AUDIO] == true
                if (!audioGranted) {
                    settingsManager.updateAudioConfig(audioConfig.copy(audioSource = AudioSourceMode.NONE))
                }
                val cameraGranted = results[Manifest.permission.CAMERA] == true
                if (!cameraGranted) {
                    settingsManager.updateFacecamConfig(facecamConfig.copy(enabled = false))
                }
            }

            val micPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (!isGranted) {
                    Toast.makeText(this, "Microphone permission denied. Recording without mic.", Toast.LENGTH_SHORT).show()
                    settingsManager.updateAudioConfig(audioConfig.copy(audioSource = AudioSourceMode.NONE))
                }
            }

            val cameraPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (!isGranted) {
                    Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_SHORT).show()
                    settingsManager.updateFacecamConfig(facecamConfig.copy(enabled = false))
                }
            }

            // Ask for all required permissions when app is opened
            LaunchedEffect(Unit) {
                val neededPermissions = buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    add(Manifest.permission.RECORD_AUDIO)
                    add(Manifest.permission.CAMERA)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }.filter { perm ->
                    ContextCompat.checkSelfPermission(this@MainActivity, perm) != PackageManager.PERMISSION_GRANTED
                }

                if (neededPermissions.isNotEmpty()) {
                    allPermissionsLauncher.launch(neededPermissions.toTypedArray())
                }
            }

            PixelgramTheme(themeMode = themeMode) {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentTab == ScreenTab.HOME,
                                onClick = { currentTab = ScreenTab.HOME },
                                icon = {
                                    Icon(
                                        if (currentTab == ScreenTab.HOME) Icons.Filled.Videocam else Icons.Outlined.Videocam,
                                        contentDescription = "Home"
                                    )
                                },
                                label = { Text("Home") }
                            )
                            NavigationBarItem(
                                selected = currentTab == ScreenTab.RECORDINGS,
                                onClick = { currentTab = ScreenTab.RECORDINGS },
                                icon = {
                                    Icon(
                                        if (currentTab == ScreenTab.RECORDINGS) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                                        contentDescription = "Recordings"
                                    )
                                },
                                label = { Text("Recordings") }
                            )
                            NavigationBarItem(
                                selected = currentTab == ScreenTab.SCREENSHOTS,
                                onClick = { currentTab = ScreenTab.SCREENSHOTS },
                                icon = {
                                    Icon(
                                        if (currentTab == ScreenTab.SCREENSHOTS) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                                        contentDescription = "Screenshots"
                                    )
                                },
                                label = { Text("Screenshots") }
                            )
                            NavigationBarItem(
                                selected = currentTab == ScreenTab.SETTINGS,
                                onClick = { currentTab = ScreenTab.SETTINGS },
                                icon = {
                                    Icon(
                                        if (currentTab == ScreenTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                        contentDescription = "Settings"
                                    )
                                },
                                label = { Text("Settings") }
                            )
                        }
                    },
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        // Current Screen Content
                        when (currentTab) {
                            ScreenTab.HOME -> {
                                HomeScreen(
                                    recordingState = recordingState,
                                    settingsManager = settingsManager,
                                    onStartRecording = { resultCode, data ->
                                        // Check mic permission if needed
                                        if (audioConfig.audioSource != AudioSourceMode.NONE &&
                                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            return@HomeScreen
                                        }

                                        // Check camera permission if facecam enabled
                                        if (facecamConfig.enabled &&
                                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            return@HomeScreen
                                        }

                                        startScreenRecordingService(resultCode, data, recordingConfig, audioConfig)

                                        if (controlConfig.floatingControls) {
                                            FloatingControlService.start(this@MainActivity)
                                        }
                                    },
                                    onPauseToggle = {
                                        val intent = Intent(this@MainActivity, ScreenRecordService::class.java).apply {
                                            action = if (recordingState.isPaused) ScreenRecordService.ACTION_RESUME else ScreenRecordService.ACTION_PAUSE
                                        }
                                        startService(intent)
                                    },
                                    onStopRecording = {
                                        val intent = Intent(this@MainActivity, ScreenRecordService::class.java).apply {
                                            action = ScreenRecordService.ACTION_STOP
                                        }
                                        startService(intent)
                                        FloatingControlService.stop(this@MainActivity)
                                    },
                                    onTakeScreenshot = {
                                        captureScreenshot()
                                    },
                                    freeStorageBytes = freeBytes,
                                    totalStorageBytes = totalBytes
                                )
                            }
                            ScreenTab.RECORDINGS -> {
                                RecordingsScreen(
                                    recordings = videos,
                                    initialVideoId = autoPlayVideoId,
                                    onDelete = { item ->
                                        lifecycleScope.launch {
                                            mediaRepository.deleteMedia(item)
                                            Toast.makeText(this@MainActivity, "Deleted recording", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onRename = { item, newName ->
                                        lifecycleScope.launch {
                                            mediaRepository.renameMedia(item, newName)
                                            Toast.makeText(this@MainActivity, "Renamed recording", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onTrim = { item, startMs, endMs ->
                                        lifecycleScope.launch {
                                            val trimmed = mediaRepository.trimVideo(item, startMs, endMs)
                                            if (trimmed != null) {
                                                Toast.makeText(this@MainActivity, "Trimmed video saved successfully", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(this@MainActivity, "Failed to trim video", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                            ScreenTab.SCREENSHOTS -> {
                                ScreenshotsScreen(
                                    screenshots = screenshots,
                                    onDelete = { item ->
                                        lifecycleScope.launch {
                                            mediaRepository.deleteMedia(item)
                                            Toast.makeText(this@MainActivity, "Deleted screenshot", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                            ScreenTab.SETTINGS -> {
                                SettingsScreen(
                                    settingsManager = settingsManager,
                                    freeStorageBytes = freeBytes,
                                    totalStorageBytes = totalBytes
                                )
                            }
                        }

                        // Touch Indicator Layer
                        TouchIndicatorLayer(showTouches = recordingConfig.showTouches)

                        // Floating Facecam overlay
                        if (facecamActive) {
                            FacecamOverlay(
                                config = facecamConfig,
                                onClose = { facecamActive = false }
                            )
                        }

                        // Screen Annotation Canvas
                        AnnotationCanvas(
                            isActive = annotationActive,
                            onClose = { annotationActive = false }
                        )

                        // In-App Floating Recording Controls
                        if (recordingState.isRecording) {
                            FloatingControlsWidget(
                                recordingState = recordingState,
                                onPauseToggle = {
                                    val intent = Intent(this@MainActivity, ScreenRecordService::class.java).apply {
                                        action = if (recordingState.isPaused) ScreenRecordService.ACTION_RESUME else ScreenRecordService.ACTION_PAUSE
                                    }
                                    startService(intent)
                                },
                                onStop = {
                                    val intent = Intent(this@MainActivity, ScreenRecordService::class.java).apply {
                                        action = ScreenRecordService.ACTION_STOP
                                    }
                                    startService(intent)
                                    FloatingControlService.stop(this@MainActivity)
                                },
                                onScreenshot = { captureScreenshot() },
                                onFacecamToggle = { facecamActive = !facecamActive },
                                onAnnotationToggle = { annotationActive = !annotationActive },
                                facecamActive = facecamActive,
                                annotationActive = annotationActive
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startScreenRecordingService(
        resultCode: Int,
        data: Intent,
        recordingConfig: RecordingConfig,
        audioConfig: AudioConfig
    ) {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        var width = metrics.widthPixels
        var height = metrics.heightPixels
        val dpi = metrics.densityDpi

        // Align dimensions to even numbers for H264 codec
        if (recordingConfig.resolution != VideoResolution.RES_DEVICE) {
            val targetW = recordingConfig.resolution.width
            val targetH = recordingConfig.resolution.height
            // Maintain aspect ratio or orientation
            if (width < height) {
                // Portrait
                width = minOf(targetH, targetW)
                height = maxOf(targetH, targetW)
            } else {
                // Landscape
                width = maxOf(targetH, targetW)
                height = minOf(targetH, targetW)
            }
        }

        width = if (width % 2 != 0) width - 1 else width
        height = if (height % 2 != 0) height - 1 else height

        val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_START
            putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecordService.EXTRA_DATA, data)
            putExtra(ScreenRecordService.EXTRA_WIDTH, width)
            putExtra(ScreenRecordService.EXTRA_HEIGHT, height)
            putExtra(ScreenRecordService.EXTRA_DPI, dpi)
            putExtra(ScreenRecordService.EXTRA_FPS, recordingConfig.fps.fps)
            putExtra(ScreenRecordService.EXTRA_BITRATE, recordingConfig.bitrate.bps)
            putExtra(ScreenRecordService.EXTRA_AUDIO_MODE, audioConfig.audioSource.name)
            putExtra(ScreenRecordService.EXTRA_AUTO_STOP_MINUTES, recordingConfig.autoStopMinutes)
        }

        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun captureScreenshot() {
        lifecycleScope.launch {
            val result = ScreenshotHelper.captureWindowScreenshot(this@MainActivity, app.mediaRepository)
            result.onSuccess { item ->
                Toast.makeText(this@MainActivity, "Screenshot saved: ${item.title}", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(this@MainActivity, "Failed to capture screenshot: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
