package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

data class TouchRipple(
    val id: Long,
    val offset: Offset,
    val progress: Animatable<Float, *>
)

@Composable
fun TouchIndicatorLayer(
    showTouches: Boolean,
    modifier: Modifier = Modifier
) {
    if (!showTouches) return

    val coroutineScope = rememberCoroutineScope()
    val ripples = remember { mutableStateListOf<TouchRipple>() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val anim = Animatable(0f)
                        val ripple = TouchRipple(
                            id = System.nanoTime(),
                            offset = offset,
                            progress = anim
                        )
                        ripples.add(ripple)
                        coroutineScope.launch {
                            anim.animateTo(1f, animationSpec = tween(400))
                            ripples.remove(ripple)
                        }
                    }
                )
            }
    ) {
        ripples.forEach { ripple ->
            val p = ripple.progress.value
            val radius = 30f + p * 60f
            val alpha = (1f - p).coerceIn(0f, 1f)

            // Inner circle
            drawCircle(
                color = Color(0xFFFF3B30).copy(alpha = alpha * 0.4f),
                radius = radius,
                center = ripple.offset
            )
            // Outer stroke
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.8f),
                radius = radius,
                center = ripple.offset,
                style = Stroke(width = 3f)
            )
        }
    }
}
