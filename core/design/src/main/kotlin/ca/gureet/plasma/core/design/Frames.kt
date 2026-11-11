// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Less to do at each frame (#127), with nothing looking different.
 *
 * Android draws all of a Plasma screen again at each frame of a move, the parts that are still as
 * well as the one that moves. A part drawn into a layer of its own, which the GPU keeps, costs one
 * picture at each frame instead of everything in it, until something in it changes.
 */

/**
 * Measures what's in it at its own height, whatever height it's given: for the parts of a row
 * that grows with focus, so that they aren't measured again at each frame of its growth, nor their
 * text laid out again, as text cut short with "…" is at each new height. It takes the room it
 * did, so it sits where it did. Only for content shorter than the row.
 */
fun Modifier.steadyHeight(): Modifier = wrapContentHeight(unbounded = true)

/**
 * Keeps what it draws as a layer while focus is elsewhere, and has been for a moment: for a part
 * of a screen that is often still while another part moves, such as the top bar, a list of
 * categories, the key hints, or what's on under Live TV's picture. While focus is in it, it draws
 * as usual: a kept layer ends at its edges, and focus lifts things past them, with their shadows.
 *
 * Nothing in it may move by itself, as a turning ring does: the layer would then be drawn again at
 * each frame, on top of what's in it.
 */
fun Modifier.keptWhileStill(): Modifier = this then KeptWhileStill

/**
 * `Modifier.alpha`, drawn into a layer the GPU keeps. Android draws a faded part that overlaps
 * itself into a new layer at each frame; this draws it once, and that picture at each frame until
 * the part changes. It looks the same. For a part that stays faded while things move around it,
 * such as a list's row at a cut-off edge.
 */
fun Modifier.keptAlpha(alpha: Float): Modifier =
    if (alpha == 1f) this else graphicsLayer(alpha = alpha, clip = true, compositingStrategy = CompositingStrategy.Offscreen)

/**
 * A layer the GPU keeps while [kept], drawn as usual otherwise: for a part that knows for itself
 * when it's still, such as a row of the Guide the cursor isn't in. While it isn't kept, nothing in
 * it is cut at its edges.
 */
fun Modifier.keptLayer(kept: Boolean): Modifier = graphicsLayer {
    compositingStrategy = if (kept) CompositingStrategy.Offscreen else CompositingStrategy.Auto
}

private data object KeptWhileStill : ModifierNodeElement<KeptWhileStillNode>() {
    override fun create() = KeptWhileStillNode()

    override fun update(node: KeptWhileStillNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "keptWhileStill"
    }
}

private class KeptWhileStillNode : Modifier.Node(), LayoutModifierNode, FocusEventModifierNode {
    /** Read in the layer's block only, so keeping the layer or letting it go doesn't measure again. */
    private var kept by mutableStateOf(false)
    private var focusInside = false
    private var settling: Job? = null

    private val layer: GraphicsLayerScope.() -> Unit = {
        compositingStrategy = if (kept) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }

    override fun onAttach() {
        focusInside = false
        kept = false
        settle()
    }

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.hasFocus == focusInside) return
        focusInside = focusState.hasFocus
        if (focusInside) {
            settling?.cancel()
            kept = false
        } else if (isAttached) {
            settle()
        }
    }

    /** Kept once focus has been elsewhere for long enough that what it did here has settled. */
    private fun settle() {
        settling?.cancel()
        settling = coroutineScope.launch {
            delay(KEEP_AFTER_MS)
            kept = true
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = layer) }
    }
}

/** Past focus's own animations (150 ms), and a little more, so a quick pass through doesn't keep it. */
private const val KEEP_AFTER_MS = 400L
