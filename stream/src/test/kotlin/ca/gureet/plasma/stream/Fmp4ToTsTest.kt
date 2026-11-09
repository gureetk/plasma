// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HLS in fMP4 turned into MPEG-TS for recordings (#123). The samples in `resources/fmp4` were
 * made with FFmpeg from its test pictures and a sine tone, two seconds each in one-second
 * segments: H.264 with AAC, the same with the sound in its own rendition, and HEVC with E-AC-3.
 */
class Fmp4ToTsTest {
    private fun fixture(path: String): ByteArray = checkNotNull(javaClass.getResourceAsStream("/fmp4/$path")) { path }.use { it.readBytes() }

    /** [segments] of one rendition after its [init], all of it out. */
    private fun convert(init: String, vararg segments: String): Ts {
        val converter = Fmp4ToTs()
        assertTrue(converter.init(0, fixture(init)))
        val out = ByteArrayOutputStream()
        for (segment in segments) {
            converter.media(0, fixture(segment))
            out.write(converter.drain())
        }
        out.write(converter.drain(all = true))
        return Ts(out.toByteArray())
    }

    @Test fun h264AndAacBecomeTsWithTheirTimesKept() {
        val ts = convert("muxed/init.mp4", "muxed/seg0.m4s", "muxed/seg1.m4s")

        assertEquals(listOf(TsScanner.TYPE_H264, 0x0F), ts.streamTypes)
        assertEquals(50, ts.pes(ts.video).size)
        assertEquals(95, ts.pes(ts.audio).size)
        // The sound's language, from its init segment.
        assertEquals("fra", ts.languages[ts.audio])
        // FFmpeg's own times for the sample, 10 s on: the picture at 0.08 s (decoded at 0), the sound at 0.058 s.
        val first = ts.pes(ts.video).first()
        assertEquals(900_000L + 7_200, first.pts)
        assertEquals(900_000L, first.dts)
        assertEquals(900_000L + 5_220, ts.pes(ts.audio).first().pts)
        // Each AAC frame with its ADTS header, saying its length.
        val adts = ts.pes(ts.audio).first().payload
        assertEquals(0xFF, adts[0].toInt() and 0xFF)
        assertEquals(0xF1, adts[1].toInt() and 0xFF)
        assertEquals(adts.size, ((adts[3].toInt() and 0x03) shl 11) or ((adts[4].toInt() and 0xFF) shl 3) or ((adts[5].toInt() and 0xFF) shr 5))
    }

    @Test fun eachKeyframeHasItsParameterSetsAndIsFoundByTheScanner() {
        val ts = convert("muxed/init.mp4", "muxed/seg0.m4s", "muxed/seg1.m4s")
        val keyframes = ArrayList<Long>()
        val scanner = TsScanner()
        scanner.feed(ts.bytes, 0, ts.bytes.size) { keyframes += it }

        assertEquals(2, keyframes.size)
        assertTrue(scanner.tables() != null)
        // An access unit delimiter, then the SPS and PPS, then the IDR.
        val types = nalTypes(ts.pes(ts.video).first().payload) { it and 0x1F }
        assertEquals(listOf(9, 7, 8), types.take(3))
        assertTrue(5 in types)
        // A picture after it has its delimiter and no parameter sets.
        assertFalse(nalTypes(ts.pes(ts.video)[1].payload) { it and 0x1F }.any { it == 7 || it == 8 })
    }

    @Test fun hevcAndEac3BecomeTs() {
        val ts = convert("hevc/init.mp4", "hevc/seg0.m4s", "hevc/seg1.m4s")

        assertEquals(listOf(TsScanner.TYPE_HEVC, 0x87), ts.streamTypes)
        assertEquals(50, ts.pes(ts.video).size)
        // E-AC-3 as private stream 1.
        assertTrue(ts.pes(ts.audio).all { it.streamId == 0xBD })
        val types = nalTypes(ts.pes(ts.video).first().payload) { (it shr 1) and 0x3F }
        assertEquals(listOf(35, 32, 33, 34), types.take(4))
        val keyframes = ArrayList<Long>()
        TsScanner().feed(ts.bytes, 0, ts.bytes.size) { keyframes += it }
        assertEquals(2, keyframes.size)
    }

