// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import ca.gureet.plasma.player.DecoderCapacity
import ca.gureet.plasma.player.LiveStatus
import ca.gureet.plasma.player.TilePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** A Multiview tile as it plays now: its channel, what it's doing, and whether it's a 4K one held back (5.8). */
data class TileNow(val channelId: String, val status: LiveStatus, val heldBack: Boolean = false)

/**
 * Multiview's players (brief 4.12 and 5.8): a [TilePlayer] for each tile, each its own VLC engine
 * and its own connection, the tile with focus heard. As many tiles as the TV's decoders take, and
 * one 4K channel at a time: a second waits with its card. Tiles stop while Plasma isn't on the
 * screen and play again when it is. The engines go when Multiview closes.
 *
 * Used on the main thread.
 */
class MultiviewHost(
    private val scope: CoroutineScope,
    private val newPlayer: () -> TilePlayer,
    /** How many tiles the decoders take. */
    val capacity: Int = DecoderCapacity.tiles(),
) {
    private val _tiles = MutableStateFlow<List<TileNow>>(emptyList())

    /** The tiles, in order. */
    val tiles: StateFlow<List<TileNow>> = _tiles.asStateFlow()

    /** Each tile's player, by channel. */
    private val players = LinkedHashMap<String, TilePlayer>()

    /** Players whose tiles went, their engines kept for the next channel. */
    private val idle = ArrayDeque<TilePlayer>()

    private var sound: String? = null
    private var onScreen = true
    private var watching: Job? = null

    /** 4K channels held back while another 4K tile plays (5.8). */
    private val heldBack = HashSet<String>()

    /** The 4K tile that plays. */
    private var uhdAllowed: String? = null

    /** The tiles show [channels], in order, as many as the decoders take: tiles already playing carry on. */
    fun show(channels: List<String>) {
        val wanted = channels.distinct().take(capacity)
        for (gone in players.keys - wanted.toSet()) {
            players.remove(gone)?.let { player ->
                player.stop()
                idle.addLast(player)
            }
            heldBack -= gone
        }
        val ordered = LinkedHashMap<String, TilePlayer>()
        for (channelId in wanted) {
            val player = players[channelId] ?: (idle.removeFirstOrNull() ?: newPlayer()).also { if (onScreen) it.play(channelId) }
            ordered[channelId] = player
        }
        players.clear()
        players.putAll(ordered)
        if (sound !in players) sound = wanted.firstOrNull()
        hear()
        // The tiles as they are now, at once; the players say more as they open.
        _tiles.value = players.map { (channelId, player) -> TileNow(channelId, player.status.value, heldBack = channelId in heldBack) }
        watch()
    }

    /** The tile with [channelId] has focus, and so the sound (4.12). */
    fun soundTo(channelId: String) {
        if (channelId !in players) return
        sound = channelId
        hear()
    }

    /** Tries a tile's channel again, after its card. */
    fun retry(channelId: String) {
        val player = players[channelId] ?: return
        heldBack -= channelId
        player.stop()
        if (onScreen) player.play(channelId)
        watch()
    }

    /** The player for [channelId]'s tile, to draw into. */
    fun playerFor(channelId: String): TilePlayer? = players[channelId]

    /** Plasma left the screen (the TV off, another app in front): the tiles stop; back on it, they play again (5.20). */
    fun onScreen(on: Boolean) {
        if (on == onScreen) return
        onScreen = on
        players.forEach { (channelId, player) -> if (on) player.play(channelId) else player.stop() }
        if (!on) {
            heldBack.clear()
            uhdAllowed = null
        }
        watch()
    }

    /**
     * Full Screen on [channelId]: its connection is kept a moment so the main player joins it
     * rather than connecting again (5.8); the rest stop.
     */
    fun handOff(channelId: String) {
        players[channelId]?.handOff(HAND_OFF_MS)
    }

    /** Multiview closed: every tile stops and the engines go. */
    fun close() {
        watching?.cancel()
        players.values.forEach { it.release() }
        players.clear()
        idle.forEach { it.release() }
        idle.clear()
        _tiles.value = emptyList()
    }

    private fun hear() {
        players.forEach { (channelId, player) -> player.setSound(channelId == sound) }
    }

    /** Follows the tiles' players for what each shows, and holds back a second 4K channel (5.8). */
    private fun watch() {
        watching?.cancel()
        val now = players.toList()
        if (now.isEmpty()) {
            _tiles.value = emptyList()
            return
        }
        watching = scope.launch {
            val flows = now.map { (channelId, player) ->
                combine(player.status, player.video.map { it != null && (it.width >= UHD_WIDTH || it.height >= UHD_HEIGHT) }) { status, uhd -> Triple(channelId, status, uhd) }
            }
            combine(flows) { it.toList() }.distinctUntilChanged().collect { states ->
                // The first 4K tile plays; any other stops and says why, until it's tried again.
                if (uhdAllowed != null && uhdAllowed !in players) uhdAllowed = null
                for ((channelId, _, uhd) in states) {
                    if (!uhd) continue
                    if (uhdAllowed == null) {
                        uhdAllowed = channelId
                    } else if (channelId != uhdAllowed && heldBack.add(channelId)) {
                        players[channelId]?.stop()
                    }
                }
                _tiles.value = states.map { (channelId, status, _) -> TileNow(channelId, status, heldBack = channelId in heldBack) }
            }
        }
    }

    private companion object {
        /** How long a tile's connection waits for the main player to join it (5.8). */
        const val HAND_OFF_MS = 8_000L
        const val UHD_WIDTH = 3_840
        const val UHD_HEIGHT = 2_160
    }
}
