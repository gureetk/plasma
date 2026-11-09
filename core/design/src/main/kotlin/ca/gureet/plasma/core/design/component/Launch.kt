// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import ca.gureet.plasma.core.design.LocalReduceMotion
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import kotlin.math.PI
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How the first screen tells the launch screen it can go (#158), through [LocalLaunchGate]: once
 * it has what it shows, or at once for a channel.
 */
@Stable
class LaunchGate {
    /** Whether the first screen has said it's ready. */
    var isReady by mutableStateOf(false)
        private set

    /** Whether it's a channel, which the launch screen doesn't hold back. */
    internal var atOnce by mutableStateOf(false)
        private set

    /** The first screen has what it shows: the launch screen goes once the word is whole. */
    fun ready() {
        isReady = true
    }

    /** A channel opened straight away, as When the TV Turns On can: the launch screen goes now. */
    fun readyNow() {
        atOnce = true
        isReady = true
    }
}

/** The launch screen's gate for the first screen to open; outside [LaunchHost], one nobody waits on. */
val LocalLaunchGate = staticCompositionLocalOf { LaunchGate() }

/**
 * Plasma's start (the owner's logo of 30 October 2026, and #158). [content] comes up under the
 * launch screen, where the wave wordmark comes together and its wave starts to flow, and the launch
 * screen goes once [content] says through [LocalLaunchGate] that it has what it shows: never
 * before 0.95 s, when the word is whole, and at once for a channel. While that takes longer than
 * 2.5 s, [status] shows under the word. The launch screen then fades out in 200 ms as [content]
 * fades in and settles 16 dp. With [reduceMotion] the word fades in at rest, and the two only
 * crossfade; it's asked as the word starts to show, by when the settings have loaded. Without
 * [intro], as when Android brings Plasma back, it's [content] alone.
 */
@Composable
fun LaunchHost(intro: Boolean, status: String, reduceMotion: () -> Boolean, content: @Composable () -> Unit) {
    val gate = remember { LaunchGate() }
    val motion = remember { LaunchMotion() }
    var showing by remember { mutableStateOf(intro) }
    if (showing) {
        LaunchedEffect(gate) {
            motion.run(gate, reduceMotion)
            showing = false
        }
    }
    val settle = with(LocalDensity.current) { PlasmaMotion.SCREEN_SETTLE.toPx() }
    CompositionLocalProvider(LocalLaunchGate provides gate) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().then(
                    if (showing) Modifier.graphicsLayer { alpha = motion.contentAlpha; translationY = motion.contentSettle * settle } else Modifier,
                ),
                propagateMinConstraints = true,
            ) {
                content()
            }
            if (showing) {
                LaunchScreen(
                    status = if (motion.statusShown) status else null,
                    modifier = Modifier.graphicsLayer { alpha = 1f - motion.contentAlpha },
                    wordAlpha = { motion.wordAlpha.value },
                    apart = { motion.apart.value },
                    phase = { motion.phase },
                    statusAlpha = { motion.statusAlpha.value },
                )
            }
        }
    }
}

/**
 * The launch screen (#158): graphite, with the wave wordmark at 120 px, its x-height across the
 * middle of the screen, and [status] 72 px under it in Text 3. [wordAlpha], [apart], [phase] and
 * [statusAlpha] are read as it draws.
 */
@Composable
fun LaunchScreen(
    status: String?,
    modifier: Modifier = Modifier,
    wordAlpha: () -> Float = { 1f },
    apart: () -> Float = { 0f },
    phase: () -> Float = { WAVE_PHASE },
    statusAlpha: () -> Float = { 1f },
) {
    Layout(
        content = {
            WaveWordmark(WORD_PX, Modifier.graphicsLayer { alpha = wordAlpha() }, phase = phase, apart = apart)
            if (status != null) {
                PlasmaText(status, plasmaStyle(STATUS_PX, 500), Modifier.graphicsLayer { alpha = statusAlpha() }, color = PlasmaColors.Text3)
            }
        },
        modifier = modifier.fillMaxSize().background(PlasmaColors.Bg),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val word = measurables[0].measure(loose)
        val line = measurables.getOrNull(1)?.measure(loose)
        layout(constraints.maxWidth, constraints.maxHeight) {
            // The middle of the x-height on the middle of the screen; the status line's baseline
            // 72 px under the word's lowest point, the p's descender.
            val baseline = constraints.maxHeight / 2 + (WORD_PX * X_HEIGHT_HALF_EM).px.roundToPx()
            word.place((constraints.maxWidth - word.width) / 2, baseline - word[FirstBaseline])
            if (line != null) {
                val lineBaseline = baseline + (WORD_PX * DESCENDER_EM + STATUS_GAP_PX).px.roundToPx()
                line.place((constraints.maxWidth - line.width) / 2, lineBaseline - line[FirstBaseline])
            }
        }
    }
}

