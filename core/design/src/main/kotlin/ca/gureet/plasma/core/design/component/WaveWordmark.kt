// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.R
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

/** Where the wave wordmark's wave starts, in radians, as the logo has it. */
const val WAVE_PHASE = 2.6f

/**
 * Plasma's wave wordmark (the owner's logo of 30 October 2026): "plasma" in Instrument Sans 600 at
 * [sizePx] board pixels, cut once through the middle of its x-height by a wave, as
 * `tools/gen_logo.py` draws it. [phase] moves the wave along, as the launch screen flows it, and
 * [apart] holds the parts above and below the wave that many board pixels apart, as they come
 * together. Both are read as it draws, so moving them doesn't compose it again. It's the size the
 * logo is whatever the TV's font scale, and its first baseline is the word's.
 */
@Composable
fun WaveWordmark(
    sizePx: Float,
    modifier: Modifier = Modifier,
    color: Color = PlasmaColors.Text,
    phase: () -> Float = { WAVE_PHASE },
    apart: () -> Float = { 0f },
) {
    val word = stringResource(R.string.wordmark)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val layout = remember(word, sizePx, density, measurer) {
        val style = plasmaStyle(sizePx, 600, trackingPx = TRACKING_EM * sizePx).copy(fontSize = (sizePx / 2f / density.fontScale).sp)
        measurer.measure(word, style, softWrap = false, maxLines = 1, density = density)
    }
    val width = with(density) { layout.size.width.toDp() }
    val height = with(density) { layout.size.height.toDp() }
    Spacer(
        modifier
            .semantics { contentDescription = word }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height, mapOf(FirstBaseline to layout.firstBaseline.roundToInt())) { placeable.place(0, 0) }
            }
            .size(width, height)
            .drawWithCache {
                val em = sizePx.px.toPx()
                val cut = WaveCut(em, layout.firstBaseline, phase(), size.width, size.height)
                onDrawBehind {
                    val gap = (apart() / 2f).px.toPx()
                    translate(top = -gap) { clipPath(cut.above) { drawText(layout, color) } }
                    translate(top = gap) { clipPath(cut.below) { drawText(layout, color) } }
                }
            },
    )
}

/**
 * The parts of the word above and below its wave, as clips: everything above the wave's upper
 * edge, and everything below its lower edge, with room around for the letters and for the parts
 * moving apart. [em] is the type's size in pixels.
 */
private class WaveCut(em: Float, baseline: Float, phase: Float, width: Float, height: Float) {
    val above = Path()
    val below = Path()

    init {
        val centre = baseline - X_HEIGHT_EM * em / 2f
        val amplitude = AMPLITUDE_EM * em
        val half = THICKNESS_EM * em / 2f
        val k = 2f * PI.toFloat() / (WAVELENGTH_EM * em)
        val margin = em
        val step = STEP_EM * em
        val steps = ceil((width + 2f * margin) / step).toInt()
        above.moveTo(-margin, -margin)
        above.lineTo(width + margin, -margin)
        below.moveTo(-margin, height + margin)
        below.lineTo(width + margin, height + margin)
        // Along the wave from right to left, so each outline closes back where it started.
        for (i in steps downTo 0) {
            val x = -margin + i * step
            val y = centre + amplitude * sin(k * x + phase)
            above.lineTo(x, y - half)
            below.lineTo(x, y + half)
        }
        above.close()
        below.close()
    }
}

/** The logo's geometry, in ems of the type (tools/gen_logo.py). */
private const val TRACKING_EM = -0.032f
private const val X_HEIGHT_EM = 0.52f
private const val THICKNESS_EM = 0.055f
private const val AMPLITUDE_EM = 0.03f
private const val WAVELENGTH_EM = 1.6f

/** How finely the wave is followed: about 2 px at the launch screen's size. */
private const val STEP_EM = 0.008f
