// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import ca.gureet.plasma.core.design.PlasmaColors
import kotlin.math.ceil

/*
 * Pictures behind a screen, drawn with as few offscreen passes as they need (#127), and looking as
 * they did.
 *
 * Android draws a blurred layer in two steps: what's in it into an offscreen picture, then that
 * picture, blurred, onto what's under it. Unless Android keeps the blurred result itself, which it
 * does with some graphics drivers only, it blurs again at each frame that draws the layer, and
 * blurring a picture the size of the screen takes several passes over it. A faded layer with
 * another layer in it is an offscreen picture too, drawn again whenever anything in it changes.
 *
 * So a blurred picture here is kept: blurred into a layer of its own once, and that layer drawn at
 * each frame until the picture changes. What fades it, as one picture gives way to the next or as
 * a picture comes in, changes how the kept layer is drawn and never what's in it. And it's kept
 * only while any of it shows: a picture that isn't there yet, or has faded out, takes no layer.
 *
 * And a picture under a wide blur is blurred from a smaller copy of itself, stretched back over its
 * place: the blur leaves nothing the copy hasn't got, and a copy a quarter the size each way is a
 * sixteenth of the pixels to decode, to blur and to keep.
 *
 * And the part of a picture that lies under something solid isn't painted ([drawnDownTo]). Android
 * paints all of a screen again at each frame of a move, one thing over another, and the GPU's time
 * goes by how much is painted, whether or not it ends up showing.
 */

/**
 * `Crossfade`, with each content drawing its own fade: it shows [targetState]'s [content], and when
 * that changes, the old fades out as the new fades in over it, by [animationSpec]. `Crossfade`
 * puts each content in a faded layer of its own, an offscreen picture while it fades; here
 * [content] gets its alpha instead, to read where it draws: a content that is one picture, or a
 * layer already, fades the same without one.
 */
@Composable
fun <T> FadeBetween(
    targetState: T,
    modifier: Modifier = Modifier,
    animationSpec: FiniteAnimationSpec<Float> = tween(),
    label: String = "FadeBetween",
    content: @Composable (state: T, alpha: State<Float>) -> Unit,
) {
    val transition = updateTransition(targetState, label)
    // What's on screen, in the order it came: the target last, over what's fading out.
    val showing = remember { mutableStateListOf(transition.currentState) }
    if (transition.currentState == transition.targetState) {
        // Settled: only the target is left.
        if (showing.size != 1 || showing[0] != transition.targetState) showing.removeAll { it != transition.targetState }
    }
    if (transition.targetState !in showing) showing.add(transition.targetState)
    Box(modifier) {
        for (i in showing.indices) {
            val state = showing[i]
            key(state) {
                val alpha = transition.animateFloat(transitionSpec = { animationSpec }, label = "alpha") { if (it == state) 1f else 0f }
                content(state, alpha)
            }
        }
    }
}

/**
 * Paints what's in this no further down than [fraction] of its height while [cut] says so, and
 * all of it otherwise (#127): for a backdrop whose lower part lies under something solid, such as
 * the end of a gradient that fades it into the screen's own color. What's cut is never seen, so it
 * looks the same, and the GPU has that much less to paint at each frame. The cut is at the whole
 * pixel at or below [fraction], so a gradient that's solid from [fraction] down is whole above it.
 * [cut] is read as it's drawn.
 */
fun Modifier.drawnDownTo(fraction: Float, cut: () -> Boolean): Modifier = drawWithContent {
    if (cut()) {
        clipRect(bottom = ceil(size.height * fraction)) { this@drawWithContent.drawContent() }
    } else {
        drawContent()
    }
}

/**
 * A picture's painter with its fade-in left to whoever draws it. [shown] is how far the picture has
 * come in: 0 until it's there, then up to 1 as [ImageResolver.painter]'s own fade would have taken
 * it, or 1 from the start for a picture that has none. Read it where the picture is drawn.
 */
@Stable
class ArrivingPainter(val painter: Painter, val shown: State<Float> = Arrived)

/** An [ImageResolver] that can leave a picture's fade-in to whoever draws it (#127). */
interface ArrivingImageResolver : ImageResolver {
    /**
     * [model]'s picture as [painter] gives it, but for the fade it has as it arrives, which comes
     * as [ArrivingPainter.shown] instead. With a [size], in pixels, it's decoded no larger than
     * covering that needs, as [SizedImageResolver.painter] does it.
     */
    @Composable
    fun arriving(model: String, size: IntSize?): ArrivingPainter?
}

