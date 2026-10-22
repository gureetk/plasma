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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
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

private val SelectKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA)

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
): Modifier {
    if (!enabled || (onClick == null && onLongClick == null)) return this
    return this.onKeyEvent { event ->
        val native = event.nativeKeyEvent
        when {
            event.key in SelectKeys && event.type == KeyEventType.KeyDown -> {
                if (native.repeatCount == 0) {
                    state.pressed = true
                    state.longFired = false
                } else if (onLongClick != null && !state.longFired && state.pressed) {
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
 * The one focusable building block. It owns focus, OK and long-press handling and the focus
 * treatment for its [style], and tells its content through [LocalFocused] and [LocalContentColor].
 *
 * @param color fill when neither focused nor selected.
 * @param selected marks the current item when focus is elsewhere (current group, channel, day, tab).
 * @param selectedColor fill for [selected]; rows use `.08` white, tabs `.12`.
 * @param focusedScale overrides the style's scale (Reduce Motion caps it at 1.04).
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
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val press = remember { PressState() }
    val reduceMotion = LocalReduceMotion.current
    val targetScale = when {
        !focused -> 1f
        reduceMotion -> minOf(focusedScale, 1.04f)
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

    Box(
        modifier = modifier
            .onFocusChanged {
                if (focused != it.isFocused) {
                    focused = it.isFocused
                    onFocusChanged?.invoke(it.isFocused)
                }
            }
            .plasmaClickable(press, enabled, onClick, onLongClick)
            // One node for TalkBack: the surface reads as its label, like a button.
            .semantics(mergeDescendants = true) { this.selected = selected }
            .focusable(enabled)
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
                if (fill.alpha > 0f) {
                    drawRect(fill)
                }
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
        ) {
            content()
        }
    }
}
