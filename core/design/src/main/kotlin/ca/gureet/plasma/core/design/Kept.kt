// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics

/*
 * A screen kept made under the screens that open over it (#127).
 *
 * Navigation takes a screen out of the composition when another opens over it, and on the way back
 * makes it again from nothing: every composable, every layout and every text, over two or three
 * long frames. For the screen that every Back ends at, that's each time.
 *
 * A kept screen stays composed instead, at the app's root and under the navigation host
 * ([KeptScreen]), and its destination in the host is a stand-in that draws nothing
 * ([KeptScreenShows]). While the destination shows, the kept screen is drawn, and it fades and
 * settles by the destination's own transition, so its crossfade with the screen over it is the one
 * there was. Once the destination has gone, the kept screen stays laid out where it is and nothing
 * of it is drawn. Nothing in it takes focus, it isn't read out, and [LocalScreenLeaving] is true in
 * it, as on a screen on its way out. What it shows while it's under is its own affair: it's told
 * when it goes under and when it comes back ([LocalScreenShown]), to stop what it was doing and to
 * open as it does from nothing.
 *
 * It stays laid out because a screen that isn't placed has every layer of it let go, and placing
 * it again makes and records them all in the frame that brings it back: most of what that frame
 * then costs. Laid out and not drawn, its layers keep what they recorded, the frame that brings it
 * back records one, and the GPU's own copies go while nothing draws them. What changes in it
 * while it's under (it goes back to how it opens) is laid out then, unseen, and not on the way
 * back.
 */

/**
 * How the screen around a composable shows, for a screen that's kept made under the screens that
 * open over it rather than made again each time one closes.
 *
 * Under another screen ([under]) it's composed but not on screen. There it goes back to how it
 * opens, with nothing left open or half done, so each [coming] to the front is an opening, as it
 * was when the screen was made from nothing each time.
 *
 * It's [inFront] while it's the screen in front or the one coming in, and not from the moment it
 * starts to leave: then nothing in it need turn, count or be read.
 *
 * A screen that's simply made, as every other screen is and as in tests and previews, is in front
 * at its first coming.
 */
@Immutable
data class ScreenShown(val inFront: Boolean = true, val under: Boolean = false, val coming: Int = 0)

/**
 * How the screen around a composable shows. [KeptScreen] says for what's in it, down to wherever
 * it's read: nothing between has it to hand on, so nothing between can leave it out.
 */
val LocalScreenShown = compositionLocalOf { ScreenShown() }

/**
 * Put before a focus target, it lets the target take focus only while its screen shows: a kept
 * screen under another closes it for everything in it at once, and nothing composes again for it.
 * [PlasmaSurface] carries it, so whatever takes focus in a kept screen has to be one, or carry it
 * too. Outside a kept screen it's nothing.
 *
 * Focus is otherwise kept from what isn't seen by its not being there. A kept screen under
 * another is there, laid out as ever: focus's search by direction would weigh its tiles against
 * the tiles of the screen in front, and where focus is lost and comes back in by itself it goes to
 * whatever lies nearest the top left, on either screen.
 */
val LocalFocusGate = staticCompositionLocalOf<Modifier> { Modifier }

/**
 * For the benchmarks (#127): a kept screen not placed while it's under another, as it first was
 * built, so that a run can measure the way back both ways. Then every layer of it is let go as it
 * goes under, and it's placed and recorded again as it comes back. The benchmark build's setup
 * sets it as Plasma starts.
 */
object KeptScreenForBenchmarks {
    @Volatile var notPlacedUnder = false

    /**
     * A kept screen's drawing left recorded while it's under another, behind its own layer at
     * alpha 0, which draws nothing: what the screen still walks keeps its layers' textures, so the
     * kept layers in it needn't be painted again as it comes back, and hold their graphics memory
     * meanwhile. A run measures the way back both ways, and what the textures weigh.
     */
    @Volatile var layersHeldUnder = false
}

/** A kept screen's place in the app: made by [rememberKeptScreen], given to [KeptScreen] and to [KeptScreenShows]. */
@Stable
class KeptScreenState internal constructor() {
    /** Its destination's fade and settling, while the destination is composed. */
    internal var shows: Shows? by mutableStateOf(null)

    /**
     * Whether its destination is the one in front or the one coming in: false under another screen,
     * and from the moment it starts to leave.
     */
    val inFront: Boolean get() = shows?.leaving == false

    /** Whether any of it shows: in front, on its way in or on its way out. Read where it's drawn. */
    val showing: Boolean get() = shows != null

    /** How many times it has come to the front from under another screen, or from nothing. */
    var comings: Int by mutableIntStateOf(0)
        internal set

    internal class Shows(val alpha: State<Float>, val settle: State<Float>) {
        var leaving by mutableStateOf(false)
    }
}

@Composable
fun rememberKeptScreen(): KeptScreenState = remember { KeptScreenState() }

