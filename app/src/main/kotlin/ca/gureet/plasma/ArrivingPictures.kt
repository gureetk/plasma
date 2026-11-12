// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import ca.gureet.plasma.core.design.component.ArrivingPainter
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.decode.DataSource
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.collectLatest

/**
 * [request]'s picture with its fade-in left to whoever draws it (#127). The painter draws the
 * picture whole from the moment it's there, and [ArrivingPainter.shown] is the fade Coil's own
 * crossfade would have drawn into it: 0 until the picture arrives, then up to 1 over [fadeMs] at an
 * even pace, or 1 at once for a picture that was in memory, which Coil doesn't fade either.
 * [request] has no crossfade of its own.
 */
@Composable
internal fun arrivingPainter(loader: ImageLoader, request: ImageRequest, fadeMs: Int): ArrivingPainter {
    val arrival = remember(request) { Arrival() }
    val painter = rememberAsyncImagePainter(request, loader, onState = arrival::onState)
    LaunchedEffect(arrival, fadeMs) {
        // Waiting from the start, rather than started by the arrival: the fade's first frame is
        // then the first that draws the picture.
        snapshotFlow { arrival.fade }.collectLatest { fade ->
            if (fade == 0) return@collectLatest
            // By the frames' own times, at an even pace, as Coil's crossfade goes by the clock,
            // whatever Android's animation scale is set to; and set as each frame starts, so that
            // frame draws with it.
            val start = withFrameNanos { it }
            var shown = 0f
            while (shown < 1f) {
                withFrameNanos { now ->
                    shown = if (fadeMs > 0) ((now - start) / (fadeMs * NANOS_PER_MS)).coerceIn(0f, 1f) else 1f
                    arrival.shown.floatValue = shown
                }
            }
        }
    }
    return remember(painter, arrival) { ArrivingPainter(painter, arrival.shown) }
}

/** How far a picture has come in since it arrived. */
@Stable
private class Arrival {
    /** 0 while there's no picture, which draws nothing either way. */
    val shown = mutableFloatStateOf(0f)

    /** Which of the picture's arrivals is fading in, counted from 1; 0 while none is. */
    var fade by mutableIntStateOf(0)
        private set
    private var fades = 0

    fun onState(state: AsyncImagePainter.State) {
        if (state !is AsyncImagePainter.State.Success) return
        if (state.result.dataSource == DataSource.MEMORY_CACHE) {
            shown.floatValue = 1f
            fade = 0
        } else {
            shown.floatValue = 0f
            fade = ++fades
        }
    }
}

private const val NANOS_PER_MS = 1_000_000f
