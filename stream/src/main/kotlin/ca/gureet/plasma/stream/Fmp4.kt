// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

/**
 * Fragmented MP4 (ISO/IEC 14496-12) as HLS carries it (RFC 8216, 3.3): an initialization
 * segment that says what each track is, then media segments of movie fragments. Only what
 * [Fmp4ToTs] needs to turn it into MPEG-TS is read (#123).
 */
internal object Fmp4 {
    enum class Codec(val video: Boolean) {
        H264(true),
        Hevc(true),
        Aac(false),
        Ac3(false),
        Eac3(false),
        Mp3(false),
    }

    /** A track the init segment declares, in a codec that MPEG-TS can carry. */
    data class Track(
        val id: Int,
        val timescale: Long,
        val codec: Codec,
        /** Video: the parameter sets to put before each keyframe (VPS, SPS, PPS…), and how long a NAL unit's length is. */
        val parameterSets: List<ByteArray> = emptyList(),
        val nalLengthSize: Int = 4,
        /** AAC: what each frame's ADTS header says. */
        val aac: Aac? = null,
        /** MP3: MPEG-2 audio rather than MPEG-1. */
        val mpeg2: Boolean = false,
        /** The ISO 639-2 language, when it's said. */
        val language: String? = null,
        /** Where presentation starts, in the track's timescale: the empty edits, less the media time skipped. */
        val offset: Long = 0,
        val defaults: Defaults = Defaults(),
    )

    /** An AAC track's ADTS fields: the profile (object type less one), the sampling frequency's index and the channels. */
    data class Aac(val profile: Int, val frequencyIndex: Int, val channels: Int)

    /** A track's sample defaults, from `trex`, or a fragment's `tfhd`. */
    data class Defaults(val duration: Long = 0, val size: Long = 0, val flags: Int = 0)

    /**
     * An init segment: its tracks MPEG-TS can carry; whether any track is encrypted (DRM, which
     * can't be recorded); and whether a video track was left out for its codec.
     */
    class Init(val tracks: List<Track>, val encrypted: Boolean, val videoLeftOut: Boolean)

    /** A sample of a fragment: its track, decode time and composition offset (in its timescale), and its bytes in the segment. */
    class Sample(val track: Int, val dts: Long, val cto: Long, val sync: Boolean, val flagged: Boolean, val offset: Int, val size: Int)

    /** The init segment in [data]; null when there's no `moov` in it. */
    fun readInit(data: ByteArray): Init? {
        val moov = boxes(data, 0, data.size).firstOrNull { it.type == "moov" } ?: return null
        val children = boxes(data, moov.body, moov.end).toList()
        val movieTimescale = children.firstOrNull { it.type == "mvhd" }?.let { mvhd ->
            u32(data, mvhd.body + if (data[mvhd.body].toInt() == 1) 20 else 12)
        }?.takeIf { it > 0 } ?: 1_000
        val defaults = HashMap<Int, Defaults>()
        children.firstOrNull { it.type == "mvex" }?.let { mvex ->
            for (trex in boxes(data, mvex.body, mvex.end).filter { it.type == "trex" }) {
                val at = trex.body + 4
                if (at + 20 > trex.end) continue
                defaults[u32(data, at).toInt()] = Defaults(u32(data, at + 8), u32(data, at + 12), u32(data, at + 16).toInt())
            }
        }
        val tracks = ArrayList<Track>()
        var encrypted = false
        var videoLeftOut = false
        for (trak in children.filter { it.type == "trak" }) {
            when (val read = readTrack(data, trak, movieTimescale, defaults)) {
                is Read.Carried -> tracks += read.track
                Read.Encrypted -> encrypted = true
                Read.VideoLeftOut -> videoLeftOut = true
                Read.Other -> Unit
            }
        }
        return Init(tracks, encrypted, videoLeftOut)
    }

    private sealed interface Read {
        class Carried(val track: Track) : Read

        data object Encrypted : Read

        data object VideoLeftOut : Read

        data object Other : Read
    }

