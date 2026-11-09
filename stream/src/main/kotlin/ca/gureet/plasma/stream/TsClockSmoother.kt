// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import kotlin.math.abs

/**
 * Carries an MPEG-TS stream's clock straight on across a jump (#138), as the stream comes in and
 * before anything keeps it: a provider whose side starts over sends a clock that jumps back or
 * forward, which VLC can't get over. From a jump in the program's clock (its PCR) on, everything
 * moves by the jump: the PCR in each adaptation field, and the PTS and DTS in each PES header, on
 * every PID, rewritten in place. So the clock goes on a step from where it was, and the rewind
 * buffer, local catch-up and recordings get it smooth too.
 *
 * Each PID's timestamps can cross the jump a little after the PCR does, so each PID takes the new
 * offset when its own timestamps cross it: when they no longer follow on from its last ones, it
 * takes the newest offset whose clock they fit. The clock wrapping at 2^33 ticks stays a wrap.
 *
 * A jump the stream marks as a discontinuity is carried on too, and the mark comes off, so no
 * engine starts its clock over. Each PID's continuity counter is carried on across a jump as well:
 * a provider's side that starts over starts its counters over, and with the mark gone an engine
 * would take the packets after the jump for damaged ones.
 *
 * Bytes that aren't MPEG-TS go through as they are. Whole packets go on as they're read; a packet
 * a chunk ends half way through goes on with the next chunk. [clock] says when each packet came,
 * in ns.
 */
internal class TsClockSmoother(private val clock: () -> Long = System::nanoTime) {
    /** How many jumps were carried on, and the last of them. */
    @Volatile var jumps: Int = 0
        private set

    @Volatile var lastJump: TsScanner.ClockJump? = null
        private set

    /** Whether the stream is MPEG-TS, as its first bytes say; null until they've come. */
    private var transportStream: Boolean? = null

    /** A packet the last chunk ended half way through. */
    private val partial = ByteArray(PACKET)
    private var partialLength = 0

    /** The PID carrying the program's clock: the first that carries one. */
    private var pcrPid = -1
    private var lastPcr = -1L
    private var lastPcrAt = 0L
    private var lastOut = -1L

    /** How far the clock usually moves from one PCR to the next, in 90 kHz ticks. */
    private var step = DEFAULT_STEP

    /** The clock's offsets so far, the latest last: from the start, then from each jump on. */
    private val epochs = ArrayList<Epoch>().apply { add(Epoch(offset = 0, first = -1)) }

    /** How many epochs have gone from the front of [epochs], so a PID's epoch stays a number. */
    private var dropped = 0
    private val pids = HashMap<Int, Pid>()

    /** Each PID's continuity counter as it went out, whether it counted up at its last packet, and how far it's moved. */
    private val counters = IntArray(PIDS) { -1 }
    private val counting = BooleanArray(PIDS)
    private val counterShift = IntArray(PIDS)

    /** PIDs whose counter is carried on at their next packet, from a jump on. */
    private val splicing = BooleanArray(PIDS)

    /** A connection started again: the half packet the last one left is gone. The clock carries on. */
    fun resync() {
        partialLength = 0
    }

    /** Rewrites [count] bytes of [chunk] in place, and hands [keep] what's ready, in order. */
    fun feed(chunk: ByteArray, count: Int, keep: (bytes: ByteArray, offset: Int, length: Int) -> Unit) {
        if (count <= 0) return
        val ts = transportStream ?: looksLikeTs(chunk, count).also { transportStream = it }
        if (!ts) return keep(chunk, 0, count)
        var at = 0
        // The packet the last chunk began.
        if (partialLength > 0) {
            val take = minOf(PACKET - partialLength, count)
            System.arraycopy(chunk, 0, partial, partialLength, take)
            partialLength += take
            at = take
            if (partialLength < PACKET) return
            partialLength = 0
            rewrite(partial, 0)
            keep(partial, 0, PACKET)
        }
        val from = at
        while (at < count) {
            // Out of step: bytes go through until a packet starts again.
            if (chunk[at].toInt() and 0xFF != SYNC) {
                at++
                continue
            }
            // A stray sync byte: the next packet doesn't start where this one ends.
            if (count - at > PACKET && chunk[at + PACKET].toInt() and 0xFF != SYNC) {
                at++
                continue
            }
            if (count - at < PACKET) {
                if (at > from) keep(chunk, from, at - from)
                System.arraycopy(chunk, at, partial, 0, count - at)
                partialLength = count - at
                return
            }
            rewrite(chunk, at)
            at += PACKET
        }
        if (at > from) keep(chunk, from, at - from)
    }

