// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import ca.gureet.plasma.core.model.QrMatrix
import ca.gureet.plasma.feature.settings.ReplaceStatus
import ca.gureet.plasma.pairing.PairingInvite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replace Google TV Home's steps from what Plasma sees of the TV (brief 4.24). */
class HomeReplacerTest {
    @Test fun theStepsFollowWhatTheTvHasDone() {
        assertEquals(1, ReplaceProgress().step)
        // Wireless debugging on: pairing next.
        assertEquals(2, ReplaceProgress(connectPort = 41285).step)
        // Paired, or the older network debugging, which needs no pairing: replacing next.
        assertEquals(3, ReplaceProgress(connectPort = 41285, paired = true).step)
        assertEquals(3, ReplaceProgress(networkDebugging = true).step)
        assertEquals(4, ReplaceProgress(connectPort = 41285, paired = true, replaced = true).step)
    }

    @Test fun boardTwentyFourShowsWhatsKnown() {
        val qr = QrMatrix(1, BooleanArray(1))
        val progress = ReplaceProgress(
            connectPort = 41285,
            pairingPort = 37115,
            address = "192.168.1.42",
            phone = PairingInvite("192.168.1.42:8485", "7KQ4MZ", "http://192.168.1.42:8485/adb?code=7KQ4MZ", qr),
            status = ReplaceStatus.WrongCode,
        )
        val state = replaceHomeState(progress, HOME, LAUNCHERS, overrideOn = true)
        assertEquals(2, state.step)
        assertEquals("192.168.1.42", state.address)
        assertEquals(37115, state.pairingPort)
        assertEquals(41285, state.connectPort)
        // The code is only ever on the TV's screen.
        assertNull(state.pairingCode)
        assertEquals(8485, state.phonePort)
        assertEquals(qr, state.qr)
        assertEquals(ReplaceStatus.WrongCode, state.status)
        assertEquals(LAUNCHERS, state.launcherPackages)
        assertTrue(state.overrideOn)
    }

    @Test fun withoutAnAddressTheCommandsSaySo() {
        val state = replaceHomeState(ReplaceProgress(), HOME, LAUNCHERS, overrideOn = false)
        assertEquals("<tv-ip>", state.address)
        assertEquals(8484, state.phonePort)
        assertNull(state.qr)
    }

    private companion object {
        const val HOME = "ca.gureet.plasma.dev/ca.gureet.plasma.HomeActivity"
        val LAUNCHERS = listOf("com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith")
    }
}