    private fun readTrack(data: ByteArray, trak: Box, movieTimescale: Long, defaults: Map<Int, Defaults>): Read {
        val parts = boxes(data, trak.body, trak.end).toList()
        val tkhd = parts.firstOrNull { it.type == "tkhd" } ?: return Read.Other
        val id = u32(data, tkhd.body + if (data[tkhd.body].toInt() == 1) 20 else 12).toInt()
        val mdia = parts.firstOrNull { it.type == "mdia" } ?: return Read.Other
        val media = boxes(data, mdia.body, mdia.end).toList()
        val mdhd = media.firstOrNull { it.type == "mdhd" } ?: return Read.Other
        val long = data[mdhd.body].toInt() == 1
        val timescale = u32(data, mdhd.body + if (long) 20 else 12).takeIf { it > 0 } ?: return Read.Other
        val language = language(u16(data, mdhd.body + if (long) 32 else 20))
        val handler = media.firstOrNull { it.type == "hdlr" }?.let { fourcc(data, it.body + 8) }
        val stbl = media.firstOrNull { it.type == "minf" }?.let { minf -> boxes(data, minf.body, minf.end).firstOrNull { it.type == "stbl" } } ?: return Read.Other
        val stsd = boxes(data, stbl.body, stbl.end).firstOrNull { it.type == "stsd" } ?: return Read.Other
        val entry = boxes(data, stsd.body + 8, stsd.end).firstOrNull() ?: return Read.Other
        val offset = parts.firstOrNull { it.type == "edts" }?.let { edts -> editOffset(data, edts, movieTimescale, timescale) } ?: 0
        val sampleDefaults = defaults[id] ?: Defaults()

        fun track(codec: Codec, sets: List<ByteArray> = emptyList(), length: Int = 4, aac: Aac? = null, mpeg2: Boolean = false) =
            Track(id, timescale, codec, sets, length, aac, mpeg2, language, offset, sampleDefaults)

        val track = when (entry.type) {
            "encv", "enca" -> return Read.Encrypted
            "avc1", "avc3" -> avcC(data, entry)?.let { (sets, length) -> track(Codec.H264, sets, length) }
            "hvc1", "hev1" -> hvcC(data, entry)?.let { (sets, length) -> track(Codec.Hevc, sets, length) }
            "mp4a" -> mp4a(data, entry)?.let { (codec, aac, mpeg2) -> track(codec, aac = aac, mpeg2 = mpeg2) }
            "ac-3" -> track(Codec.Ac3)
            "ec-3" -> track(Codec.Eac3)
            else -> null
        } ?: return if (handler == "vide") Read.VideoLeftOut else Read.Other
        return Read.Carried(track)
    }

    /**
     * Where an edit list starts presentation, in the track's [timescale], as players take it:
     * the empty edits before the first that plays (each in the movie's timescale), less where
     * in the media that one starts.
     */
    private fun editOffset(data: ByteArray, edts: Box, movieTimescale: Long, timescale: Long): Long? {
        val elst = boxes(data, edts.body, edts.end).firstOrNull { it.type == "elst" } ?: return null
        val long = data[elst.body].toInt() == 1
        val count = u32(data, elst.body + 4)
        var at = elst.body + 8
        var empty = 0L
        for (i in 0 until count) {
            if (at + (if (long) 20 else 12) > elst.end) return null
            val duration = if (long) u64(data, at) else u32(data, at)
            val mediaTime = if (long) u64(data, at + 8) else s32(data, at + 4).toLong()
            at += if (long) 20 else 12
            if (mediaTime == -1L) {
                // Rounded, as players take it.
                empty += (duration * timescale + movieTimescale / 2) / movieTimescale
            } else {
                return empty - mediaTime
            }
        }
        return empty
    }

    /** The visual sample entry's `avcC`: its SPS and PPS, and the NAL length size. */
    private fun avcC(data: ByteArray, entry: Box): Pair<List<ByteArray>, Int>? {
        val box = boxes(data, entry.body + VISUAL_ENTRY, entry.end).firstOrNull { it.type == "avcC" } ?: return null
        var at = box.body
        if (at + 6 > box.end) return null
        val length = (data[at + 4].toInt() and 0x03) + 1
        val sets = ArrayList<ByteArray>()
        val spsCount = data[at + 5].toInt() and 0x1F
        at += 6
        repeat(spsCount) {
            at = nal(data, at, box.end, sets) ?: return null
        }
        if (at >= box.end) return sets to length
        val ppsCount = data[at].toInt() and 0xFF
        at++
        repeat(ppsCount) {
            at = nal(data, at, box.end, sets) ?: return null
        }
        return sets to length
    }