    @Test fun ac3IsSaidToBe() {
        val init = fixture("hevc/init.mp4").replace("ec-3", "ac-3")
        val converter = Fmp4ToTs()
        assertTrue(converter.init(0, init))
        converter.media(0, fixture("hevc/seg0.m4s"))

        assertEquals(listOf(TsScanner.TYPE_HEVC, 0x81), Ts(converter.drain(all = true)).streamTypes)
    }

    @Test fun aSeparateAudioRenditionIsMergedInDecodeOrder() {
        val converter = Fmp4ToTs()
        assertTrue(converter.init(0, fixture("demuxed/video/init_0.mp4")))
        converter.expect(1)
        val out = ByteArrayOutputStream()
        converter.media(0, fixture("demuxed/video/seg0.m4s"))
        // The picture waits for the sound.
        assertEquals(0, converter.drain().size)
        // Its init segment says no language; its playlist does.
        assertTrue(converter.init(1, fixture("demuxed/audio/init_1.mp4"), language = "en"))
        for (segment in listOf(1 to "audio/seg0", 0 to "video/seg1", 1 to "audio/seg1", 1 to "audio/seg2")) {
            converter.media(segment.first, fixture("demuxed/${segment.second}.m4s"))
            out.write(converter.drain())
        }
        out.write(converter.drain(all = true))
        val ts = Ts(out.toByteArray())

        assertEquals(50, ts.pes(ts.video).size)
        assertEquals(95, ts.pes(ts.audio).size)
        val order = ts.all.map { it.dts }
        assertEquals(order.sorted(), order)
        assertEquals("eng", ts.languages[ts.audio])
    }

    @Test fun aRenditionThatStopsComingIsLeftBehind() {
        val converter = Fmp4ToTs(maxLagMs = 500)
        converter.init(0, fixture("demuxed/video/init_0.mp4"))
        converter.expect(1)
        converter.media(0, fixture("demuxed/video/seg0.m4s"))
        converter.media(0, fixture("demuxed/video/seg1.m4s"))

        // What's more than half a second behind the picture goes out without the sound.
        val ts = Ts(converter.drain())
        assertTrue(ts.pes(ts.video).size in 30..40)
    }

    @Test fun encryptedFmp4CantBeCarried() {
        assertFalse(Fmp4ToTs().init(0, fixture("muxed/init.mp4").replace("avc1", "encv")))
    }

    @Test fun aPictureInACodecTsDoesntCarryHereIsRefused() {
        assertFalse(Fmp4ToTs().init(0, fixture("muxed/init.mp4").replace("avc1", "av01")))
    }

    @Test fun aSoundTsDoesntCarryHereIsLeftOut() {
        val converter = Fmp4ToTs()
        assertTrue(converter.init(0, fixture("muxed/init.mp4").replace("mp4a", "Opus")))
        converter.media(0, fixture("muxed/seg0.m4s"))

        assertEquals(listOf(TsScanner.TYPE_H264), Ts(converter.drain(all = true)).streamTypes)
    }

    @Test fun samplesAreReadWithTheirFragmentsDefaults() {
        // A fragment with tfhd's default duration, size and flags (not a sync sample), a version 1
        // trun with a data offset, the first sample's flags (sync) and negative composition offsets.
        val samples = Fmp4.readSamples(
            fragment(
                tfhd = box("tfhd", u32(0x020038) + u32(1) + u32(512) + u32(4) + u32(0x00010000)),
                tfdt = box("tfdt", u32(0) + u32(90_000)),
                trun = { offset -> box("trun", u32(0x01000805) + u32(3) + u32(offset) + u32(0x02000000) + u32(-512) + u32(0) + u32(1024)) },
                data = ByteArray(12) { it.toByte() },
            ),
            mapOf(1 to Fmp4.Track(1, 12_800, Fmp4.Codec.H264)),
            HashMap(),
        )

        assertEquals(listOf(90_000L, 90_512L, 91_024L), samples.map { it.dts })
        assertEquals(listOf(-512L, 0L, 1024L), samples.map { it.cto })
        assertEquals(listOf(true, false, false), samples.map { it.sync })
        assertEquals(listOf(4, 4, 4), samples.map { it.size })
        assertEquals(samples[0].offset + 4, samples[1].offset)
    }

