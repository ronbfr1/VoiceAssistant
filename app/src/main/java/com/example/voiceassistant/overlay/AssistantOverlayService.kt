package com.example.voiceassistant.overlay

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.voiceassistant.R
import com.example.voiceassistant.VoiceEngine
import com.example.voiceassistant.accessibility.HotkeyAccessibilityService

/**
 * Ultra-lightweight floating bubble overlay.
 * - Zero background threads/coroutines
 * - No animations unless actively listening
 * - Single small View (56dp) when collapsed
 * - Expands only on demand
 * - Edge glow ONLY during active listening (4 thin lines, 2dp each)
 * - ~2-3 MB RAM, near-zero CPU when idle
 */
class AssistantOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var expandedView: View? = null
    private var edgeGlowView: View? = null
    
    private var isExpanded = false
    private var isListening = false
    private var voiceEngine: VoiceEngine? = null
    
    // Touch handling - minimal allocations
    private var initialX = 0f
    private var initialY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var hasMoved = false
    private var lastClickTime = 0L
    
    // Screen bounds (cached once)
    private var screenWidth = 0
    private var screenHeight = 0
    private var maxX = 0
    private var maxY = 0
    
    // Reused handler - single instance
    private val handler = Handler(Looper.getMainLooper())
    
    // Layout params reused to avoid allocation
    private val bubbleParams = WindowManager.LayoutParams()
    private val expandedParams = WindowManager.LayoutParams()
    private val edgeGlowParams = WindowManager.LayoutParams()
    
    // Broadcast receiver for hotkey
    private val hotkeyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == HotkeyAccessibilityService.ACTION_TOGGLE_LISTENING) {
                handler.post { toggleListening() }
            } else if (intent?.action == HotkeyAccessibilityService.ACTION_SHOW_OVERLAY) {
                handler.post { showOverlay() }
            } else if (intent?.action == HotkeyAccessibilityService.ACTION_HIDE_OVERLAY) {
                handler.post { hideOverlay() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        initScreenDimensionsOnce()
        createBubbleView()
        createEdgeGlowView()
        registerReceiver()
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(HotkeyAccessibilityService.ACTION_TOGGLE_LISTENING)
            addAction(HotkeyAccessibilityService.ACTION_SHOW_OVERLAY)
            addAction(HotkeyAccessibilityService.ACTION_HIDE_OVERLAY)
        }
        registerReceiver(hotkeyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun initScreenDimensionsOnce() {
        val display = windowManager.defaultDisplay
        val point = Point()
        display.getRealSize(point)
        screenWidth = point.x
        screenHeight = point.y
    }

    private fun createBubbleView() {
        val inflater = LayoutInflater.from(this)
        bubbleView = inflater.inflate(R.layout.overlay_bubble_light, null)
        
        val params = bubbleParams.apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            // Start at right edge, vertical center
            x = screenWidth - 68
            y = screenHeight / 2 - 28
        }

        bubbleView?.setOnTouchListener { v, event -> onBubbleTouch(v, event) }
        
        // Pre-calc max bounds for clamping
        bubbleView?.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        maxX = screenWidth - (bubbleView?.measuredWidth ?: 56)
        maxY = screenHeight - (bubbleView?.measuredHeight ?: 56)

        windowManager.addView(bubbleView!!, params)
        setBubbleState(BubbleState.IDLE)
    }

    private fun createEdgeGlowView() {
        val inflater = LayoutInflater.from(this)
        edgeGlowView = inflater.inflate(R.layout.overlay_edge_glow_light, null)
        
        edgeGlowParams.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(edgeGlowView!!, edgeGlowParams)
        edgeGlowView?.visibility = View.GONE  // GONE = no layout pass, zero cost
    }

    private fun onBubbleTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = bubbleParams.x.toFloat()
                initialY = bubbleParams.y.toFloat()
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                hasMoved = false
                // Tiny scale feedback - no animation object allocation
                v.scaleX = 1.08f
                v.scaleY = 1.08f
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (Math.hypot(dx.toDouble(), dy.toDouble()) > 12) {
                    hasMoved = true
                    bubbleParams.x = (initialX + dx).toInt().coerceIn(0, maxX)
                    bubbleParams.y = (initialY + dy).toInt().coerceIn(0, maxY)
                    windowManager.updateViewLayout(v, bubbleParams)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.scaleX = 1f
                v.scaleY = 1f
                if (!hasMoved) {
                    val now = System.currentTimeMillis()
                    if (now - lastClickTime < 250) {
                        // Double tap = toggle listening (fastest action)
                        toggleListening()
                    } else {
                        // Single tap = expand/collapse
                        toggleExpanded()
                    }
                    lastClickTime = now
                }
            }
        }
        return true
    }

    private fun toggleExpanded() {
        isExpanded = !isExpanded
        if (isExpanded) showExpandedView() else hideExpandedView()
    }

    private fun showExpandedView() {
        val inflater = LayoutInflater.from(this)
        expandedView = inflater.inflate(R.layout.overlay_expanded_light, null)
        
        expandedParams.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.BOTTOM
            y = 80
        }

        expandedView?.findViewById<ImageView>(R.id.overlay_mic_button)?.setOnClickListener { toggleListening() }
        expandedView?.findViewById<ImageView>(R.id.overlay_close_button)?.setOnClickListener { toggleExpanded() }
        
        windowManager.addView(expandedView!!, expandedParams)
        bubbleView?.visibility = View.GONE
    }

    private fun hideExpandedView() {
        expandedView?.let { windowManager.removeView(it) }
        expandedView = null
        bubbleView?.visibility = View.VISIBLE
    }

    private fun toggleListening() {
        isListening = !isListening
        if (isListening) startListening() else stopListening()
    }

    private fun startListening() {
        setBubbleState(BubbleState.LISTENING)
        edgeGlowView?.visibility = View.VISIBLE
        edgeGlowView?.alpha = 1f  // No animation - instant
        voiceEngine?.startListening()
    }

    private fun stopListening() {
        setBubbleState(BubbleState.THINKING)
        edgeGlowView?.visibility = View.GONE  // Instant hide, no fade
        voiceEngine?.stopListening()
    }

    private fun showOverlay() {
        bubbleView?.visibility = View.VISIBLE
    }

    private fun hideOverlay() {
        bubbleView?.visibility = View.GONE
        hideExpandedView()
    }

    // Callbacks from VoiceEngine (called on main thread)
    fun onVoiceResult(result: String) {
        isListening = false
        setBubbleState(BubbleState.SPEAKING)
        edgeGlowView?.visibility = View.GONE
        voiceEngine?.speak(result)
        // Auto-collapse after speaking
        handler.postDelayed({ setBubbleState(BubbleState.IDLE) }, 1500)
    }

    fun onError(error: String) {
        isListening = false
        setBubbleState(BubbleState.ERROR)
        edgeGlowView?.visibility = View.GONE
        handler.postDelayed({ setBubbleState(BubbleState.IDLE) }, 2000)
    }

    private enum class BubbleState { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

    private fun setBubbleState(state: BubbleState) {
        val bubbleImage = bubbleView?.findViewById<ImageView>(R.id.bubble_image)
        val expandedMic = expandedView?.findViewById<ImageView>(R.id.overlay_mic_button)
        
        when (state) {
            BubbleState.IDLE -> {
                bubbleImage?.setImageResource(R.drawable.ic_assistant_idle_light)
                expandedMic?.setImageResource(R.drawable.ic_mic_off_light)
            }
            BubbleState.LISTENING -> {
                bubbleImage?.setImageResource(R.drawable.ic_assistant_listening_light)
                expandedMic?.setImageResource(R.drawable.ic_mic_on_light)
                // Single pulse - no infinite loop
                bubbleView?.animate()?.scaleX(1.1f)?.scaleY(1.1f)?.setDuration(400)?.withEndAction {
                    bubbleView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(400)?.start()
                }?.start()
            }
            BubbleState.THINKING -> {
                bubbleImage?.setImageResource(R.drawable.ic_assistant_thinking_light)
                expandedMic?.setImageResource(R.drawable.ic_mic_processing_light)
            }
            BubbleState.SPEAKING -> {
                bubbleImage?.setImageResource(R.drawable.ic_assistant_speaking_light)
                expandedMic?.setImageResource(R.drawable.ic_mic_off_light)
            }
            BubbleState.ERROR -> {
                bubbleImage?.setImageResource(R.drawable.ic_assistant_error_light)
                expandedMic?.setImageResource(R.drawable.ic_mic_off_light)
            }
        }
    }

    fun setVoiceEngine(engine: VoiceEngine) {
        voiceEngine = engine
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(hotkeyReceiver) } catch (e: Exception) {}
        bubbleView?.let { windowManager.removeView(it) }
        expandedView?.let { windowManager.removeView(it) }
        edgeGlowView?.let { windowManager.removeView(it) }
    }
}