// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Setting the TV's screensaver with its own commands (brief 5.14). */
class ScreensaverSwitchTest {
    /** A TV's secure settings, as its `settings` command reads and writes them. */
    private class FakeSettings(vararg start: Pair<String, String>, private val writable: Boolean = true) {
        val values = mutableMapOf(*start)
        val ran = ArrayList<String>()

        fun run(command: String): String {
            ran += command
            val words = command.split(' ')
            return when (words.take(3)) {
                listOf("settings", "get", "secure") -> (values[words[3]] ?: "null") + "\n"
                listOf("settings", "put", "secure") -> {
                    if (writable) values[words[3]] = words[4]
                    ""
                }
                listOf("settings", "delete", "secure") -> {
                    if (writable) values.remove(words[3])
                    "Deleted 1 rows\n"
                }
                else -> "unknown\n"
            }
        }
    }

    @Test fun plasmaBecomesTheScreensaverAndTheOldOneIsKept() {
        val tv = FakeSettings(ScreensaverSwitch.COMPONENTS to BACKDROP, ScreensaverSwitch.ENABLED to "0")
        val result = ScreensaverSwitch(tv::run).use(PLASMA)

        assertEquals(ScreensaverResult.Done(BACKDROP), result)
        assertEquals(PLASMA, tv.values[ScreensaverSwitch.COMPONENTS])
        assertEquals("1", tv.values[ScreensaverSwitch.ENABLED])
    }

    @Test fun aTvWithNoneSetHasNothingToPutBack() {
        val tv = FakeSettings()
        assertEquals(ScreensaverResult.Done(null), ScreensaverSwitch(tv::run).use(PLASMA))
        // Already Plasma: nothing of its own to put back either.
        assertEquals(ScreensaverResult.Done(null), ScreensaverSwitch(tv::run).use(PLASMA))
    }

    @Test fun aTvThatDoesntTakeItSaysWhatItHas() {
        val tv = FakeSettings(ScreensaverSwitch.COMPONENTS to BACKDROP, writable = false)
        assertEquals(ScreensaverResult.Failed(BACKDROP), ScreensaverSwitch(tv::run).use(PLASMA))
    }

    @Test fun restorePutsTheOldOneBackOrTheDefault() {
        val tv = FakeSettings(ScreensaverSwitch.COMPONENTS to PLASMA)
        assertTrue(ScreensaverSwitch(tv::run).restore(BACKDROP))
        assertEquals(BACKDROP, tv.values[ScreensaverSwitch.COMPONENTS])

        assertTrue(ScreensaverSwitch(tv::run).restore(null))
        assertEquals("settings delete secure ${ScreensaverSwitch.COMPONENTS}", tv.ran[tv.ran.size - 2])
        assertFalse(ScreensaverSwitch.COMPONENTS in tv.values)
    }

    private companion object {
        const val PLASMA = "ca.gureet.plasma.dev/ca.gureet.plasma.PlasmaDream"
        const val BACKDROP = "com.google.android.apps.tv.dreamx/.service.Backdrop"
    }
}
