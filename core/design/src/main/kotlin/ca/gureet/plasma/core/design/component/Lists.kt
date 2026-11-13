// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocusPreview
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.LocalHeldKeys
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.keptAlpha
import ca.gureet.plasma.core.design.keptWhileStill
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.design.textGrowth
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
    /** Locked with the parental PIN (brief 5.12): a lock beside its count. */
    val locked: Boolean = false,
)

private val RowHeight = 58.px
private val RowGap = 4.px

/** The divider's own height with its 10 px margins. */
private val DividerBlock = 21.px

/** How long focus rests on a row before the screen shows it (brief 4.7). */
private const val SHOW_ON_REST_MS = 300L

/** More rows than a screen is tall: the rows made before the list knows its own height. */
private const val FIRST_ROWS = 18

/** How many rows past each edge of the list's window, and either side of the row with focus, are made. */
private const val NEAR_ROWS = 2

/**
 * The rows of a [CountedList] that are made: those in its [window] and a few past it; those around
 * the row with [focus], where that isn't in the window; and, until the list has scrolled to the
 * row it opens on, the rows it will then show ([landing]).
 */
private data class MadeRows(val window: IntRange, val focus: IntRange, val landing: IntRange) {
    operator fun contains(row: Int): Boolean = row in window || row in focus || row in landing
}

private fun IntRange.within(other: IntRange): Boolean = isEmpty() || (first >= other.first && last <= other.last)

/**
 * The column of groups or categories down the left of Live TV, Movies and Series (boards 07, 13
 * and 15): 58 px rows with counts, a divider after the smart ones, the current one marked. Moving
 * through it shows each one once focus rests for a moment; OK opens it.
 *
 * Give it a height. When the rows don't fit, it scrolls, and the row at a cut-off edge is drawn at
 * half strength to say there's more (board 13's "Thriller").
 *
 * Only the rows in its window, and a few past it, are made (#127): a row out of reach is an empty
 * place of its own height until the window comes near, so a provider's hundreds of groups open as
 * fast as a dozen. The list is as long as it was, and every row is where it was.
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

    // Opening on a category further down the list: bring it into view. Until then the rows it
    // will show there are made too, so the frame that scrolls to them finds them.
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(viewport > 0) {
        if (viewport > 0) {
            val index = items.indexOfFirst { it.id == selectedId }
            if (index >= 0) {
                val bottom = top(index) + rowHeight + pitch
                if (bottom > viewport) scroll.scrollTo((bottom - viewport).toInt())
            }
            opened = true
        }
    }

    // The rows that are made. An arrow that's held moves by more rows the longer it's held, so
    // while it is, rows are made as far as its next repeat can go.
    var reach by remember { mutableIntStateOf(NEAR_ROWS) }
    var focusedRow by remember { mutableIntStateOf(-1) }
    val selectedIndex = items.indexOfFirst { it.id == selectedId }
    val made by remember(items, pitch, selectedIndex) {
        derivedStateOf {
            val last = items.lastIndex
            val window = if (viewport == 0) {
                0..minOf(last, FIRST_ROWS - 1)
            } else {
                // Where the window will be once the list is laid out: no further down than its rows go.
                val start = minOf(scroll.value.toFloat(), (top(last) + rowHeight - viewport).coerceAtLeast(0f))
                val end = start + viewport
                val first = items.indices.firstOrNull { top(it) + rowHeight > start } ?: 0
                val lastIn = items.indices.lastOrNull { top(it) < end } ?: last
                (first - reach).coerceAtLeast(0)..(lastIn + reach).coerceAtMost(last)
            }
            val focus = if (focusedRow in 0..last) (focusedRow - reach).coerceAtLeast(0)..(focusedRow + reach).coerceAtMost(last) else IntRange.EMPTY
            val landing = if (!opened && selectedIndex >= 0) (selectedIndex - FIRST_ROWS).coerceAtLeast(0)..(selectedIndex + 1 + NEAR_ROWS).coerceAtMost(last) else IntRange.EMPTY
            MadeRows(window, if (focus.within(window)) IntRange.EMPTY else focus, if (landing.within(window)) IntRange.EMPTY else landing)
        }
    }

    // A held Up or Down moves through the rows itself (the owner's ask, 1 November): further the
    // longer it's held, the list scrolled at once to keep a row past the one focused in view, and it
    // stops at the list's ends rather than going up to the top bar.
    val held = LocalHeldKeys.current
    val rows = remember(items.size) { List(items.size) { FocusRequester() } }
    // A row a held arrow went to before it was made: it takes focus as soon as it is.
    var awaited by remember { mutableIntStateOf(-1) }
    fun heldMove(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyUp) reach = NEAR_ROWS
        if (!held.holding || event.type != KeyEventType.KeyDown || items.isEmpty()) return false
        val direction = when (event.key) {
            Key.DirectionUp -> -1
            Key.DirectionDown -> 1
            else -> return false
        }
        val from = focusedRow.takeIf { it in items.indices } ?: return false
        val stride = held.stride()
        val to = (from + direction * stride).coerceIn(0, items.lastIndex)
        // Its next repeat goes as far again, or twice as far.
        reach = maxOf(NEAR_ROWS, 2 * stride)
        if (to != from) {
            val start = scroll.value.toFloat()
            val wanted = when {
                top(to) - pitch < start -> top(to) - pitch
                top(to) + rowHeight + pitch > start + viewport -> top(to) + rowHeight + pitch - viewport
                else -> start
            }.coerceIn(0f, scroll.maxValue.toFloat())
            scroll.dispatchRawDelta(wanted - start)
            awaited = if (runCatching { rows[to].requestFocus(FocusDirection.Enter) }.getOrDefault(false)) -1 else to
            // The next repeat goes on from here, even if it comes before focus has said it moved.
            focusedRow = to
        }
        return true
    }

    val spec = remember(pitch) { ContextBringIntoViewSpec(pitch) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        Column(
            modifier
                .onSizeChanged { viewport = it.height }
                .onPreviewKeyEvent(::heldMove)
                .focusRestorer(selected)
                // Kept as a layer, the size of its window, while focus is in another column (#127).
                .keptWhileStill()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            items.forEachIndexed { i, item ->
                if (i == firstAfterDivider) {
                    Box(Modifier.padding(horizontal = 18.px, vertical = 10.px).fillMaxWidth().height(1.px).background(PlasmaColors.Divider))
                }
                if (i == selectedIndex || i in made) {
                    CountedRow(
                        item = item,
                        selected = item.id == selectedId,
                        faded = i in faded,
                        onClick = { onOpenItem(item.id) },
                        onFocused = { onFocusItem(item.id) },
                        onFocusChanged = { if (it) focusedRow = i },
                        modifier = Modifier
                            .focusRequester(rows[i])
                            .then(if (item.id == selectedId) Modifier.focusRequester(selected) else Modifier),
                    )
                } else {
                    // A row out of reach: its place, with nothing in it.
                    Spacer(Modifier.fillMaxWidth().height(RowHeight))
                }
            }
        }
    }
    AwaitedFocus(awaited, rows) { awaited = -1 }
}

/** Gives focus to the row a held arrow went to before it was made, once a composition has made it. */
@Composable
private fun AwaitedFocus(row: Int, rows: List<FocusRequester>, onTaken: () -> Unit) {
    if (row !in rows.indices) return
    SideEffect {
        if (rows[row].requestFocus(FocusDirection.Enter)) onTaken()
    }
}

