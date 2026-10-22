// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private const val LETTERS = "abcdefghijklmnopqrstuvwxyz"
private const val NUMBERS = "1234567890-'&.:!?"

/**
 * The tvOS-style keyboard in one 60 px bar (board 03): a–z, then 123, Space, Delete, and a mic key
 * for remotes whose mic button the system keeps for itself (brief 4.3). Holding Delete clears.
 */
@Composable
internal fun LinearKeyboard(
    onCharacter: (Char) -> Unit,
    onSpace: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var numbers by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    Row(
        modifier
            .width(1728.px)
            .height(60.px)
            .background(PlasmaColors.KeyboardStrip, PlasmaShapes.Row)
            .padding(horizontal = 14.px)
            .focusRestoringGroup(first),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val keys = if (numbers) NUMBERS else LETTERS
        keys.forEachIndexed { i, ch ->
            Key(
                onClick = { onCharacter(ch) },
                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
            ) {
                KeyLabel(ch.toString())
            }
        }
        Box(Modifier.size(1.px, 28.px).background(PlasmaColors.white(0.12f)))
        Key(onClick = { numbers = !numbers }, width = null) {
            KeyLabel(stringResource(if (numbers) R.string.key_letters else R.string.key_numbers), sizePx = 20f, padding = 12.px)
        }
        val space = stringResource(R.string.key_space)
        Key(onClick = onSpace, width = 64.px, modifier = Modifier.semantics { contentDescription = space }) {
            PlasmaIcon(PlasmaIcons.Space, 26.px, tint = keyTint())
        }
        val delete = stringResource(R.string.key_delete)
        Key(onClick = onDelete, onLongClick = onClear, width = 52.px, modifier = Modifier.semantics { contentDescription = delete }) {
            PlasmaIcon(PlasmaIcons.Backspace, 26.px, tint = keyTint())
        }
        val voice = stringResource(R.string.key_voice)
        Key(onClick = onVoice, modifier = Modifier.semantics { contentDescription = voice }) {
            PlasmaIcon(PlasmaIcons.Mic, 22.px, tint = keyTint())
        }
    }
}

@Composable
private fun keyTint() = if (LocalFocused.current) PlasmaColors.OnFocus else PlasmaColors.Text2

@Composable
private fun KeyLabel(text: String, sizePx: Float = 23f, padding: Dp = 0.px) {
    PlasmaText(text, plasmaStyle(sizePx, 500), Modifier.padding(horizontal = padding), color = keyTint())
}

/** A 44 px key: white and lifted when focused. */
@Composable
private fun Key(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 44.px,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    PlasmaSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.height(44.px).then(if (width != null) Modifier.width(width) else Modifier),
        style = FocusStyle.Key,
        shape = PlasmaShapes.SmallPlate,
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
