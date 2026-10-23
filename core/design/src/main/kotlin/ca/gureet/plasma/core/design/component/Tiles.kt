// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocusPreview
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.boxShadow
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

/**
 * The graphite fill of the Home dock tiles, All Apps and See All (board 01): a soft top-to-bottom
 * gradient with a hairline highlight along the top edge, both brighter when focused. Put it first
 * inside a [ca.gureet.plasma.core.design.PlasmaSurface] with `FocusStyle.Tile`.
 */
@Composable
fun GraphiteFill(modifier: Modifier = Modifier, focused: Boolean = LocalFocused.current) {
    val top by animateColorAsState(
        if (focused) PlasmaColors.GraphiteFocusedTop else PlasmaColors.GraphiteTop,
        PlasmaMotion.colour(),
        label = "graphiteTop",
    )
    val bottom by animateColorAsState(
        if (focused) PlasmaColors.GraphiteFocusedBottom else PlasmaColors.GraphiteBottom,
        PlasmaMotion.colour(),
        label = "graphiteBottom",
    )
    val highlight = PlasmaColors.white(if (focused) 0.16f else 0.07f)
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .drawWithContent {
                drawContent()
                drawLine(highlight, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1f)
            },
    )
}

/**
 * A graphite square with a line icon, heading a page: Settings (220 px, board 19), its pages
 * (180 px) and the PIN screen (120 px, board 25). A soft shadow under it, a hairline along the top.
 */
@Composable
fun GraphiteIconTile(
    icon: PlasmaIcons,
    size: Dp,
    radius: Dp,
    iconSize: Dp,
    stroke: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .boxShadow(PlasmaColors.black(0.5f), offsetY = 24.px, blur = 50.px, cornerRadius = radius)
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(Brush.verticalGradient(listOf(PlasmaColors.GraphiteFocusedTop, PlasmaColors.Surface3)))
            .drawWithContent {
                drawContent()
                drawLine(PlasmaColors.white(0.14f), Offset(0f, 0.5f), Offset(this.size.width, 0.5f), strokeWidth = 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        PlasmaIcon(icon, iconSize, tint = PlasmaColors.Text, stroke = stroke)
    }
}

/**
 * A 16:9 tile with a caption under it (Continue Watching and Search, boards 02 and 03). Focused, it
 * grows from its top edge and its caption moves down to make room; the title turns white and bold.
 */
@Composable
fun CaptionedTile(
    title: String,
    meta: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 400.px,
    height: Dp = 225.px,
    onLongClick: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    color: Color = PlasmaColors.Surface2,
    content: @Composable BoxScope.() -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val focused = hasFocus || LocalFocusPreview.current
    Column(Modifier.width(width)) {
        PlasmaSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.size(width, height),
            style = FocusStyle.Tile,
            shape = PlasmaShapes.Thumbnail,
            color = color,
            pivot = TransformOrigin(0.5f, 0f),
            onFocusChanged = {
                hasFocus = it
                onFocusChanged?.invoke(it)
            },
            content = content,
        )
        val shift by animateDpAsState(if (focused) 16.px else 0.px, PlasmaMotion.focus(), label = "captionShift")
        Column(
            Modifier.padding(top = 14.px).offset { IntOffset(0, shift.roundToPx()) },
            verticalArrangement = Arrangement.spacedBy(3.px),
        ) {
            PlasmaText(
                title,
                plasmaStyle(24f, if (focused) 600 else 500),
                color = if (focused) PlasmaColors.Text else PlasmaColors.TextSoft,
            )
            if (meta != null) PlasmaText(meta, plasmaStyle(21f, 500, tabular = true), color = PlasmaColors.Text2)
        }
    }
}

/**
 * A 2:3 poster (Movies and Series, boards 13 and 15). Focused, it grows from its top edge and shows
 * its caption centred underneath, wider than the poster if it needs to be. Without a [caption]
 * line the title still shows; [showCaption] false leaves both off (a detail page's small posters,
 * which sit too low for one).
 */
@Composable
fun PosterTile(
    image: String?,
    title: String,
    caption: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 209.px,
    height: Dp = 314.px,
    shape: Shape = PlasmaShapes.Poster,
    pivot: TransformOrigin = TransformOrigin(0.5f, 0f),
    showCaption: Boolean = true,
    badge: String? = null,
    progress: Float? = null,
    onLongClick: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val focused = hasFocus || LocalFocusPreview.current
    Box(modifier.size(width, height).zIndex(if (focused) 1f else 0f)) {
        PlasmaSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxSize().semantics { contentDescription = title },
            style = FocusStyle.Tile,
            shape = shape,
            color = PlasmaColors.Surface2,
            pivot = pivot,
            onFocusChanged = {
                hasFocus = it
                onFocusChanged?.invoke(it)
            },
        ) {
            PlasmaImage(image, Modifier.fillMaxSize())
            if (badge != null) PosterBadge(badge, Modifier.align(Alignment.BottomStart).padding(start = 10.px, bottom = 10.px))
            if (progress != null) {
                ProgressBar(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth(), height = 5.px, track = PlasmaColors.white(0.25f), rounded = false)
            }
        }
        if (focused && showCaption) {
            Column(
                Modifier
                    .requiredWidth(width + 80.px)
                    .offset(y = height + 38.px),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.px),
            ) {
                PlasmaText(title, plasmaStyle(23f, 600), color = PlasmaColors.Text, align = TextAlign.Center)
                if (caption != null) {
                    PlasmaText(caption, plasmaStyle(20f, 500, tabular = true), color = PlasmaColors.Text2, align = TextAlign.Center)
                }
            }
        }
    }
}
