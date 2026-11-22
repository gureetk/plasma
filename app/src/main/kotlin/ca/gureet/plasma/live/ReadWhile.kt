// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/*
 * A flow read as `collectAsState` reads it, but only while [reading]: for a screen that's kept
 * made under the screens that open over it (#127). While it's under, nothing it shows can be
 * seen, so nothing is collected: the flow isn't kept running for it, and no new value composes it
 * again. It keeps the last value it had.
 *
 * In front again, a state flow's value is read at once, in the composition that brings the screen
 * back, as a screen made from nothing reads it: what the screen then does with it (the channel
 * its picture plays) must be today's from its first frame. Any other flow gives its latest when
 * it next has one, and the last value shows until then, where a screen made from nothing had
 * none.
 *
 * The value outlives the flow it came from: where a new flow takes the last one's place (one made
 * again at each coming to the front), the last value shows until the new one's first.
 */

/** [this] read while [reading], from its value now. */
// Its value is read as this composes on purpose, as `collectAsState` itself reads a state flow's
// for its first value: what Lint asks for instead (`collectAsState`) is what this is in place of.
@SuppressLint("StateFlowValueCalledInComposition")
@Composable
internal fun <T> StateFlow<T>.readWhile(reading: Boolean): State<T> {
    val read = remember { mutableStateOf(value) }
    // Written as this composes, as `rememberUpdatedState` writes: whoever reads it below has it now.
    if (reading) read.value = value
    LaunchedEffect(this, reading) {
        if (reading) collect { read.value = it }
    }
    return read
}

/** [this] read while [reading], starting from [initial]. */
@Composable
internal fun <T> Flow<T>.readWhile(reading: Boolean, initial: T): State<T> {
    val read = remember { mutableStateOf(initial) }
    LaunchedEffect(this, reading) {
        if (reading) collect { read.value = it }
    }
    return read
}
