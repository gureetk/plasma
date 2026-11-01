// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What Settings' choices come to (brief 5.16). */
class SettingsChoicesTest {
    private val gb = 1_000_000_000L

    @Test fun storageLimitIsTheDrivesOrTheTvsOwn() {
        // 50 GB on a USB drive, 8 GB of the TV's own, unless chosen otherwise (4.31).
        assertEquals(50 * gb, SettingsChoices().storageLimitBytes(onTv = false))
        assertEquals(8 * gb, SettingsChoices().storageLimitBytes(onTv = true))

        val chosen = SettingsChoices(mapOf(SettingKeys.STORAGE_LIMIT to 5, SettingKeys.STORAGE_LIMIT_TV to 0))
        assertEquals(500 * gb, chosen.storageLimitBytes(onTv = false))
        assertEquals(2 * gb, chosen.storageLimitBytes(onTv = true))
    }

    @Test fun frozenStreamsRestartAfterFiveSecondsUnlessChosenOtherwise() {
        // The owner's #91: After 5 Seconds, After 10 Seconds or Off, in Settings' order.
        assertEquals(5_000L, SettingsChoices().restartFrozen.millis)
        assertEquals(10_000L, SettingsChoices(mapOf(SettingKeys.RESTART_FROZEN to 1)).restartFrozen.millis)
        assertNull(SettingsChoices(mapOf(SettingKeys.RESTART_FROZEN to 2)).restartFrozen.millis)
    }
}
