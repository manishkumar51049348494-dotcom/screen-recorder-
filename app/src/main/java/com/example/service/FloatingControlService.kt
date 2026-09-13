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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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

        // 1. Pause / Resume icon
        pauseIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setColorFilter(Color.WHITE)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener {
                val state = ScreenRecordService.recordingState.value
                val intent = Intent(this@FloatingControlService, ScreenRecordService::class.java).apply {
                    action = if (state.isPaused) ScreenRecordService.ACTION_RESUME else ScreenRecordService.ACTION_PAUSE
                }
                startService(intent)
            }
        }
        expandedControlsLayout?.addView(pauseIcon)

        // 2. Stop & Save icon (Red square / stop button)
        val stopBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#EF4444")) // Crimson red
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener {
                val intent = Intent(this@FloatingControlService, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_STOP
                }
                startService(intent)
                stopSelf()
            }
        }
        expandedControlsLayout?.addView(stopBtn)

        // 3. Arrow & Mark Screen Tool button (➡️ / ✏️)
        val drawBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            setColorFilter(Color.parseColor("#FBBF24")) // Yellow
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener {
                toggleScreenDrawOverlay()
            }
        }
        expandedControlsLayout?.addView(drawBtn)

        // 4. Open Pixelgram App / Home icon
        val appBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_agenda)
            setColorFilter(Color.parseColor("#38BDF8")) // Sky Blue
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener {
                val intent = Intent(this@FloatingControlService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }
        }
        expandedControlsLayout?.addView(appBtn)

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
