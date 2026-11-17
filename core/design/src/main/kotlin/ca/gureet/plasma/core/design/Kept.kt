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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager

/*
 * A screen kept made under the screens that open over it (#127).
 *
 * Navigation takes a screen out of the composition when another opens over it, and on the way back
 * makes it again from nothing: every composable, every layout and every text, over two or three
 * long frames. For the screen that every Back ends at, that's each time.
 *
 * A kept screen stays composed instead, at the app's root and under the navigation host
 * ([KeptScreen]), and its destination in the host is a stand-in that draws nothing
 * ([KeptScreenShows]). While the destination shows, the kept screen is placed, and it fades and
 * settles by the destination's own transition, so its crossfade with the screen over it is the one
 * there was. Once the destination has gone, the kept screen is measured still but not placed: it
 * isn't drawn, holds no layer, takes no focus and isn't read out, and [LocalScreenLeaving] is true
 * in it, as on a screen on its way out. What it shows while it's under is its own affair: it's
 * told when it goes under and when it comes back ([KeptScreenState.inFront],
 * [KeptScreenState.comings]), to stop what it was doing and to open as it does from nothing.
 */

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

    /** Whether any of it shows: in front, or on its way out. Read where it's placed. */
    val placed: Boolean get() = shows != null

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
    // called after the host), so it's in front, and placed, in the very frame its destination
    // comes in, and its video's slot has claimed the picture before the slot is placed.
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
 * same pass that the destination has come in. [content] is in [LocalScreenLeaving] whenever the
 * screen isn't in front. Once it has gone under it keeps no focus and can take none, not even
 * where something in it asks: a key then belongs to the screen over it.
 */
@Composable
fun KeptScreen(state: KeptScreenState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val focus = LocalFocusManager.current
    var focusInside by remember { mutableStateOf(false) }
    val placed = state.placed
    LaunchedEffect(placed) {
        // Gone under with focus still in it, where the screen over it took none: let it go, as
        // it went when the screen left the composition.
        if (!placed && focusInside) focus.clearFocus(force = true)
    }
    CompositionLocalProvider(LocalScreenLeaving provides !state.inFront) {
        Layout(
            content = {
                // The fade is the whole screen's, in a layer while it fades, as the host fades a
                // destination; the settle moves what's in it, as [SettlingScreen] does.
                Box(
                    Modifier
                        .onFocusChanged { focusInside = it.hasFocus }
                        // Nothing in it takes focus while it's under, whoever asks: focus's own
                        // search leaves out what isn't placed, but a request made from inside
                        // it (a sheet closing back on its item) would be granted.
                        .focusProperties { onEnter = { if (!state.placed) cancelFocusChange() } }
                        .focusGroup()
                        .graphicsLayer { alpha = state.shows?.alpha?.value ?: 0f },
                    propagateMinConstraints = true,
                ) {
                    Box(Modifier.graphicsLayer { translationY = state.shows?.settle?.value ?: 0f }, propagateMinConstraints = true) {
                        content()
                    }
                }
            },
            modifier = modifier,
        ) { measurables, constraints ->
            val screen = measurables.single().measure(constraints)
            layout(screen.width, screen.height) {
                // Read here, so going under and coming back only places it again.
                if (state.placed) screen.place(0, 0)
            }
        }
    }
}
