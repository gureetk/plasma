// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.R
import ca.gureet.plasma.core.design.focusRestoringGroup
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

private val Digits = mapOf(
    Key.Zero to '0', Key.One to '1', Key.Two to '2', Key.Three to '3', Key.Four to '4',
    Key.Five to '5', Key.Six to '6', Key.Seven to '7', Key.Eight to '8', Key.Nine to '9',
    Key.NumPad0 to '0', Key.NumPad1 to '1', Key.NumPad2 to '2', Key.NumPad3 to '3', Key.NumPad4 to '4',
    Key.NumPad5 to '5', Key.NumPad6 to '6', Key.NumPad7 to '7', Key.NumPad8 to '8', Key.NumPad9 to '9',
)

/** The digit a remote's number key types, or null for any other key. */
fun Key.digit(): Char? = Digits[this]

/**
 * A row of number keys, 1 to 0 and then Delete (board 25): 88 px keys, 14 px apart. Focus fills a
 * key white and lifts it to 1.1. The remote's number keys type too; the screen handles those.
 *
 * @param first focus lands here the first time, as on the PIN screen's 1.
 */
@Composable
fun NumberPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    keySize: Dp = 88.px,
    gap: Dp = 14.px,
    first: FocusRequester = remember { FocusRequester() },
) {
    Row(
        modifier.focusRestoringGroup(first),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        "1234567890".forEachIndexed { i, digit ->
            PadKey(
                onClick = { onDigit(digit) },
                size = keySize,
                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
            ) {
                val focused = LocalFocused.current
                PlasmaText(
                    digit.toString(),
                    plasmaStyle(if (focused) 36f else 34f, if (focused) 600 else 500, tabular = true),
                    color = if (focused) PlasmaColors.OnFocus else PlasmaColors.TextSoft3,
                )
            }
        }
        val delete = stringResource(R.string.key_delete)
        PadKey(onClick = onDelete, size = keySize, modifier = Modifier.semantics { contentDescription = delete }) {
            PlasmaIcon(
                PlasmaIcons.Backspace,
                34.px,
                tint = if (LocalFocused.current) PlasmaColors.OnFocus else PlasmaColors.TextSoft,
                stroke = 1.8f,
            )
        }
    }
}

@Composable
private fun PadKey(onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.size(size),
        style = FocusStyle.Key,
        shape = PlasmaShapes.PinKey,
        color = PlasmaColors.Surface2,
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
