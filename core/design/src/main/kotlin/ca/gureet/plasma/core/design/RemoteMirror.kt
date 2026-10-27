// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the phone remote shows of the TV's screen (brief 5.21): the text field taking text, so
 * what's typed on the phone goes straight in, and what the colour keys do here, as the screen's
 * own hints say.
 */
@Stable
class RemoteMirror {
    var field by mutableStateOf<RemoteTypingField?>(null)
        private set

    private val hints = mutableStateListOf<ColorKeyLabel>()

    /** The colour keys' labels on the screen now, by key ("red", "green"…): the newest hint wins. */
    val colorKeys: Map<String, String> get() = hints.associate { it.key to it.label }

    /** A screen's text field takes text from the phone while it's open. */
    fun open(field: RemoteTypingField) {
        this.field = field
    }

    fun close(field: RemoteTypingField) {
        if (this.field === field) this.field = null
    }

    /** A colour-key hint is on screen. */
    fun show(hint: ColorKeyLabel) {
        hints += hint
    }

    fun hide(hint: ColorKeyLabel) {
        hints.remove(hint)
    }
}

/**
 * A field the phone can type into: what it's for, its text now, what new text and Done do, what
 * it's found ("4 programs · 2 movies"), if anything, and whether it's a search.
 */
class RemoteTypingField(
    val label: String,
    val text: () -> String,
    val onText: (String) -> Unit,
    val onSubmit: () -> Unit = {},
    val results: () -> String? = { null },
    val search: Boolean = false,
)

/** A colour key's label on screen; each hint is its own, so taking one away leaves the others. */
class ColorKeyLabel(val key: String, val label: String)

/** The app's [RemoteMirror]; null where no phone can see, as in previews and tests. */
val LocalRemoteMirror = staticCompositionLocalOf<RemoteMirror?> { null }
