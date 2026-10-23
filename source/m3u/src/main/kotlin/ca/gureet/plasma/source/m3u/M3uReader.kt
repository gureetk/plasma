// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.source.m3u

import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URLDecoder
import java.util.zip.GZIPInputStream

/** The playlist's own settings, from its `#EXTM3U` line (brief 7.3). */
data class M3uHeader(
    /** Guide addresses from `url-tvg`, `x-tvg-url` or `tvg-url`, in order. */
    val guideUrls: List<String> = emptyList(),
    /** Catch-up defaults for every channel that doesn't set its own. */
    val catchup: String? = null,
    val catchupSource: String? = null,
    val catchupDays: Int? = null,
)

/** What an entry is: a live channel, or VOD that stays out of Live TV (brief 7.3, Size). */
enum class M3uKind { Live, Movie, Series }

/**
 * One channel or title: its `#EXTINF` line, any `#EXTGRP` and allowed `#EXTVLCOPT` lines, and
 * its address.
 */
data class M3uEntry(
    val name: String,
    val url: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val logo: String? = null,
    /** The playlist's own number for it (`tvg-chno`), which Plasma uses by default (5.2). */
    val number: Int? = null,
    val group: String? = null,
    val catchup: String? = null,
    val catchupDays: Int? = null,
    val catchupSource: String? = null,
    /** From `#EXTVLCOPT:http-user-agent`, for this channel only (5.4.11). */
    val userAgent: String? = null,
    /** From `#EXTVLCOPT:http-referrer`. */
    val referrer: String? = null,
    val isRadio: Boolean = false,
    val kind: M3uKind = M3uKind.Live,
)

/** The address holds something other than an M3U playlist: a web page, say, or an error message. */
class NotAPlaylist : IOException("That isn't an M3U playlist")

/**
 * Reads an M3U playlist as a stream, plain or gzipped, an entry at a time, so a playlist of
 * 100,000 lines with VOD mixed in never sits in memory whole (brief 7.3).
 *
 * `#EXTVLCOPT` lines may only set the user agent and the referrer: a playlist can't change how the
 * engine behaves in other ways.
 */
object M3uReader {
    /** @throws NotAPlaylist when the first line with anything on it is neither `#EXTM3U` nor `#EXTINF`. */
    fun read(input: InputStream, onHeader: (M3uHeader) -> Unit = {}, onEntry: (M3uEntry) -> Unit) {
        val reader = BufferedReader(InputStreamReader(unzipped(input), Charsets.UTF_8), BUFFER)
        var started = false
        var header = M3uHeader()
        // Everything since the last address belongs to the next one.
        var info: Extinf? = null
        var group: String? = null
        var userAgent: String? = null
        var referrer: String? = null
        while (true) {
            val line = reader.readLine()?.trim()?.trimStart(BOM)?.trim() ?: break
            if (line.isEmpty()) continue
            if (!started) {
                if (!line.startsWith(EXTM3U, ignoreCase = true) && !line.startsWith(EXTINF, ignoreCase = true)) throw NotAPlaylist()
                started = true
            }
            when {
                line.startsWith(EXTM3U, ignoreCase = true) -> {
                    header = headerOf(Attributes.parse(line, EXTM3U.length).values)
                    onHeader(header)
                }
                line.startsWith(EXTINF, ignoreCase = true) -> info = Extinf.parse(line)
                line.startsWith(EXTGRP, ignoreCase = true) -> group = line.substring(EXTGRP.length).trim().ifEmpty { null }
                line.startsWith(EXTVLCOPT, ignoreCase = true) -> {
                    val option = line.substring(EXTVLCOPT.length).trim()
                    val key = option.substringBefore('=').trim().lowercase()
                    val value = option.substringAfter('=', "").trim().ifEmpty { null }
                    when (key) {
                        "http-user-agent" -> userAgent = value
                        "http-referrer", "http-referer" -> referrer = value
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    // Only full addresses play; a bare path means nothing outside its playlist.
                    if ("://" in line) onEntry(entryOf(info, line, group, userAgent, referrer, header))
                    info = null
                    group = null
                    userAgent = null
                    referrer = null
                }
            }
        }
        if (!started) throw NotAPlaylist()
    }

    private fun headerOf(values: Map<String, String>): M3uHeader = M3uHeader(
        guideUrls = listOf("url-tvg", "x-tvg-url", "tvg-url")
            .flatMap { values[it]?.split(',').orEmpty() }
            .map { it.trim() }
            .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            .distinct(),
        catchup = values["catchup"].orNullIfBlank() ?: values["catchup-type"].orNullIfBlank(),
        catchupSource = values["catchup-source"].orNullIfBlank(),
        catchupDays = (values["catchup-days"] ?: values["timeshift"]).positiveInt(),
    )

    private fun entryOf(info: Extinf?, url: String, group: String?, userAgent: String?, referrer: String?, header: M3uHeader): M3uEntry {
        val values = info?.values.orEmpty()
        val tvgName = values["tvg-name"].orNullIfBlank()
        return M3uEntry(
            name = info?.title.orNullIfBlank() ?: tvgName ?: nameOf(url),
            url = url,
            tvgId = values["tvg-id"].orNullIfBlank(),
            tvgName = tvgName,
            logo = (values["tvg-logo"] ?: values["logo"]).orNullIfBlank(),
            number = (values["tvg-chno"] ?: values["channel-number"] ?: values["tvg-num"]).positiveInt(),
            group = values["group-title"].orNullIfBlank() ?: group,
            catchup = (values["catchup"] ?: values["catchup-type"]).orNullIfBlank() ?: header.catchup,
            catchupDays = (values["catchup-days"] ?: values["timeshift"]).positiveInt() ?: header.catchupDays,
            catchupSource = values["catchup-source"].orNullIfBlank() ?: header.catchupSource,
            userAgent = userAgent ?: values["http-user-agent"].orNullIfBlank(),
            referrer = referrer ?: (values["http-referrer"] ?: values["http-referer"]).orNullIfBlank(),
            isRadio = values["radio"].equals("true", ignoreCase = true),
            kind = kindOf(url),
        )
    }

    /**
     * Movies and series are told apart by their address (brief 7.3): Xtream-style playlists put
     * them under `/movie/` and `/series/`, and others link the files themselves.
     */
    internal fun kindOf(url: String): M3uKind {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            "/series/" in path -> M3uKind.Series
            "/movie/" in path || "/movies/" in path -> M3uKind.Movie
            VOD_EXTENSIONS.any { path.endsWith(it) } -> M3uKind.Movie
            else -> M3uKind.Live
        }
    }

