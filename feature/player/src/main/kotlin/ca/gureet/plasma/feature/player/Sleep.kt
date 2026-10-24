// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.ActionToast
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * Sleep Timer (brief 4.10): Off, 30, 60, 90 or 120 minutes, or the end of the program on screen
 * when it has [programLeft]. The one set is ticked, with the time it has left. Picking one closes
 * Player Options; Back returns to it.
 */
@Composable
internal fun SleepTimerSheet(
    channel: String,
    sleep: SleepInfo?,
    programLeft: Duration?,
    now: Instant,
    onPick: (SleepChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = sleep?.choice ?: SleepChoice.Off
    val left = sleep?.let { sleepLeft(it, now) }
    OptionsSheet(
        title = stringResource(R.string.sleep_timer),
        subtitle = channel,
        onDismiss = onDismiss,
        options = SleepChoice.entries
            .filter { it != SleepChoice.EndOfProgram || programLeft != null || it == chosen }
            .map { choice ->
                SheetOption(
                    sleepChoiceName(choice),
                    onClick = { onPick(choice) },
                    value = when {
                        choice == chosen -> left
                        choice == SleepChoice.EndOfProgram -> programLeft?.let(Formats::duration)
                        else -> null
                    },
                    icon = PlasmaIcons.Moon,
                    checked = choice == chosen,
                )
            },
    )
}

/** "30 Minutes", "End of Program". */
@Composable
private fun sleepChoiceName(choice: SleepChoice): String = stringResource(
    when (choice) {
        SleepChoice.Off -> R.string.off
        SleepChoice.Minutes30 -> R.string.sleep_30
        SleepChoice.Minutes60 -> R.string.sleep_60
        SleepChoice.Minutes90 -> R.string.sleep_90
        SleepChoice.Minutes120 -> R.string.sleep_120
        SleepChoice.EndOfProgram -> R.string.sleep_end_of_program
    },
)

/** "42 min left": the time a set timer has left, never less than a minute. */
@Composable
internal fun sleepLeft(sleep: SleepInfo, now: Instant): String =
    stringResource(R.string.min_left, Formats.duration(Duration.between(now, sleep.at).coerceAtLeast(MINUTE)))

private val MINUTE: Duration = Duration.ofMinutes(1)

/**
 * Sleep Timer's last minute (brief 4.10), at the top right like a reminder: Keep Watching turns
 * the timer off, and Stop Now stops the channel at once. Keep Watching has focus.
 */
@Composable
fun SleepToast(onKeepWatching: () -> Unit, onStopNow: () -> Unit) {
    ActionToast(
        icon = PlasmaIcons.Moon,
        line = stringResource(R.string.sleep_timer),
        title = stringResource(R.string.sleep_soon),
        primary = stringResource(R.string.keep_watching),
        onPrimary = onKeepWatching,
        secondary = stringResource(R.string.stop_now),
        onSecondary = onStopNow,
    )
}

/**
 * What Sleep Timer leaves (brief 4.10): the picture and sound stopped, and a dark screen that
 * doesn't keep the TV awake, so the TV's own power settings can turn it off. Its words fade after
 * ten seconds, so nothing stays lit; any button but the volume wakes it, and [onWake] plays the
 * channel again. [fade] is off in screenshot tests.
 */
@Composable
fun AsleepScreen(onWake: () -> Unit, fade: Boolean = true) {
    val focus = remember { FocusRequester() }
    var dark by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focus.requestFocus()
    }
    LaunchedEffect(fade) {
        if (!fade) return@LaunchedEffect
        delay(WORDS_MS)
        dark = true
    }
    val words by animateFloatAsState(if (dark) 0f else 1f, tween(FADE_MS), label = "asleepWords")
    Box(
        Modifier
            .fillMaxSize()
            .background(PlasmaColors.black(1f))
            .onKeyEvent { event ->
                if (event.key in VolumeKeys) return@onKeyEvent false
                if (event.type == KeyEventType.KeyUp) onWake()
                true
            }
            .focusRequester(focus)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.alpha(words), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.px)) {
            PlasmaIcon(PlasmaIcons.Moon, 44.px, tint = PlasmaColors.Text3)
            PlasmaText(stringResource(R.string.asleep), plasmaStyle(30f, 600, trackingPx = -0.3f), color = PlasmaColors.Text2)
            PlasmaText(stringResource(R.string.asleep_hint), plasmaStyle(22f, 500), color = PlasmaColors.Text3)
        }
    }
}

/** The volume stays the TV's while Plasma is dark. */
private val VolumeKeys = setOf(Key.VolumeUp, Key.VolumeDown, Key.VolumeMute)

private const val WORDS_MS = 10_000L
private const val FADE_MS = 1_000
