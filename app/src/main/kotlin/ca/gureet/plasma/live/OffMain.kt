// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A screen's state that takes real work to make, such as Live TV's rows for thousands of channels,
 * the Guide's programs or the player's channel list: made at once the first time, so the screen has
 * it for its first frame, then on [Dispatchers.Default] each time [keys] change (a new minute, a
 * change in the database), the last one standing until the new one is ready. Focus and keys never
 * wait for it (docs/decisions.md, Snappier). [make] reads only what [keys] hold, and what's safe to
 * read off the main thread.
 */
@Composable
internal fun <T> rememberOffMain(vararg keys: Any?, make: () -> T): T {
    val made = remember { Made(keys, make()) }
    LaunchedEffect(*keys) {
        // The first launch is for the keys the first state was made from.
        if (made.isFrom(keys)) return@LaunchedEffect
        val next = withContext(Dispatchers.Default) { make() }
        made.keys = keys
        made.value = next
    }
    return made.value
}

/** What [rememberOffMain] made last, and the keys it was made from. */
private class Made<T>(var keys: Array<out Any?>, first: T) {
    var value by mutableStateOf(first)

    /** Made from these very keys: the same objects, not just equal ones. */
    fun isFrom(other: Array<out Any?>): Boolean = keys.size == other.size && keys.indices.all { keys[it] === other[it] }
}
