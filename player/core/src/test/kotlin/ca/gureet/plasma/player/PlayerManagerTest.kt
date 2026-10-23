// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.view.SurfaceView
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.BufferSize
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** The player's part of brief 6.5, with a stand-in engine and a real stream hub. */
class PlayerManagerTest {
    /** Plays nothing; records what it's asked, and lets tests say what it's doing. */
    private class FakeEngine : PlayerEngine {
        override val id = EngineId.Vlc
        override val state = MutableStateFlow<EngineState>(EngineState.Idle)
        override val video = MutableStateFlow<VideoInfo?>(null)
        override val hasPicture = MutableStateFlow(false)
        val opened = CopyOnWriteArrayList<PlayRequest>()
        var stops = 0
        var lastVolume = -1

        override fun attach(view: SurfaceView) = Unit
        override fun detach() = Unit
        override fun setWindowSize(width: Int, height: Int) = Unit
        override fun open(request: PlayRequest) {
            opened += request
            state.value = EngineState.Opening
            hasPicture.value = false
        }
        val pauses = CopyOnWriteArrayList<Boolean>()
        override fun play() {
            pauses += false
        }
        override fun pause() {
            pauses += true
        }
        override fun stop() {
            stops++
            state.value = EngineState.Idle
            hasPicture.value = false
        }
        override fun setVolume(percent: Int) {
            lastVolume = percent
        }
        override fun release() = Unit
    }

