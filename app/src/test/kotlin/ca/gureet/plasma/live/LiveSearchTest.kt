// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.channels.NumberReason
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.ChannelGroup
import ca.gureet.plasma.core.data.NumberingSettings
import ca.gureet.plasma.core.data.Programme
import ca.gureet.plasma.feature.search.ProgramResultKind
import ca.gureet.plasma.feature.search.TitleResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Search on the source's guide (brief 4.3 and 5.10), on the boards' made-up channels. */
class LiveSearchTest {
    private val now = Instant.parse("2026-10-21T20:41:00Z")

    private fun channel(key: String, number: Int, name: String, epg: String?, catchupDays: Int? = null, hidden: Boolean = false, locked: Boolean = false) = Channel(
        id = "home:$key", sourceId = "home", key = key, name = name, number = number, numberReason = NumberReason.Playlist,
        logo = null, groupId = "home:sports", groupName = "Sports", epgKey = epg, catchupDays = catchupDays, isRadio = false,
        hidden = hidden, locked = locked,
    )

    private val arena = channel("105", 105, "Arena Sports 1", "home:arena1", catchupDays = 7)
    private val arenaHd = channel("905", 905, "Arena Sports 1 HD", "home:arena1")
    private val velocity = channel("107", 107, "Velocity Motor", "home:velocity")
    private val combat = channel("110", 110, "Combat Arena", "home:combat", locked = true)

    private fun programme(epg: String, title: String, start: String, end: String, subtitle: String? = null) = Programme(
        epgKey = epg, start = Instant.parse(start), end = Instant.parse(end), title = title, subtitle = subtitle, description = null,
        category = null, icon = null, season = null, episode = null, isNew = false,
    )

    private val stint = programme("home:velocity", "Endurance 24: Night Stint", "2026-10-21T19:00:00Z", "2026-10-21T23:00:00Z")
    private val match = programme("home:arena1", "Harbour City v Redmoor", "2026-10-21T19:45:00Z", "2026-10-21T22:00:00Z")
    private val fight = programme("home:arena1", "Fight Night: Main Card", "2026-10-21T23:30:00Z", "2026-10-22T01:30:00Z")
    private val tuesday = programme("home:arena1", "Football", "2026-10-20T19:30:00Z", "2026-10-20T21:30:00Z", subtitle = "Tuesday Night")
    private val oldRace = programme("home:velocity", "Night Race Replay", "2026-10-20T18:00:00Z", "2026-10-20T19:00:00Z")
    private val combatNight = programme("home:combat", "Night of Champions", "2026-10-21T20:00:00Z", "2026-10-21T22:00:00Z")
    private val guide = listOf(stint, match, fight, tuesday, oldRace, combatNight)

    private val world = LiveWorld(
        channels = listOf(arena, velocity, combat, arenaHd),
        groups = listOf(ChannelGroup("home:sports", "home", "Sports", hidden = false)),
        favoriteIds = emptyList(),
        recentIds = emptyList(),
        programmes = guide.groupBy { it.epgKey },
        numbering = NumberingSettings(),
        lastWatched = null,
        now = now,
        reminderIds = setOf("${arena.id}@${fight.start.epochSecond}"),
    )

    private val catalog = listOf(
        TitleResult("home:7101", "Neon Tide", null, series = false),
        TitleResult("home:8103", "The Night Desk", null, series = true),
        TitleResult("home:7105", "Night Shift at Polar Station", null, series = false),
    )

    private fun words(query: String) = query.lowercase().split(' ').filter { it.isNotBlank() }

    /** The index, as far as these tests need it: any word of the title or subtitle starting with each word. */
    private val search = LiveSearch(
        { query, from, to ->
            guide.filter { p ->
                val text = "${p.title} ${p.subtitle.orEmpty()}".lowercase().split(' ', ':')
                p.end.isAfter(from) && p.start.isBefore(to) && words(query).all { w -> text.any { it.startsWith(w) } }
            }
        },
        { query -> catalog.filter { t -> words(query).all { w -> t.title.lowercase().split(' ').any { it.startsWith(w) } } } },
    )

    private fun results(query: String) = runBlocking { search.results(world, query, now) }

    @Test fun programsAreLiveComingUpOrInTheArchive() {
        val found = results("night").programs

        assertEquals(listOf("Endurance 24: Night Stint", "Fight Night: Main Card", "Football"), found.map { it.title })
        assertEquals(listOf(ProgramResultKind.Live, ProgramResultKind.Upcoming, ProgramResultKind.CatchUp), found.map { it.kind })
        // Arena Sports 1 HD shares the guide: its programs show once, on Arena Sports 1.
        assertEquals(listOf(velocity.id, arena.id, arena.id), found.map { it.channelId })
        // The reminder set on Fight Night shows.
        assertEquals(listOf(false, true, false), found.map { it.reminder })
    }

    @Test fun localCatchUpFindsWhatItHolds() {
        // Velocity Motor has no archive; local catch-up has held it since yesterday at 17:00 (5.5).
        val since = Instant.parse("2026-10-20T17:00:00Z").toEpochMilli()
        val held = world.copy(local = LocalCatchUpNow(setOf(velocity.id), mapOf(velocity.id to since..now.toEpochMilli())))

        val found = runBlocking { search.results(held, "race", now) }.programs
        assertEquals(listOf("Night Race Replay" to ProgramResultKind.CatchUp), found.map { it.title to it.kind })
        // Without it, yesterday's race can't play.
        assertTrue(results("race").programs.isEmpty())
    }

    @Test fun aChannelsNameFindsWhatsOnItNow() {
        val found = results("arena").programs

        // Arena Sports 1 and its HD twin both have the match on; locked Combat Arena stays out.
        assertEquals(listOf("Harbour City v Redmoor"), found.map { it.title }.distinct())
        assertTrue(found.none { it.channelId == combat.id })
        assertEquals("105", found.first().channelNumber)
    }

    @Test fun moviesAndSeriesComeByNameThoseStartingWithItFirst() {
        assertEquals(listOf("Night Shift at Polar Station", "The Night Desk"), results("night").titles.map { it.title })
        assertEquals(listOf(true), results("desk").titles.map { it.series })
    }

    @Test fun nothingToLookForFindsNothing() {
        assertTrue(results("  ").programs.isEmpty())
        assertTrue(results("zzz").programs.isEmpty())
    }
}
