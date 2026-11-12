// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How a focusable thing shows focus (brief 2.5). Focus is always lift, shadow and/or a white fill,
 * never a coloured ring.
 */
enum class FocusStyle(
    internal val scale: Float,
    internal val fillsWhite: Boolean,
    internal val shadow: Dp,
    internal val glint: Boolean,
) {
    /** Posters, thumbnails, app banners, dock tiles: scale 1.08, big shadow, specular glint. */
    Tile(1.08f, false, 13.dp, true),

    /** Avatars and an app being moved: scale 1.12. */
    Avatar(1.12f, false, 13.dp, true),

    /** List items, settings rows, catch-up rows, sheet rows: white fill, scale 1.02. */
    Row(1.02f, true, 10.dp, false),

    /** Wide input fields: white fill, scale 1.015. */
    Field(1.015f, true, 10.dp, false),

    /** Buttons: white fill, scale 1.05. */
    Button(1.05f, true, 9.dp, false),

    /** Tabs and segments: a white pill, no scale. */
    Pill(1f, true, 0.dp, false),

    /** Guide cells: white fill, no scale, so the grid stays aligned. */
    Cell(1f, true, 0.dp, false),

    /** Keyboard keys: white fill, a slight lift. */
    Key(1.1f, true, 6.dp, false),

    /** No built-in treatment: the content shows focus itself through [LocalFocused] (the Home hero). */
    Plain(1f, false, 0.dp, false),
}

/** The keys that mean OK: the D-pad's center, Enter, the keypad's Enter and a gamepad's A. */
val SelectKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA)

/**
 * Makes a row or column of focusable items one stop for the D-pad: moving into it lands on the
 * item that had focus last, or the first time on [fallback] (the selected tab, the primary button)
 * rather than on whichever item happens to be nearest.
 *
 * `focusRestorer` alone only works on something that's already a focus group, as lazy lists and
 * scrolling containers are; this adds the group for plain `Row`s and `Column`s.
 */
fun Modifier.focusRestoringGroup(fallback: FocusRequester = FocusRequester.Default): Modifier =
    focusRestorer(fallback).focusGroup()

/**
 * For a keyboard row's first and last keys: Left goes round to [left] (the row's last key), and
 * Right to [right] (its first), where the row would otherwise stop (#144). A held key stops at the
 * end of the row, and the next press goes round, so holding a key to reach the end doesn't spin
 * past it. Either can be null, to leave that way as it is.
 */
fun Modifier.goesRound(left: FocusRequester? = null, right: FocusRequester? = null): Modifier =
    if (left == null && right == null) {
        this
    } else {
        onKeyEvent { event ->
            val to = when (event.key) {
                Key.DirectionLeft -> left
                Key.DirectionRight -> right
                else -> null
            } ?: return@onKeyEvent false
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) to.requestFocus()
            true
        }
    }

/**
 * Draws [PlasmaSurface]s as if they had focus, so a preview can show the focused look beside the
 * plain one, as the interface kit does (board 26).
 */
internal val LocalFocusPreview = staticCompositionLocalOf { false }

/**
 * Draws the [PlasmaSurface]s in [content] as if they had focus, for a picture of the interface
 * rather than a part of it: first-time setup's preview of the look (#85). Keep focus out of the
 * picture with [focusFree].
 */
@Composable
fun ShownFocused(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFocusPreview provides true, content = content)
}

/**
 * Keeps focus out of everything in it, so the D-pad passes it by: a picture of the interface, such
 * as first-time setup's preview of the look (#85), made of the same focusable parts.
 */
fun Modifier.focusFree(): Modifier = focusProperties { onEnter = { cancelFocusChange() } }.focusGroup()

/** Remembers an OK press between key down and key up, so a recomposition can't lose it. */
class PressState {
    internal var pressed = false
    internal var longFired = false
}

/**
 * OK (D-pad centre or Enter) clicks on key up; holding OK long-clicks once; Menu also long-clicks,
 * because Menu and long-press OK open the same options (brief 3.6). Place it before `focusable()`.
 */
