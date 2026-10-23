// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelNumbersTest {
    /** The sample channels' numbers, and Ringside 1's 212 on Sports Extras. */
    private val inUse = setOf(101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 120, 140, 180, 200, 212)

    @Test fun addsAThousandOrMore() {
        assertEquals(1108, ChannelNumbers.moveTo(108, DuplicateRule.AddThousands, inUse))
        assertEquals(2108, ChannelNumbers.moveTo(108, DuplicateRule.AddThousands, inUse + 1108))
    }

    @Test fun takesTheNextFreeNumber() {
        assertEquals(111, ChannelNumbers.moveTo(104, DuplicateRule.NextFree, inUse))
        assertEquals(213, ChannelNumbers.moveTo(212, DuplicateRule.NextFree, inUse))
    }

    @Test fun numbersGivenOutCountAsTaken() {
        val contested = listOf(104, 108, 109, 212)
        assertEquals(listOf(1104, 1108, 1109, 1212), ChannelNumbers.moveAll(contested, DuplicateRule.AddThousands, inUse))
        assertEquals(listOf(111, 112, 113, 213), ChannelNumbers.moveAll(contested, DuplicateRule.NextFree, inUse))
    }
}
