// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.channels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lists, number keys and zapping (brief 5.2), with the boards' channels. */
class TuneListTest {
    private val northline = ListChannel("northline", 101)
    private val arena1 = ListChannel("arena1", 105)
    private val velocity = ListChannel("velocity", 107)
    private val courtside = ListChannel("courtside", 108, hidden = true)
    private val wildframe = ListChannel("wildframe", 180)

    private val all = TuneList("all", listOf(northline, arena1, velocity, courtside, wildframe))
    private val favorites = TuneList("favorites", listOf(wildframe, northline, velocity, courtside), countsFromOne = true)
    private val sports = TuneList("sports", listOf(arena1, velocity, courtside))

    @Test fun numbersFollowTheList() {
        assertEquals(105, all.numberOf("arena1"))
        assertEquals(1, favorites.numberOf("wildframe"))
        assertEquals(3, favorites.numberOf("velocity"))
        assertEquals(107, sports.numberOf("velocity"))
        assertNull(favorites.numberOf("arena1"))
    }

    @Test fun findsHiddenChannelsSoTypingOneCanOfferUnhide() {
        assertEquals(courtside, all.find(108))
        assertEquals(courtside, favorites.find(4))
        assertNull(all.find(999))
    }

    @Test fun zappingWrapsAndSkipsHiddenChannels() {
        assertEquals(velocity, all.neighbor("arena1", +1))
        assertEquals(wildframe, all.neighbor("velocity", +1))
        assertEquals(northline, all.neighbor("wildframe", +1))
        assertEquals(wildframe, all.neighbor("northline", -1))
        assertEquals(arena1, sports.neighbor("velocity", +1))
        assertEquals(arena1, all.neighbor("velocity", -1))
        assertEquals(wildframe, all.neighbor("arena1", +2))
        // From outside the list, +1 starts at the top and −1 at the end.
        assertEquals(northline, all.neighbor("somewhere-else", +1))
        assertEquals(wildframe, all.neighbor("somewhere-else", -1))
        assertNull(TuneList("empty", listOf(courtside)).neighbor("courtside", +1))
    }

    @Test fun typedNumbersTryTheCurrentListFirst() {
        // In Favorites, 3 is Velocity Motor; 105 isn't a Favorites number, so it's the full list's.
        assertEquals(TypedChannel(velocity, favorites), NumberEntry.find(3, favorites, all))
        assertEquals(TypedChannel(arena1, all), NumberEntry.find(105, favorites, all))
        // A group that keeps full-list numbers, and Full List Only, use the full list.
        assertEquals(TypedChannel(arena1, all), NumberEntry.find(105, sports, all))
        assertNull(NumberEntry.find(3, sports, all))
        assertNull(NumberEntry.find(3, favorites, all, NumberKeys.FullListOnly))
        assertNull(NumberEntry.find(999, favorites, all))
    }
}
