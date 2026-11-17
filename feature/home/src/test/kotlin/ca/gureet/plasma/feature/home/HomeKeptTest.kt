// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.zIndex
import ca.gureet.plasma.core.design.KeptScreen
import ca.gureet.plasma.core.design.KeptScreenShows
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.design.rememberKeptScreen
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Home kept made under the screens that open over it (#127), as the app keeps it: under a host
 * whose destination for Home only shows it (core/design's `KeptScreen`), here an `AnimatedContent`
 * that fades as the app's navigation host does.
 *
 * Home is made once, however often another screen opens over it. And each time it comes back it's
 * the Home that opens: the same picture, point by point, as when it was first made, with focus on
 * the Live TV tile, wherever it was left and whatever was left open on it.
 */
class HomeKeptTest : ScreenshotTest() {
    /** The screen in front: [HOME] or [OTHER]. */
    private var front by mutableIntStateOf(HOME)
    private var made = 0

    private fun show(heroAutoAdvance: Boolean = false) = screen {
        val kept = rememberKeptScreen()
        Box(Modifier.fillMaxSize().background(PlasmaColors.Bg)) {
            AnimatedContent(
                targetState = front,
                transitionSpec = {
                    // As the app's host has it: Home is the one under, coming and going.
                    ContentTransform(fadeIn(PlasmaMotion.screen()), fadeOut(PlasmaMotion.screen()), targetContentZIndex = if (targetState == HOME) -1f else 1f, sizeTransform = null)
                },
                label = "host",
            ) { destination ->
                if (destination == HOME) KeptScreenShows(kept) else Other()
            }
            KeptScreen(kept, Modifier.zIndex(-1f)) {
                DisposableEffect(Unit) {
                    made++
                    onDispose { }
                }
                HomeScreen(HomeSamples.state, HomeActions(), heroAutoAdvance = heroAutoAdvance)
            }
        }
    }

    /** Another screen: it covers Home, and takes focus as it opens. */
    @Composable
    private fun Other() {
        val focus = remember { FocusRequester() }
        Box(Modifier.fillMaxSize().background(PlasmaColors.Bg)) {
            PlasmaSurface(onClick = {}, modifier = Modifier.size(200.px).focusRequester(focus), description = "Another screen") {}
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            focus.requestFocus()
        }
    }

    private fun open(destination: Int) {
        compose.runOnIdle { front = destination }
        compose.waitForIdle()
    }

    private fun focusedOn(description: String) = focused().assert(hasContentDescription(description, substring = true))

    private fun picture(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    private fun steps(a: Color, b: Color): Int =
        (maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) * 255f).roundToInt()

    /** How far apart two pictures of the screen are at their furthest, in steps of 255. */
    private fun apart(a: PixelMap, b: PixelMap): Int {
        var apart = 0
        for (y in 0 until a.height step 6) {
            for (x in 0 until a.width step 6) apart = maxOf(apart, steps(a[x, y], b[x, y]))
        }
        return apart
    }

    private fun assertItIsTheHomeThatOpens(opens: PixelMap) {
        focusedOn("Live TV")
        val apart = apart(opens, picture())
        assertTrue("Home come back to is $apart steps from the Home that opens, at most", apart <= SAME)
        assertEquals("Home is made once", 1, made)
    }

    @Test fun comingBackFromItsTopHomeIsAsItOpens() {
        show()
        focusedOn("Live TV")
        val opens = picture()
        // Along the dock, as someone opening the Guide from its tile.
        press(Key.DirectionRight)
        open(OTHER)
        focusedOn("Another screen")
        // What's on screen is the other screen alone: Home isn't drawn under it.
        assertTrue("Under another screen Home isn't seen", apart(opens, picture()) > SAME)
        open(HOME)
        assertItIsTheHomeThatOpens(opens)
    }

    @Test fun comingBackFromItsRowsHomeIsAsItOpens() {
        show()
        focusedOn("Live TV")
        val opens = picture()
        // Down into the rows, three rows down and along.
        press(Key.DirectionDown, times = 3)
        press(Key.DirectionRight, times = 2)
        assertTrue("Down in its rows Home looks another way", apart(opens, picture()) > SAME)
        open(OTHER)
        focusedOn("Another screen")
        open(HOME)
        assertItIsTheHomeThatOpens(opens)
        // And it works as it does when it opens: along the dock from the Live TV tile is the Guide's.
        press(Key.DirectionRight)
        focusedOn("TV Guide")
    }

    @Test fun aSheetLeftOpenIsShutWhenHomeComesBack() {
        show()
        focusedOn("Live TV")
        val opens = picture()
        // A favorite's options.
        press(Key.DirectionDown)
        press(Key.Menu)
        assertTrue("With a sheet open Home looks another way", apart(opens, picture()) > SAME)
        open(OTHER)
        open(HOME)
        assertItIsTheHomeThatOpens(opens)
    }

    @Test fun itsHeroDoesNotTurnWhileItIsUnder() {
        show(heroAutoAdvance = true)
        focusedOn("Live TV")
        val opens = picture()
        open(OTHER)
        // More than three of the hero's turns.
        compose.mainClock.advanceTimeBy(35_000)
        compose.waitForIdle()
        open(HOME)
        assertItIsTheHomeThatOpens(opens)
    }

    @Test fun andItOpensTheSameTimeAfterTime() {
        show()
        focusedOn("Live TV")
        val opens = picture()
        repeat(3) {
            press(Key.DirectionDown, times = it + 1)
            open(OTHER)
            open(HOME)
            assertItIsTheHomeThatOpens(opens)
        }
    }

    private companion object {
        const val HOME = 0
        const val OTHER = 1

        /** Two pictures this near are the same picture: a step or two is rounding in a layer. */
        const val SAME = 2
    }
}
