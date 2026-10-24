// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import ca.gureet.plasma.core.data.CopyState
import ca.gureet.plasma.core.data.NetworkFolder
import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingKind
import ca.gureet.plasma.core.data.RecordingProblems
import ca.gureet.plasma.core.data.RecordingStatus
import ca.gureet.plasma.core.data.RecordingStore
import ca.gureet.plasma.stream.HubConfig
import ca.gureet.plasma.stream.StreamHub
import ca.gureet.plasma.stream.UpstreamRequest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Writing a recording from the stream hub to its file (brief 5.6). */
class RecorderTest {
    private val provider = MockWebServer()
    private val folder: File = Files.createTempDirectory("recordings").toFile()
    private lateinit var hub: StreamHub

    @Before fun start() {
        provider.start()
        // One quick retry, then the upstream gives up and the recorder opens the channel again.
        hub = StreamHub(OkHttpClient(), HubConfig(noDataTimeoutMs = 2_000, reconnectDelaysMs = listOf(0)))
    }

    @After fun stop() {
        hub.close()
        provider.close()
        folder.deleteRecursively()
        shareRoot.deleteRecursively()
    }

    /** What the recorder says, as the database would keep it. */
    private class Store : RecordingStore {
        var file: String? = null
        var size = 0L
        var interruptions = 0
        var finished: Pair<Boolean, String?>? = null
        @Volatile var spool: String? = null
        var networkPath: String? = null
        var copy = CopyState.None

        /** The recording, as [get] gives it; null after it's [deleted]. */
        var recording: Recording? = null
        @Volatile var deleted = false

        override suspend fun pending(): List<Recording> = emptyList()

        override suspend fun get(id: String): Recording? =
            recording?.takeIf { !deleted }?.copy(file = file, spool = spool, networkPath = networkPath, copy = copy, sizeBytes = size)

        override suspend fun startedOnShare(id: String, spool: String, networkPath: String) {
            this.spool = spool
            this.networkPath = networkPath
            copy = CopyState.Copying
        }

        override suspend fun setSpool(id: String, spool: String?) {
            this.spool = spool
        }

        override suspend fun setCopy(id: String, state: CopyState, networkPath: String?, error: String?) {
            copy = state
            this.networkPath = networkPath
        }

        override suspend fun copied(id: String, networkPath: String) {
            copy = CopyState.Copied
            this.networkPath = networkPath
        }

        override suspend fun started(id: String, file: String) {
            this.file = file
        }

        override suspend fun progress(id: String, sizeBytes: Long) {
            size = sizeBytes
        }

        override suspend fun interrupted(id: String) {
            interruptions++
        }

        override suspend fun finished(id: String, failed: Boolean, problem: String?) {
            finished = failed to problem
        }
    }

    private val store = Store()

    /** The network folder, and what it heard: that it was away, and copies there. */
    private val shareRoot: File = Files.createTempDirectory("share").toFile()
    private val share = FolderShare(shareRoot)
    private val networkFolder = NetworkFolder("nas.home.lan", "Media", "Recordings", "maya", "s3cret")
    private var heardAway = 0
    private var heardCopied = 0

    /**
     * A host on the hub, opening the provider's channel, or saying why it can't; with Record To ›
     * Network folder when [straight].
     */
    private fun host(problem: String? = null, folder: File? = this.folder, straight: Boolean = false) = object : RecordingHost {
        override val recordings: RecordingStore = store
        override val zone: ZoneId = ZoneOffset.UTC

        override suspend fun openForRecording(channelId: String): RecordingHost.Opened =
            problem?.let { RecordingHost.Opened.Problem(it) }
                ?: RecordingHost.Opened.Stream(hub.open(UpstreamRequest(provider.url("/live/$channelId.ts").toString())))

        override fun recordingFolder(): File? = folder

        override suspend fun makeRoom(folder: File): Boolean = true

        override suspend fun recordStraightTo(): RecordingHost.CopyTo? = RecordingHost.CopyTo(networkFolder, jellyfin = false).takeIf { straight }

        override suspend fun copyTo(): RecordingHost.CopyTo? = recordStraightTo()

        override suspend fun copyWaiting(folder: NetworkFolder) {
            heardAway++
        }

        override suspend fun copied(recording: Recording, freeBytes: Long?) {
            heardCopied++
        }
    }

