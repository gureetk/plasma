// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.channels.NumberReason
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.ChannelGroup
import ca.gureet.plasma.core.data.NumberingSettings
import ca.gureet.plasma.core.data.Programme
import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingKind
import ca.gureet.plasma.core.data.RecordingStatus
import ca.gureet.plasma.core.data.Reminder
import ca.gureet.plasma.core.data.ReminderRepository
import ca.gureet.plasma.core.model.UpcomingKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** Home's Upcoming Events and Recently Recorded on a source's channels (the owner's #80). */
class HomeRowsLiveTest {
    private val now = Instant.parse("2026-10-21T20:41:00Z")
    private val zone = ZoneOffset.UTC

    private fun channel(key: String, number: Int, name: String, epg: String?, hidden: Boolean = false, locked: Boolean = false) = Channel(
        id = "home:$key", sourceId = "home", key = key, name = name, number = number, numberReason = NumberReason.Playlist,
        logo = null, groupId = "home:all", groupName = "All", epgKey = epg, catchupDays = null, isRadio = false,
        hidden = hidden, locked = locked,
    )

    private val arena = channel("105", 105, "Arena Sports 1", "home:arena1")
    private val arenaHd = channel("905", 905, "Arena Sports 1 HD", "home:arena1")
    private val velocity = channel("107", 107, "Velocity Motor", "home:velocity")
    private val combat = channel("110", 110, "Combat Arena", "home:combat", locked = true)
    private val atlas = channel("120", 120, "Atlas One", "home:atlas")
    private val hidden = channel("130", 130, "Hidden Sports", "home:hidden", hidden = true)

    private fun programme(epg: String, title: String, start: String, end: String, category: String?) = Programme(
        epgKey = epg, start = Instant.parse("2026-10-${start}:00Z"), end = Instant.parse("2026-10-${end}:00Z"), title = title, subtitle = null,
        description = null, category = category, icon = null, season = null, episode = null, isNew = false,
    )

    private val match = programme("home:arena1", "Harbour City v Redmoor", "21T19:45", "21T22:00", "Sports")
    private val extra = programme("home:arena1", "Match of the Week Extra", "21T22:00", "21T23:00", "Sports magazine")
    private val replay = programme("home:velocity", "Endurance Replay", "21T18:00", "21T19:00", "Motorsport")
    private val pitLane = programme("home:velocity", "Pit Lane", "21T21:30", "21T22:00", "Motorsport")
    private val bakers = programme("home:atlas", "Baker's Dozen", "21T21:00", "21T22:00", "Entertainment")
    private val quiz = programme("home:atlas", "Late Quiz", "21T22:00", "21T23:00", "Entertainment")
    private val champions = programme("home:combat", "Night of Champions", "21T20:00", "21T22:00", "Sports")
    private val hiddenMatch = programme("home:hidden", "Hidden Match", "21T20:00", "21T22:00", "Sports")

    private val world = LiveWorld(
        channels = listOf(arena, velocity, combat, atlas, hidden, arenaHd),
        groups = listOf(ChannelGroup("home:all", "home", "All", hidden = false)),
        favoriteIds = listOf(atlas.id, velocity.id, combat.id),
        recentIds = emptyList(),
        programmes = listOf(match, extra, replay, pitLane, bakers, quiz, champions, hiddenMatch).groupBy { it.epgKey }.mapValues { (_, list) -> list.sortedBy { it.start } },
        numbering = NumberingSettings(),
        lastWatched = null,
        now = now,
    )

    private fun reminder(channel: Channel, programme: Programme) =
        Reminder(ReminderRepository.idOf(channel.id, programme.start), channel.id, programme.title, programme.start, programme.end)

    private fun recording(
        channel: Channel,
        title: String,
        start: String,
        end: String,
        status: RecordingStatus,
        file: String? = "/recordings/$title.ts",
        positionMs: Long = 0,
        watched: Boolean = false,
        kind: RecordingKind = RecordingKind.Other,
    ): Recording {
        val from = Instant.parse("2026-10-${start}:00Z")
        return Recording(
            id = ReminderRepository.idOf(channel.id, from), channelId = channel.id, channelName = channel.name, channelLogo = null, title = title,
            subtitle = null, description = null, category = null, image = null, season = null, episode = null, kind = kind,
            start = from, end = Instant.parse("2026-10-${end}:00Z"), padBefore = Duration.ZERO, padAfter = Duration.ZERO, status = status, file = file,
            sizeBytes = 0, recordedFrom = null, recordedTo = null, interruptions = 0, problem = null, keep = false, positionMs = positionMs,
            watched = watched, watchedAt = null,
        )
    }

    private val fightNight = recording(arena, "Fight Night: Main Card", "21T23:30", "22T01:30", RecordingStatus.Scheduled)

    @Test fun upcomingEventsBringsInEachKindOnceSoonestFirst() {
        val events = upcomingEvents(world, listOf(reminder(arena, extra), reminder(hidden, hiddenMatch)), listOf(fightNight), UpcomingKind.entries.toSet(), zone)

        assertEquals(
            listOf("Harbour City v Redmoor", "Baker's Dozen", "Pit Lane", "Match of the Week Extra", "Fight Night: Main Card"),
            events.map { it.title },
        )
        // Arena Sports 1 HD shares Arena Sports 1's guide: its programs show once, on Arena Sports 1.
        assertEquals(listOf(arena.id, atlas.id, velocity.id, arena.id, arena.id), events.map { it.channelId })
        // The reminder and the sport are one program, under the reminder's id.
        assertEquals(ReminderRepository.idOf(arena.id, extra.start), events[3].id)
        assertEquals(listOf(false, false, false, true, false), events.map { it.reminder })
        assertEquals(listOf(false, false, false, false, true), events.map { it.recording })
        // Without its reminder or recording, a favorite's program or sport stays; Fight Night doesn't.
        assertEquals(listOf(true, true, true, true, false), events.map { it.fromGuide })
        assertEquals("Arena Sports 1", events.first().channelName)
        assertEquals(match.start.atZone(zone), events.first().start)
    }

    @Test fun kindsTurnedOffBringNothingIn() {
        val events = upcomingEvents(world, listOf(reminder(arena, extra)), listOf(fightNight), setOf(UpcomingKind.RemindersAndRecordings), zone)
        assertEquals(listOf("Match of the Week Extra", "Fight Night: Main Card"), events.map { it.title })
        assertEquals(listOf(false, false), events.map { it.fromGuide })

        val sport = upcomingEvents(world, emptyList(), emptyList(), setOf(UpcomingKind.LiveSportsAndEvents), zone)
        // A locked channel's sport, and a hidden channel's, stay out.
        assertEquals(listOf("Harbour City v Redmoor", "Pit Lane", "Match of the Week Extra"), sport.map { it.title })
    }

    @Test fun recentlyRecordedHasTheFinishedOnesThatPlayLatestFirst() {
        val recordings = listOf(
            recording(arena, "Tuesday Night Football", "20T19:30", "20T21:40", RecordingStatus.Done, watched = true),
            recording(atlas, "Business Wrap", "20T20:30", "20T21:00", RecordingStatus.Done, kind = RecordingKind.News),
            recording(velocity, "Endurance 24", "17T14:00", "17T20:00", RecordingStatus.Done, positionMs = Duration.ofMinutes(36).toMillis()),
            recording(arena, "Failed Match", "19T19:00", "19T21:00", RecordingStatus.Failed),
            recording(arena, "Gone Match", "18T19:00", "18T21:00", RecordingStatus.Done, file = null),
            fightNight,
            recording(arena, "Harbour City v Redmoor", "21T19:45", "21T22:00", RecordingStatus.Recording),
        )
        val recorded = recentlyRecorded(recordings, world, zone)

        assertEquals(listOf("Business Wrap", "Tuesday Night Football", "Endurance 24"), recorded.map { it.title })
        assertEquals(listOf(true, false, false), recorded.map { it.unwatched })
        // News has its time on its line, as on Recordings.
        assertEquals(listOf(true, false, false), recorded.map { it.news })
        assertNull(recorded[1].progress)
        assertEquals(0.1f, recorded[2].progress!!, 0.001f)
        assertEquals(Duration.ofMinutes(130), recorded[1].length)
        assertEquals(Instant.parse("2026-10-20T20:30:00Z").atZone(zone), recorded[0].aired)
    }
}
