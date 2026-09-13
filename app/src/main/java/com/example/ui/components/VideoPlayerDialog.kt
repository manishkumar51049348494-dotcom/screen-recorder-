package com.example.ui.components

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.MediaItem
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun VideoPlayerDialog(
    item: MediaItem,
    onDismiss: () -> Unit,
    onDelete: (MediaItem) -> Unit,
    onRename: (MediaItem, String) -> Unit,
    onTrim: (MediaItem, Long, Long) -> Unit
) {
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(item.durationMs.coerceAtLeast(1L)) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTrimDialog by remember { mutableStateOf(false) }

    // Position updater
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            videoViewRef?.let { vv ->
                currentPositionMs = vv.currentPosition.toLong()
                if (!vv.isPlaying) {
                    isPlaying = false
                }
            }
            delay(250)
        }
    }

    Dialog(
        onDismissRequest = {
            videoViewRef?.stopPlayback()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = "${item.resolutionLabel} • ${item.fps} FPS • ${item.formattedSize}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        videoViewRef?.stopPlayback()
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Video Surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(item.uri)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = false
                                    totalDurationMs = mp.duration.toLong().coerceAtLeast(1L)
                                    seekTo(1)
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    currentPositionMs = 0L
                                }
                                videoViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Play/Pause Center Button
                    IconButton(
                        onClick = {
                            videoViewRef?.let { vv ->
                                if (vv.isPlaying) {
                                    vv.pause()
                                    isPlaying = false
                                } else {
                                    vv.start()
                                    isPlaying = true
                                }
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0x99000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Seek bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(currentPositionMs),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Slider(
                        value = currentPositionMs.toFloat().coerceIn(0f, totalDurationMs.toFloat()),
                        onValueChange = { newPos ->
                            currentPositionMs = newPos.toLong()
                            videoViewRef?.seekTo(newPos.toInt())
                        },
                        valueRange = 0f..totalDurationMs.toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        text = formatTime(totalDurationMs),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Share
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, item.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }

                    // Trim
                    IconButton(onClick = { showTrimDialog = true }) {
                        Icon(Icons.Default.ContentCut, contentDescription = "Trim Video")
                    }

                    // Rename
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename Video")
                    }

                    // Open With External
                    IconButton(onClick = {
                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(item.uri, "video/mp4")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(viewIntent)
                        } catch (_: Exception) {}
                    }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open with external player")
                    }

                    // Delete
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        var newTitle by remember { mutableStateOf(item.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Recording") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onRename(item, newTitle.trim())
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Recording") },
            text = { Text("Are you sure you want to permanently delete \"${item.title}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        videoViewRef?.stopPlayback()
                        onDelete(item)
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Trim Dialog
    if (showTrimDialog) {
        var startSec by remember { mutableFloatStateOf(0f) }
        var endSec by remember { mutableFloatStateOf((totalDurationMs / 1000f).coerceAtLeast(1f)) }
        val maxDurationSec = (totalDurationMs / 1000f).coerceAtLeast(1f)

        AlertDialog(
            onDismissRequest = { showTrimDialog = false },
            title = { Text("Trim Recording") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select trim start and end point:")
                    Text(
                        text = "Start: ${String.format("%.1f", startSec)}s  •  End: ${String.format("%.1f", endSec)}s",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = startSec,
                        onValueChange = { if (it < endSec) startSec = it },
                        valueRange = 0f..maxDurationSec
                    )
                    Slider(
                        value = endSec,
                        onValueChange = { if (it > startSec) endSec = it },
                        valueRange = 0f..maxDurationSec
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTrimDialog = false
                        onTrim(item, (startSec * 1000).toLong(), (endSec * 1000).toLong())
                        onDismiss()
                    }
                ) {
                    Text("Trim & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrimDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return String.format("%02d:%02d", m, s)
}
