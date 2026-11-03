package com.example.sosmedtracker

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var list: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 3000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        status = TextView(this)
        status.textSize = 14f

        val btnUsage = Button(this)
        btnUsage.text = "1. Aktifkan Usage Access"
        btnUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        val btnAcc = Button(this)
        btnAcc.text = "2. Aktifkan Accessibility (hitung scroll)"
        btnAcc.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        list = TextView(this)
        list.textSize = 18f
        list.setPadding(0, pad, 0, 0)

        root.addView(status)
        root.addView(btnUsage)
        root.addView(btnAcc)
        root.addView(list)

        val scroll = ScrollView(this)
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
        status.text = "Usage Access: " + (if (usageOk) "AKTIF" else "belum aktif") +
            "\nAccessibility: " + (if (accOk) "AKTIF" else "belum aktif")

        val usage: Map<String, Long> = if (usageOk) Usage.todayMs(this) else emptyMap()
        val sb = StringBuilder("Hari ini\n\n")
        for (t in Store.TARGETS) {
            var ms = 0L
            var sc = 0
            for (p in t.pkgs) {
                ms += usage[p] ?: 0L
                sc += Store.getScroll(this, p)
            }
            sb.append(t.name)
                .append("\n    Durasi: ").append(fmt(ms))
                .append("   Scroll: ").append(sc)
                .append("\n\n")
        }
        list.text = sb.toString()
    }
}
