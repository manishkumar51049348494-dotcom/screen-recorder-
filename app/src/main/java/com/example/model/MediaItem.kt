package com.example.model

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val filePath: String,
    val title: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val width: Int = 0,
    val height: Int = 0,
    val isVideo: Boolean = true,
    val fps: Int = 30,
    val audioMode: String = "Microphone"
) {
    val formattedDuration: String
        get() {
            if (!isVideo) return ""
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

    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.1f MB", mb)
                else -> String.format("%.1f KB", kb)
            }
        }

    val resolutionLabel: String
        get() {
            return if (width > 0 && height > 0) {
                "${width}x${height}"
            } else {
                "Unknown"
            }
        }
}
