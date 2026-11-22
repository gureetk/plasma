// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The colour content inside a surface should use. Turns dark on a focused (white) surface. */
val LocalContentColor = compositionLocalOf { PlasmaColors.Text }

/** Whether the nearest [PlasmaSurface] has focus. */
val LocalFocused = compositionLocalOf { false }

/**
 * Inside a [PlasmaSurface] whose description already says what's in it (#128). Its words stay in
 * the semantics tree, where tests find them, but are hidden from TalkBack ([forTalkBack]), so it
 * doesn't read them after the description.
 */
val LocalSaidAbove = compositionLocalOf { false }

/**
 * Hides words from TalkBack where the surface they're in already says them ([LocalSaidAbove]):
 * pass `LocalSaidAbove.current`. Text, icons and logos use it.
 */
fun Modifier.forTalkBack(saidAbove: Boolean): Modifier = if (saidAbove) unspoken() else this

/** Leaves a mark out of what TalkBack reads, such as the dot between two parts of a line (#128). */
fun Modifier.unspoken(): Modifier = semantics { hideFromAccessibility() }

/** Settings › Appearance › Reduce Motion: scale capped at 1.04, no rises, crossfades only (brief 2.6). */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Settings › Appearance › Lighter Effects (#127): the effects that cost a slower TV its speed are
 * drawn in a lighter way, which can be seen. Off, everything is drawn as the boards have it.
 */
val LocalLighterEffects = staticCompositionLocalOf { false }

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

/**
 * Motion from the brief, section 2.6: short, eased and calm, with no bounce, no springs that
 * overshoot and no parallax. Reduce Motion ([LocalReduceMotion]) keeps fades and drops the rest.
 */
object PlasmaMotion {
    /** Focus scale and lift: 150 ms on the brief's own curve (2.5). */
    fun <T> focus(): TweenSpec<T> = tween(durationMillis = 150, easing = FastOutSlowInEasing)

    /** The fill turning white (or back): 120 ms. */
    fun <T> colour(): TweenSpec<T> = tween(durationMillis = 120, easing = FastOutSlowInEasing)

    /** A screen change: a 200 ms crossfade, the incoming screen settling [SCREEN_SETTLE] upward. */
    fun <T> screen(): TweenSpec<T> = tween(durationMillis = 200, easing = FastOutSlowInEasing)

    /**
     * An overlay, sheet or toast coming in: 200 ms, slowing as it arrives. With [overlayOut]'s
     * curve, one overlay taking another's place keeps the picture behind about as dark throughout.
     */
    fun <T> overlayIn(): TweenSpec<T> = tween(durationMillis = 200, easing = LinearOutSlowInEasing)

    /** An overlay going: 150 ms, gathering pace, so it's soon out of the way. */
    fun <T> overlayOut(): TweenSpec<T> = tween(durationMillis = 150, easing = FastOutLinearInEasing)

    /** Content that changes in place, fading in on its one copy: 150 ms. */
    fun <T> swap(): TweenSpec<T> = tween(durationMillis = 150, easing = LinearOutSlowInEasing)

    /** A list item sliding to its new place, as one moves or goes: 200 ms. */
    fun <T> place(): TweenSpec<T> = tween(durationMillis = 200, easing = FastOutSlowInEasing)

    /** A list item fading in or out as it comes or goes: 150 ms. */
    fun <T> itemFade(): TweenSpec<T> = tween(durationMillis = 150, easing = LinearOutSlowInEasing)

    /** A progress bar easing to a new value rather than jumping: 200 ms. */
    fun <T> progress(): TweenSpec<T> = tween(durationMillis = 200, easing = FastOutSlowInEasing)

    /** How far an overlay rises as it comes in (and sinks as it goes): 8 dp. */
    val OVERLAY_RISE: Dp = 8.dp

    /** How far a screen settles upward as it comes in: 16 dp. */
    val SCREEN_SETTLE: Dp = 16.dp

    /** Reduce Motion's largest focus growth. */
    const val REDUCED_SCALE = 1.04f

    /** Content that changes again within this long swaps without a fade, as while a key is held. */
    const val SWAP_QUIET_MS = 250L

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

    /**
     * With Lighter Effects, how long focus rests on an item in Home's rows before the picture
     * behind the rows follows it (#127): as long as Live TV's details wait.
     */
    const val BACKDROP_REST_MS = DETAILS_DWELL_MS
}

/**
 * Plasma's look, with Settings › Appearance's choices (brief 5.16): the [accent], [reduceMotion],
 * [lighterEffects], and [textScale], which every size set in `pxSp` grows by (Text Size: Large).
 *
 * A theme inside another, such as first-time setup's preview of a look (#85), scales text from the
 * TV's own size rather than the outer theme's, so Default inside Large is Default.
 */
@Composable
fun PlasmaTheme(
    accent: Color = PlasmaColors.AccentWhite,
    reduceMotion: Boolean = false,
    textScale: Float = 1f,
    lighterEffects: Boolean = false,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val outer = LocalTextScale.current
    CompositionLocalProvider(
        LocalAccent provides accent,
        LocalReduceMotion provides reduceMotion,
        LocalLighterEffects provides lighterEffects,
        LocalContentColor provides PlasmaColors.Text,
        LocalTextScale provides textScale,
        LocalDensity provides if (textScale == outer) density else Density(density.density, density.fontScale / outer * textScale),
        content = content,
    )
}

/** The text scale the nearest [PlasmaTheme] applies. */
private val LocalTextScale = staticCompositionLocalOf { 1f }

/** Text Size: Large, against Default. */
const val LARGE_TEXT_SCALE = 1.15f
