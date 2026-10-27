// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.epg

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream

/** A guide's `<channel>`: guides are matched to channels by [id], or by a display name. */
data class XmltvChannel(val id: String, val names: List<String>, val icon: String?)

/** A guide's `<programme>`. Times are Unix milliseconds in UTC (brief 5.3). */
data class XmltvProgramme(
    val channelId: String,
    val start: Long,
    val end: Long,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val category: String? = null,
    val icon: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val isNew: Boolean = false,
    /** Its age rating's value, as written: "TV-MA", "PG-13", "16" (brief 5.12). */
    val rating: String? = null,
)

/**
 * Reads an XMLTV guide as a stream, plain or gzipped, without holding it in memory: full
 * provider guides run to hundreds of megabytes (brief 6.7). Ported from iptv-player.
 *
 * @param notBefore programmes that end before this (Unix ms) are skipped, so a guide's old days
 *   never reach the database.
 * @param offsetMillis the source's EPG time correction (brief 5.3), added to every time.
 */
class XmltvReader(
    private val notBefore: Long = Long.MIN_VALUE,
    private val offsetMillis: Long = 0,
) {
    fun read(input: InputStream, onChannel: (XmltvChannel) -> Unit = {}, onProgramme: (XmltvProgramme) -> Unit) {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(unpack(input), null)

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "channel" -> readChannel(parser)?.let(onChannel)
                "programme" -> readProgramme(parser)?.let { if (it.end >= notBefore) onProgramme(it) }
            }
        }
    }

    private fun readChannel(parser: XmlPullParser): XmltvChannel? {
        val id = parser.getAttributeValue(null, "id")?.trim()
        val names = mutableListOf<String>()
        var icon: String? = null
        forEachChild(parser) { name ->
            when (name) {
                "display-name" -> parser.nextText().trim().takeIf { it.isNotEmpty() }?.let(names::add)
                "icon" -> {
                    icon = icon ?: parser.getAttributeValue(null, "src")?.trim()?.takeIf { it.isNotEmpty() }
                    skip(parser)
                }
                else -> skip(parser)
            }
        }
        return id?.takeIf { it.isNotEmpty() }?.let { XmltvChannel(it, names, icon) }
    }

    private fun readProgramme(parser: XmlPullParser): XmltvProgramme? {
        val channel = parser.getAttributeValue(null, "channel")?.trim()
        val start = parser.getAttributeValue(null, "start")?.let(::parseTime)?.plus(offsetMillis)
        val stop = parser.getAttributeValue(null, "stop")?.let(::parseTime)?.plus(offsetMillis)
        var title: String? = null
        var subtitle: String? = null
        var description: String? = null
        var category: String? = null
        var icon: String? = null
        var episode: Pair<Int?, Int?>? = null
        var isNew = false
        var rating: String? = null
        forEachChild(parser) { name ->
            when (name) {
                // The first of each wins; later ones are usually translations.
                "title" -> parser.nextText().trim().let { if (title == null && it.isNotEmpty()) title = it }
                "sub-title" -> parser.nextText().trim().let { if (subtitle == null && it.isNotEmpty()) subtitle = it }
                "desc" -> parser.nextText().trim().let { if (description == null && it.isNotEmpty()) description = it }
                "category" -> parser.nextText().trim().let { if (category == null && it.isNotEmpty()) category = it }
                "icon" -> {
                    icon = icon ?: parser.getAttributeValue(null, "src")?.trim()?.takeIf { it.isNotEmpty() }
                    skip(parser)
                }
                "episode-num" -> {
                    val system = parser.getAttributeValue(null, "system")
                    val parsed = Episodes.parse(system, parser.nextText())
                    // xmltv_ns is the standard; take "onscreen" only when nothing better came first.
                    if (parsed != null && (episode == null || system == "xmltv_ns")) episode = parsed
                }
                "new" -> {
                    isNew = true
                    skip(parser)
                }
                // <rating system="VCHIP"><value>TV-MA</value></rating>; the first one counts.
                "rating" -> {
                    var value: String? = null
                    forEachChild(parser) { child ->
                        if (child == "value") parser.nextText().trim().let { if (value == null && it.isNotEmpty()) value = it } else skip(parser)
                    }
                    if (rating == null) rating = value
                }
                else -> skip(parser)
            }
        }
        if (channel.isNullOrEmpty() || start == null) return null
        return XmltvProgramme(
            channelId = channel,
            start = start,
            // A missing stop time is filled in later from the next programme; an hour until then.
            end = stop?.takeIf { it > start } ?: (start + HOUR),
            title = title.orEmpty(),
            subtitle = subtitle,
            description = description,
            category = category,
            icon = icon,
            season = episode?.first,
            episode = episode?.second,
            isNew = isNew,
            rating = rating,
        )
    }

    /** Calls [block] at each direct child's start tag; [block] must read the whole element. */
    private inline fun forEachChild(parser: XmlPullParser, block: (String) -> Unit) {
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) return
            if (event == XmlPullParser.END_TAG && parser.depth == depth) return
            if (event == XmlPullParser.START_TAG && parser.depth == depth + 1) block(parser.name)
        }
    }

    /** Skips the element whose start tag the parser is on, including its end tag. */
    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    companion object {
        private const val HOUR = 3_600_000L
        private val TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

        /**
         * Unix ms from an XMLTV time: "20261021194500 +0000". The offset, and the seconds, may be
         * missing; without an offset the time is taken as UTC.
         */
        fun parseTime(value: String): Long? {
            val trimmed = value.trim()
            val digits = trimmed.takeWhile { it.isDigit() }
            val local = when (digits.length) {
                14 -> digits
                12 -> digits + "00"
                else -> return null
            }
            val offsetText = trimmed.drop(digits.length).trim()
            val offset = if (offsetText.length == 5 && (offsetText[0] == '+' || offsetText[0] == '-')) {
                val sign = if (offsetText[0] == '-') -1 else 1
                val hours = offsetText.substring(1, 3).toIntOrNull() ?: return null
                val minutes = offsetText.substring(3, 5).toIntOrNull() ?: return null
                ZoneOffset.ofTotalSeconds(sign * (hours * 3600 + minutes * 60))
            } else {
                ZoneOffset.UTC
            }
            return runCatching { LocalDateTime.parse(local, TIME).toInstant(offset).toEpochMilli() }.getOrNull()
        }

        /** Guides are often gzipped, sometimes without saying so in the headers. */
        fun unpack(input: InputStream): InputStream {
            val buffered = BufferedInputStream(input, 64 * 1024)
            buffered.mark(2)
            val magic = buffered.read() or (buffered.read() shl 8)
            buffered.reset()
            return if (magic == GZIPInputStream.GZIP_MAGIC) BufferedInputStream(GZIPInputStream(buffered, 64 * 1024), 64 * 1024) else buffered
        }
    }
}

/** Season and episode numbers from `<episode-num>`. */
internal object Episodes {
    private val ONSCREEN = Regex("""(?i)S(\d{1,4})\s*E(\d{1,5})""")

    /** (season, episode), counting from 1, or null when [text] says neither. */
    fun parse(system: String?, text: String): Pair<Int?, Int?>? = when (system) {
        // "1.4.0/1": zero-based season, episode and part, each optionally "n/total".
        "xmltv_ns" -> {
            val parts = text.split('.')
            val season = parts.getOrNull(0)?.substringBefore('/')?.trim()?.toIntOrNull()?.plus(1)
            val episode = parts.getOrNull(1)?.substringBefore('/')?.trim()?.toIntOrNull()?.plus(1)
            if (season == null && episode == null) null else season to episode
        }
        "onscreen", null -> ONSCREEN.find(text)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        else -> null
    }
}
