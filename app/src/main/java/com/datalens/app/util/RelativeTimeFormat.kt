package com.datalens.app.util

import java.util.concurrent.TimeUnit

/** "just now", "2 minutes ago", "1 hour ago", "yesterday", … */
object RelativeTimeFormat {

    fun format(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val delta = now - epochMillis
        if (delta < 0) return "just now"
        if (delta < TimeUnit.MINUTES.toMillis(1)) return "just now"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
        if (minutes < 60) return if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
        val hours = TimeUnit.MILLISECONDS.toHours(delta)
        if (hours < 24) return if (hours == 1L) "1 hour ago" else "$hours hours ago"
        val days = TimeUnit.MILLISECONDS.toDays(delta)
        return if (days == 1L) "yesterday" else "$days days ago"
    }
}