    /** The packet at [start] of [bytes], its clock and timestamps moved as they need to be. */
    private fun rewrite(bytes: ByteArray, start: Int) {
        val pid = ((bytes[start + 1].toInt() and 0x1F) shl 8) or (bytes[start + 2].toInt() and 0xFF)
        if (pid == NULL_PID) return
        val unitStart = bytes[start + 1].toInt() and PAYLOAD_START != 0
        val control = (bytes[start + 3].toInt() shr 4) and 0x03
        val adaptationLength = if (control and 0x02 != 0) bytes[start + 4].toInt() and 0xFF else -1
        if (adaptationLength >= PCR_FIELD && bytes[start + FLAGS_AT].toInt() and PCR_FLAG != 0) clock(pid, bytes, start)
        counter(pid, bytes, start, payload = control and 0x01 != 0)
        // No payload, or a scrambled one, has no PES header to read.
        val scrambled = (bytes[start + 3].toInt() shr 6) and 0x03 != 0
        if (!unitStart || control and 0x01 == 0 || scrambled) return
        val payload = start + HEADER + if (adaptationLength >= 0) 1 + adaptationLength else 0
        if (payload < start + PACKET) timestamps(pid, bytes, payload, start + PACKET)
    }

    /** The program's clock in the packet at [start]: a jump starts a new epoch, and the clock carries on. */
    private fun clock(pid: Int, bytes: ByteArray, start: Int) {
        if (pcrPid < 0) pcrPid = pid
        if (pid != pcrPid) return
        val at = start + PCR_AT
        val pcr = ((bytes[at].toLong() and 0xFF) shl 25) or ((bytes[at + 1].toLong() and 0xFF) shl 17) or
            ((bytes[at + 2].toLong() and 0xFF) shl 9) or ((bytes[at + 3].toLong() and 0xFF) shl 1) or ((bytes[at + 4].toLong() and 0xFF) shr 7)
        val now = clock()
        val before = lastPcr
        val waited = (now - lastPcrAt) / NANOS_PER_TICK
        lastPcr = pcr
        lastPcrAt = now
        if (before >= 0) {
            val ticks = wrapped(pcr - before)
            val previous = epochs.getOrNull(epochs.lastIndex - 1)
            if (previous != null && epochs.last().first == before && jumped(ticks, waited) && !jumped(wrapped(pcr - previous.last), (now - previous.lastAt) / NANOS_PER_TICK)) {
                // One bad clock, and then the clock as it was: that epoch goes, and the clock goes on as before it.
                epochs.removeAt(epochs.lastIndex)
            } else if (jumped(ticks, waited)) {
                // A step on from where the clock was, as if nothing had happened.
                val marked = bytes[start + FLAGS_AT].toInt() and DISCONTINUITY != 0
                epochs += Epoch(offset = wrap(lastOut + step - pcr), first = pcr)
                if (epochs.size > EPOCHS) {
                    epochs.removeAt(0)
                    dropped++
                }
                if (marked) bytes[start + FLAGS_AT] = (bytes[start + FLAGS_AT].toInt() and DISCONTINUITY.inv()).toByte()
                // Every PID's counter is carried on from its next packet, this one's included.
                splicing.fill(true)
                // The jump first: whoever sees the count go up finds it.
                lastJump = TsScanner.ClockJump(ticks / TICKS_PER_MS, marked)
                jumps++
            } else if (ticks in 1..MAX_STEP) {
                step = ticks
            }
        }
        // Where the clock has got to in this epoch, as it came, and when.
        val epoch = epochs.last()
        if (epoch.first < 0) epoch.first = pcr
        epoch.last = pcr
        epoch.lastAt = now
        val out = wrap(pcr + epoch.offset)
        lastOut = out
        bytes[at] = (out shr 25).toByte()
        bytes[at + 1] = (out shr 17).toByte()
        bytes[at + 2] = (out shr 9).toByte()
        bytes[at + 3] = (out shr 1).toByte()
        // The base's last bit, then the reserved bits and the extension's first, as they were.
        bytes[at + 4] = (((out and 1) shl 7).toInt() or (bytes[at + 4].toInt() and 0x7F)).toByte()
    }

