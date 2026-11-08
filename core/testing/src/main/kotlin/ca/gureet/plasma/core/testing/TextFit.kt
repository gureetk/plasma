// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.testing

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt

// Large Text and Android's own font size (#128): text that fits on a screen as it's drawn, laid
// out again with its font larger, in the room it has now. Android TV's text scaling goes to 130 %,
// and Settings › Appearance › Text Size › Large adds 15 % on top. The room is what the text was
// given, so a row that would grow with its text isn't seen to; this finds where to look.

/** The font sizes looked at: Large Text, Android's largest, and the two together. */
val LargerText = listOf(1.15f, 1.3f, 1.15f * 1.3f)

/** A piece of text that a larger font would cut off: its words, where it is in board px, and the smallest [scale] that does. */
data class CutOff(val words: String, val bounds: Rect, val scale: Float) {
    override fun toString(): String =
        "\"${words.replace('\n', ' ').take(60)}\" at ${bounds.left.roundToInt()}, ${bounds.top.roundToInt()} " +
            "(${bounds.width.roundToInt()} × ${bounds.height.roundToInt()} px) from ${(scale * 100).roundToInt()} %"
}

/**
 * The text on screen that fits as it's drawn and that a font [scales] times larger would cut off:
 * end in an ellipsis, or run past its room or its lines.
 */
fun SemanticsNodeInteractionsProvider.textCutOffAt(scales: List<Float> = LargerText): List<CutOff> {
    // The screen: the first root's bounds, with a sheet or dialog over it as roots of their own.
    val screen = onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
        .firstOrNull()?.boundsInRoot ?: return emptyList()
    return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .filter { it.boundsInRoot.overlaps(screen) }
        .mapNotNull { node ->
            val layout = node.textLayout() ?: return@mapNotNull null
            if (layout.layoutInput.text.isBlank() || layout.isCutOff()) return@mapNotNull null
            val scale = scales.firstOrNull { layout.at(it).isCutOff() } ?: return@mapNotNull null
            CutOff(layout.layoutInput.text.text, node.boundsInRoot, scale)
        }
}

private fun SemanticsNode.textLayout(): TextLayoutResult? {
    val results = mutableListOf<TextLayoutResult>()
    config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
    return results.firstOrNull()
}

/** The same text laid out with its font [scale] times larger, in the same room. */
private fun TextLayoutResult.at(scale: Float): TextLayoutResult {
    val input = layoutInput
    val larger = Density(input.density.density, input.density.fontScale * scale)
    return TextMeasurer(input.fontFamilyResolver, larger, input.layoutDirection).measure(
        text = input.text,
        style = input.style,
        overflow = input.overflow,
        softWrap = input.softWrap,
        maxLines = input.maxLines,
        placeholders = input.placeholders,
        constraints = input.constraints,
        layoutDirection = input.layoutDirection,
        density = larger,
        fontFamilyResolver = input.fontFamilyResolver,
    )
}

/** Cut off: ended in an ellipsis, or running past its room or its lines. */
private fun TextLayoutResult.isCutOff(): Boolean = hasVisualOverflow || (lineCount > 0 && isLineEllipsized(lineCount - 1))
