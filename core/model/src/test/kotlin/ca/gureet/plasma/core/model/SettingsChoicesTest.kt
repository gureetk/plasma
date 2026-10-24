// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import org.junit.Assert.assertEquals
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
}
