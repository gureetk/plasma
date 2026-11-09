// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.stream

import okhttp3.HttpUrl

/**
 * An HLS playlist (RFC 8216), as much of it as recording a channel needs (brief 5.6): a master
 * playlist's variants, or a media playlist's segments.
 */
sealed interface HlsPlaylist {
    /** A master playlist: the same channel at different bitrates, and the renditions they share (`EXT-X-MEDIA`). */
    data class Master(val variants: List<Variant>, val renditions: List<Rendition> = emptyList()) : HlsPlaylist {
        /**
         * [variant]'s sound when it's apart from its picture: its audio group's default rendition,
         * else one picked automatically, else its first. Null when the sound is in the variant's
         * own segments.
         */
        fun audioOf(variant: Variant): Rendition? {
            val group = renditions.filter { it.type == "AUDIO" && it.group == variant.audio }
            return (group.firstOrNull { it.default } ?: group.firstOrNull { it.autoselect } ?: group.firstOrNull())?.takeIf { it.url != null }
        }
    }

    /**
     * A media playlist: its segments, the first numbered [firstSequence]; [ended] once the
     * provider says no more are coming. [fragmented] is fMP4 (`EXT-X-MAP`), turned into MPEG-TS
     * to be recorded ([Fmp4ToTs]).
     */
    data class Media(
        val targetDurationMs: Long,
        val firstSequence: Long,
        val segments: List<Segment>,
        val ended: Boolean = false,
        val fragmented: Boolean = false,
    ) : HlsPlaylist

    /** A variant, and the group its separate audio renditions are in, if it has them. */
    data class Variant(val url: HttpUrl, val bandwidth: Long, val audio: String? = null)

    /** An `EXT-X-MEDIA` rendition: its [type] (AUDIO, SUBTITLES…), group, and playlist when it has one of its own. */
    data class Rendition(val type: String, val group: String, val url: HttpUrl?, val default: Boolean = false, val autoselect: Boolean = false, val language: String? = null)

    /**
     * A segment, numbered [sequence], with the key it's encrypted with, its byte range in [url]
     * when it has one, and the init segment it follows (fMP4) when it does.
     */
    data class Segment(val sequence: Long, val url: HttpUrl, val key: Key? = null, val range: LongRange? = null, val init: Init? = null)

    /** An fMP4 init segment (`EXT-X-MAP`): where it is, and the key it's encrypted with when it is. */
    data class Init(val url: HttpUrl, val range: LongRange? = null, val key: Key? = null)

    /** How segments are encrypted: AES-128 with the key at [url], and [iv] when the playlist gives one. */
    class Key(val method: String, val url: HttpUrl?, val iv: ByteArray?)

