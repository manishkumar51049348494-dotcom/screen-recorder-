package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.model.RecordingState
import kotlin.math.roundToInt

@Composable
fun FloatingControlsWidget(
    recordingState: RecordingState,
    onPauseToggle: () -> Unit,
    onStop: () -> Unit,
    onScreenshot: () -> Unit,
    onFacecamToggle: () -> Unit,
    onAnnotationToggle: () -> Unit,
    facecamActive: Boolean,
    annotationActive: Boolean,
    modifier: Modifier = Modifier
) {
    if (!recordingState.isRecording) return

    var isExpanded by remember { mutableStateOf(true) }
    var offsetX by remember { mutableFloatStateOf(40f) }
    var offsetY by remember { mutableFloatStateOf(160f) }

    // Pulse animation for recording indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xEE0F172A)
            ),
            elevation = CardDefaults.cardElevation(10.dp),
            modifier = Modifier.animateContentSize()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Recording Dot + Timer
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33334155))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .clickable { isExpanded = !isExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (recordingState.isPaused) Color(0xFFFBBF24)
                                else Color(0xFFEF4444).copy(alpha = pulseAlpha)
                            )
                    )
                    Text(
                        text = formatTime(recordingState.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }

                if (isExpanded) {
                    // Pause / Resume
                    IconButton(
                        onClick = onPauseToggle,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0x22FFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (recordingState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (recordingState.isPaused) "Resume" else "Pause",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Stop Button
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFEF4444), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Recording",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Screenshot
                    IconButton(
                        onClick = onScreenshot,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0x22FFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Take Screenshot",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Facecam
                    IconButton(
                        onClick = onFacecamToggle,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (facecamActive) Color(0xFF10B981) else Color(0x22FFFFFF),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBox,
                            contentDescription = "Toggle Facecam",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Annotations
                    IconButton(
                        onClick = onAnnotationToggle,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (annotationActive) Color(0xFFA855F7) else Color(0x22FFFFFF),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Draw,
                            contentDescription = "Toggle Annotations",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return String.format("%02d:%02d", m, s)
}
