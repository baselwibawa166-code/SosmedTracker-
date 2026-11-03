package com.example.sosmedtracker

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
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

    // Pelacak aplikasi yang sedang di depan (lewat UsageStats, bukan event jendela).
    private var fgPkg: String? = null
    private var lastEventTs = 0L

    // Cache durasi supaya tidak query berat tiap 2 detik.
    private var cachedUsage: Map<String, Long> = emptyMap()
    private var cachedAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            updateOverlay()
            handler.postDelayed(this, 2000L)
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
                // Cuma dipakai kalau Usage Access belum aktif. Abaikan jendela sistem/keyboard.
                if (pkg == "com.android.systemui") return
                if (pkg.contains("inputmethod") || pkg.contains("keyboard")) return
                currentPkg = pkg
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

    /** Update fgPkg dari event UsageStats yang baru (incremental, murah). */
    private fun pollForeground() {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val from = if (lastEventTs == 0L) now - 6L * 3600L * 1000L else lastEventTs - 3000L
            val events = usm.queryEvents(from, now) ?: return
            val e = UsageEvents.Event()
            var maxTs = lastEventTs
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val p = e.packageName ?: continue
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        fgPkg = p
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        if (fgPkg == p) fgPkg = null
                    }
                }
                if (e.timeStamp > maxTs) maxTs = e.timeStamp
            }
            lastEventTs = if (maxTs == 0L) now else maxTs
        } catch (e: Exception) {
            // abaikan, coba lagi di putaran berikutnya
        }
    }

    private fun fmt(ms: Long): String {
        val m = ms / 60000L
        return if (m >= 60L) "${m / 60L}j ${m % 60L}m" else "${m}m"
    }

    private fun updateOverlay() {
        val o = overlay ?: return
        val t = overlayText ?: return

        val hasUsage = Usage.hasAccess(this)
        val pkg: String?
        if (hasUsage) {
            pollForeground()
            pkg = fgPkg
        } else {
            pkg = currentPkg
        }

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
        if (hasUsage) {
            val nowReal = SystemClock.elapsedRealtime()
            if (nowReal - cachedAt > 5000L) {
                cachedUsage = Usage.todayMs(this)
                cachedAt = nowReal
            }
            for (p in target.pkgs) {
                ms += cachedUsage[p] ?: 0L
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
