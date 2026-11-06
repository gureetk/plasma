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
    private val time12 = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    /** Holds Settings › Appearance › Clock's choice. */
    interface Choice {
        var value: Boolean
    }

    private class PlainChoice : Choice {
        @Volatile override var value: Boolean = false
    }

    /**
     * Where the clock choice is kept. The app gives one kept as Compose state, as it starts, so a
     * screen showing a time draws it again as soon as the choice changes (#118).
     */
    @Volatile var twelveHourChoice: Choice = PlainChoice()

    /**
     * Settings › Appearance › Clock: 12-Hour ("8:41 PM") instead of 24-Hour ("20:41"), set by the
     * app as the setting changes.
     */
    var twelveHour: Boolean
        get() = twelveHourChoice.value
        set(value) {
            twelveHourChoice.value = value
        }
    private val dayMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val weekdayDay = DateTimeFormatter.ofPattern("EEE d", Locale.US)
    private val longDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.US)
    private val fullDate = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US)

    fun time(t: ZonedDateTime): String = time(t, twelveHour)

    /**
     * [t] in the 12-hour clock or the 24-hour one, as [twelveHour] says: for states made off the
     * main thread, which carry the choice they were made with.
     */
    fun time(t: ZonedDateTime, twelveHour: Boolean): String = t.format(if (twelveHour) time12 else time24)

    private val seconds24 = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
    private val seconds12 = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US)

    /** "20:41:07", or "8:41:07 PM" with the 12-hour clock: Show Freeze Watch's times. */
    fun timeWithSeconds(t: ZonedDateTime): String = t.format(if (twelveHour) seconds12 else seconds24)

    fun range(start: ZonedDateTime, end: ZonedDateTime): String = range(start, end, twelveHour)

    fun range(start: ZonedDateTime, end: ZonedDateTime, twelveHour: Boolean): String = "${time(start, twelveHour)}–${time(end, twelveHour)}"

    fun date(t: ZonedDateTime): String = t.format(dayMonth)

    /** "Wednesday 21 October", for the screensaver. */
    fun longDate(t: ZonedDateTime): String = t.format(longDate)

    /** "12 March 2027", for a subscription's end. */
    fun fullDate(d: java.time.LocalDate): String = d.format(fullDate)

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
