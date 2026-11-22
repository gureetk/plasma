// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import ca.gureet.plasma.core.design.px

// A Guide row's cells, placed by the row and not by themselves (#127).
//
// The window moves under a row at a key, and most of what's in the row then only slides: a program
// that was in the window and still is has the same words, the same width and the same colors. With
// each cell given its place as a modifier of its own, every cell of every row on screen was composed
// again at each such key, since its modifier was a new one. Here a cell says only when its program
// starts and ends ([guideSlot]), which doesn't change as the window moves, and the row's layout
// ([GuideCells]) works out where that falls. So a cell whose program stays is skipped when its row
// composes again: the layout measures it only if its width changed, and places it anew.
//
// A program that comes into the window takes over the cell of one that has left ([CellKeys]), so
// that cell is composed again with other words where one was let go and another made. For that a
// row's cells are composed in the order of their keys, which stays the same, and are placed, and so
// drawn, in the order of their programs' times, which is the order they were composed in before: a
// focused cell's shadow still falls over the cell before it and under the cell after.

/** When a cell's program starts and ends, in epoch milliseconds: what [GuideCells] places it by. */
internal fun Modifier.guideSlot(start: Long, end: Long): Modifier = this then GuideSlotElement(start, end)

private data class GuideSlotElement(val start: Long, val end: Long) : ModifierNodeElement<GuideSlotNode>() {
    override fun create() = GuideSlotNode(start, end)

    // Compose asks the node for its parent data again after this, and the row measures again.
    override fun update(node: GuideSlotNode) {
        node.start = start
        node.end = end
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "guideSlot"
        properties["start"] = start
        properties["end"] = end
    }
}

private class GuideSlotNode(var start: Long, var end: Long) : Modifier.Node(), ParentDataModifierNode {
    override fun Density.modifyParentData(parentData: Any?): Any = CellSlot(start, end)
}

private class CellSlot(val start: Long, val end: Long)

/** The room between two cells, 2 px taken from each. */
internal const val CELL_GAP_PX = 4f

/** Where a program that starts at [start] has its left edge in the window from [windowStart], in board pixels. */
internal fun cellLeft(start: Long, windowStart: Long): Float = (maxOf(start, windowStart) - windowStart) * PX_PER_MS

/** How much of a program from [start] to [end] is in the two-hour window from [windowStart], in board pixels. */
internal fun cellWidth(start: Long, end: Long, windowStart: Long): Float =
    (minOf(end, windowStart + GuideNavigator.WINDOW) - maxOf(start, windowStart)) * PX_PER_MS

/**
 * A row's time area. Each cell with a [guideSlot] is as wide as the part of its program in the
 * two-hour window from [windowStart] (epoch milliseconds), less the room between cells, and as tall
 * as the row; the cells are drawn in the order their programs start in, whatever order they were
 * composed in. Anything else in it (what a row with nothing in the window shows) is placed at its
 * start, as in a box.
 */
@Composable
internal fun GuideCells(windowStart: Long, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier, remember(windowStart) { GuideCellsPolicy(windowStart) })
}

private class GuideCellsPolicy(private val windowStart: Long) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val rowHeight = ROW_PX.px.roundToPx().coerceAtMost(loose.maxHeight)
        val count = measurables.size
        val lefts = IntArray(count)
        // Whatever isn't a cell comes before the cells.
        val starts = LongArray(count) { Long.MIN_VALUE }
        val ends = LongArray(count)
        var width = 0
        var height = 0
        val placeables = Array<Placeable>(count) { index ->
            val measurable = measurables[index]
            val slot = measurable.parentData as? CellSlot
            val placeable = if (slot == null) {
                measurable.measure(loose)
            } else {
                // The same sums, rounded the same way, as the cell's own offset and size made.
                lefts[index] = (cellLeft(slot.start, windowStart) + CELL_GAP_PX / 2).px.roundToPx()
                starts[index] = slot.start
                ends[index] = slot.end
                val wide = (cellWidth(slot.start, slot.end, windowStart) - CELL_GAP_PX).px.roundToPx()
                measurable.measure(Constraints.fixed(wide.coerceIn(0, loose.maxWidth), rowHeight))
            }
            width = maxOf(width, placeable.width)
            height = maxOf(height, placeable.height)
            placeable
        }
        // By when their programs start; of two that start together, in a guide that has such, the
        // shorter goes over the longer.
        val order = placesInOrder(count) { a, b -> starts[a] > starts[b] || (starts[a] == starts[b] && ends[a] < ends[b]) }
        return layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
            // Children given no z-index of their own are drawn in the order they're placed.
            for (index in order) placeables[index].placeRelative(lefts[index], 0)
        }
    }
}

/**
 * The keys of one row's cells as the window moves under it.
 *
 * A program that was in the window and still is keeps its key, so its cell is the one it had. A
 * program that comes in takes the key of one that has left, so that cell is composed again for it
 * rather than let go and another made; with none to take, it gets a new key.
 */
internal class CellKeys {
    private var heldIds = emptyList<String>()
    private var heldKeys = IntArray(0)
    private var made = 0

    /**
     * The key of each of [ids], a row's programs in the window, in their order. Asked again with
     * the same programs, it answers the same.
     */
    fun keysFor(ids: List<String>): IntArray {
        val before = heldIds
        val beforeKeys = heldKeys
        val kept = BooleanArray(before.size)
        val keys = IntArray(ids.size) { index ->
            val had = before.indexOf(ids[index])
            // An id that's there twice keeps its key once.
            if (had >= 0 && !kept[had]) {
                kept[had] = true
                beforeKeys[had]
            } else {
                NO_KEY
            }
        }
        // What the programs that left let go of, the lowest first, so the same move gives the same keys.
        val free = beforeKeys.filterIndexed { had, _ -> !kept[had] }.sorted()
        var taken = 0
        for (index in keys.indices) {
            if (keys[index] == NO_KEY) keys[index] = if (taken < free.size) free[taken++] else made++
        }
        heldIds = ids
        heldKeys = keys
        return keys
    }

    private companion object {
        const val NO_KEY = -1
    }
}

/** The places of [keys] in the order of the keys themselves: the order a row's cells are composed in. */
internal fun inKeyOrder(keys: IntArray): IntArray = placesInOrder(keys.size) { a, b -> keys[a] > keys[b] }

/**
 * The places 0 until [count], in order, by an insertion sort: a row has a handful of cells.
 * [after] says whether what's at the first place goes after what's at the second; places that
 * needn't swap stay as they were.
 */
private inline fun placesInOrder(count: Int, after: (Int, Int) -> Boolean): IntArray {
    val order = IntArray(count) { it }
    for (next in 1 until count) {
        val place = order[next]
        var at = next - 1
        while (at >= 0 && after(order[at], place)) {
            order[at + 1] = order[at]
            at--
        }
        order[at + 1] = place
    }
    return order
}
