package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class AnnotationTool {
    ARROW,
    RECTANGLE,
    CIRCLE,
    LINE,
    PEN,
    HIGHLIGHTER,
    ERASER
}

sealed class AnnotationElement {
    data class Freehand(
        val path: Path,
        val color: Color,
        val strokeWidth: Float,
        val isHighlighter: Boolean
    ) : AnnotationElement()

    data class Arrow(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val strokeWidth: Float
    ) : AnnotationElement()

    data class Rect(
        val topLeft: Offset,
        val size: Size,
        val color: Color,
        val strokeWidth: Float
    ) : AnnotationElement()

    data class Oval(
        val topLeft: Offset,
        val size: Size,
        val color: Color,
        val strokeWidth: Float
    ) : AnnotationElement()

    data class Line(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val strokeWidth: Float
    ) : AnnotationElement()
}

@Composable
fun AnnotationCanvas(
    isActive: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isActive) return

    val elements = remember { mutableStateListOf<AnnotationElement>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var startOffset by remember { mutableStateOf<Offset?>(null) }
    var currentDragOffset by remember { mutableStateOf<Offset?>(null) }

    var selectedTool by remember { mutableStateOf(AnnotationTool.ARROW) }
    var selectedColor by remember { mutableStateOf(Color(0xFFEF4444)) }
    var strokeWidth by remember { mutableFloatStateOf(8f) }

    val colors = listOf(
        Color(0xFFEF4444), // Red (default high-vis)
        Color(0xFFFBBF24), // Yellow
        Color(0xFF10B981), // Green
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
                            startOffset = offset
                            currentDragOffset = offset
                            if (selectedTool == AnnotationTool.PEN || selectedTool == AnnotationTool.HIGHLIGHTER) {
                                val path = Path().apply { moveTo(offset.x, offset.y) }
                                currentPath = path
                            } else if (selectedTool == AnnotationTool.ERASER) {
                                if (elements.isNotEmpty()) {
                                    elements.removeAt(elements.lastIndex)
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val pos = change.position
                            currentDragOffset = pos
                            if (selectedTool == AnnotationTool.PEN || selectedTool == AnnotationTool.HIGHLIGHTER) {
                                currentPath?.lineTo(pos.x, pos.y)
                            }
                        },
                        onDragEnd = {
                            val start = startOffset
                            val end = currentDragOffset
                            when (selectedTool) {
                                AnnotationTool.ARROW -> {
                                    if (start != null && end != null && (start - end).getDistance() > 10f) {
                                        elements.add(AnnotationElement.Arrow(start, end, selectedColor, strokeWidth))
                                    }
                                }
                                AnnotationTool.RECTANGLE -> {
                                    if (start != null && end != null) {
                                        val topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y))
                                        val size = Size(
                                            kotlin.math.abs(end.x - start.x).coerceAtLeast(10f),
                                            kotlin.math.abs(end.y - start.y).coerceAtLeast(10f)
                                        )
                                        elements.add(AnnotationElement.Rect(topLeft, size, selectedColor, strokeWidth))
                                    }
                                }
                                AnnotationTool.CIRCLE -> {
                                    if (start != null && end != null) {
                                        val topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y))
                                        val size = Size(
                                            kotlin.math.abs(end.x - start.x).coerceAtLeast(10f),
                                            kotlin.math.abs(end.y - start.y).coerceAtLeast(10f)
                                        )
                                        elements.add(AnnotationElement.Oval(topLeft, size, selectedColor, strokeWidth))
                                    }
                                }
                                AnnotationTool.LINE -> {
                                    if (start != null && end != null && (start - end).getDistance() > 10f) {
                                        elements.add(AnnotationElement.Line(start, end, selectedColor, strokeWidth))
                                    }
                                }
                                AnnotationTool.PEN, AnnotationTool.HIGHLIGHTER -> {
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
                                        elements.add(
                                            AnnotationElement.Freehand(
                                                path = p,
                                                color = finalColor,
                                                strokeWidth = width,
                                                isHighlighter = selectedTool == AnnotationTool.HIGHLIGHTER
                                            )
                                        )
                                    }
                                }
                                AnnotationTool.ERASER -> {
                                    // Handled on tap/drag
                                }
                            }
                            currentPath = null
                            startOffset = null
                            currentDragOffset = null
                        }
                    )
                }
        ) {
            // 1. Render all committed elements
            elements.forEach { element ->
                when (element) {
                    is AnnotationElement.Freehand -> {
                        drawPath(
                            path = element.path,
                            color = element.color,
                            style = Stroke(
                                width = element.strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                    is AnnotationElement.Arrow -> {
                        drawArrow(element.start, element.end, element.color, element.strokeWidth)
                    }
                    is AnnotationElement.Rect -> {
                        drawRoundRect(
                            color = element.color,
                            topLeft = element.topLeft,
                            size = element.size,
                            cornerRadius = CornerRadius(8f, 8f),
                            style = Stroke(width = element.strokeWidth)
                        )
                    }
                    is AnnotationElement.Oval -> {
                        drawOval(
                            color = element.color,
                            topLeft = element.topLeft,
                            size = element.size,
                            style = Stroke(width = element.strokeWidth)
                        )
                    }
                    is AnnotationElement.Line -> {
                        drawLine(
                            color = element.color,
                            start = element.start,
                            end = element.end,
                            strokeWidth = element.strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // 2. Render live preview of active gesture
            val start = startOffset
            val current = currentDragOffset
            if (start != null && current != null) {
                when (selectedTool) {
                    AnnotationTool.ARROW -> {
                        drawArrow(start, current, selectedColor, strokeWidth)
                    }
                    AnnotationTool.RECTANGLE -> {
                        val topLeft = Offset(minOf(start.x, current.x), minOf(start.y, current.y))
                        val size = Size(
                            kotlin.math.abs(current.x - start.x).coerceAtLeast(10f),
                            kotlin.math.abs(current.y - start.y).coerceAtLeast(10f)
                        )
                        drawRoundRect(
                            color = selectedColor,
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = CornerRadius(8f, 8f),
                            style = Stroke(width = strokeWidth)
                        )
                    }
                    AnnotationTool.CIRCLE -> {
                        val topLeft = Offset(minOf(start.x, current.x), minOf(start.y, current.y))
                        val size = Size(
                            kotlin.math.abs(current.x - start.x).coerceAtLeast(10f),
                            kotlin.math.abs(current.y - start.y).coerceAtLeast(10f)
                        )
                        drawOval(
                            color = selectedColor,
                            topLeft = topLeft,
                            size = size,
                            style = Stroke(width = strokeWidth)
                        )
                    }
                    AnnotationTool.LINE -> {
                        drawLine(
                            color = selectedColor,
                            start = start,
                            end = current,
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                    AnnotationTool.PEN, AnnotationTool.HIGHLIGHTER -> {
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
                    AnnotationTool.ERASER -> {}
                }
            }
        }

        // Floating Toolbar
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
                .padding(horizontal = 12.dp)
                .fillMaxWidth(0.95f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xF00F172A)
            ),
            elevation = CardDefaults.cardElevation(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Scrollable Tools Row
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Arrow (Pointer tool)
                    FilterChip(
                        selected = selectedTool == AnnotationTool.ARROW,
                        onClick = { selectedTool = AnnotationTool.ARROW },
                        label = { Text("Arrow ➡️") },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )

                    // Rectangle / Box
                    FilterChip(
                        selected = selectedTool == AnnotationTool.RECTANGLE,
                        onClick = { selectedTool = AnnotationTool.RECTANGLE },
                        label = { Text("Box 🔲") },
                        leadingIcon = {
                            Icon(Icons.Default.CropSquare, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )

                    // Circle / Oval
                    FilterChip(
                        selected = selectedTool == AnnotationTool.CIRCLE,
                        onClick = { selectedTool = AnnotationTool.CIRCLE },
                        label = { Text("Circle ⭕") },
                        leadingIcon = {
                            Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )

                    // Pen / Freehand
                    FilterChip(
                        selected = selectedTool == AnnotationTool.PEN,
                        onClick = { selectedTool = AnnotationTool.PEN },
                        label = { Text("Pen ✏️") },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )

                    // Highlighter
                    FilterChip(
                        selected = selectedTool == AnnotationTool.HIGHLIGHTER,
                        onClick = { selectedTool = AnnotationTool.HIGHLIGHTER },
                        label = { Text("Marker") },
                        leadingIcon = {
                            Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )

                    // Eraser
                    FilterChip(
                        selected = selectedTool == AnnotationTool.ERASER,
                        onClick = { selectedTool = AnnotationTool.ERASER },
                        label = { Text("Eraser") },
                        leadingIcon = {
                            Icon(Icons.Default.AutoFixNormal, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )

                    // Undo
                    IconButton(
                        onClick = { if (elements.isNotEmpty()) elements.removeAt(elements.lastIndex) },
                        enabled = elements.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = Color.White)
                    }

                    // Clear All
                    IconButton(
                        onClick = { elements.clear() },
                        enabled = elements.isNotEmpty()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = Color(0xFFEF4444))
                    }

                    // Close
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Colors & Sizes Row
                if (selectedTool != AnnotationTool.ERASER) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Colors
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            colors.forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
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
                        }

                        // Stroke Widths
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(4f to "S", 8f to "M", 16f to "L").forEach { (widthVal, label) ->
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
}

private fun DrawScope.drawArrow(start: Offset, end: Offset, color: Color, strokeWidth: Float) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = hypot(dx, dy)
    if (length < 8f) return

    val angle = atan2(dy, dx)
    val headLength = (strokeWidth * 3.8f).coerceIn(24f, 60f)
    val headAngle = 0.48f // ~27.5 degrees

    // Shaft line
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    // Solid triangle arrowhead at the tip
    val arrowPath = Path().apply {
        moveTo(end.x, end.y)
        lineTo(
            end.x - headLength * cos(angle - headAngle).toFloat(),
            end.y - headLength * sin(angle - headAngle).toFloat()
        )
        lineTo(
            end.x - (headLength * 0.72f) * cos(angle).toFloat(),
            end.y - (headLength * 0.72f) * sin(angle).toFloat()
        )
        lineTo(
            end.x - headLength * cos(angle + headAngle).toFloat(),
            end.y - headLength * sin(angle + headAngle).toFloat()
        )
        close()
    }
    drawPath(path = arrowPath, color = color)
}
