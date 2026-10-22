// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.absolute
import ca.gureet.plasma.core.design.boxShadow
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.focusRestoringGroup
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.design.ring
import ca.gureet.plasma.player.VideoFrame

/** How Multiview arranges its tiles (brief 4.12). */
enum class MultiviewLayout { Grid, FocusPlusThree, PictureInPicture }

/** A channel in a Multiview tile. */
@Immutable
data class MultiviewTile(
    val channelId: String,
    val channelName: String,
    val logo: String?,
    val title: String?,
    val frame: VideoFrame,
    val subtitles: Boolean = false,
)

@Immutable
data class MultiviewUiState(
    val tiles: List<MultiviewTile>,
    /** The channels Change Channel steps through. */
    val more: List<MultiviewTile> = emptyList(),
    val layout: MultiviewLayout = MultiviewLayout.Grid,
)

class MultiviewActions(
    /** OK on a tile, or Full Screen: that channel in the main player. */
    val onFullScreen: (String) -> Unit = {},
)

/** Where a tile sits in board pixels, or null when the layout leaves it out. */
private data class TileRect(val x: Float, val y: Float, val width: Float, val height: Float)

private const val AREA_X = 116f
private const val AREA_Y = 20f
private const val AREA_W = 1688f
private const val AREA_H = 944f
private const val GAP = 8f

private fun tileRect(layout: MultiviewLayout, index: Int): TileRect? = when (layout) {
    // 2 × 2, 840 × 472 each (board 12).
    MultiviewLayout.Grid -> {
        val w = (AREA_W - GAP) / 2
        val h = (AREA_H - GAP) / 2
        if (index < 4) TileRect(AREA_X + (index % 2) * (w + GAP), AREA_Y + (index / 2) * (h + GAP), w, h) else null
    }
    // One large tile and three 16:9 tiles stacked on its right.
    MultiviewLayout.FocusPlusThree -> {
        val smallH = (AREA_H - 2 * GAP) / 3
        val smallW = smallH * 16 / 9
        when (index) {
            0 -> TileRect(AREA_X, AREA_Y, AREA_W - GAP - smallW, AREA_H)
            1, 2, 3 -> TileRect(AREA_X + AREA_W - smallW, AREA_Y + (index - 1) * (smallH + GAP), smallW, smallH)
            else -> null
        }
    }
    // The first tile fills the area, the second sits in its corner.
    MultiviewLayout.PictureInPicture -> when (index) {
        0 -> TileRect(AREA_X, AREA_Y, AREA_W, AREA_H)
        1 -> TileRect(AREA_X + AREA_W - 480f - 24f, AREA_Y + AREA_H - 270f - 24f, 480f, 270f)
        else -> null
    }
}

/**
 * Multiview (board 12, brief 4.12): up to four channels at once. The focused tile has the sound,
 * shown by a white ring and a Sound badge; the others are muted. OK goes full screen on a tile and
 * Menu offers Change Channel and Remove. The toolbar below picks the layout.
 */
@Composable
fun MultiviewScreen(state: MultiviewUiState, actions: MultiviewActions, modifier: Modifier = Modifier) {
    var tiles by remember(state) { mutableStateOf(state.tiles) }
    var layout by rememberSaveable { mutableStateOf(state.layout) }
    var sound by remember { mutableIntStateOf(0) }
    var sheetOpen by remember { mutableStateOf(false) }
    val tileFocus = remember { List(4) { FocusRequester() } }
    val toolbarFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        tileFocus[0].requestFocus()
    }

    fun changeChannel(index: Int) {
        val shown = tiles.map { it.channelId }.toSet()
        val pool = state.more.filter { it.channelId !in shown }
        if (pool.isEmpty() || index !in tiles.indices) return
        // Step to the channel after this one in the list, as CH + would.
        val after = state.more.indexOfFirst { it.channelId == tiles[index].channelId }
        val next = state.more.drop(after + 1).firstOrNull { it.channelId !in shown } ?: pool.first()
        tiles = tiles.toMutableList().also { it[index] = next }
    }

    Box(modifier.fillMaxSize().background(PlasmaColors.Black)) {
        tiles.forEachIndexed { i, tile ->
            val rect = tileRect(layout, i) ?: return@forEachIndexed
            key(tile.channelId) {
                MultiviewTileView(
                    tile = tile,
                    hasSound = i == sound,
                    onClick = { actions.onFullScreen(tile.channelId) },
                    onLongClick = { sheetOpen = true },
                    onFocused = { sound = i },
                    // The corner picture draws over the big one.
                    modifier = Modifier
                        .absolute(rect.x.px, rect.y.px, rect.width.px, rect.height.px)
                        .zIndex(if (layout == MultiviewLayout.PictureInPicture && i == 1) 1f else 0f)
                        .focusRequester(tileFocus[i]),
                )
            }
        }

        Toolbar(
            layout = layout,
            onLayout = { picked ->
                layout = picked
                if (tileRect(picked, sound) == null) sound = 0
            },
            onChangeChannel = { changeChannel(sound) },
            onFullScreen = { tiles.getOrNull(sound)?.let { actions.onFullScreen(it.channelId) } },
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 990.px).focusRequester(toolbarFocus),
        )

        SheetHost(visible = sheetOpen, returnFocus = tileFocus[sound.coerceIn(0, 3)]) {
            val tile = tiles.getOrNull(sound) ?: return@SheetHost
            OptionsSheet(
                title = tile.title ?: tile.channelName,
                subtitle = tile.channelName,
                onDismiss = { sheetOpen = false },
                options = listOfNotNull(
                    SheetOption(stringResource(R.string.multiview_change_channel), onClick = { changeChannel(sound) }, icon = PlasmaIcons.ChangeChannel),
                    if (tiles.size > 1) {
                        SheetOption(stringResource(R.string.multiview_remove), onClick = {
                            tiles = tiles.filterIndexed { i, _ -> i != sound }
                            sound = 0
                        })
                    } else {
                        null
                    },
                ),
            )
        }
    }
}

