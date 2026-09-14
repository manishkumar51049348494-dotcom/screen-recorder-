package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.MainActivity
import kotlinx.coroutines.*

class FloatingControlService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var drawOverlayView: ScreenDrawOverlayView? = null
    private var isDrawModeActive = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stateObserverJob: Job? = null

    // UI elements
    private var timerText: TextView? = null
    private var pauseIcon: ImageView? = null
    private var isExpanded = true
    private var expandedControlsLayout: LinearLayout? = null

    companion object {
        var isShowing = false
            private set

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                return
            }
            try {
                val intent = Intent(context, FloatingControlService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingControlService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = createFloatingView()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 280
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var hasMoved = false

        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        hasMoved = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!hasMoved) {
                        // Tap to expand / collapse
                        toggleExpand()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(floatingView, params)
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return
        }

        // Observe recording state to update timer, pause icon, and auto-dismiss on stop
        observeRecordingState()
    }

    private fun observeRecordingState() {
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            ScreenRecordService.recordingState.collect { state ->
                if (!state.isRecording) {
                    stopSelf()
                    return@collect
                }

                // Update Timer
                val durSec = state.durationMs / 1000
                val m = (durSec % 3600) / 60
                val s = durSec % 60
                val timeFormatted = String.format("%02d:%02d", m, s)
                timerText?.text = if (state.isPaused) "⏸ $timeFormatted" else "🔴 $timeFormatted"

                // Update Pause/Resume Icon
                if (state.isPaused) {
                    pauseIcon?.setImageResource(android.R.drawable.ic_media_play)
                    pauseIcon?.setColorFilter(Color.parseColor("#10B981")) // Green for resume
                } else {
                    pauseIcon?.setImageResource(android.R.drawable.ic_media_pause)
                    pauseIcon?.setColorFilter(Color.WHITE)
                }
            }
        }
    }

    private fun toggleExpand() {
        isExpanded = !isExpanded
        expandedControlsLayout?.visibility = if (isExpanded) View.VISIBLE else View.GONE
    }

    private fun createFloatingView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(10), dp(6))
            gravity = Gravity.CENTER_VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#F00F172A")) // Modern dark slate
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            background = bg
        }

        // Live Timer Pill (always visible)
        timerText = TextView(this).apply {
            text = "🔴 00:00"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(dp(8), dp(4), dp(8), dp(4))
            val timerBg = GradientDrawable().apply {
                setColor(Color.parseColor("#33334155"))
                cornerRadius = dp(14).toFloat()
            }
            background = timerBg
        }
        root.addView(timerText)

        // Expanded Controls Layout (contains buttons: Pause, Stop, Arrow/Draw, Home)
        expandedControlsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, 0)
        }

        // 1. Pause / Resume Button
        val pauseBtnContainer = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#334155")) // Slate
                shape = GradientDrawable.OVAL
            }
            background = bg
            val p = dp(4)
            setPadding(p, p, p, p)
            val params = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginStart = dp(6)
            }
            layoutParams = params

            pauseIcon = ImageView(this@FloatingControlService).apply {
                setImageResource(android.R.drawable.ic_media_pause)
                setColorFilter(Color.WHITE)
                val iconParams = FrameLayout.LayoutParams(dp(20), dp(20)).apply {
                    gravity = Gravity.CENTER
                }
                layoutParams = iconParams
            }
            addView(pauseIcon)

            setOnClickListener {
                val state = ScreenRecordService.recordingState.value
                if (state.isPaused) {
                    ScreenRecordService.resumeRecording(this@FloatingControlService)
                } else {
                    ScreenRecordService.pauseRecording(this@FloatingControlService)
                }
            }
        }
        expandedControlsLayout?.addView(pauseBtnContainer)

        // 2. Stop & Save Button (Solid bright red circle with white square ⏹️)
        val stopBtnContainer = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#DC2626")) // Crimson red
                shape = GradientDrawable.OVAL
            }
            background = bg
            val params = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
            layoutParams = params

            // White rounded square inside (Standard ⏹️ stop icon)
            val stopSquare = View(this@FloatingControlService).apply {
                val squareBg = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(3).toFloat()
                }
                background = squareBg
                val sqParams = FrameLayout.LayoutParams(dp(14), dp(14)).apply {
                    gravity = Gravity.CENTER
                }
                layoutParams = sqParams
            }
            addView(stopSquare)

            setOnClickListener {
                Toast.makeText(this@FloatingControlService, "Recording stopped • Saving to Gallery...", Toast.LENGTH_SHORT).show()
                ScreenRecordService.stopRecording(this@FloatingControlService)
                stopSelf()
            }
        }
        expandedControlsLayout?.addView(stopBtnContainer)

        // 3. Arrow & Mark Screen Tool button (➡️ / ✏️)
        val drawBtnContainer = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#B45309")) // Amber
                shape = GradientDrawable.OVAL
            }
            background = bg
            val params = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginEnd = dp(6)
            }
            layoutParams = params

            val drawIcon = ImageView(this@FloatingControlService).apply {
                setImageResource(android.R.drawable.ic_menu_edit)
                setColorFilter(Color.parseColor("#FDE047")) // Bright Yellow
                val iconParams = FrameLayout.LayoutParams(dp(18), dp(18)).apply {
                    gravity = Gravity.CENTER
                }
                layoutParams = iconParams
            }
            addView(drawIcon)

            setOnClickListener {
                toggleScreenDrawOverlay()
            }
        }
        expandedControlsLayout?.addView(drawBtnContainer)

        // 4. Open Pixelgram App / Home icon
        val appBtnContainer = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B")) // Dark Slate
                shape = GradientDrawable.OVAL
            }
            background = bg
            val params = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginEnd = dp(4)
            }
            layoutParams = params

            val appIcon = ImageView(this@FloatingControlService).apply {
                setImageResource(android.R.drawable.ic_menu_agenda)
                setColorFilter(Color.parseColor("#38BDF8")) // Sky Blue
                val iconParams = FrameLayout.LayoutParams(dp(18), dp(18)).apply {
                    gravity = Gravity.CENTER
                }
                layoutParams = iconParams
            }
            addView(appIcon)

            setOnClickListener {
                val intent = Intent(this@FloatingControlService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }
        }
        expandedControlsLayout?.addView(appBtnContainer)

        root.addView(expandedControlsLayout)

        return root
    }

    private fun toggleScreenDrawOverlay() {
        if (isDrawModeActive) {
            closeScreenDrawOverlay()
        } else {
            openScreenDrawOverlay()
        }
    }

    private fun openScreenDrawOverlay() {
        if (drawOverlayView != null) return

        val wm = windowManager ?: return
        val overlay = ScreenDrawOverlayView(this, wm) {
            closeScreenDrawOverlay()
        }
        drawOverlayView = overlay

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        try {
            wm.addView(overlay, params)
            isDrawModeActive = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun closeScreenDrawOverlay() {
        drawOverlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        drawOverlayView = null
        isDrawModeActive = false
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        isShowing = false
        stateObserverJob?.cancel()
        serviceScope.cancel()

        closeScreenDrawOverlay()

        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        floatingView = null
    }
}
