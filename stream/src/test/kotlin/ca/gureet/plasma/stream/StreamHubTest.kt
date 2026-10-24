// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class StreamHubTest {
    private val provider = MockWebServer()
    private val http = OkHttpClient()
    private lateinit var hub: StreamHub

    private val quick = HubConfig(noDataTimeoutMs = 2_000, reconnectDelaysMs = listOf(0, 50, 50, 50))

    /** Where the rewind buffer goes (brief 5.5). */
    private val folder = Files.createTempDirectory("rewind").toFile()

    private fun rewinding(mode: RewindMode) = quick.copy(rewind = { RewindSettings(mode, keepMs = 3_600_000, maxBytes = Long.MAX_VALUE, folder = folder) })

    @Before fun start() = provider.start()

    @After fun stop() {
        if (::hub.isInitialized) hub.close()
        provider.close()
        folder.deleteRecursively()
    }

    private fun stream(bytes: ByteArray) = MockResponse.Builder()
        .addHeader("Content-Type", "video/mp2t")
        .body(Buffer().write(bytes))
        .build()

    /** A response that never comes, so the upstream waits between reconnects. */
    private val silence = MockResponse.Builder().headersDelay(30, TimeUnit.SECONDS).build()

    private fun readFrom(session: HubSession, bytes: Int): ByteArray {
        val response = http.newCall(Request.Builder().url(session.url).build()).execute()
        return response.use { it.body.source().readByteArray(bytes.toLong()) }
    }

    private fun awaitState(session: HubSession, predicate: (UpstreamState) -> Boolean): UpstreamState = runBlocking {
        withTimeout(10_000) { session.state.first(predicate) }
    }

    @Test fun servesTheProviderStreamOnLoopback() {
        hub = StreamHub(http, quick)
        val packets = tsPackets(50)
        provider.enqueue(stream(packets))
        provider.enqueue(silence)

        val session = hub.open(UpstreamRequest(provider.url("/live/viewer/s3cret/9105.ts").toString(), mapOf("User-Agent" to "VLC/3.0.21 LibVLC/3.0.21")))

        assertTrue(session.url, session.url.startsWith("http://127.0.0.1:${hub.port}/live/"))
        assertTrue("The engine never sees the credentials", "s3cret" !in session.url)
        assertArrayEquals(packets, readFrom(session, packets.size))
        assertEquals("VLC/3.0.21 LibVLC/3.0.21", provider.takeRequest().headers["User-Agent"])
        assertEquals(packets.size.toLong(), session.bytesReceived)
    }

    @Test fun servesWhatPlasmaReadsItselfUntilItsClosed() {
        hub = StreamHub(http, quick)
        val packets = tsPackets(20)
        val opened = AtomicInteger()
        val local = hub.serve {
            opened.incrementAndGet()
            object : LocalReader {
                private var at = 0

                override fun read(into: ByteArray, waitMs: Long): Int {
                    if (at >= packets.size) return -1
                    val count = minOf(into.size, packets.size - at, 1_000)
                    System.arraycopy(packets, at, into, 0, count)
                    at += count
                    return count
                }

                override fun close() = Unit
            }
        }

        assertTrue(local.url, local.url.startsWith("http://127.0.0.1:${hub.port}/local/"))
        val read = http.newCall(Request.Builder().url(local.url).build()).execute().use { it.body.bytes() }
        assertArrayEquals(packets, read)
        // Each request reads it afresh.
        http.newCall(Request.Builder().url(local.url).build()).execute().close()
        assertEquals(2, opened.get())

        local.close()
        assertEquals(404, http.newCall(Request.Builder().url(local.url).build()).execute().use { it.code })
    }

    @Test fun aLaterReaderStartsAtTheLatestPat() {
        hub = StreamHub(http, quick)
        provider.enqueue(stream(tsPackets(50, patEvery = 10)))
        provider.enqueue(silence)
        val url = provider.url("/live/9105.ts").toString()
        val first = hub.open(UpstreamRequest(url))
        readFrom(first, 50 * 188)

        // An engine switch, say: the new reader joins the same upstream and starts at packet 40.
        val second = hub.open(UpstreamRequest(url))
        val joined = readFrom(second, 10 * 188)

        assertEquals(40, counterOf(joined, packet = 0))
        assertEquals(49, counterOf(joined, packet = 9))
        assertSame("Both readers share one provider connection", first.upstream, second.upstream)
    }

    @Test fun reconnectsWhenTheStreamDrops() {
        hub = StreamHub(http, quick)
        provider.enqueue(stream(tsPackets(20)))
        provider.enqueue(stream(tsPackets(20, firstCounter = 100)))
        provider.enqueue(silence)

        val session = hub.open(UpstreamRequest(provider.url("/live/9105.ts").toString()))
        val bytes = readFrom(session, 40 * 188)

        assertEquals(0, counterOf(bytes, packet = 0))
        assertEquals(100, counterOf(bytes, packet = 20))
        assertTrue(provider.requestCount >= 2)
    }

    @Test fun refusalsAndPagesFailWithoutRetrying() {
        hub = StreamHub(http, quick)
        provider.enqueue(MockResponse(code = 403))
        val refused = hub.open(UpstreamRequest(provider.url("/live/a.ts").toString()))
        assertEquals(UpstreamState.Failed(StreamFailure.Refused(403)), awaitState(refused) { it is UpstreamState.Failed })

        provider.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/html").body("<html>Sorry</html>").build())
        val page = hub.open(UpstreamRequest(provider.url("/live/b.ts").toString()))
        assertEquals(UpstreamState.Failed(StreamFailure.NotVideo), awaitState(page) { it is UpstreamState.Failed })

        // An error page sent as if it were video, with status 200.
        provider.enqueue(MockResponse.Builder().addHeader("Content-Type", "video/mp2t").body("  <html>Too many connections</html>").build())
        val disguised = hub.open(UpstreamRequest(provider.url("/live/c.ts").toString()))
        assertEquals(UpstreamState.Failed(StreamFailure.NotVideo), awaitState(disguised) { it is UpstreamState.Failed })

        assertEquals(3, provider.requestCount)
    }

    @Test fun givesUpAfterFourRetries() {
        hub = StreamHub(http, quick)
        repeat(5) { provider.enqueue(MockResponse(code = 502)) }

        val session = hub.open(UpstreamRequest(provider.url("/live/9105.ts").toString()))

        assertEquals(UpstreamState.Failed(StreamFailure.Http(502)), awaitState(session) { it is UpstreamState.Failed })
        assertEquals(5, provider.requestCount)
    }

    @Test fun aSourceThatSendsNothingFails() {
        hub = StreamHub(http, quick.copy(noDataTimeoutMs = 200))
        provider.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse.Builder()
                .addHeader("Content-Type", "video/mp2t")
                .body(Buffer().write(tsPackets(1)))
                .bodyDelay(5, TimeUnit.SECONDS)
                .build()
        }

        val session = hub.open(UpstreamRequest(provider.url("/live/9105.ts").toString()))

        assertEquals(UpstreamState.Failed(StreamFailure.NoData), awaitState(session) { it is UpstreamState.Failed })
    }

    @Test fun refusesUnknownPathsAndMethods() {
        hub = StreamHub(http, quick)
        val notFound = URL("http://127.0.0.1:${hub.port}/live/not-a-token.ts").openConnection() as HttpURLConnection
        assertEquals(404, notFound.responseCode)

        provider.enqueue(stream(tsPackets(5)))
        provider.enqueue(silence)
        val session = hub.open(UpstreamRequest(provider.url("/live/9105.ts").toString()))
        val post = URL(session.url).openConnection() as HttpURLConnection
        post.requestMethod = "POST"
        post.doOutput = true
        post.outputStream.close()
        assertEquals(405, post.responseCode)
    }

    @Test fun theLastSessionToCloseFreesTheConnection() {
        hub = StreamHub(http, quick)
        provider.enqueue(stream(tsPackets(5)))
        provider.enqueue(silence)
        val url = provider.url("/live/9105.ts").toString()
        val budget = ConnectionBudget("home", maxConnections = 1)
        val watching = hub.open(UpstreamRequest(url, budget = budget))
        val recording = hub.open(UpstreamRequest(url, budget = budget))
        assertEquals(1, hub.connectionsInUse("home"))

        watching.close()
        assertEquals(1, hub.connectionsInUse("home"))
        recording.close()

        assertEquals(UpstreamState.Closed, recording.state.value)
        assertEquals(0, hub.connectionsInUse("home"))
    }

    @Test fun watchingAndRecordingAChannelShareItsConnectionWhateverItsAddress() {
        hub = StreamHub(http, quick)
        provider.enqueue(stream(tsPackets(5)))
        provider.enqueue(silence)
        // A portal hands each its own address for the same channel (brief 5.6).
        val watching = hub.open(UpstreamRequest(provider.url("/live/9105.ts?play_token=1").toString(), shareKey = "home:105"))
        val recording = hub.open(UpstreamRequest(provider.url("/live/9105.ts?play_token=2").toString(), shareKey = "home:105"))

        assertSame(watching.upstream, recording.upstream)
        assertEquals(5 * 188, recording.reader().let { reader ->
            val into = ByteArray(10 * 188)
            var total = 0
            while (total < 5 * 188) total += reader.read(into, 1_000).coerceAtLeast(0)
            total
        })
        // Only the first address is asked for, again when it reconnects; the second never is.
        val tokens = generateSequence { provider.takeRequest(100, TimeUnit.MILLISECONDS) }.map { it.url.queryParameter("play_token") }.toList()
        assertEquals("1", tokens.first())
        assertFalse("2" in tokens)
    }

    @Test fun keepsToTheAccountsConnections() {
        hub = StreamHub(http, quick)
        provider.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = silence
        }
        val budget = ConnectionBudget("home", maxConnections = 1)
        val arena = hub.open(UpstreamRequest(provider.url("/live/9105.ts").toString(), budget = budget))

        var refused: ConnectionLimitReached? = null
        try {
            hub.open(UpstreamRequest(provider.url("/live/9107.ts").toString(), budget = budget))
        } catch (error: ConnectionLimitReached) {
            refused = error
        }
        assertEquals(1, refused?.maxConnections)

        // Zapping on a one-connection account closes the old channel first.
        arena.close()
        hub.open(UpstreamRequest(provider.url("/live/9107.ts").toString(), budget = budget)).close()
    }

    @Test fun aPauseStartsTheRewindBufferWithWhatMemoryHolds() {
        hub = StreamHub(http, rewinding(RewindMode.WhenPaused))
        provider.enqueue(stream(tsPackets(50)))
        provider.enqueue(silence)
        val session = hub.open(UpstreamRequest(provider.url("/live/9105.ts").toString()))
        readFrom(session, 50 * 188)
        assertNull("Nothing is written before a pause", session.rewind)

        assertTrue(session.hold())

        val rewind = session.rewind!!
        assertTrue(rewind.since != null)
        assertEquals(0L, rewind.positionAt(0))
        assertEquals(1, folder.listFiles()!!.size)

        // A new channel lets it go.
        session.close()
        assertEquals(0, folder.listFiles()!!.size)
    }

    @Test fun withTheBufferOffThereIsNothingToHold() {
        hub = StreamHub(http, quick)
        provider.enqueue(stream(tsPackets(5)))
        provider.enqueue(silence)
        val session = hub.open(UpstreamRequest(provider.url("/live/9105.ts").toString()))

        assertFalse(session.hold())
        assertNull(session.rewind)
    }

    @Test fun aSeekServesTheTablesThenTheStreamFromThere() {
        hub = StreamHub(http, rewinding(RewindMode.Always))
        val packets = Ts.stream(Ts.pat(), Ts.pmt(TsScanner.TYPE_H264), Ts.h264(key = false), Ts.h264(key = true), Ts.audio(), Ts.h264(key = false), Ts.audio())
        provider.enqueue(stream(packets))
        provider.enqueue(silence)
        val session = hub.open(UpstreamRequest(provider.url("/live/9105.ts").toString()))
        readFrom(session, packets.size)
        assertTrue("Kept from the start", session.rewind != null)

        val from = 3 * 188
        val bytes = http.newCall(Request.Builder().url(session.urlAt(from.toLong())).build()).execute().use {
            it.body.source().readByteArray((2 * 188 + packets.size - from).toLong())
        }

        assertArrayEquals(Ts.pat() + Ts.pmt(TsScanner.TYPE_H264), bytes.copyOf(2 * 188))
        assertArrayEquals(packets.copyOfRange(from, packets.size), bytes.copyOfRange(2 * 188, bytes.size))
    }

    @Test fun goLiveServesFromTheLatestPat() {
        hub = StreamHub(http, quick)
        provider.enqueue(stream(tsPackets(50, patEvery = 10)))
        provider.enqueue(silence)
        val session = hub.open(UpstreamRequest(provider.url("/live/9105.ts").toString()))
        readFrom(session, 50 * 188)

        val joined = http.newCall(Request.Builder().url(session.liveUrl).build()).execute().use { it.body.source().readByteArray(10L * 188) }

        assertEquals(40, counterOf(joined, packet = 0))
    }

    companion object {
        /**
         * Made-up MPEG-TS: 188-byte packets with a PAT (PID 0) every [patEvery] packets, and a
         * running counter in each payload so tests can tell packets apart.
         */
        fun tsPackets(count: Int, patEvery: Int = 10, firstCounter: Int = 0): ByteArray {
            val bytes = ByteArray(count * Ring.TS_PACKET) { 0x11 }
            for (i in 0 until count) {
                val at = i * Ring.TS_PACKET
                val pat = i % patEvery == 0
                val pid = if (pat) 0 else 0x100
                bytes[at] = Ring.SYNC.toByte()
                bytes[at + 1] = ((if (pat) 0x40 else 0) or (pid shr 8)).toByte()
                bytes[at + 2] = (pid and 0xFF).toByte()
                bytes[at + 3] = 0x10
                val counter = firstCounter + i
                bytes[at + 4] = (counter shr 8).toByte()
                bytes[at + 5] = (counter and 0xFF).toByte()
            }
            return bytes
        }

        fun counterOf(bytes: ByteArray, packet: Int): Int {
            val at = packet * Ring.TS_PACKET
            return ((bytes[at + 4].toInt() and 0xFF) shl 8) or (bytes[at + 5].toInt() and 0xFF)
        }
    }
}
