// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Text in Plasma's type. [color] defaults to the surrounding content colour, so text turns dark
 * by itself on a focused surface. Long text truncates with an ellipsis.
 *
 * With [fit], text that a larger text size (Large Text, or Android's own font size) would cut off
 * shrinks instead, as far as the boards' size (#128): for a label in a room that can't grow with it.
 */
@Composable
fun PlasmaText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Unspecified,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    fit: Boolean = false,
) {
    val resolved = if (color != Color.Unspecified) color else style.color.takeIf { it != Color.Unspecified } ?: LocalContentColor.current
    BasicText(
        text = text,
        modifier = modifier.tightLineHeight(style).forTalkBack(LocalSaidAbove.current),
        style = style.copy(color = resolved, textAlign = align),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = maxLines > 1,
        autoSize = if (fit) fitToBoards(style) else null,
    )
}

@Composable
fun PlasmaText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Unspecified,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    fit: Boolean = false,
) {
    val resolved = if (color != Color.Unspecified) color else style.color.takeIf { it != Color.Unspecified } ?: LocalContentColor.current
    BasicText(
        text = text,
        modifier = modifier.tightLineHeight(style).forTalkBack(LocalSaidAbove.current),
        style = style.copy(color = resolved, textAlign = align),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = maxLines > 1,
        autoSize = if (fit) fitToBoards(style) else null,
    )
}

/**
 * [style]'s size at the TV's text size, down to its size on the boards, for [PlasmaText]'s `fit`;
 * none at the boards' own size, where the text is drawn as it always was.
 */
@Composable
private fun fitToBoards(style: TextStyle): TextAutoSize? {
    if (!style.fontSize.isSp) return null
    // The boards' size in sp at this text size: what draws as large as the style does at Default.
    val boards = with(LocalDensity.current) { style.fontSize.value.dp.toSp() }
    return if (boards.value < style.fontSize.value - FIT_STEP_SP) TextAutoSize.StepBased(boards, style.fontSize, FIT_STEP_SP.sp) else null
}

/** How finely [fitToBoards] tries sizes, in sp. */
private const val FIT_STEP_SP = 0.25f

/**
 * How much [style]'s text grows at the TV's text size against the boards', as Android scales it:
 * 1 at Default, more with Large Text or Android's own font size, small text by the most. A column
 * the boards size for its text, such as a channel number's, grows by as much (#128).
 */
@Composable
fun textGrowth(style: TextStyle): Float =
    if (style.fontSize.isSp) with(LocalDensity.current) { style.fontSize.toDp().value / style.fontSize.value } else 1f

/**
 * The lines a paragraph of [style]'s text needs at the TV's text size where the boards give it
 * [lines]: as many at Default, more with larger text, so it isn't cut off (#128). For a room that
 * grows with its text, such as a sheet's.
 */
@Composable
fun linesAt(lines: Int, style: TextStyle): Int = ceil(lines * textGrowth(style) - LINES_SLACK).toInt().coerceAtLeast(lines)

/** Rounding's own error, so a growth of exactly 1 gives the boards' lines. */
private const val LINES_SLACK = 0.01f

/** A font's own line height (ascent + descent from its hhea table) as a multiple of its size. */
private fun naturalLineHeight(family: FontFamily?): Float = if (family == SpaceMono) 1.481f else 1.22f

/**
 * Compose never makes a text's first line taller-topped or last line shorter than the font's own
 * height allows, even when [TextStyle.lineHeight] asks for less; CSS does. For display type with a
 * line height under the font's own (the boards' titles at 1.04–1.06), trim the difference evenly
 * from top and bottom so titles and what follows them sit where the boards put them.
 */
private fun Modifier.tightLineHeight(style: TextStyle): Modifier {
    val lineHeight = style.lineHeight
    val fontSize = style.fontSize
    if (!fontSize.isSp) return this
    val lineHeightEm = when {
        lineHeight.isEm -> lineHeight.value
        lineHeight.isSp -> lineHeight.value / fontSize.value
        else -> return this
    }
    val excessEm = naturalLineHeight(style.fontFamily) - lineHeightEm
    if (excessEm <= 0f) return this
    return this.layout { measurable, constraints ->
        val excess = (excessEm * style.fontSize.toPx()).roundToInt()
        val placeable = measurable.measure(constraints)
        layout(placeable.width, (placeable.height - excess).coerceAtLeast(0)) {
            placeable.place(0, -excess / 2)
        }
    }
}

/**
 * [text] on one line at most [maxWidth] wide, cut short with "…" by hand rather than clipped: for
 * a caption drawn below its tile, outside the tile's place in a lazy grid or row (a focused
 * poster's). Text that clips draws nothing there: a long movie name, cut short, didn't show under
 * its poster, on a TV or in tests.
 */
@Composable
fun UnclippedLine(text: String, style: TextStyle, maxWidth: Dp, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val line = remember(text, style, maxWidth, density) {
        val fit = measurer.measure(
            text,
            style,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = with(density) { maxWidth.roundToPx() }),
        )
        if (fit.isLineEllipsized(0)) text.take(fit.getLineEnd(0, visibleEnd = true)).trimEnd() + ELLIPSIS else text
    }
    PlasmaText(line, style, modifier, color, overflow = TextOverflow.Visible)
}

private const val ELLIPSIS = "\u2026"