    private val provider = MockWebServer()
    private val engine = FakeEngine()
    private val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + thread)
    private lateinit var hub: StreamHub
    private lateinit var manager: PlayerManager
    private val budget = ConnectionBudget("home", maxConnections = 1)
    private val backupBudget = ConnectionBudget("backup", maxConnections = 1)

    @Before fun setUp() {
        provider.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath.endsWith("/refused.ts") -> MockResponse(code = 403)
                // HLS behind an address that doesn't say so.
                "/hls/" in request.url.encodedPath -> MockResponse.Builder()
                    .addHeader("Content-Type", "application/vnd.apple.mpegurl")
                    .body("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nsegment1.ts\n")
                    .build()
                // A live stream that keeps going: some packets, then nothing new for a while.
                else -> MockResponse.Builder()
                    .addHeader("Content-Type", "video/mp2t")
                    .body(Buffer().write(ByteArray(188 * 20) { if (it % 188 == 0) 0x47 else 0x11 }))
                    .throttleBody(188 * 10L, 10, TimeUnit.SECONDS)
                    .build()
            }
        }
        provider.start()
        hub = StreamHub(OkHttpClient(), HubConfig(noDataTimeoutMs = 20_000, reconnectDelaysMs = listOf(0)))
        val resolver = StreamResolver { channelId -> StreamTarget(channelId, provider.url("/live/viewer/s3cret/$channelId.ts").toString(), budget = budget) }
        manager = PlayerManager(engine, hub, resolver, { PlaybackPrefs() }, scope)
    }

    @After fun tearDown() {
        scope.cancel()
        thread.close()
        hub.close()
        provider.close()
    }

    private fun onPlayer(block: () -> Unit) = runBlocking { withContext(thread) { block() } }

    private fun <T> await(flow: StateFlow<T>, predicate: (T) -> Boolean): T = runBlocking { withTimeout(10_000) { flow.first(predicate) } }

    @Test fun playsThroughTheHubAndKeepsTheCredentialsFromTheEngine() {
        onPlayer { manager.show("ch105", muted = true) }

        await(manager.status) { it == LiveStatus.Opening("ch105") }
        val request = runBlocking { withTimeout(10_000) { while (engine.opened.isEmpty()) kotlinx.coroutines.delay(10) }; engine.opened.single() }
        assertTrue(request.url, request.url.startsWith("http://127.0.0.1:${hub.port}/live/"))
        assertTrue("s3cret" !in request.url)
        assertEquals(0, engine.lastVolume)

        onPlayer {
            engine.state.value = EngineState.Playing
            engine.hasPicture.value = true
        }
        assertEquals(LiveStatus.Playing("ch105"), await(manager.status) { it is LiveStatus.Playing })
        assertEquals("ch105", await(manager.picture) { it != null })
    }

    @Test fun theSameChannelCarriesOnWithoutReopening() {
        onPlayer { manager.show("ch105", muted = true) }
        runBlocking { withTimeout(10_000) { while (engine.opened.isEmpty()) kotlinx.coroutines.delay(10) } }

        // Home's muted picture goes full screen in the player, with sound.
        onPlayer { manager.show("ch105", muted = false) }

        assertEquals(1, engine.opened.size)
        assertEquals(100, engine.lastVolume)
    }

    @Test fun zappingFreesTheOldConnectionFirst() {
        onPlayer { manager.show("ch105", muted = false) }
        runBlocking { withTimeout(10_000) { while (engine.opened.isEmpty()) kotlinx.coroutines.delay(10) } }

        onPlayer { manager.show("ch107", muted = false) }

        runBlocking { withTimeout(10_000) { while (engine.opened.size < 2) kotlinx.coroutines.delay(10) } }
        assertEquals(LiveStatus.Opening("ch107"), manager.status.value)
        assertEquals(1, hub.connectionsInUse("home"))
    }

    @Test fun aRefusedStreamFailsAndStops() {
        val refused = StreamResolver { StreamTarget(it, provider.url("/live/refused.ts").toString()) }
        val manager = PlayerManager(engine, hub, refused, { PlaybackPrefs() }, scope)

        onPlayer { manager.show("ch110", muted = false) }

        assertEquals(LiveStatus.Failed("ch110", LiveFailure.Refused), await(manager.status) { it is LiveStatus.Failed })
        assertTrue(engine.stops > 0)
    }

    @Test fun dataArrivingWhileTheEngineFailsIsTheEnginesFault() {
        onPlayer { manager.show("ch105", muted = false) }
        runBlocking { withTimeout(10_000) { while (engine.opened.isEmpty()) kotlinx.coroutines.delay(10) } }

        onPlayer { engine.state.value = EngineState.Failed("decoder") }

        assertEquals(LiveStatus.Failed("ch105", LiveFailure.CantPlay), await(manager.status) { it is LiveStatus.Failed })
        assertEquals(0, hub.connectionsInUse("home"))
    }

    @Test fun stoppingAtOnceFreesTheConnectionAndComingBackPlaysAgain() {
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)

        onPlayer { manager.standby(graceMs = 0) }
        assertEquals(LiveStatus.Idle, manager.status.value)
        assertEquals(0, hub.connectionsInUse("home"))

        onPlayer { manager.resume() }
        waitForOpens(2)
        assertEquals(1, hub.connectionsInUse("home"))
        assertEquals(100, engine.lastVolume)
    }

    @Test fun comingBackWithinTheGraceKeepsTheConnection() {
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)

        onPlayer { manager.standby(graceMs = 60_000) }
        // The picture and sound stop at once; the provider connection stays for the minute (5.20).
        assertEquals(LiveStatus.Idle, manager.status.value)
        assertTrue(engine.stops > 0)
        assertEquals(1, hub.connectionsInUse("home"))

        onPlayer { manager.resume() }
        waitForOpens(2)
        // The same hub session, so the same provider connection, joined again at the live edge.
        assertEquals(engine.opened[0].url, engine.opened[1].url)
        assertEquals(1, hub.connectionsInUse("home"))
    }

    @Test fun theConnectionGoesWhenTheGraceRunsOut() {
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)

        onPlayer { manager.standby(graceMs = 100) }

        runBlocking { withTimeout(10_000) { while (hub.connectionsInUse("home") > 0) kotlinx.coroutines.delay(10) } }
        onPlayer { manager.resume() }
        waitForOpens(2)
        assertEquals(1, hub.connectionsInUse("home"))
    }

    @Test fun zappingAwayDuringTheGraceFreesTheKeptConnectionFirst() {
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        onPlayer { manager.standby(graceMs = 60_000) }

        onPlayer { manager.show("ch107", muted = false) }

        waitForOpens(2)
        // One connection allowed, and it's free for the new channel.
        assertEquals(LiveStatus.Opening("ch107"), manager.status.value)
        assertEquals(1, hub.connectionsInUse("home"))
    }

    @Test fun anAddressThatTurnsOutToBeAPlaylistPlaysFromTheProvider() {
        val hls = StreamResolver { StreamTarget(it, provider.url("/live/hls/$it").toString(), headers = mapOf("User-Agent" to "PlasmaTest/1.0")) }
        val manager = PlayerManager(engine, hub, hls, { PlaybackPrefs() }, scope)

        onPlayer { manager.show("ch120", muted = false) }

        waitForOpens(2)
        val direct = engine.opened[1]
        assertEquals(provider.url("/live/hls/ch120").toString(), direct.url)
        assertEquals("PlasmaTest/1.0", direct.userAgent)
        assertEquals(LiveStatus.Opening("ch120"), manager.status.value)
    }

    @Test fun anAddressTheHubCantOpenPlaysFromTheProvider() {
        val multicast = StreamResolver { StreamTarget(it, "udp://@239.0.0.1:1234") }
        val manager = PlayerManager(engine, hub, multicast, { PlaybackPrefs() }, scope)

        onPlayer { manager.show("ch130", muted = false) }

        waitForOpens(2)
        assertEquals("udp://@239.0.0.1:1234", engine.opened[1].url)
    }

    @Test fun aLiveStreamThatEndsSaysTheSourceStopped() {
        val hls = StreamResolver { StreamTarget(it, provider.url("/live/$it.m3u8").toString(), throughHub = false) }
        val manager = PlayerManager(engine, hub, hls, { PlaybackPrefs() }, scope)
        onPlayer { manager.show("ch140", muted = false) }
        waitForOpens(1)

        onPlayer { engine.state.value = EngineState.Ended }

        assertEquals(LiveStatus.Failed("ch140", LiveFailure.SourceUnavailable), await(manager.status) { it is LiveStatus.Failed })
    }

    @Test fun aProgramFromTheArchivePlaysToItsEndAndPauses() {
        val key = StreamKeys.archive("ch105", java.time.Instant.parse("2026-10-21T19:45:00Z"))
        val opened = CopyOnWriteArrayList<String>()
        val archive = StreamResolver {
            opened += it
            StreamTarget(it, provider.url("/timeshift/viewer/s3cret/135/2026-10-21:20-45/105.ts").toString(), throughHub = false)
        }
        val manager = PlayerManager(engine, hub, archive, { PlaybackPrefs() }, scope)
        onPlayer { manager.show(key, muted = false) }
        waitForOpens(1)
        assertEquals(listOf(key), opened)
        assertFalse(engine.opened[0].live)

        onPlayer { engine.state.value = EngineState.Playing }
        await(manager.status) { it == LiveStatus.Playing(key) }
        onPlayer { manager.setPaused(true) }
        onPlayer { engine.state.value = EngineState.Paused }
        // Paused, it's still the program on screen.
        assertEquals(LiveStatus.Playing(key), manager.status.value)
        onPlayer { manager.setPaused(false) }
        assertEquals(listOf(false, true, false), engine.pauses.toList())

        onPlayer { engine.state.value = EngineState.Ended }
        assertEquals(LiveStatus.Ended(key), await(manager.status) { it is LiveStatus.Ended })
    }

    @Test fun liveTvDoesntPauseYet() {
        val manager = PlayerManager(engine, hub, { StreamTarget(it, provider.url("/live/$it.ts").toString()) }, { PlaybackPrefs() }, scope)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        onPlayer { manager.setPaused(true) }

        assertEquals(listOf(false), engine.pauses.toList())
    }

    @Test fun archiveKeysNameTheirChannelAndStart() {
        val start = java.time.Instant.parse("2026-10-21T19:45:00Z")
        val key = StreamKeys.archive("home:105", start)

        assertEquals(start, StreamKeys.archiveStart(key))
        assertEquals("home:105", StreamKeys.channelOf(key))
        // A channel whose name has an @ in it is still live TV.
        assertEquals(null, StreamKeys.archiveStart("home:News@24"))
        assertEquals("home:News@24", StreamKeys.channelOf("home:News@24"))
    }

    @Test fun zappingFullScreenKeepsTheFrameRateUntilTheNextIsKnown() {
        val asked = CopyOnWriteArrayList<Float?>()
        val manager = PlayerManager(engine, hub, { StreamTarget(it, provider.url("/live/$it.ts").toString()) }, { PlaybackPrefs() }, scope) { _, rate, _ -> asked += rate }
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        onPlayer { engine.video.value = VideoInfo(1920, 1080, frameRate = 25f) }
        onPlayer { manager.setFullScreen(true) }
        runBlocking { withTimeout(10_000) { while (asked.lastOrNull() != 25f) kotlinx.coroutines.delay(10) } }
        val before = asked.size

        // The next channel's rate is unknown for a moment, then the same.
        onPlayer { engine.video.value = null }
        onPlayer { engine.video.value = VideoInfo(1920, 1080, frameRate = 25f) }
        onPlayer { engine.video.value = VideoInfo(1280, 720, frameRate = 50f) }
        runBlocking { withTimeout(10_000) { while (asked.lastOrNull() != 50f) kotlinx.coroutines.delay(10) } }

        // One switch, to 50, and never back to nothing in between.
        assertEquals(listOf(50f), asked.drop(before))
    }

    @Test fun matchesTheFrameRateOnlyFullScreen() {
        val asked = CopyOnWriteArrayList<Float?>()
        val manager = PlayerManager(engine, hub, { StreamTarget(it, provider.url("/live/$it.ts").toString()) }, { PlaybackPrefs() }, scope) { _, rate, _ -> asked += rate }
        onPlayer { manager.show("ch105", muted = true) }
        waitForOpens(1)

        onPlayer { engine.video.value = VideoInfo(1920, 1080, frameRate = 25f) }
        onPlayer { manager.setFullScreen(true) }
        runBlocking { withTimeout(10_000) { while (asked.lastOrNull() != 25f) kotlinx.coroutines.delay(10) } }
        onPlayer { manager.setFullScreen(false) }
        runBlocking { withTimeout(10_000) { while (asked.lastOrNull() != null) kotlinx.coroutines.delay(10) } }

        // Home's muted picture asks for nothing; the full-screen player asks for the video's rate.
        assertEquals(listOf(null, 25f, null), asked.distinctConsecutive())
    }

    private fun waitForOpens(count: Int) = runBlocking { withTimeout(10_000) { while (engine.opened.size < count) kotlinx.coroutines.delay(10) } }

    private fun <T> List<T>.distinctConsecutive(): List<T> = filterIndexed { i, value -> i == 0 || this[i - 1] != value }

    @Test fun aSourcesOwnBufferAndSoundWinOverSettings() {
        val own = StreamResolver { channelId ->
            StreamTarget(
                channelId, provider.url("/live/viewer/s3cret/$channelId.ts").toString(), budget = budget,
                buffer = BufferSize.ExtraLarge, audioOutput = AudioOutput.DecodeToPcm,
            )
        }
        val settings = PlaybackPrefs(buffer = BufferSize.Small, audioOutput = AudioOutput.Passthrough, decoding = VideoDecoding.Software)
        val manager = PlayerManager(engine, hub, own, { settings }, scope)

        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        // What the source doesn't set comes from Settings.
        with(engine.opened[0]) {
            assertEquals(BufferSize.ExtraLarge, buffer)
            assertEquals(AudioOutput.DecodeToPcm, audioOutput)
            assertEquals(VideoDecoding.Software, decoding)
        }

        // Coming back within the grace keeps the source's choices with the connection.
        onPlayer { manager.standby(graceMs = 60_000) }
        onPlayer { manager.resume() }
        waitForOpens(2)
        assertEquals(BufferSize.ExtraLarge, engine.opened[1].buffer)
        assertEquals(AudioOutput.DecodeToPcm, engine.opened[1].audioOutput)
    }

    /** A channel on two sources, the first of which turns the box down (brief 5.1). */
    private fun mergedResolver(firstRefuses: Boolean = true) = object : StreamResolver {
        override suspend fun resolve(key: String) = when {
            key == "home:105" && firstRefuses -> StreamTarget(key, provider.url("/live/refused.ts").toString())
            key.startsWith("backup:") -> StreamTarget(key, provider.url("/live/viewer/s3cret/$key.ts").toString(), budget = backupBudget)
            else -> StreamTarget(key, provider.url("/live/viewer/s3cret/$key.ts").toString(), budget = budget)
        }

        override suspend fun candidates(key: String) = if (key == "ch105") listOf("home:105", "backup:105") else listOf(key)
    }

    @Test fun aSourceThatFailsHandsTheChannelToTheNext() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = PlayerManager(engine, hub, mergedResolver(), { PlaybackPrefs() }, scope, onEvent = { events += it })

        onPlayer { manager.show("ch105", muted = false) }

        await(manager.playingFrom) { it?.candidate == "backup:105" }
        // The home source's attempt, then the backup's.
        waitForOpens(2)
        // The refusal never shows: the channel opens from the backup, through the hub.
        assertEquals(LiveStatus.Opening("ch105"), manager.status.value)
        assertEquals(PlayingFrom("ch105", listOf("home:105", "backup:105"), "backup:105"), manager.playingFrom.value)
        assertEquals(listOf(PlayerEvent.SourceFailed("ch105", "home:105", "backup:105")), events.toList())
        assertTrue(engine.opened.last().url.startsWith("http://127.0.0.1:${hub.port}/live/"))

        // Coming back within the grace carries on from the backup, on the connection it kept.
        onPlayer { manager.standby(graceMs = 60_000) }
        assertNull(manager.playingFrom.value)
        onPlayer { manager.resume() }
        waitForOpens(3)
        assertEquals("backup:105", manager.playingFrom.value?.candidate)
        assertEquals(1, events.size)
    }

    @Test fun aCandidateIsReportedWhenItPlaysAndAgainOnceItHasPlayedAWhile() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = PlayerManager(engine, hub, mergedResolver(firstRefuses = false), { PlaybackPrefs() }, scope, onEvent = { events += it }, playedLongMs = 300)

        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        onPlayer { engine.state.value = EngineState.Playing }

        runBlocking { withTimeout(10_000) { while (events.size < 2) kotlinx.coroutines.delay(10) } }
        assertEquals(listOf(PlayerEvent.Playing("ch105", "home:105"), PlayerEvent.PlayedLong("ch105", "home:105")), events.toList())
    }

    @Test fun zappingAwayBeforeThenSaysNothingMore() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = PlayerManager(engine, hub, mergedResolver(firstRefuses = false), { PlaybackPrefs() }, scope, onEvent = { events += it }, playedLongMs = 500)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        onPlayer { engine.state.value = EngineState.Playing }
        runBlocking { withTimeout(10_000) { while (events.isEmpty()) kotlinx.coroutines.delay(10) } }

        onPlayer { manager.show("ch107", muted = false) }
        Thread.sleep(800)

        assertTrue(events.none { it is PlayerEvent.PlayedLong && it.channelId == "ch105" })
    }

    @Test fun aSourceWithNoConnectionLeftHandsTheChannelToTheNext() {
        // Something else holds the home account's one connection.
        val other = hub.open(UpstreamRequest(provider.url("/live/viewer/s3cret/other.ts").toString(), budget = budget))
        val manager = PlayerManager(engine, hub, mergedResolver(firstRefuses = false), { PlaybackPrefs() }, scope)

        onPlayer { manager.show("ch105", muted = false) }

        await(manager.playingFrom) { it?.candidate == "backup:105" }
        waitForOpens(1)
        assertEquals(LiveStatus.Opening("ch105"), manager.status.value)
        other.close()
    }

    @Test fun withoutFailoverTheFailureShows() {
        val manager = PlayerManager(engine, hub, mergedResolver(), { PlaybackPrefs(failOver = false) }, scope)

        onPlayer { manager.show("ch105", muted = false) }

        assertEquals(LiveStatus.Failed("ch105", LiveFailure.Refused), await(manager.status) { it is LiveStatus.Failed })
        assertEquals("home:105", manager.playingFrom.value?.candidate)
    }

    @Test fun switchSourcePlaysTheChosenCandidateFirst() {
        val manager = PlayerManager(engine, hub, mergedResolver(firstRefuses = false), { PlaybackPrefs() }, scope)
        onPlayer { manager.show("ch105", muted = false) }
        await(manager.playingFrom) { it?.candidate == "home:105" }
        waitForOpens(1)

        onPlayer { manager.switchSource("backup:105") }

        await(manager.playingFrom) { it?.candidate == "backup:105" }
        waitForOpens(2)
        assertEquals(LiveStatus.Opening("ch105"), manager.status.value)
        // The list keeps its order: Switch Source shows the same list with the other one checked.
        assertEquals(listOf("home:105", "backup:105"), manager.playingFrom.value?.candidates)
    }

    @Test fun aChannelWithNoStreamSaysSo() {
        val nowhere = StreamResolver { null }
        val manager = PlayerManager(engine, hub, nowhere, { PlaybackPrefs() }, scope)

        onPlayer { manager.show("ch999", muted = false) }

        assertEquals(LiveStatus.Failed("ch999", LiveFailure.NoStream), await(manager.status) { it is LiveStatus.Failed })
    }
}
