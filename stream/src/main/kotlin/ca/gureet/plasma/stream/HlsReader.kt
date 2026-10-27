// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
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
 * one MPEG-TS stream from a few segments behind live. fMP4 segments, and SAMPLE-AES, can't be
 * appended to a TS file: [unsupported] says so, and it ends. A playlist that can't be had for
 * [giveUpMs] ends it too. [onClose] lets go of the connection it was counted as.
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

    /**
     * The media playlist, once a master playlist has said which, or where its address led: read
     * from there after that, as players do.
     */
    private var media: HttpUrl? = null

    /** The next segment to read, by its number; null until the playlist's been read. */
    private var next: Long? = null
    private val pending = ArrayDeque<HlsPlaylist.Segment>()
    private var current: ByteArray? = null
    private var currentAt = 0
    private var refreshAt = 0L
    private var ended = false
    private var failingSince: Long? = null
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
            val segment = pending.removeFirstOrNull()
            if (segment != null) {
                // One that can't be had is a gap.
                current = download(segment)?.takeIf { it.isNotEmpty() }
                currentAt = 0
                continue
            }
            if (ended || unsupported) return -1
            val now = System.currentTimeMillis()
            if (now >= refreshAt) {
                refresh()
                continue
            }
            if (now >= deadline) return 0
            Thread.sleep(minOf(refreshAt, deadline) - now)
        }
        return -1
    }

    /** Reads the playlist again: what's new goes in line. */
    private fun refresh() {
        val target = media ?: url.toHttpUrlOrNull() ?: run {
            ended = true
            return
        }
        val fetched = try {
            get(target).use { response -> if (response.isSuccessful) response.body.string() to response.request.url else null }
        } catch (failed: IOException) {
            null
        }
        when (val playlist = fetched?.let { (text, base) -> HlsPlaylist.parse(text, base) }) {
            null -> {
                // Where it led may have expired: from the start again next time.
                media = null
                failed()
            }
            is HlsPlaylist.Master -> {
                // The best it offers, as a recording keeps it.
                media = playlist.variants.maxByOrNull { it.bandwidth }?.url ?: return failed()
                refreshAt = 0
            }
            is HlsPlaylist.Media -> {
                failingSince = null
                fetched?.let { media = it.second }
                if (playlist.fragmented || playlist.segments.any { segment -> segment.key?.method?.let { it != AES_128 } == true }) {
                    unsupported = true
                    return
                }
                val segments = playlist.segments
                // Numbered from much lower than before: the stream started again.
                val last = playlist.firstSequence + segments.size - 1
                if ((next ?: 0) - last > segments.size + RESTARTED) next = null
                // From a few segments behind live, as a player starts; all of one that's ended.
                val start = next ?: if (playlist.ended) playlist.firstSequence else playlist.firstSequence + (segments.size - LIVE_SEGMENTS).coerceAtLeast(0)
                // Fallen behind the playlist's window: on from its first.
                val from = maxOf(start, playlist.firstSequence)
                val fresh = segments.filter { it.sequence >= from }
                pending.addAll(fresh)
                next = fresh.lastOrNull()?.let { it.sequence + 1 } ?: from
                ended = playlist.ended
                refreshAt = System.currentTimeMillis() + if (fresh.isEmpty()) playlist.targetDurationMs / 2 else playlist.targetDurationMs
            }
        }
    }

    /** The playlist couldn't be had: tried again in a moment, until it's been too long. */
    private fun failed() {
        val now = System.currentTimeMillis()
        val since = failingSince ?: now.also { failingSince = it }
        if (now - since >= giveUpMs) ended = true
        refreshAt = now + RETRY_MS
    }

    /** [segment]'s bytes, decrypted; null when they can't be had. */
    private fun download(segment: HlsPlaylist.Segment): ByteArray? {
        val body = bytes(segment.url, segment.range) ?: return null
        val key = segment.key ?: return body
        val secret = key.url?.let { url -> keys[url] ?: bytes(url)?.takeIf { it.size == KEY_BYTES }?.also { keys[url] = it } } ?: return null
        // Without one, the IV is the segment's number (RFC 8216, 5.2).
        val iv = key.iv ?: ByteArray(KEY_BYTES).also { iv -> for (i in 0 until Long.SIZE_BYTES) iv[KEY_BYTES - 1 - i] = (segment.sequence ushr (Byte.SIZE_BITS * i)).toByte() }
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
    }
}
