// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.boxShadow
import ca.gureet.plasma.core.design.component.ColorKey
import ca.gureet.plasma.core.design.component.ColorKeySquare
import ca.gureet.plasma.core.design.component.GraphiteFill
import ca.gureet.plasma.core.design.component.KeyCap
import ca.gureet.plasma.core.design.component.LiveMark
import ca.gureet.plasma.core.design.component.LogoPlate
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.component.TechBadge
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.design.ring

/**
 * Board 26, the interface kit, drawn with the design system: the color tokens, the type scale,
 * focus on a tile, a button and a settings row, the badges and key hints, and the remote map.
 * It's a reference for checking the tokens, not a screen of the app, so its words are written
 * here rather than in string resources.
 *
 * @param logo the logo on the sample plate; without one, the plate shows the channel's name.
 */
@Composable
fun InterfaceKit(logo: String? = null) {
    Box(Modifier.fillMaxSize().background(PlasmaColors.Bg)) {
        Header()
        Colors(Modifier.offset(96.px, 150.px).width(1000.px))
        TypeScale(Modifier.offset(96.px, 398.px).width(1000.px))
        FocusSamples(Modifier.offset(96.px, 756.px).width(1000.px))
        Components(logo, Modifier.offset(96.px, 968.px))
        RemoteMap(Modifier.offset(1190.px, 150.px).width(634.px))
    }
}

@TvPreview
@Composable
private fun InterfaceKitPreview() = PreviewCanvas { InterfaceKit() }

private val SectionLabel = plasmaStyle(21f, 600)

@Composable
private fun Header() {
    Row(Modifier.offset(96.px, 54.px).width(1728.px), horizontalArrangement = Arrangement.spacedBy(22.px)) {
        PlasmaText("plasma", plasmaStyle(40f, 600, trackingPx = -1.2f), Modifier.alignBy(FirstBaseline))
        PlasmaText("Interface kit", plasmaStyle(40f, 500, trackingPx = -0.8f), Modifier.alignBy(FirstBaseline), color = PlasmaColors.Text2)
        Spacer(Modifier.weight(1f))
        PlasmaText(
            "1920 × 1080 · viewed at 3 m · safe area 96 px sides, 54 px top and bottom",
            plasmaStyle(21f, 500),
            Modifier.alignBy(FirstBaseline),
            color = PlasmaColors.Text3,
        )
    }
}

// ---- Color ---------------------------------------------------------------------------------------

private class Swatch(val name: String, val color: Color, val ring: Float = 0f)

private val Swatches = listOf(
    Swatch("Ground", PlasmaColors.Bg, ring = 0.16f),
    Swatch("Surface", PlasmaColors.Surface1, ring = 0.08f),
    Swatch("Raised", PlasmaColors.Surface3),
    Swatch("Text, Focus", PlasmaColors.Text),
    Swatch("Secondary", PlasmaColors.Text2),
    Swatch("Tertiary", PlasmaColors.Text3),
    Swatch("Live", PlasmaColors.Live),
    Swatch("Connected", PlasmaColors.Ok),
)

/** "#0B0B0D" */
private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

@Composable
private fun Colors(modifier: Modifier) {
    Column(modifier) {
        PlasmaText("Color", SectionLabel, color = PlasmaColors.Text3)
        Row(Modifier.padding(top = 16.px).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.px)) {
            for (swatch in Swatches) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.px)) {
                    val shape = RoundedCornerShape(12.px)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(72.px)
                            .background(swatch.color, shape)
                            .then(if (swatch.ring > 0f) Modifier.border(1.px, PlasmaColors.white(swatch.ring), shape) else Modifier),
                    )
                    PlasmaText(swatch.name, plasmaStyle(18f, 600))
                    PlasmaText(swatch.color.hex(), plasmaStyle(16f, 500), color = PlasmaColors.Text3)
                }
            }
        }
        PlasmaText(
            "Chrome stays neutral so artwork carries the color. Red is reserved for live and recording.",
            plasmaStyle(19f, 500, lineHeight = 1.45f),
            Modifier.padding(top = 14.px),
            color = PlasmaColors.Text3,
        )
    }
}

