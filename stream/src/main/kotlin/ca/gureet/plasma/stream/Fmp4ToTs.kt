// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.MissingResourceException

/**
 * HLS in fragmented MP4 (CMAF), turned into MPEG-TS as it's read, so a recording of it is a TS
 * file like any other: with its keyframe index, chase play, copies and Jellyfin's names as they
 * are (#123). Each rendition (the channel's own playlist, and a separate audio one when the
 * channel has it) gives its init segment, then its media segments in order. H.264, HEVC, AAC,
 * AC-3, E-AC-3 and MP3 are carried, one video track and every audio track; others are left out.
 *
 * Samples go out in decode order across the renditions, so one that's read ahead waits for the
 * other, unless the other has fallen [maxLagMs] behind, as when it's stopped coming.
 */
class Fmp4ToTs(private val maxLagMs: Long = MAX_LAG_MS) {
    /** A rendition: its init segment's tracks, the stream each goes to, and where their decode times have got to. */
    private class Rendition {
        var tracks: Map<Int, Fmp4.Track> = emptyMap()
        var routes: Map<Int, Output> = emptyMap()
        val next = HashMap<Int, Long>()
        var video: Output? = null
        val audio = ArrayList<Output>()
    }

    /** A stream in the TS: its PID, and the samples waiting to go out. */
    private class Output(val pid: Int, var track: Fmp4.Track) {
        val waiting = ArrayDeque<Ready>()

        /** The decode time of the last sample in, in 90 kHz; null before the first. */
        var last: Long? = null
        var counter = 0
        val video: Boolean get() = track.codec.video
    }

    /** A sample ready to go out, as its codec goes in a PES packet; times in 90 kHz. */
    private class Ready(val dts: Long, val pts: Long, val key: Boolean, val payload: ByteArray)

    private val renditions = HashMap<Int, Rendition>()
    private val outputs = ArrayList<Output>()

    /** Renditions to wait for before samples go out, whether they've given any yet or not. */
    private val expected = HashSet<Int>()
    private var nextPid = FIRST_PID
    private var patCounter = 0
    private var pmtCounter = 0
    private var pmtVersion = -1
    private var pat: ByteArray? = null
    private var pmt: ByteArray? = null
    private var lastPcr: Long? = null

    /** Waits for [rendition] before samples go out, though it's given nothing yet. */
    fun expect(rendition: Int) {
        expected += rendition
    }

    /** Stops waiting for [rendition]: it's gone. */
    fun retire(rendition: Int) {
        expected -= rendition
    }

    /**
     * [rendition]'s init segment: false when nothing in it can be recorded, because it's
     * encrypted, or its video or every track is in a codec MPEG-TS doesn't carry here. Its sound
     * is in [language] (as HLS gives it: "en", "eng", "pt-BR"…) when it doesn't say itself.
     */
    fun init(rendition: Int, bytes: ByteArray, language: String? = null): Boolean {
        val init = Fmp4.readInit(bytes) ?: return false
        if (init.encrypted || init.videoLeftOut || init.tracks.isEmpty()) return false
        val state = renditions.getOrPut(rendition) { Rendition() }
        val routes = HashMap<Int, Output>()
        val said = language?.let(::iso639)
        var audio = 0
        for (read in init.tracks) {
            val track = if (read.language == null && said != null && !read.codec.video) read.copy(language = said) else read
            val output = if (track.codec.video) {
                // One picture: a second video track, here or in another rendition, is left out.
                state.video ?: if (outputs.any { it.video }) null else add(track).also { state.video = it }
            } else {
                val slot = audio++
                state.audio.getOrNull(slot) ?: add(track).also { state.audio += it }
            }
            if (output == null) continue
            if (output.track.codec != track.codec || output.track.language != track.language) pmt = null
            output.track = track
            routes[track.id] = output
        }
        if (routes.isEmpty()) return false
        state.tracks = init.tracks.associateBy { it.id } + routes.mapValues { it.value.track }
        state.routes = routes
        expected += rendition
        return true
    }

    /** A language tag ("en", "en-US", "eng") as ISO 639-2 ("eng"), as the PMT says it; null when it isn't one. */
    private fun iso639(tag: String): String? = try {
        Locale.forLanguageTag(tag).isO3Language.takeIf { it.length == 3 }
    } catch (unknown: MissingResourceException) {
        null
    }

