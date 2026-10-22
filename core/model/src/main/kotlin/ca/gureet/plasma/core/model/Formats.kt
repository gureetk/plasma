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

    fun time(t: ZonedDateTime): String = t.format(time24)

    fun range(start: ZonedDateTime, end: ZonedDateTime): String = "${time(start)}–${time(end)}"

    fun date(t: ZonedDateTime): String = t.format(dayMonth)

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

    /** "1 h 19 min left", "18 min left" */
    fun left(d: Duration): String = "${durationLoose(d)} left"

    /** Like [duration] but without zero-padding minutes after hours ("1 h 9 min"), as the boards' "1 h 19 min left". */
    fun durationLoose(d: Duration): String {
        val minutes = d.toMinutes().coerceAtLeast(0)
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }

    /** Thousands separators for counts: "1,284". Never use this for channel numbers. */
    fun count(n: Int): String = String.format(Locale.US, "%,d", n)
}
