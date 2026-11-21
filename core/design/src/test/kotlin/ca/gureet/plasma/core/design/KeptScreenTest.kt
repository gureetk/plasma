// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.zIndex
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A screen kept made under the screens that open over it (#127): [KeptScreen] under a host whose
 * destination for it is [KeptScreenShows] alone. The host here is an `AnimatedContent` that fades
 * its destinations as the app's navigation host does, the kept one always the lower of the two.
 *
 * It's composed once, whatever opens over it and however often. Under another screen it stays laid
 * out and isn't drawn or read out; focus can't reach it, by a key or by itself, and it keeps none;
 * it's a screen on its way out from the moment it starts to leave until it comes back. Coming
 * back, nothing in it is laid out again. And its coming and going look as they did when it was its
 * destination's own content inside [SettlingScreen].
 */
class KeptScreenTest : ScreenshotTest() {
    /** The destination in front: [KEPT] or [OTHER]. */
    private var front by mutableIntStateOf(KEPT)

    private var made = 0
    private var gone = 0

    /** Whether the kept screen's content was a screen on its way out, each time it composed. */
    private var leaving = false

    /** How the kept screen's content was told it shows, each time it composed. */
    private var shown: ScreenShown? = null

    /** How often the kept screen's content was measured, and how often it placed what's in it. */
    private var measured = 0
    private var laidOut = 0

    /** The tile with focus, by its name. */
    private var focusOn: String? = null

    /** A host: the destination in front, the last one fading out as the next fades in over 200 ms. */
    @Composable
    private fun Host(kept: @Composable AnimatedVisibilityScope.() -> Unit, other: @Composable () -> Unit) {
        AnimatedContent(
            targetState = front,
            transitionSpec = {
                // As the app's host has it: the kept screen is the one under, coming and going.
                ContentTransform(fadeIn(PlasmaMotion.screen()), fadeOut(PlasmaMotion.screen()), targetContentZIndex = if (targetState == KEPT) -1f else 1f, sizeTransform = null)
            },
            label = "host",
        ) { destination ->
            if (destination == KEPT) kept() else other()
        }
    }

    private lateinit var focusManager: FocusManager

    /** [content] kept under a host whose other destination is [other], on black. */
    private fun kept(reduceMotion: Boolean = false, other: @Composable () -> Unit = { Other() }, content: @Composable () -> Unit = { Kept() }): KeptScreenState {
        lateinit var state: KeptScreenState
        screen {
            state = rememberKeptScreen()
            focusManager = LocalFocusManager.current
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                Box(Modifier.fillMaxSize().background(PlasmaColors.Black)) {
                    Host(kept = { KeptScreenShows(state) }, other = other)
                    KeptScreen(state, Modifier.zIndex(-1f)) { content() }
                }
            }
        }
        compose.waitForIdle()
        return state
    }

    /** A tile that takes focus, and says so by its [name]. */
    @Composable
    private fun Tile(name: String, x: Int, y: Int, focus: FocusRequester? = null) {
        PlasmaSurface(
            onClick = {},
            modifier = Modifier.offset(x.px, y.px).size(TILE.px).then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
            onFocusChanged = { has ->
                if (has) focusOn = name else if (focusOn == name) focusOn = null
            },
        ) {}
    }

    private val keptFocus = FocusRequester()
    private val keptPlainFocus = FocusRequester()
    private val otherFocus = FocusRequester()

    /**
     * The kept screen: white at the top left, two tiles across the top, and at its foot something
     * that takes focus without being a [PlasmaSurface].
     */
    @Composable
    private fun Kept() {
        DisposableEffect(Unit) {
            made++
            onDispose { gone++ }
        }
        val leavingNow = LocalScreenLeaving.current
        val shownNow = LocalScreenShown.current
        SideEffect {
            leaving = leavingNow
            shown = shownNow
        }
        // The one modifier each time this composes, so that only a layout asked of it counts.
        val counted = remember {
            Modifier.layout { measurable, constraints ->
                measured++
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    laidOut++
                    placeable.place(0, 0)
                }
            }
        }
        Box(Modifier.fillMaxSize().testTag("kept").then(counted)) {
            Box(Modifier.size(MARK.px).background(PlasmaColors.Text))
            Tile("kept a", x = 400, y = 0, focus = keptFocus)
            Tile("kept b", x = 700, y = 0)
            Box(Modifier.offset(400.px, 800.px).size(TILE.px).focusRequester(keptPlainFocus).focusable())
        }
    }

    /** The other screen: nothing at the top left, where the kept one is white, and two tiles under the kept one's. */
    @Composable
    private fun Other(takesFocus: Boolean = true) {
        Box(Modifier.fillMaxSize().testTag("other")) {
            Tile("other a", x = 400, y = 400, focus = otherFocus)
            Tile("other b", x = 700, y = 400)
        }
        if (takesFocus) {
            LaunchedEffect(Unit) {
                // Once its tiles are laid out.
                withFrameNanos { }
                otherFocus.requestFocus()
            }
        }
    }

    private fun open(destination: Int) {
        compose.runOnIdle { front = destination }
        compose.waitForIdle()
    }

    /** Stops the clock, makes [change], then lets [ms] go by: part way through what it sets off. */
    private fun partWay(ms: Long, change: () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnIdle(change)
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(ms)
    }

    private fun settle() {
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    /** How white the screen is in the middle of the kept screen's mark: 0 black, 1 Plasma's white. */
    private fun mark(): Float = compose.onRoot().captureToImage().toPixelMap()[MARK / 2, MARK / 2].red / PlasmaColors.Text.red

    /** Whether what's tagged [tag] is among what TalkBack would read, and a test would find. */
    private fun readOut(tag: String): Boolean = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test fun itIsMadeOnceWhateverOpensOverIt() {
        val state = kept()
        assertEquals(1, made)
        assertEquals(1, state.comings)
        repeat(3) {
            open(OTHER)
            assertFalse(state.inFront)
            assertFalse(state.showing)
            open(KEPT)
            assertTrue(state.inFront)
        }
        assertEquals("Made once", 1, made)
        assertEquals("Never let go", 0, gone)
        assertEquals("It came to the front at the start and three times more", 4, state.comings)
    }

    @Test fun underAnotherScreenItIsNeitherDrawnNorReadOut() {
        kept()
        assertTrue(readOut("kept"))
        assertEquals(1f, mark(), 0.02f)
        open(OTHER)
        // The other screen has nothing where the kept one's mark is: what shows there is what's under both.
        assertFalse("Under another screen it isn't read out", readOut("kept"))
        assertTrue(readOut("other"))
        assertEquals(0f, mark(), 0.02f)
        open(KEPT)
        assertTrue(readOut("kept"))
        assertEquals(1f, mark(), 0.02f)
    }

    /** What a screen that isn't placed would pay on the way back: every one of its layouts placed again. */
    @Test fun comingBackNothingInItIsLaidOutAgain() {
        kept()
        open(OTHER)
        val measuredUnder = measured
        val laidOutUnder = laidOut
        open(KEPT)
        assertEquals("Measured again on the way back", measuredUnder, measured)
        assertEquals("Laid out again on the way back", laidOutUnder, laidOut)
        // Nor as it goes under the next time.
        open(OTHER)
        assertEquals(measuredUnder, measured)
        assertEquals(laidOutUnder, laidOut)
    }

    @Test fun focusCannotReachItUnderAnotherScreen() {
        kept()
        compose.runOnIdle { keptFocus.requestFocus() }
        compose.waitForIdle()
        assertEquals("kept a", focusOn)
        open(OTHER)
        assertEquals("other a", focusOn)
        // The kept screen's tiles lie straight above the other's.
        press(Key.DirectionUp)
        assertEquals("other a", focusOn)
        press(Key.DirectionRight)
        assertEquals("other b", focusOn)
        press(Key.DirectionUp, times = 2)
        assertEquals("other b", focusOn)
    }

    @Test fun itKeepsNoFocusOnceItHasGoneUnder() {
        kept(other = { Other(takesFocus = false) })
        compose.runOnIdle { keptFocus.requestFocus() }
        compose.waitForIdle()
        assertEquals("kept a", focusOn)
        open(OTHER)
        assertEquals("The screen over it took no focus, and the kept one let go of its own", null, focusOn)
    }

    /**
     * With focus nowhere, whatever looks for it next starts from the top left, as Android does for
     * a screen that lost it: the kept screen's tiles lie nearer there than the other's, and are
     * laid out as ever.
     */
    @Test fun focusComingInByItselfGoesToTheScreenOverIt() {
        kept(other = { Other(takesFocus = false) })
        compose.runOnIdle { keptFocus.requestFocus() }
        compose.waitForIdle()
        open(OTHER)
        assertEquals(null, focusOn)
        compose.runOnIdle { focusManager.moveFocus(FocusDirection.Down) }
        compose.waitForIdle()
        assertEquals("other a", focusOn)
    }

    /** A sheet closing on a screen that's under another would ask for focus back on its item. */
    @Test fun nothingInItTakesFocusWhileItIsUnderEvenWhenAsked() {
        kept()
        open(OTHER)
        assertEquals("other a", focusOn)
        var granted = true
        compose.runOnIdle { granted = keptFocus.requestFocus(FocusDirection.Enter) }
        compose.waitForIdle()
        assertFalse("A request from under another screen isn't granted", granted)
        assertEquals("other a", focusOn)
        // In front again, it's granted as ever.
        open(KEPT)
        compose.runOnIdle { granted = keptFocus.requestFocus(FocusDirection.Enter) }
        compose.waitForIdle()
        assertTrue(granted)
        assertEquals("kept a", focusOn)
    }

    /** What takes focus in it without being a [PlasmaSurface] hasn't the gate: focus is refused as it comes in. */
    @Test fun evenWhatIsNoSurfaceTakesNoFocusWhileItIsUnder() {
        kept()
        open(OTHER)
        var granted = true
        compose.runOnIdle { granted = keptPlainFocus.requestFocus(FocusDirection.Enter) }
        compose.waitForIdle()
        assertFalse("A request from under another screen isn't granted", granted)
        assertEquals("other a", focusOn)
        // Down from the other screen's tiles, where it lies and nothing of the other screen does.
        press(Key.DirectionDown, times = 2)
        assertEquals("other a", focusOn)
        open(KEPT)
        compose.runOnIdle { granted = keptPlainFocus.requestFocus(FocusDirection.Enter) }
        compose.waitForIdle()
        assertTrue(granted)
    }

    @Test fun itIsAScreenOnItsWayOutFromTheMomentItLeavesUntilItComesBack() {
        kept()
        assertFalse(leaving)
        partWay(64) { front = OTHER }
        assertTrue("Leaving, part way out", leaving)
        assertTrue("Still drawn, part way out", mark() > 0.05f)
        settle()
        assertTrue("Under the other screen", leaving)
        partWay(64) { front = KEPT }
        assertFalse("In front again from the first of its way in", leaving)
        settle()
        assertFalse(leaving)
    }

    /** What's in it reads how it shows where it is, with nothing between to hand it on. */
    @Test fun itsContentIsToldHowItShows() {
        kept()
        assertEquals(ScreenShown(inFront = true, under = false, coming = 1), shown)
        partWay(64) { front = OTHER }
        assertEquals("Leaving, part way out", ScreenShown(inFront = false, under = false, coming = 1), shown)
        settle()
        assertEquals("Under the other screen", ScreenShown(inFront = false, under = true, coming = 1), shown)
        partWay(64) { front = KEPT }
        assertEquals("Its next coming, from the first of its way in", ScreenShown(inFront = true, under = false, coming = 2), shown)
        settle()
        assertEquals(ScreenShown(inFront = true, under = false, coming = 2), shown)
    }

    /** A screen that isn't kept, as every other screen: in front, and never under. */
    @Test fun aScreenThatIsSimplyMadeIsInFront() {
        screen { Kept() }
        compose.waitForIdle()
        assertEquals(ScreenShown(inFront = true, under = false, coming = 0), shown)
        assertFalse(leaving)
    }

    private fun steps(a: Color, b: Color): Int =
        (maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) * 255f).roundToInt()

    /** How far apart the plain way (left) and the kept way (right) are at their furthest, in steps of 255. */
    private fun apart(): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        var apart = 0
        for (y in 0 until SIDE step 5) {
            for (x in 0 until SIDE step 5) apart = maxOf(apart, steps(pixels[x, y], pixels[x + APART, y]))
        }
        return apart
    }

    /** A screen with something to see settle: white, with a darker band across its top. */
    @Composable
    private fun Seen() {
        Box(Modifier.fillMaxSize().background(PlasmaColors.Text)) {
            Box(Modifier.size(SIDE.px, BAND.px).background(PlasmaColors.Live))
        }
    }

    @Composable
    private fun OtherSeen() {
        Box(Modifier.fillMaxSize().background(PlasmaColors.Ok))
    }

    /** The same two screens crossfading twice over: as a destination's own content, and kept under the host. */
    private fun sideBySide(reduceMotion: Boolean) = screen {
        val state = rememberKeptScreen()
        CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
            Box(Modifier.fillMaxSize().background(PlasmaColors.Black)) {
                Box(Modifier.size(SIDE.px).clipToBounds()) {
                    Host(kept = { SettlingScreen { Seen() } }, other = { OtherSeen() })
                }
                Box(Modifier.offset(x = APART.px).size(SIDE.px).clipToBounds()) {
                    Host(kept = { KeptScreenShows(state) }, other = { OtherSeen() })
                    KeptScreen(state, Modifier.zIndex(-1f)) { Seen() }
                }
            }
        }
    }

    /** Out and in again, at each step of the 200 ms and past it. */
    private fun assertItComesAndGoesAsItDid(reduceMotion: Boolean) {
        sideBySide(reduceMotion)
        assertEquals("At rest", 0, apart())
        val out = mutableListOf<Int>()
        partWay(0) { front = OTHER }
        repeat(8) {
            out += apart()
            compose.mainClock.advanceTimeBy(32)
        }
        settle()
        assertEquals("Under the other screen", 0, apart())
        val back = mutableListOf<Int>()
        partWay(0) { front = KEPT }
        repeat(8) {
            back += apart()
            compose.mainClock.advanceTimeBy(32)
        }
        settle()
        assertEquals("In front again", 0, apart())
        assertTrue("Going out, the two ways are $out steps apart", out.max() <= 1)
        assertTrue("Coming back, the two ways are $back steps apart", back.max() <= 1)
    }

    @Test fun itFadesOutAndSettlesInAsADestinationsOwnContentDid() = assertItComesAndGoesAsItDid(reduceMotion = false)

    @Test fun withReduceMotionItOnlyFadesAsItDid() = assertItComesAndGoesAsItDid(reduceMotion = true)

    private companion object {
        const val KEPT = 0
        const val OTHER = 1

        /** The kept screen's white square at the top left. */
        const val MARK = 200

        const val TILE = 160

        /** Each way's square in the side-by-side, and from one's left edge to the other's. */
        const val SIDE = 600
        const val APART = 700

        /** The band across the top of the screen that's seen settling. */
        const val BAND = 120
    }
}
