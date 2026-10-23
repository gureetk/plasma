// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.view.SurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.brightness
import kotlinx.coroutines.delay

/**
 * What the video layer is showing: a channel's live picture, or, on sample data, a still picture
 * standing in for it so screens can be laid out and tested against the boards.
 *
 * @param channelId the channel to play live, when the app has live video (M1).
 * @param muted Home's picture is muted by default (Settings › Launcher & Home); other slots have sound.
 */
data class VideoFrame(
    val still: String?,
    val alignment: Alignment = Alignment.Center,
    val channelId: String? = null,
    val muted: Boolean = false,
)

/**
 * The one video layer (brief 5.4.4). It sits behind all of Plasma's UI; screens say where it goes
 * with [VideoSlot] and keep that area transparent. The player moves and resizes a single
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

    /**
     * Behind a sheet (Player Options, board 10): the picture blurred, darkened and slightly enlarged.
     * A live surface can't be blurred, so live video is only darkened.
     */
    var backdrop by mutableStateOf(false)
    internal var owner: Any? = null
}

val LocalVideoLayer = staticCompositionLocalOf<VideoLayerState> { error("No VideoHost") }

/**
 * The root of every screen: the background colour, then the video wherever a [VideoSlot] put it,
 * then the screen itself on top.
 */
@Composable
fun VideoHost(state: VideoLayerState, content: @Composable () -> Unit) {
    val live = LocalLiveVideo.current
    Box(Modifier.fillMaxSize().background(PlasmaColors.Bg)) {
        val bounds = state.bounds
        val frame = state.frame
        val picture = live?.picture?.collectAsState()?.value
        if (live != null) LiveSurface(live, state)
        if (bounds != null && frame != null) {
            val showingLive = live != null && frame.channelId != null && picture == frame.channelId
            if (showingLive) {
                LiveOverlays(state, bounds)
            } else {
                Still(state, frame, bounds, liveBehind = live != null && frame.channelId != null)
            }
        }
        content()
    }
}

/**
 * The live surface. It stays in the hierarchy for good, moved to the slot or parked out of sight,
 * because a surface that's destroyed makes the engine rebuild its video output (brief 6.6).
 */
@Composable
private fun LiveSurface(live: LiveVideo, state: VideoLayerState) {
    val frame = state.frame
    val channelId = frame?.channelId
    val muted = frame?.muted ?: true
    LaunchedEffect(live, channelId, muted) {
        // Between two screens there can be a moment with no slot; only a longer gap stops playback.
        if (channelId == null) delay(STOP_AFTER_MS)
        live.show(channelId, muted)
    }
    // Live video leaving for good, as when the last source is deleted, stops what's playing.
    DisposableEffect(live) { onDispose { live.show(null, muted = true) } }
    val slot = state.bounds?.takeIf { channelId != null }
    val aspect by live.aspectRatio.collectAsState()
    val density = LocalDensity.current
    // The picture fits inside the slot (Picture Size: Fit, 5.4.4), with black bars around it.
    val bounds = slot?.let { fit(it, aspect) }
    if (slot != null && bounds != slot) {
        with(density) {
            Box(
                Modifier
                    .offset { IntOffset(slot.left.toInt(), slot.top.toInt()) }
                    .size(slot.width.toDp(), slot.height.toDp())
                    .clip(RoundedCornerShape(state.cornerRadius))
                    .background(Color.Black),
            )
        }
    }
    val placement = if (bounds != null) {
        with(density) {
            Modifier
                .offset { IntOffset(bounds.left.toInt(), bounds.top.toInt()) }
                .size(bounds.width.toDp(), bounds.height.toDp())
        }
    } else {
        Modifier.offset { IntOffset(-PARKED_PX, -PARKED_PX) }.size(1.dp)
    }
    AndroidView(
        factory = { context -> SurfaceView(context).also(live::attach) },
        modifier = placement,
        onRelease = live::detach,
    )
}

