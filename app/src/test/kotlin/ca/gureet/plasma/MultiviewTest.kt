// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.view.SurfaceView
import ca.gureet.plasma.channels.NumberReason
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.ChannelGroup
import ca.gureet.plasma.core.data.LastWatched
import ca.gureet.plasma.core.data.NumberingSettings
import ca.gureet.plasma.live.Lists
import ca.gureet.plasma.live.LiveWorld
import ca.gureet.plasma.live.multiviewLists
import ca.gureet.plasma.live.multiviewStart
import ca.gureet.plasma.player.AudioInfo
import ca.gureet.plasma.player.EngineId
import ca.gureet.plasma.player.EngineState
import ca.gureet.plasma.player.MediaTracks
import ca.gureet.plasma.player.PlayRequest
import ca.gureet.plasma.player.PlayerEngine
import ca.gureet.plasma.player.StreamResolver
import ca.gureet.plasma.player.StreamTarget
import ca.gureet.plasma.player.TilePlayer
import ca.gureet.plasma.player.VideoInfo
import ca.gureet.plasma.stream.StreamHub
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Multiview on the source's channels (brief 4.12 and 5.8): where it starts, its lists, and its tiles' players. */
class MultiviewTest {
    private fun channel(key: String, number: Int, name: String, group: String, hidden: Boolean = false, locked: Boolean = false) = Channel(
        id = "home:$key", sourceId = "home", key = key, name = name, number = number, numberReason = NumberReason.Playlist,
        logo = null, groupId = "home:$group", groupName = group, epgKey = null, catchupDays = null, isRadio = false,
        hidden = hidden, locked = locked,
    )

    private val northline = channel("101", 101, "Northline News", "news")
    private val arena = channel("105", 105, "Arena Sports 1", "sports")
    private val velocity = channel("107", 107, "Velocity Motor", "sports", hidden = true)
    private val courtside = channel("108", 108, "Courtside+", "sports")
    private val combat = channel("110", 110, "Combat Arena", "sports", locked = true)
    private val fairway = channel("112", 112, "Fairway Golf", "sports")

    private val world = LiveWorld(
        channels = listOf(northline, arena, velocity, courtside, combat, fairway),
        groups = listOf(ChannelGroup("home:sports", "home", "Sports", hidden = false), ChannelGroup("home:news", "home", "News", hidden = false)),
        favoriteIds = listOf(courtside.id, arena.id),
        recentIds = emptyList(),
        programmes = emptyMap(),
        numbering = NumberingSettings(),
        lastWatched = LastWatched(courtside.id, "home:sports"),
        now = Instant.parse("2026-10-21T20:41:00Z"),
    )

    @Test fun itStartsOnTheChannelPlayingAndTheOnesAfterItInItsList() {
        // Courtside+, last watched in Sports: then Fairway Golf, round to Arena Sports 1; hidden and locked ones left out.
        assertEquals("home:sports" to listOf(courtside.id, fairway.id, arena.id), multiviewStart(world, playing = null, capacity = 4) { false })
        // Unlocked with the PIN, Combat Arena joins in.
        assertEquals(listOf(courtside.id, combat.id, fairway.id, arena.id), multiviewStart(world, null, capacity = 4) { it == combat.id }.second)
        // Playing a channel that isn't in the last list: All Channels.
        assertEquals(Lists.ALL to listOf(northline.id, arena.id), multiviewStart(world, playing = northline.id, capacity = 2) { false })
    }

    @Test fun changeChannelListsFavoritesTheGroupsThenAllChannels() {
        val lists = multiviewLists(world, "Favorites", "All Channels") { false }

        assertEquals(listOf("Favorites", "Sports", "News", "All Channels"), lists.map { it.name })
        assertEquals(listOf("1" to "Courtside+", "2" to "Arena Sports 1"), lists.first().channels.map { it.number to it.channelName })
        // Groups keep full-list numbers; Velocity Motor (hidden) and Combat Arena (locked) aren't there.
        assertEquals(listOf("105", "108", "112"), lists[1].channels.map { it.number })
    }

    // ---- The tiles' players ----------------------------------------------------------------------

