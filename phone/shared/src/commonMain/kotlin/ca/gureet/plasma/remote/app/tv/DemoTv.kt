// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.tv

import ca.gureet.plasma.remote.protocol.RemoteChannel
import ca.gureet.plasma.remote.protocol.RemoteKey
import ca.gureet.plasma.remote.protocol.RemoteList
import ca.gureet.plasma.remote.protocol.RemoteLists
import ca.gureet.plasma.remote.protocol.RemotePlaying
import ca.gureet.plasma.remote.protocol.RemoteState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.TimeSource

/**
 * Try the Demo (brief 5.22): a pretend TV with the boards' data (appendix A), at Wed 21 Oct 2026,
 * 20:41, for trying the app without a TV, for the stores' screenshots, and for App Review. It
 * answers as Plasma would: channel keys and numbers change channel, Play/Pause pauses and counts
 * how far behind live the picture is, and Go Live goes back to it.
 */
class DemoTv(override val name: String) : TvLink {
    private class Channel(val number: Int, val id: String, val name: String, val now: String, val startMinute: Int, val endMinute: Int)

    /** The channels, minutes from midnight on the boards' evening (past 24:00 for after midnight). */
    private val channels = listOf(
        Channel(101, "demo:101", "Northline News", "Business Wrap", 20 * 60 + 30, 21 * 60),
        Channel(102, "demo:102", "Meridian 24", "Markets Close", 19 * 60, 21 * 60),
        Channel(103, "demo:103", "Arena Sports News", "Sports Desk", 20 * 60, 21 * 60),
        Channel(104, "demo:104", "Arena Sports Extra", "Transfer Talk", 20 * 60 + 30, 21 * 60 + 30),
        Channel(105, "demo:105", "Arena Sports 1", "Harbour City v Redmoor", 19 * 60 + 45, 22 * 60),
        Channel(106, "demo:106", "Arena Sports 2", "Grand Slam Tennis: Quarter-final", 19 * 60, 22 * 60 + 30),
        Channel(107, "demo:107", "Velocity Motor", "Endurance 24: Night Stint", 20 * 60, 21 * 60 + 30),
        Channel(108, "demo:108", "Courtside+", "City Lights v Northport", 20 * 60 + 30, 23 * 60),
        Channel(109, "demo:109", "Fairway Golf", "Coastal Classic, Round 2", 18 * 60, 22 * 60),
        Channel(120, "demo:120", "Atlas One", "The Quiz Hour", 20 * 60, 21 * 60),
        Channel(140, "demo:140", "Cinema Prime 4K", "The Last Lighthouse", 19 * 60 + 30, 21 * 60 + 45),
        Channel(180, "demo:180", "Wildframe 4K", "Kingdoms of Ice, Episode 3", 20 * 60, 21 * 60),
        Channel(200, "demo:200", "Pulse", "Late Night Sessions", 20 * 60, 22 * 60),
    )

    /** Favorites in the boards' order, counting from 1 (brief 5.2). */
    private val favorites = listOf(105, 107, 101, 102, 180, 120, 108)

    private val lists = listOf(
        DemoList(RemoteList("favorites", "Favorites", favorites.size, countsFromOne = true), favorites),
        DemoList(RemoteList("sports", "Sports", 6), listOf(103, 104, 105, 106, 108, 109)),
        DemoList(RemoteList("news", "News", 2), listOf(101, 102)),
        DemoList(RemoteList("movies", "Movies", 1), listOf(140)),
        DemoList(RemoteList("all", "All Channels", channels.size), channels.map { it.number }),
    )

    private class DemoList(val list: RemoteList, val numbers: List<Int>)

    private val _state = MutableStateFlow(stateFor(channel(105), behindLive = 134))
    override val state: StateFlow<RemoteState> = _state.asStateFlow()

    override val status: StateFlow<LinkStatus> = MutableStateFlow(LinkStatus.Connected).asStateFlow()

    private var typedNumber = ""

    /** When Play/Pause paused the picture: it falls behind live from then. */
    private var pausedAt: TimeSource.Monotonic.ValueTimeMark? = null

    private fun channel(number: Int): Channel = channels.first { it.number == number }

    private val playingChannel: Channel get() = channels.first { it.id == _state.value.playing?.channelId }

    private fun stateFor(channel: Channel, behindLive: Long = 0, paused: Boolean = false): RemoteState {
        val length = (channel.endMinute - channel.startMinute).toFloat()
        val live = ((NOW - channel.startMinute) / length).coerceIn(0f, 1f)
        val picture = ((NOW - channel.startMinute - behindLive / 60f) / length).coerceIn(0f, 1f)
        return RemoteState(
            front = true,
            playing = RemotePlaying(channel.id, channel.number.toString(), channel.name, channel.now, live, picture, behindLive, paused),
            keys = mapOf("red" to "Favorite", "green" to "Catch-up", "yellow" to "Sort", "blue" to "Multiview"),
        )
    }

    override fun key(key: RemoteKey, down: Boolean) {
        if (!down) return
        when (key) {
            RemoteKey.ChannelUp -> zap(+1)
            RemoteKey.ChannelDown -> zap(-1)
            RemoteKey.PlayPause -> {
                val behind = _state.value.playing?.behindLive ?: return
                val paused = pausedAt
                pausedAt = if (paused == null) TimeSource.Monotonic.markNow() else null
                val now = if (paused == null) behind else behind + paused.elapsedNow().inWholeSeconds
                _state.value = stateFor(playingChannel, now, paused = paused == null)
            }
            else -> {}
        }
    }

    private fun zap(step: Int) {
        val at = channels.indexOf(playingChannel)
        pausedAt = null
        _state.value = stateFor(channels[(at + step).mod(channels.size)])
    }

    override fun digits(digits: String) {
        typedNumber = (typedNumber + digits).takeLast(3)
        val match = channels.firstOrNull { it.number.toString() == typedNumber } ?: return
        typedNumber = ""
        pausedAt = null
        _state.value = stateFor(match)
    }

    override fun text(text: String) {}

    override fun submit() {}

    override fun tune(channelId: String, listId: String?) {
        val channel = channels.firstOrNull { it.id == channelId } ?: return
        pausedAt = null
        _state.value = stateFor(channel)
    }

    override fun goLive() {
        pausedAt = null
        _state.value = stateFor(playingChannel)
    }

    override suspend fun lists(listId: String?, from: Int): RemoteLists {
        val shown = lists.firstOrNull { it.list.id == listId } ?: lists.first()
        val rows = shown.numbers.mapIndexed { index, number ->
            val channel = channel(number)
            val progress = ((NOW - channel.startMinute) / (channel.endMinute - channel.startMinute).toFloat()).coerceIn(0f, 1f)
            val listNumber = if (shown.list.countsFromOne) (index + 1).toString() else channel.number.toString()
            RemoteChannel(channel.id, listNumber, channel.name, channel.now, progress, playing = channel.id == _state.value.playing?.channelId)
        }
        return RemoteLists(lists.map { it.list }, shown.list.id, from, rows.drop(from))
    }

    private companion object {
        /** 20:41, the boards' time. */
        const val NOW = 20 * 60 + 41
    }
}