    private fun add(track: Fmp4.Track): Output = Output(nextPid++, track).also {
        outputs += it
        pmt = null
    }

    /** One of [rendition]'s media segments, from [rangeStart] in its file. */
    fun media(rendition: Int, bytes: ByteArray, rangeStart: Long = 0) {
        val state = renditions[rendition] ?: return
        for (sample in Fmp4.readSamples(bytes, state.tracks, state.next, rangeStart)) {
            val output = state.routes[sample.track] ?: continue
            val track = output.track
            val data = bytes.copyOfRange(sample.offset, sample.offset + sample.size)
            var key = false
            val payload = when (track.codec) {
                Fmp4.Codec.H264, Fmp4.Codec.Hevc -> video(track, data, sample).let { (annexB, keyframe) ->
                    key = keyframe
                    annexB
                }
                Fmp4.Codec.Aac -> adts(track.aac ?: continue, data)
                else -> data
            }
            val dts = Fmp4.scale(sample.dts + track.offset, track.timescale, TICKS_PER_SECOND)
            val pts = Fmp4.scale(sample.dts + sample.cto + track.offset, track.timescale, TICKS_PER_SECOND)
            output.waiting += Ready(dts, pts, key, payload)
            output.last = dts
        }
    }

    /**
     * How far [rendition] has got, in 90 kHz: the decode time of its last sample in, or of the
     * track furthest behind when it has a picture and sound of its own.
     */
    fun reached(rendition: Int): Long? {
        val state = renditions[rendition] ?: return null
        return state.routes.values.mapNotNull { it.last }.minOrNull()
    }

    /**
     * MPEG-TS for the samples that can go out now, in decode order, with the PAT and the PMT
     * first and before every keyframe; all of them when [all], as when the stream's ended.
     */
    fun drain(all: Boolean = false): ByteArray {
        val limit = if (all) Long.MAX_VALUE else limit() ?: return ByteArray(0)
        val out = ByteArrayOutputStream()
        while (true) {
            val output = outputs.filter { it.waiting.isNotEmpty() && it.waiting.first().dts <= limit }.minByOrNull { it.waiting.first().dts } ?: break
            val ready = output.waiting.removeFirst()
            if (out.size() == 0 || (output.video && ready.key)) writeTables(out)
            writePes(out, output, ready)
        }
        return out.toByteArray()
    }

    /**
     * Up to when samples can go out: where the rendition furthest behind has got to, but no
     * further back than [maxLagMs] before the one furthest ahead; null before any has given any.
     */
    private fun limit(): Long? {
        val reached = expected.map { reached(it) }
        val ahead = reached.filterNotNull().maxOrNull() ?: return null
        val behind = if (reached.any { it == null }) Long.MIN_VALUE else reached.filterNotNull().min()
        return maxOf(behind, ahead - maxLagMs * TICKS_PER_SECOND / MS_PER_SECOND)
    }

    /**
     * A video sample in Annex B: an access unit delimiter, the parameter sets before a keyframe
     * that hasn't its own, then its NAL units; and whether it's a keyframe.
     */
    private fun video(track: Fmp4.Track, data: ByteArray, sample: Fmp4.Sample): Pair<ByteArray, Boolean> {
        val hevc = track.codec == Fmp4.Codec.Hevc
        val nals = nals(data, track.nalLengthSize)
        val types = nals.map { (at, _) -> if (hevc) (data[at].toInt() shr 1) and 0x3F else data[at].toInt() and 0x1F }
        // One with an IDR or other random-access picture, or, where its flags were given, one they say is a sync sample.
        val key = types.any { if (hevc) it in HEVC_IRAP else it == H264_IDR } || (sample.flagged && sample.sync)
        val out = ByteArrayOutputStream(data.size + PARAMETER_ROOM)
        out.write(START)
        out.write(if (hevc) HEVC_AUD else H264_AUD)
        if (key && types.none { it in (if (hevc) HEVC_SETS else H264_SETS) }) {
            for (set in track.parameterSets) {
                out.write(START)
                out.write(set)
            }
        }
        for ((i, nal) in nals.withIndex()) {
            if (types[i] == (if (hevc) HEVC_AUD_TYPE else H264_AUD_TYPE)) continue
            out.write(START)
            out.write(data, nal.first, nal.second)
        }
        return out.toByteArray() to key
    }

