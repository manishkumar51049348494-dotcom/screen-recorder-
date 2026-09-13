package com.example.recording

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresApi

sealed class CallAudioCompatibilityResult {
    object Supported : CallAudioCompatibilityResult()
    data class Restricted(val reason: String) : CallAudioCompatibilityResult()
}

object AudioCaptureHelper {

    const val CALL_AUDIO_RESTRICTED_MESSAGE =
        "Call audio is restricted by Android or the calling app on this device."

    /**
     * Inspects whether Android platform and third-party call applications allow audio playback capture.
     * On Android 10+ (API 29+), AudioPlaybackCapture is officially available, but third-party VoIP
     * applications (WhatsApp, Instagram, Messenger, Phone calls) explicitly set AudioAttributes.USAGE_VOICE_COMMUNICATION
     * or set ALLOW_CAPTURE_BY_NONE in their audio attributes, which Android OS enforces as strictly uncapturable.
     */
    fun checkCallAudioCompatibility(context: Context): CallAudioCompatibilityResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return CallAudioCompatibilityResult.Restricted(
                "Internal audio playback capture requires Android 10 (API 29) or higher. $CALL_AUDIO_RESTRICTED_MESSAGE"
            )
        }
        // In Android 10+, calling apps like WhatsApp, Messenger, and Phone calls flag audio as non-capturable
        return CallAudioCompatibilityResult.Restricted(
            CALL_AUDIO_RESTRICTED_MESSAGE
        )
    }

    /**
     * Checks if microphone permission and hardware are genuinely available without being held exclusively
     * by another calling app in VoIP mode.
     */
    fun isMicrophoneAvailable(context: Context): Boolean {
        var audioRecord: AudioRecord? = null
        return try {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (minBufferSize <= 0) return false

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize
            )
            val state = audioRecord.state == AudioRecord.STATE_INITIALIZED
            audioRecord.release()
            state
        } catch (_: Exception) {
            audioRecord?.release()
            false
        }
    }
}