    @Test fun aSegmentWithoutItsDecodeTimeCarriesOnFromTheLast() {
        val next = HashMap<Int, Long>()
        val tracks = mapOf(1 to Fmp4.Track(1, 1_000, Fmp4.Codec.Aac))
        val make = { tfdt: ByteArray -> fragment(box("tfhd", u32(0x020018) + u32(1) + u32(20) + u32(2)), tfdt, { offset -> box("trun", u32(0x000001) + u32(2) + u32(offset)) }, ByteArray(4)) }
        Fmp4.readSamples(make(box("tfdt", u32(0) + u32(1_000))), tracks, next)

        assertEquals(listOf(1_040L, 1_060L), Fmp4.readSamples(make(ByteArray(0)), tracks, next).map { it.dts })
    }

    @Test fun anAacConfigGivesItsAdtsFields() {
        // AAC-LC, 48 kHz, stereo.
        assertEquals(Fmp4.Aac(1, 3, 2), Fmp4.aac(byteArrayOf(0x11, 0x90.toByte())))
        // HE-AAC signalled outright: its core, AAC-LC at 24 kHz.
        assertEquals(Fmp4.Aac(1, 6, 2), Fmp4.aac(byteArrayOf(0x2B, 0x11, 0x88.toByte())))
        // xHE-AAC (object type 42) can't be said in ADTS.
        assertEquals(null, Fmp4.aac(byteArrayOf(0xF9.toByte(), 0x46, 0x40)))
    }

