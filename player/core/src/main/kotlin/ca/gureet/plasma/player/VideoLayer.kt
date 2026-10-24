// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.graphics.PixelFormat
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ca.gureet.plasma.core.design.InstrumentSans
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.brightness
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the video layer is showing: a channel's live picture, or, on sample data, a still picture
 * standing in for it so screens can be laid out and tested against the boards.
 *
 * @param channelId the channel to play live, when the app has live video (M1).
 * @param muted Home's picture is muted by default (Settings › Launcher & Home); other slots have sound.
 * @param pictureSize how the live picture fills the slot: the player's Picture Size; previews fit.
 */
data class VideoFrame(
    val still: String?,
    val alignment: Alignment = Alignment.Center,
    val channelId: String? = null,
    val muted: Boolean = false,
    val pictureSize: PictureSize = PictureSize.Fit,
)

/** Player Options › Picture Size (brief 4.10 and 5.4.4): how the picture fills the screen. */
enum class PictureSize {
    /** All of the picture, in its own shape, with black bars where the shapes differ. */
    Fit,

    /** Stretched to the screen's shape. */
    Fill,

    /** The screen filled in the picture's own shape, its edges cut off. */
    Zoom,

    /** Shown as 16:9, whatever the stream says. */
    Wide,

    /** Shown as 4:3, whatever the stream says. */
    Standard,
}

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
            if (live != null && showingLive) {
                LiveCues(live, bounds, frame.pictureSize)
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
    // The picture goes in the slot as Picture Size says (4.10 and 5.4.4): it fits, with black bars
    // around it, unless the player asks otherwise.
    val bounds = slot?.let { place(it, aspect, frame?.pictureSize ?: PictureSize.Fit) }
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
        Modifier.placedAt(bounds)
    } else {
        Modifier.offset { IntOffset(-PARKED_PX, -PARKED_PX) }.size(1.dp)
    }
    AndroidView(
        factory = { context ->
            // The subtitles surface lies over the video one, the same size, for engines that draw
            // subtitles apart from the picture (VLC with hardware decoding, 5.4.4). It stays
            // empty, and so unseen, while there are none.
            FrameLayout(context).apply {
                val video = SurfaceView(context)
                val subtitles = SurfaceView(context).apply {
                    setZOrderMediaOverlay(true)
                    holder.setFormat(PixelFormat.TRANSLUCENT)
                }
                addView(video, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                addView(subtitles, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                live.attach(video, subtitles)
            }
        },
        modifier = placement,
        onRelease = { layout -> (layout.getChildAt(0) as? SurfaceView)?.let(live::detach) },
    )
}

/**
 * Puts the layer at [rect], in the root's pixels, whatever room its parent gives it: Zoom takes
 * more than the screen.
 */
private fun Modifier.placedAt(rect: Rect): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints.fixed(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)))
    layout(constraints.minWidth, constraints.minHeight) { placeable.place(rect.left.roundToInt(), rect.top.roundToInt()) }
}

/** Where the picture goes in [slot] for Picture Size [size], given its [aspect] (4.10 and 5.4.4). */
internal fun place(slot: Rect, aspect: Float?, size: PictureSize): Rect = when (size) {
    PictureSize.Fit -> fit(slot, aspect)
    PictureSize.Fill -> slot
    PictureSize.Zoom -> cover(slot, aspect)
    PictureSize.Wide -> fit(slot, WIDE)
    PictureSize.Standard -> fit(slot, STANDARD)
}

/** The largest rectangle with the picture's [aspect] that fits in [slot], centred. */
internal fun fit(slot: Rect, aspect: Float?): Rect {
    if (aspect == null || aspect <= 0f || slot.height <= 0f) return slot
    val slotAspect = slot.width / slot.height
    // Within a percent is close enough; most channels are 16:9 in a 16:9 slot.
    if (abs(slotAspect - aspect) / aspect < SAME_SHAPE) return slot
    return if (aspect > slotAspect) {
        val height = slot.width / aspect
        Rect(slot.left, slot.top + (slot.height - height) / 2, slot.right, slot.top + (slot.height + height) / 2)
    } else {
        val width = slot.height * aspect
        Rect(slot.left + (slot.width - width) / 2, slot.top, slot.left + (slot.width + width) / 2, slot.bottom)
    }
}

/** The smallest rectangle with the picture's [aspect] that covers [slot], centred: Zoom. */
internal fun cover(slot: Rect, aspect: Float?): Rect {
    if (aspect == null || aspect <= 0f || slot.height <= 0f) return slot
    val slotAspect = slot.width / slot.height
    if (abs(slotAspect - aspect) / aspect < SAME_SHAPE) return slot
    return if (aspect > slotAspect) {
        val width = slot.height * aspect
        Rect(slot.center.x - width / 2, slot.top, slot.center.x + width / 2, slot.bottom)
    } else {
        val height = slot.width / aspect
        Rect(slot.left, slot.center.y - height / 2, slot.right, slot.center.y + height / 2)
    }
}