    /**
     * The continuity counter of the packet at [start] on [pid], carried on across a jump: the first
     * packet after it takes the next count from the last before it (the same count, without a
     * payload), and the PID's packets after it move as far. Only a PID whose counter counted up
     * before the jump: one that doesn't keeps its counts.
     */
    private fun counter(pid: Int, bytes: ByteArray, start: Int, payload: Boolean) {
        val count = bytes[start + 3].toInt() and 0x0F
        val last = counters[pid]
        if (splicing[pid]) {
            splicing[pid] = false
            if (last >= 0 && counting[pid]) counterShift[pid] = ((if (payload) last + 1 else last) - count) and 0x0F
        }
        val out = (count + counterShift[pid]) and 0x0F
        if (out != count) bytes[start + 3] = ((bytes[start + 3].toInt() and 0xF0) or out).toByte()
        // A packet without a payload keeps the count it follows.
        if (payload) {
            counting[pid] = last >= 0 && out == ((last + 1) and 0x0F)
            counters[pid] = out
        }
    }

    /** The PTS and DTS of the PES that starts at [at] on [pid], moved by its PID's epoch. */
    private fun timestamps(pid: Int, bytes: ByteArray, at: Int, end: Int) {
        // 00 00 01, the stream, its length, two flag bytes, the header's length: then PTS and DTS.
        if (at + PES_HEADER > end || bytes[at].toInt() != 0 || bytes[at + 1].toInt() != 0 || bytes[at + 2].toInt() != 1) return
        if (bytes[at + 3].toInt() and 0xFF in NO_HEADER) return
        val flags = (bytes[at + 7].toInt() shr 6) and 0x03
        if (flags and 0x02 == 0) return
        val hasDts = flags == 0x03
        if (at + PES_HEADER + (if (hasDts) 2 * STAMP else STAMP) > end) return
        val pts = read(bytes, at + PES_HEADER)
        val dts = if (hasDts) read(bytes, at + PES_HEADER + STAMP) else pts
        val latest = dropped + epochs.lastIndex
        val own = pids.getOrPut(pid) { Pid(epoch = latest, last = dts) }
        own.epoch = own.epoch.coerceIn(dropped, latest)
        // Its timestamps jumped, or it's sparse: the newest epoch whose clock they fit, from its own on.
        if (abs(wrapped(dts - own.last)) > FOLLOWS_ON) own.epoch = crossing(dts - own.lead, own.epoch)
        own.last = dts
        // How far its timestamps run ahead of the clock, while it's on the latest epoch.
        if (own.epoch == latest && lastPcr >= 0) own.lead = wrapped(dts - lastPcr).coerceIn(-MAX_LEAD, MAX_LEAD)
        val offset = epochs[(own.epoch - dropped).coerceIn(0, epochs.lastIndex)].offset
        if (offset == 0L) return
        write(bytes, at + PES_HEADER, wrap(pts + offset))
        if (hasDts) write(bytes, at + PES_HEADER + STAMP, wrap(dts + offset))
    }

    /**
     * The epoch a PID belongs with once its timestamps no longer follow on: the newest, from [from]
     * on, whose clock as it came holds [clock], give or take [MAX_LEAD]. [clock] is the PID's
     * timestamp less how far its timestamps ran ahead of the clock, which can change when the
     * provider's side starts over. A jump back lands where the clock has already been, inside the
     * epoch before it too, so the newest one that fits wins: a PID's timestamps jump when the
     * stream's clock has. With none, it stays on its own. A PID never goes back an epoch.
     */
    private fun crossing(clock: Long, from: Int): Int {
        for (index in epochs.lastIndex downTo (from - dropped).coerceAtLeast(0)) {
            val epoch = epochs[index]
            if (epoch.first < 0) continue
            if (wrapped(clock - epoch.first) >= -MAX_LEAD && wrapped(clock - epoch.last) <= MAX_LEAD) return dropped + index
        }
        return from
    }

    /** Whether the clock moving [ticks] in the [waited] ticks it took to come is a jump: as [TsScanner] finds them. */
    private fun jumped(ticks: Long, waited: Long): Boolean = ticks < -JUMP_TICKS || ticks - waited > JUMP_TICKS

