// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.view.SurfaceView
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.BufferSize
import ca.gureet.plasma.core.model.IfStreamFails
import ca.gureet.plasma.core.model.ChannelTracks
import ca.gureet.plasma.core.model.ORIGINAL_LANGUAGE
import ca.gureet.plasma.core.model.SubtitleMode
import ca.gureet.plasma.core.model.TrackPick
import ca.gureet.plasma.core.model.VideoDecoding
import ca.gureet.plasma.stream.ConnectionBudget
import ca.gureet.plasma.stream.HubConfig
import ca.gureet.plasma.stream.RewindMode
import ca.gureet.plasma.stream.RewindSettings
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
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
    private class FakeEngine(override val id: EngineId = EngineId.Vlc) : PlayerEngine {
        override val state = MutableStateFlow<EngineState>(EngineState.Idle)
        override val video = MutableStateFlow<VideoInfo?>(null)
        override val hasPicture = MutableStateFlow(false)
        override val audio = MutableStateFlow<AudioInfo?>(null)
        override val tracks = MutableStateFlow(MediaTracks())
        override val position = MutableStateFlow(0L)
        override val length = MutableStateFlow(0L)
        override val cues = MutableStateFlow<List<SubtitleCue>>(emptyList())
        val opened = CopyOnWriteArrayList<PlayRequest>()

        /** Each place it was asked to jump to. */
        val seeks = CopyOnWriteArrayList<Long>()

        /** Each sound and subtitle track picked: "audio:2", "subtitles:4", "subtitles:off". */
        val picked = CopyOnWriteArrayList<String>()

        /** Each level set, and the level each stream opened with. */
        val levels = CopyOnWriteArrayList<Int>()
        val openedLevels = CopyOnWriteArrayList<Int>()
        var stops = 0
        var lastVolume = -1

        var attached = 0
        var detached = 0
        override fun attach(view: SurfaceView, subtitles: SurfaceView?) {
            attached++
        }
        override fun detach() {
            detached++
        }
        override fun setWindowSize(width: Int, height: Int) = Unit
        override fun open(request: PlayRequest) {
            opened += request
            openedLevels += levels.lastOrNull() ?: 0
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
        override fun seekTo(ms: Long) {
            seeks += ms
            position.value = ms
        }
        override fun setVolume(percent: Int) {
            lastVolume = percent
        }
        override fun setLevel(db: Int) {
            levels += db
        }
        override fun selectAudio(id: String) {
            picked += "audio:$id"
            tracks.value = tracks.value.copy(audioId = id)
        }
        override fun selectSubtitles(id: String?) {
            picked += "subtitles:${id ?: "off"}"
            tracks.value = tracks.value.copy(subtitleId = id)
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

    /** How many times "/once/" was asked for: it plays once, a few seconds, then refuses. */
    private val onceAsked = AtomicInteger()

    private fun stream(packets: Int = 20) = MockResponse.Builder()
        .addHeader("Content-Type", "video/mp2t")
        .body(Buffer().write(ByteArray(188 * packets) { if (it % 188 == 0) 0x47 else 0x11 }))

    @Before fun setUp() {
        provider.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath.endsWith("/refused.ts") -> MockResponse(code = 403)
                // A provider that takes its time to answer.
                "/slow/" in request.url.encodedPath -> stream().headersDelay(1_500, TimeUnit.MILLISECONDS).throttleBody(188 * 10L, 10, TimeUnit.SECONDS).build()
                "/once/" in request.url.encodedPath -> if (onceAsked.incrementAndGet() == 1) {
                    stream(packets = 40).throttleBody(188 * 10L, 1, TimeUnit.SECONDS).build()
                } else {
                    MockResponse(code = 403)
                }
                // A live stream that keeps coming: ten packets every tenth of a second.
                "/steady/" in request.url.encodedPath -> stream(packets = 3_000).throttleBody(188 * 10L, 100, TimeUnit.MILLISECONDS).build()
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

    @Test fun aSourcesOwnUseStreamHubWinsOverSettings() {
        val direct = StreamResolver { StreamTarget(it, provider.url("/live/viewer/s3cret/$it.ts").toString(), useHub = false) }
        val off = PlayerManager(engine, hub, direct, { PlaybackPrefs() }, scope)
        onPlayer { off.show("ch105", muted = false) }
        waitForOpens(1)
        // Off for this source: straight to the engine, though Settings has the hub on.
        assertEquals(provider.url("/live/viewer/s3cret/ch105.ts").toString(), engine.opened.last().url)
        onPlayer { off.release() }

        val hubbed = StreamResolver { StreamTarget(it, provider.url("/live/viewer/s3cret/$it.ts").toString(), useHub = true) }
        val on = PlayerManager(engine, hub, hubbed, { PlaybackPrefs(throughHub = false) }, scope)
        onPlayer { on.show("ch106", muted = false) }
        waitForOpens(2)
        // On for this source: through the hub, though Settings has it off.
        assertTrue(engine.opened.last().url.startsWith("http://127.0.0.1:${hub.port}/live/"))
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

    @Test fun aSourcePickedForAChannelNotOnScreenGoesFirstWhenItPlays() {
        val manager = PlayerManager(engine, hub, mergedResolver(firstRefuses = false), { PlaybackPrefs() }, scope)
        onPlayer { manager.show("ch101", muted = false) }
        waitForOpens(1)

        // Live TV's channel options, on a channel the preview hasn't reached yet.
        onPlayer { manager.switchSource("ch105", "backup:105") }
        assertEquals("ch101", manager.playingFrom.value?.key)

        onPlayer { manager.show("ch105", muted = false) }
        await(manager.playingFrom) { it?.key == "ch105" }
        assertEquals("backup:105", manager.playingFrom.value?.candidate)

        // Only that once: the next tune starts in the channel's own order.
        onPlayer { manager.show("ch101", muted = false) }
        await(manager.playingFrom) { it?.key == "ch101" }
        onPlayer { manager.show("ch105", muted = false) }
        await(manager.playingFrom) { it?.key == "ch105" }
        assertEquals("home:105", manager.playingFrom.value?.candidate)
    }

    @Test fun aSourcePickedForTheChannelOnScreenPlaysAtOnce() {
        val manager = PlayerManager(engine, hub, mergedResolver(firstRefuses = false), { PlaybackPrefs() }, scope)
        onPlayer { manager.show("ch105", muted = false) }
        await(manager.playingFrom) { it?.candidate == "home:105" }

        onPlayer { manager.switchSource("ch105", "backup:105") }

        await(manager.playingFrom) { it?.candidate == "backup:105" }
    }

    // ---- Engines: mpv as the backup, fallback and Switch Player (brief 5.4.3) ----------------------

    private val mpv = FakeEngine(EngineId.Mpv)
    private val live = StreamResolver { channelId -> StreamTarget(channelId, provider.url("/live/viewer/s3cret/$channelId.ts").toString(), budget = budget) }

    private fun withMpv(
        prefs: PlaybackPrefs = PlaybackPrefs(),
        resolver: StreamResolver = live,
        events: MutableList<PlayerEvent> = CopyOnWriteArrayList(),
        engineWorkedMs: Long = 30_000,
        mpvLoads: Boolean = true,
        stallWindowMs: Long = 60_000,
    ) = PlayerManager(
        engine, hub, resolver, { prefs }, scope, onEvent = { events += it },
        backups = mapOf(EngineId.Mpv to { if (mpvLoads) mpv else null }), engineWorkedMs = engineWorkedMs, stallWindowMs = stallWindowMs,
    )

    /** The hub has the provider's data by now. */
    private fun waitForData() {
        runBlocking { withTimeout(10_000) { while (provider.requestCount == 0) kotlinx.coroutines.delay(10) } }
        Thread.sleep(300)
    }

    private fun waitForOpens(engine: FakeEngine, count: Int) = runBlocking { withTimeout(10_000) { while (engine.opened.size < count) kotlinx.coroutines.delay(10) } }

    @Test fun whatVlcCantPlayMpvPlaysOnTheSameConnection() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = withMpv(events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        waitForData()

        onPlayer { engine.state.value = EngineState.Failed("decoder") }

        waitForOpens(mpv, 1)
        // The same stream from the hub: the provider sees no new connection.
        assertEquals(engine.opened.single().url, mpv.opened.single().url)
        assertEquals(1, provider.requestCount)
        assertEquals(VideoDecoding.Hardware, mpv.opened.single().decoding)
        assertEquals(EngineId.Mpv, await(manager.engineId) { it == EngineId.Mpv })
        assertTrue(engine.detached > 0 && mpv.attached == 0)
        assertEquals(listOf(PlayerEvent.SwitchedEngine("ch105", EngineId.Vlc, EngineId.Mpv)), events.filterIsInstance<PlayerEvent.SwitchedEngine>())
        // VLC's failure never shows.
        assertEquals(LiveStatus.Opening("ch105"), manager.status.value)
    }

    @Test fun mpvTriesCopyBackBeforeTheErrorShows() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = withMpv(events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        waitForData()
        onPlayer { engine.state.value = EngineState.Failed("decoder") }
        waitForOpens(mpv, 1)

        onPlayer { mpv.state.value = EngineState.Failed("mediacodec") }

        waitForOpens(mpv, 2)
        assertEquals(VideoDecoding.HardwareCopyBack, mpv.opened[1].decoding)
        assertEquals(1, events.filterIsInstance<PlayerEvent.SwitchedEngine>().size)
        onPlayer { mpv.state.value = EngineState.Failed("again") }
        assertEquals(LiveStatus.Failed("ch105", LiveFailure.CantPlay), await(manager.status) { it is LiveStatus.Failed })
    }

    @Test fun noPictureInTimeIsTheEnginesFault() {
        val manager = withMpv(prefs = PlaybackPrefs(connectionTimeoutMs = 400, buffer = BufferSize.Minimal))
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)

        // Sound, but no picture.
        onPlayer { engine.state.value = EngineState.Playing }

        waitForOpens(mpv, 1)
        assertEquals(engine.opened.single().url, mpv.opened.single().url)
    }

    @Test fun thePictureClockStartsWithTheData() {
        val slow = StreamResolver { StreamTarget(it, provider.url("/slow/live/$it.ts").toString(), budget = budget) }
        val manager = withMpv(prefs = PlaybackPrefs(connectionTimeoutMs = 300, buffer = BufferSize.Minimal), resolver = slow)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)

        // The provider answers after 1.5 s, longer than the timeout and the buffer together; the
        // picture comes soon after its data.
        Thread.sleep(1_700)
        onPlayer {
            engine.state.value = EngineState.Playing
            engine.hasPicture.value = true
        }

        Thread.sleep(1_500)
        assertTrue(mpv.opened.isEmpty())
        assertEquals(LiveStatus.Playing("ch105"), manager.status.value)
    }

    @Test fun aPictureLostForAMomentIsntTheEnginesFault() {
        val manager = withMpv(prefs = PlaybackPrefs(connectionTimeoutMs = 200, buffer = BufferSize.Minimal))
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        waitForData()
        onPlayer {
            engine.state.value = EngineState.Playing
            engine.hasPicture.value = true
        }
        Thread.sleep(1_000)

        // VLC sets its video up again, for a new format or after a reconnect.
        onPlayer { engine.hasPicture.value = false }

        Thread.sleep(1_000)
        assertTrue(mpv.opened.isEmpty())
    }

    @Test fun aHandoffCutShortStillFinishes() {
        // Arena Sports 1 plays in VLC; Courtside+ has mpv of its own.
        val ownEngines = object : StreamResolver {
            override suspend fun resolve(key: String) = StreamTarget(key, provider.url("/live/viewer/s3cret/$key.ts").toString(), budget = budget)

            override suspend fun engineFor(key: String) = if (key == "ch108") EngineId.Mpv else null
        }
        val manager = withMpv(resolver = ownEngines)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)

        // Zapping on through Courtside+ while VLC hands it to mpv.
        onPlayer { manager.show("ch108", muted = false) }
        runBlocking { withTimeout(10_000) { while (engine.detached == 0) kotlinx.coroutines.delay(5) } }
        onPlayer { manager.show("ch109", muted = false) }

        // mpv took over, then handed back: VLC has the surface for the next channel.
        waitForOpens(2)
        assertEquals(LiveStatus.Opening("ch109"), manager.status.value)
        assertTrue(mpv.opened.isEmpty())
        assertEquals(1, mpv.detached)
        // The engine's id follows the engine a moment later.
        assertEquals(EngineId.Vlc, await(manager.engineId) { it == EngineId.Vlc })
    }

    @Test fun standbyRightAfterAZapLetsTheChannelBeforesConnectionGo() {
        val gate = CompletableDeferred<Unit>()
        val slow = StreamResolver { key ->
            if (key == "ch108") gate.await()
            StreamTarget(key, provider.url("/live/viewer/s3cret/$key.ts").toString(), budget = budget)
        }
        val manager = PlayerManager(engine, hub, slow, { PlaybackPrefs() }, scope)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        waitForData()

        // The TV goes off while Courtside+ is still being looked up.
        onPlayer { manager.show("ch108", muted = false) }
        onPlayer { manager.standby(graceMs = 60_000) }
        gate.complete(Unit)
        onPlayer { manager.resume() }

        // Courtside+ gets its own connection, not Arena Sports 1's.
        waitForOpens(2)
        assertNotEquals(engine.opened[0].url, engine.opened[1].url)
        runBlocking { withTimeout(10_000) { while (provider.requestCount < 2) kotlinx.coroutines.delay(10) } }
        assertEquals(listOf("/live/viewer/s3cret/ch105.ts", "/live/viewer/s3cret/ch108.ts"), (1..2).map { provider.takeRequest().url.encodedPath })
    }

    @Test fun aChannelTakenUpAgainStillFailsOver() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val merged = object : StreamResolver {
            override suspend fun resolve(key: String) = when {
                key.startsWith("backup:") -> StreamTarget(key, provider.url("/live/viewer/s3cret/$key.ts").toString(), budget = backupBudget)
                else -> StreamTarget(key, provider.url("/once/live/$key.ts").toString(), budget = budget)
            }

            override suspend fun candidates(key: String) = listOf("home:105", "backup:105")
        }
        val manager = PlayerManager(engine, hub, merged, { PlaybackPrefs() }, scope, onEvent = { events += it })
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        waitForData()
        onPlayer { manager.standby(graceMs = 60_000) }
        onPlayer { manager.resume() }
        waitForOpens(2)
        assertEquals(engine.opened[0].url, engine.opened[1].url)

        // Home's stream ends a few seconds in, and it refuses to start again: the backup takes over.
        await(manager.playingFrom) { it?.candidate == "backup:105" }
        assertEquals(PlayerEvent.SourceFailed("ch105", "home:105", "backup:105"), events.filterIsInstance<PlayerEvent.SourceFailed>().single())
    }

    // ---- A channel's own sound (brief 5.4.6) -------------------------------------------------------

    private val ownSound = object : StreamResolver {
        override suspend fun resolve(key: String) =
            StreamTarget(key, provider.url("/live/viewer/s3cret/$key.ts").toString(), budget = budget, audioOutput = AudioOutput.Passthrough)

        override suspend fun audioFor(key: String) = when (key) {
            "ch101" -> ChannelAudio(levelDb = 3)
            "ch104" -> ChannelAudio(levelDb = -2, output = AudioOutput.DecodeToPcm)
            else -> null
        }
    }

    @Test fun aChannelsLevelIsInBeforeItsFirstSound() {
        val manager = PlayerManager(engine, hub, ownSound, { PlaybackPrefs() }, scope)

        onPlayer { manager.show("ch101", muted = false) }
        waitForOpens(1)
        onPlayer { manager.show("ch107", muted = false) }
        waitForOpens(2)

        // Northline News opens at +3 dB; the next channel, with none of its own, at 0 dB.
        assertEquals(listOf(3, 0), engine.openedLevels.toList())
    }

    @Test fun decodeThisChannelGoesOverTheSourcesOutput() {
        val manager = PlayerManager(engine, hub, ownSound, { PlaybackPrefs() }, scope)

        onPlayer { manager.show("ch104", muted = false) }
        waitForOpens(1)
        onPlayer { manager.show("ch107", muted = false) }
        waitForOpens(2)

        assertEquals(listOf(AudioOutput.DecodeToPcm, AudioOutput.Passthrough), engine.opened.map { it.audioOutput })
        assertEquals(-2, engine.openedLevels.first())
    }

    @Test fun theBarChangesTheChannelPlayingOnly() {
        val manager = PlayerManager(engine, hub, ownSound, { PlaybackPrefs() }, scope)
        onPlayer { manager.show("ch101", muted = false) }
        waitForOpens(1)

        onPlayer { manager.setLevel("ch101", -4) }
        onPlayer { manager.setLevel("ch107", 5) }

        assertEquals(-4, engine.levels.last())
        assertFalse(5 in engine.levels)
    }

    @Test fun aRadioChannelHasNoPictureToWaitFor() {
        val radio = StreamResolver { StreamTarget(it, provider.url("/live/viewer/s3cret/$it.ts").toString(), budget = budget, radio = true) }
        val manager = withMpv(prefs = PlaybackPrefs(connectionTimeoutMs = 200), resolver = radio)
        onPlayer { manager.show("ch201", muted = false) }
        waitForOpens(1)

        onPlayer { engine.state.value = EngineState.Playing }

        Thread.sleep(700)
        assertTrue(mpv.opened.isEmpty())
        assertEquals(LiveStatus.Playing("ch201"), manager.status.value)
    }

    @Test fun showAnErrorMeansNoOtherEngine() {
        val manager = withMpv(prefs = PlaybackPrefs(ifStreamFails = IfStreamFails.ShowError))
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        waitForData()

        onPlayer { engine.state.value = EngineState.Failed("decoder") }

        assertEquals(LiveStatus.Failed("ch105", LiveFailure.CantPlay), await(manager.status) { it is LiveStatus.Failed })
        assertTrue(mpv.opened.isEmpty())
    }

    @Test fun anEngineThatWontLoadIsLeftOut() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = withMpv(events = events, mpvLoads = false)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        waitForData()

        onPlayer { engine.state.value = EngineState.Failed("decoder") }

        assertEquals(LiveStatus.Failed("ch105", LiveFailure.CantPlay), await(manager.status) { it is LiveStatus.Failed })
        assertTrue(events.none { it is PlayerEvent.SwitchedEngine })
        assertEquals(null, manager.nextEngine())
    }

    @Test fun mpvSetInSettingsButMissingPlaysWithVlc() {
        val manager = withMpv(prefs = PlaybackPrefs(engine = EngineId.Mpv), mpvLoads = false)

        onPlayer { manager.show("ch105", muted = false) }

        waitForOpens(1)
        assertEquals(EngineId.Vlc, manager.engineId.value)
    }

    @Test fun switchPlayerReopensTheSameStreamInTheOtherEngine() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = withMpv(events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        assertEquals(EngineId.Mpv, manager.nextEngine())

        onPlayer { manager.switchEngine() }

        waitForOpens(mpv, 1)
        assertEquals(engine.opened.single().url, mpv.opened.single().url)
        assertEquals(listOf(PlayerEvent.EngineChosen("ch105", EngineId.Mpv)), events.toList())
        assertEquals(EngineId.Vlc, await(manager.engineId) { it == EngineId.Mpv }.let { manager.nextEngine() })

        onPlayer { manager.switchEngine() }

        waitForOpens(2)
        assertEquals(engine.opened[0].url, engine.opened[1].url)
        assertEquals(1, provider.requestCount)
    }

    @Test fun theSubtitlesOnScreenAreTheEnginePlayings() {
        val manager = withMpv(prefs = PlaybackPrefs(tunneling = true))
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        // Tunneled Playback reaches the engine, for the Android player.
        assertTrue(engine.opened.single().tunneling)

        val line = SubtitleCue.Text("Where were you?")
        onPlayer { engine.cues.value = listOf(line) }
        assertEquals(listOf(line), await(manager.cues) { it.isNotEmpty() })

        // mpv draws its own: none from the engine before it.
        onPlayer { manager.switchEngine() }
        waitForOpens(mpv, 1)
        await(manager.cues) { it.isEmpty() }
    }

    @Test fun aChannelStartsWithItsOwnEngine() {
        val own = object : StreamResolver {
            override suspend fun resolve(key: String) = live.resolve(key)

            override suspend fun engineFor(key: String) = if (key == "ch105") EngineId.Mpv else null
        }
        val manager = withMpv(resolver = own)

        onPlayer { manager.show("ch105", muted = false) }

        waitForOpens(mpv, 1)
        assertTrue(engine.opened.isEmpty())
        // Another channel starts with VLC again.
        onPlayer { manager.show("ch107", muted = false) }
        waitForOpens(1)
        assertEquals(EngineId.Vlc, await(manager.engineId) { it == EngineId.Vlc })
    }

    @Test fun aFallbackEngineThatKeepsPlayingIsReported() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = withMpv(events = events, engineWorkedMs = 300)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        waitForData()
        onPlayer { engine.state.value = EngineState.Failed("decoder") }
        waitForOpens(mpv, 1)

        // The stream info's dot is amber just after the fallback (4.10)...
        assertTrue(await(manager.afterFallback) { it })
        onPlayer {
            mpv.state.value = EngineState.Playing
            mpv.hasPicture.value = true
        }

        runBlocking { withTimeout(10_000) { while (events.none { it is PlayerEvent.EngineWorked }) kotlinx.coroutines.delay(10) } }
        assertEquals(listOf(PlayerEvent.EngineWorked("ch105", EngineId.Mpv)), events.filterIsInstance<PlayerEvent.EngineWorked>())
        // ...until the engine has played the stream for a while.
        assertFalse(await(manager.afterFallback) { !it })
    }

    // ---- Stalls (brief 5.4.3) ---------------------------------------------------------------------

    private val steady = StreamResolver { channelId -> StreamTarget(channelId, provider.url("/steady/live/$channelId.ts").toString(), budget = budget) }

    private fun playing(engine: FakeEngine) = onPlayer {
        engine.state.value = EngineState.Playing
        engine.hasPicture.value = true
    }

    /** The engine buffers for a moment, then plays on. */
    private fun stall(engine: FakeEngine) {
        onPlayer { engine.state.value = EngineState.Buffering(40f) }
        Thread.sleep(100)
        onPlayer { engine.state.value = EngineState.Playing }
        Thread.sleep(100)
    }

    @Test fun threeStallsWhileTheDataKeepsComingAreTheEnginesFault() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = withMpv(events = events, resolver = steady)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        playing(engine)
        // Long enough for the hub's data to have kept coming for two seconds.
        Thread.sleep(2_500)

        stall(engine)
        stall(engine)
        assertTrue(mpv.opened.isEmpty())
        assertFalse(manager.afterFallback.value)
        stall(engine)

        // mpv takes the same stream from the hub, and the dot turns amber.
        waitForOpens(mpv, 1)
        assertEquals(engine.opened.single().url, mpv.opened.single().url)
        assertEquals(listOf(PlayerEvent.SwitchedEngine("ch105", EngineId.Vlc, EngineId.Mpv)), events.filterIsInstance<PlayerEvent.SwitchedEngine>())
        assertTrue(await(manager.afterFallback) { it })

        // A new tune starts afresh.
        onPlayer { manager.show("ch107", muted = false) }
        assertFalse(await(manager.afterFallback) { !it })
    }

    @Test fun stallsWhileTheProviderIsSlowArentTheEnginesFault() {
        // The usual test stream: a few packets, then nothing new for ten seconds.
        val manager = withMpv()
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        waitForData()
        playing(engine)
        Thread.sleep(2_500)

        repeat(3) { stall(engine) }

        Thread.sleep(500)
        assertTrue(mpv.opened.isEmpty())
    }

    @Test fun stallsFurtherApartThanAMinuteDontAddUp() {
        val manager = withMpv(resolver = steady, stallWindowMs = 300)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        playing(engine)
        Thread.sleep(2_500)

        repeat(3) {
            stall(engine)
            Thread.sleep(600)
        }

        assertTrue(mpv.opened.isEmpty())
    }

    // ---- Audio and Subtitles (brief 4.10, 5.4.6 and 5.4.7) ----------------------------------------

    /** Arena Sports 1's tracks: Spanish first, then English audio description and English; English subtitles. */
    private val arenaTracks = MediaTracks(
        audio = listOf(
            MediaTrack("1", language = "spa", codec = "a52", channels = 6),
            MediaTrack("2", language = "eng", title = "Audio Description", codec = "mp4a", channels = 2),
            MediaTrack("3", language = "eng", codec = "mp4a", channels = 2),
        ),
        subtitles = listOf(MediaTrack("5", language = "eng", codec = "dvbs")),
        audioId = "1",
    )

    private val withPicks = object : StreamResolver {
        override suspend fun resolve(key: String) = StreamTarget(key, provider.url("/live/viewer/s3cret/$key.ts").toString(), budget = budget)

        override suspend fun audioFor(key: String) = when (key) {
            // Spanish was picked on it, and its subtitles turned off.
            "ch105" -> ChannelAudio(tracks = ChannelTracks(audio = TrackPick("spa", "a52", 6), subtitlesOff = true))
            else -> null
        }
    }

    /** Plays [channelId] until the engine says so, with [tracks]. */
    private fun playWithTracks(manager: PlayerManager, channelId: String, tracks: MediaTracks) {
        onPlayer { manager.show(channelId, muted = false) }
        waitForOpens(1)
        onPlayer {
            engine.tracks.value = tracks
            engine.state.value = EngineState.Playing
            engine.hasPicture.value = true
        }
    }

    private fun waitForPicks(count: Int) = runBlocking { withTimeout(10_000) { while (engine.picked.size < count) kotlinx.coroutines.delay(10) } }

    @Test fun aStreamsTracksFollowTheLanguageRules() {
        val prefs = PlaybackPrefs(tracks = TrackPrefs(languages = listOf("en", ORIGINAL_LANGUAGE), skipDescription = true, subtitles = SubtitleMode.On))
        val manager = PlayerManager(engine, hub, withPicks, { prefs }, scope)

        playWithTracks(manager, "ch107", arenaTracks)

        waitForPicks(2)
        // English, passing over its audio description, and English subtitles.
        assertEquals(listOf("audio:3", "subtitles:5"), engine.picked.toList())
    }

    @Test fun whatWasPickedOnAChannelComesBeforeTheRules() {
        val prefs = PlaybackPrefs(tracks = TrackPrefs(subtitles = SubtitleMode.On))
        val manager = PlayerManager(engine, hub, withPicks, { prefs }, scope)

        // It starts on English, with the subtitles on.
        playWithTracks(manager, "ch105", arenaTracks.copy(audioId = "3", subtitleId = "5"))

        waitForPicks(2)
        assertEquals(listOf("audio:1", "subtitles:off"), engine.picked.toList())
    }

    @Test fun aTrackPickedInPlayerOptionsPlaysAndIsReported() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = PlayerManager(engine, hub, withPicks, { PlaybackPrefs(tracks = TrackPrefs(languages = listOf(ORIGINAL_LANGUAGE))) }, scope, onEvent = { events += it })
        playWithTracks(manager, "ch107", arenaTracks)
        await(manager.tracks) { it.audio.isNotEmpty() }

        onPlayer {
            manager.selectAudio("2")
            manager.selectSubtitles("5")
            manager.selectSubtitles(null)
        }

        assertEquals(listOf("audio:2", "subtitles:5", "subtitles:off"), engine.picked.toList())
        assertEquals(
            listOf(
                PlayerEvent.AudioChosen("ch107", arenaTracks.audio[1]),
                PlayerEvent.SubtitlesChosen("ch107", arenaTracks.subtitles[0]),
                PlayerEvent.SubtitlesChosen("ch107", null),
            ),
            events.filter { it is PlayerEvent.AudioChosen || it is PlayerEvent.SubtitlesChosen },
        )
    }

    // ---- Movies and episodes (brief 4.9 and 5.9) ------------------------------------------------

    private val files = StreamResolver { key -> StreamTarget(key, provider.url("/movie/viewer/s3cret/7101.mkv").toString(), throughHub = false) }

    @Test fun aMovieGoesStraightToTheEngineFromWhereItWasLeft() {
        val manager = PlayerManager(engine, hub, files, { PlaybackPrefs() }, scope)
        val neon = StreamKeys.movie("home:7101")

        onPlayer { manager.playVod(neon, startMs = 3_720_000) }

        waitForOpens(1)
        val request = engine.opened.single()
        assertEquals(provider.url("/movie/viewer/s3cret/7101.mkv").toString(), request.url)
        assertEquals(false, request.live)
        assertEquals(3_720_000L, request.startMs)
        assertEquals(100, engine.lastVolume)
        assertEquals(0, provider.requestCount)

        onPlayer {
            engine.state.value = EngineState.Playing
            engine.hasPicture.value = true
            engine.length.value = 7_080_000
        }
        await(manager.status) { it == LiveStatus.Playing(neon) }
        assertEquals(7_080_000L, await(manager.length) { it > 0 })
        onPlayer {
            manager.setPaused(true)
            manager.seekTo(3_600_000)
        }
        assertEquals(true, engine.pauses.last())
        assertEquals(listOf(3_600_000L), engine.seeks.toList())
        assertEquals(3_600_000L, await(manager.position) { it > 0 })

        // A movie ends; it hasn't failed.
        onPlayer { engine.state.value = EngineState.Ended }
        assertEquals(LiveStatus.Ended(neon), await(manager.status) { it is LiveStatus.Ended })
    }

    @Test fun aMovieCarriesOnWhereItWasInAnotherEngineAndAfterStandby() {
        val manager = withMpv(resolver = files)
        val neon = StreamKeys.movie("home:7101")
        onPlayer { manager.playVod(neon, startMs = 0) }
        waitForOpens(1)
        onPlayer {
            engine.state.value = EngineState.Playing
            engine.position.value = 600_000
        }

        onPlayer { manager.switchEngine() }
        waitForOpens(mpv, 1)
        assertEquals(600_000L, mpv.opened.single().startMs)

        // Back from standby, it opens again (with the usual engine: the app keeps Switch Player's
        // pick) where it had got to.
        onPlayer { mpv.position.value = 900_000 }
        onPlayer { manager.standby(graceMs = 0) }
        onPlayer { manager.resume() }
        waitForOpens(2)
        assertEquals(900_000L, engine.opened[1].startMs)

        // Played from the beginning: from the beginning, whatever it had got to.
        onPlayer { manager.playVod(neon, startMs = 0) }
        waitForOpens(3)
        assertEquals(0L, engine.opened[2].startMs)
    }

    @Test fun withoutTheRewindBufferLiveTvDoesntPauseOrSeek() {
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)

        onPlayer {
            manager.setPaused(true)
            manager.seekTo(60_000)
        }

        assertEquals(listOf(false), engine.pauses.toList())
        assertEquals(emptyList<Long>(), engine.seeks.toList())
        assertEquals(true, engine.opened.single().live)
        assertNull(onPlayerResult { manager.rewindReachMs() })
        assertNull(runBlocking { withContext(thread) { manager.seekLive(30_000, orOldest = true) } })

        // A channel with an archive holds its picture, for the archive to carry on from (5.5).
        onPlayer { manager.holdPicture() }
        assertEquals(listOf(false, true), engine.pauses.toList())
        assertNull(manager.behindLive.value)
    }

    private fun <T> onPlayerResult(block: () -> T): T = runBlocking { withContext(thread) { block() } }

    /**
     * A player on a hub with the rewind buffer (brief 5.5) in [folder], playing a steady stream,
     * with [clock] for its wall clock; [use] runs with it, and it's closed after.
     */
    private fun rewinding(
        mode: RewindMode,
        folder: File,
        clock: () -> Long = System::currentTimeMillis,
        keepMs: Long = 3_600_000,
        hubClock: () -> Long = System::currentTimeMillis,
        use: (PlayerManager) -> Unit,
    ) {
        val settings = RewindSettings(mode, keepMs = keepMs, maxBytes = Long.MAX_VALUE, folder = folder)
        val rewindHub = StreamHub(OkHttpClient(), HubConfig(noDataTimeoutMs = 20_000, reconnectDelaysMs = listOf(0), rewind = { settings }, clock = hubClock))
        val steady = StreamResolver { channelId -> StreamTarget(channelId, provider.url("/steady/$channelId.ts").toString(), budget = budget) }
        val manager = PlayerManager(engine, rewindHub, steady, { PlaybackPrefs() }, scope, clock = clock)
        try {
            onPlayer { manager.show("ch105", muted = false) }
            waitForOpens(1)
            onPlayer {
                engine.state.value = EngineState.Playing
                engine.hasPicture.value = true
            }
            await(manager.status) { it is LiveStatus.Playing }
            use(manager)
        } finally {
            onPlayer { manager.show(null, muted = true) }
            rewindHub.close()
            folder.deleteRecursively()
        }
    }

    @Test fun pausingLiveTvKeepsTheStreamInTheRewindBuffer() {
        val folder = Files.createTempDirectory("rewind").toFile()
        rewinding(RewindMode.WhenPaused, folder) { manager ->
            onPlayer { manager.setPaused(true) }

            assertEquals(listOf(false, true), engine.pauses.toList())
            assertTrue(manager.behindLive.value!!.paused)
            // The buffer starts off the main thread, in a folder of its own.
            runBlocking { withTimeout(10_000) { while (folder.listFiles().isNullOrEmpty()) kotlinx.coroutines.delay(10) } }

            onPlayer { manager.setPaused(false) }

            assertEquals(listOf(false, true, false), engine.pauses.toList())
            assertFalse(manager.behindLive.value!!.paused)
            // The same stream carries on: nothing opened again.
            assertEquals(1, engine.opened.size)
        }
    }

    @Test fun rewindingOpensTheBufferAndGoLiveTheLiveEdge() {
        val folder = Files.createTempDirectory("rewind").toFile()
        // Ten seconds on, as the player sees it: the buffer reaches back far enough.
        rewinding(RewindMode.Always, folder, clock = { System.currentTimeMillis() + 10_000 }) { manager ->
            // Kept from the start: once the stream's first bytes are in, it can be rewound.
            runBlocking { withTimeout(10_000) { while (withContext(thread) { manager.rewindReachMs() } == null) kotlinx.coroutines.delay(10) } }

            val landed = runBlocking { withContext(thread) { manager.seekLive(5_000) } }

            assertTrue("Landed $landed behind", landed != null && landed >= 5_000)
            waitForOpens(2)
            assertTrue(engine.opened[1].url, "?at=" in engine.opened[1].url)
            assertTrue(engine.opened[1].live)
            assertTrue(manager.behindLive.value != null)

            onPlayer { manager.goLive() }

            waitForOpens(3)
            assertTrue(engine.opened[2].url, engine.opened[2].url.endsWith("?at=live"))
            assertNull(manager.behindLive.value)
        }
    }

    @Test fun pausedLongerThanTheBufferKeepsItPlaysOnFromTheOldestItHolds() {
        val folder = Files.createTempDirectory("rewind").toFile()
        // Time twenty times as fast, for the player and the hub alike.
        val start = System.currentTimeMillis()
        val fast = { start + (System.currentTimeMillis() - start) * 20 }
        rewinding(RewindMode.WhenPaused, folder, clock = fast, keepMs = 20_000, hubClock = fast) { manager ->
            onPlayer { manager.setPaused(true) }
            // A minute, as their clock has it: the buffer has let go of where the picture stopped.
            Thread.sleep(3_000)

            onPlayer { manager.setPaused(false) }

            waitForOpens(2)
            assertTrue(engine.opened[1].url, "?at=" in engine.opened[1].url)
            val behind = manager.behindLive.value!!
            assertFalse(behind.paused)
            // A little after the oldest moment it holds, twenty seconds or so behind.
            assertTrue("${behind.behindMs(fast())} behind", behind.behindMs(fast()) in 5_000..25_000)
        }
    }

    @Test fun rewindingFurtherThanTheBufferLandsOnItsOldestWhenAsked() {
        val folder = Files.createTempDirectory("rewind").toFile()
        rewinding(RewindMode.Always, folder, clock = { System.currentTimeMillis() + 10_000 }) { manager ->
            runBlocking { withTimeout(10_000) { while (onPlayerResult { manager.rewindReachMs() } == null) kotlinx.coroutines.delay(10) } }

            // With the archive to carry on, it says the buffer doesn't reach.
            assertNull(runBlocking { withContext(thread) { manager.seekLive(60_000) } })
            assertEquals(1, engine.opened.size)

            val landed = runBlocking { withContext(thread) { manager.seekLive(60_000, orOldest = true) } }

            assertTrue("Landed $landed behind", landed != null && landed in 10_000..20_000)
            waitForOpens(2)
            assertTrue(engine.opened[1].url, "?at=0" in engine.opened[1].url)
        }
    }

    @Test fun aSeekCloseToLiveIsLive() {
        val folder = Files.createTempDirectory("rewind").toFile()
        rewinding(RewindMode.Always, folder) { manager ->
            assertEquals(0L, runBlocking { withContext(thread) { manager.seekLive(1_000) } })
            assertNull(manager.behindLive.value)
            assertEquals(1, engine.opened.size)
        }
    }

    @Test fun aMoviesKeySaysWhatItIs() {
        assertEquals("movie" to "home:7101", StreamKeys.vodOf(StreamKeys.movie("home:7101")))
        assertEquals("episode" to "home:9101", StreamKeys.vodOf(StreamKeys.episode("home:9101")))
        assertEquals("jellyfin" to "11111111-1111-1111-1111-111111111111", StreamKeys.vodOf(StreamKeys.jellyfin("11111111-1111-1111-1111-111111111111")))
        assertEquals(null, StreamKeys.vodOf("ch105"))
        // A movie whose id ends like an archive start is still a movie.
        assertEquals(null, StreamKeys.archiveStart(StreamKeys.movie("home@1792615260")))
    }

    @Test fun aChannelWithNoStreamSaysSo() {
        val nowhere = StreamResolver { null }
        val manager = PlayerManager(engine, hub, nowhere, { PlaybackPrefs() }, scope)

        onPlayer { manager.show("ch999", muted = false) }

        assertEquals(LiveStatus.Failed("ch999", LiveFailure.NoStream), await(manager.status) { it is LiveStatus.Failed })
    }
}