/**
 * A kept screen's destination in the navigation host: nothing to see, and [state]'s screen under
 * the host shows for it, fading and settling as [SettlingScreen] would have this destination's
 * content: in over the brief's 200 ms, 16 dp upward unless Reduce Motion is on, and out with the
 * fade alone.
 */
@Composable
fun AnimatedVisibilityScope.KeptScreenShows(state: KeptScreenState) {
    val reduceMotion = LocalReduceMotion.current
    val distance = with(LocalDensity.current) { PlasmaMotion.SCREEN_SETTLE.toPx() }
    // The host's own fade for this destination, and the settle [SettlingScreen] gives a screen.
    val alpha = transition.animateFloat(transitionSpec = { PlasmaMotion.screen() }, label = "keptAlpha") { if (it == EnterExitState.Visible) 1f else 0f }
    val settle = transition.animateFloat(transitionSpec = { PlasmaMotion.screen() }, label = "keptSettle") { if (it == EnterExitState.PreEnter && !reduceMotion) distance else 0f }
    val shows = remember(alpha, settle) { KeptScreenState.Shows(alpha, settle) }
    // Told as this composes, not after it: the kept screen composes later in the same pass (it's
    // called after the host), so it's in front, and drawn, in the very frame its destination
    // comes in.
    shows.leaving = transition.targetState == EnterExitState.PostExit
    if (state.shows !== shows) {
        state.shows = shows
        state.comings++
    }
    DisposableEffect(state, shows) {
        onDispose {
            if (state.shows === shows) state.shows = null
        }
    }
}

/**
 * [state]'s screen, kept composed. See [KeptScreenState]. Call it after the navigation host, and
 * draw it under the host (`Modifier.zIndex(-1f)`): composed after its destination, it knows in the
 * same pass that the destination has come in. [content] reads how it shows from
 * [LocalScreenShown], and is in [LocalScreenLeaving] whenever the screen isn't in front.
 *
 * Under another screen it's laid out still, and nothing of it is drawn or read out. It keeps no
 * focus and can take none: its [PlasmaSurface]s can't ([LocalFocusGate]), and anything else that
 * asked would be refused as focus came in. A key then belongs to the screen over it.
 */
@Composable
fun KeptScreen(state: KeptScreenState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val focus = LocalFocusManager.current
    var focusInside by remember { mutableStateOf(false) }
    val showing = state.showing
    LaunchedEffect(showing) {
        // Gone under with focus still in it, where the screen over it took none: let it go, as
        // it went when the screen left the composition.
        if (!showing && focusInside) focus.clearFocus(force = true)
    }
    val gate = remember(state) { Modifier.focusProperties { if (!state.showing) canFocus = false } }
    val shown = ScreenShown(inFront = state.inFront, under = !showing, coming = state.comings)
    // The benchmarks' switch, read once: the screen in a layout that places it only while it shows.
    val notPlacedUnder = remember { KeptScreenForBenchmarks.notPlacedUnder }
    CompositionLocalProvider(LocalScreenLeaving provides !shown.inFront, LocalScreenShown provides shown, LocalFocusGate provides gate) {
        if (notPlacedUnder) {
            Layout(content = { Kept(state, Modifier, showing, { focusInside = it }, content) }, modifier = modifier) { measurables, constraints ->
                val screen = measurables.single().measure(constraints)
                layout(screen.width, screen.height) {
                    if (state.showing) screen.place(0, 0)
                }
            }
        } else {
            Kept(state, modifier, showing, { focusInside = it }, content)
        }
    }
}

/** [KeptScreen]'s own box, apart for the benchmarks' switch. */
@Composable
private fun Kept(state: KeptScreenState, modifier: Modifier, showing: Boolean, onFocusInside: (Boolean) -> Unit, content: @Composable () -> Unit) {
    // The fade is the whole screen's, in a layer while it fades, as the host fades a
    // destination; the settle moves what's in it, as [SettlingScreen] does.
    Box(
        modifier
            .onFocusChanged { onFocusInside(it.hasFocus) }
            // The net under the gate: focus coming into it while it's under is refused, for
            // whatever in it takes focus without being a [PlasmaSurface].
            .focusProperties { onEnter = { if (!state.showing) cancelFocusChange() } }
            .focusGroup()
            .graphicsLayer { alpha = state.shows?.alpha?.value ?: 0f }
            // Read as it's drawn, so going under and coming back only records this one layer
            // again: the layers in it keep what they recorded.
            .drawWithContent { if (state.showing || KeptScreenForBenchmarks.layersHeldUnder) drawContent() }
            // Under another screen it's out of what TalkBack reads, and of what a test finds.
            // Last, so that going under and coming back changes nothing before it.
            .then(if (showing) Modifier else Modifier.clearAndSetSemantics { }),
        propagateMinConstraints = true,
    ) {
        Box(Modifier.graphicsLayer { translationY = state.shows?.settle?.value ?: 0f }, propagateMinConstraints = true) {
            content()
        }
    }
}
