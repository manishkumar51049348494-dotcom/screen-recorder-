package com.example.model

enum class VideoResolution(val label: String, val width: Int, val height: Int) {
    RES_480P("480p SD", 854, 480),
    RES_720P("720p HD", 1280, 720),
    RES_1080P("1080p Full HD", 1920, 1080),
    RES_DEVICE("Device Maximum", -1, -1)
}

enum class VideoFps(val label: String, val fps: Int) {
    FPS_24("24 FPS (Cinematic)", 24),
    FPS_30("30 FPS (Standard)", 30),
    FPS_60("60 FPS (Smooth)", 60)
}

enum class VideoBitrate(val label: String, val bps: Int) {
    LOW("Low (4 Mbps)", 4_000_000),
    MEDIUM("Medium (8 Mbps)", 8_000_000),
    HIGH("High (12 Mbps)", 12_000_000),
    ULTRA("Ultra (16 Mbps)", 16_000_000)
}

enum class VideoOrientation(val label: String) {
    AUTO("Auto (Follow device)"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class AudioSourceMode(val label: String, val description: String) {
    NONE("No Audio", "Silent recording"),
    MIC("Microphone", "Record speech and ambient sounds via mic"),
    INTERNAL("Internal Device Audio", "Android 10+ system playback audio capture"),
    INTERNAL_AND_MIC("Internal + Microphone", "Record device gameplay/media sound plus microphone commentary")
}

enum class AudioQuality(val label: String, val bitrate: Int, val sampleRate: Int) {
    STANDARD("Standard (128 kbps)", 128_000, 44100),
    HIGH("High (256 kbps)", 256_000, 48000),
    STUDIO("Studio (320 kbps)", 320_000, 48000)
}

enum class FacecamShape(val label: String) {
    CIRCLE("Circle"),
    ROUNDED_RECT("Rounded Rectangle")
}

enum class FacecamSize(val label: String, val dpSize: Int) {
    SMALL("Small", 110),
    MEDIUM("Medium", 140),
    LARGE("Large", 180)
}

enum class FacecamPosition(val label: String) {
    TOP_RIGHT("Top Right"),
    TOP_LEFT("Top Left"),
    BOTTOM_RIGHT("Bottom Right"),
    BOTTOM_LEFT("Bottom Left")
}

data class RecordingConfig(
    val resolution: VideoResolution = VideoResolution.RES_1080P,
    val fps: VideoFps = VideoFps.FPS_30,
    val bitrate: VideoBitrate = VideoBitrate.HIGH,
    val orientation: VideoOrientation = VideoOrientation.AUTO,
    val countdownSeconds: Int = 3,
    val autoStopMinutes: Int = 0, // 0 = disabled
    val showTouches: Boolean = false
)

data class AudioConfig(
    val audioSource: AudioSourceMode = AudioSourceMode.MIC,
    val audioQuality: AudioQuality = AudioQuality.HIGH
)

data class FacecamConfig(
    val enabled: Boolean = false,
    val shape: FacecamShape = FacecamShape.CIRCLE,
    val size: FacecamSize = FacecamSize.MEDIUM,
    val position: FacecamPosition = FacecamPosition.TOP_RIGHT,
    val mirrorCamera: Boolean = true
)

data class ControlConfig(
    val floatingControls: Boolean = true,
    val notificationControls: Boolean = true
)

enum class AppThemeMode(val label: String) {
    SYSTEM("System Default"),
    DARK("Dark Theme"),
    LIGHT("Light Theme")
}

data class RecordingState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val durationMs: Long = 0L,
    val outputPath: String? = null,
    val resolutionLabel: String = "1080p",
    val fps: Int = 30,
    val audioMode: AudioSourceMode = AudioSourceMode.MIC,
    val error: String? = null
)
