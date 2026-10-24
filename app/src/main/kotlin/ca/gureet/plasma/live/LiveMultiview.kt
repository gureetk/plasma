// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import ca.gureet.plasma.AppGraph
import ca.gureet.plasma.MultiviewHost
import ca.gureet.plasma.R
import ca.gureet.plasma.TileNow
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.feature.player.MultiviewActions
import ca.gureet.plasma.feature.player.MultiviewList
import ca.gureet.plasma.feature.player.MultiviewScreen
import ca.gureet.plasma.feature.player.MultiviewTile
import ca.gureet.plasma.feature.player.MultiviewUiState
import ca.gureet.plasma.feature.player.PlayerStatus
import ca.gureet.plasma.feature.player.TileCard
import ca.gureet.plasma.player.DecoderCapacity
import ca.gureet.plasma.player.LiveFailure
import ca.gureet.plasma.player.LiveStatus
import ca.gureet.plasma.player.StreamKeys
import ca.gureet.plasma.player.TilePlayer
import ca.gureet.plasma.player.VideoFrame

/**
 * Multiview on the source's channels (brief 4.12 and 5.8): the channel playing, or the one last
 * watched, and the ones after it in its list, as many as the TV's decoders take. Full Screen
 * hands the tile's connection to the main player, in that list.
 */
@Composable
fun LiveMultiview(graph: AppGraph, onFullScreen: (channelId: String, listId: String) -> Unit) {
    val world by graph.world.collectAsState()
    val now = world ?: return
    val capacity = remember { DecoderCapacityHint.tiles }
    val start = remember {
        val playing = graph.playingNow()?.takeIf { !StreamKeys.isVod(it) }?.let(StreamKeys::channelOf)
        multiviewStart(now, playing, capacity) { graph.parental.isUnlocked(it) }
    }
    val host = remember { MultiviewHost(graph.scope, graph::tilePlayer, capacity).also { it.show(start.second) } }
    DisposableEffect(host) { onDispose { host.close() } }
    // The tiles play, not the main player, so the TV's own screensaver is kept off here (5.20).
    DisposableEffect(graph) {
        graph.holdScreen(true)
        onDispose { graph.holdScreen(false) }
    }
    // Off the screen, the tiles stop; back on it, they play again (5.20).
    val front by graph.standby.inFrontNow.collectAsState()
    val tvOn by graph.standby.tvOn.collectAsState()
    LaunchedEffect(front, tvOn) { host.onScreen(front && tvOn) }
    val tiles by host.tiles.collectAsState()
    val sources by graph.data.sources.sources.collectAsState(emptyList())
    val favorites = stringResource(R.string.favorites)
    val allChannels = stringResource(R.string.all_channels)
    val lists = remember(now, favorites, allChannels) { multiviewLists(now, favorites, allChannels) { graph.parental.isUnlocked(it) } }
    val limits = remember(sources) { sources.associate { it.id to it.maxConnections } }
    val state = remember(now, tiles, lists, limits) {
        MultiviewUiState(tiles = tiles.mapNotNull { tileOf(now, it, limits) }, lists = lists)
    }
    val actions = remember(host) {
        MultiviewActions(
            onFullScreen = { channelId ->
                host.handOff(channelId)
                onFullScreen(channelId, start.first)
            },
            onChannels = host::show,
            onSound = host::soundTo,
            onRetry = host::retry,
        )
    }
    MultiviewScreen(state, actions, tileVideo = { channelId, onTop -> TileVideo(host.playerFor(channelId), onTop) })
}

/** What [DecoderCapacity] says, read once: probing the codecs isn't free. */
private object DecoderCapacityHint {
    val tiles: Int by lazy { DecoderCapacity.tiles() }
}

/** A tile's picture: a surface of its own for its engine, over the other tiles when [onTop] (Picture in Picture). */
@Composable
private fun TileVideo(player: TilePlayer?, onTop: Boolean) {
    player ?: return
    // A surface's place among the others is set before it's shown, so a new one comes with the layout.
    key(player, onTop) {
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    if (onTop) setZOrderMediaOverlay(true)
                    player.attach(this)
                }
            },
            modifier = Modifier.fillMaxSize().onSizeChanged { player.setWindowSize(it.width, it.height) },
            onRelease = { player.detach() },
        )
    }
}

/**
 * Where Multiview starts (5.8): [playing], or the channel last watched, then the ones after it in
 * its list, round to the start, as many as [capacity]; hidden channels, and locked ones not
 * unlocked, left out. The list's id comes first.
 */
internal fun multiviewStart(world: LiveWorld, playing: String?, capacity: Int, isUnlocked: (String) -> Boolean): Pair<String, List<String>> {
    val current = playing?.takeIf { it in world.byId } ?: world.currentChannel?.id
    val listId = world.lastWatched?.listId?.takeIf { id -> world.list(id).any { it.id == current } } ?: Lists.ALL
    val list = world.list(listId).filter { open(world, it, isUnlocked) }
    val from = list.indexOfFirst { it.id == current }.coerceAtLeast(0)
    return listId to (list.drop(from) + list.take(from)).take(capacity).map { it.id }
}

/**
 * Change Channel's lists (4.12): Favorites, the groups with channels, then All Channels, numbered
 * as Live TV numbers them (5.2), without hidden channels or locked ones not unlocked.
 */
internal fun multiviewLists(world: LiveWorld, favorites: String, allChannels: String, isUnlocked: (String) -> Boolean): List<MultiviewList> {
    fun listFor(id: String, name: String, smart: Boolean): MultiviewList? {
        val countsFromOne = world.countsFromOne(id)
        val channels = world.list(id).mapIndexedNotNull { index, channel ->
            if (!open(world, channel, isUnlocked)) return@mapIndexedNotNull null
            tileFor(world, channel, number = if (countsFromOne) index + 1 else channel.number)
        }
        return MultiviewList(id, name, channels, smart).takeIf { channels.isNotEmpty() || smart }
    }
    return buildList {
        listFor(Lists.FAVORITES, favorites, smart = true)?.takeIf { it.channels.isNotEmpty() }?.let(::add)
        world.groups.filter { !it.hidden }.forEach { group -> listFor(group.id, group.name, smart = false)?.let(::add) }
        listFor(Lists.ALL, allChannels, smart = true)?.let(::add)
    }
}

private fun open(world: LiveWorld, channel: Channel, isUnlocked: (String) -> Boolean): Boolean = !channel.hidden && (!world.lockedNow(channel) || isUnlocked(channel.id))

private fun tileFor(world: LiveWorld, channel: Channel, number: Int = channel.number, card: TileCard? = null) = MultiviewTile(
    channelId = channel.id,
    channelName = channel.name,
    logo = channel.logo,
    title = world.nowNext(channel).now?.title,
    frame = VideoFrame(still = null, channelId = channel.id),
    number = number.toString(),
    card = card,
)

/** A tile as it plays now, with its card when it can't: the account's limit, a second 4K channel, or what failed. */
private fun tileOf(world: LiveWorld, tile: TileNow, limits: Map<String, Int?>): MultiviewTile? {
    val channel = world.byId[tile.channelId] ?: return null
    val status = tile.status
    val card = when {
        tile.heldBack -> TileCard.OneUhd
        status is LiveStatus.Failed && status.failure == LiveFailure.ConnectionLimit -> TileCard.Limit(limits[channel.sourceId] ?: 1)
        else -> (statusFor(status, tile.channelId) as? PlayerStatus.Failed)?.let { TileCard.Problem(it.problem) }
    }
    return tileFor(world, channel, card = card)
}
