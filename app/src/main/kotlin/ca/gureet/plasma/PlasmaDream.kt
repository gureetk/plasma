// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.service.dreams.DreamService
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import ca.gureet.plasma.core.design.component.LocalImageResolver
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.fixtures.FixtureImages
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.feature.home.PlasmaPictures
import ca.gureet.plasma.feature.home.ScreensaverScreen

/**
 * Plasma's screensaver as the TV's own (brief 4.6 and 5.14): the screen Plasma shows when it's
 * left idle, shown by the TV whenever it's idle, in any app, at the TV's own time. Any key ends it.
 */
class PlasmaDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        saved.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        setContentView(
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@PlasmaDream)
                setViewTreeSavedStateRegistryOwner(this@PlasmaDream)
                setContent {
                    PlasmaAppTheme(graph) {
                        CompositionLocalProvider(
                            LocalPlasmaClock provides PlasmaClock.System,
                            LocalImageResolver provides FixtureImages.resolver,
                        ) {
                            // What's recording now (5.6).
                            val recordingNow by graph.recordingNow.collectAsState(null)
                            ScreensaverScreen(remember(recordingNow) { PlasmaPictures.copy(recording = recordingNow) })
                        }
                    }
                }
            },
        )
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDetachedFromWindow()
    }
}
