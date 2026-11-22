// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * [this], a flow a screen reads, worked out off the thread that collects it, which for a screen is
 * the main thread; but where it's collected while Plasma starts, until [started] says the first
 * screen has what it shows (#127).
 *
 * What a flow works out upstream of its collector (rows turned into what shows, lists sorted and
 * matched, the next read of a chain begun) holds the main thread up when a screen's reads all
 * start at once, as they do each time a screen comes back: moved off it, they run side by side on
 * other threads, and only their answers come to the main thread.
 *
 * While Plasma starts, that's the wrong way round. The launch screen waits for the first
 * screen's own data alone, and a TV's processors are full with making it; whatever else the first
 * screen reads, run all at once beside it, makes it later. Collected on the main thread, those
 * reads go a step at a time between its frames, and most of their work falls after the first
 * screen is out. So a collection that begins while Plasma starts stays where it's collected, for
 * as long as it lasts, and one that begins after is moved.
 */
fun <T> Flow<T>.offMainOnce(started: () -> Boolean): Flow<T> {
    val upstream = this
    val moved = upstream.flowOn(Dispatchers.Default)
    return flow { emitAll(if (started()) moved else upstream) }
}
