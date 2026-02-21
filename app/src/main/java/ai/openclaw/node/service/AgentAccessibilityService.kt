package ai.openclaw.node.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Accessibility service that gives agents full screen control:
 * - Tap/click at coordinates
 * - Swipe gestures
 * - Type text into focused fields
 * - Read screen content (UI tree)
 * - Find and interact with elements by text/description
 */
class AgentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "A11yAgent"
        var instance: AgentAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected — agents have screen control")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to react to events, just provide control
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ── Actions agents can invoke ──

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    fun takeScreenshot(): Boolean = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)

    fun findByText(text: String): JSONArray {
        val results = JSONArray()
        val root = rootInActiveWindow ?: return results

        val nodes = root.findAccessibilityNodeInfosByText(text)
        nodes?.forEach { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            results.put(JSONObject().apply {
                put("text", node.text?.toString() ?: "")
                put("className", node.className?.toString() ?: "")
                put("contentDescription", node.contentDescription?.toString() ?: "")
                put("bounds", JSONObject().apply {
                    put("left", rect.left)
                    put("top", rect.top)
                    put("right", rect.right)
                    put("bottom", rect.bottom)
                    put("centerX", rect.centerX())
                    put("centerY", rect.centerY())
                })
                put("clickable", node.isClickable)
                put("editable", node.isEditable)
            })
        }
        return results
    }

    fun getScreenTree(maxDepth: Int = 5): JSONObject {
        val root = rootInActiveWindow ?: return JSONObject().apply { put("error", "No window") }
        return nodeToJson(root, 0, maxDepth)
    }

    private fun nodeToJson(node: AccessibilityNodeInfo, depth: Int, maxDepth: Int): JSONObject {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val obj = JSONObject().apply {
            put("class", node.className?.toString()?.substringAfterLast('.') ?: "")
            node.text?.let { put("text", it.toString().take(100)) }
            node.contentDescription?.let { put("desc", it.toString().take(100)) }
            if (node.isClickable) put("clickable", true)
            if (node.isEditable) put("editable", true)
            if (node.isChecked) put("checked", true)
            put("bounds", "${rect.left},${rect.top},${rect.right},${rect.bottom}")
        }

        if (depth < maxDepth && node.childCount > 0) {
            val children = JSONArray()
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { children.put(nodeToJson(it, depth + 1, maxDepth)) }
            }
            if (children.length() > 0) obj.put("children", children)
        }

        return obj
    }
}
