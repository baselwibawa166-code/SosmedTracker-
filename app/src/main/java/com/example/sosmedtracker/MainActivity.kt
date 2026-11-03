package com.example.sosmedtracker

import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private class Row(val duration: TextView, val scroll: TextView, val bar: ProgressBar)

    private val rows = ArrayList<Row>()
    private lateinit var statusText: TextView
    private lateinit var btnUsage: Button
    private lateinit var btnAcc: Button
    private lateinit var totalDuration: TextView
    private lateinit var totalScroll: TextView
    private var dp = 1f
    private val matchParent = LinearLayout.LayoutParams.MATCH_PARENT
    private val wrapContent = LinearLayout.LayoutParams.WRAP_CONTENT
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 3000L)
        }
    }

    private fun px(v: Int): Int = (v * dp).toInt()

    private fun card(color: Int): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable()
        bg.setColor(color)
        bg.cornerRadius = px(16).toFloat()
        c.background = bg
        c.setPadding(px(16), px(16), px(16), px(16))
        val lp = LinearLayout.LayoutParams(matchParent, wrapContent)
        lp.bottomMargin = px(12)
        c.layoutParams = lp
        return c
    }

    private fun label(size: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dp = resources.displayMetrics.density

        val dark = Color.parseColor("#0F172A")
        val gray = Color.parseColor("#64748B")

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(px(16), px(16), px(16), px(24))

        // Kartu total
        val totalCard = card(Color.parseColor("#1E293B"))
        val totalLabel = label(13f, Color.parseColor("#CBD5E1"), false)
        totalLabel.text = "Total hari ini"
        totalDuration = label(36f, Color.WHITE, true)
        totalScroll = label(16f, Color.parseColor("#E2E8F0"), false)
        totalCard.addView(totalLabel)
        totalCard.addView(totalDuration)
        totalCard.addView(totalScroll)
        val resetBtn = Button(this)
        resetBtn.text = "Reset hari ini"
        resetBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset hari ini?")
                .setMessage("Durasi dan jumlah scroll hari ini dihitung ulang dari nol.")
                .setPositiveButton("Reset") { _, _ ->
                    Store.resetToday(this)
                    refresh()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
        totalCard.addView(resetBtn)
        root.addView(totalCard)

        // Kartu status izin
        val statusCard = card(Color.WHITE)
        statusText = label(14f, Color.parseColor("#334155"), false)
        btnUsage = Button(this)
        btnUsage.text = "Aktifkan Usage Access"
        btnUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        btnAcc = Button(this)
        btnAcc.text = "Aktifkan Accessibility (hitung scroll)"
        btnAcc.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        statusCard.addView(statusText)
        statusCard.addView(btnUsage)
        statusCard.addView(btnAcc)
        root.addView(statusCard)

        // Kartu per aplikasi
        for (t in Store.TARGETS) {
            val c = card(Color.WHITE)

            val top = LinearLayout(this)
            top.orientation = LinearLayout.HORIZONTAL
            val name = label(17f, dark, true)
            name.text = t.name
            name.layoutParams = LinearLayout.LayoutParams(0, wrapContent, 1f)
            val dur = label(17f, dark, true)
            top.addView(name)
            top.addView(dur)

            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
            bar.max = 100
            bar.progress = 0
            bar.progressTintList = ColorStateList.valueOf(Color.parseColor("#3B82F6"))
            bar.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
            val barLp = LinearLayout.LayoutParams(matchParent, px(8))
            barLp.topMargin = px(10)
            barLp.bottomMargin = px(8)
            bar.layoutParams = barLp

            val sc = label(14f, gray, false)

            c.addView(top)
            c.addView(bar)
            c.addView(sc)
            root.addView(c)
            rows.add(Row(dur, sc, bar))
        }

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#F2F4F7"))
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isServiceOn(): Boolean {
        val s = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return s.contains("$packageName/")
    }

    private fun fmt(ms: Long): String {
        val m = ms / 60000L
        return if (m >= 60L) "${m / 60L}j ${m % 60L}m" else "${m}m"
    }

    private fun refresh() {
        val usageOk = hasUsageAccess()
        val accOk = isServiceOn()

        if (usageOk && accOk) {
            statusText.text = "✓ Semua izin aktif"
        } else {
            statusText.text = "Usage Access: " + (if (usageOk) "aktif" else "belum aktif") +
                "\nAccessibility: " + (if (accOk) "aktif" else "belum aktif")
        }
        btnUsage.visibility = if (usageOk) View.GONE else View.VISIBLE
        btnAcc.visibility = if (accOk) View.GONE else View.VISIBLE

        val usage: Map<String, Long> = if (usageOk) Usage.todayMs(this) else emptyMap()

        val n = Store.TARGETS.size
        val durations = LongArray(n)
        val scrolls = IntArray(n)
        for (i in 0 until n) {
            var ms = 0L
            var sc = 0
            for (p in Store.TARGETS[i].pkgs) {
                ms += usage[p] ?: 0L
                sc += Store.getScroll(this, p)
            }
            durations[i] = ms
            scrolls[i] = sc
        }

        var maxMs = 1L
        for (d in durations) {
            if (d > maxMs) maxMs = d
        }

        var totalMs = 0L
        var totalSc = 0
        for (i in 0 until n) {
            totalMs += durations[i]
            totalSc += scrolls[i]
            val r = rows[i]
            r.duration.text = fmt(durations[i])
            r.scroll.text = "Scroll: " + scrolls[i]
            r.bar.progress = (durations[i] * 100L / maxMs).toInt()
        }

        totalDuration.text = fmt(totalMs)
        totalScroll.text = "" + totalSc + " scroll"
    }
}
