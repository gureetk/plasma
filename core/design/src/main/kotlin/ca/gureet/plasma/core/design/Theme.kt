// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** The colour content inside a surface should use. Turns dark on a focused (white) surface. */
val LocalContentColor = compositionLocalOf { PlasmaColors.Text }

/** Whether the nearest [PlasmaSurface] has focus. */
val LocalFocused = compositionLocalOf { false }

/** Settings › Appearance › Reduce Motion: scale capped at 1.04, no rises, crossfades only (brief 2.6). */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Shapes from the brief, section 2.4, at board radii. */
object PlasmaShapes {
    val DockTile = RoundedCornerShape(16.px)
    val Row = RoundedCornerShape(14.px)
    val Thumbnail = RoundedCornerShape(14.px)
    val Card = RoundedCornerShape(14.px)
    val Button = RoundedCornerShape(12.px)
    val Poster = RoundedCornerShape(12.px)
    val Tab = RoundedCornerShape(12.px)
    /** Tabs on detail pages (More Like This, seasons). */
    val SmallTab = RoundedCornerShape(10.px)
    /** The 150 × 225 posters on a detail page. */
    val PosterSmall = RoundedCornerShape(10.px)
    val Field = RoundedCornerShape(12.px)
    val Key = RoundedCornerShape(10.px)
    val PinKey = RoundedCornerShape(18.px)
    val Plate = RoundedCornerShape(10.px)
    val SmallPlate = RoundedCornerShape(9.px)
    val Cell = RoundedCornerShape(8.px)
    val Badge = RoundedCornerShape(5.px)
    val Pill = RoundedCornerShape(7.px)
    val Sheet = RoundedCornerShape(28.px)
    val Toast = RoundedCornerShape(22.px)
    val HeroIcon = RoundedCornerShape(48.px)
    val Round = CircleShape
}

/** Motion from the brief, section 2.6. */
object PlasmaMotion {
    fun <T> focus(): TweenSpec<T> = tween(durationMillis = 150, easing = FastOutSlowInEasing)
    fun <T> colour(): TweenSpec<T> = tween(durationMillis = 120, easing = FastOutSlowInEasing)
    fun <T> screen(): TweenSpec<T> = tween(durationMillis = 200, easing = FastOutSlowInEasing)
    fun <T> overlayIn(): TweenSpec<T> = tween(durationMillis = 200, easing = FastOutSlowInEasing)
    fun <T> overlayOut(): TweenSpec<T> = tween(durationMillis = 150, easing = FastOutSlowInEasing)

    const val OVERLAY_HIDE_MS = 5_000L
    const val ZAP_BANNER_HIDE_MS = 4_000L
    const val ZAP_HINT_HIDE_MS = 8_000L
    const val NUMBER_ENTRY_TUNE_MS = 1_500L
    const val SEARCH_DEBOUNCE_MS = 150L
    const val HERO_ADVANCE_MS = 10_000L

    /**
     * How long focus rests on a channel in Live TV before what's on and coming up under the picture
     * follows it, so holding Down doesn't redraw it for every row. The picture itself waits for OK
     * (the owner's ask, 29 October), where brief 4.7 had it follow focus after 600 ms.
     */
    const val DETAILS_DWELL_MS = 300L
}

/**
 * Plasma's look, with Settings › Appearance's choices (brief 5.16): the [accent], [reduceMotion],
 * and [textScale], which every size set in `pxSp` grows by (Text Size: Large).
 */
@Composable
fun PlasmaTheme(
    accent: Color = PlasmaColors.AccentWhite,
    reduceMotion: Boolean = false,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalAccent provides accent,
        LocalReduceMotion provides reduceMotion,
        LocalContentColor provides PlasmaColors.Text,
        LocalDensity provides if (textScale == 1f) density else Density(density.density, density.fontScale * textScale),
        content = content,
    )
}

/** Text Size: Large, against Default. */
const val LARGE_TEXT_SCALE = 1.15f
