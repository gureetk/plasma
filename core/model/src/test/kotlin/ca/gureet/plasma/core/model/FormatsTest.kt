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
        assertEquals("Wednesday 21 October", Formats.longDate(at))
        assertEquals("12 March 2027", Formats.fullDate(java.time.LocalDate.of(2027, 3, 12)))
    }

    @Test fun theClockChoiceIsKeptWhereTheAppSays() {
        // The app keeps it as Compose state, so times on screen change at once (#118).
        val kept = Formats.twelveHourChoice
        val choice = object : Formats.Choice {
            var reads = 0
            var held = false
            override var value: Boolean
                get() = held.also { reads++ }
                set(value) {
                    held = value
                }
        }
        try {
            Formats.twelveHourChoice = choice
            Formats.twelveHour = true
            assertEquals("8:41 PM", Formats.time(at))
            assertEquals(true, choice.reads > 0)
            // A state made with the choice it carries writes it, whatever the choice is now.
            assertEquals("20:41", Formats.time(at, twelveHour = false))
            assertEquals("7:45 PM–10:00 PM", Formats.range(at.withHour(19).withMinute(45), at.withHour(22).withMinute(0)))
        } finally {
            Formats.twelveHourChoice = kept
        }
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

    @Test fun sizes() {
        assertEquals("212 GB", Formats.size(212_000_000_000))
        assertEquals("500 GB", Formats.size(500_000_000_000))
        assertEquals("3.1 TB", Formats.size(3_100_000_000_000))
        assertEquals("2 TB", Formats.size(2_000_000_000_000))
        assertEquals("640 MB", Formats.size(640_000_000))
    }

    @Test fun titlesSortWithoutTheirArticle() {
        assertEquals(listOf("Deep Field", "The Last Lighthouse", "A Night Out"), listOf("The Last Lighthouse", "A Night Out", "Deep Field").sortedBy { Titles.sortKey(it) })
    }
}
