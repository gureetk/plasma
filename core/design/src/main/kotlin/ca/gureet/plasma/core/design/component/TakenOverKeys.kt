// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

/**
 * Keys for a lazy list's rows that the next list's rows take over (#127).
 *
 * A lazy list keeps each row's composition under the row's key. When what the list shows gives way
 * to something else's rows, as one channel's schedule does to the next channel's, keys made from
 * the items are all new ones: every row is taken down and another made, though each new row is the
 * shape of the one it replaces. With these keys the list can stay the same list:
 *
 * - While it's one owner's, a key stays with its item. A row that moves up as the one above it
 *   ends is still the same row, with its focus and its place in what the list remembers.
 * - When the owner changes, the new owner's rows are given the last one's keys, in order. Each row
 *   is then composed again where it is, with what it now shows, as if only its words had changed.
 * - A row that has taken focus keeps its key to itself: the new owner's row in its place is a new
 *   one. Focus doesn't pass from one owner's row to another's, and a list that brings focus back
 *   to the row that had it last (`focusRestorer`) knows that row by its key, so the next owner's
 *   list starts with no such row, as a new list would.
 *
 * For one list, from its composition.
 */
class TakenOverKeys {
    private var owner: Any? = null
    private var byId: Map<Any, Int> = emptyMap()
    private var inOrder = IntArray(0)
    private val tookFocus = HashSet<Int>()
    private var next = 0

    /**
     * The keys of [owner]'s rows, in the order of [ids], which tell its items apart. Asked again
     * for the same owner's rows, each item has the key it had.
     */
    fun keysFor(owner: Any?, ids: List<Any>): IntArray {
        val another = owner != this.owner
        val keys = IntArray(ids.size)
        val live = HashMap<Any, Int>(ids.size * 2)
        for (i in ids.indices) {
            val id = ids[i]
            val had = if (another) inOrder.getOrNull(i)?.takeIf { it !in tookFocus } else byId[id]
            // An id that comes twice has a key of its own the second time: a list's keys all differ.
            val key = if (had == null || id in live) next++ else had
            keys[i] = key
            live[id] = key
        }
        // The keys that took focus have gone with the last owner's rows.
        if (another) tookFocus.clear()
        this.owner = owner
        byId = live
        inOrder = keys
        return keys
    }

    /** The row with [key] has taken focus: the next owner's row in its place is a new one. */
    fun tookFocus(key: Int) {
        tookFocus += key
    }
}
