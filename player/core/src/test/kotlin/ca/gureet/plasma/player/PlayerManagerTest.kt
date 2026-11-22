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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
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
import kotlinx.coroutines.withTimeoutOrNull

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

        /** What it has shown and played, as engines count it (#91); null for one that can't tell. */
        val shown = MutableStateFlow<Long?>(null)
        override fun output(): Long? = shown.value

        /** Its pictures alone, as mpv and the Android player count them; null, as VLC, for one that can't tell. */
        val pictures = MutableStateFlow<Long?>(null)
        override fun pictureOutput(): Long? = pictures.value

        /** Each place it was asked to jump to. */
        val seeks = CopyOnWriteArrayList<Long>()

        /** Each sound and subtitle track picked: "audio:2", "subtitles:4", "subtitles:off". */
        val picked = CopyOnWriteArrayList<String>()

        /** Each level set, and the level each stream opened with. */
        val levels = CopyOnWriteArrayList<Int>()
        val openedLevels = CopyOnWriteArrayList<Int>()

        /** Night Mode and Audio Delay as set, and as each stream opened with them. */
        val nightModes = CopyOnWriteArrayList<Boolean>()
        val delays = CopyOnWriteArrayList<Int>()
        val openedSound = CopyOnWriteArrayList<Pair<Boolean?, Int?>>()
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
            openedSound += nightModes.lastOrNull() to delays.lastOrNull()
            // As the engines do: it counts from where it opens.
            position.value = request.startMs
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
        override fun setNightMode(on: Boolean) {
            nightModes += on
        }
        override fun setAudioDelay(ms: Int) {
            delays += ms
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

    /** [count] MPEG-TS packets carrying only the stream's clock, 40 ms apart from [fromMs]. */
    private fun clockPackets(fromMs: Long, count: Int): ByteArray {
        val packets = ByteArray(188 * count) { 0xFF.toByte() }
        repeat(count) { index ->
            val base = (fromMs + index * 40L) * 90
            val at = index * 188
            packets[at] = 0x47
            packets[at + 1] = 0x01
            packets[at + 2] = 0x01
            packets[at + 3] = 0x20
            packets[at + 4] = 183.toByte()
            packets[at + 5] = 0x10
            packets[at + 6] = (base shr 25).toByte()
            packets[at + 7] = (base shr 17).toByte()
            packets[at + 8] = (base shr 9).toByte()
            packets[at + 9] = (base shr 1).toByte()
            packets[at + 10] = (((base and 1L) shl 7) or 0x7EL).toByte()
            packets[at + 11] = 0
        }
        return packets
    }

    private fun stream(packets: Int = 20) = MockResponse.Builder()
        .addHeader("Content-Type", "video/mp2t")
        .body(Buffer().write(ByteArray(188 * packets) { if (it % 188 == 0) 0x47 else 0x11 }))

    /** Every path the provider was asked for, in order. */
    private val asked = CopyOnWriteArrayList<String>()

    @Before fun setUp() {
        provider.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked += request.url.encodedPath
                return when {
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
                    // A stream whose clock starts again from 0 after two seconds, as the provider's
                    // stream starting again does; then it goes on steadily.
                    "/jumping/" in request.url.encodedPath -> MockResponse.Builder()
                        .addHeader("Content-Type", "video/mp2t")
                        .body(Buffer().write(clockPackets(fromMs = 0, count = 50) + clockPackets(fromMs = 0, count = 3_000)))
                        .throttleBody(188 * 10L, 100, TimeUnit.MILLISECONDS)
                        .build()
                    // The same, with the clock starting again two seconds in, once a picture can be up.
                    "/jumps-later/" in request.url.encodedPath -> MockResponse.Builder()
                        .addHeader("Content-Type", "video/mp2t")
                        .body(Buffer().write(clockPackets(fromMs = 0, count = 200) + clockPackets(fromMs = 0, count = 3_000)))
                        .throttleBody(188 * 10L, 100, TimeUnit.MILLISECONDS)
                        .build()
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

    // ---- Plasma's screensaver (#193) ----------------------------------------------------------------

    @Test fun theScreensaverStopsThePreviewAndLetsItsConnectionGo() {
        onPlayer { manager.show("ch105", muted = true) }
        waitForOpens(1)
        assertEquals(1, hub.connectionsInUse("home"))

        onPlayer { manager.setScreensaver(true) }

        // The picture and sound stop, and the connection goes at once, so nothing keeps the
        // screen on (5.20).
        assertEquals(LiveStatus.Idle, manager.status.value)
        assertTrue(engine.stops > 0)
        assertEquals(0, hub.connectionsInUse("home"))
    }

    @Test fun aKeyThatWakesPlasmaPlaysThePreviewAgainFromItsLiveEdge() {
        onPlayer { manager.show("ch105", muted = true) }
        waitForOpens(1)
        onPlayer { manager.setScreensaver(true) }

        onPlayer { manager.setScreensaver(false) }

        // As when the screen opened: a new connection, muted as Home's banner had it.
        waitForOpens(2)
        assertEquals(LiveStatus.Opening("ch105"), manager.status.value)
        assertNotEquals(engine.opened[0].url, engine.opened[1].url)
        assertEquals(0, engine.lastVolume)
        assertEquals(1, hub.connectionsInUse("home"))
    }

    @Test fun whatAScreenAsksForUnderTheScreensaverWaitsForItToGo() {
        onPlayer { manager.show("ch105", muted = true) }
        waitForOpens(1)
        onPlayer { manager.setScreensaver(true) }

        // The screen underneath moves on to another channel, with sound.
        onPlayer { manager.show("ch107", muted = false) }
        Thread.sleep(300)
        assertEquals(1, engine.opened.size)
        assertEquals(LiveStatus.Idle, manager.status.value)
        assertEquals(0, hub.connectionsInUse("home"))

        onPlayer { manager.setScreensaver(false) }
        waitForOpens(2)
        assertEquals(LiveStatus.Opening("ch107"), manager.status.value)
        assertEquals(100, engine.lastVolume)
    }

    @Test fun aScreenThatShowsNothingUnderTheScreensaverPlaysNothingAfterIt() {
        onPlayer { manager.show("ch105", muted = true) }
        waitForOpens(1)
        onPlayer { manager.setScreensaver(true) }
        onPlayer { manager.show(null, muted = true) }

        onPlayer { manager.setScreensaver(false) }

        Thread.sleep(300)
        assertEquals(1, engine.opened.size)
        assertEquals(LiveStatus.Idle, manager.status.value)
        assertEquals(0, hub.connectionsInUse("home"))
    }

    @Test fun backInFrontUnderTheScreensaverNothingPlaysUntilItGoes() {
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        onPlayer { manager.standby(graceMs = 60_000) }

        // The screensaver over Plasma as it comes back to the front (5.20): the connection kept
        // for the channel goes, and the channel waits.
        onPlayer { manager.setScreensaver(true) }
        assertEquals(0, hub.connectionsInUse("home"))
        onPlayer { manager.resume() }
        Thread.sleep(300)
        assertEquals(1, engine.opened.size)
        assertEquals(LiveStatus.Idle, manager.status.value)
        assertEquals(0, hub.connectionsInUse("home"))

        onPlayer { manager.setScreensaver(false) }
        waitForOpens(2)
        assertEquals(LiveStatus.Opening("ch105"), manager.status.value)
        assertEquals(100, engine.lastVolume)
    }

    @Test fun theScreensaverStopsWhatPlaysAgainAsPlasmaComesBack() {
        // Its wait ran out while Plasma was away: back in front, the channel plays again with the
        // connection kept for it, then the screensaver shows over it.
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        onPlayer { manager.standby(graceMs = 60_000) }
        onPlayer { manager.resume() }

        onPlayer { manager.setScreensaver(true) }

        assertEquals(LiveStatus.Idle, manager.status.value)
        runBlocking { withTimeout(10_000) { while (hub.connectionsInUse("home") > 0) kotlinx.coroutines.delay(10) } }
        val opens = engine.opened.size
        onPlayer { manager.setScreensaver(false) }
        waitForOpens(opens + 1)
        assertEquals(LiveStatus.Opening("ch105"), manager.status.value)
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

    @Test fun afterStandbyAProgramFromTheArchiveWaitsToBeToldWhereItHadGotTo() {
        // #117: back in front, the screen that played it opens it again where it had got to.
        val start = java.time.Instant.parse("2026-10-21T19:45:00Z")
        val key = StreamKeys.archive("ch105", start)
        val opened = CopyOnWriteArrayList<String>()
        val archive = StreamResolver {
            opened += it
            StreamTarget(it, provider.url("/timeshift/$it.ts").toString(), throughHub = false)
        }
        val manager = PlayerManager(engine, hub, archive, { PlaybackPrefs() }, scope, clock = { STOPPED_AT })
        onPlayer { manager.show(key, muted = false) }
        waitForOpens(1)
        onPlayer { engine.state.value = EngineState.Playing }

        onPlayer { manager.standby(graceMs = 60_000) }
        assertEquals(ArchiveStop(key, STOPPED_AT), manager.archiveStopped.value)
        onPlayer { manager.resume() }
        assertEquals(ArchiveStop(key, STOPPED_AT, back = true), manager.archiveStopped.value)
        // Nothing opens until the screen says where.
        Thread.sleep(300)
        assertEquals(1, engine.opened.size)

        val later = StreamKeys.archive("ch105", start.plusSeconds(1_200))
        onPlayer { manager.carryOn(later) }
        waitForOpens(2)
        assertEquals(listOf(key, later), opened)
        assertEquals(null, manager.archiveStopped.value)
    }

    @Test fun inTheSmallWindowAProgramFromTheArchivePlaysFromItsStartAfterStandby() {
        val key = StreamKeys.archive("ch105", java.time.Instant.parse("2026-10-21T19:45:00Z"))
        val opened = CopyOnWriteArrayList<String>()
        val archive = StreamResolver {
            opened += it
            StreamTarget(it, provider.url("/timeshift/$it.ts").toString(), throughHub = false)
        }
        val manager = PlayerManager(engine, hub, archive, { PlaybackPrefs() }, scope)
        onPlayer { manager.show(key, muted = false) }
        waitForOpens(1)

        onPlayer { manager.standby(graceMs = 0) }
        onPlayer { manager.resume(fromItsStart = true) }
        waitForOpens(2)
        assertEquals(listOf(key, key), opened)
        assertEquals(null, manager.archiveStopped.value)
    }

    @Test fun somethingElseOnScreenForgetsTheProgramFromTheArchive() {
        val key = StreamKeys.archive("ch105", java.time.Instant.parse("2026-10-21T19:45:00Z"))
        val manager = PlayerManager(engine, hub, { StreamTarget(it, provider.url("/timeshift/$it.ts").toString(), throughHub = false) }, { PlaybackPrefs() }, scope)
        onPlayer { manager.show(key, muted = false) }
        waitForOpens(1)
        onPlayer { manager.standby(graceMs = 0) }
        onPlayer { manager.resume() }

        // Home came up instead, with its preview.
        onPlayer { manager.show("ch106", muted = true) }
        waitForOpens(2)
        assertEquals(null, manager.archiveStopped.value)
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

    @Test fun aSourceSetToPassthroughSendsEveryFormat() {
        // The source's own Passthrough sends every format, for setups behind ARC that report less
        // than they take, over Settings' Decode to PCM (5.4.6).
        val every = SurroundFormat.entries.toSet()
        val own = StreamResolver { channelId ->
            StreamTarget(
                channelId, provider.url("/live/viewer/s3cret/$channelId.ts").toString(), budget = budget,
                audioOutput = AudioOutput.Passthrough, passthroughFormats = every,
            )
        }
        val settings = PlaybackPrefs(audioOutput = AudioOutput.DecodeToPcm, passthroughFormats = setOf(SurroundFormat.DolbyDigital))
        val manager = PlayerManager(engine, hub, own, { settings }, scope)

        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        assertEquals(AudioOutput.Passthrough, engine.opened[0].audioOutput)
        assertEquals(every, engine.opened[0].passthroughFormats)
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

    /** A clock that moves only when a test moves it, for how long pictures take (#127). */
    private val elapsed = AtomicLong(1_000)

    private fun timed(events: List<PlayerEvent>, count: Int): List<TuneTiming> = runBlocking {
        withTimeout(10_000) { while (events.count { it is PlayerEvent.Timed } < count) kotlinx.coroutines.delay(10) }
        events.filterIsInstance<PlayerEvent.Timed>().map { it.timing }
    }

    @Test fun aTuneIsTimedToItsFirstFrame() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = PlayerManager(engine, hub, live, { PlaybackPrefs() }, scope, onEvent = { events += it }, elapsed = elapsed::get)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        // Playing isn't enough: it's timed to the first frame on screen.
        onPlayer { engine.state.value = EngineState.Playing }
        elapsed.addAndGet(800)
        onPlayer { engine.hasPicture.value = true }

        assertEquals(listOf(TuneTiming(TuneTiming.Kind.Zap, "ch105", EngineId.Vlc, 800)), timed(events, 1))
        assertEquals(listOf(800L), manager.timings.value.zaps)
        assertEquals(800L, manager.timings.value.zapP95)
    }

    @Test fun aFallbackIsTimedFromTheFaultAndTheZapThroughIt() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = PlayerManager(
            engine, hub, live, { PlaybackPrefs() }, scope, onEvent = { events += it },
            backups = mapOf(EngineId.Mpv to { mpv }), elapsed = elapsed::get,
        )
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        waitForData()
        elapsed.addAndGet(4_000)
        onPlayer { engine.state.value = EngineState.Failed("decoder") }
        waitForOpens(mpv, 1)
        elapsed.addAndGet(1_200)
        onPlayer {
            mpv.state.value = EngineState.Playing
            mpv.hasPicture.value = true
        }

        assertEquals(
            setOf(TuneTiming(TuneTiming.Kind.Zap, "ch105", EngineId.Mpv, 5_200), TuneTiming(TuneTiming.Kind.Fallback, "ch105", EngineId.Mpv, 1_200)),
            timed(events, 2).toSet(),
        )
    }

    @Test fun switchPlayerIsTimedToThePictureInTheEnginePicked() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = PlayerManager(
            engine, hub, live, { PlaybackPrefs() }, scope, onEvent = { events += it },
            backups = mapOf(EngineId.Mpv to { mpv }), elapsed = elapsed::get,
        )
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        onPlayer {
            engine.state.value = EngineState.Playing
            engine.hasPicture.value = true
        }
        timed(events, 1)

        elapsed.addAndGet(10_000)
        onPlayer { manager.switchEngine() }
        waitForOpens(mpv, 1)
        elapsed.addAndGet(1_500)
        onPlayer {
            mpv.state.value = EngineState.Playing
            mpv.hasPicture.value = true
        }

        assertEquals(TuneTiming(TuneTiming.Kind.SwitchPlayer, "ch105", EngineId.Mpv, 1_500), timed(events, 2).last())
        assertEquals(1_500L, manager.timings.value.last[TuneTiming.Kind.SwitchPlayer]?.millis)
    }

    @Test fun theZapsPercentileIsByNearestRankOverTheLastHundred() {
        val timings = TuneTimings()
            .with(TuneTiming(TuneTiming.Kind.Zap, "ch105", EngineId.Vlc, 400))
            .with(TuneTiming(TuneTiming.Kind.Zap, "ch106", EngineId.Vlc, 2_000))
        // The 95th percentile by nearest rank: of two, the slower.
        assertEquals(2_000L, timings.zapP95)
        val many = (1..100).fold(TuneTimings()) { all, ms -> all.with(TuneTiming(TuneTiming.Kind.Zap, "ch105", EngineId.Vlc, ms * 10L)) }
        assertEquals(950L, many.zapP95)
        // Only the last hundred zaps count.
        assertEquals(TuneTimings.KEPT, many.with(TuneTiming(TuneTiming.Kind.Zap, "ch105", EngineId.Vlc, 5)).zaps.size)
    }

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

    @Test fun theOtherOfMpvAndVlcTakesOverThenTheAndroidPlayer() {
        // The owner's #119: mpv plays first, so VLC takes over from it, and the Android player last.
        assertEquals(listOf(EngineId.Vlc, EngineId.Media3), fallbacksAfter(EngineId.Mpv))
        assertEquals(listOf(EngineId.Mpv, EngineId.Media3), fallbacksAfter(EngineId.Vlc))
        assertEquals(emptyList<EngineId>(), fallbacksAfter(EngineId.Media3))
    }

    @Test fun whatMpvCantPlayVlcPlaysOnTheSameConnection() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = withMpv(prefs = PlaybackPrefs(engine = EngineId.Mpv), events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(mpv, 1)
        waitForData()

        // mpv tries copy-back first, then VLC takes over.
        onPlayer { mpv.state.value = EngineState.Failed("mediacodec") }
        waitForOpens(mpv, 2)
        onPlayer { mpv.state.value = EngineState.Failed("again") }

        waitForOpens(1)
        assertEquals(mpv.opened.first().url, engine.opened.single().url)
        assertEquals(1, provider.requestCount)
        assertEquals(EngineId.Vlc, await(manager.engineId) { it == EngineId.Vlc })
        assertEquals(listOf(PlayerEvent.SwitchedEngine("ch105", EngineId.Mpv, EngineId.Vlc)), events.filterIsInstance<PlayerEvent.SwitchedEngine>())
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

    @Test fun settingsSoundIsInBeforeItsFirstSound() {
        // Settings › Audio: Stereo Downmix, Night Mode and Audio Delay (5.4.6).
        val settings = PlaybackPrefs(audioOutput = AudioOutput.DecodeToPcm, stereoDownmix = true, nightMode = true, audioDelayMs = 50)
        val manager = PlayerManager(engine, hub, ownSound, { settings }, scope)

        onPlayer { manager.show("ch107", muted = false) }
        waitForOpens(1)

        assertTrue(engine.opened[0].stereoDownmix)
        assertEquals(true to 50, engine.openedSound[0])
    }

    @Test fun nightModeAndAudioDelayChangeTheSoundPlaying() {
        var settings = PlaybackPrefs()
        val manager = PlayerManager(engine, hub, ownSound, { settings }, scope)
        onPlayer { manager.show("ch107", muted = false) }
        waitForOpens(1)
        assertEquals(false to 0, engine.openedSound[0])

        settings = settings.copy(nightMode = true, audioDelayMs = -100)
        onPlayer { manager.soundChanged() }

        // At once, with no new stream.
        assertEquals(1, engine.opened.size)
        assertEquals(true, engine.nightModes.last())
        assertEquals(-100, engine.delays.last())
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

    @Test fun aChannelsOwnAudioDelayGoesOnTopOfThisTvs() {
        // #116: this TV's Audio Delay, +50 ms, and Northline News' own, +80 ms.
        val delayed = object : StreamResolver {
            override suspend fun resolve(key: String) = StreamTarget(key, provider.url("/live/viewer/s3cret/$key.ts").toString(), budget = budget)

            override suspend fun audioFor(key: String) = if (key == "ch101") ChannelAudio(delayMs = 80) else null
        }
        var settings = PlaybackPrefs(audioDelayMs = 50)
        val manager = PlayerManager(engine, hub, delayed, { settings }, scope)
        onPlayer { manager.show("ch101", muted = false) }
        waitForOpens(1)
        assertEquals(130, engine.openedSound[0].second)

        // Player Options' bar moves the channel playing's own, at once, and no other's.
        onPlayer { manager.setChannelDelay("ch101", -20) }
        onPlayer { manager.setChannelDelay("ch107", 300) }
        assertEquals(30, engine.delays.last())
        assertFalse(350 in engine.delays)
        assertEquals(1, engine.opened.size)

        // Settings' delay changing keeps the channel's on top.
        settings = settings.copy(audioDelayMs = 100)
        onPlayer { manager.soundChanged() }
        assertEquals(80, engine.delays.last())

        // The next channel has just this TV's.
        onPlayer { manager.show("ch107", muted = false) }
        waitForOpens(2)
        assertEquals(100, engine.openedSound[1].second)
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

    /** Settings for a soundbar that takes Dolby Digital and no decoded surround, with Encode Surround on (#102). */
    private val encoding = PlaybackPrefs(passthroughFormats = setOf(SurroundFormat.DolbyDigital), pcmChannels = 2, encodeSurround = true)

    private fun MutableStateFlow<MediaTracks>.plays(codec: String, channels: Int) {
        value = MediaTracks(audio = listOf(MediaTrack("1", codec = codec, channels = channels)), audioId = "1")
    }

    @Test fun surroundThatCantPassThroughPlaysWithMpvToBeEncoded() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = withMpv(prefs = encoding, events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)

        // AAC 5.1: the soundbar takes it only as Dolby Digital.
        onPlayer { engine.tracks.plays("aac", 6) }

        waitForOpens(mpv, 1)
        assertTrue(mpv.opened.single().encodeSurround)
        assertEquals(setOf(SurroundFormat.DolbyDigital), mpv.opened.single().passthroughFormats)
        assertEquals(engine.opened.single().url, mpv.opened.single().url)
        // Not remembered for the channel: the next tune starts with its own engine again.
        assertTrue(events.none { it is PlayerEvent.EngineChosen })
    }

    @Test fun encodingComesBeforeTheSourcesOwnEngine() {
        // The sound rule comes before the source's Player Engine (5.4.3).
        val sourceVlc = StreamResolver { channelId ->
            StreamTarget(channelId, provider.url("/live/viewer/s3cret/$channelId.ts").toString(), budget = budget, engine = EngineId.Vlc)
        }
        val manager = withMpv(prefs = encoding, resolver = sourceVlc)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)

        onPlayer { engine.tracks.plays("aac", 6) }

        waitForOpens(mpv, 1)
        assertTrue(mpv.opened.single().encodeSurround)
    }

    @Test fun aChannelsOwnEngineKeepsItsSound() {
        // Switch Player's pick for the channel comes first: Encode Surround doesn't move it.
        val own = object : StreamResolver {
            override suspend fun resolve(key: String) = live.resolve(key)

            override suspend fun engineFor(key: String) = EngineId.Vlc
        }
        val manager = withMpv(prefs = encoding, resolver = own)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)

        onPlayer { engine.tracks.plays("aac", 6) }

        runBlocking { kotlinx.coroutines.delay(500) }
        assertTrue(mpv.opened.isEmpty())
    }

    @Test fun surroundThatPassesThroughOrStereoStaysWithItsEngine() {
        val manager = withMpv(prefs = encoding)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)

        // Dolby Digital 5.1 passes through as it is, and stereo stays decoded.
        onPlayer { engine.tracks.plays("ac3", 6) }
        onPlayer { engine.tracks.plays("aac", 2) }

        runBlocking { kotlinx.coroutines.delay(500) }
        assertTrue(mpv.opened.isEmpty())
    }

    @Test fun withoutASoundSystemThatTakesDolbyDigitalNothingMoves() {
        // A TV's own speakers, or a receiver that takes decoded surround: Encode Surround has nothing to do.
        for (prefs in listOf(encoding.copy(passthroughFormats = emptySet()), encoding.copy(pcmChannels = 8), encoding.copy(audioOutput = AudioOutput.DecodeToPcm))) {
            assertFalse(prefs.encodes)
        }
        assertTrue(encoding.encodes)
        assertTrue(needsEncoding(MediaTracks(listOf(MediaTrack("1", codec = "eac3", channels = 6)), audioId = "1"), null, setOf(SurroundFormat.DolbyDigital)))
        assertFalse(needsEncoding(MediaTracks(listOf(MediaTrack("1", codec = "eac3", channels = 6)), audioId = "1"), null, setOf(SurroundFormat.DolbyDigital, SurroundFormat.DolbyDigitalPlus)))
        // Not before its channels are known.
        assertFalse(needsEncoding(MediaTracks(), AudioInfo(AudioCodec.Other), setOf(SurroundFormat.DolbyDigital)))
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

    // ---- Frozen streams (the owner's #91) ---------------------------------------------------------

    /**
     * A player that looks at the picture and sound ten times a second, and calls them frozen once
     * they've stood still for [stillMs]; mpv is there to take over.
     */
    private fun watchingFreezes(
        stillMs: Long? = 400,
        resolver: StreamResolver = steady,
        events: MutableList<PlayerEvent> = CopyOnWriteArrayList(),
    ) = PlayerManager(
        engine, hub, resolver, { PlaybackPrefs(restartFrozenMs = stillMs) }, scope, onEvent = { events += it },
        backups = mapOf(EngineId.Mpv to { mpv }), freezeLookMs = LOOK_MS,
    )

    /** [engine] shows pictures and plays sound for [ms]: its count moves on every 50 ms. */
    private fun moving(engine: FakeEngine, ms: Long = 600) {
        repeat((ms / 50).toInt()) {
            engine.shown.value = (engine.shown.value ?: 0) + 1
            Thread.sleep(50)
        }
    }

    /** The picture comes up and the engine plays for a moment; then nothing moves on. */
    private fun playsThenFreezes(engine: FakeEngine) {
        playing(engine)
        moving(engine)
    }

    /** [engine]'s count moves on every 50 ms, from another thread, until it's closed. */
    private fun keepMoving(engine: FakeEngine): AutoCloseable {
        val going = java.util.concurrent.atomic.AtomicBoolean(true)
        val mover = Thread {
            while (going.get()) {
                engine.shown.value = (engine.shown.value ?: 0) + 1
                Thread.sleep(50)
            }
        }.apply {
            isDaemon = true
            start()
        }
        return AutoCloseable {
            going.set(false)
            mover.join()
        }
    }

    private fun restarts(events: List<PlayerEvent>) = events.filterIsInstance<PlayerEvent.Restarted>()

    /** [engine]'s picture and sound both move on every 50 ms, from another thread, until it's closed. */
    private fun keepPicturesMoving(engine: FakeEngine): AutoCloseable {
        val going = java.util.concurrent.atomic.AtomicBoolean(true)
        val mover = Thread {
            while (going.get()) {
                engine.shown.value = (engine.shown.value ?: 0) + 1
                engine.pictures.value = (engine.pictures.value ?: 0) + 1
                Thread.sleep(50)
            }
        }.apply {
            isDaemon = true
            start()
        }
        return AutoCloseable {
            going.set(false)
            mover.join()
        }
    }

    @Test fun aStreamThatFreezesWhileItsDataKeepsComingOpensAgainOnANewConnection() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        // Each tune asks for the channel's address, as a portal's expires (7.1).
        val asked = AtomicInteger()
        val portal = StreamResolver { channelId ->
            StreamTarget(channelId, provider.url("/steady/live/$channelId-${asked.incrementAndGet()}.ts").toString(), budget = budget)
        }
        val manager = watchingFreezes(resolver = portal, events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)

        // The picture and sound stand still while the hub's data keeps coming.
        playsThenFreezes(engine)

        // As changing channel and back would: the address asked for again, a new connection, the
        // same engine, from the live edge.
        waitForOpens(2)
        assertEquals(listOf(PlayerEvent.Restarted("ch105")), restarts(events))
        assertNotEquals(engine.opened[0].url, engine.opened[1].url)
        assertTrue(engine.opened[1].live)
        runBlocking { withTimeout(10_000) { while (provider.requestCount < 2) kotlinx.coroutines.delay(10) } }
        assertEquals(listOf("/steady/live/ch105-1.ts", "/steady/live/ch105-2.ts"), (1..2).map { provider.takeRequest().url.encodedPath })
        assertEquals(1, hub.connectionsInUse("home"))
        assertTrue(mpv.opened.isEmpty())
        assertTrue(events.none { it is PlayerEvent.SwitchedEngine || it is PlayerEvent.SourceFailed })
        // The channel is still the one on screen, from its source.
        assertEquals(PlayingFrom("ch105", listOf("ch105"), "ch105"), manager.playingFrom.value)
    }

    @Test fun nothingIsFrozenBeforeTheFirstPicture() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = watchingFreezes(events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)

        // Sound for a moment, then nothing, and no picture yet: it may still be on its way.
        onPlayer { engine.state.value = EngineState.Playing }
        moving(engine)

        Thread.sleep(2_500)
        assertEquals(1, engine.opened.size)
        assertTrue(restarts(events).isEmpty())
    }

    @Test fun nothingIsFrozenWhilePausedOrBuffering() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = watchingFreezes(events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        playing(engine)
        moving(engine)

        onPlayer { engine.state.value = EngineState.Paused }
        Thread.sleep(1_500)
        onPlayer { engine.state.value = EngineState.Playing }
        moving(engine)
        onPlayer { engine.state.value = EngineState.Buffering(30f) }
        Thread.sleep(1_500)

        assertEquals(1, engine.opened.size)
        assertTrue(restarts(events).isEmpty())
    }

    @Test fun nothingIsFrozenBehindLiveOrBeforeASeekShowsItsPicture() {
        val folder = Files.createTempDirectory("rewind").toFile()
        val events = CopyOnWriteArrayList<PlayerEvent>()
        rewinding(RewindMode.Always, folder, clock = { System.currentTimeMillis() + 10_000 }, stillMs = 400, events = events) { manager ->
            runBlocking { withTimeout(10_000) { while (onPlayerResult { manager.rewindReachMs() } == null) kotlinx.coroutines.delay(10) } }

            // It plays at the live edge, then is rewound in the buffer, where it plays for a moment.
            keepMoving(engine).use {
                Thread.sleep(500)
                assertTrue(runBlocking { withContext(thread) { manager.seekLive(5_000) } } != null)
                waitForOpens(2)
                playing(engine)
                Thread.sleep(300)
            }

            // Behind live, it stands still: nothing restarts.
            Thread.sleep(2_000)
            assertEquals(2, engine.opened.size)

            // Go Live opens it again: nothing has moved there yet, so nothing has stood still.
            onPlayer { manager.goLive() }
            waitForOpens(3)
            playing(engine)
            Thread.sleep(1_500)

            assertEquals(3, engine.opened.size)
            assertTrue(restarts(events).isEmpty())
        }
    }

    @Test fun aRadioChannelsSoundCounts() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val radio = StreamResolver { StreamTarget(it, provider.url("/steady/live/$it.ts").toString(), budget = budget, radio = true) }
        val manager = watchingFreezes(resolver = radio, events = events)
        onPlayer { manager.show("ch201", muted = false) }
        waitForOpens(1)

        // No picture, ever: while the sound plays, nothing has frozen.
        onPlayer { engine.state.value = EngineState.Playing }
        moving(engine, ms = 2_500)
        assertEquals(1, engine.opened.size)

        // The sound stops too.
        waitForOpens(2)
        assertEquals(listOf(PlayerEvent.Restarted("ch201")), restarts(events))
    }

    @Test fun theThirdFreezeWithinTheWindowHandsTheStreamToTheNextEngine() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = watchingFreezes(events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        playsThenFreezes(engine)
        waitForOpens(2)
        playsThenFreezes(engine)
        waitForOpens(3)
        assertTrue(mpv.opened.isEmpty())

        playsThenFreezes(engine)

        // mpv takes the same stream from the hub, as after three stalls, with the fallback's notice.
        waitForOpens(mpv, 1)
        assertEquals(engine.opened[2].url, mpv.opened.single().url)
        assertEquals(3, engine.opened.size)
        assertEquals(2, restarts(events).size)
        assertEquals(listOf(PlayerEvent.SwitchedEngine("ch105", EngineId.Vlc, EngineId.Mpv)), events.filterIsInstance<PlayerEvent.SwitchedEngine>())
        assertTrue(await(manager.afterFallback) { it })
        // A connection for each restart, and none for the handover.
        assertEquals(3, provider.requestCount)
        // Show Freeze Watch counts them against VLC, newest first.
        val watch = manager.freezeWatch.value
        assertEquals(mapOf(EngineId.Vlc to 2), watch.restarts)
        assertEquals(mapOf(EngineId.Vlc to 1), watch.handedOn)
        assertEquals(
            listOf(FreezeEvent.Kind.EngineSwitched, FreezeEvent.Kind.NextEngine, FreezeEvent.Kind.Restarted, FreezeEvent.Kind.Restarted),
            watch.events.map { it.kind },
        )
        assertEquals(EngineId.Mpv, watch.events.first().to)
    }

    @Test fun offLeavesAFrozenStreamAlone() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = watchingFreezes(stillMs = null, events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)

        playsThenFreezes(engine)

        Thread.sleep(2_500)
        assertEquals(1, engine.opened.size)
        assertTrue(restarts(events).isEmpty())
    }

    @Test fun aFreezeWhileTheProviderSendsNothingIsTheHubs() {
        // The usual test stream: a few packets, then nothing new for ten seconds.
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = watchingFreezes(resolver = live, events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        waitForData()

        playsThenFreezes(engine)

        Thread.sleep(2_500)
        assertEquals(1, engine.opened.size)
        assertTrue(restarts(events).isEmpty())
    }

    @Test fun anEngineThatCantCountGoesByWhereItIs() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = watchingFreezes(events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        playing(engine)

        // Nothing it reports moves: it never looks frozen, as the Android player's live TV.
        Thread.sleep(2_500)
        assertEquals(1, engine.opened.size)

        // Where it is moves on, then stands still.
        repeat(12) {
            onPlayer { engine.position.value += 40 }
            Thread.sleep(50)
        }
        waitForOpens(2)
        assertEquals(listOf(PlayerEvent.Restarted("ch105")), restarts(events))
    }

    @Test fun aChannelTakenUpAgainAfterStandbyIsWatchedToo() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = watchingFreezes(events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        onPlayer { manager.standby(graceMs = 60_000) }
        onPlayer { manager.resume() }
        waitForOpens(2)
        assertEquals(engine.opened[0].url, engine.opened[1].url)

        playsThenFreezes(engine)

        // A new connection in place of the one kept, in the same engine.
        waitForOpens(3)
        assertNotEquals(engine.opened[1].url, engine.opened[2].url)
        assertEquals(listOf(PlayerEvent.Restarted("ch105")), restarts(events))
        assertEquals(1, hub.connectionsInUse("home"))
        assertTrue(mpv.opened.isEmpty())
    }

    // ---- Show Freeze Watch (the owner's ask of 2 November) ------------------------------------------

    @Test fun freezeWatchNotesARestartWithItsEngineAndHowLongItStoodStill() {
        val manager = watchingFreezes()
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        // Opening, until there's a picture that moves.
        assertEquals(FreezeLook.State.Opening, await(manager.freezeWatch) { it.now.channelId == "ch105" }.now.state)
        playing(engine)
        keepMoving(engine).use {
            val watching = await(manager.freezeWatch) { it.now.state == FreezeLook.State.Watching }.now
            assertEquals(EngineId.Vlc, watching.engine)
            assertEquals(400L, watching.afterMs)
            assertFalse(watching.byPlace)
        }

        // It stands still while the stream keeps coming: restarted, and counted against VLC.
        waitForOpens(2)
        val watch = manager.freezeWatch.value
        assertEquals(mapOf(EngineId.Vlc to 1), watch.restarts)
        assertTrue(watch.handedOn.isEmpty())
        val restart = watch.events.single { it.kind == FreezeEvent.Kind.Restarted }
        assertEquals("ch105", restart.channelId)
        assertEquals(EngineId.Vlc, restart.engine)
        assertTrue("Still for ${restart.ms} ms", restart.ms >= 400)
        assertTrue(restart.bytes > 0)
    }

    @Test fun freezeWatchTimesTheEnginesBuffering() {
        val manager = watchingFreezes()
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        playing(engine)
        keepMoving(engine).use {
            Thread.sleep(300)
            onPlayer { engine.state.value = EngineState.Buffering(20f) }
            assertEquals(EngineId.Vlc, await(manager.freezeWatch) { it.now.state == FreezeLook.State.Buffering }.now.engine)
            Thread.sleep(300)
            onPlayer { engine.state.value = EngineState.Playing }

            val buffered = await(manager.freezeWatch) { watch -> watch.events.any { it.kind == FreezeEvent.Kind.Buffered } }
                .events.single { it.kind == FreezeEvent.Kind.Buffered }
            assertEquals("ch105", buffered.channelId)
            assertEquals(EngineId.Vlc, buffered.engine)
            assertTrue("Buffered for ${buffered.ms} ms", buffered.ms in 250L..3_000L)
        }
    }

    @Test fun freezeWatchSaysWhenItIsntWatching() {
        val manager = watchingFreezes(stillMs = null)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        val off = await(manager.freezeWatch) { it.now.channelId == "ch105" }.now
        assertEquals(FreezeLook.State.Off, off.state)
        assertNull(off.afterMs)

        // Nothing on screen: nothing to watch.
        onPlayer { manager.show(null, muted = true) }
        assertEquals(FreezeLook(), await(manager.freezeWatch) { it.now.channelId == null }.now)
    }

    @Test fun aClockJumpBeforeThePictureIsUpIsListedOnly() {
        val jumping = StreamResolver { channelId -> StreamTarget(channelId, provider.url("/jumping/live/$channelId.ts").toString(), budget = budget) }
        val manager = watchingFreezes(resolver = jumping)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)

        val watch = await(manager.freezeWatch) { it.jumps > 0 }
        val jump = watch.events.first { it.kind == FreezeEvent.Kind.ClockJumped }
        assertEquals("ch105", jump.channelId)
        assertEquals(EngineId.Vlc, jump.engine)
        // From two seconds in back to 0: the last clock before was 1.96 s.
        assertEquals(-1_960L, jump.ms)
        assertFalse(jump.marked)
        // VLC hadn't shown a picture yet: nothing restarts for it.
        Thread.sleep(500)
        assertEquals(1, engine.opened.size)
    }

    // ---- A jump in the stream's clock restarts VLC (the owner's ask of 3 November) ---------------

    private val jumpsLater = StreamResolver { channelId -> StreamTarget(channelId, provider.url("/jumps-later/live/$channelId.ts").toString(), budget = budget) }

    @Test fun aClockJumpRestartsVlcAtOnceOnANewConnection() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        // Long enough that no look finds it still first.
        val manager = watchingFreezes(stillMs = 60_000, resolver = jumpsLater, events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        playing(engine)

        waitForOpens(2)
        assertNotEquals(engine.opened[0].url, engine.opened[1].url)
        assertEquals(PlayerEvent.Restarted("ch105", onJump = true), restarts(events).first())
        val watch = manager.freezeWatch.value
        val jump = watch.events.first { it.kind == FreezeEvent.Kind.ClockJumped }
        assertEquals(-7_960L, jump.ms)
        val restart = watch.events.first { it.kind == FreezeEvent.Kind.Restarted }
        assertTrue(restart.onJump)
        assertEquals(EngineId.Vlc, restart.engine)
        assertEquals(0L, restart.ms)
        assertEquals(mapOf(EngineId.Vlc to 1), watch.restarts)
        assertTrue(mpv.opened.isEmpty())
    }

    @Test fun aClockJumpIsListedOnlyWhileRestartFrozenStreamsIsOff() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = watchingFreezes(stillMs = null, resolver = jumpsLater, events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        playing(engine)

        await(manager.freezeWatch) { it.jumps > 0 }
        Thread.sleep(500)
        assertEquals(1, engine.opened.size)
        assertTrue(restarts(events).isEmpty())
    }

    @Test fun aClockJumpInMpvIsListedOnlyAsMpvStartsAgainByItself() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = PlayerManager(
            engine, hub, jumpsLater, { PlaybackPrefs(engine = EngineId.Mpv, restartFrozenMs = 60_000) }, scope, onEvent = { events += it },
            backups = mapOf(EngineId.Mpv to { mpv }), freezeLookMs = LOOK_MS,
        )
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(mpv, 1)
        playing(mpv)

        val jump = await(manager.freezeWatch) { it.jumps > 0 }.events.first { it.kind == FreezeEvent.Kind.ClockJumped }
        assertEquals(EngineId.Mpv, jump.engine)
        Thread.sleep(500)
        assertEquals(1, mpv.opened.size)
        assertTrue(restarts(events).isEmpty())
    }

    @Test fun aClockJumpBehindLiveRestartsNothing() {
        val folder = Files.createTempDirectory("rewind").toFile()
        val events = CopyOnWriteArrayList<PlayerEvent>()
        rewinding(RewindMode.WhenPaused, folder, stillMs = 60_000, events = events, path = "/jumps-later/") { manager ->
            // Paused into the rewind buffer: opening again would take it back to live.
            onPlayer { manager.setPaused(true) }
            assertTrue(manager.behindLive.value!!.paused)

            await(manager.freezeWatch) { it.jumps > 0 }
            Thread.sleep(500)
            assertEquals(1, engine.opened.size)
            assertTrue(restarts(events).isEmpty())
        }
    }

    @Test fun theThirdJumpRestartInTenMinutesHandsTheStreamToMpv() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = watchingFreezes(stillMs = 60_000, resolver = jumpsLater, events = events)
        onPlayer { manager.show("ch105", muted = false) }
        // Each new connection jumps a second in, as this provider's might.
        for (open in 1..3) {
            waitForOpens(open)
            playing(engine)
        }

        waitForOpens(mpv, 1)
        assertEquals(listOf(PlayerEvent.Restarted("ch105", onJump = true), PlayerEvent.Restarted("ch105", onJump = true)), restarts(events))
        assertTrue(events.contains(PlayerEvent.SwitchedEngine("ch105", EngineId.Vlc, EngineId.Mpv)))
        val handed = manager.freezeWatch.value.events.first { it.kind == FreezeEvent.Kind.NextEngine }
        assertTrue(handed.onJump)
        // mpv takes over the third connection, as after a third freeze.
        assertEquals(engine.opened[2].url, mpv.opened.single().url)
    }

    // ---- The picture alone (the owner's answer of 2 November) --------------------------------------

    @Test fun aPictureThatFreezesWhileTheSoundPlaysOnRestartsWhenTheEngineCountsItApart() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = watchingFreezes(events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        playing(engine)
        keepPicturesMoving(engine).use { Thread.sleep(600) }

        // The picture stops; the sound plays on, so picture and sound together never stand still.
        keepMoving(engine).use {
            val still = await(manager.freezeWatch) { it.now.pictureStillMs > 0 }.now
            assertEquals(FreezeLook.State.Watching, still.state)
            assertEquals(0L, still.stillMs)
            waitForOpens(2)
        }

        assertEquals(listOf(PlayerEvent.Restarted("ch105")), restarts(events))
        val restart = manager.freezeWatch.value.events.single { it.kind == FreezeEvent.Kind.Restarted }
        assertTrue(restart.pictureOnly)
        assertTrue("Still for ${restart.ms} ms", restart.ms >= 400)
        assertTrue(restart.bytes > 0)
    }

    @Test fun aPictureThatNeverMovedOrCameOnceIsntFrozenWhileTheSoundPlays() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = watchingFreezes(events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        playing(engine)

        // A music channel's still: its picture count never moves, while its sound does.
        engine.pictures.value = 1
        keepMoving(engine).use { Thread.sleep(1_500) }
        // One picture that comes and stays is no picture playing either.
        engine.pictures.value = 2
        keepMoving(engine).use { Thread.sleep(1_500) }

        assertEquals(1, engine.opened.size)
        assertTrue(restarts(events).isEmpty())
        assertEquals(0L, manager.freezeWatch.value.now.pictureStillMs)
    }

    @Test fun anEngineThatCantCountItsPictureApartGoesByPictureAndSoundTogether() {
        val events = CopyOnWriteArrayList<PlayerEvent>()
        val manager = watchingFreezes(events = events)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        playing(engine)

        // As VLC: no count of its picture alone, and its count moving.
        keepMoving(engine).use { Thread.sleep(2_000) }

        assertEquals(1, engine.opened.size)
        assertTrue(restarts(events).isEmpty())
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
        assertEquals(3_600_000L, await(manager.position) { it == 3_600_000L })

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

    @Test fun aRecordingBeingWrittenGrowsAndASeekOpensItAgainAtAKeyframe() {
        val written = AtomicLong(600_000)
        // Holds a seek in the index, while it's shut.
        val gate = AtomicReference<CountDownLatch?>(null)
        val chase = object : Chase {
            override fun urlAt(position: Long) = "http://127.0.0.1:9/local/r1.ts?at=$position"

            // A keyframe every 2 s, each 1,000 bytes on from the one before.
            override fun seek(ms: Long): Pair<Long, Long> {
                gate.get()?.await(10, TimeUnit.SECONDS)
                val at = ms / 2_000 * 2_000
                return at / 2 to at
            }

            override fun writtenMs() = written.get()
        }
        val recording = StreamKeys.recording("r1")
        val neon = StreamKeys.movie("home:7101")
        val resolver = StreamResolver { key ->
            if (key == recording) StreamTarget(key, "http://127.0.0.1:9/local/r1.ts", throughHub = false, chase = chase) else files.resolve(key)
        }
        val manager = PlayerManager(engine, hub, resolver, { PlaybackPrefs() }, scope)

        // From where it was left: the keyframe before, and the engine counts from there.
        onPlayer { manager.playVod(recording, startMs = 121_500) }
        waitForOpens(1)
        with(engine.opened.single()) {
            assertEquals("http://127.0.0.1:9/local/r1.ts?at=60000", url)
            assertEquals(0L, startMs)
            assertFalse(live)
        }
        onPlayer {
            engine.state.value = EngineState.Playing
            engine.hasPicture.value = true
            engine.position.value = 5_000
        }
        await(manager.position) { it == 125_000L }
        await(manager.length) { it == 600_000L }

        // It grows as it's written.
        written.set(612_000)
        await(manager.length) { it == 612_000L }

        // A seek shows where it's going until the engine opens again at the keyframe before.
        val shut = CountDownLatch(1)
        gate.set(shut)
        onPlayer { manager.seekTo(301_000) }
        await(manager.position) { it == 301_000L }
        assertEquals(1, engine.opened.size)
        shut.countDown()
        waitForOpens(2)
        assertEquals("http://127.0.0.1:9/local/r1.ts?at=150000", engine.opened[1].url)
        await(manager.position) { it == 300_000L }
        onPlayer { engine.position.value = 2_000 }
        await(manager.position) { it == 302_000L }

        // Something else plays: it's that one's length, and its place.
        onPlayer { manager.playVod(neon, startMs = 0) }
        waitForOpens(3)
        onPlayer {
            engine.length.value = 7_080_000
            engine.position.value = 1_000
        }
        await(manager.length) { it == 7_080_000L }
        await(manager.position) { it == 1_000L }
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
     * A player on a hub with the rewind buffer (brief 5.5) in [folder], playing a steady stream
     * (or the one at [path]), with [clock] for its wall clock; [use] runs with it, and it's closed
     * after. A picture and sound that stand still for [stillMs] are frozen (#91), told to [events].
     */
    private fun rewinding(
        mode: RewindMode,
        folder: File,
        clock: () -> Long = System::currentTimeMillis,
        keepMs: Long = 3_600_000,
        hubClock: () -> Long = System::currentTimeMillis,
        stillMs: Long? = PlaybackPrefs().restartFrozenMs,
        events: MutableList<PlayerEvent> = CopyOnWriteArrayList(),
        path: String = "/steady/",
        use: (PlayerManager) -> Unit,
    ) {
        val settings = RewindSettings(mode, keepMs = keepMs, maxBytes = Long.MAX_VALUE, folder = folder)
        val rewindHub = StreamHub(OkHttpClient(), HubConfig(noDataTimeoutMs = 20_000, reconnectDelaysMs = listOf(0), rewind = { settings }, clock = hubClock))
        val steady = StreamResolver { channelId -> StreamTarget(channelId, provider.url("$path$channelId.ts").toString(), budget = budget) }
        val manager = PlayerManager(
            engine, rewindHub, steady, { PlaybackPrefs(restartFrozenMs = stillMs) }, scope, onEvent = { events += it },
            freezeLookMs = LOOK_MS, clock = clock,
        )
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

    private companion object {
        /** Tests look at the picture and sound ten times a second, rather than once (#91). */
        const val LOOK_MS = 100L

        /** When Plasma left the front, by the wall clock. */
        const val STOPPED_AT = 1_793_800_000_000L
    }

    // ---- Fast Zap (brief 5.4.8) ---------------------------------------------------------------------

    private fun askedFor(channelId: String) = asked.count { it.endsWith("/$channelId.ts") }

    private fun awaitAsked(channelId: String) = runBlocking { withTimeout(10_000) { while (askedFor(channelId) == 0) kotlinx.coroutines.delay(10) } }

    @Test fun aChannelKeptReadyPlaysOnTheConnectionAlreadyOpen() {
        val two = ConnectionBudget("home", maxConnections = 2)
        val manager = withMpv(resolver = StreamResolver { id -> StreamTarget(id, provider.url("/steady/live/$id.ts").toString(), budget = two) })
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)

        onPlayer { manager.keepReady(listOf("ch106")) }
        awaitAsked("ch106")
        onPlayer { manager.show("ch106", muted = false) }
        waitForOpens(2)
        Thread.sleep(300)

        assertEquals("No second connection for the zap", 1, askedFor("ch106"))
        // ch105's connection went with the zap; ch106's is the one kept ready.
        assertEquals(1, hub.connectionsInUse("home"))
        // Kept ready for the next zap: ch105 is behind it now, and ch106 lets its spare go.
        onPlayer { manager.keepReady(listOf("ch105")) }
        val inUse = runBlocking { withTimeoutOrNull(5_000) { while (hub.connectionsInUse("home") != 2) kotlinx.coroutines.delay(20) } }
        assertEquals("ch106 and ch105 kept ready (asked: $asked)", 2, hub.connectionsInUse("home"))
        assertTrue(inUse != null)
    }

    @Test fun aChannelIsKeptReadyOnlyWithAConnectionToSpare() {
        // One connection, and ch105 has it.
        val manager = withMpv(resolver = steady)
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        waitForData()

        onPlayer { manager.keepReady(listOf("ch106")) }
        Thread.sleep(500)

        assertEquals(0, askedFor("ch106"))
        assertEquals(1, hub.connectionsInUse("home"))
    }

    @Test fun standbyLetsGoOfWhatsKeptReady() {
        val two = ConnectionBudget("home", maxConnections = 2)
        val manager = withMpv(resolver = StreamResolver { id -> StreamTarget(id, provider.url("/steady/live/$id.ts").toString(), budget = two) })
        onPlayer { manager.show("ch105", muted = false) }
        waitForOpens(1)
        onPlayer { manager.keepReady(listOf("ch106")) }
        awaitAsked("ch106")

        onPlayer { manager.standby(graceMs = 0) }
        // The connections close on the hub's own threads, which take longer on a busy machine than
        // any set time: wait for them. The hub closes nothing on a timer, so they close only if
        // standby lets them go.
        val deadline = System.nanoTime() + 5_000_000_000L
        while (hub.connectionsInUse("home") > 0 && System.nanoTime() < deadline) Thread.sleep(10)

        assertEquals(0, hub.connectionsInUse("home"))
    }
}
