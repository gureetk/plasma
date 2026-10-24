// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.boxShadow
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.px

/**
 * Move mode's arrows (board 04): a white 40 px disc with a chevron beside an item being moved,
 * where the D-pad will carry it. An app on Apps, a favorite on Home or in Live TV's Favorites.
 */
@Composable
fun MoveArrow(icon: PlasmaIcons, description: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(40.px)
            .boxShadow(PlasmaColors.black(0.5f), offsetY = 8.px, blur = 20.px, cornerRadius = 20.px)
            .background(PlasmaColors.Text, CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        PlasmaIcon(icon, 20.px, tint = PlasmaColors.OnFocus, stroke = 3f)
    }
}

/**
 * Move mode in a list or row (board 04): [items] with the one at [from] carried [delta] places
 * along, or null when it can't go further. The list holds still, rather than following the item
 * as it would by itself, and scrolls only as far as it takes to keep [context] items either side
 * of it wholly in view. The list's items are [items] alone, all the same size.
 */
fun <T> LazyListState.carry(items: List<T>, from: Int, delta: Int, context: Int = 1): List<T>? {
    val to = from + delta
    if (from !in items.indices || to !in items.indices) return null
    keepInView(to, items.size, context)
    return items.toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * For the list's next layout, once its items have changed (a move, or a move put back): it holds
 * still, and scrolls only as far as it takes to keep the item at [index] of [count] wholly in view
 * with [context] items either side.
 */
fun LazyListState.keepInView(index: Int, count: Int, context: Int = 1) {
    val info = layoutInfo
    val end = info.viewportEndOffset - info.afterContentPadding
    val whole = info.visibleItemsInfo.filter { it.offset >= 0 && it.offset + it.size <= end }
    val first = whole.firstOrNull()?.index
    val last = whole.lastOrNull()?.index
    val wantFirst = (index - context).coerceAtLeast(0)
    val wantLast = (index + context).coerceAtMost(count - 1)
    when {
        index !in 0 until count || first == null || last == null -> requestScrollToItem(firstVisibleItemIndex, firstVisibleItemScrollOffset)
        wantFirst < first -> requestScrollToItem(wantFirst)
        wantLast > last -> requestScrollToItem(first + (wantLast - last))
        else -> requestScrollToItem(firstVisibleItemIndex, firstVisibleItemScrollOffset)
    }
}