/** A picture that's simply there. */
private object Arrived : State<Float> {
    override val value: Float get() = 1f
}

@Composable
private fun arrivingPainter(model: String, size: IntSize?): ArrivingPainter? {
    val resolver = LocalImageResolver.current
    if (resolver is ArrivingImageResolver) return resolver.arriving(model, size)
    val painter = (if (size != null && resolver is SizedImageResolver) resolver.painter(model, size, ContentScale.Crop) else resolver.painter(model)) ?: return null
    return remember(painter) { ArrivingPainter(painter) }
}

/**
 * How much smaller, each way, a picture is drawn to be blurred by [radiusPx] and stretched back
 * over its place (#127): by 4 under Home's widest blur, CSS `blur(60px)`, and by 2 under the
 * `blur(28px)` behind a poster. Either way the copy's own blur has a sigma of 11 px or more (15
 * and 14 for those two), which leaves nothing that stretching it back could show.
 */
internal fun blurReduction(radiusPx: Float): Int = when {
    radiusPx >= WIDE_BLUR_PX -> 4
    radiusPx >= MIDDLING_BLUR_PX -> 2
    else -> 1
}

/**
 * The radius that blurs a copy [by] times smaller as [radiusPx] blurs the whole. Android blurs by
 * a sigma of 0.57735 × radius + 0.5, and it's the sigma that shrinks with the copy.
 */
internal fun reducedBlurRadius(radiusPx: Float, by: Float): Float =
    if (by <= 1f) radiusPx else (((radiusPx * SIGMA_PER_RADIUS + SIGMA_AT_NONE) / by - SIGMA_AT_NONE) / SIGMA_PER_RADIUS).coerceAtLeast(0f)

private const val WIDE_BLUR_PX = 80f
private const val MIDDLING_BLUR_PX = 40f
private const val SIGMA_PER_RADIUS = 0.57735f
private const val SIGMA_AT_NONE = 0.5f

/** How much smaller than its place a copy came out, each way, once it's measured. */
private class CopyScale {
    val across = mutableFloatStateOf(1f)
    val down = mutableFloatStateOf(1f)
}

/** [length] a [by]th as long, rounded up to whole pixels. */
private fun smaller(length: Int, by: Int): Int = ceil(length / by.toFloat()).toInt().coerceAtLeast(1)

/**
 * What follows, measured a [by]th of this place each way and drawn stretched back over it, in a
 * layer of that smaller size that [layer] sets up as `graphicsLayer` would. [scale] is told how
 * much smaller it came out. A place without a size of its own is left as it is.
 */
private fun Modifier.drawnSmaller(by: Int, scale: CopyScale, layer: GraphicsLayerScope.() -> Unit): Modifier = layout { measurable, constraints ->
    if (!constraints.hasFixedWidth || !constraints.hasFixedHeight) {
        val whole = measurable.measure(constraints)
        scale.across.floatValue = 1f
        scale.down.floatValue = 1f
        layout(whole.width, whole.height) { whole.placeWithLayer(0, 0, layerBlock = layer) }
    } else {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val copy = measurable.measure(Constraints.fixed(smaller(width, by), smaller(height, by)))
        // A size that doesn't divide leaves the copy a part of a pixel over: each way has its own.
        val across = width / copy.width.toFloat()
        val down = height / copy.height.toFloat()
        scale.across.floatValue = across
        scale.down.floatValue = down
        layout(width, height) {
            copy.placeWithLayer(0, 0) {
                scaleX = across
                scaleY = down
                transformOrigin = TransformOrigin(0f, 0f)
                layer()
            }
        }
    }
}