    @Test fun psiSectionsEndWithMpeg2sCrc() {
        // A known PAT: program 1 on PID 0x1000.
        val pat = byteArrayOf(0x00, 0xB0.toByte(), 0x0D, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00, 0x00, 0x01, 0xF0.toByte(), 0x00)
        val crc = Fmp4ToTs.crc32(pat)
        assertArrayEquals(byteArrayOf(0x2A, 0xB1.toByte(), 0x04, 0xB2.toByte()), byteArrayOf((crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte()))
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    private fun ByteArray.replace(from: String, to: String): ByteArray {
        val copy = copyOf()
        val needle = from.toByteArray(Charsets.US_ASCII)
        val at = (0..size - needle.size).first { i -> needle.indices.all { this[i + it] == needle[it] } }
        to.toByteArray(Charsets.US_ASCII).copyInto(copy, at)
        return copy
    }

    private fun u32(value: Int) = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

    private fun box(type: String, body: ByteArray) = u32(body.size + 8) + type.toByteArray(Charsets.US_ASCII) + body

    /** A moof with one traf and its mdat; [trun] is given the data's offset from the moof. */
    private fun fragment(tfhd: ByteArray, tfdt: ByteArray, trun: (Int) -> ByteArray, data: ByteArray): ByteArray {
        val runLength = trun(0).size
        // The moof's header, its mfhd, the traf's header, then the traf's boxes.
        val moofLength = 8 + 16 + 8 + tfhd.size + tfdt.size + runLength
        val traf = box("traf", tfhd + tfdt + trun(moofLength + 8))
        val moof = box("moof", box("mfhd", u32(0) + u32(1)) + traf)
        return moof + box("mdat", data)
    }

    /** The NAL units' types in an Annex B [payload]. */
    private fun nalTypes(payload: ByteArray, type: (Int) -> Int): List<Int> {
        val found = ArrayList<Int>()
        var i = 0
        while (i + 3 < payload.size) {
            if (payload[i].toInt() == 0 && payload[i + 1].toInt() == 0 && payload[i + 2].toInt() == 1) {
                found += type(payload[i + 3].toInt() and 0xFF)
                i += 3
            } else {
                i++
            }
        }
        return found
    }

    /** A PES packet as it was read back: its stream, times and payload. */
    class Pes(val pid: Int, val position: Int, val streamId: Int, val pts: Long, val dts: Long, val payload: ByteArray)

    /** MPEG-TS read back: its PMT's streams, and its PES packets in order. */
    class Ts(val bytes: ByteArray) {
        val streamTypes = ArrayList<Int>()
        val languages = HashMap<Int, String>()
        val all = ArrayList<Pes>()
        var video = -1
        var audio = -1

        init {
            assertEquals(0, bytes.size % 188)
            // Each PID's PES being read, from where it starts.
            val open = HashMap<Int, Pair<Int, ByteArrayOutputStream>>()
            val pids = ArrayList<Int>()
            for (at in bytes.indices step 188) {
                assertEquals(0x47, bytes[at].toInt())
                val pid = ((bytes[at + 1].toInt() and 0x1F) shl 8) or (bytes[at + 2].toInt() and 0xFF)
                val start = bytes[at + 1].toInt() and 0x40 != 0
                val control = (bytes[at + 3].toInt() shr 4) and 0x03
                var payload = at + 4
                if (control and 0x02 != 0) payload += 1 + (bytes[at + 4].toInt() and 0xFF)
                if (control and 0x01 == 0) continue
                if (pid == 0x1000 && start && streamTypes.isEmpty()) readPmt(payload, pids)
                if (pid == 0 || pid == 0x1000) continue
                if (start) open.remove(pid)?.let { (from, data) -> all += pes(pid, from, data.toByteArray()) }
                open.getOrPut(pid) { at to ByteArrayOutputStream() }.second.write(bytes, payload, at + 188 - payload)
            }
            for ((pid, read) in open) all += pes(pid, read.first, read.second.toByteArray())
            all.sortBy { it.position }
            video = pids.getOrElse(0) { -1 }
            audio = pids.getOrElse(1) { -1 }
        }

        private fun readPmt(payload: Int, pids: MutableList<Int>) {
            val section = payload + 1 + (bytes[payload].toInt() and 0xFF)
            val length = ((bytes[section + 1].toInt() and 0x0F) shl 8) or (bytes[section + 2].toInt() and 0xFF)
            val infoLength = ((bytes[section + 10].toInt() and 0x0F) shl 8) or (bytes[section + 11].toInt() and 0xFF)
            var entry = section + 12 + infoLength
            val end = section + 3 + length - 4
            while (entry < end) {
                val type = bytes[entry].toInt() and 0xFF
                val pid = ((bytes[entry + 1].toInt() and 0x1F) shl 8) or (bytes[entry + 2].toInt() and 0xFF)
                val esInfo = ((bytes[entry + 3].toInt() and 0x0F) shl 8) or (bytes[entry + 4].toInt() and 0xFF)
                streamTypes += type
                pids += pid
                if (esInfo >= 6 && bytes[entry + 5].toInt() == 0x0A) languages[pid] = String(bytes, entry + 7, 3, Charsets.US_ASCII)
                entry += 5 + esInfo
            }
        }

        private fun pes(pid: Int, position: Int, data: ByteArray): Pes {
            assertEquals(1, data[2].toInt())
            val flags = data[7].toInt() and 0xC0
            val headerLength = data[8].toInt() and 0xFF
            val pts = time(data, 9)
            val dts = if (flags == 0xC0) time(data, 14) else pts
            return Pes(pid, position, data[3].toInt() and 0xFF, pts, dts, data.copyOfRange(9 + headerLength, data.size))
        }

        private fun time(data: ByteArray, at: Int): Long =
            (((data[at].toLong() shr 1) and 0x07) shl 30) or ((data[at + 1].toLong() and 0xFF) shl 22) or
                (((data[at + 2].toLong() shr 1) and 0x7F) shl 15) or ((data[at + 3].toLong() and 0xFF) shl 7) or
                ((data[at + 4].toLong() shr 1) and 0x7F)

        fun pes(pid: Int): List<Pes> = all.filter { it.pid == pid }
    }
}
