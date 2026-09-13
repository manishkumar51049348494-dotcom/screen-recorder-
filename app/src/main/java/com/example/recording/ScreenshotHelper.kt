package com.example.recording

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import com.example.data.MediaRepository
import com.example.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

object ScreenshotHelper {

    suspend fun captureWindowScreenshot(
        activity: Activity,
        mediaRepository: MediaRepository
    ): Result<MediaItem> = withContext(Dispatchers.IO) {
        val window = activity.window
        val decorView = window.decorView
        val width = decorView.width.coerceAtLeast(1)
        val height = decorView.height.coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val copySuccess = suspendCancellableCoroutine<Boolean> { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PixelCopy.request(
                    window,
                    Rect(0, 0, width, height),
                    bitmap,
                    { copyResult ->
                        continuation.resume(copyResult == PixelCopy.SUCCESS)
                    },
                    Handler(Looper.getMainLooper())
                )
            } else {
                @Suppress("DEPRECATION")
                decorView.isDrawingCacheEnabled = true
                @Suppress("DEPRECATION")
                val cache = decorView.drawingCache
                if (cache != null) {
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawBitmap(cache, 0f, 0f, null)
                }
                @Suppress("DEPRECATION")
                decorView.isDrawingCacheEnabled = false
                continuation.resume(true)
            }
        }

        if (!copySuccess) {
            return@withContext Result.failure(Exception("PixelCopy failed to capture screen"))
        }

        val picturesDir = MediaRepository.getPicturesDirectory(activity)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(picturesDir, "Pixelgram_Screenshot_$timestamp.png")

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val mediaItem = mediaRepository.saveScreenshot(file, width, height)
            Result.success(mediaItem)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
