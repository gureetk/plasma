// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Text in Plasma's type. [color] defaults to the surrounding content colour, so text turns dark
 * by itself on a focused surface. Long text truncates with an ellipsis.
 *
 * Text that a larger text size (Large Text, or Android's own font size) would cut off shrinks
 * instead, to a little under the boards' size at most, when that saves it (#128): a label's room
 * on the boards often can't grow with it. [fit] false keeps it at its size.
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
    fit: Boolean = true,
) {
    val resolved = if (color != Color.Unspecified) color else style.color.takeIf { it != Color.Unspecified } ?: LocalContentColor.current
    val fitted = if (fit) fitToBoards(style) else null
    BasicText(
        text = text,
        modifier = modifier.tightLineHeight(style).forTalkBack(LocalSaidAbove.current).fitsDownTo(fitted),
        style = style.copy(color = resolved, textAlign = align),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = maxLines > 1,
        autoSize = fitted?.let { FitWhenItSaves(it, style.fontSize) },
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
    fit: Boolean = true,
) {
    val resolved = if (color != Color.Unspecified) color else style.color.takeIf { it != Color.Unspecified } ?: LocalContentColor.current
    val fitted = if (fit) fitToBoards(style) else null
    BasicText(
        text = text,
        modifier = modifier.tightLineHeight(style).forTalkBack(LocalSaidAbove.current).fitsDownTo(fitted),
        style = style.copy(color = resolved, textAlign = align),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = maxLines > 1,
        autoSize = fitted?.let { FitWhenItSaves(it, style.fontSize) },
    )
}

/**
 * [PlasmaText]'s fit: its own size when the text fits, as most does, in one layout; else the
 * largest size down to [smallest] at which it fits; else its own size again, cut short, since
 * shrinking wouldn't save it.
 */
private class FitWhenItSaves(private val smallest: TextUnit, private val largest: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        if (!performLayout(constraints, text, largest).cutShort()) return largest
        if (performLayout(constraints, text, smallest).cutShort()) return largest
        var fits = smallest.toPx()
        var cut = largest.toPx()
        val step = FIT_STEP_SP.sp.toPx()
        while (cut - fits > step) {
            val middle = (fits + cut) / 2
            if (performLayout(constraints, text, middle.toSp()).cutShort()) cut = middle else fits = middle
        }
        return fits.toSp()
    }

    override fun equals(other: Any?): Boolean = other is FitWhenItSaves && other.smallest == smallest && other.largest == largest

    override fun hashCode(): Int = 31 * smallest.hashCode() + largest.hashCode()
}

/** Ended in an ellipsis, or ran past its room. */
private fun TextLayoutResult.cutShort(): Boolean =
    didOverflowWidth || didOverflowHeight || (lineCount > 0 && isLineEllipsized(lineCount - 1))

/**
 * The smallest size [PlasmaText]'s `fit` shrinks [style]'s text to at the TV's text size: a little
 * under the size the boards have it ([FIT_FLOOR]). Null at the boards' own size, where the text is
 * drawn as it always was.
 */
@Composable
private fun fitToBoards(style: TextStyle): TextUnit? {
    if (!style.fontSize.isSp) return null
    val boards = with(LocalDensity.current) { style.fontSize.value.dp.toSp() }
    return if (boards.value < style.fontSize.value - FIT_STEP_SP) (boards.value * FIT_FLOOR).sp else null
}

/**
 * How far under the boards' size fitted text may go: a label a hair smaller than at Default
 * reads better than one cut short, where the boards leave it no room to spare.
 */
private const val FIT_FLOOR = 0.9f

/**
 * The size a fitted text ([PlasmaText]'s `fit`) may shrink to. Its semantics give its layout at
 * its own size, so the larger-text check in the screen tests (#128) asks whether it fits at this.
 */
val FitsDownTo = SemanticsPropertyKey<TextUnit>("FitsDownTo")

private fun Modifier.fitsDownTo(size: TextUnit?): Modifier = if (size == null) this else semantics { this[FitsDownTo] = size }

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
 * How wide [sample] is in [style] at the TV's text size. A column the boards size for its text,
 * such as a channel number's, is at least this wide, so a larger text size doesn't cut it off (#128).
 */
@Composable
fun textWidth(sample: String, style: TextStyle): Dp {
    val density = LocalDensity.current
    val fonts = LocalFontFamilyResolver.current
    val direction = LocalLayoutDirection.current
    return remember(sample, style, density, fonts, direction) { MeasuredWidths.of(sample, style, density, fonts, direction) }
}

/**
 * The widths [textWidth] has measured, kept for each of Android's font resolvers, so the rows of a
 * list measure their number column's sample once between them, rather than each one as it comes on
 * screen or takes focus (#127).
 */
private object MeasuredWidths {
    private val known = WeakHashMap<FontFamily.Resolver, MutableMap<Measured, Dp>>()

    fun of(sample: String, style: TextStyle, density: Density, fonts: FontFamily.Resolver, direction: LayoutDirection): Dp = synchronized(this) {
        val widths = known.getOrPut(fonts) { HashMap() }
        widths.getOrPut(Measured(sample, style, density.density, density.fontScale, direction)) {
            if (widths.size >= MOST) widths.clear()
            val measured = TextMeasurer(fonts, density, direction, cacheSize = 0).measure(sample, style, softWrap = false, maxLines = 1)
            with(density) { measured.size.width.toDp() }
        }
    }

    private data class Measured(val sample: String, val style: TextStyle, val density: Float, val fontScale: Float, val direction: LayoutDirection)

    /** More than a screen's columns ever ask for; past it, they're measured afresh. */
    private const val MOST = 64
}

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
    val boards = fitToBoards(style)
    val (line, size) = remember(text, style, maxWidth, density, boards) {
        val width = with(density) { maxWidth.roundToPx() }
        fun at(size: TextUnit) = measurer.measure(
            text,
            style.copy(fontSize = size),
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = width),
        )
        var size = style.fontSize
        var fit = at(size)
        // Larger text (Large Text, or Android's font size) shrinks back as far as the boards' size
        // before it's cut short (#128): the largest size that fits, found by halving.
        if (fit.isLineEllipsized(0) && boards != null && !at(boards).isLineEllipsized(0)) {
            var low = boards.value
            var high = style.fontSize.value
            while (high - low > FIT_STEP_SP) {
                val middle = (low + high) / 2
                if (at(middle.sp).isLineEllipsized(0)) high = middle else low = middle
            }
            size = low.sp
            fit = at(size)
        } else if (fit.isLineEllipsized(0) && boards != null) {
            size = boards
            fit = at(size)
        }
        val shown = if (fit.isLineEllipsized(0)) text.take(fit.getLineEnd(0, visibleEnd = true)).trimEnd() + ELLIPSIS else text
        shown to size
    }
    PlasmaText(line, style.copy(fontSize = size), modifier, color, overflow = TextOverflow.Visible, fit = false)
}

private const val ELLIPSIS = "\u2026"
