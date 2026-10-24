// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

/**
 * An app's art on its tile (brief 4.4): its banner; or, for an app without one, its [icon] centred
 * on graphite with its [label] below (none on a small thumbnail). Fill the tile with [modifier].
 */
@Composable
fun AppArtwork(banner: String?, icon: String?, label: String?, modifier: Modifier = Modifier, iconSize: Dp = 64.px) {
    if (banner != null || icon == null) {
        PlasmaImage(banner, modifier)
        return
    }
    val focused = LocalFocused.current
    Column(
        modifier.background(
            Brush.verticalGradient(
                if (focused) listOf(PlasmaColors.GraphiteFocusedTop, PlasmaColors.GraphiteFocusedBottom) else listOf(PlasmaColors.GraphiteTop, PlasmaColors.GraphiteBottom),
            ),
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PlasmaImage(icon, Modifier.size(iconSize), contentScale = ContentScale.Fit, placeholder = Color.Transparent)
        if (label != null) {
            PlasmaText(
                label,
                plasmaStyle(19f, 500),
                Modifier.padding(start = 12.px, end = 12.px, top = 10.px),
                color = if (focused) PlasmaColors.Text else PlasmaColors.TextSoft,
                align = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
