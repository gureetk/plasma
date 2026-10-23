// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.channels.NumberReason
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.ChannelGroup
import ca.gureet.plasma.core.data.LastWatched
import ca.gureet.plasma.core.data.NumberingSettings
import ca.gureet.plasma.core.data.Programme
import ca.gureet.plasma.core.data.Source
import ca.gureet.plasma.core.data.SourceType
import ca.gureet.plasma.feature.guide.GuideStale
import ca.gureet.plasma.feature.player.PlayerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** The live screens' states from the data (brief 4.7–4.9, 5.2), on the boards' made-up world. */
class LiveStatesTest {
    private val now = Instant.parse("2026-10-21T20:41:00Z")
    private val names = LiveNames(profileName = "Me", allChannels = "All Channels", favorites = "Favorites", recentlyWatched = "Recently Watched")

    private fun channel(key: String, number: Int, name: String, group: String, epg: String? = null, hidden: Boolean = false, locked: Boolean = false) = Channel(
        id = "home:$key", sourceId = "home", key = key, name = name, number = number, numberReason = NumberReason.Playlist,
        logo = null, groupId = "home:$group", groupName = group, epgKey = epg, catchupDays = null, isRadio = false,
        hidden = hidden, locked = locked,
    )

    private val northline = channel("101", 101, "Northline News", "news", epg = "home:northline")
    private val arena = channel("105", 105, "Arena Sports 1", "sports", epg = "home:arena1")
    private val velocity = channel("107", 107, "Velocity Motor", "sports", hidden = true)
    private val courtside = channel("108", 108, "Courtside+", "sports")
    private val combat = channel("110", 110, "Combat Arena", "sports", locked = true)

    private val match = Programme(
        epgKey = "home:arena1", start = Instant.parse("2026-10-21T19:45:00Z"), end = Instant.parse("2026-10-21T22:00:00Z"),
        title = "Harbour City v Redmoor", subtitle = null, description = null, category = null, icon = null,
        season = null, episode = null, isNew = false,
    )
    private val extra = match.copy(start = match.end, end = match.end.plusSeconds(3_600), title = "Match of the Week Extra")

    private fun world(favorites: List<String> = listOf(courtside.id, arena.id), numbering: NumberingSettings = NumberingSettings()) = LiveWorld(
        channels = listOf(northline, arena, velocity, courtside, combat),
        groups = listOf(ChannelGroup("home:sports", "home", "Sports", hidden = false), ChannelGroup("home:news", "home", "News", hidden = false), ChannelGroup("home:music", "home", "Music", hidden = false)),
        favoriteIds = favorites,
        recentIds = emptyList(),
        programmes = mapOf("home:arena1" to listOf(match, extra)),
        numbering = numbering,
        lastWatched = LastWatched(arena.id, "sports"),
        now = now,
    )

    private fun states(unlocked: Set<String> = emptySet()) = LiveStates(names, ZoneOffset.UTC) { it in unlocked }

    @Test fun favoritesCountFromOneAndGroupsKeepTheirNumbers() {
        val state = states().liveTv(world(), selectedGroupId = null)

        assertEquals(listOf("1", "2"), state.channels.getValue(Lists.FAVORITES).map { it.number })
        assertEquals(listOf("105", "107", "108", "110"), state.channels.getValue("home:sports").map { it.number })
        // The last channel's list is where Live TV opens.
        assertEquals("home:sports", state.selectedGroupId)
        assertEquals(arena.id, state.currentChannelId)
    }

    @Test fun emptyProviderGroupsStayOutAndHiddenChannelsAreMarked() {
        val state = states().liveTv(world(), selectedGroupId = null)

        assertEquals(listOf("all", "favorites", "recent", "home:sports", "home:news"), state.groups.map { it.id })
        assertEquals(3, state.groups.first { it.id == "home:sports" }.count)
        assertEquals(setOf(velocity.id), state.hidden)
    }

    @Test fun lockedChannelsShowNoPictureUntilUnlocked() {
        val locked = states().liveTv(world(), null).channels.getValue(Lists.ALL).first { it.id == combat.id }
        assertNull(locked.frame.channelId)

        val unlocked = states(unlocked = setOf(combat.id)).liveTv(world(), null).channels.getValue(Lists.ALL).first { it.id == combat.id }
        assertEquals(combat.id, unlocked.frame.channelId)
    }

    @Test fun nowAndNextComeFromTheGuide() {
        val arenaRow = states().liveTv(world(), null).channels.getValue(Lists.ALL).first { it.id == arena.id }

        assertEquals("Harbour City v Redmoor", arenaRow.nowTitle)
        assertEquals("19:45–22:00", arenaRow.nowRange)
        assertEquals(56f / 135f, arenaRow.progress!!, 0.001f)
    }

    @Test fun thePlayerZapsThroughTheListItWasOpenedFrom() {
        val player = states().player(world(), Lists.FAVORITES, arena.id, PlayerStatus.Playing, sourceName = "Home")!!

        assertEquals(listOf(courtside.id, arena.id), player.channels.map { it.id })
        assertEquals(listOf("1", "2"), player.channels.map { it.number })
        // Opened on a channel that isn't in the list, it zaps through All Channels, hidden ones left out.
        val fromAll = states().player(world(), Lists.FAVORITES, northline.id, PlayerStatus.Playing, sourceName = null)!!
        assertEquals(listOf(northline.id, arena.id, courtside.id, combat.id), fromAll.channels.map { it.id })
    }

    @Test fun theGuideShowsTheDayItWasGivenAndItsGroups() {
        val guide = states().guide(world(), mapOf("home:arena1" to listOf(extra)), loading = false, stale = null, listId = Lists.FAVORITES)

        assertEquals("Favorites", guide.groupName)
        assertEquals(listOf("all", "favorites", "home:sports", "home:news"), guide.groups.map { it.id })
        assertEquals(listOf("1", "2"), guide.channels.map { it.number })
        assertEquals(listOf("Match of the Week Extra"), guide.channels.first { it.id == arena.id }.programs.map { it.title })

        val loading = states().guide(world(), emptyMap(), loading = true, stale = null, listId = "unknown")
        assertTrue(loading.loading)
        assertEquals("All Channels", loading.groupName)
    }

    @Test fun theGuideIsOutOfDateAfterTwoDaysOrWhenItRunsOut() {
        fun source(updated: Instant?) = Source(
            id = "home", type = SourceType.Xtream, name = "Home", server = "http://line.example.org", username = "viewer",
            priority = 0, enabled = true, status = "Active", expiresAt = null, maxConnections = null, activeConnections = null,
            streamFormat = "ts", userAgent = null, channelsRefreshedAt = updated, guideRefreshedAt = updated, lastError = null,
        )

        assertNull(guideStale(emptyList(), null, now))
        assertEquals(GuideStale(null), guideStale(listOf(source(null)), null, now))
        assertNull(guideStale(listOf(source(now.minus(Duration.ofHours(30)))), now.plus(Duration.ofDays(6)), now))
        val old = now.minus(Duration.ofHours(49))
        assertEquals(GuideStale(old), guideStale(listOf(source(old)), now.plus(Duration.ofDays(6)), now))
        val recent = now.minus(Duration.ofHours(2))
        assertEquals(GuideStale(recent), guideStale(listOf(source(recent)), now.plus(Duration.ofHours(6)), now))
    }
}
