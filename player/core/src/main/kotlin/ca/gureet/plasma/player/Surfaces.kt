// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

/**
 * The surfaces a picture can show on, the newest last (brief 5.20): Plasma's own screen, and the
 * small window's while it's open. [same] tells whether two are the same surface.
 */
internal class Surfaces<T : Any>(private val same: (T, T) -> Boolean) {
    private val list = ArrayList<T>()

    /** The one the picture shows on: the newest. */
    val top: T? get() = list.lastOrNull()

    /** [surface] arrives, and the picture goes to it. Arriving again, it moves to the top. */
    fun add(surface: T) {
        list.removeAll { same(it, surface) }
        list.add(surface)
    }

    /**
     * [surface] goes. When the picture was on it, the answer holds the one it goes back to
     * (null for none); when it wasn't, or [surface] wasn't here, there's no answer: nothing moves.
     */
    fun remove(surface: T): Under<T>? {
        val wasTop = top?.let { same(it, surface) } == true
        val found = list.removeAll { same(it, surface) }
        return if (found && wasTop) Under(top) else null
    }

    /** What the picture goes back to: [value], or nothing. */
    class Under<T>(val value: T?)
}
