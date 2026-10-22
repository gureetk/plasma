// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.px

/**
 * Scrolls just enough to bring the focused item into view with [contextPx] more past it, at either
 * end: one more row of a list (Live TV's groups), or nothing more for a grid that shows whole rows.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class ContextBringIntoViewSpec(private val contextPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leading = offset - contextPx
        val trailing = offset + size + contextPx - containerSize
        return when {
            leading < 0f -> leading
            trailing > 0f -> trailing
            else -> 0f
        }
    }
}

/** Scrolls so the focused item's leading edge sits [fraction] of the way into the container. */
@OptIn(ExperimentalFoundationApi::class)
private class FractionBringIntoViewSpec(private val fraction: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - fraction * containerSize
}

/**
 * A vertical list that keeps the focused item about 30 % of the way down while there's room to
 * scroll (brief 6.6), so focus never runs to the bottom edge and scrolling doesn't jump. With
 * [pivot] null it scrolls only when the focused item would leave the list, keeping [context] more
 * in view past it (a channel list down the side). Focus comes back to the item that had it last,
 * or the first time to [focusFallback].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlasmaColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    gap: Dp = 0.px,
    contentPadding: PaddingValues = PaddingValues(),
    pivot: Float? = 0.3f,
    context: Dp = 0.px,
    focusFallback: FocusRequester = FocusRequester.Default,
    content: LazyListScope.() -> Unit,
) {
    val contextPx = with(LocalDensity.current) { context.toPx() }
    val spec = remember(pivot, contextPx) { if (pivot != null) FractionBringIntoViewSpec(pivot) else ContextBringIntoViewSpec(contextPx) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        LazyColumn(
            modifier = modifier.focusRestorer(focusFallback),
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(gap),
            content = content,
        )
    }
}

/**
 * A grid of posters (Movies and Series, boards 13 and 15) that keeps the focused row about 30 % of
 * the way down while there's room to scroll, like [PlasmaColumn]. Focus comes back to the poster
 * that had it last. With [pivot] null it scrolls only as far as the focused row needs, for a grid
 * that shows two whole rows at a time (Recordings, board 17).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlasmaGrid(
    columns: Int,
    columnGap: Dp,
    rowGap: Dp,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(),
    pivot: Float? = 0.3f,
    content: LazyGridScope.() -> Unit,
) {
    val spec = remember(pivot) { if (pivot != null) FractionBringIntoViewSpec(pivot) else ContextBringIntoViewSpec(0f) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = modifier.focusRestorer(),
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(columnGap),
            verticalArrangement = Arrangement.spacedBy(rowGap),
            content = content,
        )
    }
}

/**
 * Scrolls so the focused item's leading edge sits [pivotPx] from the container's edge. The list
 * clamps at its end, so the last items come into view normally.
 */
@OptIn(ExperimentalFoundationApi::class)
private class LeadingEdgeBringIntoViewSpec(private val pivotPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - pivotPx
}

/**
 * A row of tiles that runs from the screen's left edge to its right, starting at x 96 and peeking
 * past the right edge. Moving right keeps the focused tile at the left (x 96) until the row reaches
 * its end (brief 4.2). Focus comes back to the tile that had it last, or the first time to
 * [focusFallback] (the current episode, say) when that's on screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlasmaRow(
    gap: Dp,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    startPadding: Dp = 96.px,
    endPadding: Dp = 96.px,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    focusFallback: FocusRequester = FocusRequester.Default,
    content: LazyListScope.() -> Unit,
) {
    val pivot = with(LocalDensity.current) { startPadding.toPx() }
    val spec = remember(pivot) { LeadingEdgeBringIntoViewSpec(pivot) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        LazyRow(
            modifier = modifier.focusRestorer(focusFallback),
            state = state,
            contentPadding = PaddingValues(start = startPadding, end = endPadding),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = verticalAlignment,
            content = content,
        )
    }
}
