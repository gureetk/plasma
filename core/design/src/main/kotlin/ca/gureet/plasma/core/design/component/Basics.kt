// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.core.model.PlasmaClock
import java.time.ZonedDateTime
import kotlin.math.sqrt
import kotlinx.coroutines.delay

// ---- Time --------------------------------------------------------------------------------------

/** The clock everything reads. Tests and previews provide a fixed one. */
val LocalPlasmaClock = staticCompositionLocalOf { PlasmaClock.System }

/** The current time, updated at the start of every minute. */
@Composable
fun rememberNow(): State<ZonedDateTime> {
    val clock = LocalPlasmaClock.current
    val state = remember(clock) { mutableStateOf(clock.nowZoned()) }
    LaunchedEffect(clock) {
        while (true) {
            val now = clock.nowZoned()
            state.value = now
            delay((60 - now.second) * 1_000L - now.nano / 1_000_000L + 50L)
        }
    }
    return state
}

/** The 24-hour clock at the top right of most screens: 30 px, weight 500. */
@Composable
fun ClockText(modifier: Modifier = Modifier, style: TextStyle = PlasmaType.Clock) {
    val now by rememberNow()
    PlasmaText(Formats.time(now), style, modifier)
}

// ---- Images ------------------------------------------------------------------------------------

/**
 * Turns an image reference (a URL, or `fixture://name` for sample art) into a painter.
 * The app provides the resolver; Coil-backed URL loading arrives with real sources.
 */
fun interface ImageResolver {
    @Composable
    fun painter(model: String): Painter?
}

val LocalImageResolver = staticCompositionLocalOf { ImageResolver { null } }

/**
 * An image from [model], cropped like CSS `object-fit: cover` around [alignment]; [placeholder] fills
 * while there's none. [colorFilter] darkens or tints it, as the boards' `filter: brightness()`.
 */
@Composable
fun PlasmaImage(
    model: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    placeholder: Color = PlasmaColors.Surface2,
    colorFilter: ColorFilter? = null,
) {
    val painter = model?.let { LocalImageResolver.current.painter(it) }
    if (painter == null) {
        Box(modifier.background(placeholder))
    } else {
        Image(
            painter,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
            alignment = alignment,
            colorFilter = colorFilter,
        )
    }
}

/** CSS `filter: brightness(b)`: scales the colour channels by [brightness]. */
fun brightness(brightness: Float): ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply { setToScale(brightness, brightness, brightness, 1f) },
)

/** A focal-point alignment like CSS `object-position: 60% 40%`. */
fun focal(xPercent: Float, yPercent: Float): Alignment = androidx.compose.ui.BiasAlignment(
    horizontalBias = xPercent / 50f - 1f,
    verticalBias = yPercent / 50f - 1f,
)

/**
 * A channel logo on a plate (brief 2.7): logos fit within 80 % of the plate's width and 55 % of its
 * height. When there's no logo, the channel name is set on the plate instead.
 */
