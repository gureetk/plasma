// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.component.PlasmaImage

/**
 * What the video layer is showing. Until the player arrives (M1), a still picture of the channel
 * stands in for its video, so screens can be laid out and tested against the boards.
 */
data class VideoFrame(val still: String?, val alignment: Alignment = Alignment.Center)

/**
 * The one video layer (brief 5.4.4). It sits behind all of Plasma's UI; screens say where it goes
 * with [VideoSlot] and keep that area transparent. The real player moves and resizes a single
 * SurfaceView the same way, so moving between a preview and full screen never restarts playback.
 */
@Stable
class VideoLayerState {
    var frame by mutableStateOf<VideoFrame?>(null)
    var bounds by mutableStateOf<Rect?>(null)
    var cornerRadius by mutableStateOf(0.dp)

    /** Fades the picture with the screen around it (Home fades its top section out as it scrolls). */
    var alpha by mutableFloatStateOf(1f)

    /** Shown in the slot while there's no picture. */
    var background by mutableStateOf(PlasmaColors.Black)
    internal var owner: Any? = null
}

val LocalVideoLayer = staticCompositionLocalOf<VideoLayerState> { error("No VideoHost") }

/**
 * The root of every screen: the background colour, then the video wherever a [VideoSlot] put it,
 * then the screen itself on top.
 */
@Composable
fun VideoHost(state: VideoLayerState, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(PlasmaColors.Bg)) {
        val bounds = state.bounds
        val frame = state.frame
        if (bounds != null && frame != null) {
            val density = LocalDensity.current
            with(density) {
                PlasmaImage(
                    model = frame.still,
                    modifier = Modifier
                        .offset { IntOffset(bounds.left.toInt(), bounds.top.toInt()) }
                        .size(bounds.width.toDp(), bounds.height.toDp())
                        .clip(RoundedCornerShape(state.cornerRadius))
                        .alpha(state.alpha),
                    alignment = frame.alignment,
                    placeholder = state.background,
                )
            }
        }
        content()
    }
}

/**
 * Marks where the video shows on this screen and what it shows. The slot itself draws nothing,
 * so the screen must leave this area transparent.
 */
@Composable
fun VideoSlot(
    frame: VideoFrame?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
    alpha: Float = 1f,
    background: Color = PlasmaColors.Black,
) {
    val layer = LocalVideoLayer.current
    val token = remember { Any() }
    SideEffect { if (layer.owner === token) layer.alpha = alpha }
    DisposableEffect(layer, frame, cornerRadius, background) {
        layer.owner = token
        layer.frame = frame
        layer.cornerRadius = cornerRadius
        layer.alpha = alpha
        layer.background = background
        onDispose {
            if (layer.owner === token) {
                layer.owner = null
                layer.bounds = null
                layer.frame = null
                layer.alpha = 1f
            }
        }
    }
    Box(modifier.onGloballyPositioned { coordinates ->
        if (layer.owner === token) layer.bounds = coordinates.boundsInRoot()
    })
}