    /** Plays nothing; records what it's asked, and lets tests say what it's doing. */
    private class FakeEngine : PlayerEngine {
        override val id = EngineId.Vlc
        override val state = MutableStateFlow<EngineState>(EngineState.Idle)
        override val video = MutableStateFlow<VideoInfo?>(null)
        override val hasPicture = MutableStateFlow(false)
        override val audio = MutableStateFlow<AudioInfo?>(null)
        override val tracks = MutableStateFlow(MediaTracks())
        override val position = MutableStateFlow(0L)
        override val length = MutableStateFlow(0L)
        val opened = CopyOnWriteArrayList<String>()
        var heard = -1
        var stops = 0
        var released = false

        override fun attach(view: SurfaceView, subtitles: SurfaceView?) = Unit
        override fun detach() = Unit
        override fun setWindowSize(width: Int, height: Int) = Unit
        override fun open(request: PlayRequest) {
            opened += request.url.substringAfterLast('/')
            state.value = EngineState.Opening
        }
        override fun play() = Unit
        override fun pause() = Unit
        override fun stop() {
            stops++
            state.value = EngineState.Idle
            video.value = null
        }
        override fun seekTo(ms: Long) = Unit
        override fun setVolume(percent: Int) {
            heard = percent
        }
        override fun setLevel(db: Int) = Unit
        override fun selectAudio(id: String) = Unit
        override fun selectSubtitles(id: String?) = Unit
        override fun release() {
            released = true
        }
    }

    private val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + thread)
    private val hub = StreamHub(OkHttpClient())
    private val engines = CopyOnWriteArrayList<FakeEngine>()

    /** Straight to the engine, as an HLS channel is: no connection to open. */
    private val streams = StreamResolver { StreamTarget(it, "http://media.home.lan/live/$it.m3u8", throughHub = false) }

    private fun host(capacity: Int = 4) = MultiviewHost(scope, { TilePlayer(FakeEngine().also { engines += it }, hub, streams, scope) }, capacity)

    private fun <T> onMain(block: () -> T): T = runBlocking { withContext(thread) { block() } }

    private fun settle() = runBlocking { delay(200) }

    @After fun tearDown() {
        scope.cancel()
        thread.close()
        hub.close()
    }

    @Test fun asManyTilesAsTheDecodersTakeAndTheFirstIsHeard() {
        val host = host(capacity = 2)

        onMain { host.show(listOf("a", "b", "c", "d")) }
        settle()

        assertEquals(listOf("a", "b"), host.tiles.value.map { it.channelId })
        assertEquals(listOf(listOf("a.m3u8"), listOf("b.m3u8")), engines.map { it.opened.toList() })
        assertEquals(listOf(100, 0), engines.map { it.heard })

        onMain { host.soundTo("b") }
        assertEquals(listOf(0, 100), engines.map { it.heard })
    }

    @Test fun changingATileKeepsTheOthersPlayingAndReusesItsEngine() {
        val host = host()
        onMain { host.show(listOf("a", "b")) }
        settle()

        onMain { host.show(listOf("a", "c")) }
        settle()

        // a carried on; b's engine took c.
        assertEquals(listOf(listOf("a.m3u8"), listOf("b.m3u8", "c.m3u8")), engines.map { it.opened.toList() })
        assertEquals(2, engines.size)
    }

    @Test fun aSecond4kChannelWaitsWithItsCard() {
        val host = host()
        onMain { host.show(listOf("a", "b", "c")) }
        settle()

        onMain { engines[0].video.value = VideoInfo(3840, 2160) }
        settle()
        onMain { engines[2].video.value = VideoInfo(3840, 2160) }
        settle()

        assertEquals(listOf(false, false, true), host.tiles.value.map { it.heldBack })
        assertEquals(1, engines[2].stops - 1)
        assertFalse(engines[0].stops > 1)
    }

    @Test fun offTheScreenTheTilesStopAndComeBack() {
        val host = host()
        onMain { host.show(listOf("a", "b")) }
        settle()

        onMain { host.onScreen(false) }
        assertTrue(engines.all { it.state.value == EngineState.Idle })
        onMain { host.onScreen(true) }
        runBlocking { withTimeout(5_000) { while (engines.any { it.opened.size < 2 }) delay(10) } }

        onMain { host.close() }
        assertTrue(engines.all { it.released })
    }
}
