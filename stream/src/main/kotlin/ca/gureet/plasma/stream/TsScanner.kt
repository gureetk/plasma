// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

/**
 * Reads an MPEG-TS stream as it arrives and finds the packets an engine can start from: where the
 * video's keyframes begin (brief 5.5), with the stream's latest PAT and PMT to put in front of them.
 *
 * A keyframe is a video packet starting a picture that needs no other: one the broadcaster marks
 * with the random access indicator, or whose start holds an H.264 or HEVC parameter set or IDR
 * picture, or an MPEG-2 sequence or GOP header. Chunks needn't line up with packets.
 *
 * It also follows the program's clock, its PCR, for Show Freeze Watch (the owner's ask of
 * 2 November): a clock that goes back, or races ahead of the time its packets took to come, has
 * jumped, as when the provider's stream starts again or a reconnect joins two streams. [clock]
 * says when each packet came, in ns.
 */
class TsScanner(private val clock: () -> Long = System::nanoTime) {
    /** The latest PAT packet, whole. */
    @Volatile var pat: ByteArray? = null
        private set

    /** The latest PMT packet for the first program, whole. */
    @Volatile var pmt: ByteArray? = null
        private set

    /** The PAT and the PMT, to go in front of a keyframe; null until both have come. */
    fun tables(): ByteArray? {
        val pat = pat ?: return null
        val pmt = pmt ?: return null
        return pat + pmt
    }

    /** Whether the stream has video: until a PMT says so, it might. */
    val hasVideo: Boolean get() = videoPid >= 0 || pmt == null

    private var pmtPid = -1
    private var videoPid = -1
    private var videoType = 0

    /** A jump in the stream's clock: by how much, in ms (back below 0), and whether the stream marked it as a discontinuity. */
    data class ClockJump(val ms: Long, val marked: Boolean)

    /** The jumps in the stream's clock so far, and the last of them. */
    @Volatile var clockJumps: Int = 0
        private set

    @Volatile var lastJump: ClockJump? = null
        private set

    /** The PID carrying the program's clock: the PMT's, or before it the first that carries one. */
    private var pcrPid = -1
    private var lastPcr = -1L
    private var lastPcrAt = 0L

    private val packet = ByteArray(PACKET)
    private var filled = 0

    /** Where in the stream [packet] begins. */
    private var packetStart = 0L

    /** Bytes seen so far. */
    private var position = 0L

    /** Drops a packet half read, as when the connection starts again: the next sync byte begins one. */
    fun resync() {
        filled = 0
    }

    /** Reads [length] bytes from [offset]; [onKeyframe] hears where each keyframe's packet begins. */
    fun feed(bytes: ByteArray, offset: Int, length: Int, onKeyframe: (Long) -> Unit = {}) {
        var at = offset
        val end = offset + length
        while (at < end) {
            if (filled == 0) {
                // Packets start with the sync byte; anything else is skipped until one does.
                if (bytes[at].toInt() and 0xFF != SYNC) {
                    at++
                    position++
                    continue
                }
                packetStart = position
            }
            val count = minOf(PACKET - filled, end - at)
            System.arraycopy(bytes, at, packet, filled, count)
            filled += count
            at += count
            position += count
            if (filled == PACKET) {
                filled = 0
                if (read(packet)) onKeyframe(packetStart)
            }
        }
    }

    /** Reads one whole packet; true when it begins a keyframe. */
    private fun read(packet: ByteArray): Boolean {
        val pid = ((packet[1].toInt() and 0x1F) shl 8) or (packet[2].toInt() and 0xFF)
        val start = packet[1].toInt() and PAYLOAD_START != 0
        val adaptation = (packet[3].toInt() shr 4) and 0x03
        val adaptationLength = if (adaptation and 0x02 != 0) packet[4].toInt() and 0xFF else -1
        if (adaptationLength >= PCR_FIELD && packet.at(FLAGS_AT) and PCR_FLAG != 0) readPcr(pid, packet)
        val payload = when {
            adaptation and 0x01 == 0 -> PACKET
            adaptationLength >= 0 -> HEADER + 1 + adaptationLength
            else -> HEADER
        }
        when {
            pid == PAT_PID && start -> {
                pat = packet.copyOf()
                readPat(packet, payload)
            }
            pid == pmtPid && start -> {
                pmt = packet.copyOf()
                readPmt(packet, payload)
            }
            pid == videoPid && start -> {
                // Marked by the broadcaster.
                if (adaptationLength > 0 && packet[HEADER + 1].toInt() and RANDOM_ACCESS != 0) return true
                return startsAPicture(packet, payload)
            }
        }
        return false
    }

    private fun readPat(packet: ByteArray, payload: Int) {
        val section = payload + 1 + (packet.getOrNull(payload)?.toInt()?.and(0xFF) ?: return)
        // Programs from byte 8 of the section, four bytes each; program 0 is the network's.
        val length = ((packet.at(section + 1) and 0x0F) shl 8) or packet.at(section + 2)
        var entry = section + 8
        val last = minOf(section + 3 + length - CRC, PACKET) - 4
        while (entry <= last) {
            val program = (packet.at(entry) shl 8) or packet.at(entry + 1)
            if (program != 0) {
                pmtPid = ((packet.at(entry + 2) and 0x1F) shl 8) or packet.at(entry + 3)
                return
            }
            entry += 4
        }
    }

