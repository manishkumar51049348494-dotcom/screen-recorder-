package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class OverlayToolType {
    ARROW,
    RECTANGLE,
    CIRCLE,
    PEN
}

sealed class OverlayShape {
    data class Arrow(val startX: Float, val startY: Float, val endX: Float, val endY: Float, val color: Int, val strokeWidth: Float) : OverlayShape()
    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float, val color: Int, val strokeWidth: Float) : OverlayShape()
    data class Circle(val left: Float, val top: Float, val right: Float, val bottom: Float, val color: Int, val strokeWidth: Float) : OverlayShape()
    data class Freehand(val path: Path, val color: Int, val strokeWidth: Float) : OverlayShape()
}

@SuppressLint("ViewConstructor")
class ScreenDrawOverlayView(
    context: Context,
    private val windowManager: WindowManager,
    private val onCloseListener: () -> Unit
) : FrameLayout(context) {

    private val shapes = mutableListOf<OverlayShape>()
    private var currentTool = OverlayToolType.ARROW
    private var currentColor = Color.parseColor("#EF4444") // Red default high-vis
    private var currentStrokeWidth = 10f

    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isDragging = false
    private var currentFreehandPath: Path? = null

    private val drawingCanvas: View
    private val toolbarLayout: LinearLayout

    private val colorsList = listOf(
        Color.parseColor("#EF4444"), // Red
        Color.parseColor("#FBBF24"), // Yellow
        Color.parseColor("#10B981"), // Green
        Color.parseColor("#38BDF8"), // Cyan / Sky Blue
        Color.WHITE
    )
    private var colorIndex = 0

    init {
        setBackgroundColor(Color.TRANSPARENT)

        // 1. Drawing Canvas (fills entire screen)
        drawingCanvas = object : View(context) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)

                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }

                // Render committed shapes
                for (shape in shapes) {
                    when (shape) {
                        is OverlayShape.Arrow -> {
                            paint.color = shape.color
                            paint.strokeWidth = shape.strokeWidth
                            drawArrow(canvas, shape.startX, shape.startY, shape.endX, shape.endY, paint)
                        }
                        is OverlayShape.Rect -> {
                            paint.color = shape.color
                            paint.strokeWidth = shape.strokeWidth
                            canvas.drawRoundRect(shape.left, shape.top, shape.right, shape.bottom, 16f, 16f, paint)
                        }
                        is OverlayShape.Circle -> {
                            paint.color = shape.color
                            paint.strokeWidth = shape.strokeWidth
                            canvas.drawOval(shape.left, shape.top, shape.right, shape.bottom, paint)
                        }
                        is OverlayShape.Freehand -> {
                            paint.color = shape.color
                            paint.strokeWidth = shape.strokeWidth
                            canvas.drawPath(shape.path, paint)
                        }
                    }
                }

                // Render active shape being dragged
                if (isDragging) {
                    paint.color = currentColor
                    paint.strokeWidth = currentStrokeWidth
                    when (currentTool) {
                        OverlayToolType.ARROW -> {
                            drawArrow(canvas, startX, startY, currentX, currentY, paint)
                        }
                        OverlayToolType.RECTANGLE -> {
                            val left = minOf(startX, currentX)
                            val top = minOf(startY, currentY)
                            val right = maxOf(startX, currentX)
                            val bottom = maxOf(startY, currentY)
                            canvas.drawRoundRect(left, top, right, bottom, 16f, 16f, paint)
                        }
                        OverlayToolType.CIRCLE -> {
                            val left = minOf(startX, currentX)
                            val top = minOf(startY, currentY)
                            val right = maxOf(startX, currentX)
                            val bottom = maxOf(startY, currentY)
                            canvas.drawOval(left, top, right, bottom, paint)
                        }
                        OverlayToolType.PEN -> {
                            currentFreehandPath?.let { canvas.drawPath(it, paint) }
                        }
                    }
                }
            }
        }

        addView(drawingCanvas, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Touch handling on canvas
        drawingCanvas.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    currentX = event.x
                    currentY = event.y
                    isDragging = true
                    if (currentTool == OverlayToolType.PEN) {
                        currentFreehandPath = Path().apply { moveTo(event.x, event.y) }
                    }
                    drawingCanvas.invalidate()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    currentX = event.x
                    currentY = event.y
                    if (currentTool == OverlayToolType.PEN) {
                        currentFreehandPath?.lineTo(event.x, event.y)
                    }
                    drawingCanvas.invalidate()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        when (currentTool) {
                            OverlayToolType.ARROW -> {
                                if (hypot((currentX - startX).toDouble(), (currentY - startY).toDouble()) > 10) {
                                    shapes.add(OverlayShape.Arrow(startX, startY, currentX, currentY, currentColor, currentStrokeWidth))
                                }
                            }
                            OverlayToolType.RECTANGLE -> {
                                val left = minOf(startX, currentX)
                                val top = minOf(startY, currentY)
                                val right = maxOf(startX, currentX)
                                val bottom = maxOf(startY, currentY)
                                if (right - left > 10 && bottom - top > 10) {
                                    shapes.add(OverlayShape.Rect(left, top, right, bottom, currentColor, currentStrokeWidth))
                                }
                            }
                            OverlayToolType.CIRCLE -> {
                                val left = minOf(startX, currentX)
                                val top = minOf(startY, currentY)
                                val right = maxOf(startX, currentX)
                                val bottom = maxOf(startY, currentY)
                                if (right - left > 10 && bottom - top > 10) {
                                    shapes.add(OverlayShape.Circle(left, top, right, bottom, currentColor, currentStrokeWidth))
                                }
                            }
                            OverlayToolType.PEN -> {
                                currentFreehandPath?.let {
                                    shapes.add(OverlayShape.Freehand(it, currentColor, currentStrokeWidth))
                                }
                            }
                        }
                    }
                    isDragging = false
                    currentFreehandPath = null
                    drawingCanvas.invalidate()
                    true
                }
                else -> false
            }
        }

        // 2. Toolbar at the bottom
        val scroll = HorizontalScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        toolbarLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#EE0F172A"))
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            background = bg
        }

        buildToolbarButtons()
        scroll.addView(toolbarLayout)

        val toolbarParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(40)
        }
        addView(scroll, toolbarParams)
    }

    private fun buildToolbarButtons() {
        toolbarLayout.removeAllViews()

        // 1. Arrow Tool (➡️)
        val arrowBtn = createToolButton("➡️ Arrow", currentTool == OverlayToolType.ARROW) {
            currentTool = OverlayToolType.ARROW
            buildToolbarButtons()
        }
        toolbarLayout.addView(arrowBtn)

        // 2. Rectangle / Box Tool (🔲)
        val boxBtn = createToolButton("🔲 Box", currentTool == OverlayToolType.RECTANGLE) {
            currentTool = OverlayToolType.RECTANGLE
            buildToolbarButtons()
        }
        toolbarLayout.addView(boxBtn)

        // 3. Circle / Oval Tool (⭕)
        val circleBtn = createToolButton("⭕ Circle", currentTool == OverlayToolType.CIRCLE) {
            currentTool = OverlayToolType.CIRCLE
            buildToolbarButtons()
        }
        toolbarLayout.addView(circleBtn)

        // 4. Pen / Freehand (✏️)
        val penBtn = createToolButton("✏️ Pen", currentTool == OverlayToolType.PEN) {
            currentTool = OverlayToolType.PEN
            buildToolbarButtons()
        }
        toolbarLayout.addView(penBtn)

        // 5. Color Toggle
        val colorBtn = createColorButton {
            colorIndex = (colorIndex + 1) % colorsList.size
            currentColor = colorsList[colorIndex]
            buildToolbarButtons()
        }
        toolbarLayout.addView(colorBtn)

        // 6. Undo
        val undoBtn = createIconButton(android.R.drawable.ic_menu_revert, Color.WHITE) {
            if (shapes.isNotEmpty()) {
                shapes.removeAt(shapes.lastIndex)
                drawingCanvas.invalidate()
            }
        }
        toolbarLayout.addView(undoBtn)

        // 7. Clear All
        val clearBtn = createIconButton(android.R.drawable.ic_menu_delete, Color.parseColor("#EF4444")) {
            shapes.clear()
            drawingCanvas.invalidate()
        }
        toolbarLayout.addView(clearBtn)

        // 8. Close & Resume phone touches
        val closeBtn = createIconButton(android.R.drawable.ic_menu_close_clear_cancel, Color.parseColor("#94A3B8")) {
            onCloseListener()
        }
        toolbarLayout.addView(closeBtn)
    }

    private fun createToolButton(text: String, isSelected: Boolean, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#94A3B8"))
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isSelected) Color.parseColor("#2563EB") else Color.parseColor("#1E293B"))
                cornerRadius = dp(16).toFloat()
            }
            background = bg
            setPadding(dp(12), dp(6), dp(12), dp(6))
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(6)
            }
            layoutParams = params
            setOnClickListener { onClick() }
        }
    }

    private fun createColorButton(onClick: () -> Unit): View {
        return View(context).apply {
            val size = dp(28)
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(currentColor)
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setStroke(dp(2), Color.WHITE)
            }
            background = bg
            val params = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(6)
            }
            layoutParams = params
            setOnClickListener { onClick() }
        }
    }

    private fun createIconButton(resId: Int, tintColor: Int, onClick: () -> Unit): ImageView {
        return ImageView(context).apply {
            setImageResource(resId)
            setColorFilter(tintColor)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(4)
            }
            layoutParams = params
            setOnClickListener { onClick() }
        }
    }

    private fun drawArrow(canvas: Canvas, sx: Float, sy: Float, ex: Float, ey: Float, paint: Paint) {
        val dx = ex - sx
        val dy = ey - sy
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (length < 8f) return

        // Draw line shaft
        canvas.drawLine(sx, sy, ex, ey, paint)

        // Draw solid arrow head at the end
        val angle = atan2(dy.toDouble(), dx.toDouble())
        val headLength = (paint.strokeWidth * 3.8f).coerceIn(24f, 54f)
        val headAngle = 0.48 // radians

        val arrowPath = Path().apply {
            moveTo(ex, ey)
            lineTo(
                (ex - headLength * cos(angle - headAngle)).toFloat(),
                (ey - headLength * sin(angle - headAngle)).toFloat()
            )
            lineTo(
                (ex - headLength * 0.72f * cos(angle)).toFloat(),
                (ey - headLength * 0.72f * sin(angle)).toFloat()
            )
            lineTo(
                (ex - headLength * cos(angle + headAngle)).toFloat(),
                (ey - headLength * sin(angle + headAngle)).toFloat()
            )
            close()
        }

        val fillPaint = Paint(paint).apply {
            style = Paint.Style.FILL
        }
        canvas.drawPath(arrowPath, fillPaint)
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
