// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import ca.gureet.plasma.core.design.LocalReduceMotion
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.px

/**
 * A ring turning, for a wait with no shape of its own to show (#158): a faint circle with a bright
 * arc going round it, once every 0.9 s, as the player's. With Reduce Motion the arc stands still.
 */
@Composable
fun TurningRing(sizePx: Float, modifier: Modifier = Modifier, strokePx: Float = sizePx / RING_STROKE_PART) {
    // The angle is read as the ring draws, so it turns without composing again at each frame (#127).
    val angle: State<Float> = if (LocalReduceMotion.current) {
        remember { mutableFloatStateOf(0f) }
    } else {
        val turning = rememberInfiniteTransition(label = "ring")
        turning.animateFloat(
            initialValue = 0f,
            targetValue = FULL_TURN,
            animationSpec = infiniteRepeatable(tween(TURN_MS, easing = LinearEasing)),
            label = "angle",
        )
    }
    Canvas(modifier.size(sizePx.px)) {
        val width = strokePx.px.toPx()
        val inset = width / 2f
        val arcSize = Size(size.width - width, size.height - width)
        val topLeft = Offset(inset, inset)
        drawArc(PlasmaColors.white(RING_TRACK_ALPHA), 0f, FULL_TURN, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width))
        drawArc(PlasmaColors.white(RING_ARC_ALPHA), angle.value - QUARTER_TURN, ARC, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width, cap = StrokeCap.Round))
    }
}

private const val TURN_MS = 900
private const val FULL_TURN = 360f
private const val QUARTER_TURN = 90f
private const val ARC = 100f
private const val RING_TRACK_ALPHA = 0.16f
private const val RING_ARC_ALPHA = 0.9f

/** The ring's stroke is this part of its size, as the player's (6 px on 76 px). */
private const val RING_STROKE_PART = 12.7f
