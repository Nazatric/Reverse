package com.thegadget.app.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Every string formatter in the web app, ported arithmetic-for-arithmetic
 * (`utils/musicLibrary.formatTime`, `utils/link.ago`, `utils/link.duration`,
 * `utils/stats.fmtDuration`, `components/hub/TopBar.formatClock`).
 *
 * JavaScript's `Math.round` rounds ties towards +∞, which is *not* Kotlin's `round`, so [jsRound]
 * implements it exactly.
 */
object GadgetText {

    /** `Math.round` in JavaScript: ties go to +Infinity. */
    fun jsRound(x: Double): Double = floor(x + 0.5)

    /** `formatTime` — `m:ss`, and `0:00` for anything non-finite or negative. */
    fun formatTime(sec: Double): String {
        if (sec.isNaN() || sec.isInfinite() || sec < 0) return "0:00"
        val m = floor(sec / 60).toInt()
        val s = floor(sec % 60).toInt()
        return "$m:${s.toString().padStart(2, '0')}"
    }

    /** `ago` — relative time used by games, homies and chat. */
    fun ago(ts: Long?, now: Long): String {
        if (ts == null || ts == 0L) return "never"
        val s = max(1L, jsRound((now - ts) / 1000.0).toLong())
        if (s < 60) return "just now"
        val m = jsRound(s / 60.0).toLong()
        if (m < 60) return "${m}m ago"
        val h = jsRound(m / 60.0).toLong()
        if (h < 24) return "${h}h ago"
        val d = jsRound(h / 24.0).toLong()
        if (d < 30) return "${d}d ago"
        // `new Date(ts).toLocaleDateString()` — the platform's short date.
        return AbsoluteDate.format(ts)
    }

    /** `duration` — used by the now-playing footer and playlist headers. */
    fun duration(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        if (h > 0) return "${h}h ${m}m"
        if (m > 0) return "${m}m"
        return "${sec}s"
    }

    /** `fmtDuration` — account stats and top songs. */
    fun fmtDuration(sec: Double): String {
        if (sec < 60) return "${floor(sec).toInt()}s"
        if (sec < 3600) return "${floor(sec / 60).toInt()}m"
        val h = floor(sec / 3600).toInt()
        val m = jsRound((sec % 3600) / 60.0).toInt()
        return if (m != 0) "${h}h ${m}m" else "${h}h"
    }

    /** `formatClock` — the status pill. `4:20PM` / `16:20`. */
    fun formatClock(hour: Int, minute: Int, hour24: Boolean): String {
        val mm = minute.toString().padStart(2, '0')
        if (hour24) return "${hour.toString().padStart(2, '0')}:$mm"
        val h12 = hour % 12
        return "${if (h12 == 0) 12 else h12}:$mm${if (hour >= 12) "PM" else "AM"}"
    }

    /** Milliseconds until the next minute boundary (+30 ms), as `useClock` schedules it. */
    fun msToNextMinute(second: Int, millisecond: Int): Long =
        (60_000 - (second * 1000 + millisecond) + 30).toLong()

    /** `titleFromFile` — file name to track title. */
    fun titleFromFile(name: String): String =
        name.substringBeforeLast('.', name).replace(Regex("_+"), " ").trim()

    /** `formatTime` uses `(sec / 60)` flooring; kept for parity in tests. */
    fun isSameMinute(a: Long, b: Long): Boolean = abs(a / 60_000 - b / 60_000) < 1
}

/** `toLocaleDateString()` needs a platform formatter; kept behind a hook so the core stays pure. */
interface AbsoluteDateFormatter {
    fun format(epochMillis: Long): String
}

object AbsoluteDate {
    /** Default is an ISO-ish date; Android installs a locale-aware formatter at startup. */
    var formatter: AbsoluteDateFormatter = object : AbsoluteDateFormatter {
        override fun format(epochMillis: Long): String {
            val days = epochMillis / 86_400_000L
            return "day $days"
        }
    }

    fun format(epochMillis: Long): String = formatter.format(epochMillis)
}
