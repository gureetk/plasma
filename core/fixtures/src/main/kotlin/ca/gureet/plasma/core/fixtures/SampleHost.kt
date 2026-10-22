// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.fixtures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import ca.gureet.plasma.core.design.PlasmaTheme
import ca.gureet.plasma.core.design.component.LocalImageResolver
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.player.LocalVideoLayer
import ca.gureet.plasma.player.VideoHost
import ca.gureet.plasma.player.VideoLayerState

/**
 * Everything a Plasma screen expects around it, on the sample data: the theme, the clock, the
 * sample art and the video layer. The M0 app, previews and screenshot tests all use it, so they
 * render the same way. Previews and tests stand still at 20:41 on Wed 21 Oct 2026, the boards'
 * moment (appendix A).
 */
@Composable
fun SampleHost(clock: PlasmaClock = Sample.clock, content: @Composable () -> Unit) {
    val video = remember { VideoLayerState() }
    PlasmaTheme {
        CompositionLocalProvider(
            LocalPlasmaClock provides clock,
            LocalImageResolver provides FixtureImages.resolver,
            LocalVideoLayer provides video,
        ) {
            VideoHost(video, content)
        }
    }
}
