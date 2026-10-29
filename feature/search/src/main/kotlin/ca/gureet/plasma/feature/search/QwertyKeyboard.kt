// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import ca.gureet.plasma.core.design.focusRestoringGroup
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

/** The keyboard's rows of characters: digits, then QWERTY, with what names have in them. */
private val ROWS = listOf("1234567890", "qwertyuiop", "asdfghjkl'", "zxcvbnm-&.")

/** A key's side, and the gap between keys. */
internal const val KEY_PX = 56
internal const val KEY_GAP_PX = 8

/** Ten keys and nine gaps. */
internal const val KEYBOARD_WIDTH_PX = 10 * KEY_PX + 9 * KEY_GAP_PX

/** Five rows and four gaps. */
internal const val KEYBOARD_HEIGHT_PX = 5 * KEY_PX + 4 * KEY_GAP_PX

/**
 * Search's keyboard: QWERTY in a grid, digits above, and Space, Delete, Clear and a mic key below
 * (the mic key for remotes whose mic button the system keeps for itself, brief 4.3). Focus starts
 * on A. Holding Delete clears too. The owner chose it over board 03's one-line keyboard (28 October).
 */
@Composable
internal fun QwertyKeyboard(
    onCharacter: (Char) -> Unit,
    onSpace: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    Column(
        modifier.width(KEYBOARD_WIDTH_PX.px).focusRestoringGroup(first),
        verticalArrangement = Arrangement.spacedBy(KEY_GAP_PX.px),
    ) {
        ROWS.forEachIndexed { row, keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP_PX.px)) {
                keys.forEachIndexed { i, ch ->
                    Key(onClick = { onCharacter(ch) }, modifier = if (row == 2 && i == 0) Modifier.focusRequester(first) else Modifier) {
                        KeyLabel(ch.toString())
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP_PX.px)) {
            val space = stringResource(R.string.key_space)
            Key(onClick = onSpace, width = keys(5), modifier = Modifier.semantics { contentDescription = space }) {
                PlasmaIcon(PlasmaIcons.Space, 28.px, tint = keyTint())
            }
            val delete = stringResource(R.string.key_delete)
            Key(onClick = onDelete, onLongClick = onClear, width = keys(2), modifier = Modifier.semantics { contentDescription = delete }) {
                PlasmaIcon(PlasmaIcons.Backspace, 28.px, tint = keyTint())
            }
            Key(onClick = onClear, width = keys(2)) {
                KeyLabel(stringResource(R.string.key_clear), sizePx = 21f)
            }
            val voice = stringResource(R.string.key_voice)
            Key(onClick = onVoice, modifier = Modifier.semantics { contentDescription = voice }) {
                PlasmaIcon(PlasmaIcons.Mic, 24.px, tint = keyTint())
            }
        }
    }
}

/** The width of [count] keys side by side, with the gaps between them. */
private fun keys(count: Int): Dp = (count * KEY_PX + (count - 1) * KEY_GAP_PX).px

@Composable
private fun keyTint() = if (LocalFocused.current) PlasmaColors.OnFocus else PlasmaColors.TextSoft2

@Composable
private fun KeyLabel(text: String, sizePx: Float = 25f) {
    PlasmaText(text, plasmaStyle(sizePx, 500), color = keyTint())
}

/** A key: a faint plate, white and lifted when focused. */
@Composable
private fun Key(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = KEY_PX.px,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    PlasmaSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.size(width, KEY_PX.px),
        style = FocusStyle.Key,
        shape = PlasmaShapes.SmallPlate,
        color = PlasmaColors.white(0.07f),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
