// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * An HLS channel read for a recording (brief 5.6: "For HLS, download the segments and append
 * them"): its media playlist, or the best of a master playlist's variants, fetched again as its
 * target duration says; each new segment downloaded in order, AES-128 ones decrypted, and read as
 * one MPEG-TS stream from a few segments behind live. fMP4 segments are turned into MPEG-TS as
 * they come ([Fmp4ToTs], #123), with the variant's separate audio rendition, when it has one,
 * read alongside and merged in. SAMPLE-AES, and fMP4 that's encrypted or in codecs MPEG-TS can't
 * carry here, can't be recorded: [unsupported] says so, and it ends. A playlist that can't be had
 * for [giveUpMs] ends it too. [onClose] lets go of the connection it was counted as.
 */
class HlsReader(
    private val http: OkHttpClient,
    private val url: String,
    private val headers: Map<String, String> = emptyMap(),
    private val giveUpMs: Long = GIVE_UP_MS,
    private val onClose: () -> Unit = {},
) : AutoCloseable {
    /** Whether the channel's HLS can't be recorded at all. */
    @Volatile var unsupported = false
        private set

    /** A media playlist being read: the channel's own ([main]), or its separate audio. */
    private inner class Playlist(val rendition: Int, var address: HttpUrl?) {
        /** The next segment to read, by its number; null until the playlist's been read. */
        var next: Long? = null
        val pending = ArrayDeque<HlsPlaylist.Segment>()
        var refreshAt = 0L
        var ended = false
        var failingSince: Long? = null

        /** The init segment [converter] has from this playlist, by where it is. */
        var init: Pair<HttpUrl, LongRange?>? = null
    }

    /**
     * The channel's playlist: its address, until a master playlist has said which media playlist,
     * or where its address led: read from there after that, as players do.
     */
    private val main = Playlist(MAIN, null)

    /** The variant's separate audio, read alongside once its picture turns out to be fMP4. */
    private var audio: Playlist? = null

    /** The variant's audio rendition, from the master playlist. */
    private var audioRendition: HlsPlaylist.Rendition? = null
    private var converter: Fmp4ToTs? = null

    /** Since when the channel's playlist has waited for its audio to catch up. */
    private var waitingSince: Long? = null
    private var flushed = false
    private var current: ByteArray? = null
    private var currentAt = 0
    private val keys = HashMap<HttpUrl, ByteArray>()
    @Volatile private var call: Call? = null
    @Volatile private var closed = false

    /**
     * Copies what's come into [into], waiting up to [waitMs] for something to, as the hub's reader
     * does: the count, 0 when nothing came in time, or -1 once it's ended or can't go on.
     */
    fun read(into: ByteArray, waitMs: Long): Int {
        val deadline = System.currentTimeMillis() + waitMs
        while (!closed) {
            current?.let { data ->
                val count = minOf(into.size, data.size - currentAt)
                data.copyInto(into, 0, currentAt, currentAt + count)
                currentAt += count
                if (currentAt >= data.size) current = null
                return count
            }
            if (unsupported) return -1
            val now = System.currentTimeMillis()
            val due = playlists().firstOrNull { !it.ended && now >= it.refreshAt }
            if (due != null) {
                refresh(due)
                continue
            }
            val playlist = nextToRead(now)
            if (playlist != null) {
                // One that can't be had is a gap.
                current = take(playlist, playlist.pending.removeFirst())?.takeIf { it.isNotEmpty() }
                currentAt = 0
                continue
            }
            if (playlists().all { it.ended && it.pending.isEmpty() }) {
                // What's left in the converter goes out once; then it's over.
                if (!flushed) {
                    flushed = true
                    current = converter?.drain(all = true)?.takeIf { it.isNotEmpty() }
                    currentAt = 0
                    continue
                }
                return -1
            }
            if (now >= deadline) return 0
            val wake = playlists().filter { !it.ended }.minOfOrNull { it.refreshAt } ?: deadline
            Thread.sleep((minOf(wake, deadline) - now).coerceIn(1, MAX_NAP_MS))
        }
        return -1
    }

    private fun playlists(): List<Playlist> = listOfNotNull(main, audio)

    /**
     * The playlist to download a segment from: the one that's further behind, so the picture and
     * the sound are merged in order, the picture first. When the one behind has nothing new, the
     * other may go [AHEAD_TICKS] further, then waits for it, until it's been [giveUpMs] without
     * catching up; then the one ahead goes on alone.
     */
    private fun nextToRead(now: Long): Playlist? {
        val sound = audio
        val converter = converter
        if (sound == null || converter == null) return main.takeIf { it.pending.isNotEmpty() }
        val mine = converter.reached(MAIN)
        val theirs = converter.reached(AUDIO)
        val behind = when {
            mine == null -> main
            theirs == null -> sound
            theirs < mine -> sound
            else -> main
        }
        val other = if (behind === main) sound else main
        if (behind.pending.isNotEmpty()) {
            waitingSince = null
            return behind
        }
        if (other.pending.isEmpty()) return null
        val near = mine != null && theirs != null && abs(mine - theirs) < AHEAD_TICKS
        if (behind.ended || near) {
            waitingSince = null
            return other
        }
        val since = waitingSince ?: now.also { waitingSince = it }
        if (now - since < giveUpMs) return null
        // Waited too long: the one behind is let go, or, for the picture, left to catch up.
        waitingSince = null
        if (behind === sound) dropAudio()
        return other
    }

    /** The variant's audio can't be had, or merged: the recording goes on without it. */
    private fun dropAudio() {
        audio = null
        converter?.retire(AUDIO)
    }

    /** Reads [playlist] again: what's new goes in line. */
    private fun refresh(playlist: Playlist) {
        val target = playlist.address ?: (if (playlist === main) url.toHttpUrlOrNull() else null) ?: run {
            playlist.ended = true
            return
        }
        val fetched = try {
            get(target).use { response -> if (response.isSuccessful) response.body.string() to response.request.url else null }
        } catch (failed: IOException) {
            null
        }
        when (val parsed = fetched?.let { (text, base) -> HlsPlaylist.parse(text, base) }) {
            null -> {
                // Where the channel's address led may have expired: from the start again next time.
                if (playlist === main) playlist.address = null
                failed(playlist)
            }
            is HlsPlaylist.Master -> {
                if (playlist !== main) return failed(playlist)
                // The best it offers, as a recording keeps it, and its sound when that's apart.
                val variant = parsed.variants.maxByOrNull { it.bandwidth } ?: return failed(playlist)
                playlist.address = variant.url
                audioRendition = parsed.audioOf(variant)
                playlist.refreshAt = 0
            }
            is HlsPlaylist.Media -> {
                playlist.failingSince = null
                fetched?.let { playlist.address = it.second }
                // SAMPLE-AES and the like: the sound alone can go; the channel's own playlist can't.
                val encrypted = parsed.segments.any { segment -> segment.key?.method?.let { it != AES_128 } == true }
                if (playlist === main) {
                    if (encrypted) {
                        unsupported = true
                        return
                    }
                    if (parsed.fragmented) startFragmented()
                } else if (encrypted || !parsed.fragmented) {
                    // A separate audio rendition in TS can't be merged with fMP4 here.
                    return dropAudio()
                }
                queue(playlist, parsed)
            }
        }
    }

    /** The channel turned out fMP4: a converter, and its separate audio read alongside. */
    private fun startFragmented() {
        if (converter != null) return
        val made = Fmp4ToTs()
        converter = made
        audioRendition?.url?.let { address ->
            audio = Playlist(AUDIO, address)
            made.expect(AUDIO)
        }
    }

    /** What's new in [parsed], [playlist]'s latest, goes in line. */
    private fun queue(playlist: Playlist, parsed: HlsPlaylist.Media) {
        val segments = parsed.segments
        // Numbered from much lower than before: the stream started again.
        val last = parsed.firstSequence + segments.size - 1
        if ((playlist.next ?: 0) - last > segments.size + RESTARTED) playlist.next = null
        // From a few segments behind live, as a player starts; all of one that's ended.
        val start = playlist.next ?: if (parsed.ended) parsed.firstSequence else parsed.firstSequence + (segments.size - LIVE_SEGMENTS).coerceAtLeast(0)
        // Fallen behind the playlist's window: on from its first.
        val from = maxOf(start, parsed.firstSequence)
        val fresh = segments.filter { it.sequence >= from }
        playlist.pending.addAll(fresh)
        playlist.next = fresh.lastOrNull()?.let { it.sequence + 1 } ?: from
        playlist.ended = parsed.ended
        playlist.refreshAt = System.currentTimeMillis() + if (fresh.isEmpty()) parsed.targetDurationMs / 2 else parsed.targetDurationMs
    }

    /** The playlist couldn't be had: tried again in a moment, until it's been too long. */
    private fun failed(playlist: Playlist) {
        val now = System.currentTimeMillis()
        val since = playlist.failingSince ?: now.also { playlist.failingSince = it }
        if (now - since >= giveUpMs) {
            playlist.ended = true
            if (playlist === audio) dropAudio()
        }
        playlist.refreshAt = now + RETRY_MS
    }

    /**
     * [segment] of [playlist] as MPEG-TS: as it is, or turned from fMP4, with its init segment
     * read first when it's a new one. Null when it can't be had, or nothing's ready to go out yet.
     */
    private fun take(playlist: Playlist, segment: HlsPlaylist.Segment): ByteArray? {
        val init = segment.init ?: return if (playlist === main) download(segment) else null
        val converter = converter ?: Fmp4ToTs().also { converter = it }
        val at = init.url to init.range
        if (playlist.init != at) {
            val bytes = bytes(init.url, init.range)?.let { decrypt(it, init.key, null) } ?: return null
            // The sound's language from the master playlist, where its init segment doesn't say.
            val language = audioRendition?.language?.takeIf { playlist === audio }
            if (!converter.init(playlist.rendition, bytes, language)) {
                // Its sound alone can go; the channel's own, or its picture, can't.
                if (playlist === main) unsupported = true else dropAudio()
                return null
            }
            playlist.init = at
        }
        val body = download(segment) ?: return null
        converter.media(playlist.rendition, body, segment.range?.first ?: 0)
        return converter.drain()
    }

    /** [segment]'s bytes, decrypted; null when they can't be had. */
    private fun download(segment: HlsPlaylist.Segment): ByteArray? {
        val body = bytes(segment.url, segment.range) ?: return null
        return decrypt(body, segment.key, segment.sequence)
    }

    /**
     * [body] decrypted with [key]; as it is without one. Without an IV, it's the segment's
     * [sequence] (RFC 8216, 5.2), which an init segment hasn't: null then, as when it can't be.
     */
    private fun decrypt(body: ByteArray, key: HlsPlaylist.Key?, sequence: Long?): ByteArray? {
        if (key == null) return body
        val secret = key.url?.let { url -> keys[url] ?: bytes(url)?.takeIf { it.size == KEY_BYTES }?.also { keys[url] = it } } ?: return null
        val iv = key.iv ?: sequence?.let { number -> ByteArray(KEY_BYTES).also { iv -> for (i in 0 until Long.SIZE_BYTES) iv[KEY_BYTES - 1 - i] = (number ushr (Byte.SIZE_BITS * i)).toByte() } } ?: return null
        return try {
            Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), IvParameterSpec(iv))
                doFinal(body)
            }
        } catch (failed: GeneralSecurityException) {
            null
        }
    }

    /** What's at [target], or [range] of it; null when it can't be had, or is too big to be a segment. */
    private fun bytes(target: HttpUrl, range: LongRange? = null): ByteArray? = try {
        get(target, range).use { response ->
            val source = response.body.source()
            when {
                !response.isSuccessful -> null
                source.request(MAX_SEGMENT_BYTES + 1) -> null
                else -> source.readByteArray()
            }
        }
    } catch (failed: IOException) {
        null
    }

    private fun get(target: HttpUrl, range: LongRange? = null): Response {
        val request = Request.Builder().url(target).apply {
            headers.forEach { (name, value) -> header(name, value) }
            range?.let { header("Range", "bytes=${it.first}-${it.last}") }
        }.build()
        return http.newCall(request).also { call = it }.execute()
    }

    override fun close() {
        if (closed) return
        closed = true
        call?.cancel()
        onClose()
    }

    companion object {
        /** A playlist that can't be had for this long: the channel's dropped, and the recorder opens it again. */
        const val GIVE_UP_MS = 30_000L

        private const val RETRY_MS = 2_000L

        /** How far behind live it starts, as players do. */
        private const val LIVE_SEGMENTS = 3

        /** How far back the numbering has to go to be a stream that started again. */
        private const val RESTARTED = 10
        private const val KEY_BYTES = 16
        private const val MAX_SEGMENT_BYTES = 64L * 1024 * 1024
        private const val AES_128 = "AES-128"

        /** The renditions, as [Fmp4ToTs] knows them. */
        private const val MAIN = 0
        private const val AUDIO = 1

        /** How far, in 90 kHz, the picture or the sound may be read ahead of the other: 10 seconds. */
        private const val AHEAD_TICKS = 900_000L

        /** The longest a read sleeps at once, so it hears of closing. */
        private const val MAX_NAP_MS = 500L
    }
}