    /** The visual sample entry's `hvcC`: its parameter sets (VPS, SPS, PPS and any SEI, in order), and the NAL length size. */
    private fun hvcC(data: ByteArray, entry: Box): Pair<List<ByteArray>, Int>? {
        val box = boxes(data, entry.body + VISUAL_ENTRY, entry.end).firstOrNull { it.type == "hvcC" } ?: return null
        if (box.body + 23 > box.end) return null
        val length = (data[box.body + 21].toInt() and 0x03) + 1
        val arrays = data[box.body + 22].toInt() and 0xFF
        var at = box.body + 23
        val sets = ArrayList<ByteArray>()
        repeat(arrays) {
            if (at + 3 > box.end) return null
            val count = u16(data, at + 1)
            at += 3
            repeat(count) {
                at = nal(data, at, box.end, sets) ?: return null
            }
        }
        return sets to length
    }

    /** A NAL unit with a 16-bit length at [at], added to [into]; where the next begins, or null past [end]. */
    private fun nal(data: ByteArray, at: Int, end: Int, into: MutableList<ByteArray>): Int? {
        if (at + 2 > end) return null
        val size = u16(data, at)
        if (at + 2 + size > end) return null
        into += data.copyOfRange(at + 2, at + 2 + size)
        return at + 2 + size
    }

    /** An `mp4a` entry, from its `esds`: AAC with its ADTS fields, or MP3 (MPEG-2 audio when so). */
    private fun mp4a(data: ByteArray, entry: Box): Triple<Codec, Aac?, Boolean>? {
        // A sound sample entry: 28 bytes, 16 more in version 1 and 36 more in version 2 (QuickTime's).
        val version = u16(data, entry.body + 8)
        val skip = AUDIO_ENTRY + when (version) {
            1 -> 16
            2 -> 36
            else -> 0
        }
        val esds = boxes(data, entry.body + skip, entry.end).firstOrNull { it.type == "esds" } ?: return null
        val (objectType, info) = decoderConfig(data, esds.body + 4, esds.end) ?: return null
        return when (objectType) {
            OTI_MP3, OTI_MPEG2_AUDIO -> Triple(Codec.Mp3, null, objectType == OTI_MPEG2_AUDIO)
            OTI_AAC, OTI_AAC_MAIN, OTI_AAC_LC, OTI_AAC_SSR -> aac(info)?.let { Triple(Codec.Aac, it, false) }
            else -> null
        }
    }

    /** The ES descriptor's object type, and its decoder-specific info (an AudioSpecificConfig for AAC). */
    private fun decoderConfig(data: ByteArray, from: Int, end: Int): Pair<Int, ByteArray>? {
        var at = from
        val (esTag, esSize, esBody) = descriptor(data, at, end) ?: return null
        if (esTag != TAG_ES) return null
        val esEnd = minOf(esBody + esSize, end)
        at = esBody + 2
        if (at >= esEnd) return null
        val flags = data[at].toInt() and 0xFF
        at++
        if (flags and 0x80 != 0) at += 2
        if (flags and 0x40 != 0) at += 1 + (data.getOrNull(at)?.toInt()?.and(0xFF) ?: return null)
        if (flags and 0x20 != 0) at += 2
        val (configTag, configSize, configBody) = descriptor(data, at, esEnd) ?: return null
        if (configTag != TAG_DECODER_CONFIG || configBody + 13 > esEnd) return null
        val objectType = data[configBody].toInt() and 0xFF
        val configEnd = minOf(configBody + configSize, esEnd)
        val info = descriptor(data, configBody + 13, configEnd)?.takeIf { it.first == TAG_DECODER_INFO }?.let { (_, size, body) ->
            data.copyOfRange(body, minOf(body + size, configEnd))
        } ?: ByteArray(0)
        return objectType to info
    }

    /** A descriptor's tag, size and where its body starts, its size in up to four bytes of seven bits. */
    private fun descriptor(data: ByteArray, at: Int, end: Int): Triple<Int, Int, Int>? {
        if (at >= end) return null
        val tag = data[at].toInt() and 0xFF
        var size = 0
        var p = at + 1
        repeat(4) {
            if (p >= end) return null
            val b = data[p++].toInt() and 0xFF
            size = (size shl 7) or (b and 0x7F)
            if (b and 0x80 == 0) return Triple(tag, size, p)
        }
        return Triple(tag, size, p)
    }

    /**
     * What ADTS needs from an AudioSpecificConfig: the profile, frequency index and channels. HE-AAC
     * signalled outright gives its core AAC, as ADTS carries it. Null for what ADTS can't say.
     */
    fun aac(config: ByteArray): Aac? {
        val bits = Bits(config)
        var type = bits.objectType() ?: return null
        val frequency = bits.frequencyIndex() ?: return null
        val channels = bits.read(4) ?: return null
        if (type == AOT_SBR || type == AOT_PS) {
            bits.frequencyIndex() ?: return null
            type = bits.objectType() ?: return null
        }
        if (type !in 1..4 || channels !in 1..7) return null
        return Aac(type - 1, frequency, channels)
    }

