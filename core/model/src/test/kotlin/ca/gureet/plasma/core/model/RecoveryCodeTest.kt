// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A PIN's recovery code (the owner's #112). */
class RecoveryCodeTest {
    @Test fun aCodeIsTwelveCharactersThatCantBeMixedUp() {
        repeat(200) {
            val code = RecoveryCode.new()
            assertEquals(12, code.length)
            assertTrue(code, code.all { it in RecoveryCode.ALPHABET })
        }
        // No 0 and O, no 1, I and L.
        assertTrue("0O1IL".none { it in RecoveryCode.ALPHABET })
        assertNotEquals(RecoveryCode.new(), RecoveryCode.new())
    }

    @Test fun itShowsInThreeGroups() {
        assertEquals("7KQX-M4TR-9WCH", RecoveryCode.shown("7KQXM4TR9WCH"))
        assertEquals("7KQX-M4", RecoveryCode.shown("7KQXM4"))
    }

    @Test fun typedItIsCapitalsWithoutDashesOrSpaces() {
        assertEquals("7KQXM4TR9WCH", RecoveryCode.typed("7kqx-m4tr 9wch"))
        assertEquals("7KQXM4TR9WCH", RecoveryCode.typed("7KQX-M4TR-9WCH"))
    }
}
