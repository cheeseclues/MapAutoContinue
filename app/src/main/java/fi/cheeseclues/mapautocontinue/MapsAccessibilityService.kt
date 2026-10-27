package fi.cheeseclues.mapautocontinue

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Google Maps-only, explicitly armed, accessibility-based waypoint continuation.
 * Detection is reported while stationary; tapping additionally requires reliable movement.
 * Never issues coordinate gestures or blindly clicks controls.
 */
class MapsAccessibilityService : AccessibilityService() {
    companion object {
        private const val MAPS_PACKAGE = "com.google.android.apps.maps"
        private const val TAG = "MapAutoContinue"
        private const val POLL_INTERVAL_MS = 1200L
        private const val MIN_BUTTON_VISIBLE_MS = 2500L
        private const val CLICK_COOLDOWN_MS = 15000L
        private const val STATUS_REPEAT_MS = 15000L
        private const val DIAGNOSTIC_REPEAT_MS = 10000L
        private const val MAX_TREE_NODES = 1500

        private val BUTTON_TEXTS = listOf(
            "Next stop",  // Google Maps arrival screen, English (verified from screenshot)
            "Continue",   // Other Google Maps versions
            "Jatka",      // Finnish
            "Seuraava pysähdys", // Potential Finnish translation; not verified
            "Seuraava kohde"     // Potential Finnish translation; not verified
        )
        private val BUTTON_LABELS = BUTTON_TEXTS.map { normalize(it) }.toSet()

        private fun normalize(value: String) =
            value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var firstSeenElapsedMs = 0L
    private var handledThisAppearance = false
    private var lastClickElapsedMs = 0L
    private var previousFound = false
    private var lastStatus = ""
    private var lastStatusLogMs = 0L
    private var lastDiagnosticLogMs = 0L

    private val poll = object : Runnable {
        override fun run() {
            try {
                inspectMaps()
            } catch (e: Exception) {
                Log.e(TAG, "Accessibility inspection failed", e)
                status("Accessibility error: ${e.javaClass.simpleName}")
            } finally {
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        RideState.accessibilityConnected = true
        handler.removeCallbacks(poll)
        handler.post(poll)
        Log.i(TAG, "Accessibility connected (v0.4; auto-continue enabled during monitoring)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Polling also catches stationary arrival screens without fresh accessibility events.
    }

    override fun onInterrupt() = resetDetection()

    private fun status(message: String) {
        RideState.lastAction = message
        val now = SystemClock.elapsedRealtime()
        if (lastStatus != message || now - lastStatusLogMs >= STATUS_REPEAT_MS) {
            Log.i(TAG, message)
            lastStatus = message
            lastStatusLogMs = now
        }
    }

    private fun resetDetection() {
        firstSeenElapsedMs = 0L
        handledThisAppearance = false
        previousFound = false
    }

    private fun inspectMaps() {
        if (!RideState.riding) {
            resetDetection()
            // Do not overwrite lastAction when monitoring has deliberately been stopped.
            return
        }
        val root = rootInActiveWindow
        if (root == null) {
            resetDetection()
            status("Monitoring: no readable foreground window")
            return
        }
        if (root.packageName?.toString() != MAPS_PACKAGE) {
            resetDetection()
            status("Monitoring: waiting for Google Maps foreground")
            return
        }

        val button = findWaypointButton(root)
        if (button == null) {
            resetDetection()
            status("Google Maps visible — no recognized waypoint button (see tree diagnostics)")
            return
        }

        // Be deliberately conservative: a 'Next stop' label without arrival context
        // could be an ordinary trip-planning control unrelated to a reached waypoint.
        val arrivalContext = hasArrivalContext(root)
        val label = button.first
        val clickTarget = button.second
        val now = SystemClock.elapsedRealtime()

        if (!previousFound) {
            firstSeenElapsedMs = now
            previousFound = true
            Log.i(TAG, "Waypoint button detected: $label (arrival context=$arrivalContext)")
        }

        if (!arrivalContext) {
            status("DETECTED '$label' — arrival context not verified; no click")
            return
        }

        if (!RideState.hasFreshMovingFix(AppPrefs.minimumSpeed(this))) {
            status("DETECTED '$label' — waiting for fresh GPS movement above ${AppPrefs.minimumSpeed(this)} km/h")
            return
        }
        if (now - firstSeenElapsedMs < MIN_BUTTON_VISIBLE_MS) {
            status("DETECTED '$label' — checking that button stays visible")
            return
        }
        if (handledThisAppearance) return
        if (lastClickElapsedMs != 0L && now - lastClickElapsedMs < CLICK_COOLDOWN_MS) {
            status("DETECTED '$label' — click cooldown")
            return
        }

        val clicked = clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (clicked) {
            handledThisAppearance = true
            lastClickElapsedMs = now
            status("ACTION_CLICK sent to '$label' — verify Maps advanced")
        } else {
            status("ACTION_CLICK rejected for '$label'")
        }
    }

    /**
     * Google's new Compose bottom sheet exposes "Next stop" on a TextView whose
     * parent android.view.View is clickable. The android.widget.Button is a
     * NON-clickable sibling. UIAutomator verified this hierarchy in Oct 2026.
     *
     * v0.2 relied on findAccessibilityNodeInfosByText() alone. We now walk the
     * node tree directly, with the old API as a fallback, and log why matching
     * may fail. Never use blind coordinate taps.
     */
    private fun findWaypointButton(root: AccessibilityNodeInfo): Pair<String, AccessibilityNodeInfo>? {
        val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        var matchingLabels = 0
        var invisibleLabels = 0
        var withoutClickableParent = 0
        var candidate: Pair<String, AccessibilityNodeInfo>? = null

        fun inspect(node: AccessibilityNodeInfo, source: String) {
            val labelText = node.text?.toString()?.trim().orEmpty()
            val description = node.contentDescription?.toString()?.trim().orEmpty()
            val matches = normalize(labelText) in BUTTON_LABELS ||
                normalize(description) in BUTTON_LABELS
            if (!matches) return
            matchingLabels++
            if (!node.isVisibleToUser) {
                invisibleLabels++
                return
            }
            var current: AccessibilityNodeInfo? = node
            var level = 0
            while (current != null && level <= 5) {
                if (current.isClickable && current.isEnabled && current.isVisibleToUser) {
                    if (candidate == null) {
                        val label = if (normalize(labelText) in BUTTON_LABELS) labelText else description
                        candidate = label to current
                        Log.i(TAG, "Found waypoint '$label' via $source; " +
                            "target=${current.className}, clickable=${current.isClickable}, " +
                            "visible=${current.isVisibleToUser}, parentDepth=$level")
                    }
                    return
                }
                current = current.parent
                level++
            }
            withoutClickableParent++
        }

        // Scan all accessible children, not just descendants returned by text-search.
        while (stack.isNotEmpty() && visited < MAX_TREE_NODES) {
            val node = stack.removeLast()
            visited++
            inspect(node, "tree traversal")
            for (i in node.childCount - 1 downTo 0) {
                try {
                    node.getChild(i)?.let { stack.add(it) }
                } catch (_: IllegalStateException) {
                    // Maps can replace a Compose node mid-scan.
                }
            }
        }
        if (candidate != null) return candidate

        // Fallback for nodes that an accessibility provider exposes via search
        // but not through the direct child hierarchy.
        for (query in BUTTON_TEXTS) {
            try {
                for (node in root.findAccessibilityNodeInfosByText(query)) {
                    inspect(node, "accessibility text search")
                }
            } catch (_: IllegalStateException) { }
            if (candidate != null) return candidate
        }

        val now = SystemClock.elapsedRealtime()
        if (lastDiagnosticLogMs == 0L || now - lastDiagnosticLogMs >= DIAGNOSTIC_REPEAT_MS) {
            lastDiagnosticLogMs = now
            Log.i(TAG, "Tree diagnostics: visited=$visited, labels=$matchingLabels, " +
                "invisible=$invisibleLabels, noClickableParent=$withoutClickableParent, " +
                "rootChildren=${root.childCount}. " +
                "If labels=0, service hierarchy differs from UIAutomator dump.")
        }
        return null
    }

    private fun hasArrivalContext(root: AccessibilityNodeInfo): Boolean {
        val keywords = listOf("arriving at", "you've arrived", "you have arrived", "saavut", "olet saapunut")
        val stack = java.util.ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_TREE_NODES) {
            val node = stack.removeLast()
            visited++
            val text = normalize(node.text?.toString().orEmpty())
            val desc = normalize(node.contentDescription?.toString().orEmpty())
            if (node.isVisibleToUser && keywords.any { text.contains(it) || desc.contains(it) }) {
                return true
            }
            for (i in node.childCount - 1 downTo 0) {
                try {
                    node.getChild(i)?.let { stack.add(it) }
                } catch (_: IllegalStateException) { }
            }
        }
        // Fallback consistent with previous version.
        return listOf("Arriving at", "You've arrived", "Olet saapunut").any { phrase ->
            root.findAccessibilityNodeInfosByText(phrase).any { it.isVisibleToUser }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        RideState.accessibilityConnected = false
        resetDetection()
        super.onDestroy()
    }
}
