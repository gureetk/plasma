// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.channels

import ca.gureet.plasma.core.model.DuplicateRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Numbering as brief 5.2 and its tests in 6.9 describe it, on the boards' channels. */
class ChannelNumbererTest {
    private fun entries(vararg pairs: Pair<String, Int?>) = pairs.map { (key, number) -> PlaylistEntry(key, number) }

    private val home = SourceEntries(
        "home",
        entries("arena1" to 105, "velocity" to 107, "courtside" to 108, "pulse" to 200, "orbit" to null, "atlas" to 120),
    )
    private val backup = SourceEntries(
        "backup",
        // A different channel on 108, and two channels sharing 300 inside one source.
        entries("ringside" to 108, "fairway" to 109, "kids1" to 300, "kids2" to 300, "radio" to null),
    )

    private fun Numbering.of(source: String, key: String) = numbers.getValue(ChannelRef(source, key))

    @Test fun takesThePlaylistsNumbers() {
        val numbering = ChannelNumberer.assign(listOf(home))

        assertEquals(AssignedNumber(105, NumberReason.Playlist), numbering.of("home", "arena1"))
        assertEquals(AssignedNumber(120, NumberReason.Playlist), numbering.of("home", "atlas"))
        // No number: the next free one after the highest in its source.
        assertEquals(AssignedNumber(201, NumberReason.Auto), numbering.of("home", "orbit"))
        assertEquals(6, numbering.newlyNumbered.size)
        assertTrue(numbering.newDuplicates.isEmpty())
    }

    @Test fun theHigherSourceAndTheFirstInPlaylistOrderKeepAContestedNumber() {
        val numbering = ChannelNumberer.assign(listOf(home, backup))

        assertEquals(AssignedNumber(108, NumberReason.Playlist), numbering.of("home", "courtside"))
        assertEquals(AssignedNumber(1108, NumberReason.DuplicateFix, wanted = 108), numbering.of("backup", "ringside"))
        assertEquals(AssignedNumber(300, NumberReason.Playlist), numbering.of("backup", "kids1"))
        assertEquals(AssignedNumber(1300, NumberReason.DuplicateFix, wanted = 300), numbering.of("backup", "kids2"))
        // The highest in the backup source, not counting moved duplicates, is 300.
        assertEquals(AssignedNumber(301, NumberReason.Auto), numbering.of("backup", "radio"))
        assertEquals(listOf(ChannelRef("backup", "ringside"), ChannelRef("backup", "kids2")), numbering.newDuplicates)
    }

    @Test fun movesDuplicatesToTheNextFreeNumberWhenAsked() {
        val numbering = ChannelNumberer.assign(listOf(home, backup), rule = DuplicateRule.NextFree)

        // 109 is Fairway's, so Ringside goes to 110.
        assertEquals(AssignedNumber(110, NumberReason.DuplicateFix, wanted = 108), numbering.of("backup", "ringside"))
        assertEquals(AssignedNumber(301, NumberReason.DuplicateFix, wanted = 300), numbering.of("backup", "kids2"))
        assertEquals(AssignedNumber(302, NumberReason.Auto), numbering.of("backup", "radio"))
    }

    @Test fun numbersAreSticky() {
        val first = ChannelNumberer.assign(listOf(home, backup))

        // The provider renumbers Arena Sports 1, drops Atlas One, and adds a channel on 105.
        val refreshed = SourceEntries(
            "home",
            entries("arena1" to 106, "velocity" to 107, "courtside" to 108, "pulse" to 200, "orbit" to null, "harbour" to 105),
        )
        val second = ChannelNumberer.assign(listOf(refreshed, backup), previous = first.numbers)

        assertEquals(105, second.of("home", "arena1").number)
        assertEquals(201, second.of("home", "orbit").number)
        assertEquals(1108, second.of("backup", "ringside").number)
        assertEquals(AssignedNumber(1105, NumberReason.DuplicateFix, wanted = 105), second.of("home", "harbour"))
        assertEquals(listOf(ChannelRef("home", "harbour")), second.newDuplicates)
        assertEquals(listOf(ChannelRef("home", "harbour")), second.newlyNumbered)
        assertTrue(ChannelRef("home", "atlas") !in second.numbers)
    }

    @Test fun theUsersChoicesWinAndSurviveRefreshes() {
        val first = ChannelNumberer.assign(listOf(home, backup))
        // Keep Ringside on 108 and send Courtside+ to 208 (Swap and Choose Another Number, 4.30).
        val overrides = mapOf(ChannelRef("backup", "ringside") to NumberChoice(108), ChannelRef("home", "courtside") to NumberChoice(208))

        val second = ChannelNumberer.assign(listOf(home, backup), previous = first.numbers, overrides = overrides)

        assertEquals(AssignedNumber(108, NumberReason.User), second.of("backup", "ringside"))
        assertEquals(AssignedNumber(208, NumberReason.User), second.of("home", "courtside"))
        assertTrue(second.newDuplicates.isEmpty())

        val third = ChannelNumberer.assign(listOf(home, backup), previous = second.numbers, overrides = overrides)
        assertEquals(second.numbers, third.numbers)
    }

