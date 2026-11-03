package com.example.sosmedtracker

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Target(val name: String, val pkgs: List<String>)

object Store {
    // Ubah daftar ini kalau mau menambah/mengurangi aplikasi yang dilacak.
    val TARGETS: List<Target> = listOf(
        Target("Instagram", listOf("com.instagram.android")),
        Target("TikTok", listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")),
        Target("YouTube", listOf("com.google.android.youtube")),
        Target("Facebook", listOf("com.facebook.katana")),
        Target("X (Twitter)", listOf("com.twitter.android")),
        Target("Threads", listOf("com.instagram.barcelona")),
        Target("Reddit", listOf("com.reddit.frontpage"))
    )

    val TRACKED: Set<String> = TARGETS.flatMap { it.pkgs }.toSet()

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("scroll_counts", Context.MODE_PRIVATE)

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun key(pkg: String): String = today() + "_" + pkg

    fun addScroll(ctx: Context, pkg: String) {
        val p = prefs(ctx)
        val k = key(pkg)
        p.edit().putInt(k, p.getInt(k, 0) + 1).apply()
    }

    fun getScroll(ctx: Context, pkg: String): Int = prefs(ctx).getInt(key(pkg), 0)

    /** Hapus hitungan scroll hari ini dan catat waktu reset untuk durasi. */
    fun resetToday(ctx: Context) {
        val e = prefs(ctx).edit()
        for (p in TRACKED) {
            e.remove(key(p))
        }
        e.putLong("reset_" + today(), System.currentTimeMillis())
        e.apply()
    }

    /** Waktu reset terakhir hari ini (0 kalau belum pernah reset). */
    fun resetAt(ctx: Context): Long = prefs(ctx).getLong("reset_" + today(), 0L)
}
