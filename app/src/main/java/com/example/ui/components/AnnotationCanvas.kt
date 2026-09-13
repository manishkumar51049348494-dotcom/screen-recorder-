package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

enum class AnnotationTool {
    PEN,
    HIGHLIGHTER,
    ERASER
}

data class DrawnPath(
    val path: Path,
    val color: Color,
    val strokeWidth: Float,
    val isHighlighter: Boolean
)

@Composable
fun AnnotationCanvas(
    isActive: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isActive) return

    val paths = remember { mutableStateListOf<DrawnPath>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var currentPoints by remember { mutableStateOf(listOf<Offset>()) }

    var selectedTool by remember { mutableStateOf(AnnotationTool.PEN) }
    var selectedColor by remember { mutableStateOf(Color(0xFFEF4444)) }
    var strokeWidth by remember { mutableFloatStateOf(8f) }

    val colors = listOf(
        Color(0xFFEF4444), // Crimson
        Color(0xFFFBBF24), // Amber
        Color(0xFF10B981), // Emerald
        Color(0xFF38BDF8), // Sky Blue
        Color(0xFFA855F7), // Purple
        Color.White,
        Color.Black
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Drawing Surface
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedTool, selectedColor, strokeWidth) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (selectedTool == AnnotationTool.ERASER) {
                                // Clear near eraser
                                val eraseRadius = strokeWidth * 3
                                paths.removeAll { drawn ->
                                    // Basic check or remove last
                                    false
                                }
                                if (paths.isNotEmpty()) {
                                    paths.removeAt(paths.lastIndex)
                                }
                            } else {
                                val path = Path().apply { moveTo(offset.x, offset.y) }
                                currentPath = path
                                currentPoints = listOf(offset)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val pos = change.position
                            if (selectedTool == AnnotationTool.ERASER) {
                                if (paths.isNotEmpty()) {
                                    paths.removeAt(paths.lastIndex)
                                }
                            } else {
                                currentPath?.lineTo(pos.x, pos.y)
                                currentPoints = currentPoints + pos
                            }
                        },
                        onDragEnd = {
                            currentPath?.let { p ->
                                val finalColor = if (selectedTool == AnnotationTool.HIGHLIGHTER) {
                                    selectedColor.copy(alpha = 0.45f)
                                } else {
                                    selectedColor
                                }
                                val width = if (selectedTool == AnnotationTool.HIGHLIGHTER) {
                                    strokeWidth * 2.5f
                                } else {
                                    strokeWidth
                                }
                                paths.add(
                                    DrawnPath(
                                        path = p,
                                        color = finalColor,
                                        strokeWidth = width,
                                        isHighlighter = selectedTool == AnnotationTool.HIGHLIGHTER
                                    )
                                )
                            }
                            currentPath = null
                            currentPoints = emptyList()
                        }
                    )
                }
        ) {
            // Render committed paths
            paths.forEach { drawn ->
                drawPath(
                    path = drawn.path,
                    color = drawn.color,
                    style = Stroke(
                        width = drawn.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            // Render live path
            currentPath?.let { p ->
                val liveColor = if (selectedTool == AnnotationTool.HIGHLIGHTER) {
                    selectedColor.copy(alpha = 0.45f)
                } else {
                    selectedColor
                }
                val width = if (selectedTool == AnnotationTool.HIGHLIGHTER) {
                    strokeWidth * 2.5f
                } else {
                    strokeWidth
                }
                drawPath(
                    path = p,
                    color = liveColor,
                    style = Stroke(
                        width = width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // Floating Toolbar
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xEE1E293B)
            ),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tool and actions row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pen
                    FilterChip(
                        selected = selectedTool == AnnotationTool.PEN,
                        onClick = { selectedTool = AnnotationTool.PEN },
                        label = { Text("Pen") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )

                    // Highlighter
                    FilterChip(
                        selected = selectedTool == AnnotationTool.HIGHLIGHTER,
                        onClick = { selectedTool = AnnotationTool.HIGHLIGHTER },
                        label = { Text("Highlighter") },
                        leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )

                    // Eraser
                    FilterChip(
                        selected = selectedTool == AnnotationTool.ERASER,
                        onClick = { selectedTool = AnnotationTool.ERASER },
                        label = { Text("Eraser") },
                        leadingIcon = { Icon(Icons.Default.AutoFixNormal, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )

                    // Undo
                    IconButton(
                        onClick = { if (paths.isNotEmpty()) paths.removeAt(paths.lastIndex) },
                        enabled = paths.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo", tint = Color.White)
                    }

                    // Clear All
                    IconButton(
                        onClick = { paths.clear() },
                        enabled = paths.isNotEmpty()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = Color(0xFFEF4444))
                    }

                    // Close Canvas
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close Annotations", tint = Color.White)
                    }
                }

                // Colors Row (when not in eraser mode)
                if (selectedTool != AnnotationTool.ERASER) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colors.forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .clickable { selectedColor = col }
                                    .then(
                                        if (selectedColor == col) {
                                            Modifier.border(2.5.dp, Color.White, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Stroke Size toggles
                        listOf(4f to "S", 10f to "M", 20f to "L").forEach { (widthVal, label) ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (strokeWidth == widthVal) Color(0xFF38BDF8) else Color(0xFF334155))
                                    .clickable { strokeWidth = widthVal },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