// ---- Type ----------------------------------------------------------------------------------------

@Composable
private fun TypeScale(modifier: Modifier) {
    Column(modifier) {
        PlasmaText("Type · Instrument Sans", SectionLabel, color = PlasmaColors.Text3)
        Column(Modifier.padding(top = 12.px), verticalArrangement = Arrangement.spacedBy(10.px)) {
            TypeRow("Display 68 / 600", "Harbour City", plasmaStyle(68f, 600, trackingPx = -1.6f, lineHeight = 1.04f))
            TypeRow("Title 52 / 600", "The World Tonight", plasmaStyle(52f, 600, trackingPx = -1.1f, lineHeight = 1.08f))
            TypeRow("Section 30 / 600", "Favorite Channels", plasmaStyle(30f, 600, trackingPx = -0.3f))
            TypeRow("Body 25 / 400", "A winter storm cuts the island off for a week.", plasmaStyle(25f, 400), PlasmaColors.TextSoft)
            TypeRow("Caption 22 / 500", "Catch-up · Yesterday, 20:00 · 18 min left", plasmaStyle(22f, 500, tabular = true), PlasmaColors.Text2)
        }
    }
}

@Composable
private fun TypeRow(label: String, sample: String, style: TextStyle, color: Color = PlasmaColors.Text) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.px)) {
        PlasmaText(label, plasmaStyle(18f, 500), Modifier.width(200.px).alignBy(FirstBaseline), color = PlasmaColors.Text3)
        PlasmaText(sample, style, Modifier.alignBy(FirstBaseline), color = color)
    }
}

// ---- Focus ---------------------------------------------------------------------------------------

@Composable
private fun FocusSamples(modifier: Modifier) {
    Column(modifier) {
        PlasmaText("Focus · lift, shadow and white fill, never a colored ring", SectionLabel, color = PlasmaColors.Text3)
        Row(Modifier.padding(top = 22.px), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.px)) {
            SampleTile()
            Focused { SampleTile() }
            SampleDivider()
            Column(verticalArrangement = Arrangement.spacedBy(12.px)) {
                SampleButton("Trailer")
                Focused { SampleButton("Resume") }
            }
            SampleDivider()
            Column(Modifier.width(340.px), verticalArrangement = Arrangement.spacedBy(12.px)) {
                SampleRow("Clock", "24-Hour")
                Focused { SampleRow("Home Button", "Plasma Home") }
            }
        }
    }
}

@Composable
private fun SampleTile() {
    PlasmaSurface(
        onClick = {},
        modifier = Modifier.size(150.px, 90.px),
        style = FocusStyle.Tile,
        shape = PlasmaShapes.Button,
        contentAlignment = Alignment.Center,
    ) {
        GraphiteFill()
        PlasmaIcon(PlasmaIcons.LiveTv, 34.px, tint = if (LocalFocused.current) PlasmaColors.white(1f) else PlasmaColors.TextSoft2)
    }
}

@Composable
private fun SampleDivider() = Box(Modifier.padding(horizontal = 6.px).size(1.px, 70.px).background(PlasmaColors.white(0.1f)))

@Composable
private fun SampleButton(text: String) =
    PlasmaButton(text, onClick = {}, height = 52.px, textSizePx = 21f, startPadding = 22.px, endPadding = 22.px)

/** A settings row as Settings draws it (board 19), at the kit's smaller size. */
@Composable
private fun SampleRow(label: String, value: String) {
    PlasmaSurface(
        onClick = {},
        modifier = Modifier.fillMaxWidth().height(52.px),
        style = FocusStyle.Row,
        shape = PlasmaShapes.Button,
        color = PlasmaColors.Surface1,
        contentAlignment = Alignment.CenterStart,
    ) {
        val focused = LocalFocused.current
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.px), verticalAlignment = Alignment.CenterVertically) {
            PlasmaText(label, plasmaStyle(21f, if (focused) 600 else 500), Modifier.weight(1f))
            PlasmaText(value, plasmaStyle(21f, 500), color = if (focused) PlasmaColors.OnFocus2 else PlasmaColors.Text2)
        }
    }
}

