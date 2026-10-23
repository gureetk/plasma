// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaTheme
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.model.FixedClock
import java.time.Instant
import java.time.ZoneOffset

/**
 * A preview at the boards' size: 1920 × 1080 px on a TV, which is 960 × 540 dp at xhdpi. Screens
 * show their sample data in SampleHost; components draw on a [PreviewCanvas].
 *
 * Previews live in the debug source sets, so they never ship.
 */
@Preview(
    name = "TV 1080p",
    device = "spec:width=1920px,height=1080px,dpi=320",
    uiMode = Configuration.UI_MODE_TYPE_TELEVISION,
    showBackground = true,
    backgroundColor = 0xFF0B0B0D,
)
annotation class TvPreview

/** The boards' moment, Wed 21 Oct 2026 at 20:41, for the clocks in previews. */
private val BoardsClock = FixedClock(Instant.parse("2026-10-21T20:41:00Z"), ZoneOffset.UTC)

/** A canvas for component previews: the theme, the boards' clock and the ground color. */
@Composable
fun PreviewCanvas(content: @Composable BoxScope.() -> Unit) {
    PlasmaTheme {
        CompositionLocalProvider(LocalPlasmaClock provides BoardsClock) {
            Box(Modifier.fillMaxSize().background(PlasmaColors.Bg), content = content)
        }
    }
}
