// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.channels.NumberReason
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.ChannelLineup
import ca.gureet.plasma.core.data.Source
import ca.gureet.plasma.core.data.SourceType
import ca.gureet.plasma.core.model.DuplicateRule
import ca.gureet.plasma.feature.settings.KeepReason
import ca.gureet.plasma.feature.settings.MergedChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Duplicate Numbers (brief 4.30) from real channels: Home Portal, the primary, and Sports Extras. */
class DuplicateNumbersStateTest {
    private fun source(id: String, name: String, priority: Int, type: SourceType = SourceType.Xtream) = Source(
        id = id, type = type, name = name, server = "http://$id.example.org/", username = "viewer", priority = priority,
        enabled = true, status = "Active", expiresAt = null, maxConnections = 2, activeConnections = 0, streamFormat = "ts",
        userAgent = null, channelsRefreshedAt = null, guideRefreshedAt = null, lastError = null, serverTimeZone = null,
        outputFormats = listOf("ts"),
    )

    private val sources = listOf(source("home", "Home Portal", 0, SourceType.Stalker), source("extras", "Sports Extras", 1, SourceType.M3u))

    private fun channel(
        id: String,
        name: String,
        number: Int,
        reason: NumberReason = NumberReason.Playlist,
        wanted: Int? = null,
        position: Int = 0,
        candidates: List<String> = listOf(id),
    ) = Channel(
        id = id, sourceId = id.substringBefore(':'), key = id.substringAfter(':'), name = name, number = number, numberReason = reason,
        logo = null, groupId = null, groupName = null, epgKey = null, catchupDays = null, isRadio = false, hidden = false, locked = false,
        candidates = candidates, wantedNumber = wanted, position = position,
    )

    private val arena = channel("home:105", "Arena Sports 1", 105, candidates = listOf("home:105", "extras:7105"))
    private val courtside = channel("home:108", "Courtside+", 108)
    private val extrasArena = channel("extras:7105", "Arena Sports 1", 1105, NumberReason.DuplicateFix, wanted = 105)
    private val classics = channel("extras:7108", "Courtside Classics", 1108, NumberReason.DuplicateFix, wanted = 108, position = 1)
    private val ringside1 = channel("extras:7211", "Ringside 1", 212, position = 2)
    private val ringside2 = channel("extras:7212", "Ringside 2", 1212, NumberReason.DuplicateFix, wanted = 212, position = 3)

    private fun lineup(vararg listed: Channel) = ChannelLineup(listed.toList(), listed.filter { it.id != extrasArena.id })

    @Test fun movedChannelsTheListsShowAreConflictsWithWhoKeptTheirNumber() {
        val state = duplicateNumbersState(lineup(arena, courtside, extrasArena, classics, ringside1, ringside2), sources, DuplicateRule.AddThousands)

        // Extras' Arena Sports 1 is merged into Home's, so its clash isn't listed.
        assertEquals(listOf("extras:7108", "extras:7212"), state.conflicts.map { it.id })
        val first = state.conflicts.first()
        assertEquals("home:108", first.keeperId)
        assertEquals(108, first.number)
        assertEquals(1108, first.newNumber)
        assertEquals("Courtside+", first.keeper.name)
        assertEquals("Home Portal", first.keeper.source)
        assertEquals("Sports Extras", first.mover.source)
        assertEquals(KeepReason.Primary, first.reason)
        assertFalse(first.chosen)
        assertEquals(2, first.uses)
        assertEquals(KeepReason.PlaylistOrder, state.conflicts.last().reason)
        assertEquals(listOf(MergedChannel("home:105", 105, "Arena Sports 1", null, listOf("Home Portal", "Sports Extras"))), state.merged)
        assertEquals(1, state.mergedCount)
    }

    @Test fun theNumbersTheRuleGaveAreFreeForTheRule() {
        val state = duplicateNumbersState(lineup(arena, courtside, extrasArena, classics, ringside1, ringside2), sources, DuplicateRule.AddThousands)

        assertEquals("Courtside+", state.owner(108))
        // A merged-away channel still has its number.
        assertEquals("Arena Sports 1", state.numbers[1105])
        assertNull(state.numbers[1108])
        assertEquals("Courtside Classics", state.owner(1108))
        assertEquals(listOf(109, 213), state.withRule(DuplicateRule.NextFree).conflicts.map { it.newNumber })
    }

    @Test fun chosenAndSwappedNumbersSaySo() {
        // Swapped: Classics has 108, and Courtside+ 1108 because of it. Ringside 2 kept 1212.
        val swappedClassics = classics.copy(number = 108, numberReason = NumberReason.User)
        val movedCourtside = courtside.copy(number = 1108, numberReason = NumberReason.User, wantedNumber = 108)
        val keptRingside = ringside2.copy(numberReason = NumberReason.User)

        val state = duplicateNumbersState(lineup(arena, movedCourtside, swappedClassics, ringside1, keptRingside), sources, DuplicateRule.AddThousands)

        val swapped = state.conflicts.first { it.number == 108 }
        assertEquals("home:108", swapped.id)
        assertEquals("Courtside Classics", swapped.keeper.name)
        assertEquals(KeepReason.Swapped, swapped.reason)
        assertTrue(swapped.chosen)
        assertTrue(state.conflicts.first { it.number == 212 }.chosen)
        assertEquals(1108, state.numbers.entries.first { it.value == "Courtside+" }.key)
    }

    @Test fun aClashSaysHowManyWantedTheNumberAndWhoHadItFirst() {
        val ringside3 = channel("extras:7213", "Ringside 3", 2212, NumberReason.DuplicateFix, wanted = 212, position = 4)
        // Home's channel came after Extras' had 300, when Extras was the primary source.
        val late = channel("home:300", "Late Arrival", 1300, NumberReason.DuplicateFix, wanted = 300, position = 5)
        val early = channel("extras:7300", "Early Bird", 300, position = 9)

        val state = duplicateNumbersState(lineup(arena, courtside, ringside1, ringside2, ringside3, early, late), sources, DuplicateRule.AddThousands)

        assertEquals(listOf(3, 3), state.conflicts.filter { it.number == 212 }.map { it.uses })
        assertEquals(KeepReason.HadItFirst, state.conflicts.single { it.number == 300 }.reason)
    }

    @Test fun aClashNobodyElseHasTheNumberForIsGone() {
        // Courtside+ left the playlist: Courtside Classics keeps 1108, and there's nothing to review.
        val state = duplicateNumbersState(lineup(arena, classics), sources, DuplicateRule.AddThousands)

        assertTrue(state.conflicts.isEmpty())
    }
}
