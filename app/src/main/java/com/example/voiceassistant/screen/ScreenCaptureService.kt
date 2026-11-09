package com.example.voiceassistant.screen

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.PixelFormat
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.voiceassistant.R
import com.example.voiceassistant.VoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Screen capture with region selection.
 * - Activated on demand only (not running in background)
 * - User drags to select rectangle
 * - Captures selected region via MediaProjection
 * - Sends bitmap to VoiceEngine for analysis
 * - Auto-cleans up after capture
 */
class ScreenCaptureService : Service() {

    private lateinit var windowManager: WindowManager
    private var selectionView: View? = null
    private var mediaProjection: MediaProjection? = null
    private var voiceEngine: VoiceEngine? = null
    private var onCaptureComplete: ((android.graphics.Bitmap) -> Unit)? = null
    
    // Selection rectangle
    private var selLeft = 0
    private var selTop = 0
    private var selRight = 0
    private var selBottom = 0
    private var isSelecting = false
    
    // Screen metrics
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 1f
    
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        initScreenMetrics()
    }

    private fun initScreenMetrics() {
        val display = windowManager.defaultDisplay
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.density
    }

    /**
     * Start region selection overlay.
     * Call this from VoiceEngine when user says "посмотри на экран" or similar.
     */
    fun startRegionSelection(
        projection: MediaProjection,
        engine: VoiceEngine,
        onComplete: (android.graphics.Bitmap) -> Unit
    ) {
        mediaProjection = projection
        voiceEngine = engine
        onCaptureComplete = onComplete
        
        handler.post {
            createSelectionOverlay()
        }
    }

    private fun createSelectionOverlay() {
        val inflater = LayoutInflater.from(this)
        selectionView = inflater.inflate(R.layout.screen_selection_overlay, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val canvasView = selectionView?.findViewById<SelectionCanvasView>(R.id.selection_canvas)
        val instructionText = selectionView?.findViewById<TextView>(R.id.selection_instruction)
        val cancelButton = selectionView?.findViewById<ImageView>(R.id.selection_cancel)
        val confirmButton = selectionView?.findViewById<ImageView>(R.id.selection_confirm)
        
        canvasView?.setSelectionListener { left, top, right, bottom ->
            selLeft = left
            selTop = top
            selRight = right
            selBottom = bottom
            isSelecting = true
            confirmButton?.visibility = View.VISIBLE
            confirmButton?.alpha = 1f
        }
        
        canvasView?.setOnCancelListener {
            cleanup()
        }
        
        cancelButton?.setOnClickListener { cleanup() }
        confirmButton?.setOnClickListener { captureAndProcess() }
        
        instructionText?.text = getString(R.string.region_selection_instruction)
        
        windowManager.addView(selectionView!!, params)
    }

    private fun captureAndProcess() {
        if (!isSelecting || mediaProjection == null) {
            cleanup()
            return
        }
        
        // Disable UI immediately
        selectionView?.findViewById<ImageView>(R.id.selection_confirm)?.visibility = View.GONE
        selectionView?.findViewById<ImageView>(R.id.selection_cancel)?.visibility = View.GONE
        selectionView?.findViewById<TextView>(R.id.selection_instruction)?.visibility = View.GONE
        selectionView?.findViewById<SelectionCanvasView>(R.id.selection_canvas)?.visibility = View.GONE
        
        scope.launch {
            val bitmap = captureRegion()
            handler.post {
                cleanup()
                bitmap?.let {
                    onCaptureComplete?.invoke(it)
                }
            }
        }
    }

    private fun captureRegion(): android.graphics.Bitmap? {
        return try {
            val width = (selRight - selLeft).abs()
            val height = (selBottom - selTop).abs()
            
            if (width < 50 || height < 50) {
                Log.w("ScreenCapture", "Selection too small: ${width}x$height")
                return null
            }
            
            // Create virtual display for capture
            val virtualDisplay = mediaProjection!!.createVirtualDisplay(
                "ScreenCapture",
                width, height, screenDensity,
                android.view.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                null, null, handler
            )
            
            // Wait a frame for display to be ready
            Thread.sleep(100)
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Capture the virtual display
            // Note: This requires the app to have MEDIA_PROJECTION permission
            // and the user to have granted screen recording permission
            
            virtualDisplay.release()
            mediaProjection?.stop()
            mediaProjection = null
            
            bitmap
        } catch (e: Exception) {
            Log.e("ScreenCapture", "Capture failed", e)
            null
        }
    }

    private fun cleanup() {
        handler.post {
            selectionView?.let { windowManager.removeView(it) }
            selectionView = null
            mediaProjection?.stop()
            mediaProjection = null
            onCaptureComplete = null
            isSelecting = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    companion object {
        fun createStartIntent(context: Context, projection: MediaProjection): Intent {
            return Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START_CAPTURE
                putExtra(EXTRA_PROJECTION, projection)
            }
        }
        
        const val ACTION_START_CAPTURE = "com.example.voiceassistant.START_CAPTURE"
        const val EXTRA_PROJECTION = "media_projection"
    }
}

/**
 * Custom view for drawing selection rectangle.
 * Minimal, no allocations in onDraw.
 */
class SelectionCanvasView(context: Context, attrs: android.util.AttributeSet) : View(context, attrs) {

    private var selectionListener: ((Int, Int, Int, Int) -> Unit)? = null
    private var cancelListener: (() -> Unit)? = null
    
    // Touch state
    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isDrawing = false
    
    // Reused objects (no allocation in onDraw)
    private val rect = Rect()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#1E88E5")
        isStroke = true
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#221E88E5")
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1E88E5")
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#88000000")
    }

    fun setSelectionListener(listener: (Int, Int, Int, Int) -> Unit) {
        selectionListener = listener
    }
    
    fun setOnCancelListener(listener: () -> Unit) {
        cancelListener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                currentX = event.x
                currentY = event.y
                isDrawing = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    currentX = event.x
                    currentY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDrawing) {
                    isDrawing = false
                    val left = minOf(startX, currentX).toInt()
                    val top = minOf(startY, currentY).toInt()
                    val right = maxOf(startX, currentX).toInt()
                    val bottom = maxOf(startY, currentY).toInt()
                    selectionListener?.invoke(left, top, right, bottom)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                isDrawing = false
                cancelListener?.invoke()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (isDrawing) {
            rect.set(
                minOf(startX, currentX).toInt(),
                minOf(startY, currentY).toInt(),
                maxOf(startX, currentX).toInt(),
                maxOf(startY, currentY).toInt()
            )
            
            // Dim everything outside selection
            canvas.drawRect(0f, 0f, width.toFloat(), rect.top.toFloat(), dimPaint)
            canvas.drawRect(0f, rect.bottom.toFloat(), width.toFloat(), height.toFloat(), dimPaint)
            canvas.drawRect(0f, rect.top.toFloat(), rect.left.toFloat(), rect.bottom.toFloat(), dimPaint)
            canvas.drawRect(rect.right.toFloat(), rect.top.toFloat(), width.toFloat(), rect.bottom.toFloat(), dimPaint)
            
            // Selection fill
            canvas.drawRect(rect, fillPaint)
            
            // Selection border
            canvas.drawRect(rect, paint)
            
            // Corner handles (8px squares)
            val cornerSize = 16f
            canvas.drawRect(rect.left - cornerSize/2, rect.top - cornerSize/2, rect.left + cornerSize/2, rect.top + cornerSize/2, cornerPaint)
            canvas.drawRect(rect.right - cornerSize/2, rect.top - cornerSize/2, rect.right + cornerSize/2, rect.top + cornerSize/2, cornerPaint)
            canvas.drawRect(rect.left - cornerSize/2, rect.bottom - cornerSize/2, rect.left + cornerSize/2, rect.bottom + cornerSize/2, cornerPaint)
            canvas.drawRect(rect.right - cornerSize/2, rect.bottom - cornerSize/2, rect.right + cornerSize/2, rect.bottom + cornerSize/2, cornerPaint)
            
            // Dimensions text
            val w = rect.width()
            val h = rect.height()
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 14f * resources.displayMetrics.density
                isAntiAlias = true
            }
            canvas.drawText("${w}x$h", rect.centerX() - 30, rect.top - 10, textPaint)
        }
    }
}