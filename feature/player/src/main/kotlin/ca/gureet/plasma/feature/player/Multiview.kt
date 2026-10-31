// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.absolute
import ca.gureet.plasma.core.design.boxShadow
import ca.gureet.plasma.core.design.component.CountedItem
import ca.gureet.plasma.core.design.component.CountedList
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.component.LogoPlate
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.PlasmaColumn
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.component.SheetPanel
import ca.gureet.plasma.core.design.focusRestoringGroup
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.design.ring
import ca.gureet.plasma.player.VideoFrame

/**
 * How Multiview arranges its tiles (brief 4.12), and how many it shows. Beside board 12's Grid,
 * Focus + 3 and Picture in Picture, the owner asked for 2 and 3 tiles (28 October): 2 Tiles,
 * 3 Tiles and 4 Tiles (the board's Grid) are one grid, side by side, two over one, and 2 × 2.
 * Settings keeps the last one by its place here.
 */
enum class MultiviewLayout(val tiles: Int) {
    TwoTiles(2),
    ThreeTiles(3),
    FourTiles(4),
    FocusPlusThree(4),
    PictureInPicture(2),
    ;

    /** The grid's tiles sit by how many there are, not by the layout. */
    val isGrid: Boolean get() = this == TwoTiles || this == ThreeTiles || this == FourTiles

    /** One tile large and the rest beside it: the tile heard goes large. */
    val focusesOne: Boolean get() = this == FocusPlusThree || this == PictureInPicture

    companion object {
        /** The grid with [count] tiles: two at the least, four at the most. */
        fun grid(count: Int): MultiviewLayout = when {
            count >= 4 -> FourTiles
            count == 3 -> ThreeTiles
            else -> TwoTiles
        }

        /** [layout], or the grid of as many tiles as the TV's decoders take when that's fewer (4.12). */
        fun fitting(layout: MultiviewLayout, maxTiles: Int): MultiviewLayout = if (layout.tiles <= maxTiles) layout else grid(maxTiles)
    }
}

/** What a tile shows over its picture while it has none to show (brief 5.8). */
sealed interface TileCard {
    /** The stream couldn't play: why, as the player's error card says it (5.4.9). */
    data class Problem(val problem: PlayerProblem) : TileCard

    /** Every connection the account allows is in use: "Your subscription allows 2 streams at a time." */
    data class Limit(val streams: Int) : TileCard

    /** A second 4K channel: Multiview shows one at a time (5.8). */
    data object OneUhd : TileCard
}

/** A channel in a Multiview tile, or in Change Channel's lists. */
@Immutable
data class MultiviewTile(
    val channelId: String,
    val channelName: String,
    val logo: String?,
    val title: String?,
    val frame: VideoFrame,
    val subtitles: Boolean = false,
    /** Its number in its list, for Change Channel. */
    val number: String = "",
    /** Over its picture instead of it; null while it plays, or opens. */
    val card: TileCard? = null,
)

/** One of Change Channel's lists: Favorites, a group, All Channels. */
@Immutable
data class MultiviewList(val id: String, val name: String, val channels: List<MultiviewTile>, val smart: Boolean = false)

@Immutable
data class MultiviewUiState(
    val tiles: List<MultiviewTile>,
    /** The lists Change Channel picks from (brief 4.12). */
    val lists: List<MultiviewList> = emptyList(),
    val layout: MultiviewLayout = MultiviewLayout.FourTiles,
    /** As many tiles as the TV's decoders take (5.8): layouts with more aren't offered. */
    val maxTiles: Int = 4,
    /** The list the tiles came from: a layout with more tiles adds the channels after theirs in it. */
    val listId: String? = null,
)

class MultiviewActions(
    /** OK on a tile, or Full Screen: that channel in the main player. */
    val onFullScreen: (String) -> Unit = {},
    /** The tiles' channels changed, by a layout, Change Channel or Remove: these, in order. */
    val onChannels: (List<String>) -> Unit = {},
    /** The tile with this channel has focus, and so the sound (4.12). */
    val onSound: (String) -> Unit = {},
    /** OK on a tile with a card: try its channel again. */
    val onRetry: (String) -> Unit = {},
    /** The layout picked, for Multiview to open in next time. */
    val onLayout: (MultiviewLayout) -> Unit = {},
)

/** Where a tile sits in board pixels, or null when the layout leaves it out. */
private data class TileRect(val x: Float, val y: Float, val width: Float, val height: Float)