/**
 * Subtitles the engine hands out (the Android player's cues, brief 5.4.4 and 5.4.7), over the part
 * of the picture the slot shows: white, with a soft shadow and no box, sized to the picture.
 */
@Composable
private fun LiveCues(live: LiveVideo, slot: Rect, size: PictureSize) {
    val cues by live.cues.collectAsState()
    if (cues.isEmpty()) return
    val aspect by live.aspectRatio.collectAsState()
    val area = place(slot, aspect, size).intersect(slot)
    if (area.width <= 0f || area.height <= 0f) return
    val density = LocalDensity.current
    val textPx = area.height * CUE_TEXT
    val style = TextStyle(
        color = PlasmaColors.white(1f),
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.Medium,
        fontSize = with(density) { textPx.toSp() },
        shadow = Shadow(PlasmaColors.black(CUE_SHADOW_ALPHA), Offset(0f, textPx * CUE_SHADOW_OFFSET), textPx * CUE_SHADOW_BLUR),
    )
    with(density) {
        Box(
            Modifier
                .offset { IntOffset(area.left.roundToInt(), area.top.roundToInt()) }
                .size(area.width.toDp(), area.height.toDp()),
        ) {
            for (cue in cues) {
                when (cue) {
                    is SubtitleCue.Text -> BasicText(
                        text = cue.text,
                        modifier = Modifier.cue(cue.place, lineHeight = textPx * CUE_LINE_HEIGHT),
                        style = style.copy(
                            textAlign = when (cue.align) {
                                CueAlign.Start -> TextAlign.Start
                                CueAlign.Center -> TextAlign.Center
                                CueAlign.End -> TextAlign.End
                            },
                        ),
                    )
                    is SubtitleCue.Picture -> {
                        val bitmap = remember(cue.bitmap) { cue.bitmap.asImageBitmap() }
                        val width = cue.place.width?.let { area.width * it } ?: bitmap.width.toFloat()
                        val height = cue.place.height?.let { area.height * it } ?: (width * bitmap.height / bitmap.width.coerceAtLeast(1))
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.cue(cue.place, lineHeight = textPx * CUE_LINE_HEIGHT).size(width.toDp(), height.toDp()),
                            contentScale = ContentScale.FillBounds,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Puts a subtitle where its [place] says in the area its parent gives, kept inside it, as Media3
 * places cues: by default at the bottom in the middle, a little up from the edge.
 */
private fun Modifier.cue(place: CuePlace, lineHeight: Float): Modifier = layout { measurable, constraints ->
    val areaWidth = constraints.maxWidth
    val areaHeight = constraints.maxHeight
    val widest = ((place.width ?: CUE_WIDTH) * areaWidth).roundToInt().coerceIn(1, areaWidth)
    val placeable = measurable.measure(Constraints(maxWidth = widest, maxHeight = areaHeight))
    val x = place.x?.let { along(it * areaWidth, place.xAnchor, placeable.width) } ?: ((areaWidth - placeable.width) / 2f)
    val row = place.row
    val y = when {
        place.y != null -> along(place.y * areaHeight, place.yAnchor, placeable.height)
        row != null && row >= 0 -> row * lineHeight
        row != null -> areaHeight + (row + 1) * lineHeight - placeable.height
        else -> areaHeight * (1f - CUE_BOTTOM) - placeable.height
    }
    layout(areaWidth, areaHeight) {
        placeable.place(
            x.roundToInt().coerceIn(0, (areaWidth - placeable.width).coerceAtLeast(0)),
            y.roundToInt().coerceIn(0, (areaHeight - placeable.height).coerceAtLeast(0)),
        )
    }
}

/** Where a box [length] long starts, when its [anchor] edge or middle is at [at]. */
private fun along(at: Float, anchor: CueAnchor, length: Int): Float = when (anchor) {
    CueAnchor.Start -> at
    CueAnchor.Middle -> at - length / 2f
    CueAnchor.End -> at - length
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

// Subtitles drawn over the picture (5.4.7): text a twentieth of the picture's height, as Media3
// sets it; at most nine tenths of its width; 8 % up from the bottom.
private const val CUE_TEXT = 0.0533f
private const val CUE_LINE_HEIGHT = 1.25f
private const val CUE_WIDTH = 0.9f
private const val CUE_BOTTOM = 0.08f
private const val CUE_SHADOW_ALPHA = 0.8f
private const val CUE_SHADOW_OFFSET = 0.04f
private const val CUE_SHADOW_BLUR = 0.15f

/** How long a screen without a video slot waits before live video stops. */
private const val STOP_AFTER_MS = 1_500L

/** Picture Size's 16:9 and 4:3. */
private const val WIDE = 16f / 9f
private const val STANDARD = 4f / 3f

/** Shapes within a percent of each other are the same: most channels are 16:9 in a 16:9 slot. */
private const val SAME_SHAPE = 0.01f
private const val PARKED_PX = 16
private const val BACKDROP_DIM = 0.38f
