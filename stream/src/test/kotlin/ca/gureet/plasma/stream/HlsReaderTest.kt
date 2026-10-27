// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** An HLS channel read a segment at a time for a recording (brief 5.6). */
class HlsReaderTest {
    private val provider = MockWebServer()
    private val http = OkHttpClient()

    /** How many times the media playlist was asked for. */
    private val playlistAsked = AtomicInteger()

    /** What each path answers; a function of how often the playlist has been asked for. */
    private var routes: (path: String) -> MockResponse? = { null }

    @Before fun start() {
        provider.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = routes(request.url.encodedPath) ?: MockResponse(code = 404)
        }
        provider.start()
    }

    @After fun stop() {
        provider.close()
    }

    /** Segment [n]: two packets of made-up MPEG-TS saying which it is. */
    private fun segment(n: Int): ByteArray = ByteArray(376) { if (it % 188 == 0) 0x47 else n.toByte() }

    private fun text(body: String) = MockResponse.Builder().addHeader("Content-Type", "application/vnd.apple.mpegurl").body(body).build()

    private fun bytes(body: ByteArray) = MockResponse.Builder().addHeader("Content-Type", "video/mp2t").body(Buffer().write(body)).build()

    private fun media(first: Int, last: Int, ended: Boolean = false, extra: String = "") = text(
        buildString {
            append("#EXTM3U\n#EXT-X-TARGETDURATION:1\n#EXT-X-MEDIA-SEQUENCE:$first\n$extra")
            for (n in first..last) append("#EXTINF:1.0,\nseg$n.ts\n")
            if (ended) append("#EXT-X-ENDLIST\n")
        },
    )

    /** Reads [reader] until it has [count] bytes, or ends. */
    private fun HlsReader.readUntil(count: Int, timeoutMs: Long = 10_000): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1_000)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (out.size() < count && System.currentTimeMillis() < deadline) {
            val got = read(buffer, 200)
            if (got < 0) break
            out.write(buffer, 0, got)
        }
        return out.toByteArray()
    }

    @Test fun theBestVariantFromAFewSegmentsBehindLiveThenWhatsNew() {
        routes = { path ->
            when {
                path.endsWith("/105.m3u8") -> text("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000\nlow.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=5000000\nhigh.m3u8\n")
                path.endsWith("/high.m3u8") -> if (playlistAsked.incrementAndGet() == 1) media(10, 14) else media(11, 15)
                path.endsWith(".ts") -> bytes(segment(path.substringAfterLast("seg").removeSuffix(".ts").toInt()))
                else -> null
            }
        }

        HlsReader(http, provider.url("/live/viewer/s3cret/105.m3u8").toString()).use { reader ->
            val read = reader.readUntil(4 * 376)

            assertArrayEquals(segment(12) + segment(13) + segment(14) + segment(15), read)
            assertFalse(reader.unsupported)
        }
        assertTrue(provider.takeRequest().url.encodedPath.endsWith("/105.m3u8"))
    }

    @Test fun aes128SegmentsAreDecrypted() {
        val key = ByteArray(16) { (it * 7).toByte() }
        val iv = ByteArray(16) { 3 }

        fun encrypt(plain: ByteArray, with: ByteArray) = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(with))
            doFinal(plain)
        }
        // Without an IV in the playlist, it's the segment's number.
        val numbered = ByteArray(16).also { it[15] = 6 }
        routes = { path ->
            when {
                path.endsWith("/105.m3u8") -> text(
                    "#EXTM3U\n#EXT-X-TARGETDURATION:1\n#EXT-X-MEDIA-SEQUENCE:5\n" +
                        "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\",IV=0x03030303030303030303030303030303\n#EXTINF:1.0,\nseg5.ts\n" +
                        "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n#EXTINF:1.0,\nseg6.ts\n#EXT-X-ENDLIST\n",
                )
                path.endsWith("/key.bin") -> bytes(key)
                path.endsWith("/seg5.ts") -> bytes(encrypt(segment(5), iv))
                path.endsWith("/seg6.ts") -> bytes(encrypt(segment(6), numbered))
                else -> null
            }
        }

        HlsReader(http, provider.url("/live/viewer/s3cret/105.m3u8").toString()).use { reader ->
            // One that's ended reads from its start, then ends.
            assertArrayEquals(segment(5) + segment(6), reader.readUntil(Int.MAX_VALUE))
            assertEquals(-1, reader.read(ByteArray(10), 10))
        }
    }

    @Test fun fmp4CantBeRecorded() {
        routes = { path -> if (path.endsWith("/105.m3u8")) media(1, 3, extra = "#EXT-X-MAP:URI=\"init.mp4\"\n") else null }

        HlsReader(http, provider.url("/live/viewer/s3cret/105.m3u8").toString()).use { reader ->
            assertEquals(-1, reader.read(ByteArray(10), 1_000))
            assertTrue(reader.unsupported)
        }
    }

    @Test fun aSegmentThatCantBeHadIsAGap() {
        routes = { path ->
            when {
                path.endsWith("/105.m3u8") -> media(1, 3, ended = true)
                path.endsWith("/seg2.ts") -> null
                path.endsWith(".ts") -> bytes(segment(path.substringAfterLast("seg").removeSuffix(".ts").toInt()))
                else -> null
            }
        }

        HlsReader(http, provider.url("/live/viewer/s3cret/105.m3u8").toString()).use { reader ->
            assertArrayEquals(segment(1) + segment(3), reader.readUntil(Int.MAX_VALUE))
        }
    }

    @Test fun aPlaylistThatCantBeHadForLongEndsIt() {
        HlsReader(http, provider.url("/live/viewer/s3cret/105.m3u8").toString(), giveUpMs = 100).use { reader ->
            assertEquals(0, reader.readUntil(1, timeoutMs = 10_000).size)
            assertEquals(-1, reader.read(ByteArray(10), 10))
            assertFalse(reader.unsupported)
        }
    }

    @Test fun closedItLetsGoOfItsConnection() {
        var released = 0
        HlsReader(http, provider.url("/live/viewer/s3cret/105.m3u8").toString(), onClose = { released++ }).use { }

        assertEquals(1, released)
    }
}