    /** A name for an entry without one: its address's last part. */
    private fun nameOf(url: String): String {
        val path = url.substringAfter("://").substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/').substringBeforeLast('.').ifEmpty { path.substringBefore('/') }
        // The String overload: the Charset one is newer than the Android versions Plasma supports.
        return runCatching { URLDecoder.decode(last, "UTF-8") }.getOrDefault(last)
    }

    /** Playlists are sometimes gzipped without saying so in the headers. */
    private fun unzipped(input: InputStream): InputStream {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input, BUFFER)
        buffered.mark(2)
        val magic = buffered.read() or (buffered.read() shl 8)
        buffered.reset()
        return if (magic == GZIPInputStream.GZIP_MAGIC) BufferedInputStream(GZIPInputStream(buffered, BUFFER), BUFFER) else buffered
    }

    private const val EXTM3U = "#EXTM3U"
    private const val EXTINF = "#EXTINF:"
    private const val EXTGRP = "#EXTGRP:"
    private const val EXTVLCOPT = "#EXTVLCOPT:"
    private const val BOM = '﻿'
    private const val BUFFER = 64 * 1024
    private val VOD_EXTENSIONS = listOf(".mp4", ".mkv", ".avi", ".mov", ".m4v", ".wmv", ".webm")
}

/** An `#EXTINF` line: its attributes and the title after them. */
internal class Extinf(val values: Map<String, String>, val title: String?) {
    companion object {
        fun parse(line: String): Extinf {
            // The duration comes first: -1 for live channels.
            var start = line.indexOf(':') + 1
            while (start < line.length && !line[start].isWhitespace() && line[start] != ',') start++
            val parsed = Attributes.parse(line, start)
            return Extinf(parsed.values, parsed.title)
        }
    }
}

/**
 * `key="value"` pairs, quoted or not, up to the first comma outside quotes; what follows the comma
 * is the title, commas and all.
 */
internal class Attributes(val values: Map<String, String>, val title: String?) {
    companion object {
        fun parse(text: String, from: Int): Attributes {
            val values = LinkedHashMap<String, String>()
            var i = from
            val n = text.length
            while (i < n) {
                val c = text[i]
                when {
                    c.isWhitespace() -> i++
                    c == ',' -> return Attributes(values, text.substring(i + 1).trim())
                    else -> {
                        val keyStart = i
                        while (i < n && text[i] != '=' && text[i] != ',' && !text[i].isWhitespace()) i++
                        val key = text.substring(keyStart, i).lowercase()
                        if (i < n && text[i] == '=') {
                            i++
                            val value: String
                            if (i < n && (text[i] == '"' || text[i] == '\'')) {
                                val quote = text[i]
                                val end = text.indexOf(quote, i + 1).let { if (it < 0) n else it }
                                value = text.substring(i + 1, end)
                                i = minOf(end + 1, n)
                            } else {
                                val valueStart = i
                                while (i < n && text[i] != ',' && !text[i].isWhitespace()) i++
                                value = text.substring(valueStart, i)
                            }
                            // The first of a repeated attribute wins, as in most players.
                            values.putIfAbsent(key, value.trim())
                        } else if (key.isNotEmpty()) {
                            values.putIfAbsent(key, "")
                        }
                    }
                }
            }
            return Attributes(values, null)
        }
    }
}

private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun String?.positiveInt(): Int? = this?.trim()?.toIntOrNull()?.takeIf { it > 0 }
