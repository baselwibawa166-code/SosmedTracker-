package com.example.sosmedtracker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

object Usage {

    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            ctx.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfDay(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** Total waktu di foreground per package sejak 00:00 (atau sejak reset terakhir), dalam ms. */
    fun todayMs(ctx: Context): Map<String, Long> {
        val total = HashMap<String, Long>()
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = maxOf(startOfDay(), Store.resetAt(ctx))
        val events = usm.queryEvents(start, end) ?: return total
        val e = UsageEvents.Event()
        val resumedAt = HashMap<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            if (!Store.TRACKED.contains(pkg)) continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    resumedAt[pkg] = e.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val s = resumedAt.remove(pkg)
                    if (s != null) {
                        total[pkg] = (total[pkg] ?: 0L) + (e.timeStamp - s)
                    }
                }
            }
        }
        // Aplikasi yang masih terbuka sekarang.
        for ((pkg, s) in resumedAt) {
            total[pkg] = (total[pkg] ?: 0L) + (end - s)
        }
        return total
    }
}
