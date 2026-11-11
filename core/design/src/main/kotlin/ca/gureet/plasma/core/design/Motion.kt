// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

/*
 * Motion (brief 2.6). Everything here animates through a graphics layer — alpha and translation —
 * read in the layer's own block, so a frame redraws without composing or laying out again, and
 * nothing waits on an animation: a key pressed while one runs acts at once.
 */

/**
 * Something on its way off screen: an overlay fading out. Its [PlasmaSurface]s let focus and keys
 * go at once, so the screen underneath takes the next key, and keep the look they had while they
 * fade (a sheet's chosen row stays white).
 */
val LocalLeaving = staticCompositionLocalOf { false }

/**
 * A screen on its way out as another comes in (a navigation destination fading out): its video
 * lets the picture go at once, since a picture can't fade with it. Only what reads it composes
 * again as the screen starts to leave.
 */
val LocalScreenLeaving = compositionLocalOf { false }

/** An overlay's coming and going, from [rememberOverlay]; show it with [OverlayContent]. */
@Stable
class OverlayMotion internal constructor(
    internal val state: MutableTransitionState<Boolean>,
) {
    /** 0 gone, 1 shown; read it in a draw or layer block, never in composition. */
    internal var progress: State<Float>? = null
    internal lateinit var transition: Transition<Boolean>

    /** Each showing composes afresh, so its first-frame effects (taking focus) run each time. */
    internal var showing by mutableIntStateOf(0)
    internal var wasVisible = false

    /** On screen: shown, or still fading out. */
    val present: Boolean get() = transition.currentState || transition.targetState

    /** Shown, rather than going or gone. */
    val visible: Boolean get() = state.targetState
}

/**
 * How an overlay, sheet or toast comes and goes (brief 2.6): it fades in and rises 8 dp over 200
 * ms, and fades out and sinks back over 150 ms; with Reduce Motion it only fades. Shown when the
 * screen first appears, it's simply there, unless [appear] asks for it to come in too.
 */
@Composable
fun rememberOverlay(visible: Boolean, appear: Boolean = false): OverlayMotion {
    val motion = remember { OverlayMotion(MutableTransitionState(visible && !appear)) }
    motion.state.targetState = visible
    val transition = rememberTransition(motion.state, label = "overlay")
    motion.transition = transition
    motion.progress = transition.animateFloat(
        transitionSpec = { if (targetState) PlasmaMotion.overlayIn() else PlasmaMotion.overlayOut() },
        label = "overlayProgress",
    ) { shown -> if (shown) 1f else 0f }
    // Counted without reading it here, so counting doesn't compose this again.
    if (visible && !motion.wasVisible) motion.showing = Snapshot.withoutReadObservation { motion.showing } + 1
    motion.wasVisible = visible
    return motion
}

/**
 * [content] while [motion] is [present][OverlayMotion.present], faded and risen as it says. While it
 * goes, it's [LocalLeaving]. Give it the [modifier] the content would have had for its place.
 *
 * The fade multiplies each thing drawn by the overlay's alpha rather than drawing the whole into a
 * buffer first: a sheet's dimmed backdrop covers the screen, and a full-screen buffer every frame
 * would cost the TV more than the fade is worth.
 */
@Composable
fun OverlayContent(motion: OverlayMotion, modifier: Modifier = Modifier, rise: Boolean = true, content: @Composable () -> Unit) {
    if (!motion.present) return
    val progress = motion.progress ?: return
    val risePx = if (rise && !LocalReduceMotion.current) with(LocalDensity.current) { PlasmaMotion.OVERLAY_RISE.toPx() } else 0f
    val leaving = !motion.visible || LocalLeaving.current
    key(motion.showing) {
        CompositionLocalProvider(LocalLeaving provides leaving) {
            Box(
                modifier.graphicsLayer {
                    val shown = progress.value
                    alpha = shown
                    translationY = (1f - shown) * risePx
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
                propagateMinConstraints = true,
            ) {
                content()
            }
        }
    }
}

/** [content] as an overlay that comes and goes with [visible]: see [rememberOverlay]. */
@Composable
fun Overlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    rise: Boolean = true,
    appear: Boolean = false,
    content: @Composable () -> Unit,
) {
    OverlayContent(rememberOverlay(visible, appear), modifier, rise, content)
}

