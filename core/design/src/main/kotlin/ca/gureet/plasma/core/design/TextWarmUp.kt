// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Sets a line in each of Plasma's type styles once, as Plasma starts (#127). The first screen's
 * text then finds Instrument Sans's four weights read and the code that sets text loaded. Without
 * this, the main thread does both in the first frame that has text, and that frame waits while
 * they're read from storage. It doesn't change anything on screen, and it doesn't keep anything
 * itself: the fonts stay in the font cache and their files in Android's page cache.
 *
 * Call it off the main thread.
 */
fun warmUpText(context: Context) {
    val measurer = TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(context),
        defaultDensity = Density(context),
        defaultLayoutDirection = LayoutDirection.Ltr,
        cacheSize = 0,
    )
    WARM_UP_STYLES.forEach { measurer.measure(WARM_UP_LINE, it) }
}

/**
 * Every weight of Instrument Sans, with and without tabular figures. Space Mono is left out: only
 * Settings uses it.
 */
internal val WARM_UP_STYLES: List<TextStyle> = with(PlasmaType) {
    listOf(Display, PageTitle, ListTitle, ListTitleStrong, Body, Caption, Small, Badge, Clock)
}

/**
 * Letters, figures and the punctuation in Plasma's own words. "±" (CH ±) isn't in Instrument
 * Sans, so it also brings in the Android font it comes from.
 */
internal const val WARM_UP_LINE = "Plasma Live TV · Guide › 0123456789 – — … ’ “ ” × CH ±"
