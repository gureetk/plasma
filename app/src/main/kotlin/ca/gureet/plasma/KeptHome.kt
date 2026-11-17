// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.LocalOwnersProvider
import ca.gureet.plasma.core.design.KeptScreen
import ca.gureet.plasma.core.design.KeptScreenShows
import ca.gureet.plasma.core.design.KeptScreenState
import ca.gureet.plasma.core.design.rememberKeptScreen

/*
 * Home, kept made under the screens that open over it (#127).
 *
 * Every Back ends at Home, and navigation made Home again from nothing each time: its hero, dock
 * and rows composed, laid out and drawn over two or three long frames. Home now stays composed
 * from the first time its destination shows until its entry leaves the back stack. The
 * destination in the navigation host is [KeptHomeShows], which draws nothing; Home itself is
 * [KeptHomeHost]'s, under the host (core/design's `KeptScreen` has how it shows and hides).
 *
 * Home has there what a destination's content has: its back-stack entry as the owner of its
 * lifecycle, its saved state and its view models. So what Home saves is saved with its entry, as
 * before, and what goes by its lifecycle (Back's handlers, pictures on their way) stops while Home
 * is under another screen, as it stopped when Home wasn't there at all.
 */

/**
 * For the benchmarks (#127): Home taken out of the composition while another screen is over it
 * and made again from nothing as it comes back, as it was before it was kept, so that a run can
 * measure the way back to Home both ways. The benchmark build's setup sets it as Plasma starts.
 */
object HomeForBenchmarks {
    @Volatile var madeAgain = false
}

/** Home's place in the app: its state as a kept screen, and its entry once its destination has shown. */
@Stable
internal class KeptHomeState(val screen: KeptScreenState) {
    var entry: NavBackStackEntry? by mutableStateOf(null)
}

@Composable
internal fun rememberKeptHome(): KeptHomeState {
    val screen = rememberKeptScreen()
    return remember(screen) { KeptHomeState(screen) }
}

/** Home's destination in the navigation host. */
@Composable
internal fun AnimatedVisibilityScope.KeptHomeShows(home: KeptHomeState, entry: NavBackStackEntry) {
    // Told as this composes, not after it: [KeptHomeHost] is called after the navigation host, so
    // it has the entry in the same pass, and Home's first frame is the one it always was.
    if (home.entry !== entry) home.entry = entry
    KeptScreenShows(home.screen)
}

/**
 * Home itself: call it after the navigation host. [content] reads how it shows from core/design's
 * `LocalScreenShown`, and leaves the composition only when Home's entry leaves the back stack.
 */
@Composable
internal fun KeptHomeHost(home: KeptHomeState, content: @Composable () -> Unit) {
    val saved = rememberSaveableStateHolder()
    val entry = home.entry ?: return
    DisposableEffect(home, entry) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY && home.entry === entry) home.entry = null
        }
        entry.lifecycle.addObserver(observer)
        onDispose { entry.lifecycle.removeObserver(observer) }
    }
    // The benchmarks' switch: Home let go while it's under, and made again at its next coming.
    if (HomeForBenchmarks.madeAgain && !home.screen.showing) return
    KeptScreen(home.screen, Modifier.zIndex(-1f)) {
        entry.LocalOwnersProvider(saved, content)
    }
}