/**
 * [value], or while it's null, the last value it had: what an overlay shows as it fades out after
 * what it was about is gone (a sheet's channel, a typed number).
 */
@Composable
fun <T : Any> rememberLast(value: T?): T? {
    val last = remember { LastValue<T>() }
    if (value != null) last.value = value
    return value ?: last.value
}

private class LastValue<T> {
    var value: T? = null
}

/**
 * A screen coming in settles 16 dp upward as it fades in (brief 2.6); with Reduce Motion it only
 * fades. Put it around a navigation destination's content. A screen on its way out is
 * [LocalScreenLeaving], so its video lets the picture go at once.
 */
@Composable
fun AnimatedVisibilityScope.SettlingScreen(content: @Composable () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val distance = with(LocalDensity.current) { PlasmaMotion.SCREEN_SETTLE.toPx() }
    val settle = transition.animateFloat(
        transitionSpec = { PlasmaMotion.screen() },
        label = "screenSettle",
    ) { state -> if (state == EnterExitState.PreEnter && !reduceMotion) distance else 0f }
    val leaving = transition.targetState == EnterExitState.PostExit
    CompositionLocalProvider(LocalScreenLeaving provides leaving) {
        Box(Modifier.graphicsLayer { translationY = settle.value }, propagateMinConstraints = true) {
            content()
        }
    }
}

/**
 * Content that changes in place fades in on its one copy, rather than jumping: see
 * [rememberSwapFade] and [swapFade].
 */
@Stable
class SwapFade internal constructor() {
    /** Read in a layer block only, so the fade redraws without composing. */
    internal var alpha by mutableFloatStateOf(1f)

    /** How many fades have started: the key that starts the next. */
    internal var restarts = 0
}

/** What a [SwapFade] last saw: plain fields, for composition to compare against. */
private class SwapSeen(var key: Any?) {
    /** When [key] last changed, by `System.nanoTime`; null until it first does. */
    var changedAtNanos: Long? = null

    /**
     * Takes [key] as the one seen, and says whether a fade starts again for it: it's another key,
     * the first change or one [PlasmaMotion.SWAP_QUIET_MS] or more after the last.
     */
    fun fadesTo(key: Any?): Boolean {
        if (this.key == key) return false
        val now = System.nanoTime()
        val quiet = changedAtNanos.let { last -> last == null || now - last > PlasmaMotion.SWAP_QUIET_MS * NANOS_PER_MS }
        this.key = key
        changedAtNanos = now
        return quiet
    }
}

/**
 * Fades content in from [from] each time [key] changes after the first (a category's channels, a
 * day of the guide, a setting's detail), over 150 ms. The new content is there at once, faded,
 * and nothing waits for it; changes that come quicker than [PlasmaMotion.SWAP_QUIET_MS] apart, as
 * while a key is held, swap without starting it again, so fast scrolling doesn't flicker. Reduce
 * Motion keeps it: it's a crossfade, not a movement.
 */
@Composable
fun rememberSwapFade(key: Any?, from: Float = 0f): SwapFade {
    val fade = remember { SwapFade() }
    val seen = remember { SwapSeen(key) }
    if (seen.fadesTo(key)) {
        // Faded already in the frame that shows the new content, so it never flashes in full.
        fade.alpha = from
        fade.restarts++
    }
    LaunchedEffect(fade.restarts) {
        if (fade.restarts == 0) return@LaunchedEffect
        animate(fade.alpha, 1f, animationSpec = PlasmaMotion.swap()) { value, _ -> fade.alpha = value }
    }
    return fade
}

/**
 * A [SwapFade] for content that comes in a part at a time, and how much of it is in: see
 * [rememberSteppedSwap].
 */