fun Modifier.plasmaClickable(
    state: PressState,
    enabled: Boolean = true,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    /**
     * Long-Press OK (Settings › Remote & Buttons): holding OK is [onLongClick]; off, a held OK is
     * a click when it's let go. Menu is [onLongClick] either way.
     */
    longPressOk: Boolean = true,
): Modifier {
    if (!enabled || (onClick == null && onLongClick == null)) return this
    return this.onKeyEvent { event ->
        val native = event.nativeKeyEvent
        when {
            event.key in SelectKeys && event.type == KeyEventType.KeyDown -> {
                if (native.repeatCount == 0) {
                    state.pressed = true
                    state.longFired = false
                } else if (longPressOk && onLongClick != null && !state.longFired && state.pressed) {
                    state.longFired = true
                    onLongClick()
                }
                true
            }
            event.key in SelectKeys && event.type == KeyEventType.KeyUp -> {
                val click = state.pressed && !state.longFired
                state.pressed = false
                if (click) onClick?.invoke()
                true
            }
            event.key == Key.Menu && onLongClick != null -> {
                if (event.type == KeyEventType.KeyUp) onLongClick()
                true
            }
            else -> false
        }
    }.semantics {
        role = Role.Button
        if (onClick != null) onClick { onClick(); true }
        if (onLongClick != null) onLongClick { onLongClick(); true }
    }
}

/**
 * How wide the frame of fill is that's painted under content that covers a place: every pixel the
 * place's outline cuts through lies within a pixel and a half of it, and this takes those in with
 * as much again to spare.
 */
private const val FRAME_PX = 3f

/**
 * The corner radius of a place with this [shape] whose fill can be painted as a frame along its
 * outline: a rounded rectangle with one round radius at every corner, larger than the frame is
 * wide. Null for any other outline, or a place too small for a frame to leave anything out: its
 * fill is painted whole.
 */
private fun DrawScope.framedCorner(shape: Shape): Float? {
    val outline = (shape.createOutline(size, layoutDirection, this) as? Outline.Rounded)?.roundRect ?: return null
    val corner = outline.topLeftCornerRadius
    val oneRadius = corner.x == corner.y &&
        outline.topRightCornerRadius == corner &&
        outline.bottomRightCornerRadius == corner &&
        outline.bottomLeftCornerRadius == corner
    return if (oneRadius && corner.x > FRAME_PX && 2f * FRAME_PX < minOf(size.width, size.height)) corner.x else null
}

/**
 * The one focusable building block. It owns focus, OK and long-press handling and the focus
 * treatment for its [style], and tells its content through [LocalFocused] and [LocalContentColor].
 *
 * @param color fill when neither focused nor selected.
 * @param selected marks the current item when focus is elsewhere (current group, channel, day, tab).
 * @param selectedColor fill for [selected]; rows use `.08` white, tabs `.12`.
 * @param focusedScale overrides the style's scale (Reduce Motion caps it at 1.04).
 * @param description what TalkBack says for it, in place of the words in it, which TalkBack then
 *   passes by, so nothing is read twice (#128). Tests still find it by those words. Without it,
 *   TalkBack reads the words in it, in order.
 * @param covered whether what's in it now covers all of it with nothing showing through, as a
 *   poster does once it's in ([ca.gureet.plasma.core.design.component.PictureCover]); read as it's
 *   drawn. At its own size, its fill is then painted as a frame along its rounded outline only,
 *   where the outline cuts through pixels and the fill shows in them: nothing of the rest would
 *   be seen, and the GPU's time goes by how much is painted (#127). The frame is a rounded
 *   rectangle's stroke, painted under the clipped layer and not in it: a rounded rectangle is what
 *   a whole fill under a rounded clip is drawn as, so its edge is the same one, and the frames of
 *   a screen's tiles are drawn together as their fills were. Grown or lifted by focus, or shown
 *   faint because it's off, a surface is one of many no longer, and its fill is painted whole.
 */
