// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Plasma's launch screen (#158, the owner's logo of 30 October 2026). */
class LaunchTest : ScreenshotTest() {
    @Test fun theWordWithItsWave() {
        screen { LaunchScreen(status = null) }
        compose.onNodeWithContentDescription("plasma").assertExists()
        capture("launch")
    }

    @Test fun stillLoadingSaysWhatItsDoing() {
        screen { LaunchScreen(status = LOADING) }
        capture("launch-loading")
    }

    @Test fun aQuickStartStillShowsTheWholeWord() {
        compose.mainClock.autoAdvance = false
        screen {
            LaunchHost(intro = true, status = LOADING, reduceMotion = { false }) {
                val gate = LocalLaunchGate.current
                LaunchedEffect(gate) { gate.ready() }
                Box(Modifier.fillMaxSize())
            }
        }
        // Ready at once, it waits until the word has come together, 0.95 s in.
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithContentDescription("plasma").assertExists()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("plasma").assertDoesNotExist()
    }

    @Test fun aSlowStartSaysWhatItsDoingAfterTwoAndAHalfSeconds() {
        compose.mainClock.autoAdvance = false
        screen { LaunchHost(intro = true, status = LOADING, reduceMotion = { false }) { Box(Modifier.fillMaxSize()) } }
        compose.mainClock.advanceTimeBy(2_300)
        compose.onNodeWithText(LOADING).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(LOADING).assertExists()
        compose.onNodeWithContentDescription("plasma").assertExists()
    }

    @Test fun aChannelIsntHeldBack() {
        compose.mainClock.autoAdvance = false
        screen {
            LaunchHost(intro = true, status = LOADING, reduceMotion = { false }) {
                val gate = LocalLaunchGate.current
                LaunchedEffect(gate) { gate.readyNow() }
                Box(Modifier.fillMaxSize())
            }
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("plasma").assertDoesNotExist()
    }

    @Test fun broughtBackByAndroidItsTheScreenAlone() {
        screen { LaunchHost(intro = false, status = LOADING, reduceMotion = { false }) { PlasmaText("Home", PlasmaType.Body) } }
        compose.onNodeWithContentDescription("plasma").assertDoesNotExist()
        compose.onNodeWithText("Home").assertExists()
    }

    private companion object {
        const val LOADING = "Loading channels…"
    }
}