@Stable
class SteppedSwap internal constructor(steps: Int) {
    /** The fade, for [swapFade]. */
    val fade = SwapFade()

    /** How many parts are in after the first: all of them, but in the frames a change comes in over. */
    var step by mutableIntStateOf(steps)
        internal set
}

/**
 * [rememberSwapFade] for content that's too much to set in one frame (Live TV's details: what's on
 * now, then its schedule). When [key] changes and the fade starts again, [SteppedSwap.step] counts
 * up from 0 to [steps], a frame at a time, and the fade waits at [from] until it gets there. The
 * new content comes in a part at a time while it's still hidden, then fades in whole, and no one
 * frame sets all of it (#127). A change that swaps without fading has every part at once.
 */
@Composable
fun rememberSteppedSwap(key: Any?, steps: Int, from: Float = 0f): SteppedSwap {
    val swap = remember { SteppedSwap(steps) }
    val seen = remember { SwapSeen(key) }
    if (seen.fadesTo(key)) {
        swap.fade.alpha = from
        swap.fade.restarts++
        swap.step = 0
    }
    LaunchedEffect(swap.fade.restarts) {
        if (swap.fade.restarts == 0) return@LaunchedEffect
        while (swap.step < steps) {
            withFrameNanos { }
            swap.step++
        }
        animate(swap.fade.alpha, 1f, animationSpec = PlasmaMotion.swap()) { value, _ -> swap.fade.alpha = value }
    }
    return swap
}

/** Draws this with [fade]'s alpha (see [rememberSwapFade]). */
fun Modifier.swapFade(fade: SwapFade): Modifier = graphicsLayer {
    alpha = fade.alpha
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

/**
 * A lazy list's item that slides to its new place as the list changes — one moved past it, or one
 * before it gone — and with [fade], fades in as it comes and out as it goes (brief 2.6). With
 * Reduce Motion it doesn't slide. [placement] false keeps it still, for a list that has just
 * become another list (a new category), where sliding would only show the old one's order.
 */
fun Modifier.itemMotion(scope: LazyItemScope, reduceMotion: Boolean, placement: Boolean = true, fade: Boolean = true): Modifier =
    with(scope) {
        this@itemMotion.animateItem(
            fadeInSpec = if (fade) PlasmaMotion.itemFade() else null,
            placementSpec = if (placement && !reduceMotion) PlasmaMotion.place() else null,
            fadeOutSpec = if (fade) PlasmaMotion.itemFade() else null,
        )
    }

/**
 * An item stepping back while another moves (board 04's move mode: the rest at 60 %): it fades
 * there over 150 ms rather than at once, and back again.
 */
@Composable
fun rememberStepBack(back: Boolean, alpha: Float): State<Float> =
    animateFloatAsState(if (back) alpha else 1f, PlasmaMotion.focus(), label = "stepBack")

/** Draws this at [alpha]'s value, read where it's drawn: see [rememberStepBack]. Faded, it clips, as `Modifier.alpha` does. */
fun Modifier.stepBack(alpha: State<Float>): Modifier = graphicsLayer {
    val shown = alpha.value
    this.alpha = shown
    clip = shown < 1f
}

/**
 * An item lifted to be moved (board 04): it rises 6 px over 150 ms and settles back as it's
 * dropped; with Reduce Motion it lifts at once.
 */
@Composable
fun rememberMoveLift(moving: Boolean): State<Float> {
    val reduceMotion = LocalReduceMotion.current
    return animateFloatAsState(if (moving) 1f else 0f, if (reduceMotion) snap() else PlasmaMotion.focus(), label = "moveLift")
}

/** Draws this [lift]ed (see [rememberMoveLift]), read where it's drawn. */
fun Modifier.moveLift(lift: State<Float>): Modifier = graphicsLayer { translationY = -MoveLift.toPx() * lift.value }

/** How far an item lifts to be moved: 6 px on the boards. */
private val MoveLift = 6.px

private const val NANOS_PER_MS = 1_000_000L