@Composable
fun PlasmaSurface(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    style: FocusStyle = FocusStyle.Row,
    shape: Shape = PlasmaShapes.Row,
    color: Color = Color.Transparent,
    selected: Boolean = false,
    selectedColor: Color = if (style == FocusStyle.Pill) PlasmaColors.Selected else PlasmaColors.SelectedSoft,
    focusedColor: Color = PlasmaColors.Text,
    focusedScale: Float = style.scale,
    pivot: TransformOrigin = TransformOrigin.Center,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    description: String? = null,
    covered: (() -> Boolean)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val focused = hasFocus || LocalFocusPreview.current
    val press = remember { PressState() }
    val reduceMotion = LocalReduceMotion.current
    val longPressOk = LocalKeyChoices.current.longPressOk
    // In an overlay on its way out, it lets focus and keys go at once, and keeps the look it had
    // (and tells no one it lost focus) while it fades.
    val leaving = LocalLeaving.current
    val gone = rememberUpdatedState(leaving)
    val targetScale = when {
        !focused -> 1f
        reduceMotion -> minOf(focusedScale, PlasmaMotion.REDUCED_SCALE)
        else -> focusedScale
    }
    val scale by animateFloatAsState(targetScale, PlasmaMotion.focus(), label = "focusScale")
    val lift by animateFloatAsState(if (focused) 1f else 0f, PlasmaMotion.focus(), label = "focusLift")
    val fill by animateColorAsState(
        targetValue = when {
            focused && style.fillsWhite -> focusedColor
            selected -> selectedColor
            else -> color
        },
        animationSpec = PlasmaMotion.colour(),
        label = "focusFill",
    )
    val contentColor = if (focused && style.fillsWhite) PlasmaColors.OnFocus else LocalContentColor.current
    val shadow = style.shadow
    // Under a cover and at rest, the fill is a frame (#127): its corner's radius then, or null for
    // the whole fill. Read where each is drawn, so the frame goes as the whole fill comes.
    val framed: DrawScope.() -> Float? = {
        if (covered != null && enabled && covered() && scale == 1f && lift == 0f && fill.alpha > 0f) framedCorner(shape) else null
    }

    Box(
        modifier = modifier
            .onFocusChanged {
                if (!gone.value && hasFocus != it.isFocused) {
                    hasFocus = it.isFocused
                    onFocusChanged?.invoke(it.isFocused)
                }
            }
            .plasmaClickable(press, enabled && !leaving, onClick, onLongClick, longPressOk)
            // One node for TalkBack: the surface reads as its label, like a button. Only the current
            // or chosen one is marked, so TalkBack says "Selected" on it and nothing on the rest,
            // rather than "Not selected" on every button (#128).
            .semantics(mergeDescendants = true) {
                if (selected) this.selected = true
                if (description != null) contentDescription = description
            }
            .focusable(enabled && !leaving)
            .then(
                if (covered == null) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        val corner = framed()
                        if (corner != null) {
                            // The stroke's outer edge is the outline's own: the rounded rectangle half
                            // the frame's width in, stroked that far out again.
                            val half = FRAME_PX / 2f
                            drawRoundRect(
                                color = fill,
                                topLeft = Offset(half, half),
                                size = Size(size.width - FRAME_PX, size.height - FRAME_PX),
                                cornerRadius = CornerRadius(corner - half),
                                style = Stroke(FRAME_PX),
                            )
                        }
                    }
                },
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = pivot
                shadowElevation = shadow.toPx() * lift
                this.shape = shape
                clip = true
                ambientShadowColor = Color.Black.copy(alpha = 0.5f)
                spotShadowColor = Color.Black.copy(alpha = 0.7f)
                alpha = if (enabled) 1f else 0.45f
            }
            .drawWithContent {
                // Whole, unless it's the frame under this layer.
                if (fill.alpha > 0f && framed() == null) drawRect(fill)
                drawContent()
                if (style.glint && lift > 0f) {
                    drawRect(
                        brush = Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.2f * lift),
                            0.42f to Color.Transparent,
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                    )
                }
            },
        contentAlignment = contentAlignment,
    ) {
        CompositionLocalProvider(
            LocalFocused provides focused,
            LocalContentColor provides contentColor,
            LocalSaidAbove provides (description != null || LocalSaidAbove.current),
        ) {
            content()
        }
    }
}