@Composable
private fun MultiviewTileView(
    tile: MultiviewTile,
    hasSound: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val description = listOfNotNull(tile.channelName, tile.title).joinToString(": ")
    Box(modifier.then(if (focused) Modifier.ring(4.px, PlasmaColors.Text, 12.px) else Modifier)) {
        PlasmaSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxSize().semantics { contentDescription = description },
            style = FocusStyle.Plain,
            shape = PlasmaShapes.Poster,
            color = PlasmaColors.VideoTile,
            onFocusChanged = {
                focused = it
                if (it) onFocused()
            },
        ) {
            PlasmaImage(tile.frame.still, Modifier.fillMaxSize(), alignment = tile.frame.alignment, placeholder = PlasmaColors.VideoTile)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(140.px)
                    .background(Brush.verticalGradient(listOf(PlasmaColors.black(0f), PlasmaColors.black(0.78f)))),
            )
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = 22.px, bottom = 22.px),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.px),
            ) {
                LogoImage(tile.logo, tile.channelName, LogoSize.Multiview)
                if (tile.title != null) PlasmaText(tile.title, plasmaStyle(24f, 600), color = PlasmaColors.Text)
                if (tile.subtitles) PlasmaText(stringResource(R.string.subtitles_on), plasmaStyle(20f, 500), color = PlasmaColors.TextSoft)
            }
            if (hasSound) {
                SoundBadge(Modifier.align(Alignment.TopEnd).padding(top = 16.px, end = 18.px))
            } else {
                val muted = stringResource(R.string.muted)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.px, end = 18.px)
                        .size(34.px)
                        .background(PlasmaColors.black(0.5f), RoundedCornerShape(9.px))
                        .semantics { contentDescription = muted },
                    contentAlignment = Alignment.Center,
                ) {
                    PlasmaIcon(PlasmaIcons.Muted, 18.px, tint = PlasmaColors.TextSoft)
                }
            }
        }
    }
}

/** "🔊 Sound" on the tile you're hearing. */
@Composable
private fun SoundBadge(modifier: Modifier) {
    Row(
        modifier.height(34.px).background(PlasmaColors.Text, RoundedCornerShape(9.px)).padding(horizontal = 13.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.px),
    ) {
        PlasmaIcon(PlasmaIcons.Volume, 18.px, tint = PlasmaColors.OnFocus, stroke = 2.4f)
        PlasmaText(stringResource(R.string.sound), plasmaStyle(18f, 600), color = PlasmaColors.OnFocus)
    }
}

/**
 * Grid · Focus + 3 · Picture in Picture | Change Channel · Full Screen, in a glass bar. The current
 * layout is white while focus is on the tiles; once the bar has focus, focus is white and the
 * current layout is marked like a tab.
 */
@Composable
private fun Toolbar(
    layout: MultiviewLayout,
    onLayout: (MultiviewLayout) -> Unit,
    onChangeChannel: () -> Unit,
    onFullScreen: () -> Unit,
    modifier: Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val selected = remember { FocusRequester() }
    Row(
        modifier
            .boxShadow(PlasmaColors.black(0.6f), offsetY = 20.px, blur = 50.px, cornerRadius = 18.px)
            .background(PlasmaColors.ToolbarFill, RoundedCornerShape(18.px))
            .padding(8.px)
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusRestoringGroup(selected),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.px),
    ) {
        val layouts = listOf(
            Triple(MultiviewLayout.Grid, PlasmaIcons.Multiview, R.string.multiview_grid),
            Triple(MultiviewLayout.FocusPlusThree, PlasmaIcons.FocusPlusThree, R.string.multiview_focus_three),
            Triple(MultiviewLayout.PictureInPicture, PlasmaIcons.PictureInPicture, R.string.multiview_pip),
        )
        for ((value, icon, label) in layouts) {
            ToolbarButton(
                icon = icon,
                label = stringResource(label),
                onClick = { onLayout(value) },
                current = layout == value,
                barHasFocus = hasFocus,
                modifier = if (layout == value) Modifier.focusRequester(selected) else Modifier,
            )
        }
        Box(Modifier.padding(horizontal = 6.px).size(1.px, 30.px).background(PlasmaColors.white(0.14f)))
        ToolbarButton(PlasmaIcons.ChangeChannel, stringResource(R.string.multiview_change_channel), onChangeChannel, current = false, barHasFocus = hasFocus)
        ToolbarButton(PlasmaIcons.FullScreen, stringResource(R.string.multiview_full_screen), onFullScreen, current = false, barHasFocus = hasFocus)
    }
}

@Composable
private fun ToolbarButton(
    icon: PlasmaIcons,
    label: String,
    onClick: () -> Unit,
    current: Boolean,
    barHasFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.height(56.px),
        style = FocusStyle.Pill,
        shape = PlasmaShapes.Tab,
        selected = current,
        selectedColor = if (barHasFocus) PlasmaColors.Selected else PlasmaColors.Text,
        contentAlignment = Alignment.Center,
    ) {
        val white = LocalFocused.current || (current && !barHasFocus)
        val color = when {
            white -> PlasmaColors.OnFocus
            current -> PlasmaColors.Text
            else -> PlasmaColors.TextSoft2
        }
        Row(
            Modifier.padding(horizontal = 20.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.px),
        ) {
            PlasmaIcon(icon, 22.px, tint = color, stroke = 2.2f)
            PlasmaText(label, plasmaStyle(21f, if (white || current) 600 else 500), color = color)
        }
    }
}
