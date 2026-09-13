package com.example.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class MediaRepository(
    private val context: Context,
    private val mediaRecordDao: MediaRecordDao
) {
    companion object {
        fun getVideosDirectory(context: Context): File {
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Pixelgram")
            if (publicDir.exists() || publicDir.mkdirs()) {
                if (publicDir.canWrite()) {
                    return publicDir
                }
            }
            val fallback = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "Pixelgram")
            fallback.mkdirs()
            return fallback
        }

        fun getPicturesDirectory(context: Context): File {
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Pixelgram")
            if (publicDir.exists() || publicDir.mkdirs()) {
                if (publicDir.canWrite()) {
                    return publicDir
                }
            }
            val fallback = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "Pixelgram")
            fallback.mkdirs()
            return fallback
        }
    }

    val videosFlow: Flow<List<MediaItem>> = mediaRecordDao.getAllVideosFlow().map { list ->
        list.map { it.toMediaItem(context) }
    }

    val screenshotsFlow: Flow<List<MediaItem>> = mediaRecordDao.getAllScreenshotsFlow().map { list ->
        list.map { it.toMediaItem(context) }
    }

    suspend fun syncWithStorage() = withContext(Dispatchers.IO) {
        // 1. Scan Public and App-specific Videos folders
        val videoDirs = listOfNotNull(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Pixelgram"),
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let { File(it, "Pixelgram") }
        )

        for (dir in videoDirs) {
            if (dir.exists()) {
                dir.listFiles { file -> file.extension.equals("mp4", ignoreCase = true) }?.forEach { file ->
                    val entity = extractVideoMetadata(file)
                    mediaRecordDao.insert(entity)
                }
            }
        }

        // 2. Scan Public and App-specific Screenshots folders
        val pictureDirs = listOfNotNull(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Pixelgram"),
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { File(it, "Pixelgram") }
        )

        for (dir in pictureDirs) {
            if (dir.exists()) {
                dir.listFiles { file -> file.extension.equals("png", ignoreCase = true) || file.extension.equals("jpg", ignoreCase = true) }?.forEach { file ->
                    val entity = extractImageMetadata(file)
                    mediaRecordDao.insert(entity)
                }
            }
        }
    }

    private fun extractVideoMetadata(file: File): MediaRecordEntity {
        var durationMs = 0L
        var width = 0
        var height = 0
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            durationMs = durStr?.toLongOrNull() ?: 0L
            width = wStr?.toIntOrNull() ?: 0
            height = hStr?.toIntOrNull() ?: 0
            retriever.release()
        } catch (_: Exception) {}

        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }

        return MediaRecordEntity(
            filePath = file.absolutePath,
            contentUriString = uri.toString(),
            title = file.nameWithoutExtension,
            durationMs = durationMs,
            sizeBytes = file.length(),
            dateAdded = file.lastModified(),
            width = width,
            height = height,
            isVideo = true,
            fps = 30,
            audioMode = "Recorded Audio"
        )
    }

    private fun extractImageMetadata(file: File): MediaRecordEntity {
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }

        return MediaRecordEntity(
            filePath = file.absolutePath,
            contentUriString = uri.toString(),
            title = file.nameWithoutExtension,
            durationMs = 0L,
            sizeBytes = file.length(),
            dateAdded = file.lastModified(),
            width = 1080,
            height = 1920,
            isVideo = false,
            fps = 0,
            audioMode = "None"
        )
    }

    suspend fun saveRecordedVideo(
        file: File,
        resolutionLabel: String,
        fps: Int,
        audioMode: String,
        durationMs: Long
    ): MediaItem = withContext(Dispatchers.IO) {
        var width = 1080
        var height = 1920
        var realDurationMs = durationMs
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (durStr != null) realDurationMs = durStr.toLongOrNull() ?: durationMs
            width = wStr?.toIntOrNull() ?: 1080
            height = hStr?.toIntOrNull() ?: 1920
            retriever.release()
        } catch (_: Exception) {}

        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }

        val entity = MediaRecordEntity(
            filePath = file.absolutePath,
            contentUriString = uri.toString(),
            title = file.nameWithoutExtension,
            durationMs = realDurationMs,
            sizeBytes = file.length(),
            dateAdded = System.currentTimeMillis(),
            width = width,
            height = height,
            isVideo = true,
            fps = fps,
            audioMode = audioMode
        )
        val id = mediaRecordDao.insert(entity)
        // Scan into phone's MediaStore so it immediately appears in phone's Files and Gallery
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("video/mp4"),
                null
            )
        } catch (_: Exception) {}

        entity.copy(id = id).toMediaItem(context)
    }

    suspend fun saveScreenshot(
        file: File,
        width: Int,
        height: Int
    ): MediaItem = withContext(Dispatchers.IO) {
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }

        val entity = MediaRecordEntity(
            filePath = file.absolutePath,
            contentUriString = uri.toString(),
            title = file.nameWithoutExtension,
            durationMs = 0L,
            sizeBytes = file.length(),
            dateAdded = System.currentTimeMillis(),
            width = width,
            height = height,
            isVideo = false,
            fps = 0,
            audioMode = "None"
        )
        val id = mediaRecordDao.insert(entity)
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("image/png"),
                null
            )
        } catch (_: Exception) {}

        entity.copy(id = id).toMediaItem(context)
    }

    suspend fun deleteMedia(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(item.filePath)
            if (file.exists()) {
                file.delete()
            }
            mediaRecordDao.deleteById(item.id)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun renameMedia(item: MediaItem, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val oldFile = File(item.filePath)
            if (!oldFile.exists()) return@withContext false

            val ext = oldFile.extension
            val newFile = File(oldFile.parentFile, "$newTitle.$ext")
            if (oldFile.renameTo(newFile)) {
                val newUri = try {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", newFile)
                } catch (_: Exception) {
                    Uri.fromFile(newFile)
                }
                val current = mediaRecordDao.getById(item.id)
                if (current != null) {
                    mediaRecordDao.update(
                        current.copy(
                            filePath = newFile.absolutePath,
                            contentUriString = newUri.toString(),
                            title = newTitle
                        )
                    )
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun trimVideo(item: MediaItem, startMs: Long, endMs: Long): MediaItem? = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(item.filePath)
            if (!srcFile.exists()) return@withContext null

            val trimmedFile = File(
                srcFile.parentFile,
                "${item.title}_trimmed_${System.currentTimeMillis()}.mp4"
            )

            val extractor = MediaExtractor()
            extractor.setDataSource(srcFile.absolutePath)
            val muxer = MediaMuxer(trimmedFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackCount = extractor.trackCount
            val indexMap = HashMap<Int, Int>(trackCount)
            var bufferSize = -1

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val dstIndex = muxer.addTrack(format)
                    indexMap[i] = dstIndex
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        bufferSize = if (newSize > bufferSize) newSize else bufferSize
                    }
                }
            }

            if (bufferSize < 0) bufferSize = 1024 * 1024
            val dstBuf = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            muxer.start()

            // Seek
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(dstBuf, 0)
                if (bufferInfo.size < 0) {
                    bufferInfo.size = 0
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                if (bufferInfo.presentationTimeUs > endMs * 1000) {
                    break
                }
                bufferInfo.flags = extractor.sampleFlags
                val trackIndex = extractor.sampleTrackIndex
                val muxerTrackIndex = indexMap[trackIndex]
                if (muxerTrackIndex != null) {
                    muxer.writeSampleData(muxerTrackIndex, dstBuf, bufferInfo)
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()

            val trimmedItem = saveRecordedVideo(
                file = trimmedFile,
                resolutionLabel = item.resolutionLabel,
                fps = item.fps,
                audioMode = item.audioMode,
                durationMs = (endMs - startMs).coerceAtLeast(0L)
            )
            trimmedItem
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getStorageInfo(): Pair<Long, Long> {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val availableBlocks = stat.availableBlocksLong
        val totalBlocks = stat.blockCountLong
        val freeBytes = availableBlocks * blockSize
        val totalBytes = totalBlocks * blockSize
        return Pair(freeBytes, totalBytes)
    }
}

private fun MediaRecordEntity.toMediaItem(context: Context): MediaItem {
    val file = File(filePath)
    val uri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: Exception) {
        Uri.parse(contentUriString)
    }
    return MediaItem(
        id = id,
        uri = uri,
        filePath = filePath,
        title = title,
        durationMs = durationMs,
        sizeBytes = if (file.exists()) file.length() else sizeBytes,
        dateAdded = dateAdded,
        width = width,
        height = height,
        isVideo = isVideo,
        fps = fps,
        audioMode = audioMode
    )
}
