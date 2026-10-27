// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
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
import ca.gureet.plasma.core.design.R as DesignR

private const val DIGITS = "1234567890"

/**
 * The keyboard's sets of letter keys. Board 21 draws small letters only; capitals and symbols are
 * there for passwords, serial numbers and signatures, which often have them.
 */
private enum class KeyLayer(val rows: List<String>, @StringRes val key: Int, @StringRes val spoken: Int) {
    Letters(listOf("qwertyuiop", "asdfghjkl:", "zxcvbnm./"), R.string.key_capitals, R.string.key_capitals_label),
    Capitals(listOf("QWERTYUIOP", "ASDFGHJKL:", "ZXCVBNM./"), R.string.key_symbols, R.string.key_symbols_label),
    Symbols(listOf("!@#$%^&*()", "~`+=[]{};'", "\",<>?\\|./"), R.string.key_letters, R.string.key_letters_label),
    ;

    val next: KeyLayer get() = entries[(ordinal + 1) % entries.size]
}

/** The shortcut keys for a web address, and their widths: board 21's. */
internal val URL_SHORTCUTS = listOf("http://" to 120, ".com" to 96, ".net" to 96, ":8080" to 104, "/c/" to 84)

/**
 * The keyboard for addresses (board 21): digits and letters, the characters URLs need, and a row
 * of [shortcuts] (`http://`, `.com`, `:8080`…), Space and Next. Next moves to the next field. The
 * key before Z goes on to capitals, then symbols, then small letters again.
 */
@Composable
internal fun SourceKeyboard(
    onCharacters: (String) -> Unit,
    onDelete: () -> Unit,
    onNext: () -> Unit,
    first: FocusRequester,
    modifier: Modifier = Modifier,
    shortcuts: List<Pair<String, Int>> = URL_SHORTCUTS,
) {
    var layer by remember { mutableStateOf(KeyLayer.Letters) }
    val rows = listOf(DIGITS) + layer.rows
    Column(modifier.fillMaxWidth().focusRestoringGroup(first), verticalArrangement = Arrangement.spacedBy(8.px)) {
        rows.forEachIndexed { row, keys ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.px)) {
                if (row == rows.lastIndex) {
                    val spoken = stringResource(layer.spoken)
                    TextKey(
                        stringResource(layer.key),
                        onClick = { layer = layer.next },
                        modifier = Modifier.weight(1f).semantics { contentDescription = spoken },
                        color = PlasmaColors.white(0.16f),
                        strong = true,
                    )
                }
                keys.forEachIndexed { i, key ->
                    TextKey(
                        key.toString(),
                        onClick = { onCharacters(key.toString()) },
                        modifier = Modifier.weight(1f).then(if (row == 0 && i == 0) Modifier.focusRequester(first) else Modifier),
                    )
                }
                if (row == rows.lastIndex) {
                    val delete = stringResource(DesignR.string.key_delete)
                    KeyboardKey(onDelete, Modifier.weight(1f).semantics { contentDescription = delete }) {
                        PlasmaIcon(PlasmaIcons.Backspace, 26.px, tint = keyTint(), stroke = 2f)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.px)) {
            for ((text, width) in shortcuts) {
                TextKey(text, onClick = { onCharacters(text) }, modifier = Modifier.width(width.px))
            }
            TextKey("-", onClick = { onCharacters("-") }, modifier = Modifier.weight(1f))
            TextKey("_", onClick = { onCharacters("_") }, modifier = Modifier.weight(1f))
            TextKey(stringResource(R.string.key_space), onClick = { onCharacters(" ") }, modifier = Modifier.width(170.px))
            TextKey(
                stringResource(R.string.key_next),
                onClick = onNext,
                modifier = Modifier.width(150.px),
                color = PlasmaColors.white(0.16f),
                strong = true,
            )
        }
    }
}

@Composable
private fun keyTint() = if (LocalFocused.current) PlasmaColors.OnFocus else PlasmaColors.TextSoft2

@Composable
private fun TextKey(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = PlasmaColors.Surface2, strong: Boolean = false) {
    KeyboardKey(onClick, modifier, color) {
        PlasmaText(
            text,
            plasmaStyle(22f, if (strong || LocalFocused.current) 600 else 500),
            color = if (LocalFocused.current) PlasmaColors.OnFocus else if (strong) PlasmaColors.Text else PlasmaColors.TextSoft2,
        )
    }
}

@Composable
private fun KeyboardKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PlasmaColors.Surface2,
    height: Dp = 48.px,
    content: @Composable () -> Unit,
) {
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.height(height),
        style = FocusStyle.Key,
        shape = PlasmaShapes.Key,
        color = color,
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