/** The largest rectangle with the picture's [aspect] that fits in [slot], centred. */
internal fun fit(slot: Rect, aspect: Float?): Rect {
    if (aspect == null || aspect <= 0f || slot.height <= 0f) return slot
    val slotAspect = slot.width / slot.height
    // Within a percent is close enough; most channels are 16:9 in a 16:9 slot.
    if (kotlin.math.abs(slotAspect - aspect) / aspect < 0.01f) return slot
    return if (aspect > slotAspect) {
        val height = slot.width / aspect
        Rect(slot.left, slot.top + (slot.height - height) / 2, slot.right, slot.top + (slot.height + height) / 2)
    } else {
        val width = slot.height * aspect
        Rect(slot.left + (slot.width - width) / 2, slot.top, slot.left + (slot.width + width) / 2, slot.bottom)
    }
}

/** Over live video: the backdrop's darkening, Home's fade and the slot's rounded corners. */
@Composable
private fun LiveOverlays(state: VideoLayerState, bounds: Rect) {
    val density = LocalDensity.current
    val placed = with(density) {
        Modifier
            .offset { IntOffset(bounds.left.toInt(), bounds.top.toInt()) }
            .size(bounds.width.toDp(), bounds.height.toDp())
    }
    // A surface can't fade, so the screen's background covers it as Home scrolls away.
    if (state.alpha < 1f) Box(placed.background(PlasmaColors.Bg.copy(alpha = 1f - state.alpha)))
    if (state.backdrop) Box(placed.background(Color.Black.copy(alpha = BACKDROP_DIM)))
    val radius = with(density) { state.cornerRadius.toPx() }
    if (radius > 0f) {
        Canvas(placed) {
            val corners = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
            }
            drawPath(corners, PlasmaColors.Bg)
        }
    }
}

/** The sample still, or, over a live channel that's still opening, the slot's background. */
@Composable
private fun Still(state: VideoLayerState, frame: VideoFrame, bounds: Rect, liveBehind: Boolean) {
    with(LocalDensity.current) {
        val backdrop = state.backdrop
        PlasmaImage(
            model = if (liveBehind) null else frame.still,
            modifier = Modifier
                .offset { IntOffset(bounds.left.toInt(), bounds.top.toInt()) }
                .size(bounds.width.toDp(), bounds.height.toDp())
                .clip(RoundedCornerShape(state.cornerRadius))
                .alpha(state.alpha)
                .then(
                    if (backdrop) {
                        Modifier
                            .graphicsLayer {
                                scaleX = 1.08f
                                scaleY = 1.08f
                            }
                            .blur(38.dp)
                    } else {
                        Modifier
                    },
                ),
            alignment = frame.alignment,
            placeholder = state.background,
            colorFilter = if (backdrop) brightness(0.62f) else null,
        )
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
    backdrop: Boolean = false,
) {
    val layer = LocalVideoLayer.current
    val token = remember { Any() }
    SideEffect {
        if (layer.owner === token) {
            layer.alpha = alpha
            layer.backdrop = backdrop
        }
    }
    DisposableEffect(layer, frame, cornerRadius, background) {
        layer.owner = token
        layer.frame = frame
        layer.cornerRadius = cornerRadius
        layer.alpha = alpha
        layer.background = background
        layer.backdrop = backdrop
        onDispose {
            if (layer.owner === token) {
                layer.owner = null
                layer.bounds = null
                layer.frame = null
                layer.alpha = 1f
                layer.backdrop = false
            }
        }
    }
    Box(modifier.onGloballyPositioned { coordinates ->
        if (layer.owner === token) layer.bounds = coordinates.boundsInRoot()
    })
}

/** How long a screen without a video slot waits before live video stops. */
private const val STOP_AFTER_MS = 1_500L
private const val PARKED_PX = 16
private const val BACKDROP_DIM = 0.38f