/**
 * [model]'s picture, blurred by [radius]: what `PlasmaImage(model, modifier.blur(radius))` draws,
 * with the blurred picture kept as a layer, so a frame that leaves the picture as it is draws that
 * layer and blurs nothing (#127).
 *
 * The picture's fade as it arrives is drawn on the kept layer too, where the resolver can leave
 * it to this ([ArrivingImageResolver]), rather than under the blur, where each of its frames
 * would blur the picture again. A blur of a faded picture is that fade of the blurred picture, so
 * it looks the same.
 *
 * While none of it shows (the picture isn't there yet, or [alpha] is 0), nothing is blurred or
 * kept: Android gives a layer its memory whether or not it's seen, and a row moved along quickly
 * leaves many pictures on their way out behind it.
 *
 * Under a wide blur the picture is decoded, blurred and kept as a smaller copy ([blurReduction]),
 * and the kept layer is stretched back over its place as it's drawn. It can't be told from the
 * whole picture blurred, and it isn't the same to the last step of each pixel.
 *
 * @param opacity the picture's own, under the blur: `alpha(opacity)` after `blur(radius)`.
 * @param alpha the kept layer's, read as it's drawn: [FadeBetween]'s, for one picture giving way
 *   to the next.
 * @param drawnSize the size it's drawn at, when the caller knows it: a picture from the network
 *   is then decoded for that, and otherwise for the window.
 */
@Composable
fun BlurredImage(
    model: String?,
    radius: Dp,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    placeholder: Color = PlasmaColors.Surface2,
    colorFilter: ColorFilter? = null,
    opacity: Float = 1f,
    alpha: State<Float>? = null,
    drawnSize: DpSize? = null,
) {
    val reduction = with(LocalDensity.current) { blurReduction(radius.toPx()) }
    BlurredImage(model, radius, reduction, modifier, alignment, placeholder, colorFilter, opacity, alpha, drawnSize)
}

/** [BlurredImage] from a copy [reduction] times smaller each way, or from the whole picture at 1. */
@Composable
internal fun BlurredImage(
    model: String?,
    radius: Dp,
    reduction: Int,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    placeholder: Color = PlasmaColors.Surface2,
    colorFilter: ColorFilter? = null,
    opacity: Float = 1f,
    alpha: State<Float>? = null,
    drawnSize: DpSize? = null,
) {
    // Decoded no larger than the copy: the caller's size or the window's, that much smaller.
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val decoded = remember(drawnSize, reduction, density, window) {
        val whole = drawnSize?.let { with(density) { IntSize(it.width.roundToPx(), it.height.roundToPx()) } } ?: window
        if (whole.width <= 0 || whole.height <= 0 || (drawnSize == null && reduction == 1)) null else IntSize(smaller(whole.width, reduction), smaller(whole.height, reduction))
    }
    val arriving = model?.let { arrivingPainter(it, decoded) }
    if (arriving == null) {
        // The placeholder: a blur leaves a plain color as it is, so it's drawn as it is, at the
        // picture's opacity and with its fade, in no layer; a clear one is nothing at all.
        val color = placeholder.copy(alpha = placeholder.alpha * opacity)
        val faded = if (alpha == null) {
            Modifier
        } else {
            Modifier.graphicsLayer {
                this.alpha = alpha.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
        }
        Box(if (color.alpha > 0f) modifier.then(faded).background(color) else modifier)
        return
    }
    val shown = arriving.shown
    // Whether any of it shows: read by the layers, which change only as it starts or stops showing.
    val showing = remember(alpha, shown) { derivedStateOf { (alpha?.value ?: 1f) * shown.value > 0f } }
    val copy = remember { CopyScale() }
    val kept = remember(alpha, shown, showing, radius, reduction, copy) {
        val keeping: GraphicsLayerScope.() -> Unit = {
            this.alpha = (alpha?.value ?: 1f) * shown.value
            compositingStrategy = if (showing.value) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
        (if (reduction > 1) Modifier.drawnSmaller(reduction, copy, keeping) else Modifier.graphicsLayer(keeping))
            // `blur(radius)`, as wide as the copy needs, while any of it shows.
            .graphicsLayer {
                val across = reducedBlurRadius(radius.toPx(), copy.across.floatValue)
                val down = reducedBlurRadius(radius.toPx(), copy.down.floatValue)
                renderEffect = if (showing.value && (across > 0f || down > 0f)) BlurEffect(across, down, TileMode.Clamp) else null
                shape = RectangleShape
                clip = true
            }
    }
    // One thing is drawn under the blur, so fading that one thing is fading the whole, with no
    // offscreen picture in between.
    val dimmed = if (opacity < 1f) Modifier.graphicsLayer(alpha = opacity, compositingStrategy = CompositingStrategy.ModulateAlpha) else Modifier
    Image(
        arriving.painter,
        contentDescription = null,
        modifier = modifier.then(kept).then(dimmed),
        contentScale = ContentScale.Crop,
        alignment = alignment,
        colorFilter = colorFilter,
    )
}
