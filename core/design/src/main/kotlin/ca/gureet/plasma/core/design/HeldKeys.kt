// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * The key held down, if one is (the owner's ask of 1 November). A remote sends a held key again and
 * again, about 20 times a second. Held, an arrow moves through a list faster the longer it's held,
 * scrolling without easing, and it never carries focus out of the list or up into the top bar. A
 * press moves one item, as before, and can leave the list.
 */
@Stable
class HeldKeys {
    /** The key repeating now, or null when none is. */
    var key: Key? = null
        private set

    /** How long [key] has been held, in milliseconds, as of its last repeat. */
    var heldMs: Long = 0
        private set

    /** A key is held: what's handled now is one of its repeats. */
    val holding: Boolean get() = key != null

    /** Notes [event] on its way to the item with focus. */
    fun saw(event: KeyEvent) {
        val native = event.nativeKeyEvent
        if (event.type == KeyEventType.KeyDown && native.repeatCount > 0) {
            key = event.key
            heldMs = native.eventTime - native.downTime
        } else {
            key = null
            heldMs = 0
        }
    }

    /**
     * How many items a held arrow moves at once: one for its first second, then two, four from 2.5
     * seconds and eight from 4, so a list of thousands goes by in seconds. A press moves one.
     */
    fun stride(): Int = when {
        !holding || heldMs < STRIDE_2_MS -> 1
        heldMs < STRIDE_4_MS -> 2
        heldMs < STRIDE_8_MS -> 4
        else -> 8
    }

    companion object {
        /** How long an arrow is held before it moves two items at a time, then four, then eight. */
        const val STRIDE_2_MS = 1_000L
        const val STRIDE_4_MS = 2_500L
        const val STRIDE_8_MS = 4_000L
    }
}

/** The keys as the screen's root sees them (see [watchHeldKeys]); never held where nothing watches. */
val LocalHeldKeys = staticCompositionLocalOf { HeldKeys() }

/** Keeps [keys] up to date with each key on its way to focus: on the root of every screen. */
fun Modifier.watchHeldKeys(keys: HeldKeys): Modifier = onPreviewKeyEvent { event ->
    keys.saw(event)
    false
}

/** Which ways a held arrow can't take focus out of a list: see [keepsHeldKeys]. */
enum class HeldWays(internal val directions: List<FocusDirection>) {
    /** A column: Up and Down stop at its ends; Left and Right still lead out of it. */
    Vertical(listOf(FocusDirection.Up, FocusDirection.Down)),

    /** A row: Left and Right stop at its ends. */
    Horizontal(listOf(FocusDirection.Left, FocusDirection.Right)),

    /** A grid, or the top bar: no arrow takes focus out of it, or into it, while held. */
    All(listOf(FocusDirection.Up, FocusDirection.Down, FocusDirection.Left, FocusDirection.Right)),
}

/**
 * A list or grid that a held arrow doesn't leave in [ways]: it stops at the list's end, and a press
 * goes on from there as before (the owner's ask, 1 November). Put it before the list's own focus
 * modifiers, such as its focus restorer, which it wraps in a focus group of its own.
 */
fun Modifier.keepsHeldKeys(keys: HeldKeys, ways: HeldWays): Modifier =
    focusProperties {
        onExit = { if (keys.holding && requestedFocusDirection in ways.directions) cancelFocusChange() }
    }.focusGroup()

/**
 * Something a held arrow doesn't move focus into, as the top bar: focus coming to it by an arrow
 * held from below stops where it is, and a press brings it there as before.
 */
fun Modifier.refusesHeldKeys(keys: HeldKeys): Modifier =
    focusProperties {
        onEnter = { if (keys.holding && requestedFocusDirection in HeldWays.All.directions) cancelFocusChange() }
    }.focusGroup()
