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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.PlasmaColors

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
     * as [ArrivingPainter.shown] instead.
     */
    @Composable
    fun arriving(model: String): ArrivingPainter?
}

/** A picture that's simply there. */
private object Arrived : State<Float> {
    override val value: Float get() = 1f
}

@Composable
private fun arrivingPainter(model: String): ArrivingPainter? {
    val resolver = LocalImageResolver.current
    if (resolver is ArrivingImageResolver) return resolver.arriving(model)
    val painter = resolver.painter(model) ?: return null
    return remember(painter) { ArrivingPainter(painter) }
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
 * @param opacity the picture's own, under the blur: `alpha(opacity)` after `blur(radius)`.
 * @param alpha the kept layer's, read as it's drawn: [FadeBetween]'s, for one picture giving way
 *   to the next.
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
) {
    val arriving = model?.let { arrivingPainter(it) }
    val shown = arriving?.shown
    // Whether any of it shows: read by the layers, which change only as it starts or stops showing.
    val showing = remember(alpha, shown) { derivedStateOf { (alpha?.value ?: 1f) * (shown?.value ?: 1f) > 0f } }
    val kept = remember(alpha, shown, showing, radius) {
        Modifier
            .graphicsLayer {
                this.alpha = (alpha?.value ?: 1f) * (shown?.value ?: 1f)
                compositingStrategy = if (showing.value) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            // `blur(radius)`, while any of it shows.
            .graphicsLayer {
                val blur = radius.toPx()
                renderEffect = if (showing.value && blur > 0f) BlurEffect(blur, blur, TileMode.Clamp) else null
                shape = RectangleShape
                clip = true
            }
    }
    // One thing is drawn under the blur, so fading that one thing is fading the whole, with no
    // offscreen picture in between.
    val dimmed = if (opacity < 1f) Modifier.graphicsLayer(alpha = opacity, compositingStrategy = CompositingStrategy.ModulateAlpha) else Modifier
    val picture = modifier.then(kept).then(dimmed)
    if (arriving == null) {
        // The placeholder, blurred as the picture would be; a clear one is nothing at all.
        Box(if (placeholder.alpha > 0f) picture.background(placeholder) else modifier)
    } else {
        Image(
            arriving.painter,
            contentDescription = null,
            modifier = picture,
            contentScale = ContentScale.Crop,
            alignment = alignment,
            colorFilter = colorFilter,
        )
    }
}
