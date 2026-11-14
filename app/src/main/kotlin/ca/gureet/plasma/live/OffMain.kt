// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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

/**
 * A screen's state kept made while the screen isn't open, so that opening the screen doesn't make
 * it in its first frame (#127): Live TV's rows for every channel were the largest single thing in
 * that frame, at each opening and at each Back from a channel. While [keep] runs, the state is made
 * on [on] from each [K] that comes, the last one standing until the next is ready, as
 * [rememberOffMain] does it while a screen shows. [make] reads only what its [K] holds, and what's
 * safe to read off the main thread.
 */
class MadeAhead<K : Any, T : Any>(private val on: CoroutineDispatcher = Dispatchers.Default, private val make: (K) -> T) {
    /** What was made, and what it was made from. */
    class Made<K, T>(val from: K, val value: T)

    private val kept = MutableStateFlow<Made<K, T>?>(null)

    /** What's made now; null before the first is ready, and while [keep] isn't running. */
    val made: StateFlow<Made<K, T>?> get() = kept

    /**
     * Makes the state from each of [from] as it comes, for as long as this runs. One that comes
     * while another is being made takes its place. What was made is let go when this ends, since
     * nothing keeps it current then.
     */
    suspend fun keep(from: Flow<K>) {
        try {
            from.collectLatest { latest -> kept.value = Made(latest, withContext(on) { make(latest) }) }
        } finally {
            kept.value = null
        }
    }
}

/**
 * [ahead]'s state for a screen as it opens and while it shows. As it opens: what's kept, when
 * [isCurrent] says that was made from what the screen shows now; otherwise [make]'s, made at once
 * as [rememberOffMain] makes its first, so the first frame has it either way. After that, whatever
 * [ahead] makes next.
 */
@Composable
internal fun <K : Any, T : Any> rememberMadeAhead(ahead: MadeAhead<K, T>, isCurrent: (K) -> Boolean, make: () -> T): T {
    val opened = remember { Opened(ahead.made.value, isCurrent, make) }
    LaunchedEffect(ahead) { ahead.made.collect { opened.take(it) } }
    return opened.shown
}

/**
 * For the benchmarks alone, and not for `main`: Live TV's rows made at once as it opens, and
 * nothing kept made, as before #229, so that one run measures an opening both ways. Plasma reads
 * the choice as it starts.
 */
object LiveTvRowsForBenchmarks {
    @Volatile var atOpening = false
}

/** What a screen shows of what's made ahead, from what was kept as it opened ([first]) on. */
internal class Opened<K : Any, T : Any>(private val first: MadeAhead.Made<K, T>?, isCurrent: (K) -> Boolean, make: () -> T) {
    /**
     * A state is shown when it's another object than the one before: each making gives a new one,
     * and telling two apart by what they hold would mean reading every channel's row.
     */
    var shown: T by mutableStateOf(first?.takeIf { isCurrent(it.from) }?.value ?: make(), referentialEqualityPolicy())
        private set

    /**
     * [made] is what's kept now. Nothing kept changes nothing; neither does [first], which was
     * either shown from the start or made from something the screen no longer showed.
     */
    fun take(made: MadeAhead.Made<K, T>?) {
        if (made != null && made !== first) shown = made.value
    }
}
