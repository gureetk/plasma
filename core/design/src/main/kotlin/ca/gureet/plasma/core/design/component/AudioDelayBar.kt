// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.R
import ca.gureet.plasma.core.model.AudioDelays

/**
 * Audio Delay's slider (brief 4.28 and 4.35; #116): the [LevelBar] from −500 to +500 ms in 10 ms
 * steps, "Audio delay · Northline News · +120 ms", with a dot every 100 ms. [onChange] has the
 * delay in ms at each step, for the sound to follow as it moves; OK keeps it, Back puts it back.
 *
 * With a [note], the delay can't apply, as with the Android player, and [action] is the way out.
 */
@Composable
fun AudioDelayBar(
    subject: String,
    ms: Int,
    onChange: (ms: Int) -> Unit,
    onKeep: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    LevelBar(
        label = stringResource(R.string.audio_delay_bar),
        subject = subject,
        value = AudioDelays.clamp(ms) / AudioDelays.STEP_MS,
        valueText = delayText(ms),
        min = AudioDelays.MIN_MS / AudioDelays.STEP_MS,
        max = AudioDelays.MAX_MS / AudioDelays.STEP_MS,
        onChange = { step -> onChange(step * AudioDelays.STEP_MS) },
        onKeep = onKeep,
        onCancel = onCancel,
        modifier = modifier,
        note = note,
        action = action,
        onAction = onAction,
        marksEvery = MARK_MS / AudioDelays.STEP_MS,
    )
}

/** "+120 ms", "0 ms" or "−80 ms": an Audio Delay, later or earlier than the picture. */
@Composable
fun delayText(ms: Int): String = when {
    ms > 0 -> stringResource(R.string.delay_later, ms)
    ms < 0 -> stringResource(R.string.delay_earlier, -ms)
    else -> stringResource(R.string.delay_none)
}

/** The bar's dots, every 100 ms. */
private const val MARK_MS = 100