    private fun readPmt(packet: ByteArray, payload: Int) {
        val section = payload + 1 + (packet.getOrNull(payload)?.toInt()?.and(0xFF) ?: return)
        val length = ((packet.at(section + 1) and 0x0F) shl 8) or packet.at(section + 2)
        pcrPid = ((packet.at(section + 8) and 0x1F) shl 8) or packet.at(section + 9)
        val infoLength = ((packet.at(section + 10) and 0x0F) shl 8) or packet.at(section + 11)
        var entry = section + 12 + infoLength
        val last = minOf(section + 3 + length - CRC, PACKET) - 5
        while (entry <= last) {
            val type = packet.at(entry)
            val pid = ((packet.at(entry + 1) and 0x1F) shl 8) or packet.at(entry + 2)
            val esInfo = ((packet.at(entry + 3) and 0x0F) shl 8) or packet.at(entry + 4)
            if (type in VIDEO_TYPES) {
                videoPid = pid
                videoType = type
                return
            }
            entry += 5 + esInfo
        }
        videoPid = -1
    }

    /**
     * The program's clock in [packet], on [pid]: compared with the one before, it has jumped when it
     * went back more than a second, or moved on more than a second further than the time it took to
     * come. A connection started again carries on from the last clock, so two streams joined count.
     */
    private fun readPcr(pid: Int, packet: ByteArray) {
        if (pcrPid < 0) pcrPid = pid
        if (pid != pcrPid) return
        // The clock's 33-bit base, in 90 kHz ticks; its 27 MHz extension doesn't matter here.
        val pcr = (packet.at(PCR_AT).toLong() shl 25) or (packet.at(PCR_AT + 1).toLong() shl 17) or
            (packet.at(PCR_AT + 2).toLong() shl 9) or (packet.at(PCR_AT + 3).toLong() shl 1) or (packet.at(PCR_AT + 4).toLong() shr 7)
        val now = clock()
        val before = lastPcr
        val waitedMs = (now - lastPcrAt) / NANOS_PER_MS
        lastPcr = pcr
        lastPcrAt = now
        if (before < 0) return
        var ticks = pcr - before
        // It wraps every 2^33 ticks, about 26.5 hours.
        if (ticks > PCR_WRAP / 2) ticks -= PCR_WRAP else if (ticks < -PCR_WRAP / 2) ticks += PCR_WRAP
        val ms = ticks / TICKS_PER_MS
        if (ms < -JUMP_MS || ms - waitedMs > JUMP_MS) {
            // The jump first: whoever sees the count go up finds it.
            lastJump = ClockJump(ms, marked = packet.at(FLAGS_AT) and DISCONTINUITY != 0)
            clockJumps++
        }
    }

    /** Whether the PES starting in [packet] at [payload] opens with a picture an engine can start from. */
    private fun startsAPicture(packet: ByteArray, payload: Int): Boolean {
        // A PES header: 00 00 01, the stream, the length, two flag bytes and its own length.
        if (payload + 9 > PACKET || packet.at(payload) != 0 || packet.at(payload + 1) != 0 || packet.at(payload + 2) != 1) return false
        var at = payload + 9 + packet.at(payload + 8)
        while (at + 3 < PACKET) {
            if (packet.at(at) == 0 && packet.at(at + 1) == 0 && packet.at(at + 2) == 1) {
                val code = packet.at(at + 3)
                when (videoType) {
                    TYPE_H264 -> when (code and 0x1F) {
                        NAL_IDR, NAL_SPS -> return true
                    }
                    TYPE_HEVC -> when ((code shr 1) and 0x3F) {
                        in HEVC_IRAP, HEVC_VPS, HEVC_SPS -> return true
                    }
                    else -> if (code == MPEG2_SEQUENCE || code == MPEG2_GOP) return true
                }
                at += 3
            } else {
                at++
            }
        }
        return false
    }

    private fun ByteArray.at(index: Int): Int = if (index in indices) this[index].toInt() and 0xFF else 0

    companion object {
        const val PACKET = 188
        private const val SYNC = 0x47
        private const val HEADER = 4
        private const val PAYLOAD_START = 0x40
        private const val RANDOM_ACCESS = 0x40
        private const val PAT_PID = 0
        private const val CRC = 4

        /** The adaptation field's flags, and the clock after them: six bytes, so a field of seven at least. */
        private const val FLAGS_AT = 5
        private const val PCR_AT = 6
        private const val PCR_FIELD = 7
        private const val PCR_FLAG = 0x10
        private const val DISCONTINUITY = 0x80
        private const val PCR_WRAP = 1L shl 33
        private const val TICKS_PER_MS = 90L
        private const val NANOS_PER_MS = 1_000_000L

        /** A clock that moves this far more than the time it took, either way, has jumped. */
        private const val JUMP_MS = 1_000L

        const val TYPE_MPEG2 = 0x02
        const val TYPE_H264 = 0x1B
        const val TYPE_HEVC = 0x24
        private val VIDEO_TYPES = setOf(0x01, TYPE_MPEG2, TYPE_H264, TYPE_HEVC)

        private const val NAL_IDR = 5
        private const val NAL_SPS = 7
        private val HEVC_IRAP = 16..21
        private const val HEVC_VPS = 32
        private const val HEVC_SPS = 33
        private const val MPEG2_SEQUENCE = 0xB3
        private const val MPEG2_GOP = 0xB8
    }
}
