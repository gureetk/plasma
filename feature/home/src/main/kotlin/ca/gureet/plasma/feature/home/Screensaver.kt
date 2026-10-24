// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.rememberNow
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import ca.gureet.plasma.core.design.R as DesignR

@Immutable
data class ScreensaverUiState(
    val art: String?,
    val artAlignment: Alignment = Alignment.Center,
    /** What's recording now, if anything: "● Recording Harbour City v Redmoor". */
    val recording: String? = null,
)

/** How far the text block drifts, at most, so nothing burns in (4.6). */
private const val DRIFT_PX = 16f

/**
 * The screensaver (board 06, brief 4.6): full-bleed art, the time, the date and what's recording at
 * the bottom left, and the wordmark at the bottom right. It shows over whatever was on screen; the
 * key that wakes Plasma is used up, so it doesn't also act on the screen underneath. The text moves
 * to a new spot within 16 px every minute.
 *
 * @param drift false keeps the text where the board has it (tests and previews).
 */
@Composable
fun ScreensaverScreen(state: ScreensaverUiState, modifier: Modifier = Modifier, drift: Boolean = true) {
    val now by rememberNow()
    // One step around a circle each minute: the text never sits still for long, and never moves far.
    val angle = (now.hour * 60 + now.minute) * (2 * PI / 7)
    val dx = if (drift) (DRIFT_PX * cos(angle)).toFloat() else 0f
    val dy = if (drift) (DRIFT_PX * sin(angle)).toFloat() else 0f
    val density = LocalDensity.current
    val glow = with(density) { Shadow(PlasmaColors.black(0.35f), Offset(0f, 4.px.toPx()), blurRadius = 30.px.toPx()) }
    val description = stringResource(R.string.screensaver_description)
    Box(modifier.fillMaxSize().background(PlasmaColors.Black).semantics { contentDescription = description }) {
        PlasmaImage(state.art, Modifier.fillMaxSize(), alignment = state.artAlignment, placeholder = PlasmaColors.Black)
        Box(Modifier.fillMaxSize().background(Scrim))

        Box(Modifier.fillMaxSize().offset { IntOffset(dx.px.roundToPx(), dy.px.roundToPx()) }) {
            PlasmaText(
                Formats.time(now),
                plasmaStyle(150f, 500, trackingPx = -5f, lineHeight = 1f, tabular = true).copy(shadow = glow),
                Modifier.align(Alignment.BottomStart).padding(start = 96.px, bottom = 150.px),
                color = PlasmaColors.white(1f),
            )
            PlasmaText(
                Formats.longDate(now),
                plasmaStyle(32f, 500, trackingPx = -0.3f),
                Modifier.align(Alignment.BottomStart).padding(start = 102.px, bottom = 98.px),
                color = PlasmaColors.TextSoft3,
            )
            if (state.recording != null) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(start = 102.px, bottom = 54.px),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.px),
                ) {
                    Box(Modifier.size(9.px).background(PlasmaColors.Live, CircleShape))
                    PlasmaText(stringResource(R.string.recording_title, state.recording), plasmaStyle(21f, 500), color = PlasmaColors.TextSoft)
                }
            }
            PlasmaText(
                stringResource(DesignR.string.wordmark),
                plasmaStyle(30f, 600, trackingPx = -1f),
                Modifier.align(Alignment.BottomEnd).padding(end = 96.px, bottom = 58.px),
                color = PlasmaColors.white(0.55f),
            )
        }
    }
}

/**
 * CSS `linear-gradient(20deg, rgba(0,0,0,.62), rgba(0,0,0,.2) 38%, transparent 60%)` across the
 * whole screen: darkest at the bottom left, under the time.
 */
private val Scrim = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val radians = 20.0 * PI / 180
        val dirX = sin(radians).toFloat()
        val dirY = -cos(radians).toFloat()
        // CSS makes the gradient line long enough for its ends to reach the corners.
        val length = abs(size.width * dirX) + abs(size.height * dirY)
        val center = Offset(size.width / 2, size.height / 2)
        val half = Offset(dirX * length / 2, dirY * length / 2)
        return LinearGradientShader(
            from = center - half,
            to = center + half,
            colors = listOf(PlasmaColors.black(0.62f), PlasmaColors.black(0.2f), PlasmaColors.black(0f)),
            colorStops = listOf(0f, 0.38f, 0.6f),
        )
    }
}