    /** [count] packets of made-up MPEG-TS, numbered from [from]. */
    private fun packets(count: Int, from: Int = 0): ByteArray = ByteArray(count * PACKET) { i ->
        if (i % PACKET == 0) SYNC else ((from + i / PACKET) % NUMBERS).toByte()
    }

    /** [bytes], then the end of the stream; or with [keepOpen], nothing more for half a minute. */
    private fun stream(bytes: ByteArray, keepOpen: Boolean) = MockResponse.Builder()
        .addHeader("Content-Type", "video/mp2t")
        .body(Buffer().write(if (keepOpen) bytes + ByteArray(bytes.size) else bytes))
        .apply { if (keepOpen) throttleBody(bytes.size.toLong(), 30, TimeUnit.SECONDS) }
        .build()

    /** A recording of channel 105 that ends [forMs] from now. */
    private fun recording(forMs: Long): Recording {
        val start = Instant.parse("2026-10-21T20:41:00Z")
        val now = Instant.now()
        return Recording(
            id = "home:105@${start.epochSecond}",
            channelId = "home:105",
            channelName = "Arena Sports 1",
            channelLogo = null,
            title = "Fight Night: Main Card",
            subtitle = null,
            description = "Five fights.",
            category = "Sports",
            image = null,
            season = null,
            episode = null,
            kind = RecordingKind.Sports,
            start = start,
            end = now.plusMillis(forMs),
            padBefore = Duration.ZERO,
            padAfter = Duration.ZERO,
            status = RecordingStatus.Scheduled,
            file = null,
            sizeBytes = 0,
            recordedFrom = null,
            recordedTo = null,
            interruptions = 0,
            problem = null,
            keep = false,
            positionMs = 0,
            watched = false,
            watchedAt = null,
        )
    }

    @Test fun writesTheStreamToItsFileUntilItsEnd() = runBlocking {
        val bytes = packets(50)
        provider.enqueue(stream(bytes, keepOpen = true))

        Recorder(host()).record(recording(forMs = 1_500))

        val file = File(store.file!!)
        assertEquals("Arena Sports 1 – Fight Night - Main Card – 2026-10-21 2041.ts", file.name)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(bytes.size.toLong(), store.size)
        assertEquals(false to null, store.finished)
        assertEquals(0, store.interruptions)
        // What the guide said goes beside it, with the keyframe index.
        val sidecar = RecordingFiles.sidecarOf(file).readText()
        assertTrue(sidecar, "\"title\":\"Fight Night: Main Card\"" in sidecar && "\"channel\":\"Arena Sports 1\"" in sidecar)
        assertTrue(RecordingFiles.indexOf(file).exists())
    }

    @Test fun opensTheChannelAgainWhenTheStreamStops() = runBlocking {
        val first = packets(20)
        val second = packets(30, from = 20)
        provider.enqueue(stream(first, keepOpen = false))
        // The hub's own retry finds nothing, so it gives up; the recorder opens the channel again.
        provider.enqueue(MockResponse.Builder().code(404).build())
        provider.enqueue(stream(second, keepOpen = true))

        Recorder(host(), retryMs = 100).record(recording(forMs = 2_500))

        assertArrayEquals(first + second, File(store.file!!).readBytes())
        assertEquals(1, store.interruptions)
        assertEquals(false to null, store.finished)
    }

    @Test fun aChannelThatCantBeOpenedFailsAndLeavesNoFile() = runBlocking {
        Recorder(host(problem = RecordingProblems.NO_CONNECTION), retryMs = 100).record(recording(forMs = 500))

        assertEquals(true to RecordingProblems.NO_CONNECTION, store.finished)
        assertFalse(File(store.file!!).exists())
        assertTrue(folder.listFiles()!!.isEmpty())
    }

