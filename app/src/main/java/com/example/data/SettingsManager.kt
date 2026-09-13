package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pixelgram_preferences", Context.MODE_PRIVATE)

    private val _recordingConfig = MutableStateFlow(loadRecordingConfig())
    val recordingConfig: StateFlow<RecordingConfig> = _recordingConfig.asStateFlow()

    private val _audioConfig = MutableStateFlow(loadAudioConfig())
    val audioConfig: StateFlow<AudioConfig> = _audioConfig.asStateFlow()

    private val _facecamConfig = MutableStateFlow(loadFacecamConfig())
    val facecamConfig: StateFlow<FacecamConfig> = _facecamConfig.asStateFlow()

    private val _controlConfig = MutableStateFlow(loadControlConfig())
    val controlConfig: StateFlow<ControlConfig> = _controlConfig.asStateFlow()

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _avatarVideoUri = MutableStateFlow(prefs.getString("header_avatar_video_uri", null))
    val avatarVideoUri: StateFlow<String?> = _avatarVideoUri.asStateFlow()

    fun updateAvatarVideoUri(uri: String?) {
        prefs.edit().putString("header_avatar_video_uri", uri).apply()
        _avatarVideoUri.value = uri
    }

    private val _githubRepo = MutableStateFlow(
        prefs.getString("github_repo_slug", "manishkumar51049348494-dotcom/screen-recorder") ?: "manishkumar51049348494-dotcom/screen-recorder"
    )
    val githubRepo: StateFlow<String> = _githubRepo.asStateFlow()

    fun updateGithubRepo(repo: String) {
        val clean = repo.trim().removePrefix("https://github.com/").removeSuffix("/")
        prefs.edit().putString("github_repo_slug", clean).apply()
        _githubRepo.value = clean
    }

    private fun loadRecordingConfig(): RecordingConfig {
        val resName = prefs.getString("video_resolution", VideoResolution.RES_1080P.name)
        val fpsName = prefs.getString("video_fps", VideoFps.FPS_30.name)
        val bitName = prefs.getString("video_bitrate", VideoBitrate.HIGH.name)
        val oriName = prefs.getString("video_orientation", VideoOrientation.AUTO.name)
        val countdown = prefs.getInt("countdown_seconds", 3)
        val autoStop = prefs.getInt("auto_stop_minutes", 0)
        val touches = prefs.getBoolean("show_touches", false)

        return RecordingConfig(
            resolution = runCatching { VideoResolution.valueOf(resName ?: "") }.getOrDefault(VideoResolution.RES_1080P),
            fps = runCatching { VideoFps.valueOf(fpsName ?: "") }.getOrDefault(VideoFps.FPS_30),
            bitrate = runCatching { VideoBitrate.valueOf(bitName ?: "") }.getOrDefault(VideoBitrate.HIGH),
            orientation = runCatching { VideoOrientation.valueOf(oriName ?: "") }.getOrDefault(VideoOrientation.AUTO),
            countdownSeconds = countdown,
            autoStopMinutes = autoStop,
            showTouches = touches
        )
    }

    fun updateRecordingConfig(newConfig: RecordingConfig) {
        prefs.edit()
            .putString("video_resolution", newConfig.resolution.name)
            .putString("video_fps", newConfig.fps.name)
            .putString("video_bitrate", newConfig.bitrate.name)
            .putString("video_orientation", newConfig.orientation.name)
            .putInt("countdown_seconds", newConfig.countdownSeconds)
            .putInt("auto_stop_minutes", newConfig.autoStopMinutes)
            .putBoolean("show_touches", newConfig.showTouches)
            .apply()
        _recordingConfig.value = newConfig
    }

    private fun loadAudioConfig(): AudioConfig {
        val srcName = prefs.getString("audio_source", AudioSourceMode.MIC.name)
        val qualName = prefs.getString("audio_quality", AudioQuality.HIGH.name)
        return AudioConfig(
            audioSource = runCatching { AudioSourceMode.valueOf(srcName ?: "") }.getOrDefault(AudioSourceMode.MIC),
            audioQuality = runCatching { AudioQuality.valueOf(qualName ?: "") }.getOrDefault(AudioQuality.HIGH)
        )
    }

    fun updateAudioConfig(newConfig: AudioConfig) {
        prefs.edit()
            .putString("audio_source", newConfig.audioSource.name)
            .putString("audio_quality", newConfig.audioQuality.name)
            .apply()
        _audioConfig.value = newConfig
    }

    private fun loadFacecamConfig(): FacecamConfig {
        val enabled = prefs.getBoolean("facecam_enabled", false)
        val shapeName = prefs.getString("facecam_shape", FacecamShape.CIRCLE.name)
        val sizeName = prefs.getString("facecam_size", FacecamSize.MEDIUM.name)
        val posName = prefs.getString("facecam_position", FacecamPosition.TOP_RIGHT.name)
        val mirror = prefs.getBoolean("facecam_mirror", true)

        return FacecamConfig(
            enabled = enabled,
            shape = runCatching { FacecamShape.valueOf(shapeName ?: "") }.getOrDefault(FacecamShape.CIRCLE),
            size = runCatching { FacecamSize.valueOf(sizeName ?: "") }.getOrDefault(FacecamSize.MEDIUM),
            position = runCatching { FacecamPosition.valueOf(posName ?: "") }.getOrDefault(FacecamPosition.TOP_RIGHT),
            mirrorCamera = mirror
        )
    }

    fun updateFacecamConfig(newConfig: FacecamConfig) {
        prefs.edit()
            .putBoolean("facecam_enabled", newConfig.enabled)
            .putString("facecam_shape", newConfig.shape.name)
            .putString("facecam_size", newConfig.size.name)
            .putString("facecam_position", newConfig.position.name)
            .putBoolean("facecam_mirror", newConfig.mirrorCamera)
            .apply()
        _facecamConfig.value = newConfig
    }

    private fun loadControlConfig(): ControlConfig {
        return ControlConfig(
            floatingControls = prefs.getBoolean("floating_controls", true),
            notificationControls = prefs.getBoolean("notification_controls", true)
        )
    }

    fun updateControlConfig(newConfig: ControlConfig) {
        prefs.edit()
            .putBoolean("floating_controls", newConfig.floatingControls)
            .putBoolean("notification_controls", newConfig.notificationControls)
            .apply()
        _controlConfig.value = newConfig
    }

    private fun loadThemeMode(): AppThemeMode {
        val themeName = prefs.getString("app_theme", AppThemeMode.SYSTEM.name)
        return runCatching { AppThemeMode.valueOf(themeName ?: "") }.getOrDefault(AppThemeMode.SYSTEM)
    }

    fun updateThemeMode(newMode: AppThemeMode) {
        prefs.edit().putString("app_theme", newMode.name).apply()
        _themeMode.value = newMode
    }
}
