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
import kotlin.math.roundToInt

// Large Text and Android's own font size (#128). Android TV's text scaling goes to 130 %, and
// Settings › Appearance › Text Size › Large adds 15 % on top: [ScreenshotTest] draws each screen
// again at those sizes and notes the text they cut off that fits at the boards' size.

/** The text sizes looked at, against the boards': Large Text, Android's largest, and the two together. */
val LargerText = listOf(1.15f, 1.3f, 1.15f * 1.3f)

/** A piece of text cut off on screen: its words, and where it is in board px. */
data class CutOff(val words: String, val bounds: Rect) {
    override fun toString(): String =
        "\"${words.replace('\n', ' ').take(60)}\" at ${bounds.left.roundToInt()}, ${bounds.top.roundToInt()} " +
            "(${bounds.width.roundToInt()} × ${bounds.height.roundToInt()} px)"
}

/** The text on screen that's cut off as it's drawn: ending in an ellipsis, or running past its room or its lines. */
fun SemanticsNodeInteractionsProvider.textCutOff(): List<CutOff> {
    // The screen: the first root's bounds, with a sheet or dialog over it as roots of their own.
    val screen = onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
        .firstOrNull()?.boundsInRoot ?: return emptyList()
    return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .filter { it.boundsInRoot.overlaps(screen) }
        .mapNotNull { node ->
            val layout = node.textLayout() ?: return@mapNotNull null
            if (layout.layoutInput.text.isBlank() || !layout.isCutOff()) return@mapNotNull null
            CutOff(layout.layoutInput.text.text, node.boundsInRoot)
        }
}

private fun SemanticsNode.textLayout(): TextLayoutResult? {
    val results = mutableListOf<TextLayoutResult>()
    config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
    return results.firstOrNull()
}

/** Cut off: ended in an ellipsis, or running past its room or its lines. */
private fun TextLayoutResult.isCutOff(): Boolean = hasVisualOverflow || (lineCount > 0 && isLineEllipsized(lineCount - 1))
