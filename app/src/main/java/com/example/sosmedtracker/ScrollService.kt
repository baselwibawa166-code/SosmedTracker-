package com.example.sosmedtracker

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class ScrollService : AccessibilityService() {

    private val lastCounted = HashMap<String, Long>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val pkg = event.packageName?.toString() ?: return
        if (!Store.TRACKED.contains(pkg)) return

        // Satu gestur scroll bisa memicu banyak event, jadi dihitung 1x per 400 ms.
        val now = SystemClock.elapsedRealtime()
        val last = lastCounted[pkg] ?: 0L
        if (now - last < 400L) return
        lastCounted[pkg] = now

        Store.addScroll(this, pkg)
    }

    override fun onInterrupt() {}
}