    @Test fun nowhereToWriteFails() = runBlocking {
        Recorder(host(folder = null)).record(recording(forMs = 500))

        assertEquals(true to RecordingProblems.NO_ROOM, store.finished)
    }

    // ---- Record To › Network folder (brief 5.6) --------------------------------------------------

    private val onShare = "Recordings/Arena Sports 1 – Fight Night - Main Card – 2026-10-21 2041.ts"

    @Test fun writesStraightToTheNetworkFolder() = runBlocking {
        val bytes = packets(50)
        provider.enqueue(stream(bytes, keepOpen = true))
        val recording = recording(forMs = 1_500)
        store.recording = recording

        Recorder(host(straight = true), openShare = { share }).record(recording)

        assertArrayEquals(bytes, File(shareRoot, onShare).readBytes())
        assertFalse(File(shareRoot, "$onShare.partial").exists())
        assertEquals(CopyState.Copied, store.copy)
        assertEquals("smb://nas.home.lan/Media/$onShare", store.networkPath)
        assertEquals(1, heardCopied)
        // Nothing of it stays on this TV.
        assertEquals(null, store.file)
        assertEquals(null, store.spool)
        assertTrue(File(folder, Recorder.SPOOLS).listFiles().orEmpty().isEmpty())
        assertEquals(bytes.size.toLong(), store.size)
        assertEquals(false to null, store.finished)
    }

    @Test fun aNetworkFolderAwayAtTheStartRecordsHereToCopyLater() = runBlocking {
        val bytes = packets(50)
        provider.enqueue(stream(bytes, keepOpen = true))
        share.away = true

        Recorder(host(straight = true), openShare = { share }).record(recording(forMs = 1_500))

        assertArrayEquals(bytes, File(store.file!!).readBytes())
        assertEquals(1, heardAway)
        assertEquals(null, store.spool)
        assertTrue(shareRoot.listFiles().orEmpty().isEmpty())
    }

    @Test fun aNetworkFolderThatDropsKeepsTheRestHereForLater() = runBlocking {
        val bytes = packets(50)
        provider.enqueue(stream(bytes, keepOpen = true))
        val recording = recording(forMs = 1_500)
        store.recording = recording
        // The share's there to plan where it goes, then it drops.
        var opens = 0
        val dropping = { _: NetworkFolder -> if (opens++ == 0) share else throw ShareException(ShareProblem.CantReach) }

        Recorder(host(straight = true), openShare = dropping, shareRetryMs = 20, shareGiveUpMs = 100).record(recording)

        assertEquals(CopyState.Waiting, store.copy)
        assertEquals("smb://nas.home.lan/Media/$onShare", store.networkPath)
        assertTrue(heardAway > 0)
        // All of it waits here, in the spool, for the copy job.
        Spool(File(store.spool!!)).use { spool ->
            assertEquals(bytes.size.toLong(), spool.length)
            val back = ByteArray(bytes.size)
            var at = 0
            while (at < back.size) at += spool.read(at.toLong(), back, at, back.size - at)
            assertArrayEquals(bytes, back)
        }
        assertEquals(false to null, store.finished)
    }

    @Test fun deletedWhileItsWrittenNothingStaysHereOrThere() = runBlocking {
        provider.enqueue(stream(packets(50), keepOpen = true))
        val recording = recording(forMs = 30_000)
        store.recording = recording
        val writing = launch(Dispatchers.IO) { Recorder(host(straight = true), openShare = { share }).record(recording) }
        withTimeout(10_000) { while (!File(shareRoot, "$onShare.partial").exists()) delay(10) }
        val spool = File(store.spool!!)

        store.deleted = true
        writing.cancelAndJoin()

        assertFalse(spool.exists())
        assertFalse(File(shareRoot, "$onShare.partial").exists())
        assertEquals(null, store.finished)
    }

    private companion object {
        const val PACKET = 188
        const val SYNC: Byte = 0x47
        const val NUMBERS = 64
    }
}