    /** An offset from a jump on, and the clock as it came in it: its first and its last, and when the last came. */
    private class Epoch(val offset: Long, var first: Long) {
        var last: Long = first
        var lastAt: Long = 0
    }

    /** A PID's epoch, counted from the start, its last timestamp as it came, and how far its timestamps run ahead of the clock. */
    private class Pid(var epoch: Int, var last: Long, var lead: Long = 0)

    companion object {
        const val PACKET = 188
        private const val SYNC = 0x47
        private const val HEADER = 4
        private const val NULL_PID = 0x1FFF
        private const val PIDS = 0x2000
        private const val PAYLOAD_START = 0x40
        private const val FLAGS_AT = 5
        private const val PCR_AT = 6
        private const val PCR_FIELD = 7
        private const val PCR_FLAG = 0x10
        private const val DISCONTINUITY = 0x80

        /** A PES header to its PTS: nine bytes. Each timestamp is five. */
        private const val PES_HEADER = 9
        private const val STAMP = 5

        /** Streams whose PES have no header of timestamps: the program stream map, padding, private 2, ECM, EMM, DSM-CC, H.222.1 E, the directory. */
        private val NO_HEADER = setOf(0xBC, 0xBE, 0xBF, 0xF0, 0xF1, 0xF2, 0xF8, 0xFF)

        private const val WRAP = 1L shl 33
        private const val TICKS_PER_MS = 90L
        private const val NANOS_PER_TICK = 1_000_000L / TICKS_PER_MS

        /** A clock that moves this far more than the time it took, either way, has jumped: as [TsScanner] finds them. */
        private const val JUMP_TICKS = 1_000 * TICKS_PER_MS

        /** The step the clock is taken to move between PCRs until one's seen: 40 ms, and at most the standard's 100. */
        private const val DEFAULT_STEP = 40 * TICKS_PER_MS
        private const val MAX_STEP = 100 * TICKS_PER_MS

        /** A PID's timestamps this close to its last follow on from them, through B-frames' reordering. */
        private const val FOLLOWS_ON = 1_000 * TICKS_PER_MS

        /**
         * As far as a PID's timestamps are taken to run ahead of the clock, or behind it; and how far
         * a PID's clock can be from an epoch's and still fit it.
         */
        private const val MAX_LEAD = 5_000 * TICKS_PER_MS

        /** The epochs kept for PIDs that are slow to cross a jump. */
        private const val EPOCHS = 8

        /** MPEG-TS packets, one after the next, as far as the first bytes reach. */
        internal fun looksLikeTs(bytes: ByteArray, count: Int): Boolean =
            bytes[0].toInt() and 0xFF == SYNC && (1..2).all { it * PACKET >= count || bytes[it * PACKET].toInt() and 0xFF == SYNC }

        /** [value] on the clock's 33 bits. */
        internal fun wrap(value: Long): Long = value and (WRAP - 1)

        /** A difference on a clock that wraps: the shorter way round. */
        internal fun wrapped(ticks: Long): Long {
            val inRange = ((ticks % WRAP) + WRAP) % WRAP
            return if (inRange >= WRAP / 2) inRange - WRAP else inRange
        }

        /** A PTS or DTS: 33 bits in five bytes, among marker bits. */
        internal fun read(bytes: ByteArray, at: Int): Long =
            (((bytes[at].toLong() and 0x0E) shr 1) shl 30) or
                ((bytes[at + 1].toLong() and 0xFF) shl 22) or
                (((bytes[at + 2].toLong() and 0xFE) shr 1) shl 15) or
                ((bytes[at + 3].toLong() and 0xFF) shl 7) or
                ((bytes[at + 4].toLong() and 0xFE) shr 1)

        /** Writes [value] where [read] found one, keeping the prefix and the marker bits. */
        internal fun write(bytes: ByteArray, at: Int, value: Long) {
            bytes[at] = ((bytes[at].toInt() and 0xF1) or (((value shr 30).toInt() and 0x07) shl 1)).toByte()
            bytes[at + 1] = (value shr 22).toByte()
            bytes[at + 2] = ((((value shr 15).toInt() and 0x7F) shl 1) or (bytes[at + 2].toInt() and 0x01)).toByte()
            bytes[at + 3] = (value shr 7).toByte()
            bytes[at + 4] = (((value.toInt() and 0x7F) shl 1) or (bytes[at + 4].toInt() and 0x01)).toByte()
        }
    }
}
