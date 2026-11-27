// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ca.gureet.plasma.remote.app.resources.Res
import ca.gureet.plasma.remote.app.resources.instrument_sans_400
import ca.gureet.plasma.remote.app.resources.instrument_sans_500
import ca.gureet.plasma.remote.app.resources.instrument_sans_600
import org.jetbrains.compose.resources.Font

/**
 * Plasma Remote's colors: the TV's tokens (brief 2.2), as the phone remote's web page has them
 * (board 33), so the apps and the page look the same.
 */
object RemoteColors {
    val Bg = Color(0xFF0B0B0D)
    val Surface1 = Color(0xFF161618)
    val Surface3 = Color(0xFF232327)
    val Plate = Color(0xFF18181B)
    val OkFill = Color(0xFF2A2A2F)
    val Text = Color(0xFFF5F5F7)
    val Text2 = Color(0xFFA1A1A8)
    val Text3 = Color(0xFF8A8A91)
    val Soft = Color(0xFFC7C7CC)
    val Line = Color.White.copy(alpha = 0.08f)
    val Button = Color.White.copy(alpha = 0.12f)
    val Track = Color.White.copy(alpha = 0.18f)
    val MiniTrack = Color.White.copy(alpha = 0.14f)
    val Ring = Color.White.copy(alpha = 0.06f)
    val Pressed = Color.White.copy(alpha = 0.08f)
    val Ok = Color(0xFF3DD68C)
    val Live = Color(0xFFFF453A)
    val Warn = Color(0xFFFF6B61)
    val Scrim = Color.Black.copy(alpha = 0.55f)
    val Red = Color(0xFFE5484D)
    val Green = Color(0xFF3DD68C)
    val Yellow = Color(0xFFFFD60A)
    val Blue = Color(0xFF3B82F6)
}

/** Instrument Sans, the TV's typeface, at the weights the page uses. */
@Composable
fun instrumentSans(): FontFamily = FontFamily(
    Font(Res.font.instrument_sans_400, FontWeight.Normal),
    Font(Res.font.instrument_sans_500, FontWeight.Medium),
    Font(Res.font.instrument_sans_600, FontWeight.SemiBold),
)

/** The text style everything starts from: 15 sp Medium, line height 1.3, as the page's body. */
val LocalTextStyle = staticCompositionLocalOf { TextStyle.Default }

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    val base = TextStyle(
        fontFamily = instrumentSans(),
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 1.3.em,
        color = RemoteColors.Text,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    CompositionLocalProvider(LocalTextStyle provides base, content = content)
}

/** The base style at [size], [weight] and [color]. */
@Composable
fun textStyle(size: TextUnit, weight: FontWeight = FontWeight.Medium, color: Color = RemoteColors.Text, letterSpacing: TextUnit = TextUnit.Unspecified): TextStyle =
    LocalTextStyle.current.copy(fontSize = size, fontWeight = weight, color = color, letterSpacing = letterSpacing)
