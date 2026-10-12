package com.parag.chatgptshutter

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class ShutterAccessibilityService : AccessibilityService() {

    private val TAG = "ShutterAccessibility"

    // Set this to whatever key code you find from the logs.
    // Common selfie remotes use VOLUME_UP (24), ENTER (66), or MEDIA_PLAY_PAUSE (85).
    private val CONFIGURED_SHUTTER_KEY = KeyEvent.KEYCODE_VOLUME_UP

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected! Ready to receive events.")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Safe diagnostic logging for you to identify the actual key your remote sends.
        val actionName = when (event.action) {
            KeyEvent.ACTION_DOWN -> "ACTION_DOWN"
            KeyEvent.ACTION_UP -> "ACTION_UP"
            else -> "UNKNOWN_ACTION"
        }
        
        Log.d(TAG, "Diagnostic - onKeyEvent invoked! keyCode=${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)}), action=$actionName, deviceId=${event.deviceId}, source=${event.source}, scanCode=${event.scanCode}")

        if (event.keyCode == CONFIGURED_SHUTTER_KEY && event.action == KeyEvent.ACTION_UP) {
            Log.d(TAG, "Configured shutter key detected. Launching CaptureFlowActivity.")
            val intent = Intent(this, CaptureFlowActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("AUTO_CAPTURE", true)
            }
            startActivity(intent)
            return true
        }

        // Return false to let the system handle events we don't consume
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // UI automation will be added later.
    }

    override fun onInterrupt() {
        // No action required.
    }
}