// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.media3

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/** Reading a movie's start for its picture (#113), from a Matroska file made here. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class Media3ProbeTest {
    private val asked = mutableListOf<String?>()

    @Test fun aDolbyVisionRecordMakesItDolbyVision() {
        // Profile 5, level 6, as streaming services' Dolby Vision without an HDR10 base.
        assertEquals(ProbedVideo(VideoRange.DolbyVision, 3840, 2160), Media3Probe.read(InMemory(movie(dolbyVision = true))))
    }

    @Test fun withoutOneItGoesByItsTransfer() {
        assertEquals(ProbedVideo(VideoRange.Hdr10, 3840, 2160), Media3Probe.read(InMemory(movie(transfer = PQ))))
        assertEquals(ProbedVideo(VideoRange.Hlg, 3840, 2160), Media3Probe.read(InMemory(movie(transfer = HLG))))
        assertEquals(ProbedVideo(VideoRange.Sdr, 3840, 2160), Media3Probe.read(InMemory(movie())))
    }

    @Test fun aFileWithNoPictureHasNone() {
        assertNull(Media3Probe.read(InMemory(movie(video = false))))
        assertNull(Media3Probe.read(InMemory(ByteArray(4096) { 0x5A })))
    }

    @Test fun itsReadOverHttpWithTheHeadersItsPlayedWith() = MockWebServer().use { server ->
        val file = movie(dolbyVision = true) + ByteArray(64 * 1024)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked += request.headers["User-Agent"]
                return MockResponse.Builder().addHeader("Content-Type", "video/x-matroska").body(Buffer().write(file)).build()
            }
        }
        server.start()

        val video = runBlocking { Media3Probe.video(OkHttpClient(), server.url("/movie/viewer/s3cret/7.mkv").toString(), mapOf("User-Agent" to "VLC/3.0.21"), 5_000) }

        assertEquals(VideoRange.DolbyVision, video?.range)
        assertEquals(listOf<String?>("VLC/3.0.21"), asked)
    }

    @Test fun aServerThatRefusesItLeavesItUnknown() = MockWebServer().use { server ->
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse(code = 403)
        }
        server.start()
        assertNull(runBlocking { Media3Probe.video(OkHttpClient(), server.url("/movie/viewer/s3cret/7.mkv").toString(), emptyMap(), 5_000) })
    }

    @Test fun mediaThreeNamesTheRanges() {
        val hevc = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265)
        assertEquals(VideoRange.DolbyVision, Media3Probe.rangeOf(Format.Builder().setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).build()))
        assertEquals(VideoRange.DolbyVision, Media3Probe.rangeOf(hevc.setCodecs("dvh1.08.06").build()))
        assertEquals(VideoRange.Hdr10, Media3Probe.rangeOf(hevc.setCodecs(null).setColorInfo(colors(C.COLOR_TRANSFER_ST2084)).build()))
        assertEquals(VideoRange.Hlg, Media3Probe.rangeOf(hevc.setColorInfo(colors(C.COLOR_TRANSFER_HLG)).build()))
        assertEquals(VideoRange.Sdr, Media3Probe.rangeOf(hevc.setColorInfo(colors(C.COLOR_TRANSFER_SDR)).build()))
    }

    private fun colors(transfer: Int) = ColorInfo.Builder().setColorSpace(C.COLOR_SPACE_BT2020).setColorRange(C.COLOR_RANGE_LIMITED).setColorTransfer(transfer).build()

    /** A file in memory, opened anywhere. */
    private class InMemory(private val bytes: ByteArray) : SeekableFile {
        private var at = 0

        override fun open(position: Long): Long {
            at = position.toInt()
            return (bytes.size - at).toLong()
        }

        override val reader = DataReader { buffer, offset, length ->
            if (at >= bytes.size) return@DataReader C.RESULT_END_OF_INPUT
            val read = minOf(length, bytes.size - at)
            System.arraycopy(bytes, at, buffer, offset, read)
            at += read
            read
        }

        override fun close() = Unit
    }

    private companion object {
        /** Matroska's transfer characteristics: SMPTE ST 2084 (PQ) and ARIB STD-B67 (HLG). */
        const val PQ = 16L
        const val HLG = 18L

        /**
         * A Matroska file's start: its header, then a 4K HEVC track with or without a Dolby Vision
         * configuration and a transfer, or only a sound track.
         */
        fun movie(dolbyVision: Boolean = false, transfer: Long? = null, video: Boolean = true): ByteArray {
            val header = element(
                0x1A45DFA3,
                uint(0x4286, 1), uint(0x42F7, 1), uint(0x42F2, 4), uint(0x42F3, 8),
                text(0x4282, "matroska"), uint(0x4287, 4), uint(0x4285, 2),
            )
            val picture = element(
                0xAE,
                uint(0xD7, 1), uint(0x73C5, 1), uint(0x83, 1),
                text(0x86, "V_MPEGH/ISO/HEVC"),
                // An HEVC configuration with no parameter sets: what Media3 needs to take the track.
                element(0x63A2, ByteArray(21).also { it[0] = 1 } + byteArrayOf(0x0F, 0)),
                element(
                    0xE0,
                    uint(0xB0, 3840), uint(0xBA, 2160),
                    *listOfNotNull(transfer?.let { element(0x55B0, uint(0x55BA, it), uint(0x55BB, 9)) }).toTypedArray(),
                ),
                *listOfNotNull(
                    if (dolbyVision) {
                        // dvcC: version 1.0, profile 5, level 6, with an RPU and a base layer.
                        element(0x41E4, uint(0x41E7, 0x64766343), element(0x41ED, byteArrayOf(1, 0, 0x0A, 0x35) + ByteArray(20)))
                    } else {
                        null
                    },
                ).toTypedArray(),
            )
            val sound = element(0xAE, uint(0xD7, 2), uint(0x73C5, 2), uint(0x83, 2), text(0x86, "A_AC3"), element(0xE1, uint(0x9F, 2)))
            val segment = element(
                0x18538067,
                element(0x1549A966, uint(0x2AD7B1, 1_000_000)),
                element(0x1654AE6B, *listOfNotNull(picture.takeIf { video }, sound).toTypedArray()),
            )
            return header + segment
        }

        fun element(id: Long, vararg children: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            children.forEach(out::write)
            val payload = out.toByteArray()
            // The id as written, then its size in eight bytes.
            val idBytes = (3 downTo 0).map { (id shr (it * 8)).toInt().toByte() }.dropWhile { it == 0.toByte() }.toByteArray()
            val size = byteArrayOf(1) + (6 downTo 0).map { (payload.size.toLong() shr (it * 8)).toByte() }.toByteArray()
            return idBytes + size + payload
        }

        fun uint(id: Long, value: Long): ByteArray {
            val bytes = (7 downTo 0).map { (value shr (it * 8)).toByte() }.dropWhile { it == 0.toByte() }.toByteArray()
            return element(id, if (bytes.isEmpty()) byteArrayOf(0) else bytes)
        }

        fun text(id: Long, value: String): ByteArray = element(id, value.toByteArray())
    }
}
