// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.profiles

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.component.ClockText
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.focusRestoringGroup
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import kotlin.math.PI
import ca.gureet.plasma.core.design.R as DesignR

/** A profile on Who's Watching. */
@Immutable
data class ProfileItem(
    val id: String,
    val name: String,
    /** The profile's picture; without one, a figure on graphite (a guest). */
    val avatar: String?,
    val pin: Boolean = false,
    /** A kids profile's age rating. */
    val maxAge: Int? = null,
    /** A guest keeps no history. */
    val guest: Boolean = false,
    /** The channel this profile watched last. */
    val lastWatched: String? = null,
)

@Immutable
data class ProfilesUiState(val profiles: List<ProfileItem>, val currentId: String)

class ProfilesActions(
    /** A PIN-protected profile goes to the PIN screen first (4.5). */
    val onPick: (ProfileItem) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onManage: () -> Unit = {},
)

/**
 * Who's Watching (board 05, brief 4.5): the profiles as 200 px circles with the current one
 * focused, Add Profile, and Manage Profiles below. It opens on wake and from the avatar in the top
 * bar.
 */
@Composable
fun ProfilesScreen(state: ProfilesUiState, actions: ProfilesActions, modifier: Modifier = Modifier) {
    val current = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        current.requestFocus()
    }
    Box(modifier.fillMaxSize().glow()) {
        Row(Modifier.offset(96.px, 46.px).size(1728.px, 56.px), verticalAlignment = Alignment.CenterVertically) {
            PlasmaText(stringResource(DesignR.string.wordmark), PlasmaType.Wordmark)
            Spacer(Modifier.weight(1f))
            ClockText()
        }
        PlasmaText(
            stringResource(R.string.whos_watching),
            plasmaStyle(56f, 600, trackingPx = -1.3f),
            Modifier.offset(0.px, 286.px).width(1920.px),
            align = TextAlign.Center,
        )
        Row(
            Modifier.offset(316.px, 420.px).width(1288.px).focusRestoringGroup(current),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (profile in state.profiles) {
                ProfileTile(
                    profile,
                    onClick = { actions.onPick(profile) },
                    modifier = if (profile.id == state.currentId) Modifier.focusRequester(current) else Modifier,
                )
            }
            AddProfileTile(onClick = actions.onAdd)
        }
        Column(
            Modifier.offset(0.px, 812.px).width(1920.px),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.px),
        ) {
            PlasmaButton(
                stringResource(R.string.manage_profiles),
                onClick = actions.onManage,
                height = 60.px,
                startPadding = 30.px,
                endPadding = 30.px,
                unfocusedColor = PlasmaColors.white(0.1f),
            )
            PlasmaText(stringResource(R.string.profiles_explained), plasmaStyle(21f, 500), color = PlasmaColors.Text3)
        }
    }
}

/** The soft light behind the profiles: an ellipse 1200 × 700 px, a little above centre. */
private fun Modifier.glow(): Modifier = background(PlasmaColors.Bg).drawBehind {
    val center = Offset(size.width / 2, size.height * 0.42f)
    val radiusY = 700.px.toPx()
    val radiusX = 1200.px.toPx()
    scale(scaleX = radiusX / radiusY, scaleY = 1f, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(0f to PlasmaColors.Glow, 0.7f to PlasmaColors.Bg, center = center, radius = radiusY),
            radius = radiusY,
            center = center,
        )
    }
}

/** The name grows and drops below the lifted avatar when it has focus. */
@Composable
private fun TileCaption(focused: Boolean, content: @Composable () -> Unit) {
    val shift by animateDpAsState(if (focused) 12.px else 0.px, PlasmaMotion.focus(), label = "profileName")
    Column(
        Modifier
            .padding(top = 28.px)
            .offset { IntOffset(0, shift.roundToPx()) }
            // Names and meta lines may run wider than the avatar, centred under it.
            .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.px),
    ) {
        content()
    }
}

@Composable
private fun ProfileTile(profile: ProfileItem, onClick: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val description = if (profile.pin) stringResource(R.string.profile_pin_protected, profile.name) else profile.name
    Column(Modifier.width(200.px), horizontalAlignment = Alignment.CenterHorizontally) {
        PlasmaSurface(
            onClick = onClick,
            modifier = modifier
                .size(200.px)
                .alpha(if (focused) 1f else 0.8f)
                .semantics { contentDescription = description },
            style = FocusStyle.Avatar,
            shape = CircleShape,
            color = PlasmaColors.Surface2,
            onFocusChanged = { focused = it },
            contentAlignment = Alignment.Center,
        ) {
            if (profile.avatar != null) {
                PlasmaImage(profile.avatar, Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(PlasmaColors.GraphiteFocusedTop, PlasmaColors.Surface3))),
                )
                PlasmaIcon(PlasmaIcons.Person, 86.px, tint = PlasmaColors.TextSoft)
            }
        }
        TileCaption(focused) {
            PlasmaText(
                profile.name,
                plasmaStyle(if (focused) 28f else 27f, if (focused) 600 else 500),
                color = if (focused) PlasmaColors.Text else PlasmaColors.TextSoft,
            )
            val metaColor = if (focused) PlasmaColors.Text2 else PlasmaColors.Text3
            val meta = plasmaStyle(20f, 500)
            when {
                profile.pin -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.px)) {
                    PlasmaIcon(PlasmaIcons.Lock, 16.px, tint = metaColor, stroke = 2.4f)
                    PlasmaText(stringResource(R.string.pin), meta, color = metaColor)
                }
                profile.maxAge != null -> PlasmaText(pluralStringResource(R.plurals.ages_and_under, profile.maxAge, profile.maxAge), meta, color = metaColor)
                profile.guest -> PlasmaText(stringResource(R.string.no_history), meta, color = metaColor)
                profile.lastWatched != null -> PlasmaText(stringResource(R.string.last_watched, profile.lastWatched), meta, color = metaColor)
            }
        }
    }
}

/** A dashed circle with "+": 3 px dashes at `.22` white, brighter with focus. */
@Composable
private fun AddProfileTile(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val label = stringResource(R.string.add_profile)
    Column(Modifier.width(200.px), horizontalAlignment = Alignment.CenterHorizontally) {
        PlasmaSurface(
            onClick = onClick,
            modifier = Modifier.size(200.px).semantics { contentDescription = label },
            style = FocusStyle.Avatar,
            shape = CircleShape,
            onFocusChanged = { focused = it },
            contentAlignment = Alignment.Center,
        ) {
            val isFocused = LocalFocused.current
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (isFocused) PlasmaColors.white(0.08f) else PlasmaColors.white(0f), CircleShape)
                    .drawBehind {
                        val width = 3.px.toPx()
                        val radius = (size.minDimension - width) / 2
                        // The board's dashes: 74 around the circle, a little longer than their gaps.
                        val period = (2 * PI * radius).toFloat() / 74
                        drawCircle(
                            color = PlasmaColors.white(if (isFocused) 0.5f else 0.22f),
                            radius = radius,
                            style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(period * 0.56f, period * 0.44f))),
                        )
                    },
            )
            PlasmaIcon(PlasmaIcons.Plus, 60.px, tint = if (isFocused) PlasmaColors.Text else PlasmaColors.Text2, stroke = 1.6f)
        }
        TileCaption(focused) {
            PlasmaText(
                label,
                plasmaStyle(if (focused) 28f else 27f, if (focused) 600 else 500),
                color = if (focused) PlasmaColors.Text else PlasmaColors.Text2,
            )
        }
    }
}