    @Test fun aChannelThatLosesItsNumberToAChoiceIsMoved() {
        val first = ChannelNumberer.assign(listOf(home))
        val second = ChannelNumberer.assign(
            listOf(home),
            previous = first.numbers,
            overrides = mapOf(ChannelRef("home", "pulse") to NumberChoice(107)),
        )

        assertEquals(AssignedNumber(107, NumberReason.User), second.of("home", "pulse"))
        assertEquals(AssignedNumber(1107, NumberReason.DuplicateFix, wanted = 107), second.of("home", "velocity"))
        assertEquals(listOf(ChannelRef("home", "velocity")), second.newDuplicates)
        assertTrue(second.newlyNumbered.isEmpty())
    }

    @Test fun aNumberChosenOnDuplicateNumbersKeepsItsClash() {
        val first = ChannelNumberer.assign(listOf(home, backup))
        // Keep 1108 for Ringside, and give Kids 2 the number 400.
        val overrides = mapOf(
            ChannelRef("backup", "ringside") to NumberChoice(1108, wanted = 108),
            ChannelRef("backup", "kids2") to NumberChoice(400, wanted = 300),
        )

        val second = ChannelNumberer.assign(listOf(home, backup), previous = first.numbers, overrides = overrides)

        assertEquals(AssignedNumber(1108, NumberReason.User, wanted = 108), second.of("backup", "ringside"))
        assertEquals(AssignedNumber(400, NumberReason.User, wanted = 300), second.of("backup", "kids2"))
        assertEquals(108, second.of("home", "courtside").number)
    }

    @Test fun anotherRuleMovesOnlyWhatNumberingMoved() {
        // Ringside lost 108 and Kids 2 lost 300; Harbour lost 105 to a channel since removed.
        val numbers = listOf(
            ChannelRef("home", "arena1") to AssignedNumber(106, NumberReason.Playlist),
            ChannelRef("home", "courtside") to AssignedNumber(108, NumberReason.Playlist),
            ChannelRef("home", "harbour") to AssignedNumber(1105, NumberReason.DuplicateFix, wanted = 105),
            ChannelRef("backup", "ringside") to AssignedNumber(1108, NumberReason.DuplicateFix, wanted = 108),
            ChannelRef("backup", "fairway") to AssignedNumber(109, NumberReason.Playlist),
            ChannelRef("backup", "kids1") to AssignedNumber(300, NumberReason.Playlist),
            ChannelRef("backup", "kids2") to AssignedNumber(1300, NumberReason.User, wanted = 300),
            ChannelRef("backup", "kids3") to AssignedNumber(2300, NumberReason.DuplicateFix, wanted = 300),
        )

        val moved = ChannelNumberer.moveAgain(numbers, DuplicateRule.NextFree)

        // 109 is Fairway's; Kids 2 keeps the number chosen for it, and Harbour has no clash left.
        assertEquals(
            mapOf(
                ChannelRef("backup", "ringside") to AssignedNumber(110, NumberReason.DuplicateFix, wanted = 108),
                ChannelRef("backup", "kids3") to AssignedNumber(301, NumberReason.DuplicateFix, wanted = 300),
            ),
            moved,
        )
        assertTrue(ChannelNumberer.moveAgain(numbers, DuplicateRule.AddThousands).isEmpty())
    }

    @Test fun numbersInPlaylistOrderAcrossSources() {
        val numbering = ChannelNumberer.assign(listOf(home, backup), source = NumberSource.PlaylistOrder)

        assertEquals((1..11).toList(), numbering.numbers.values.map { it.number }.sorted())
        assertEquals(1, numbering.of("home", "arena1").number)
        assertEquals(6, numbering.of("home", "atlas").number)
        assertEquals(7, numbering.of("backup", "ringside").number)
        assertTrue(numbering.numbers.values.all { it.reason == NumberReason.Auto })

        // A refresh adds a channel at the top: it goes to the end, and nothing else moves.
        val refreshed = SourceEntries("home", entries("harbour" to 1) + home.entries)
        val second = ChannelNumberer.assign(listOf(refreshed, backup), previous = numbering.numbers, source = NumberSource.PlaylistOrder)
        assertEquals(12, second.of("home", "harbour").number)
        assertEquals(1, second.of("home", "arena1").number)
    }

    /** The budget is 300 ms for 1,500 channels from 3 sources on a TV box (brief 6.7); a test machine gets more room. */
    @Test fun numbersThreeSourcesOfFifteenHundredChannelsQuickly() {
        val big = (1..3).map { s ->
            SourceEntries("s$s", (1..1_500).map { PlaylistEntry("c$it", if (it % 10 == 0) null else it) })
        }
        val started = System.nanoTime()
        val numbering = ChannelNumberer.assign(big)
        val millis = (System.nanoTime() - started) / 1_000_000
        assertEquals(4_500, numbering.numbers.values.map { it.number }.toSet().size)
        assertTrue("Took $millis ms", millis < 1_000)
    }
}