private const val AREA_X = 116f
private const val AREA_Y = 20f
private const val AREA_W = 1688f
private const val AREA_H = 944f
private const val GAP = 8f

/** Where a tile left out of the layout waits, off the screen. */
private const val PARKED = 16f

private fun tileRect(layout: MultiviewLayout, index: Int, count: Int): TileRect? = when (layout) {
    // 840 × 472 tiles (board 12): two side by side across the middle, three as two over one, four
    // 2 × 2. One left, after Remove, fills the area.
    MultiviewLayout.TwoTiles, MultiviewLayout.ThreeTiles, MultiviewLayout.FourTiles -> {
        val w = (AREA_W - GAP) / 2
        val h = (AREA_H - GAP) / 2
        when {
            index >= layout.tiles -> null
            count == 1 -> TileRect(AREA_X, AREA_Y, AREA_W, AREA_H)
            count == 2 -> TileRect(AREA_X + index * (w + GAP), AREA_Y + (AREA_H - h) / 2, w, h)
            count == 3 && index == 2 -> TileRect(AREA_X + (AREA_W - w) / 2, AREA_Y + h + GAP, w, h)
            else -> TileRect(AREA_X + (index % 2) * (w + GAP), AREA_Y + (index / 2) * (h + GAP), w, h)
        }
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

/** The tiles once a layout's picked: the one heard, by its place, and the ones let go, to come back first. */
internal data class LayoutTiles(val tiles: List<MultiviewTile>, val heard: Int, val dropped: List<MultiviewTile>)

/**
 * The tiles for [layout]. With fewer tiles, the one [heard] stays, with the first of the others;
 * the rest go, and connect no more. With more, the ones let go before come back, then the
 * channels after the tiles' own in the list they came from ([listId]), round to its start, then
 * All Channels' (the last list). Focus + 3 and Picture in Picture put the tile heard first, large.
 */
internal fun tilesFor(
    layout: MultiviewLayout,
    tiles: List<MultiviewTile>,
    heard: Int,
    dropped: List<MultiviewTile>,
    lists: List<MultiviewList>,
    listId: String?,
): LayoutTiles {
    val heardId = (tiles.getOrNull(heard) ?: tiles.firstOrNull())?.channelId
    val keep = if (tiles.size > layout.tiles) {
        (listOfNotNull(heardId) + tiles.map { it.channelId }.filter { it != heardId }).take(layout.tiles).toSet()
    } else {
        tiles.mapTo(HashSet()) { it.channelId }
    }
    val result = tiles.filterTo(ArrayList()) { it.channelId in keep }
    val letGo = tiles.filter { it.channelId !in keep }
    val showing = result.mapTo(HashSet()) { it.channelId }

    fun after(list: MultiviewList?): List<MultiviewTile> {
        val channels = list?.channels.orEmpty()
        val last = result.asReversed().firstNotNullOfOrNull { tile -> channels.indexOfFirst { it.channelId == tile.channelId }.takeIf { it >= 0 } } ?: -1
        return channels.drop(last + 1) + channels.take(last + 1)
    }
    if (result.size < layout.tiles) {
        val candidates = dropped.asSequence() + after(lists.firstOrNull { it.id == listId }).asSequence() + after(lists.lastOrNull()).asSequence()
        for (candidate in candidates) {
            if (result.size >= layout.tiles) break
            if (showing.add(candidate.channelId)) result += candidate.copy(card = null)
        }
    }
    val ordered = if (layout.focusesOne && heardId != null) result.sortedBy { if (it.channelId == heardId) 0 else 1 } else result
    return LayoutTiles(
        tiles = ordered,
        heard = ordered.indexOfFirst { it.channelId == heardId }.coerceAtLeast(0),
        dropped = (letGo + dropped).filter { it.channelId !in showing }.distinctBy { it.channelId },
    )
}

/**
 * Multiview (board 12, brief 4.12): two to four channels at once. The focused tile has the sound,
 * shown by a white ring and a Sound badge; the others are muted. OK goes full screen on a tile and
 * Menu offers Change Channel and Remove; Change Channel picks from the lists, as Live TV has them.
 * The toolbar below picks the layout, and with it how many tiles there are ([tilesFor]).
 *
 * @param tileVideo draws a tile's live picture: its channel, and whether it lies over another
 *   tile, as Picture in Picture's small one does. Without it, the tile shows its still.
 */
@Composable
fun MultiviewScreen(
    state: MultiviewUiState,
    actions: MultiviewActions,
    modifier: Modifier = Modifier,
    tileVideo: (@Composable BoxScope.(channelId: String, onTop: Boolean) -> Unit)? = null,
) {
    var tiles by remember { mutableStateOf(state.tiles) }
    // The app's tiles, as they open, fail or change: what's changed here shows until it says so too.
    LaunchedEffect(state.tiles) { tiles = state.tiles }
    var layout by rememberSaveable {
        val fitting = MultiviewLayout.fitting(state.layout, state.maxTiles)
        // Fewer channels than the grid has room for: the grid of as many as there are.
        mutableStateOf(if (fitting.isGrid && state.tiles.size in 1 until fitting.tiles) MultiviewLayout.grid(state.tiles.size) else fitting)
    }
    var sound by remember { mutableIntStateOf(0) }
    var sheetOpen by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Int?>(null) }
    // Tiles a smaller layout let go, to come back first in a larger one.
    var dropped by remember { mutableStateOf(emptyList<MultiviewTile>()) }
    val tileFocus = remember { List(4) { FocusRequester() } }
    val toolbarFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        tileFocus[0].requestFocus()
    }

    fun changed(next: List<MultiviewTile>) {
        tiles = next
        actions.onChannels(next.map { it.channelId })
    }

    fun pickLayout(picked: MultiviewLayout) {
        val next = tilesFor(picked, tiles, sound, dropped, state.lists, state.listId)
        if (next.tiles.map { it.channelId } != tiles.map { it.channelId }) changed(next.tiles)
        dropped = next.dropped
        // Too few channels to fill the grid: the grid of as many as there are.
        layout = if (picked.isGrid) MultiviewLayout.grid(next.tiles.size) else picked
        sound = next.heard
        next.tiles.getOrNull(next.heard)?.let { actions.onSound(it.channelId) }
        actions.onLayout(layout)
    }

    Box(modifier.fillMaxSize().background(PlasmaColors.Black)) {
        tiles.forEachIndexed { i, tile ->
            val rect = tileRect(layout, i, tiles.size)
            val onTop = layout == MultiviewLayout.PictureInPicture && i == 1
            key(tile.channelId) {
                MultiviewTileView(
                    tile = tile,
                    hasSound = i == sound,
                    onClick = { if (tile.card != null) actions.onRetry(tile.channelId) else actions.onFullScreen(tile.channelId) },
                    onLongClick = { sheetOpen = true },
                    onFocused = {
                        sound = i
                        actions.onSound(tile.channelId)
                    },
                    video = tileVideo?.let { draw -> { draw(tile.channelId, onTop) } },
                    modifier = if (rect != null) {
                        Modifier
                            .absolute(rect.x.px, rect.y.px, rect.width.px, rect.height.px)
                            // The corner picture draws over the big one.
                            .zIndex(if (onTop) 1f else 0f)
                            .focusRequester(tileFocus[i])
                    } else {
                        // Left out of this layout, it plays on out of sight, its picture's surface
                        // kept, so it's back at once in the next.
                        Modifier.absolute((-PARKED).px, (-PARKED).px, 1.px, 1.px).focusProperties { canFocus = false }
                    },
                )
            }
        }

        Toolbar(
            layout = layout,
            layouts = MultiviewLayout.entries.filter { it.tiles <= state.maxTiles },
            onLayout = ::pickLayout,
            onChangeChannel = { if (sound in tiles.indices) picking = sound },
            onFullScreen = { tiles.getOrNull(sound)?.let { actions.onFullScreen(it.channelId) } },
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 990.px).focusRequester(toolbarFocus),
        )

        SheetHost(visible = sheetOpen, returnFocus = tileFocus[sound.coerceIn(0, 3)], handBack = { picking == null }) {
            val tile = tiles.getOrNull(sound) ?: return@SheetHost
            OptionsSheet(
                title = tile.title ?: tile.channelName,
                subtitle = tile.channelName,
                onDismiss = { sheetOpen = false },
                options = listOfNotNull(
                    SheetOption(stringResource(R.string.multiview_change_channel), onClick = { picking = sound }, icon = PlasmaIcons.ChangeChannel),
                    if (tiles.size > 1) {
                        SheetOption(stringResource(R.string.multiview_remove), onClick = {
                            changed(tiles.filterIndexed { i, _ -> i != sound })
                            sound = 0
                            tiles.firstOrNull()?.let { actions.onSound(it.channelId) }
                            // The grid of one fewer: 4 Tiles becomes 3 Tiles.
                            if (layout.isGrid) {
                                layout = MultiviewLayout.grid(tiles.size)
                                actions.onLayout(layout)
                            }
                        })
                    } else {
                        null
                    },
                ),
            )
        }

        SheetHost(visible = picking != null, returnFocus = tileFocus[(picking ?: sound).coerceIn(0, 3)]) {
            val index = picking ?: return@SheetHost
            val tile = tiles.getOrNull(index) ?: return@SheetHost
            ChannelPicker(
                lists = state.lists,
                current = tile,
                showing = tiles.mapTo(HashSet()) { it.channelId } - tile.channelId,
                onPick = { picked ->
                    picking = null
                    if (picked.channelId != tile.channelId) changed(tiles.toMutableList().also { it[index] = picked })
                    actions.onSound(picked.channelId)
                },
                onDismiss = { picking = null },
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
    video: (@Composable BoxScope.() -> Unit)?,
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
            if (video != null) {
                if (tile.card == null) video()
            } else {
                PlasmaImage(tile.frame.still, Modifier.fillMaxSize(), alignment = tile.frame.alignment, placeholder = PlasmaColors.VideoTile)
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(140.px)
                    .background(Brush.verticalGradient(listOf(PlasmaColors.black(0f), PlasmaColors.black(0.78f)))),
            )
            tile.card?.let { card -> TileCardView(card, tile.channelName, Modifier.align(Alignment.Center)) }
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

/** Why a tile has no picture, in the middle of it: "Your subscription allows 2 streams at a time." */
@Composable
private fun TileCardView(card: TileCard, channelName: String, modifier: Modifier) {
    val text = when (card) {
        is TileCard.Limit -> pluralStringResource(R.plurals.multiview_limit, card.streams, card.streams)
        TileCard.OneUhd -> stringResource(R.string.multiview_one_uhd)
        is TileCard.Problem -> stringResource(
            when (card.problem) {
                PlayerProblem.ConnectionLimit -> R.string.problem_connection_limit
                PlayerProblem.Refused -> R.string.problem_refused
                PlayerProblem.SourceUnavailable -> R.string.problem_source
                PlayerProblem.NotVideo -> R.string.problem_not_video
                PlayerProblem.CantPlay -> R.string.problem_cant_play
                PlayerProblem.NoStream -> R.string.problem_no_stream
            },
        )
    }
    Column(
        modifier
            .padding(horizontal = 40.px)
            .background(PlasmaColors.Toast, PlasmaShapes.Toast)
            .padding(horizontal = 28.px, vertical = 22.px),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.px),
    ) {
        if (card is TileCard.Problem) {
            PlasmaText(stringResource(R.string.cant_play, channelName), plasmaStyle(23f, 600), align = TextAlign.Center, maxLines = 1)
        }
        PlasmaText(text, plasmaStyle(20f, 500, lineHeight = 1.35f), color = PlasmaColors.Text2, align = TextAlign.Center, maxLines = 3)
    }
}

/**
 * Change Channel (brief 4.12's channel picker): Live TV's lists down the left, the list's channels
 * beside them, over the tiles. OK on a channel puts it in the tile; a channel in another tile
 * already can't be picked twice. Back closes it.
 */
@Composable
private fun ChannelPicker(
    lists: List<MultiviewList>,
    current: MultiviewTile,
    showing: Set<String>,
    onPick: (MultiviewTile) -> Unit,
    onDismiss: () -> Unit,
) {
    // It opens on the list that has the tile's channel, Favorites first.
    var listId by remember { mutableStateOf(lists.firstOrNull { list -> list.channels.any { it.channelId == current.channelId } }?.id ?: lists.firstOrNull()?.id.orEmpty()) }
    val list = lists.firstOrNull { it.id == listId }
    val channelsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { channelsFocus.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            // Darker than a sheet's backdrop: the tiles behind keep moving, and the panel is tall.
            .background(PlasmaColors.black(0.72f))
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back || event.key == Key.Escape) {
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        SheetPanel(Modifier.padding(start = 96.px).width(1120.px).height(920.px)) {
            Column(Modifier.padding(horizontal = 10.px), verticalArrangement = Arrangement.spacedBy(4.px)) {
                PlasmaText(stringResource(R.string.multiview_change_channel), plasmaStyle(30f, 600, trackingPx = -0.4f))
                PlasmaText(current.channelName, plasmaStyle(21f, 500), color = PlasmaColors.Text2, maxLines = 1)
            }
            Box(Modifier.padding(top = 22.px, bottom = 14.px).fillMaxWidth().height(1.px).background(PlasmaColors.white(0.1f)))
            Row(horizontalArrangement = Arrangement.spacedBy(24.px)) {
                CountedList(
                    items = lists.map { CountedItem(it.id, it.name, it.channels.size, it.smart) },
                    selectedId = listId,
                    onFocusItem = { listId = it },
                    onOpenItem = { runCatching { channelsFocus.requestFocus() } },
                    modifier = Modifier.width(290.px).fillMaxHeightOfPicker(),
                )
                val channels = list?.channels.orEmpty()
                val start = channels.indexOfFirst { it.channelId == current.channelId }.coerceAtLeast(0)
                key(listId) {
                    val firstRow = remember { FocusRequester() }
                    PlasmaColumn(
                        modifier = Modifier.weight(1f).fillMaxHeightOfPicker().focusRequester(channelsFocus).focusRestoringGroup(firstRow),
                        state = rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0)),
                        gap = 4.px,
                        pivot = null,
                        context = 80.px,
                    ) {
                        itemsIndexed(channels, key = { _, channel -> channel.channelId }) { i, channel ->
                            PickerRow(
                                channel = channel,
                                current = channel.channelId == current.channelId,
                                elsewhere = channel.channelId in showing,
                                onClick = { onPick(channel) },
                                modifier = if (i == start) Modifier.focusRequester(firstRow) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.fillMaxHeightOfPicker(): Modifier = this.height(740.px)

/** A channel in Change Channel: its number, logo and name, and what's on. One in another tile is dimmed and can't be picked. */
@Composable
private fun PickerRow(channel: MultiviewTile, current: Boolean, elsewhere: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val inMultiview = stringResource(R.string.multiview_showing)
    val description = listOfNotNull(channel.number.takeIf { it.isNotEmpty() }, channel.channelName, channel.title, inMultiview.takeIf { elsewhere }).joinToString(", ")
    PlasmaSurface(
        onClick = onClick,
        enabled = !elsewhere,
        modifier = modifier
            .fillMaxWidth()
            .height(78.px)
            .alpha(if (elsewhere) 0.45f else 1f)
            .semantics { contentDescription = description },
        style = FocusStyle.Row,
        shape = PlasmaShapes.Row,
        selected = current,
        contentAlignment = Alignment.CenterStart,
    ) {
        val focused = LocalFocused.current
        Row(
            Modifier.fillMaxSize().padding(start = 12.px, end = 20.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.px),
        ) {
            PlasmaText(
                channel.number,
                plasmaStyle(22f, if (focused) 600 else 500, tabular = true),
                Modifier.width(56.px),
                color = if (focused) PlasmaColors.OnFocus2 else PlasmaColors.Text3,
                align = TextAlign.End,
            )
            LogoPlate(channel.logo, channel.channelName, 112.px, 52.px, logoSize = LogoSize.Plate)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.px)) {
                PlasmaText(channel.channelName, plasmaStyle(23f, 600), color = if (focused) PlasmaColors.OnFocus else PlasmaColors.Text, maxLines = 1)
                val line = if (elsewhere) inMultiview else channel.title
                if (line != null) PlasmaText(line, plasmaStyle(19f, 500), color = if (focused) PlasmaColors.OnFocus3 else PlasmaColors.Text3, maxLines = 1)
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
 * 2 Tiles · 3 Tiles · 4 Tiles · Focus + 3 · Picture in Picture | Change Channel · Full Screen, in a
 * glass bar; [layouts] are those the TV's decoders take. The current layout is white while focus
 * is on the tiles; once the bar has focus, focus is white and the current layout is marked like a tab.
 */
@Composable
private fun Toolbar(
    layout: MultiviewLayout,
    layouts: List<MultiviewLayout>,
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
        for (value in layouts) {
            val (icon, label) = when (value) {
                MultiviewLayout.TwoTiles -> PlasmaIcons.TwoTiles to R.string.multiview_two
                MultiviewLayout.ThreeTiles -> PlasmaIcons.ThreeTiles to R.string.multiview_three
                MultiviewLayout.FourTiles -> PlasmaIcons.Multiview to R.string.multiview_four
                MultiviewLayout.FocusPlusThree -> PlasmaIcons.FocusPlusThree to R.string.multiview_focus_three
                MultiviewLayout.PictureInPicture -> PlasmaIcons.PictureInPicture to R.string.multiview_pip
            }
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