// ---- Components ----------------------------------------------------------------------------------

@Composable
private fun Components(logo: String?, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px)) {
        Box(
            Modifier.height(32.px).background(PlasmaColors.white(0.08f), RoundedCornerShape(8.px)).padding(horizontal = 12.px),
            contentAlignment = Alignment.Center,
        ) {
            LiveMark("LIVE", style = plasmaStyle(16f, 600, trackingPx = 1.3f), dot = 8.px, gap = 8.px)
        }
        for (badge in listOf("4K", "HDR", "5.1", "CC")) {
            TechBadge(badge, borderColor = PlasmaColors.Text4, color = PlasmaColors.TextSoft)
        }
        ProgressBar(0.42f, Modifier.padding(start = 10.px).width(160.px), height = 4.px, color = PlasmaColors.Text, track = PlasmaColors.white(0.2f))
        LogoPlate(logo, "Arena Sports 1", 132.px, 56.px, Modifier.padding(start = 10.px), logoHeight = 40.px)
        KeyCap("OK", Modifier.padding(start = 10.px))
        for (key in ColorKey.entries) ColorKeySquare(key)
    }
}

// ---- Remote map ----------------------------------------------------------------------------------

/** The remote's body and keys (board 26), drawn only here. */
private val RemoteTop = Color(0xFF2C2C31)
private val RemoteBottom = Color(0xFF1A1A1D)
private val RemoteKey = Color(0xFF0F0F11)
private val RemoteOk = Color(0xFF26262A)

private class Callout(val number: Int, val title: String, val text: String, val x: Int, val y: Int)

private val Callouts = listOf(
    Callout(1, "Home", "Returns to Plasma Home from anywhere. Hold for recent apps.", 104, 266),
    Callout(2, "Back", "Previous screen. In the player, hides the overlay.", 8, 270),
    Callout(3, "OK and arrows", "While watching: OK shows the overlay, up and down change channel, left opens the channel list.", 140, 134),
    Callout(4, "Power", "Wakes into the last channel or Home, as set in Settings.", 66, 30),
    Callout(5, "Menu", "Options for whatever is focused.", 168, 270),
    Callout(6, "CH + and −", "Change channel. In lists and the guide, page up and down.", 156, 350),
    Callout(7, "Color keys", "Red favorite, green catch-up, yellow sort and group, blue multiview.", 168, 502),
    Callout(8, "0–9", "Type a channel number from any screen.", 168, 572),
)

