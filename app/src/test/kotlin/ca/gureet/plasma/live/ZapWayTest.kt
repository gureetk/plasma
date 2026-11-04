// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.model.FastZap
import org.junit.Assert.assertEquals
import org.junit.Test

/** Fast Zap (brief 5.4.8): the channels kept ready around the one playing. */
class ZapWayTest {
    private val ids = listOf("101", "102", "103", "104", "105")

    @Test fun oneAheadFollowsTheWayZappingWent() {
        val way = ZapWay()

        assertEquals("Up the list, at first", listOf("103"), way.next(ids, "102", FastZap.Ahead))
        assertEquals(listOf("104"), way.next(ids, "103", FastZap.Ahead))
        // A zap back down the list turns the way round.
        assertEquals(listOf("101"), way.next(ids, "102", FastZap.Ahead))
        // A typed number keeps the way it had.
        assertEquals(listOf("103"), way.next(ids, "104", FastZap.Ahead))
    }

    @Test fun theListWrapsRoundAtItsEnds() {
        val way = ZapWay()
        way.next(ids, "104", FastZap.Ahead)

        assertEquals(listOf("101"), way.next(ids, "105", FastZap.Ahead))
        assertEquals(listOf("102"), way.next(ids, "101", FastZap.Ahead))
    }

    @Test fun bothWaysKeepsTheChannelsOnEitherSide() {
        val way = ZapWay()

        assertEquals(listOf("104", "102"), way.next(ids, "103", FastZap.BothWays))
        assertEquals("Two channels have one neighbor", listOf("102"), way.next(listOf("101", "102"), "101", FastZap.BothWays))
    }

    @Test fun offOrAChannelNotInTheListKeepsNothingReady() {
        val way = ZapWay()

        assertEquals(emptyList<String>(), way.next(ids, "103", FastZap.Off))
        assertEquals(emptyList<String>(), way.next(ids, "999", FastZap.Ahead))
        assertEquals(emptyList<String>(), way.next(listOf("101"), "101", FastZap.BothWays))
    }
}