    /** Where each NAL unit is in [data], and its length. A length that runs past the end ends them. */
    private fun nals(data: ByteArray, lengthSize: Int): List<Pair<Int, Int>> {
        val found = ArrayList<Pair<Int, Int>>()
        var at = 0
        while (at + lengthSize <= data.size) {
            var length = 0L
            repeat(lengthSize) { length = (length shl 8) or (data[at + it].toLong() and 0xFF) }
            at += lengthSize
            if (length <= 0 || at + length > data.size) break
            found += at to length.toInt()
            at += length.toInt()
        }
        return found
    }

    /** An AAC frame with its ADTS header. */
    private fun adts(aac: Fmp4.Aac, frame: ByteArray): ByteArray {
        val length = frame.size + ADTS_HEADER
        val out = ByteArray(length)
        out[0] = 0xFF.toByte()
        out[1] = 0xF1.toByte()
        out[2] = ((aac.profile shl 6) or (aac.frequencyIndex shl 2) or ((aac.channels shr 2) and 0x01)).toByte()
        out[3] = (((aac.channels and 0x03) shl 6) or ((length shr 11) and 0x03)).toByte()
        out[4] = (length shr 3).toByte()
        out[5] = (((length and 0x07) shl 5) or 0x1F).toByte()
        out[6] = 0xFC.toByte()
        frame.copyInto(out, ADTS_HEADER)
        return out
    }

    /** The PAT and the PMT, each in a packet of its own; the PMT made again when the streams change. */
    private fun writeTables(out: ByteArrayOutputStream) {
        val patSection = pat ?: section(TABLE_PAT, TRANSPORT_STREAM, 0, byteArrayOf(0, PROGRAM.toByte(), (0xE0 or (PMT_PID shr 8)).toByte(), PMT_PID.toByte())).also { pat = it }
        val pmtSection = pmt ?: makePmt().also { pmt = it }
        writeSection(out, PAT_PID, patSection, patCounter++)
        writeSection(out, PMT_PID, pmtSection, pmtCounter++)
    }

    private fun makePmt(): ByteArray {
        pmtVersion = (pmtVersion + 1) and 0x1F
        val pcr = pcrPid()
        val body = ByteArrayOutputStream()
        body.write(byteArrayOf((0xE0 or (pcr shr 8)).toByte(), pcr.toByte(), 0xF0.toByte(), 0))
        for (output in outputs) {
            val track = output.track
            val language = track.language?.takeIf { !track.codec.video && it.length == 3 }
            val descriptors = if (language != null) byteArrayOf(ISO_639, 4) + language.toByteArray(Charsets.US_ASCII) + byteArrayOf(0) else ByteArray(0)
            body.write(streamType(track))
            body.write(0xE0 or (output.pid shr 8))
            body.write(output.pid)
            body.write(0xF0 or (descriptors.size shr 8))
            body.write(descriptors.size)
            body.write(descriptors)
        }
        return section(TABLE_PMT, PROGRAM, pmtVersion, body.toByteArray())
    }

    /** The clock goes with the picture, or the first sound without one. */
    private fun pcrPid(): Int = (outputs.firstOrNull { it.video } ?: outputs.firstOrNull())?.pid ?: FIRST_PID

    private fun streamType(track: Fmp4.Track): Int = when (track.codec) {
        Fmp4.Codec.H264 -> TsScanner.TYPE_H264
        Fmp4.Codec.Hevc -> TsScanner.TYPE_HEVC
        Fmp4.Codec.Aac -> TYPE_ADTS
        Fmp4.Codec.Ac3 -> TYPE_AC3
        Fmp4.Codec.Eac3 -> TYPE_EAC3
        Fmp4.Codec.Mp3 -> if (track.mpeg2) TYPE_MPEG2_AUDIO else TYPE_MPEG1_AUDIO
    }