    private class Bits(private val data: ByteArray) {
        private var at = 0

        fun read(count: Int): Int? {
            var value = 0
            repeat(count) {
                val byte = at / 8
                if (byte >= data.size) return null
                value = (value shl 1) or ((data[byte].toInt() shr (7 - at % 8)) and 1)
                at++
            }
            return value
        }

        fun objectType(): Int? = read(5)?.let { if (it == 31) read(6)?.plus(32) else it }

        /** The sampling frequency's index; one given in hertz is matched to the table. */
        fun frequencyIndex(): Int? = read(4)?.let { index ->
            if (index != 15) index else read(24)?.let { hertz -> FREQUENCIES.indexOf(hertz).takeIf { it >= 0 } }
        }
    }

    /**
     * The samples of each movie fragment in a media segment, in the order they're stored, with
     * their tracks' decode times carried on in [next] (by track) where a fragment doesn't say.
     * [rangeStart] is where the segment starts in its file, for a base data offset given from the
     * start of the file. A fragment that doesn't add up ends the reading there.
     */
    fun readSamples(data: ByteArray, tracks: Map<Int, Track>, next: MutableMap<Int, Long>, rangeStart: Long = 0): List<Sample> {
        val samples = ArrayList<Sample>()
        for (moof in boxes(data, 0, data.size).filter { it.type == "moof" }) {
            var previousEnd = moof.start.toLong()
            for (traf in boxes(data, moof.body, moof.end).filter { it.type == "traf" }) {
                val parts = boxes(data, traf.body, traf.end).toList()
                val tfhd = parts.firstOrNull { it.type == "tfhd" } ?: continue
                val flags = u24(data, tfhd.body + 1)
                val id = u32(data, tfhd.body + 4).toInt()
                val track = tracks[id]
                var at = tfhd.body + 8
                val base = when {
                    flags and TFHD_BASE_OFFSET != 0 -> (u64(data, at) - rangeStart).also { at += 8 }
                    flags and TFHD_BASE_IS_MOOF != 0 -> moof.start.toLong()
                    else -> previousEnd
                }
                if (flags and TFHD_DESCRIPTION != 0) at += 4
                val defaults = track?.defaults ?: Defaults()
                val duration = if (flags and TFHD_DURATION != 0) u32(data, at).also { at += 4 } else defaults.duration
                val size = if (flags and TFHD_SIZE != 0) u32(data, at).also { at += 4 } else defaults.size
                val sampleFlags = if (flags and TFHD_FLAGS != 0) u32(data, at).toInt().also { at += 4 } else defaults.flags
                val tfdt = parts.firstOrNull { it.type == "tfdt" }
                var dts = tfdt?.let { if (data[it.body].toInt() == 1) u64(data, it.body + 4) else u32(data, it.body + 4) } ?: next[id] ?: 0
                var dataAt = base
                for (trun in parts.filter { it.type == "trun" }) {
                    val runFlags = u24(data, trun.body + 1)
                    val signed = data[trun.body].toInt() != 0
                    val count = u32(data, trun.body + 4)
                    var p = trun.body + 8
                    if (runFlags and TRUN_DATA_OFFSET != 0) {
                        dataAt = base + s32(data, p)
                        p += 4
                    }
                    val first = if (runFlags and TRUN_FIRST_FLAGS != 0) u32(data, p).toInt().also { p += 4 } else null
                    val each = (if (runFlags and TRUN_DURATION != 0) 4 else 0) + (if (runFlags and TRUN_SIZE != 0) 4 else 0) +
                        (if (runFlags and TRUN_FLAGS != 0) 4 else 0) + (if (runFlags and TRUN_CTO != 0) 4 else 0)
                    if (p + count * each > trun.end) break
                    for (i in 0 until count) {
                        val d = if (runFlags and TRUN_DURATION != 0) u32(data, p).also { p += 4 } else duration
                        val s = if (runFlags and TRUN_SIZE != 0) u32(data, p).also { p += 4 } else size
                        val f = if (runFlags and TRUN_FLAGS != 0) u32(data, p).toInt().also { p += 4 } else if (i == 0L && first != null) first else sampleFlags
                        val cto = if (runFlags and TRUN_CTO != 0) (if (signed) s32(data, p).toLong() else u32(data, p)).also { p += 4 } else 0
                        if (dataAt < 0 || s < 0 || dataAt + s > data.size) return samples
                        val given = runFlags and TRUN_FLAGS != 0 || (i == 0L && first != null) || flags and TFHD_FLAGS != 0 || defaults.flags != 0
                        if (track != null) samples += Sample(id, dts, cto, f and NON_SYNC == 0, given, dataAt.toInt(), s.toInt())
                        dataAt += s
                        dts += d
                    }
                }
                next[id] = dts
                previousEnd = dataAt
            }
        }
        return samples
    }

