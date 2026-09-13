package com.example.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun CircularVideoAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    customVideoUriString: String? = null,
    onVideoSelected: ((Uri) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showPreviewDialog by remember { mutableStateOf(false) }

    val defaultUri = remember(context) {
        Uri.parse("android.resource://${context.packageName}/${R.raw.header_avatar_video}")
    }

    val activeUri = remember(customVideoUriString, defaultUri) {
        if (!customVideoUriString.isNullOrBlank()) {
            try {
                Uri.parse(customVideoUriString)
            } catch (e: Exception) {
                defaultUri
            }
        } else {
            defaultUri
        }
    }

    // Retained MediaPlayer instance for non-stop playback
    var mediaPlayerInstance by remember { mutableStateOf<MediaPlayer?>(null) }
    var surfaceInstance by remember { mutableStateOf<Surface?>(null) }
    var isPrepared by remember { mutableStateOf(false) }

    // Initialize or re-initialize MediaPlayer when URI changes
    DisposableEffect(activeUri) {
        val player = MediaPlayer().apply {
            try {
                setDataSource(context, activeUri)
                isLooping = true
                setVolume(0f, 0f) // Silent continuous ambient avatar loop
                setOnPreparedListener { mp ->
                    isPrepared = true
                    try {
                        surfaceInstance?.let { s ->
                            if (s.isValid) {
                                mp.setSurface(s)
                            }
                        }
                        mp.start()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                setOnCompletionListener { mp ->
                    // Non-stop continuous playback guarantee: restart instantly
                    try {
                        mp.seekTo(0)
                        mp.start()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                setOnErrorListener { mp, _, _ ->
                    // Automatic error recovery: re-prepare and keep playing
                    try {
                        mp.reset()
                        mp.setDataSource(context, activeUri)
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
                        mp.prepareAsync()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                }
                prepareAsync()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mediaPlayerInstance = player

        onDispose {
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mediaPlayerInstance = null
            isPrepared = false
        }
    }

    // Watchdog to ensure video keeps running non-stop ("chalta rahe, ruke nahi")
    LaunchedEffect(activeUri, mediaPlayerInstance) {
        while (isActive) {
            delay(800)
            mediaPlayerInstance?.let { player ->
                try {
                    if (isPrepared && !player.isPlaying) {
                        surfaceInstance?.let { s ->
                            if (s.isValid) {
                                player.setSurface(s)
                            }
                        }
                        player.start()
                    }
                } catch (e: Exception) {
                    // Try to recover
                    try {
                        player.seekTo(0)
                        player.start()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // Resume playback when app or screen comes back to foreground
    DisposableEffect(lifecycleOwner, mediaPlayerInstance) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                try {
                    mediaPlayerInstance?.let { player ->
                        if (isPrepared && !player.isPlaying) {
                            player.start()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    listOf(Color(0xFFEF4444), Color(0xFFDC2626), Color(0xFF991B1B))
                ),
                shape = CircleShape
            )
            .clickable { showPreviewDialog = true },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            val newSurface = Surface(surface)
                            surfaceInstance = newSurface
                            mediaPlayerInstance?.let { player ->
                                try {
                                    player.setSurface(newSurface)
                                    if (isPrepared && !player.isPlaying) {
                                        player.start()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            surfaceInstance?.release()
                            surfaceInstance = null
                            mediaPlayerInstance?.let { player ->
                                try {
                                    player.setSurface(null)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }

                    // In case surface is already available
                    if (isAvailable && surfaceTexture != null) {
                        val newSurface = Surface(surfaceTexture)
                        surfaceInstance = newSurface
                        mediaPlayerInstance?.let { player ->
                            try {
                                player.setSurface(newSurface)
                                if (isPrepared && !player.isPlaying) {
                                    player.start()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            },
            update = { textureView ->
                if (textureView.isAvailable && surfaceInstance == null && textureView.surfaceTexture != null) {
                    val newSurface = Surface(textureView.surfaceTexture)
                    surfaceInstance = newSurface
                    mediaPlayerInstance?.let { player ->
                        try {
                            player.setSurface(newSurface)
                            if (isPrepared && !player.isPlaying) {
                                player.start()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        )
    }

    if (showPreviewDialog) {
        AvatarVideoPreviewDialog(
            videoUri = activeUri,
            onDismiss = { showPreviewDialog = false },
            onPickNewVideo = { newUri ->
                onVideoSelected?.invoke(newUri)
                showPreviewDialog = false
            }
        )
    }
}

@Composable
private fun AvatarVideoPreviewDialog(
    videoUri: Uri,
    onDismiss: () -> Unit,
    onPickNewVideo: (Uri) -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onPickNewVideo(uri)
        }
    }

    Dialog(
        onDismissRequest = {
            mediaPlayerRef?.release()
            mediaPlayerRef = null
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pixelgram Avatar Video",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(
                        onClick = {
                            mediaPlayerRef?.release()
                            mediaPlayerRef = null
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Video player in circular / rounded frame
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                        .border(
                            width = 3.dp,
                            brush = Brush.linearGradient(
                                listOf(Color(0xFFEF4444), Color(0xFFDC2626), Color(0xFF991B1B))
                            ),
                            shape = CircleShape
                        )
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                        val mp = MediaPlayer().apply {
                                            try {
                                                setSurface(Surface(surface))
                                                setDataSource(ctx, videoUri)
                                                isLooping = true
                                                setVolume(1f, 1f) // Audio enabled in preview dialog
                                                setOnCompletionListener { player ->
                                                    try {
                                                        player.seekTo(0)
                                                        player.start()
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                                prepareAsync()
                                                setOnPreparedListener { player ->
                                                    player.start()
                                                    isPlaying = true
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        mediaPlayerRef = mp
                                    }

                                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                        mediaPlayerRef?.release()
                                        mediaPlayerRef = null
                                        return true
                                    }

                                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                                }
                            }
                        }
                    )
                }

                // Controls: Play/Pause and Choose Video
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            mediaPlayerRef?.let { mp ->
                                if (mp.isPlaying) {
                                    mp.pause()
                                    isPlaying = false
                                } else {
                                    mp.start()
                                    isPlaying = true
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPlaying) "Pause" else "Play")
                    }

                    Button(
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Choose Video")
                    }
                }
            }
        }
    }
}
