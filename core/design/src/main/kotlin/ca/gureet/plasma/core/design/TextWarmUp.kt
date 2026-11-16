// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import android.content.Context
import androidx.compose.runtime.snapshots.Snapshot
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
 * Each line is set in a snapshot of this thread's own. Compose's text code makes state the first
 * time it's asked whether Android's emoji font is loaded, on the thread that asks, and at a start
 * that's this one. A composition reads state as it was when the composition began: it can't read
 * state another thread made after that outside a snapshot, and the launch screen measures its
 * wordmark as it's composed, so a start at which this thread's first line fell within that
 * composition closed Plasma as it opened. State made in a snapshot can be read by every
 * composition, whenever it began.
 *
 * Call it off the main thread.
 */
fun warmUpText(context: Context) {
    warmUpText(context) { measurer, style -> measurer.measure(WARM_UP_LINE, style) }
}

/** [warmUpText], with [set] setting each style's line: a test's makes state as Compose's text code does. */
internal fun warmUpText(context: Context, set: (TextMeasurer, TextStyle) -> Unit) {
    val measurer = TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(context),
        defaultDensity = Density(context),
        defaultLayoutDirection = LayoutDirection.Ltr,
        cacheSize = 0,
    )
    WARM_UP_STYLES.forEach { style -> Snapshot.withMutableSnapshot { set(measurer, style) } }
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