    /** A PSI section: its header, [body] and CRC. */
    private fun section(table: Int, id: Int, version: Int, body: ByteArray): ByteArray {
        val length = SECTION_FIELDS + body.size + CRC_BYTES
        val head = byteArrayOf(
            table.toByte(),
            (0xB0 or (length shr 8)).toByte(),
            length.toByte(),
            (id shr 8).toByte(),
            id.toByte(),
            (0xC1 or (version shl 1)).toByte(),
            0,
            0,
        )
        val section = head + body
        val crc = crc32(section)
        return section + byteArrayOf((crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte())
    }

    /** A section in a packet of its own, after its pointer field. */
    private fun writeSection(out: ByteArrayOutputStream, pid: Int, section: ByteArray, counter: Int) {
        val packet = ByteArray(PACKET) { 0xFF.toByte() }
        packet[0] = SYNC
        packet[1] = (PAYLOAD_START or (pid shr 8)).toByte()
        packet[2] = pid.toByte()
        packet[3] = (0x10 or (counter and 0x0F)).toByte()
        packet[4] = 0
        section.copyInto(packet, HEADER + 1, 0, minOf(section.size, PACKET - HEADER - 1))
        out.write(packet)
    }

    /** [ready] as a PES packet in TS packets on [output]'s PID, with the clock when it's the clock's PID. */
    private fun writePes(out: ByteArrayOutputStream, output: Output, ready: Ready) {
        val video = output.video
        val pts = (ready.pts + BASE) and MASK_33
        val dts = ((ready.dts + BASE) and MASK_33).takeIf { it != pts }
        val headerData = if (dts != null) 10 else 5
        val streamId = when {
            video -> STREAM_VIDEO
            output.track.codec == Fmp4.Codec.Ac3 || output.track.codec == Fmp4.Codec.Eac3 -> STREAM_PRIVATE_1
            else -> STREAM_AUDIO
        }
        val pesLength = PES_FLAGS + headerData + ready.payload.size
        val pes = ByteArrayOutputStream(PES_HEADER_MAX + ready.payload.size)
        pes.write(byteArrayOf(0, 0, 1, streamId.toByte()))
        // Video's length can be left unsaid, and may be too long to say.
        val said = if (video || pesLength > 0xFFFF) 0 else pesLength
        pes.write(said shr 8)
        pes.write(said)
        pes.write(0x84)
        pes.write(if (dts != null) 0xC0 else 0x80)
        pes.write(headerData)
        pes.write(timestamp(if (dts != null) 0x3 else 0x2, pts))
        if (dts != null) pes.write(timestamp(0x1, dts))
        pes.write(ready.payload)
        val pcr = if (output.pid == pcrPid()) (ready.dts + BASE - PCR_LEAD) and MASK_33 else null
        val jumped = pcr != null && lastPcr.let { it != null && (pcr < it || pcr - it > PCR_JUMP) }
        if (pcr != null) lastPcr = pcr
        packetize(out, output, pes.toByteArray(), pcr, randomAccess = ready.key, discontinuity = jumped)
    }

    /** A timestamp's five bytes, after its four-bit [prefix]. */
    private fun timestamp(prefix: Int, value: Long): ByteArray = byteArrayOf(
        ((prefix shl 4) or (((value shr 30) and 0x07).toInt() shl 1) or 1).toByte(),
        (value shr 22).toByte(),
        ((((value shr 15) and 0x7F).toInt() shl 1) or 1).toByte(),
        (value shr 7).toByte(),
        (((value and 0x7F).toInt() shl 1) or 1).toByte(),
    )

    /** [pes] in TS packets: the first with the clock and a keyframe's mark, the last filled out with stuffing. */
    private fun packetize(out: ByteArrayOutputStream, output: Output, pes: ByteArray, pcr: Long?, randomAccess: Boolean, discontinuity: Boolean) {
        var at = 0
        var first = true
        while (at < pes.size) {
            val packet = ByteArray(PACKET)
            packet[0] = SYNC
            packet[1] = ((if (first) PAYLOAD_START else 0) or (output.pid shr 8)).toByte()
            packet[2] = output.pid.toByte()
            // The adaptation field's flags and fields, after its length.
            val fields = ByteArrayOutputStream()
            if (first && (pcr != null || randomAccess || discontinuity)) {
                fields.write((if (discontinuity) 0x80 else 0) or (if (randomAccess) 0x40 else 0) or (if (pcr != null) 0x10 else 0))
                if (pcr != null) {
                    fields.write((pcr shr 25).toInt())
                    fields.write((pcr shr 17).toInt())
                    fields.write((pcr shr 9).toInt())
                    fields.write((pcr shr 1).toInt())
                    fields.write(((pcr and 0x01).toInt() shl 7) or 0x7E)
                    fields.write(0)
                }
            }
            var adaptation = if (fields.size() > 0) fields.size() else -1
            val remaining = pes.size - at
            val room = PACKET - HEADER - (if (adaptation >= 0) 1 + adaptation else 0)
            // The last packet is filled out with stuffing in the adaptation field.
            if (remaining < room) adaptation = if (adaptation < 0) room - remaining - 1 else adaptation + room - remaining
            packet[3] = ((if (adaptation >= 0) 0x30 else 0x10) or (output.counter and 0x0F)).toByte()
            output.counter++
            var p = HEADER
            if (adaptation >= 0) {
                packet[p++] = adaptation.toByte()
                val end = p + adaptation
                if (adaptation > 0) {
                    val written = fields.toByteArray()
                    if (written.isEmpty()) {
                        packet[p++] = 0
                    } else {
                        written.copyInto(packet, p)
                        p += written.size
                    }
                    while (p < end) packet[p++] = 0xFF.toByte()
                }
            }
            val payload = minOf(remaining, PACKET - p)
            pes.copyInto(packet, p, at, at + payload)
            at += payload
            first = false
            out.write(packet)
        }
    }

    companion object {
        /** How far one rendition can fall behind the other before the other goes on without it. */
        const val MAX_LAG_MS = 30_000L

        private const val TICKS_PER_SECOND = 90_000L
        private const val MS_PER_SECOND = 1_000L
        private const val MASK_33 = (1L shl 33) - 1

        /** Added to every timestamp, so a stream that starts at 0 doesn't go below it with its edits. */
        private const val BASE = 10 * TICKS_PER_SECOND

        /** How far the clock runs behind the pictures' decode times: 0.7 s, as muxers do. */
        private const val PCR_LEAD = 63_000L

        /** A clock that moves on more than this is a jump, and marked so. */
        private const val PCR_JUMP = 10 * TICKS_PER_SECOND

        private const val PACKET = 188
        private const val HEADER = 4
        private const val SYNC = 0x47.toByte()
        private const val PAYLOAD_START = 0x40
        private const val PAT_PID = 0x0000
        private const val PMT_PID = 0x1000
        private const val FIRST_PID = 0x0100
        private const val PROGRAM = 1
        private const val TRANSPORT_STREAM = 1
        private const val TABLE_PAT = 0x00
        private const val TABLE_PMT = 0x02
        private const val SECTION_FIELDS = 5
        private const val CRC_BYTES = 4
        private const val ISO_639: Byte = 0x0A
        private const val TYPE_ADTS = 0x0F
        private const val TYPE_AC3 = 0x81
        private const val TYPE_EAC3 = 0x87
        private const val TYPE_MPEG1_AUDIO = 0x03
        private const val TYPE_MPEG2_AUDIO = 0x04
        private const val STREAM_VIDEO = 0xE0
        private const val STREAM_AUDIO = 0xC0
        private const val STREAM_PRIVATE_1 = 0xBD

        /** A PES header's flags and the length of its optional fields. */
        private const val PES_FLAGS = 3
        private const val PES_HEADER_MAX = 19
        private const val ADTS_HEADER = 7
        private const val PARAMETER_ROOM = 256

        private val START = byteArrayOf(0, 0, 0, 1)
        private val H264_AUD = byteArrayOf(0x09, 0xF0.toByte())
        private val HEVC_AUD = byteArrayOf(0x46, 0x01, 0x50)
        private const val H264_AUD_TYPE = 9
        private const val HEVC_AUD_TYPE = 35
        private const val H264_IDR = 5
        private val H264_SETS = setOf(7, 8)
        private val HEVC_SETS = setOf(32, 33, 34)
        private val HEVC_IRAP = 16..23

        private val CRC_TABLE = IntArray(256) { n ->
            var c = n shl 24
            repeat(8) { c = if (c and Int.MIN_VALUE != 0) (c shl 1) xor 0x04C11DB7 else c shl 1 }
            c
        }

        /** MPEG-2's CRC-32, which PSI sections end with. */
        internal fun crc32(data: ByteArray): Int {
            var crc = -1
            for (b in data) crc = (crc shl 8) xor CRC_TABLE[((crc ushr 24) xor (b.toInt() and 0xFF)) and 0xFF]
            return crc
        }
    }
}