@Composable
private fun CountedRow(
    item: CountedItem,
    selected: Boolean,
    faded: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val focused = hasFocus || LocalFocusPreview.current
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
            .keptAlpha(if (faded && !focused) 0.5f else 1f),
        style = FocusStyle.Row,
        shape = PlasmaShapes.Tab,
        selected = selected,
        onFocusChanged = {
            hasFocus = it
            onFocusChanged(it)
        },
        contentAlignment = Alignment.CenterStart,
        keptWhileStill = true,
    ) {
        val isFocused = LocalFocused.current
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.px), verticalAlignment = Alignment.CenterVertically) {
            val style = plasmaStyle(25f, if (selected || isFocused) 600 else 500)
            PlasmaText(
                item.name,
                style,
                // Fitted to its room at a larger text size, it keeps clear of the count.
                Modifier.weight(1f).padding(end = if (textGrowth(style) > 1.01f) 8.px else 0.px),
                color = when {
                    isFocused -> PlasmaColors.OnFocus
                    selected -> PlasmaColors.Text
                    else -> PlasmaColors.Text2
                },
            )
            val countColor = when {
                isFocused -> PlasmaColors.OnFocus3
                selected -> PlasmaColors.TextSoft
                else -> PlasmaColors.Text3
            }
            if (item.locked) PlasmaIcon(PlasmaIcons.Lock, 18.px, Modifier.padding(end = 10.px), tint = countColor)
            PlasmaText(Formats.count(item.count), plasmaStyle(21f, 500, tabular = true), color = countColor)
        }
    }
}
