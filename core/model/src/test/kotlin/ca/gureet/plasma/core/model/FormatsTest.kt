// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatsTest {
    private val at = ZonedDateTime.of(2026, 10, 21, 20, 41, 0, 0, ZoneOffset.UTC)

    @Test fun times() {
        assertEquals("20:41", Formats.time(at))
        assertEquals("19:45–22:00", Formats.range(at.withHour(19).withMinute(45), at.withHour(22).withMinute(0)))
        assertEquals("Wed 21 Oct", Formats.date(at))
    }

    @Test fun durations() {
        assertEquals("2 h 14 min", Formats.duration(Duration.ofMinutes(134)))
        assertEquals("58 min", Formats.duration(Duration.ofMinutes(58)))
        assertEquals("1 h 02 min", Formats.duration(Duration.ofMinutes(62)))
        assertEquals("1 h 19 min left", Formats.left(Duration.ofMinutes(79)))
        assertEquals("1 h 02 min left", Formats.left(Duration.ofMinutes(62)))
        assertEquals("18 min left", Formats.left(Duration.ofMinutes(18)))
        assertEquals("1,284", Formats.count(1284))
    }
}
