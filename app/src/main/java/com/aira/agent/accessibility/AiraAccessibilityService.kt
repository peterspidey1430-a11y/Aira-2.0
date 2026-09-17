package com.aira.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.aira.agent.errors.AiraError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aira's screen-aware brain. When the user enables this in Settings →
 * Accessibility, Aira can:
 *   - read the current screen (nodes, text, ids, content-desc),
 *   - find a button/field by text or id,
 *   - tap, long-tap, type, scroll, swipe,
 *   - press Back globally,
 *   - inspect any visible window.
 *
 * The service is **only** active when the user has explicitly turned
 * on Accessibility for Aira in Settings. It does nothing silently.
 */
class AiraAccessibilityService : AccessibilityService() {

    private val _screenSnapshot = MutableStateFlow<List<String>>(emptyList())
    val screenSnapshot: StateFlow<List<String>> = _screenSnapshot.asStateFlow()

    private val _currentPackage = MutableStateFlow<String?>(null)
    val currentPackage: StateFlow<String?> = _currentPackage.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _enabled.value = true
        serviceInfo?.let { Log.i(TAG, "Service connected: ${it.description}") }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        _enabled.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        _enabled.value = false
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            _currentPackage.value = event?.packageName?.toString()
        } catch (e: Throwable) {}
    }

    override fun onInterrupt() {}

    // ------------------ Public operations ------------------

    fun readScreen(max: Int = 40): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = mutableListOf<String>()
        walk(root, out, max)
        _screenSnapshot.value = out
        root.recycle()
        return out
    }

    private fun walk(node: AccessibilityNodeInfo, out: MutableList<String>, max: Int) {
        if (out.size >= max) return
        try {
            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            val id = node.viewIdResourceName
            val cls = node.className?.toString()?.substringAfterLast('.') ?: ""
            val line = buildString {
                if (id != null) append("id=$id ")
                if (text.isNotEmpty()) append("text=\"$text\" ")
                if (desc.isNotEmpty()) append("desc=\"$desc\" ")
                if (cls.isNotEmpty()) append("[${cls}]")
            }.trim()
            if (line.isNotEmpty()) out += line
            for (i in 0 until node.childCount) {
                val c = node.getChild(i) ?: continue
                walk(c, out, max)
                c.recycle()
            }
        } catch (e: Throwable) {}
    }

    fun tap(text: String?, resourceId: String?, contentDesc: String?): Boolean {
        val root = rootInActiveWindow ?: throw AiraError.AutomationBlocked("No active window")
        try {
            val target = findNode(root, text, resourceId, contentDesc) ?: throw AiraError.ActionFailed("Could not find element to tap")
            val bounds = Rect()
            target.getBoundsInScreen(bounds)
            val x = bounds.centerX().toFloat()
            val y = bounds.centerY().toFloat()
            val success = dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(Path().apply { moveTo(x, y); lineTo(x, y) }, 0, 80))
                    .build(),
                null,
                null
            )
            return success
        } finally {
            root.recycle()
        }
    }

    fun type(text: String, targetId: String?): Boolean {
        val root = rootInActiveWindow ?: throw AiraError.AutomationBlocked("No active window")
        try {
            val target = if (targetId != null) findNode(root, null, targetId, null)
                         else findNode(root, null, null, "EditText") ?: findFirstFocusableEditable(root)
                         ?: throw AiraError.ActionFailed("No input field found")
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val args = Bundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
            }
            return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally {
            root.recycle()
        }
    }

    fun scroll(direction: String, amount: Int): Boolean {
        val root = rootInActiveWindow ?: throw AiraError.AutomationBlocked("No active window")
        try {
            val target = findScrollable(root) ?: throw AiraError.ActionFailed("No scrollable view")
            val action = when (direction.lowercase()) {
                "down" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                "up" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                "left" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                "right" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            var ok = false
            repeat(amount.coerceIn(1, 10)) { ok = target.performAction(action) }
            return ok
        } finally {
            root.recycle()
        }
    }

    fun swipe(direction: String, magnitude: Float): Boolean {
        val displayMetrics = resources.displayMetrics
        val w = displayMetrics.widthPixels.toFloat()
        val h = displayMetrics.heightPixels.toFloat()
        val (sx, sy, ex, ey) = when (direction.lowercase()) {
            "up" -> Quadruple(w / 2, h * 0.8f, w / 2, h * (0.8f - magnitude))
            "down" -> Quadruple(w / 2, h * 0.2f, w / 2, h * (0.2f + magnitude))
            "left" -> Quadruple(w * 0.8f, h / 2, w * (0.8f - magnitude), h / 2)
            "right" -> Quadruple(w * 0.2f, h / 2, w * (0.2f + magnitude), h / 2)
            else -> Quadruple(w / 2, h * 0.8f, w / 2, h * 0.3f)
        }
        val path = Path().apply {
            moveTo(sx, sy)
            lineTo(ex, ey)
        }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
                .build(),
            null, null
        )
    }

    fun globalBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    // ------------------ Helpers ------------------

    private fun findNode(root: AccessibilityNodeInfo, text: String?, id: String?, desc: String?): AccessibilityNodeInfo? {
        val all = root.findAccessibilityNodeInfosByViewId(id ?: "android:id/content").orEmpty()
        if (all.isNotEmpty() && id != null) return all.firstOrNull()

        val lowerText = text?.lowercase()
        val lowerDesc = desc?.lowercase()
        return findRecursive(root) { node ->
            val nodeText = node.text?.toString()?.lowercase()
            val nodeDesc = node.contentDescription?.toString()?.lowercase()
            val nodeId = node.viewIdResourceName
            val okText = lowerText == null || nodeText?.contains(lowerText) == true
            val okDesc = lowerDesc == null || nodeDesc?.contains(lowerDesc) == true
            val okId = id == null || nodeId == id
            okText && okDesc && okId
        }
    }

    private fun findFirstFocusableEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findRecursive(root) { node ->
            node.isEditable && (node.isFocusable || node.className?.toString()?.contains("EditText") == true)
        }

    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findRecursive(root) { it.isScrollable }

    private inline fun findRecursive(node: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (match(node)) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val r = findRecursive(c, match)
            if (r != null) return r
        }
        return null
    }

    private data class Quadruple(val a: Float, val b: Float, val c: Float, val d: Float)

    companion object {
        private const val TAG = "AiraA11y"
        @Volatile var instance: AiraAccessibilityService? = null
            private set

        /** Whether the user enabled our service via Settings → Accessibility. */
        fun isEnabled(context: android.content.Context): Boolean {
            val expected = context.packageName + "/" + AiraAccessibilityService::class.java.name
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return enabled?.split(':')?.any { it.equals(expected, ignoreCase = true) } == true
        }

        /** Active foreground window's package — works even without our service. */
        fun foregroundPackage(): String? {
            return try {
                instance?._currentPackage?.value
            } catch (_: Throwable) { null }
        }
    }
}