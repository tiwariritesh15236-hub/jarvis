package com.ritesh.jarvis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Phone control ka darwaza. Sirf tab chalta hai jab user Settings > Accessibility mein ise ON kare. */
class JarvisAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: JarvisAccessibilityService? = null }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun goBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun lockScreen(): Boolean =
        if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) else false

    fun screenshot(): Boolean =
        if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) else false

    /** Ungli se upar/neeche swipe. down=true matlab page neeche chale. */
    fun scroll(down: Boolean): Boolean {
        val m = resources.displayMetrics
        val x = m.widthPixels / 2f
        val y1 = m.heightPixels * (if (down) 0.72f else 0.28f)
        val y2 = m.heightPixels * (if (down) 0.28f else 0.72f)
        val path = Path().apply { moveTo(x, y1); lineTo(x, y2) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build()
        return dispatchGesture(g, null, null)
    }

    /** Screen par dikhne wale is likhe hue button/text par click. */
    fun clickText(t: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(t)
        for (n in nodes) {
            var c: AccessibilityNodeInfo? = n
            while (c != null) {
                if (c.isClickable) return c.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                c = c.parent
            }
        }
        return false
    }
}