@Composable
fun LogoPlate(
    logo: String?,
    name: String,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    logoSize: LogoSize? = null,
    logoHeight: Dp = height * 0.62f,
    color: Color = PlasmaColors.Plate,
    shape: Shape = PlasmaShapes.Plate,
    fallbackStyle: TextStyle = plasmaStyle(15f, 600),
) {
    Box(
        modifier.size(width, height).clip(shape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        val painter = logo?.let { LocalImageResolver.current.painter(it) }
        when {
            painter == null ->
                PlasmaText(name, fallbackStyle, Modifier.padding(horizontal = 8.px), color = PlasmaColors.TextSoft2, maxLines = 2)
            logoSize != null -> LogoImage(logo, name, logoSize)
            else -> Image(
                painter, contentDescription = name,
                modifier = Modifier.widthIn(max = width * 0.86f).height(logoHeight),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** A logo straight on imagery or video (no plate), [height] tall. */
@Composable
fun LogoImage(logo: String?, name: String, height: Dp, modifier: Modifier = Modifier) {
    val painter = logo?.let { LocalImageResolver.current.painter(it) }
    if (painter != null) {
        Image(painter, contentDescription = name, modifier = modifier.height(height), contentScale = ContentScale.FillHeight)
    } else {
        PlasmaText(name, plasmaStyle(22f, 700), modifier)
    }
}

/**
 * How big a logo is drawn on imagery. Logos come in every shape, so they're sized by area rather
 * than height: a wide logo is drawn shorter and a squarer one taller, so they all look the same
 * size. This is what the boards do by hand (Wildframe 24 px tall, Northline 31, Cinema Prime 36).
 * All values are board pixels.
 */
@Immutable
class LogoSize(val areaPx: Float, val maxHeightPx: Float, val maxWidthPx: Float) {
    companion object {
        /** Favorite Channels and other 352 × 198 live thumbnails. */
        val Thumbnail = LogoSize(2_800f, 36f, 150f)

        /** 400 × 225 cards: Continue Watching, Search. */
        val Card = LogoSize(3_100f, 38f, 160f)

        /** The Home hero (Arena Sports 1 at 56 px tall). */
        val Hero = LogoSize(6_400f, 64f, 260f)

        /** On a 150 × 66 plate in the Live TV list (Arena Sports 1 at 42 px tall, Velocity at 26). */
        val Plate = LogoSize(3_550f, 44f, 136f)
    }
}

/** A logo straight on imagery or video, sized by [size] (see [LogoSize]). Without a logo, the name. */
@Composable
fun LogoImage(
    logo: String?,
    name: String,
    size: LogoSize,
    modifier: Modifier = Modifier,
    fallbackStyle: TextStyle = plasmaStyle(22f, 700),
) {
    val painter = logo?.let { LocalImageResolver.current.painter(it) }
    val intrinsic = painter?.intrinsicSize
    if (painter == null || intrinsic == null || !intrinsic.isSpecified || intrinsic.width <= 0f || intrinsic.height <= 0f) {
        PlasmaText(name, fallbackStyle, modifier)
        return
    }
    val aspect = intrinsic.width / intrinsic.height
    var heightPx = minOf(sqrt(size.areaPx / aspect), size.maxHeightPx)
    var widthPx = heightPx * aspect
    if (widthPx > size.maxWidthPx) {
        widthPx = size.maxWidthPx
        heightPx = widthPx / aspect
    }
    Image(painter, contentDescription = name, modifier = modifier.size(widthPx.px, heightPx.px), contentScale = ContentScale.Fit)
}

// ---- Badges and status ---------------------------------------------------------------------------

/** "● LIVE" (or REC): a red dot and the word, inline. */
@Composable
fun LiveMark(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = plasmaStyle(20f, 600, trackingPx = 1.6f),
    dot: Dp = 10.px,
    gap: Dp = 10.px,
    dotColor: Color = PlasmaColors.Live,
    color: Color = PlasmaColors.Text,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
        Box(Modifier.size(dot).background(dotColor, CircleShape))
        PlasmaText(text, style, color = color)
    }
}

/** LIVE, REC or CATCH-UP on top of an image: in a dark pill (brief 2.9). */
@Composable
fun ImageBadge(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Color(0x99000000),
    leading: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.height(30.px).background(background, PlasmaShapes.Pill).padding(horizontal = 10.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.px),
    ) {
        leading?.invoke()
        PlasmaText(text, plasmaStyle(15f, 600, trackingPx = 1.2f), color = PlasmaColors.Text)
    }
}

/** A red dot for [ImageBadge]. */
@Composable
fun RedDot(size: Dp = 8.px, color: Color = PlasmaColors.Live) {
    Box(Modifier.size(size).background(color, CircleShape))
}

/** Technical badges: 4K, HDR, HD, 5.1, CC, 50 FPS, 13+ (outlined, brief 2.9). */
@Composable
fun TechBadge(
    text: String,
    modifier: Modifier = Modifier,
    borderColor: Color = Color(0x73FFFFFF),
    color: Color = PlasmaColors.TextSoft3,
) {
    Box(
        modifier.height(26.px).border(1.5f.px, borderColor, PlasmaShapes.Badge).padding(horizontal = 7.px),
        contentAlignment = Alignment.Center,
    ) {
        PlasmaText(text, plasmaStyle(15f, 700, trackingPx = 0.4f, tabular = true), color = color)
    }
}

/** "NEW EPISODE", "3 EPISODES": filled poster badges. */
@Composable
fun PosterBadge(text: String, modifier: Modifier = Modifier, background: Color = Color(0xD10B0B0D)) {
    Box(modifier.height(30.px).background(background, PlasmaShapes.Pill).padding(horizontal = 10.px), contentAlignment = Alignment.Center) {
        PlasmaText(text, plasmaStyle(14f, 700, trackingPx = 1f), color = PlasmaColors.Text)
    }
}

/** A 10 px status dot (green connected, amber stale, grey standby, red error). */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.px) {
    Box(modifier.size(size).background(color, CircleShape))
}

/** A progress bar: a track and a fill in the accent (or red while recording). */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 5.px,
    color: Color = ca.gureet.plasma.core.design.LocalAccent.current,
    track: Color = Color(0x38FFFFFF),
    rounded: Boolean = true,
) {
    val shape = if (rounded) RoundedCornerShape(height / 2) else RoundedCornerShape(0.px)
    Box(modifier.height(height).clip(shape).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(shape).background(color))
    }
}

