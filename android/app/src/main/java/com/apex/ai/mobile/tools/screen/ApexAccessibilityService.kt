package com.apex.ai.mobile.tools.screen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ApexAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isActive = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank()) {
                currentForegroundPackage = pkg
            }
        }
    }

    override fun onInterrupt() {
        isActive = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            isActive = false
        }
    }

    fun getVisibleUiHierarchy(): List<Map<String, Any>> {
        val rootNode = rootInActiveWindow ?: return emptyList()
        val elements = mutableListOf<Map<String, Any>>()
        traverseNode(rootNode, elements, 0)
        return elements
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, list: MutableList<Map<String, Any>>, depth: Int) {
        if (node == null || depth > 8) return

        // Skip password / sensitive fields to preserve privacy
        if (node.isPassword) return

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val rect = Rect()
        node.getBoundsInScreen(rect)

        if (text.isNotBlank() || desc.isNotBlank() || node.isClickable) {
            list.add(
                mapOf(
                    "text" to text,
                    "description" to desc,
                    "view_id" to viewId,
                    "clickable" to node.isClickable,
                    "bounds" to "${rect.left},${rect.top},${rect.right},${rect.bottom}"
                )
            )
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), list, depth + 1)
        }
    }

    fun tapScreen(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return root.performAction(action)
    }

    fun typeTextIntoFocused(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (focused.isPassword) return false // Never auto-fill passwords

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun triggerBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    companion object {
        @Volatile
        var instance: ApexAccessibilityService? = null
            private set

        @Volatile
        var isActive: Boolean = false
            private set

        @Volatile
        var currentForegroundPackage: String = "com.apex.ai.mobile"
            private set
    }
}
