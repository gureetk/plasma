// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import ca.gureet.plasma.core.model.UpcomingReason.Favorite
import ca.gureet.plasma.core.model.UpcomingReason.Recording
import ca.gureet.plasma.core.model.UpcomingReason.Reminder
import ca.gureet.plasma.core.model.UpcomingReason.Sports
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's Upcoming Events (the owner's #80): what it brings in, and in what order. */
class UpcomingEventsTest {
    private val now: Instant = Instant.parse("2026-10-21T20:41:00Z")

    private fun at(time: String, day: Int = 21): Instant = Instant.parse("2026-10-${day}T$time:00Z")

    private fun program(channel: Int, title: String, start: Instant, end: Instant, reason: UpcomingReason) =
        UpcomingCandidate("ch$channel@${start.epochSecond}", "ch$channel", title, start, end, reason, channel)

    @Test fun eachKindHasItsOwnWindow() {
        val candidates = listOf(
            // A reminder within the day, and one too far ahead.
            program(120, "Baker's Dozen", at("21:00"), at("22:00"), Reminder),
            program(120, "Sunday Brunch", at("10:00", day = 23), at("11:00", day = 23), Reminder),
            // A recording being made now, and one tonight.
            program(105, "Harbour City v Redmoor", at("19:45"), at("22:00"), Recording),
            program(105, "Fight Night: Main Card", at("00:30", day = 22), at("03:00", day = 22), Recording),
            // On a favorite: within the hour counts, on now and later don't.
            program(101, "The World Tonight", at("21:00"), at("22:00"), Favorite),
            program(101, "Evening Bulletin", at("20:30"), at("21:00"), Favorite),
            program(102, "Meridian Debate", at("21:45"), at("22:30"), Favorite),
            // Sport: on now, or within two hours; not later, and not once it's over.
            program(108, "City Lights v Northport", at("20:30"), at("23:00"), Sports),
            program(107, "Endurance 24: Night Stint, Part 2", at("22:00"), at("23:30"), Sports),
            program(106, "Late Match", at("23:00"), at("23:59"), Sports),
            program(103, "Sports Desk", at("19:00"), at("20:00"), Sports),
        )
        assertEquals(
            listOf("Harbour City v Redmoor", "City Lights v Northport", "Baker's Dozen", "The World Tonight", "Endurance 24: Night Stint, Part 2", "Fight Night: Main Card"),
            UpcomingEvents.pick(candidates, now).map { it.title },
        )
    }

    @Test fun aProgramShowsOnceWithEveryReason() {
        val start = at("22:00")
        val candidates = listOf(
            program(105, "Match of the Week Extra", start, at("23:00"), Sports),
            program(105, "Match of the Week Extra", start, at("23:00"), Reminder),
        )
        val events = UpcomingEvents.pick(candidates, now)
        assertEquals(1, events.size)
        assertEquals(setOf(Sports, Reminder), events.single().reasons)
        assertTrue(events.single().reminder)
        assertFalse(events.single().recording)
        assertFalse(events.single().isOn(now))
    }

    @Test fun programsStartingTogetherPutRemindersAndRecordingsFirstThenChannelNumbers() {
        val nine = at("21:00")
        val candidates = listOf(
            program(180, "Rivers of the North", nine, at("22:00"), Favorite),
            program(101, "The World Tonight", nine, at("22:00"), Favorite),
            program(120, "Baker's Dozen", nine, at("22:00"), Favorite),
            program(120, "Baker's Dozen", nine, at("22:00"), Reminder),
        )
        assertEquals(listOf("Baker's Dozen", "The World Tonight", "Rivers of the North"), UpcomingEvents.pick(candidates, now).map { it.title })
    }

    @Test fun kindsTurnedOffBringNothingIn() {
        val candidates = listOf(
            program(120, "Baker's Dozen", at("21:00"), at("22:00"), Reminder),
            program(108, "City Lights v Northport", at("20:30"), at("23:00"), Sports),
            program(101, "The World Tonight", at("21:00"), at("22:00"), Favorite),
        )
        val kinds = setOf(UpcomingKind.StartingSoonOnFavorites)
        assertEquals(listOf("The World Tonight"), UpcomingEvents.pick(candidates, now, kinds).map { it.title })
        assertEquals(emptyList<UpcomingEvent>(), UpcomingEvents.pick(candidates, now, emptySet()))
    }

    @Test fun theRowStopsAtItsLimit() {
        val candidates = (1..30).map { i -> program(100 + i, "Program $i", now.plus(Duration.ofMinutes(i.toLong())), now.plus(Duration.ofMinutes(60L + i)), Reminder) }
        val events = UpcomingEvents.pick(candidates, now)
        assertEquals(UpcomingEvents.LIMIT, events.size)
        assertEquals("Program 1", events.first().title)
        assertEquals("Program 20", events.last().title)
    }

    @Test fun sportIsWhatTheGuidesCategorySays() {
        for (category in listOf("Sports", "sport", "Sports event", "Motorsport", "Football", "Tennis", "Deportes", "Desporto", "E-Sports", "Horse Racing")) {
            assertTrue(category, UpcomingEvents.isSports(category))
        }
        for (category in listOf(null, "", "News", "Movie", "Documentary", "Transport", "Passport Control", "Children's")) {
            assertFalse(category.toString(), UpcomingEvents.isSports(category))
        }
    }
}
