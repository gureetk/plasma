// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import ca.gureet.plasma.core.design.LocalReduceMotion
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.px
import kotlinx.coroutines.delay

/**
 * The shape of something still loading (#158): Surface 2, with a soft band of light sweeping
 * across the screen every 1.6 s, so skeletons side by side share one sweep. With Reduce Motion it
 * fades gently in and out instead. It takes no focus and no keys.
 */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: Shape = PlasmaShapes.Cell) {
    val reduceMotion = LocalReduceMotion.current
    val turning = rememberInfiniteTransition(label = "skeleton")
    val sweep by turning.animateFloat(0f, 1f, infiniteRepeatable(tween(SWEEP_MS, easing = LinearEasing)), label = "sweep")
    val pulse by turning.animateFloat(1f, PULSE_LOW, infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse), label = "pulse")
    var left by remember { mutableFloatStateOf(0f) }
    var across by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .onGloballyPositioned {
                left = it.positionInRoot().x
                across = it.findRootCoordinates().size.width.toFloat()
            }
            .graphicsLayer { if (reduceMotion) alpha = pulse }
            .clip(shape)
            .background(PlasmaColors.Surface2)
            .drawWithContent {
                drawContent()
                if (!reduceMotion && across > 0f) {
                    // Where the band is on the screen, from just off its left edge to just off its right.
                    val band = SWEEP_BAND_PX.px.toPx()
                    val centre = -band + sweep * (across + 2f * band) - left
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, PlasmaColors.white(SWEEP_ALPHA), Color.Transparent),
                            startX = centre - band / 2f,
                            endX = centre + band / 2f,
                        ),
                    )
                }
            },
    )
}

/**
 * Whether a wait has gone on long enough for a skeleton (#158): false for the first 300 ms after
 * it starts, so a quick load never flashes one. Start it where the wait starts, keyed on what's
 * being waited for.
 */
@Composable
fun rememberLongWait(key: Any?): Boolean {
    var long by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        delay(LONG_WAIT_MS)
        long = true
    }
    return long
}

private const val SWEEP_MS = 1_600
private const val SWEEP_BAND_PX = 560f
private const val SWEEP_ALPHA = 0.06f
private const val PULSE_MS = 900
private const val PULSE_LOW = 0.55f

/** A wait shorter than this shows nothing. */
private const val LONG_WAIT_MS = 300L
