// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animate
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset

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

    /**
     * The one layer block [swapFade] draws with: the same each time, so the modifier it makes is
     * equal to the last one, and what has it doesn't compose again for it (#127).
     */
    internal val layer: GraphicsLayerScope.() -> Unit = {
        alpha = this@SwapFade.alpha
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/** What a [SwapFade] last saw: plain fields, for composition to compare against. */
private class SwapSeen(var key: Any?) {
    /** When [key] last changed, by `System.nanoTime`; null until it first does. */
    var changedAtNanos: Long? = null
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
    if (seen.key != key) {
        val now = System.nanoTime()
        val quiet = seen.changedAtNanos.let { last -> last == null || now - last > PlasmaMotion.SWAP_QUIET_MS * NANOS_PER_MS }
        seen.key = key
        seen.changedAtNanos = now
        if (quiet) {
            // Faded already in the frame that shows the new content, so it never flashes in full.
            fade.alpha = from
            fade.restarts++
        }
    }
    LaunchedEffect(fade.restarts) {
        if (fade.restarts == 0) return@LaunchedEffect
        animate(fade.alpha, 1f, animationSpec = PlasmaMotion.swap()) { value, _ -> fade.alpha = value }
    }
    return fade
}

/** Draws this with [fade]'s alpha (see [rememberSwapFade]). */
fun Modifier.swapFade(fade: SwapFade): Modifier = graphicsLayer(fade.layer)

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
    followFloatAsState(if (back) alpha else 1f, PlasmaMotion.focus())

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
    return followFloatAsState(if (moving) 1f else 0f, if (reduceMotion) snap() else PlasmaMotion.focus())
}

/** Draws this [lift]ed (see [rememberMoveLift]), read where it's drawn. */
fun Modifier.moveLift(lift: State<Float>): Modifier = graphicsLayer { translationY = -MoveLift.toPx() * lift.value }

/**
 * Draws this at [alpha], read where it's drawn, as `Modifier.alpha` does: faded, it's drawn whole
 * and then faded, and clipped. A fade read in composition would compose what has it again at each
 * of its frames; read here, a frame only draws again (#127).
 */
fun Modifier.fade(alpha: () -> Float): Modifier = graphicsLayer {
    val shown = alpha()
    this.alpha = shown
    clip = shown < 1f
}

/**
 * A row [height] tall with [margin] above and below it, as wide as it may be: what
 * `padding(vertical = margin).fillMaxWidth().height(height)` makes, but read as it's laid out, so a
 * row growing as it takes focus lays its list out again at each frame rather than composing again
 * (#127).
 */
fun Modifier.rowSize(height: State<Dp>, margin: State<Dp>? = null): Modifier = layout { measurable, constraints ->
    val room = margin?.value?.roundToPx() ?: 0
    val inner = constraints.offset(vertical = -2 * room)
    val tall = height.value.roundToPx().coerceIn(inner.minHeight, inner.maxHeight)
    val wide = if (inner.hasBoundedWidth) inner.maxWidth else inner.minWidth
    val placeable = measurable.measure(Constraints(wide, inner.maxWidth, tall, tall))
    layout(constraints.constrainWidth(placeable.width), constraints.constrainHeight(placeable.height + 2 * room)) {
        placeable.placeRelative(0, room)
    }
}

/** How far an item lifts to be moved: 6 px on the boards. */
private val MoveLift = 6.px

private const val NANOS_PER_MS = 1_000_000L