    /** A box: its type, where it starts, where its body starts, and where it ends. */
    class Box(val type: String, val start: Int, val body: Int, val end: Int)

    /** The boxes from [from] to [to]; one that runs past [to] ends them. */
    fun boxes(data: ByteArray, from: Int, to: Int): Sequence<Box> = sequence {
        var at = from
        while (at + 8 <= to) {
            var size = u32(data, at)
            var header = 8
            if (size == 1L) {
                if (at + 16 > to) break
                size = u64(data, at + 8)
                header = 16
            } else if (size == 0L) {
                size = (to - at).toLong()
            }
            if (size < header || size > to - at) break
            yield(Box(fourcc(data, at + 4), at, at + header, at + size.toInt()))
            at += size.toInt()
        }
    }

    /** [value] from one timescale to another, without overflowing on large times. */
    fun scale(value: Long, from: Long, to: Long): Long = (value / from) * to + (value % from) * to / from

    private fun language(packed: Int): String? {
        if (packed == 0 || packed == UND) return null
        val chars = CharArray(3) { (((packed shr (10 - it * 5)) and 0x1F) + 0x60).toChar() }
        return String(chars).takeIf { text -> text.all { it in 'a'..'z' } && text != "und" }
    }

    private fun fourcc(data: ByteArray, at: Int): String =
        if (at + 4 > data.size) "" else String(CharArray(4) { (data[at + it].toInt() and 0xFF).toChar() })

    private fun u16(data: ByteArray, at: Int): Int = if (at + 2 > data.size) 0 else ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)

    private fun u24(data: ByteArray, at: Int): Int = if (at + 3 > data.size) 0 else ((data[at].toInt() and 0xFF) shl 16) or u16(data, at + 1)

    private fun u32(data: ByteArray, at: Int): Long = if (at + 4 > data.size) 0 else (u16(data, at).toLong() shl 16) or u16(data, at + 2).toLong()

    private fun s32(data: ByteArray, at: Int): Int = u32(data, at).toInt()

    private fun u64(data: ByteArray, at: Int): Long = if (at + 8 > data.size) 0 else (u32(data, at) shl 32) or u32(data, at + 4)

    /** A visual sample entry's fields before its boxes. */
    private const val VISUAL_ENTRY = 78

    /** A sound sample entry's fields before its boxes. */
    private const val AUDIO_ENTRY = 28

    private const val UND = 0x55C4
    private const val TAG_ES = 0x03
    private const val TAG_DECODER_CONFIG = 0x04
    private const val TAG_DECODER_INFO = 0x05
    private const val OTI_AAC = 0x40
    private const val OTI_AAC_MAIN = 0x66
    private const val OTI_AAC_LC = 0x67
    private const val OTI_AAC_SSR = 0x68
    private const val OTI_MPEG2_AUDIO = 0x69
    private const val OTI_MP3 = 0x6B
    private const val AOT_SBR = 5
    private const val AOT_PS = 29
    private val FREQUENCIES = listOf(96_000, 88_200, 64_000, 48_000, 44_100, 32_000, 24_000, 22_050, 16_000, 12_000, 11_025, 8_000, 7_350)

    private const val TFHD_BASE_OFFSET = 0x000001
    private const val TFHD_DESCRIPTION = 0x000002
    private const val TFHD_DURATION = 0x000008
    private const val TFHD_SIZE = 0x000010
    private const val TFHD_FLAGS = 0x000020
    private const val TFHD_BASE_IS_MOOF = 0x020000
    private const val TRUN_DATA_OFFSET = 0x000001
    private const val TRUN_FIRST_FLAGS = 0x000004
    private const val TRUN_DURATION = 0x000100
    private const val TRUN_SIZE = 0x000200
    private const val TRUN_FLAGS = 0x000400
    private const val TRUN_CTO = 0x000800

    /** sample_is_non_sync_sample, in a sample's flags. */
    private const val NON_SYNC = 0x00010000
}
