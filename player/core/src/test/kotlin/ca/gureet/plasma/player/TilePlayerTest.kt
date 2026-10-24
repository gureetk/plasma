// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.view.SurfaceView
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.VideoDecoding
import ca.gureet.plasma.stream.ConnectionBudget
import ca.gureet.plasma.stream.HubConfig
import ca.gureet.plasma.stream.StreamHub
import ca.gureet.plasma.stream.UpstreamRequest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** A Multiview tile's player (brief 4.12 and 5.8), with a stand-in engine and a real stream hub. */
class TilePlayerTest {
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
        val opened = CopyOnWriteArrayList<PlayRequest>()
        var lastVolume = -1
        var stops = 0

        override fun attach(view: SurfaceView, subtitles: SurfaceView?) = Unit
        override fun detach() = Unit
        override fun setWindowSize(width: Int, height: Int) = Unit
        override fun open(request: PlayRequest) {
            opened += request
            state.value = EngineState.Opening
        }
        override fun play() = Unit
        override fun pause() = Unit
        override fun stop() {
            stops++
            state.value = EngineState.Idle
            hasPicture.value = false
        }
        override fun seekTo(ms: Long) = Unit
        override fun setVolume(percent: Int) {
            lastVolume = percent
        }
        override fun setLevel(db: Int) = Unit
        override fun selectAudio(id: String) = Unit
        override fun selectSubtitles(id: String?) = Unit
        override fun release() = Unit
    }

    private val provider = MockWebServer()
    private val engine = FakeEngine()
    private val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + thread)
    private val budget = ConnectionBudget("home", maxConnections = 2)
    private lateinit var hub: StreamHub
    private lateinit var tile: TilePlayer

    @Before fun setUp() {
        provider.dispatcher = object : Dispatcher() {
            // A live stream that keeps coming: ten packets every tenth of a second.
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse.Builder()
                .addHeader("Content-Type", "video/mp2t")
                .body(Buffer().write(ByteArray(188 * 3_000) { if (it % 188 == 0) 0x47 else 0x11 }))
                .throttleBody(188 * 10L, 100, TimeUnit.MILLISECONDS)
                .build()
        }
        provider.start()
        hub = StreamHub(OkHttpClient(), HubConfig(noDataTimeoutMs = 20_000, reconnectDelaysMs = listOf(0)))
        tile = TilePlayer(engine, hub, resolver, scope)
    }

    @After fun tearDown() {
        scope.cancel()
        thread.close()
        hub.close()
        provider.close()
    }

    private val resolver = StreamResolver { channelId -> StreamTarget(channelId, provider.url("/live/viewer/s3cret/$channelId.ts").toString(), budget = budget) }

    private fun onPlayer(block: () -> Unit) = runBlocking { withContext(thread) { block() } }

    private fun <T> await(flow: StateFlow<T>, predicate: (T) -> Boolean): T = runBlocking { withTimeout(10_000) { flow.first(predicate) } }

    private fun awaitOpened(count: Int = 1) = runBlocking { withTimeout(10_000) { while (engine.opened.size < count) delay(10) } }

    @Test fun aTileJoinsTheConnectionOpenForItsChannelAndDecodesItsSound() {
        // The main player's connection to the channel, as it plays it.
        val main = hub.open(UpstreamRequest(provider.url("/live/viewer/s3cret/ch105.ts").toString(), budget = budget, shareKey = "ch105"))

        onPlayer { tile.play("ch105") }
        awaitOpened()

        assertEquals(1, hub.connectionsInUse("home"))
        val request = engine.opened.single()
        assertTrue(request.url.startsWith("http://127.0.0.1:${hub.port}/live/"))
        assertEquals(AudioOutput.DecodeToPcm, request.audioOutput)
        assertEquals(VideoDecoding.Hardware, request.decoding)
        onPlayer {
            engine.state.value = EngineState.Playing
            engine.hasPicture.value = true
        }
        assertEquals(LiveStatus.Playing("ch105"), await(tile.status) { it is LiveStatus.Playing })
        main.close()
    }

    @Test fun overTheAccountsLimitTheTileSaysSo() {
        val one = TilePlayer(engine, hub, StreamResolver { StreamTarget(it, provider.url("/live/$it.ts").toString(), budget = ConnectionBudget("home", maxConnections = 1)) }, scope)
        val main = hub.open(UpstreamRequest(provider.url("/live/ch105.ts").toString(), budget = ConnectionBudget("home", maxConnections = 1), shareKey = "ch105"))

        onPlayer { one.play("ch107") }

        assertEquals(LiveStatus.Failed("ch107", LiveFailure.ConnectionLimit), await(one.status) { it is LiveStatus.Failed })
        assertTrue(engine.opened.isEmpty())
        main.close()
    }

    @Test fun theTileWithTheSoundIsHeard() {
        onPlayer { tile.play("ch105") }
        awaitOpened()
        assertEquals(0, engine.lastVolume)

        onPlayer { tile.setSound(true) }
        assertEquals(100, engine.lastVolume)
        onPlayer { tile.setSound(false) }
        assertEquals(0, engine.lastVolume)
    }

    @Test fun aStreamItsEngineCantPlayFreesTheConnection() {
        onPlayer { tile.play("ch105") }
        awaitOpened()
        assertEquals(1, hub.connectionsInUse("home"))

        onPlayer { engine.state.value = EngineState.Failed("no decoder") }

        assertEquals(LiveStatus.Failed("ch105", LiveFailure.CantPlay), await(tile.status) { it is LiveStatus.Failed })
        assertEquals(0, hub.connectionsInUse("home"))
    }

    @Test fun goingFullScreenKeepsTheConnectionAMomentForTheMainPlayer() {
        onPlayer { tile.play("ch105") }
        awaitOpened()

        onPlayer { tile.handOff(holdMs = 500) }

        // The main player joins it in the meantime.
        val main = hub.open(UpstreamRequest(provider.url("/live/viewer/s3cret/ch105.ts").toString(), budget = budget, shareKey = "ch105"))
        assertEquals(1, hub.connectionsInUse("home"))
        runBlocking { delay(1_000) }
        assertEquals(1, hub.connectionsInUse("home"))
        main.close()
        runBlocking { withTimeout(5_000) { while (hub.connectionsInUse("home") > 0) delay(20) } }
    }
}
