// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.boxShadow
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import kotlinx.coroutines.delay

/** "Reconnecting…" at the top while a dropped stream comes back; the last frame stays up (5.4.8). */
@Composable
internal fun ReconnectingPill() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        PlasmaText(
            stringResource(R.string.reconnecting),
            plasmaStyle(22f, 600),
            Modifier
                .offset(y = 56.px)
                .background(PlasmaColors.Toast, PlasmaShapes.Toast)
                .padding(horizontal = 26.px, vertical = 14.px),
        )
    }
}

/**
 * A ring turning in the middle while what was chosen opens, or fills its buffer again, so it's
 * plain that Plasma is at it (brief 2.9, honest status). It waits a moment first, so a quick start
 * never flashes it.
 */
@Composable
internal fun LoadingRing() {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_AFTER_MS)
        shown = true
    }
    if (!shown) return
    val turning = rememberInfiniteTransition(label = "loading")
    val angle by turning.animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(tween(LOADING_TURN_MS, easing = LinearEasing)),
        label = "angle",
    )
    val label = stringResource(R.string.loading)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(76.px).semantics { contentDescription = label }) {
            val width = 6.px.toPx()
            drawArc(PlasmaColors.white(0.16f), 0f, FULL_TURN, useCenter = false, style = Stroke(width))
            drawArc(PlasmaColors.white(0.9f), angle - QUARTER_TURN, ARC, useCenter = false, style = Stroke(width, cap = StrokeCap.Round))
        }
    }
}

private const val LOADING_AFTER_MS = 400L
private const val LOADING_TURN_MS = 900
private const val FULL_TURN = 360f
private const val QUARTER_TURN = 90f
private const val ARC = 100f

/**
 * The error card (brief 4.35 and 5.4.9): what can't play and why, with Try Again. Switch Source,
 * Switch Player and Report join it with failover and the other engines (M3). [movie]: it's a
 * movie or an episode, and the reasons say so.
 */
@Composable
internal fun PlayerErrorCard(channelName: String, problem: PlayerProblem, onRetry: () -> Unit, movie: Boolean = false) {
    val retry = remember { FocusRequester() }
    LaunchedEffect(problem) {
        withFrameNanos { }
        retry.requestFocus()
    }
    Box(Modifier.fillMaxSize().background(PlasmaColors.black(0.55f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(820.px)
                .boxShadow(PlasmaColors.black(0.55f), offsetY = 30.px, blur = 70.px, cornerRadius = 22.px)
                .background(PlasmaColors.Toast, PlasmaShapes.Toast)
                .padding(horizontal = 44.px, vertical = 40.px),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.px),
        ) {
            PlasmaText(
                stringResource(R.string.cant_play, channelName),
                plasmaStyle(34f, 600, trackingPx = -0.3f),
                align = TextAlign.Center,
                maxLines = 2,
            )
            PlasmaText(
                stringResource(
                    when (problem) {
                        PlayerProblem.ConnectionLimit -> R.string.problem_connection_limit
                        PlayerProblem.Refused -> if (movie) R.string.problem_file_refused else R.string.problem_refused
                        PlayerProblem.SourceUnavailable -> if (movie) R.string.problem_file_source else R.string.problem_source
                        PlayerProblem.NotVideo -> if (movie) R.string.problem_file_not_video else R.string.problem_not_video
                        PlayerProblem.CantPlay -> if (movie) R.string.problem_file_cant_play else R.string.problem_cant_play
                        PlayerProblem.NoStream -> if (movie) R.string.problem_file_no_stream else R.string.problem_no_stream
                    },
                ),
                plasmaStyle(23f, 500, lineHeight = 1.35f),
                color = PlasmaColors.Text2,
                align = TextAlign.Center,
                maxLines = 3,
            )
            PlasmaButton(
                stringResource(R.string.try_again),
                onClick = onRetry,
                modifier = Modifier.padding(top = 12.px).focusRequester(retry),
            )
        }
    }
}