@Composable
private fun RemoteMap(modifier: Modifier) {
    Column(modifier) {
        PlasmaText("Remote map", SectionLabel, color = PlasmaColors.Text3)
        Row(Modifier.padding(top = 20.px), horizontalArrangement = Arrangement.spacedBy(40.px)) {
            Remote()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.px)) {
                for (callout in Callouts) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.px)) {
                        NumberDot(callout.number, 26.px, plasmaStyle(14f, 700))
                        Column(verticalArrangement = Arrangement.spacedBy(3.px)) {
                            PlasmaText(callout.title, plasmaStyle(22f, 600))
                            PlasmaText(callout.text, plasmaStyle(19f, 500, lineHeight = 1.4f), color = PlasmaColors.Text2, maxLines = 3)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberDot(number: Int, size: Dp, style: TextStyle, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(PlasmaColors.Text, CircleShape), contentAlignment = Alignment.Center) {
        PlasmaText(number.toString(), style, color = PlasmaColors.OnFocus)
    }
}

@Composable
private fun Remote() {
    Box(
        Modifier
            .size(200.px, 800.px)
            .boxShadow(PlasmaColors.black(0.6f), offsetY = 30.px, blur = 60.px, cornerRadius = 64.px)
            .background(Brush.verticalGradient(listOf(RemoteTop, RemoteBottom)), RoundedCornerShape(64.px))
            .drawWithContent {
                drawContent()
                drawLine(PlasmaColors.white(0.1f), Offset(64.px.toPx(), 0.5f), Offset(size.width - 64.px.toPx(), 0.5f))
            },
    ) {
        RoundKey(28, 34, 36) { PlasmaIcon(PlasmaIcons.Power, 17.px, tint = PlasmaColors.LiveText) }
        Box(Modifier.offset(22.px, 96.px).size(156.px).background(RemoteKey, CircleShape))
        RoundKey(66, 140, 68, color = RemoteOk) { PlasmaText("OK", plasmaStyle(16f, 600), color = PlasmaColors.TextSoft2) }
        Arrows()
        RoundKey(24, 282, 48) { PlasmaIcon(PlasmaIcons.Back, 19.px, tint = PlasmaColors.TextSoft) }
        RoundKey(76, 282, 48, color = PlasmaColors.Text) { PlasmaIcon(PlasmaIcons.Home, 21.px, tint = PlasmaColors.OnFocus) }
        RoundKey(128, 282, 48) { PlasmaIcon(PlasmaIcons.Menu, 19.px, tint = PlasmaColors.TextSoft) }
        Rocker(32, "VOL")
        Rocker(116, "CH")
        Row(Modifier.offset(28.px, 516.px).width(144.px), horizontalArrangement = Arrangement.SpaceBetween) {
            for (key in ColorKey.entries) Box(Modifier.size(30.px, 18.px).background(key.color, RoundedCornerShape(5.px)))
        }
        NumberKeys(Modifier.offset(34.px, 564.px).width(132.px))
        // The numbers sit on the keys, with a ring of the remote's color around them.
        for (callout in Callouts) {
            NumberDot(callout.number, 24.px, plasmaStyle(13f, 700), Modifier.offset(callout.x.px, callout.y.px).ring(3.px, RemoteBottom, 12.px))
        }
    }
}

@Composable
private fun RoundKey(x: Int, y: Int, size: Int, color: Color = RemoteKey, content: @Composable () -> Unit) {
    Box(Modifier.offset(x.px, y.px).size(size.px).background(color, CircleShape), contentAlignment = Alignment.Center) { content() }
}

/** The D-pad's four arrows, small triangles on the ring. */
@Composable
private fun Arrows() {
    Canvas(Modifier.size(200.px, 260.px)) {
        val u = 1.px.toPx()
        fun triangle(vararg points: Pair<Float, Float>) {
            val path = Path().apply {
                moveTo(points[0].first * u, points[0].second * u)
                for (p in points.drop(1)) lineTo(p.first * u, p.second * u)
                close()
            }
            drawPath(path, PlasmaColors.Text4)
        }
        triangle(94f to 114f, 100f to 106f, 106f to 114f)
        triangle(94f to 234f, 106f to 234f, 100f to 242f)
        triangle(41f to 168f, 41f to 180f, 33f to 174f)
        triangle(159f to 168f, 167f to 174f, 159f to 180f)
    }
}

@Composable
private fun Rocker(x: Int, label: String) {
    Column(
        Modifier.offset(x.px, 360.px).size(52.px, 124.px).background(RemoteKey, RoundedCornerShape(26.px)).padding(vertical = 12.px),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        val sign = plasmaStyle(20f, 600)
        PlasmaText("+", sign, color = PlasmaColors.TextSoft)
        PlasmaText(label, plasmaStyle(11f, 600, trackingPx = 1f), color = PlasmaColors.Text3)
        PlasmaText("−", sign, color = PlasmaColors.TextSoft)
    }
}

@Composable
private fun NumberKeys(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.px)) {
        for (row in listOf("123", "456", "789", " 0 ")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.px)) {
                for (digit in row) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(34.px)
                            .then(if (digit != ' ') Modifier.background(RemoteKey, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (digit != ' ') PlasmaText(digit.toString(), plasmaStyle(15f, 600), color = PlasmaColors.TextSoft)
                    }
                }
            }
        }
    }
}