/** The launch screen's motion (#158), each value read as it draws. */
@Stable
private class LaunchMotion {
    val wordAlpha = Animatable(0f)
    val apart = Animatable(START_APART_PX)
    var phase by mutableFloatStateOf(WAVE_PHASE)
    val statusAlpha = Animatable(0f)
    var statusShown by mutableStateOf(false)

    /** How far the launch screen has gone: 0 while it's up, 1 once [content] has taken its place. */
    private val gone = Animatable(0f)
    private var settles by mutableStateOf(true)

    /** The content under it, which fades in as the launch screen fades out. */
    val contentAlpha: Float get() = gone.value

    /** How far the content still has to settle, from 1 (16 dp down) to 0. */
    val contentSettle: Float get() = if (settles) 1f - gone.value else 0f

    suspend fun run(gate: LaunchGate, reduceMotion: () -> Boolean) = coroutineScope {
        val start = withFrameMillis { it }
        val word = launch {
            delay(WORD_IN_MS)
            launch { wordAlpha.animateTo(1f, tween(WORD_FADE_MS, easing = LinearOutSlowInEasing)) }
            if (reduceMotion()) {
                apart.snapTo(0f)
            } else {
                launch { apart.animateTo(0f, tween(COME_TOGETHER_MS, easing = EaseOutCubic)) }
                delay(FLOW_FROM_MS - WORD_IN_MS)
                flow()
            }
        }
        val slow = launch {
            delay(STATUS_FROM_MS)
            statusShown = true
            statusAlpha.animateTo(1f, tween(STATUS_FADE_MS, easing = LinearOutSlowInEasing))
        }
        withTimeoutOrNull(LONGEST_MS) { snapshotFlow { gate.isReady }.first { it } }
        if (!gate.atOnce) {
            val waited = withFrameMillis { it } - start
            if (waited < SHORTEST_MS) delay(SHORTEST_MS - waited)
        }
        slow.cancel()
        // A channel's picture can't move with the screen, and Reduce Motion only fades.
        settles = !gate.atOnce && !reduceMotion()
        gone.animateTo(1f, PlasmaMotion.screen())
        word.cancel()
    }

    private suspend fun flow() = flowWave { phase = it }
}

/**
 * The wave wordmark's wave flowing left to right, as on the launch screen (#158): one wavelength
 * every 2.4 s, coming up to speed over 0.6 s. Each frame's phase goes to [onPhase]; it runs until
 * it's cancelled.
 */
suspend fun flowWave(onPhase: (Float) -> Unit) {
    val from = withInfiniteAnimationFrameMillis { it }
    while (true) {
        withInfiniteAnimationFrameMillis { now ->
            val seconds = (now - from) / 1_000f
            val travelled = if (seconds < RAMP_S) seconds * seconds / (2f * RAMP_S) else seconds - RAMP_S / 2f
            onPhase(WAVE_PHASE - 2f * PI.toFloat() * travelled / WAVE_PERIOD_S)
        }
    }
}

/**
 * The wave wordmark with its wave flowing, as on the launch screen, for screens where Plasma is
 * at work (#158); at rest with Reduce Motion.
 */
@Composable
fun FlowingWordmark(sizePx: Float, modifier: Modifier = Modifier) {
    var phase by remember { mutableFloatStateOf(WAVE_PHASE) }
    if (!LocalReduceMotion.current) LaunchedEffect(Unit) { flowWave { phase = it } }
    WaveWordmark(sizePx, modifier, phase = { phase })
}

/** The word's type, and the status line's, in board pixels. */
private const val WORD_PX = 120f
private const val STATUS_PX = 24f

/** Half the word's x-height, and its descender, in ems: the logo's (tools/gen_logo.py). */
private const val X_HEIGHT_HALF_EM = 0.26f
private const val DESCENDER_EM = 0.205f

/** From the word's lowest point to the status line's baseline. */
private const val STATUS_GAP_PX = 72f

/** How far apart the parts above and below the wave start, in board pixels. */
private const val START_APART_PX = 10f

/** The word starts to show, fades in, and comes together (ease-out cubic). */
private const val WORD_IN_MS = 150L
private const val WORD_FADE_MS = 500
private const val COME_TOGETHER_MS = 800

/** The wave starts to flow, comes up to speed, and moves one wavelength in this long. */
private const val FLOW_FROM_MS = 750L
private const val RAMP_S = 0.6f
private const val WAVE_PERIOD_S = 2.4f

/** Still loading this long after Plasma starts, the status line fades in. */
private const val STATUS_FROM_MS = 2_500L
private const val STATUS_FADE_MS = 300

/** The launch screen stays at least this long, so the word is whole, unless for a channel. */
private const val SHORTEST_MS = 950L

/** However the first screen gets on, the launch screen goes after this long. */
private const val LONGEST_MS = 20_000L
