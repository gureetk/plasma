// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

/** Instrument Sans at 400–700 (SIL OFL 1.1), static instances of the Google Fonts variable font. */
val InstrumentSans = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.W400),
    Font(R.font.instrument_sans_medium, FontWeight.W500),
    Font(R.font.instrument_sans_semibold, FontWeight.W600),
    Font(R.font.instrument_sans_bold, FontWeight.W700),
)

/** Space Mono 400 (SIL OFL 1.1), for command blocks only. */
val SpaceMono = FontFamily(Font(R.font.space_mono_regular, FontWeight.W400))

/**
 * A text style from board values: font size, weight and letter spacing in board pixels.
 * Line height follows the font's own metrics, like CSS `line-height: normal` on the boards,
 * unless [lineHeight] gives a multiple of the size (as the boards do for body text).
 */
fun plasmaStyle(
    sizePx: Float,
    weight: Int = 500,
    trackingPx: Float = 0f,
    lineHeight: Float? = null,
    tabular: Boolean = false,
    family: FontFamily = InstrumentSans,
): TextStyle = TextStyle(
    fontFamily = family,
    fontSize = sizePx.pxSp,
    fontWeight = FontWeight(weight),
    letterSpacing = if (trackingPx == 0f) TextUnit.Unspecified else tracking(trackingPx, sizePx),
    lineHeight = lineHeight?.em ?: TextUnit.Unspecified,
    lineHeightStyle = if (lineHeight != null) {
        LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    } else {
        null
    },
    fontFeatureSettings = if (tabular) "tnum" else null,
)

/** Use tabular figures, for times, numbers, counts and durations (brief 2.3). */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/** The type scale from the brief, section 2.3. Sizes are the board values; see [plasmaStyle]. */
object PlasmaType {
    val Display = plasmaStyle(68f, 600, trackingPx = -1.6f, lineHeight = 1.04f)
    val TitleL = plasmaStyle(52f, 600, trackingPx = -1.2f, lineHeight = 1.06f)
    val TitleM = plasmaStyle(48f, 600, trackingPx = -1f, lineHeight = 1.06f)
    val PageTitle = plasmaStyle(34f, 600, trackingPx = -0.4f)
    val Section = plasmaStyle(30f, 600, trackingPx = -0.3f)
    val ListTitle = plasmaStyle(25f, 500)
    val ListTitleStrong = plasmaStyle(25f, 600)
    val Body = plasmaStyle(24f, 400, lineHeight = 1.4f)
    val Caption = plasmaStyle(21f, 500, tabular = true)
    val Small = plasmaStyle(19f, 500, tabular = true)
    val Badge = plasmaStyle(15f, 700, trackingPx = 0.4f)
    val Clock = plasmaStyle(30f, 500, trackingPx = -0.4f, tabular = true)
    val Wordmark = plasmaStyle(32f, 600, trackingPx = -1f)
    val Mono = plasmaStyle(18f, 400, lineHeight = 1.55f, family = SpaceMono)
}
