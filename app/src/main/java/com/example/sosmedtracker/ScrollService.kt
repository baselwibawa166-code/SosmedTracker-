package com.example.sosmedtracker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class ScrollService : AccessibilityService() {

    private val lastCounted = HashMap<String, Long>()
    private var currentPkg: String? = null
    private var overlay: LinearLayout? = null
    private var overlayText: TextView? = null
    private var wm: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            updateOverlay()
            handler.postDelayed(this, 5000L)
        }
    }

    private fun px(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val box = LinearLayout(this)
            box.orientation = LinearLayout.HORIZONTAL
            box.gravity = Gravity.CENTER_VERTICAL
            box.setPadding(px(6), px(5), px(12), px(5))
            val bg = GradientDrawable()
            bg.setColor(Color.parseColor("#CC0F172A"))
            bg.cornerRadius = px(20).toFloat()
            box.background = bg
            box.visibility = View.GONE

            val img = ImageView(this)
            img.setImageResource(R.drawable.overlay_avatar)
            val imgLp = LinearLayout.LayoutParams(px(22), px(22))
            imgLp.rightMargin = px(8)
            img.layoutParams = imgLp

            val tv = TextView(this)
            tv.textSize = 13f
            tv.setTextColor(Color.WHITE)

            box.addView(img)
            box.addView(tv)

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.y = px(36)

            manager.addView(box, lp)
            wm = manager
            overlay = box
            overlayText = tv
        } catch (e: Exception) {
            overlay = null
            overlayText = null
        }
        handler.post(ticker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                currentPkg = pkg
                updateOverlay()
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (!Store.TRACKED.contains(pkg)) return
                currentPkg = pkg

                // Satu gestur scroll bisa memicu banyak event, jadi dihitung 1x per 400 ms.
                val now = SystemClock.elapsedRealtime()
                val last = lastCounted[pkg] ?: 0L
                if (now - last < 400L) return
                lastCounted[pkg] = now

                Store.addScroll(this, pkg)
                updateOverlay()
            }
        }
    }

    private fun fmt(ms: Long): String {
        val m = ms / 60000L
        return if (m >= 60L) "${m / 60L}j ${m % 60L}m" else "${m}m"
    }

    private fun updateOverlay() {
        val o = overlay ?: return
        val t = overlayText ?: return
        val pkg = currentPkg
        var target: Target? = null
        if (pkg != null) {
            for (x in Store.TARGETS) {
                if (x.pkgs.contains(pkg)) target = x
            }
        }
        if (target == null || !Store.overlayOn(this)) {
            o.visibility = View.GONE
            return
        }

        var sc = 0
        for (p in target.pkgs) {
            sc += Store.getScroll(this, p)
        }
        var ms = 0L
        if (Usage.hasAccess(this)) {
            val usage = Usage.todayMs(this)
            for (p in target.pkgs) {
                ms += usage[p] ?: 0L
            }
        }
        t.text = target.name + "  ↕ " + sc + "  ⏱ " + fmt(ms)
        o.visibility = View.VISIBLE
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        try {
            val o = overlay
            if (o != null) wm?.removeView(o)
        } catch (e: Exception) {
        }
        overlay = null
        overlayText = null
        super.onDestroy()
    }
}
