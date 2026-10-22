// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Time and duration formats from the brief, section 2.10: 24-hour "20:41", ranges with an en dash
 * "19:45–22:00", durations "2 h 14 min", "58 min", "1 h 02 min left", dates "Wed 21 Oct".
 */
object Formats {
    private val time24 = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val dayMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val weekdayDay = DateTimeFormatter.ofPattern("EEE d", Locale.US)
    private val longDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.US)

    fun time(t: ZonedDateTime): String = t.format(time24)

    fun range(start: ZonedDateTime, end: ZonedDateTime): String = "${time(start)}–${time(end)}"

    fun date(t: ZonedDateTime): String = t.format(dayMonth)

    /** "Wednesday 21 October", for the screensaver. */
    fun longDate(t: ZonedDateTime): String = t.format(longDate)

    /** "Thu 22", for the guide's day tabs. */
    fun weekdayAndDay(d: java.time.LocalDate): String = d.format(weekdayDay)

    /** "2 h 14 min", "58 min", "1 h" */
    fun duration(d: Duration): String {
        val minutes = d.toMinutes().coerceAtLeast(0)
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h ${"%02d".format(Locale.US, m)} min"
        }
    }

    /** "1 h 02 min left", "18 min left" */
    fun left(d: Duration): String = "${duration(d)} left"

    /** Thousands separators for counts: "1,284". Never use this for channel numbers. */
    fun count(n: Int): String = String.format(Locale.US, "%,d", n)

    /**
     * Storage in decimal units, as drives are sold: "212 GB", "3.1 TB", "640 MB". Gigabytes are
     * whole; terabytes keep one decimal unless it's zero.
     */
    fun size(bytes: Long): String {
        val tb = bytes / 1e12
        val gb = bytes / 1e9
        return when {
            tb >= 1 -> {
                val tenths = Math.round(tb * 10)
                if (tenths % 10 == 0L) "${tenths / 10} TB" else String.format(Locale.US, "%.1f TB", tenths / 10.0)
            }
            gb >= 1 -> "${Math.round(gb)} GB"
            else -> "${Math.round(bytes / 1e6)} MB"
        }
    }
}