    companion object {
        /** [text], a playlist fetched from [base], which its addresses are relative to; null when it isn't one. */
        fun parse(text: String, base: HttpUrl): HlsPlaylist? {
            val lines = text.removePrefix("﻿").lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.firstOrNull() != "#EXTM3U") return null
            val variants = ArrayList<Variant>()
            val renditions = ArrayList<Rendition>()
            val segments = ArrayList<Segment>()
            var targetMs = DEFAULT_TARGET_MS
            var firstSequence = 0L
            var ended = false
            var fragmented = false
            var key: Key? = null
            var init: Init? = null
            // What's said about the next address.
            var variant: Map<String, String>? = null
            var segment = false
            var lastRangeEnd: Pair<HttpUrl, Long>? = null
            var length: Long? = null
            var offset: Long? = null
            for (line in lines.drop(1)) {
                when {
                    line.startsWith("#EXT-X-STREAM-INF:") -> variant = attributes(line.substringAfter(':'))
                    line.startsWith("#EXT-X-TARGETDURATION:") -> line.substringAfter(':').toDoubleOrNull()?.let { targetMs = (it * MS_PER_S).toLong() }
                    line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> line.substringAfter(':').toLongOrNull()?.let { firstSequence = it }
                    line.startsWith("#EXT-X-KEY:") -> {
                        val attributes = attributes(line.substringAfter(':'))
                        val method = attributes["METHOD"] ?: "NONE"
                        key = if (method == "NONE") null else Key(method, attributes["URI"]?.let(base::resolve), attributes["IV"]?.let(::hex))
                    }
                    line.startsWith("#EXT-X-MAP:") -> {
                        fragmented = true
                        val attributes = attributes(line.substringAfter(':'))
                        init = attributes["URI"]?.let(base::resolve)?.let { url -> Init(url, attributes["BYTERANGE"]?.let(::range), key) }
                    }
                    line.startsWith("#EXT-X-MEDIA:") -> {
                        val attributes = attributes(line.substringAfter(':'))
                        renditions += Rendition(
                            type = attributes["TYPE"].orEmpty(),
                            group = attributes["GROUP-ID"].orEmpty(),
                            url = attributes["URI"]?.let(base::resolve),
                            default = attributes["DEFAULT"] == "YES",
                            autoselect = attributes["AUTOSELECT"] == "YES",
                            language = attributes["LANGUAGE"],
                        )
                    }
                    line == "#EXT-X-ENDLIST" -> ended = true
                    line.startsWith("#EXTINF:") -> segment = true
                    line.startsWith("#EXT-X-BYTERANGE:") -> {
                        val value = line.substringAfter(':')
                        length = value.substringBefore('@').toLongOrNull()
                        offset = value.substringAfter('@', "").toLongOrNull()
                    }
                    line.startsWith("#") -> Unit
                    else -> {
                        val url = base.resolve(line) ?: continue
                        val asked = variant
                        if (asked != null) {
                            variants += Variant(url, asked["BANDWIDTH"]?.toLongOrNull() ?: 0, asked["AUDIO"])
                        } else if (segment) {
                            val range = length?.let { count ->
                                // Without an offset, it carries on from the last range of the same file.
                                val from = offset ?: lastRangeEnd?.takeIf { it.first == url }?.second ?: 0
                                lastRangeEnd = url to from + count
                                from until from + count
                            }
                            segments += Segment(firstSequence + segments.size, url, key, range, init)
                        }
                        variant = null
                        segment = false
                        length = null
                        offset = null
                    }
                }
            }
            return if (variants.isNotEmpty()) Master(variants, renditions) else Media(targetMs, firstSequence, segments, ended, fragmented)
        }

        /** A BYTERANGE attribute, "length@offset", as the bytes it covers. */
        private fun range(value: String): LongRange? {
            val length = value.substringBefore('@').toLongOrNull() ?: return null
            val from = value.substringAfter('@', "0").toLongOrNull() ?: return null
            return from until from + length
        }

        /** `KEY=VALUE,KEY="VALUE, with commas"`. */
        private fun attributes(text: String): Map<String, String> {
            val found = LinkedHashMap<String, String>()
            var at = 0
            while (at < text.length) {
                val equals = text.indexOf('=', at)
                if (equals < 0) break
                val name = text.substring(at, equals).trim()
                var end: Int
                val value: String
                if (text.getOrNull(equals + 1) == '"') {
                    end = text.indexOf('"', equals + 2).let { if (it < 0) text.length else it }
                    value = text.substring(equals + 2, end)
                    end = text.indexOf(',', end).let { if (it < 0) text.length else it }
                } else {
                    end = text.indexOf(',', equals + 1).let { if (it < 0) text.length else it }
                    value = text.substring(equals + 1, end).trim()
                }
                found[name] = value
                at = end + 1
            }
            return found
        }

        /** "0x0123…" as its 16 bytes. */
        private fun hex(text: String): ByteArray? {
            val digits = text.removePrefix("0x").removePrefix("0X")
            if (digits.isEmpty() || digits.length > IV_BYTES * 2 || digits.any { Character.digit(it, HEX) < 0 }) return null
            val padded = digits.padStart(IV_BYTES * 2, '0')
            return ByteArray(IV_BYTES) { ((Character.digit(padded[it * 2], HEX) shl 4) or Character.digit(padded[it * 2 + 1], HEX)).toByte() }
        }

        private const val DEFAULT_TARGET_MS = 6_000L
        private const val MS_PER_S = 1_000
        private const val IV_BYTES = 16
        private const val HEX = 16
    }
}
