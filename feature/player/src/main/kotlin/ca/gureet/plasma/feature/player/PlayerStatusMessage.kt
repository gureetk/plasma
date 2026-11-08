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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.boxShadow
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import kotlinx.coroutines.delay
import ca.gureet.plasma.core.design.R as DesignR

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

/** The error card's buttons, to say which takes focus. */
internal enum class CardButton { TryAgain, SwitchSource, SwitchPlayer, Report, Close }

/**
 * The error card (brief 4.35 and 5.4.9): what can't play and why, and what to do about it (#114):
 * Try Again; Switch Source, when the channel has another source ([onSwitchSource]); Switch Player,
 * when the TV has another player ([onSwitchPlayer]); Report, which opens Report a Problem
 * ([onReport]); and Close, which leaves the player as Back does (the owner's ask, 3 November). Left
 * and Right move between them. [movie]: it's a movie or an episode, and the reasons say so.
 *
 * [focus] takes focus whenever [takesFocus] turns true: when the card shows, and again when what
 * covered it goes, such as the sheet one of its buttons opened. Try Again does when [focus] isn't
 * on the card.
 */
@Composable
internal fun PlayerErrorCard(
    channelName: String,
    problem: PlayerProblem,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    movie: Boolean = false,
    takesFocus: Boolean = true,
    onSwitchSource: (() -> Unit)? = null,
    onSwitchPlayer: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    focus: CardButton = CardButton.TryAgain,
) {
    val buttons = remember { CardButton.entries.associateWith { FocusRequester() } }
    val shows = when (focus) {
        CardButton.SwitchSource -> onSwitchSource != null
        CardButton.SwitchPlayer -> onSwitchPlayer != null
        CardButton.Report -> onReport != null
        CardButton.TryAgain, CardButton.Close -> true
    }
    val focused = if (shows) focus else CardButton.TryAgain
    LaunchedEffect(problem, takesFocus) {
        if (!takesFocus) return@LaunchedEffect
        withFrameNanos { }
        buttons.getValue(focused).requestFocus()
    }
    Box(Modifier.fillMaxSize().background(PlasmaColors.black(0.55f)), contentAlignment = Alignment.Center) {
        CardColumn(
            modifier = Modifier
                .boxShadow(PlasmaColors.black(0.55f), offsetY = 30.px, blur = 70.px, cornerRadius = 22.px)
                .background(PlasmaColors.Toast, PlasmaShapes.Toast)
                .padding(horizontal = 44.px, vertical = 40.px),
            words = {
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
                            PlayerProblem.Encrypted -> if (movie) R.string.problem_file_encrypted else R.string.problem_encrypted
                            PlayerProblem.CantPlay -> if (movie) R.string.problem_file_cant_play else R.string.problem_cant_play
                            PlayerProblem.NoStream -> if (movie) R.string.problem_file_no_stream else R.string.problem_no_stream
                        },
                    ),
                    plasmaStyle(23f, 500, lineHeight = 1.35f),
                    color = PlasmaColors.Text2,
                    align = TextAlign.Center,
                    maxLines = 3,
                )
            },
            buttons = {
                val focusOf = { button: CardButton -> Modifier.focusRequester(buttons.getValue(button)) }
                Row(horizontalArrangement = Arrangement.spacedBy(16.px)) {
                    PlasmaButton(stringResource(R.string.try_again), onClick = onRetry, modifier = focusOf(CardButton.TryAgain))
                    onSwitchSource?.let { PlasmaButton(stringResource(DesignR.string.switch_source), onClick = it, modifier = focusOf(CardButton.SwitchSource)) }
                    onSwitchPlayer?.let { PlasmaButton(stringResource(R.string.switch_player), onClick = it, modifier = focusOf(CardButton.SwitchPlayer)) }
                    onReport?.let { PlasmaButton(stringResource(R.string.report), onClick = it, modifier = focusOf(CardButton.Report)) }
                    PlasmaButton(stringResource(R.string.close), onClick = onClose, modifier = focusOf(CardButton.Close))
                }
            },
        )
    }
}

/**
 * The card's words over its buttons, which the card is wide enough for: 820 px, or 1000 px for all
 * five. It widens by that step rather than to the pixel, so it keeps its width as focus moves and
 * the focused button's words turn bolder.
 */
@Composable
private fun CardColumn(words: @Composable () -> Unit, buttons: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Layout(contents = listOf(words, buttons), modifier = modifier) { (wordParts, buttonParts), constraints ->
        val row = buttonParts.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val needed = row.maxOfOrNull { it.width } ?: 0
        val step = if (needed <= CARD_WORDS_WIDTH.roundToPx()) CARD_WORDS_WIDTH else CARD_WIDE_WORDS_WIDTH
        val width = maxOf(step.roundToPx(), needed).coerceAtMost(constraints.maxWidth)
        val lines = wordParts.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
        val gap = CARD_WORDS_GAP.roundToPx()
        val above = CARD_BUTTONS_GAP.roundToPx()
        val height = lines.sumOf { it.height } + gap * (lines.size - 1).coerceAtLeast(0) + above + row.sumOf { it.height }
        layout(width, height) {
            var y = 0
            lines.forEach { line ->
                line.place(0, y)
                y += line.height + gap
            }
            y += above - gap
            row.forEach { it.place((width - it.width) / 2, y) }
        }
    }
}

/** The card is 820 px wide, or 1000 px, 44 px of it padding on each side. */
private val CARD_WORDS_WIDTH = 732.px
private val CARD_WIDE_WORDS_WIDTH = 912.px
private val CARD_WORDS_GAP = 16.px
private val CARD_BUTTONS_GAP = 28.px
