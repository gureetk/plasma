// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import kotlinx.coroutines.delay

/** A group or category with its count: Sports 186, Recently Added 212. */
@Immutable
data class CountedItem(
    val id: String,
    val name: String,
    val count: Int,
    /** All Channels, Favorites, Recently Added…: listed above the divider. */
    val smart: Boolean = false,
)

private val RowHeight = 58.px
private val RowGap = 4.px

/** The divider's own height with its 10 px margins. */
private val DividerBlock = 21.px

/** How long focus rests on a row before the screen shows it (brief 4.7). */
private const val SHOW_ON_REST_MS = 300L

/** Scrolls just enough to keep one more row in view past the focused one, at either end. */
@OptIn(ExperimentalFoundationApi::class)
private class ContextBringIntoViewSpec(private val contextPx: Float) : BringIntoViewSpec {
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

/**
 * The column of groups or categories down the left of Live TV, Movies and Series (boards 07, 13
 * and 15): 58 px rows with counts, a divider after the smart ones, the current one marked. Moving
 * through it shows each one once focus rests for a moment; OK opens it.
 *
 * Give it a height. When the rows don't fit, it scrolls, and the row at a cut-off edge is drawn at
 * half strength to say there's more (board 13's "Thriller").
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CountedList(
    items: List<CountedItem>,
    selectedId: String,
    onFocusItem: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = remember { FocusRequester() }
    val scroll = rememberScrollState()
    var viewport by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val pitch = with(density) { (RowHeight + RowGap).toPx() }
    val rowHeight = with(density) { RowHeight.toPx() }
    val dividerExtra = with(density) { (DividerBlock + RowGap).toPx() }
    val firstAfterDivider = items.indices.firstOrNull { it > 0 && items[it - 1].smart && !items[it].smart }

    fun top(index: Int): Float = index * pitch + if (firstAfterDivider != null && index >= firstAfterDivider) dividerExtra else 0f

    // Rows at a cut-off edge, drawn faded.
    val faded by remember(items, pitch) {
        derivedStateOf {
            if (viewport == 0) return@derivedStateOf emptySet<Int>()
            val start = scroll.value.toFloat()
            val end = start + viewport
            val visible = items.indices.filter { top(it) >= start - 1f && top(it) + rowHeight <= end + 1f }
            buildSet {
                visible.lastOrNull()?.let { if (it < items.lastIndex) add(it) }
                visible.firstOrNull()?.let { if (it > 0) add(it) }
            }
        }
    }

    // Opening on a category further down the list: bring it into view.
    LaunchedEffect(viewport > 0) {
        val index = items.indexOfFirst { it.id == selectedId }
        if (viewport > 0 && index >= 0) {
            val bottom = top(index) + rowHeight + pitch
            if (bottom > viewport) scroll.scrollTo((bottom - viewport).toInt())
        }
    }

    val spec = remember(pitch) { ContextBringIntoViewSpec(pitch) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        Column(
            modifier
                .onSizeChanged { viewport = it.height }
                .focusRestorer(selected)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            items.forEachIndexed { i, item ->
                if (i == firstAfterDivider) {
                    Box(Modifier.padding(horizontal = 18.px, vertical = 10.px).fillMaxWidth().height(1.px).background(PlasmaColors.Divider))
                }
                CountedRow(
                    item = item,
                    selected = item.id == selectedId,
                    faded = i in faded,
                    onClick = { onOpenItem(item.id) },
                    onFocused = { onFocusItem(item.id) },
                    modifier = if (item.id == selectedId) Modifier.focusRequester(selected) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun CountedRow(
    item: CountedItem,
    selected: Boolean,
    faded: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused && !selected) {
            delay(SHOW_ON_REST_MS)
            onFocused()
        }
    }
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(RowHeight)
            .alpha(if (faded && !focused) 0.5f else 1f),
        style = FocusStyle.Row,
        shape = PlasmaShapes.Tab,
        selected = selected,
        onFocusChanged = { focused = it },
        contentAlignment = Alignment.CenterStart,
    ) {
        val isFocused = LocalFocused.current
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.px), verticalAlignment = Alignment.CenterVertically) {
            PlasmaText(
                item.name,
                plasmaStyle(25f, if (selected || isFocused) 600 else 500),
                Modifier.weight(1f),
                color = when {
                    isFocused -> PlasmaColors.OnFocus
                    selected -> PlasmaColors.Text
                    else -> PlasmaColors.Text2
                },
            )
            PlasmaText(
                Formats.count(item.count),
                plasmaStyle(21f, 500, tabular = true),
                color = when {
                    isFocused -> PlasmaColors.OnFocus3
                    selected -> PlasmaColors.TextSoft
                    else -> PlasmaColors.Text3
                },
            )
        }
    }
}
