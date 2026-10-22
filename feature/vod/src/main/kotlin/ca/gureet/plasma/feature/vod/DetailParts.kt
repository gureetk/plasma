// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.vod

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.absolute
import ca.gureet.plasma.core.design.component.BackPill
import ca.gureet.plasma.core.design.component.ClockText
import ca.gureet.plasma.core.design.component.LocalImageResolver
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.component.TechBadge
import ca.gureet.plasma.core.design.component.brightness
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import kotlin.math.min

/**
 * The scrims over a detail page's backdrop, as (position, opacity) stops in the page's own color:
 * across from the left, so the text reads, and down from the top, so the tabs or episodes below
 * sit on solid color. Positions are fractions of the 1920 × 1080 screen.
 */
internal class DetailScrims(val across: List<Pair<Float, Float>>, val down: List<Pair<Float, Float>>) {
    companion object {
        /** Board 14: solid from 820 px, under More Like This. */
        val Movie = DetailScrims(
            across = listOf(0f to 0.96f, 0.30f to 0.86f, 0.55f to 0.35f, 0.72f to 0f),
            down = listOf(0f to 0.6f, (180 / 1080f) to 0f, (520 / 1080f) to 0f, (740 / 1080f) to 0.9f, (820 / 1080f) to 1f),
        )

        /** Board 16: darker from 700 px, for the season tabs and episodes. */
        val Series = DetailScrims(
            across = listOf(0f to 0.96f, 0.30f to 0.8f, 0.56f to 0.2f, 0.70f to 0f),
            down = listOf(0f to 0.5f, (180 / 1080f) to 0f, (520 / 1080f) to 0f, (700 / 1080f) to 0.82f, (820 / 1080f) to 0.94f, 1f to 1f),
        )
    }
}

private fun List<Pair<Float, Float>>.stops(color: Color): Array<Pair<Float, Color>> =
    map { (at, alpha) -> at to color.copy(alpha = alpha) }.toTypedArray()

/** CSS `blur(60px)` as a Compose blur radius (Android blurs with sigma ≈ 0.58 × radius). */
private val CssBlur60 = 104.px

/** The full-bleed backdrop in the page's color, shifted as its art needs, under the two scrims. */
@Composable
internal fun DetailBackground(backdrop: Backdrop, scrims: DetailScrims) {
    Box(Modifier.fillMaxSize().background(backdrop.color)) {
        if (backdrop.blurred) {
            PlasmaImage(backdrop.image, Modifier.fillMaxSize().blur(CssBlur60), placeholder = backdrop.color, colorFilter = brightness(0.6f))
        } else {
            PlasmaImage(
                backdrop.image,
                Modifier.absolute(backdrop.shiftXPx.px, backdrop.shiftYPx.px, 1920.px, 1080.px),
                placeholder = backdrop.color,
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(*scrims.across.stops(backdrop.color))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(*scrims.down.stops(backdrop.color))))
    }
}

/** "‹ Movies" on the left, the clock on the right (boards 14 and 16). */
@Composable
internal fun DetailHeader(backLabel: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.offset(96.px, 46.px).size(1728.px, 56.px),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackPill(backLabel, onBack)
        Spacer(Modifier.weight(1f))
        ClockText()
    }
}

/** Title art fits within this box (board pixels): The Last Lighthouse fills its width, Atlas Station its height. */
private const val TITLE_ART_MAX_W = 800f
private const val TITLE_ART_MAX_H = 188f

/** The title's logo art (brief 4.14), or the title in large type when there's none. */
@Composable
internal fun TitleArt(art: String?, title: String, modifier: Modifier = Modifier) {
    val painter = art?.let { LocalImageResolver.current.painter(it) }
    val intrinsic = painter?.intrinsicSize
    if (painter == null || intrinsic == null || !intrinsic.isSpecified || intrinsic.width <= 0f || intrinsic.height <= 0f) {
        PlasmaText(title, PlasmaType.Display, modifier.widthIn(max = 960.px), color = PlasmaColors.Text, maxLines = 2)
        return
    }
    val aspect = intrinsic.width / intrinsic.height
    val heightPx = min(TITLE_ART_MAX_H, TITLE_ART_MAX_W / aspect)
    Image(painter, contentDescription = title, modifier = modifier.size((heightPx * aspect).px, heightPx.px), contentScale = ContentScale.Fit)
}

/** "Drama · Mystery · 2025 · 2 h 14 min" and the outlined badges after it. */
@Composable
internal fun MetaLine(parts: List<String>, badges: List<String>, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px)) {
        val style = plasmaStyle(23f, 500)
        parts.forEachIndexed { i, part ->
            if (i > 0) PlasmaText("·", style, color = PlasmaColors.Text4)
            PlasmaText(part, style, color = PlasmaColors.TextSoft)
        }
        if (badges.isNotEmpty()) {
            Row(Modifier.padding(start = 8.px), horizontalArrangement = Arrangement.spacedBy(7.px)) {
                for (badge in badges) TechBadge(badge, borderColor = PlasmaColors.white(0.42f))
            }
        }
    }
}

/** Up to three lines of synopsis (brief 4.14). */
@Composable
internal fun Synopsis(text: String, maxWidthPx: Int, modifier: Modifier = Modifier) {
    PlasmaText(
        text,
        plasmaStyle(25f, 400, lineHeight = 1.5f),
        modifier.widthIn(max = maxWidthPx.px),
        color = PlasmaColors.TextSoft,
        maxLines = 3,
    )
}

/** A 200 px bar and what's left: "1 h 02 min left", "Dead Reckoning · 18 min left". */
@Composable
internal fun ProgressLine(fraction: Float?, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.px)) {
        if (fraction != null) {
            ProgressBar(fraction, Modifier.width(200.px), height = 4.px, track = PlasmaColors.white(0.2f))
        }
        PlasmaText(text, plasmaStyle(21f, 500, tabular = true), color = PlasmaColors.Text2)
    }
}
