// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import ca.gureet.plasma.channels.NumberReason
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.ChannelGroup
import ca.gureet.plasma.core.data.LastWatched
import ca.gureet.plasma.core.data.NumberingSettings
import ca.gureet.plasma.live.LiveWorld
import ca.gureet.plasma.live.Lists
import ca.gureet.plasma.player.StreamKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Keep Watching in a Small Window (brief 5.20): when it opens, and its channel buttons. */
class SmallWindowTest {
    private val now = Instant.parse("2026-10-21T20:41:00Z")

    private fun channel(key: String, number: Int, name: String, group: String, hidden: Boolean = false) = Channel(
        id = "home:$key", sourceId = "home", key = key, name = name, number = number, numberReason = NumberReason.Playlist,
        logo = null, groupId = "home:$group", groupName = group, epgKey = null, catchupDays = null, isRadio = false,
        hidden = hidden, locked = false,
    )

    private val northline = channel("101", 101, "Northline News", "news")
    private val arena = channel("105", 105, "Arena Sports 1", "sports")
    private val velocity = channel("107", 107, "Velocity Motor", "sports", hidden = true)
    private val courtside = channel("108", 108, "Courtside+", "sports")
    private val lantern = channel("120", 120, "Little Lantern", "kids")

    private fun world(last: LastWatched?) = LiveWorld(
        channels = listOf(northline, arena, velocity, courtside, lantern),
        groups = listOf(
            ChannelGroup("home:news", "home", "News", hidden = false),
            ChannelGroup("home:sports", "home", "Sports", hidden = false),
            ChannelGroup("home:kids", "home", "Kids", hidden = false),
        ),
        favoriteIds = listOf(courtside.id, northline.id),
        recentIds = emptyList(),
        programmes = emptyMap(),
        numbering = NumberingSettings(),
        lastWatched = last,
        now = now,
    )

    @Test fun goesSmallOnTvsThatCanWhileSomethingPlays() {
        assertTrue(SmallWindow.goesSmall(supported = true, wanted = true, playing = arena.id))
        assertFalse("No picture-in-picture", SmallWindow.goesSmall(supported = false, wanted = true, playing = arena.id))
        assertFalse("Set to Never", SmallWindow.goesSmall(supported = true, wanted = false, playing = arena.id))
        assertFalse("Nothing playing", SmallWindow.goesSmall(supported = true, wanted = true, playing = null))
    }

    @Test fun channelButtonsGoThroughTheListLastWatchedPastHiddenChannels() {
        val sports = world(LastWatched(arena.id, "home:sports"))
        // Velocity Motor is hidden.
        assertEquals(courtside.id, SmallWindow.neighbourOf(sports, arena.id, +1))
        // Round the ends.
        assertEquals(arena.id, SmallWindow.neighbourOf(sports, courtside.id, +1))
        assertEquals(courtside.id, SmallWindow.neighbourOf(sports, arena.id, -1))

        val favorites = world(LastWatched(courtside.id, Lists.FAVORITES))
        assertEquals(northline.id, SmallWindow.neighbourOf(favorites, courtside.id, +1))
        assertEquals(northline.id, SmallWindow.neighbourOf(favorites, courtside.id, -1))
    }

    @Test fun aChannelOutsideThatListGoesThroughAllChannels() {
        val sports = world(LastWatched(arena.id, "home:sports"))
        assertEquals(northline.id, SmallWindow.neighbourOf(sports, lantern.id, +1))
        assertEquals(courtside.id, SmallWindow.neighbourOf(sports, lantern.id, -1))
        // Nothing watched yet: number order.
        assertEquals(arena.id, SmallWindow.neighbourOf(world(null), northline.id, +1))
        assertEquals(lantern.id, SmallWindow.neighbourOf(world(null), northline.id, -1))
    }

    @Test fun moviesAndTheArchiveHaveNoChannelButtons() {
        val any = world(null)
        assertNull(SmallWindow.neighbourOf(any, StreamKeys.movie("m1"), +1))
        assertNull(SmallWindow.neighbourOf(any, StreamKeys.archive(arena.id, now.minusSeconds(3_600)), +1))
        assertNull("Not a channel there is", SmallWindow.neighbourOf(any, "home:999", +1))
    }
}
