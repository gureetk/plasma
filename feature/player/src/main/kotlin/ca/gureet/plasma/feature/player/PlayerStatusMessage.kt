// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.boxShadow
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

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
 * The error card (brief 4.35 and 5.4.9): what can't play and why, with Try Again. Switch Source,
 * Switch Player and Report join it with failover and the other engines (M3).
 */
@Composable
internal fun PlayerErrorCard(channelName: String, problem: PlayerProblem, onRetry: () -> Unit) {
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
                        PlayerProblem.Refused -> R.string.problem_refused
                        PlayerProblem.SourceUnavailable -> R.string.problem_source
                        PlayerProblem.NotVideo -> R.string.problem_not_video
                        PlayerProblem.CantPlay -> R.string.problem_cant_play
                        PlayerProblem.NoStream -> R.string.problem_no_stream
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
