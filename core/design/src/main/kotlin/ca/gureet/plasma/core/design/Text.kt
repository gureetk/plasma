// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.foundation.text.BasicText
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
import kotlin.math.roundToInt

/**
 * Text in Plasma's type. [color] defaults to the surrounding content colour, so text turns dark
 * by itself on a focused surface. Long text truncates with an ellipsis.
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
) {
    val resolved = if (color != Color.Unspecified) color else style.color.takeIf { it != Color.Unspecified } ?: LocalContentColor.current
    BasicText(
        text = text,
        modifier = modifier.tightLineHeight(style),
        style = style.copy(color = resolved, textAlign = align),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = maxLines > 1,
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
) {
    val resolved = if (color != Color.Unspecified) color else style.color.takeIf { it != Color.Unspecified } ?: LocalContentColor.current
    BasicText(
        text = text,
        modifier = modifier.tightLineHeight(style),
        style = style.copy(color = resolved, textAlign = align),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = maxLines > 1,
    )
}

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
