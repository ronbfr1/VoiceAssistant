package com.example.voiceassistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.voiceassistant.R
import com.example.voiceassistant.overlay.AssistantOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Lightweight AccessibilityService for global hotkey detection.
 * - No background processing
 * - Only intercepts specific key combinations
 * - Delegates to overlay service
 * - Minimal memory footprint
 */
class HotkeyAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Hotkey state tracking
    private var metaPressed = false
    private var spacePressed = false
    private var lastKeyTime = 0L
    private val KEY_COMBO_TIMEOUT = 500L  // ms
    
    // Configurable hotkey (default: Meta+Space)
    private var hotkeyMeta = true
    private var hotkeyKeyCode = KeyEvent.KEYCODE_SPACE

    override fun onCreate() {
        super.onCreate()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used - we only care about key events
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        event?.let { keyEvent ->
            val keyCode = keyEvent.keyCode
            val action = keyEvent.action
            val now = System.currentTimeMillis()
            
            // Reset combo if too much time passed
            if (now - lastKeyTime > KEY_COMBO_TIMEOUT) {
                metaPressed = false
                spacePressed = false
            }
            lastKeyTime = now
            
            when (action) {
                KeyEvent.ACTION_DOWN -> {
                    when (keyCode) {
                        KeyEvent.KEYCODE_META_LEFT,
                        KeyEvent.KEYCODE_META_RIGHT -> metaPressed = true
                        KeyEvent.KEYCODE_SPACE -> spacePressed = true
                    }
                    
                    // Check for hotkey combo
                    if (isHotkeyPressed()) {
                        triggerAssistant()
                        return true  // Consume event
                    }
                }
                KeyEvent.ACTION_UP -> {
                    when (keyCode) {
                        KeyEvent.KEYCODE_META_LEFT,
                        KeyEvent.KEYCODE_META_RIGHT -> metaPressed = false
                        KeyEvent.KEYCODE_SPACE -> spacePressed = false
                    }
                }
            }
        }
        return super.onKeyEvent(event)
    }

    private fun isHotkeyPressed(): Boolean {
        return (hotkeyMeta && metaPressed && spacePressed) ||
               (!hotkeyMeta && spacePressed)  // Space-only fallback
    }

    private fun triggerAssistant() {
        // Reset to prevent double-trigger
        metaPressed = false
        spacePressed = false
        
        handler.post {
            // Send broadcast to overlay service
            val intent = Intent(ACTION_TOGGLE_LISTENING)
            intent.packageName = packageName
            sendBroadcast(intent)
            
            // Optional haptic feedback
            performHapticFeedback()
        }
    }

    private fun performHapticFeedback() {
        scope.launch {
            try {
                // Light haptic via accessibility gesture (minimal)
                val path = Path().apply { moveTo(0f, 0f); lineTo(1f, 1f) }
                val gesture = GestureDescription.StrokeDescription(path, 0, 1)
                dispatchGesture(GestureDescription().addStroke(gesture), null, null)
            } catch (e: Exception) {
                // Ignore - haptic is optional
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_LISTENING = "com.example.voiceassistant.TOGGLE_LISTENING"
        const val ACTION_SHOW_OVERLAY = "com.example.voiceassistant.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.example.voiceassistant.HIDE_OVERLAY"
    }
}