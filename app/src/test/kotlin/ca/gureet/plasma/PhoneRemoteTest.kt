// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import ca.gureet.plasma.channels.NumberReason
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.ChannelGroup
import ca.gureet.plasma.core.data.NumberingSettings
import ca.gureet.plasma.core.data.Programme
import ca.gureet.plasma.live.Lists
import ca.gureet.plasma.live.LiveWorld
import ca.gureet.plasma.player.BehindLive
import ca.gureet.plasma.player.StreamKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** What a phone's Channels and Remote tabs get from the TV (brief 5.21), on the boards' made-up world. */
class PhoneRemoteTest {
    private val now = Instant.parse("2026-10-21T20:41:00Z")

    private fun channel(key: String, number: Int, name: String, group: String, epg: String? = null, hidden: Boolean = false) = Channel(
        id = "home:$key", sourceId = "home", key = key, name = name, number = number, numberReason = NumberReason.Playlist,
        logo = null, groupId = "home:$group", groupName = group, epgKey = epg, catchupDays = null, isRadio = false,
        hidden = hidden, locked = false,
    )

    private val northline = channel("101", 101, "Northline News", "news")
    private val arena = channel("105", 105, "Arena Sports 1", "sports", epg = "home:arena1")
    private val velocity = channel("107", 107, "Velocity Motor", "sports", hidden = true)
    private val courtside = channel("108", 108, "Courtside+", "sports")

    private val match = Programme(
        epgKey = "home:arena1", start = Instant.parse("2026-10-21T19:45:00Z"), end = Instant.parse("2026-10-21T22:00:00Z"),
        title = "Harbour City v Redmoor", subtitle = null, description = null, category = null, icon = null,
        season = null, episode = null, isNew = false,
    )

    private fun world(favorites: List<String> = listOf(courtside.id, velocity.id, arena.id)) = LiveWorld(
        channels = listOf(northline, arena, velocity, courtside),
        groups = listOf(ChannelGroup("home:sports", "home", "Sports", hidden = false), ChannelGroup("home:news", "home", "News", hidden = false), ChannelGroup("home:kids", "home", "Kids", hidden = false)),
        favoriteIds = favorites,
        recentIds = emptyList(),
        programmes = mapOf("home:arena1" to listOf(match)),
        numbering = NumberingSettings(),
        lastWatched = null,
        now = now,
    )

    @Test fun favoritesComeFirstAndCountFromOneLikeOnTheTv() {
        val lists = phoneLists(world(), listId = null, from = 0, playing = arena.id, favorites = "Favorites", allChannels = "All Channels")

        // Favorites, the groups with channels, then All Channels; an empty group stays out.
        assertEquals(listOf("Favorites", "Sports", "News", "All Channels"), lists.lists.map { it.name })
        assertEquals(listOf(2, 2, 1, 3), lists.lists.map { it.count })
        assertEquals(Lists.FAVORITES, lists.list)
        assertTrue(lists.lists.first().countsFromOne)
        // The hidden favorite keeps its place: Arena Sports 1 is 3, as on the TV.
        assertEquals(listOf("1" to "Courtside+", "3" to "Arena Sports 1"), lists.channels.map { it.number to it.name })
        assertEquals(listOf(false, true), lists.channels.map { it.playing })
        assertEquals("Harbour City v Redmoor", lists.channels.last().now)
    }

    @Test fun groupsKeepTheirNumbersAndNoFavoritesOpensTheFirstList() {
        val lists = phoneLists(world(favorites = emptyList()), listId = null, from = 0, playing = null, favorites = "Favorites", allChannels = "All Channels")

        assertEquals("home:sports", lists.list)
        assertEquals(listOf("105", "108"), lists.channels.map { it.number })
        assertEquals(listOf("101"), phoneLists(world(), "home:news", 0, null, "Favorites", "All Channels").channels.map { it.number })
        // A list that's gone opens the first one instead.
        assertEquals(Lists.FAVORITES, phoneLists(world(), "home:gone", 0, null, "Favorites", "All Channels").list)
    }

    @Test fun longListsComeAPageAtATime() {
        val many = (1..450).map { channel("$it", it, "Channel $it", "sports") }
        val world = world().copy(channels = many, favoriteIds = emptyList())

        val second = phoneLists(world, "home:sports", from = 200, playing = null, favorites = "Favorites", allChannels = "All Channels")

        assertEquals(200, second.channels.size)
        assertEquals("201", second.channels.first().number)
        assertEquals(50, phoneLists(world, "home:sports", 400, null, "Favorites", "All Channels").channels.size)
    }

    @Test fun theRemoteTabShowsHowFarBehindLiveThePictureIs() {
        val behind = BehindLive(pictureTime = now.toEpochMilli() - 134_000, since = now.toEpochMilli(), paused = true)

        val playing = phonePlaying(world(), arena.id, behind)!!

        assertEquals("105", playing.number)
        assertEquals("Harbour City v Redmoor", playing.title)
        assertEquals(134L, playing.behindLive)
        assertTrue(playing.paused)
        // 56 of 135 minutes in at live; the picture a little before.
        assertEquals(56f / 135f, playing.live!!, 0.001f)
        assertTrue(playing.progress!! < playing.live!!)
        // At the live edge, nothing's behind.
        assertEquals(0L, phonePlaying(world(), arena.id, null)!!.behindLive)
    }

    @Test fun aProgramFromTheArchiveShowsItsTitleAndAMovieNothing() {
        val fromArchive = phonePlaying(world(), StreamKeys.archive(arena.id, match.start), null)!!

        assertEquals("Harbour City v Redmoor", fromArchive.title)
        assertNull(fromArchive.live)
        assertNull(phonePlaying(world(), StreamKeys.movie("m1"), null))
    }
}