// ---- Footer key hints ----------------------------------------------------------------------------

/** A remote key drawn as a small cap: "OK", "0–9", "CH ±" (board 07 footer). */
@Composable
fun KeyCap(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier.height(30.px).widthIn(min = 30.px).background(PlasmaColors.white(0.1f), PlasmaShapes.Pill).padding(horizontal = 8.px),
        contentAlignment = Alignment.Center,
    ) {
        PlasmaText(label, plasmaStyle(15f, 600, tabular = true), color = PlasmaColors.TextSoft2)
    }
}

/** A key cap holding an icon instead of a label (◀, ≡, ↺). */
@Composable
fun KeyCapIcon(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.height(30.px).widthIn(min = 30.px).background(PlasmaColors.white(0.1f), PlasmaShapes.Pill).padding(horizontal = 8.px),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

enum class ColorKey(val color: Color) {
    Red(PlasmaColors.KeyRed), Green(PlasmaColors.KeyGreen), Yellow(PlasmaColors.KeyYellow), Blue(PlasmaColors.KeyBlue)
}

/** A colour-key hint: a 14 px square and its label. */
@Composable
fun ColorKeyHint(key: ColorKey, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
        Box(Modifier.size(14.px).background(key.color, RoundedCornerShape(3.px)))
        PlasmaText(label, FooterLabel, color = PlasmaColors.Text3)
    }
}

/** A remote-key hint: a key cap and what it does. */
@Composable
fun KeyHint(label: String, action: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
        KeyCap(label)
        PlasmaText(action, FooterLabel, color = PlasmaColors.Text3)
    }
}

/** A remote-key hint with an icon on the cap. */
@Composable
fun KeyHint(icon: PlasmaIcons, action: String, modifier: Modifier = Modifier, iconSize: Dp = 15.px) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
        KeyCapIcon { PlasmaIcon(icon, iconSize, tint = PlasmaColors.TextSoft2) }
        PlasmaText(action, FooterLabel, color = PlasmaColors.Text3)
    }
}

private val FooterLabel = plasmaStyle(21f, 500)

/** One entry in a [KeyFooter]'s right-hand group. */
@Immutable
class FooterKey private constructor(val label: String?, val icon: PlasmaIcons?, val iconSize: Dp, val action: String) {
    constructor(label: String, action: String) : this(label, null, 0.px, action)
    constructor(icon: PlasmaIcons, action: String, iconSize: Dp = 15.px) : this(null, icon, iconSize, action)
}

/**
 * The footer on list screens (board 07): colour keys on the left, remote keys on the right,
 * at y 992 between the side margins. Hints are shortcuts; everything also works with OK and Menu.
 */
@Composable
fun KeyFooter(colorKeys: List<Pair<ColorKey, String>>, keys: List<FooterKey>, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(start = 96.px, top = 992.px).width(1728.px).height(36.px),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(30.px), verticalAlignment = Alignment.CenterVertically) {
            for ((key, label) in colorKeys) ColorKeyHint(key, label)
        }
        Box(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(28.px), verticalAlignment = Alignment.CenterVertically) {
            for (key in keys) {
                if (key.icon != null) KeyHint(key.icon, key.action, iconSize = key.iconSize) else KeyHint(key.label.orEmpty(), key.action)
            }
        }
    }
}

// ---- Scrims ---------------------------------------------------------------------------------------

/** A vertical gradient from [stops] (fraction to colour), like the boards' scrims. */
fun verticalScrim(vararg stops: Pair<Float, Color>): Brush = Brush.verticalGradient(*stops)

/** A horizontal gradient from [stops]. */
fun horizontalScrim(vararg stops: Pair<Float, Color>): Brush = Brush.horizontalGradient(*stops)

/** Fills the parent with [brush]. */
@Composable
fun Scrim(brush: Brush, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(brush))
}

/** A thin divider. */
@Composable
fun Divider(modifier: Modifier = Modifier, color: Color = PlasmaColors.Divider, vertical: Boolean = false) {
    if (vertical) Box(modifier.width(1.px).background(color)) else Box(modifier.height(1.px).background(color))
}
