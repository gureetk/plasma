// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.source.m3u

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** How a playlist channel's archive is addressed: its `catchup` attribute (brief 7.3). */
enum class CatchupMode { Default, Append, Shift, Flussonic, FlussonicHls, FlussonicTs, Xc, Vod }

/**
 * Builds the address of a past programme from a playlist channel's catch-up settings (brief 7.3),
 * as Kodi's IPTV Simple and TiviMate read them, so playlists written for those players work.
 */
object Catchup {
    fun modeOf(name: String?): CatchupMode? = when (name?.trim()?.lowercase()) {
        "default" -> CatchupMode.Default
        "append" -> CatchupMode.Append
        "shift", "timeshift" -> CatchupMode.Shift
        "flussonic", "fs" -> CatchupMode.Flussonic
        "flussonic-hls" -> CatchupMode.FlussonicHls
        "flussonic-ts" -> CatchupMode.FlussonicTs
        "xc" -> CatchupMode.Xc
        "vod" -> CatchupMode.Vod
        else -> null
    }

    /**
     * The address that plays [start] to [end] from the archive, or null when the channel's mode
     * can't make one from what the playlist gives. Times in the template are written in [zone].
     */
    fun url(
        mode: CatchupMode,
        source: String?,
        liveUrl: String,
        start: Instant,
        end: Instant,
        now: Instant,
        zone: ZoneId,
        programmeId: String? = null,
    ): String? {
        val template = when (mode) {
            CatchupMode.Default -> source?.takeIf { it.isNotBlank() } ?: return null
            CatchupMode.Append -> liveUrl + (source?.takeIf { it.isNotBlank() } ?: return null)
            CatchupMode.Shift -> liveUrl + (if ('?' in liveUrl) "&" else "?") + "utc={utc}&lutc={lutc}"
            CatchupMode.Flussonic, CatchupMode.FlussonicHls, CatchupMode.FlussonicTs -> flussonic(liveUrl, mode) ?: return null
            CatchupMode.Xc -> xtream(liveUrl) ?: return null
            CatchupMode.Vod -> source?.takeIf { it.isNotBlank() } ?: liveUrl
        }
        return fill(template, start, end, now, zone, programmeId)
    }

    /** Fills a template's placeholders: `{utc}`, `${start}`, `{Y}`, `{duration:60}`, `{utc:YmdHM}` and the rest. */
    internal fun fill(template: String, start: Instant, end: Instant, now: Instant, zone: ZoneId, programmeId: String?): String {
        val durationSeconds = (end.epochSecond - start.epochSecond).coerceAtLeast(0)
        val offsetSeconds = (now.epochSecond - start.epochSecond).coerceAtLeast(0)
        val local = start.atZone(zone)
        return PLACEHOLDER.replace(template) { match ->
            val name = match.groupValues[1].ifEmpty { match.groupValues[3] }
            val argument = match.groupValues[2].ifEmpty { match.groupValues[4] }.takeIf { it.isNotEmpty() }
            when (name) {
                "utc", "start" -> argument?.let { format(start, zone, it) } ?: start.epochSecond.toString()
                "utcend", "end" -> argument?.let { format(end, zone, it) } ?: end.epochSecond.toString()
                "lutc", "now", "timestamp" -> argument?.let { format(now, zone, it) } ?: now.epochSecond.toString()
                "Y" -> pad(local.year, 4)
                "m" -> pad(local.monthValue, 2)
                "d" -> pad(local.dayOfMonth, 2)
                "H" -> pad(local.hour, 2)
                "M" -> pad(local.minute, 2)
                "S" -> pad(local.second, 2)
                "duration" -> (durationSeconds / (argument?.toLongOrNull()?.takeIf { it > 0 } ?: 1)).toString()
                "offset" -> (offsetSeconds / (argument?.toLongOrNull()?.takeIf { it > 0 } ?: 1)).toString()
                "catchup-id" -> programmeId.orEmpty()
                else -> match.value
            }
        }
    }

    /** `{utc:YmdHM}`: Y, m, d, H, M and S stand for the parts of the time; anything else stays as it is. */
    private fun format(time: Instant, zone: ZoneId, pattern: String): String {
        val local = time.atZone(zone)
        return buildString {
            for (c in pattern) {
                when (c) {
                    'Y' -> append(pad(local.year, 4))
                    'm' -> append(pad(local.monthValue, 2))
                    'd' -> append(pad(local.dayOfMonth, 2))
                    'H' -> append(pad(local.hour, 2))
                    'M' -> append(pad(local.minute, 2))
                    'S' -> append(pad(local.second, 2))
                    else -> append(c)
                }
            }
        }
    }

    /**
     * Flussonic's archive beside the live address: `timeshift_abs-{start}.ts` for MPEG-TS, and
     * `…-timeshift_rel-{offset}.m3u8` for HLS, as IPTV Simple writes them.
     */
    private fun flussonic(liveUrl: String, mode: CatchupMode): String? {
        val match = FLUSSONIC.matchEntire(liveUrl) ?: return null
        val (host, channel, list, type, query) = match.destructured
        val ts = mode == CatchupMode.FlussonicTs || (mode == CatchupMode.Flussonic && type == "mpegts")
        return when {
            ts -> "$host/$channel/timeshift_abs-\${start}.ts$query"
            list == "index" -> "$host/$channel/timeshift_rel-{offset:1}.m3u8$query"
            else -> "$host/$channel/$list-timeshift_rel-{offset:1}.m3u8$query"
        }
    }

    /** An Xtream-style live address turned into its archive: `/timeshift/U/P/{minutes}/{start}/{id}.ts`. */
    private fun xtream(liveUrl: String): String? {
        val match = XTREAM.matchEntire(liveUrl) ?: return null
        val (host, user, password, id, extension) = match.destructured
        return "$host/timeshift/$user/$password/{duration:60}/{Y}-{m}-{d}:{H}-{M}/$id${extension.ifEmpty { ".ts" }}"
    }

    /** `{name}` or `{name:argument}`, and `${name}` or `${name:argument}`. */
    private val PLACEHOLDER = Regex("""\{([A-Za-z-]+)(?::([^}]*))?\}|\$\{([A-Za-z-]+)(?::([^}]*))?\}""")
    private val FLUSSONIC = Regex("""^(https?://[^/]+)/(.*)/([^/]*)(mpegts|\.m3u8)(\?.+=.+)?$""")
    private val XTREAM = Regex("""^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/([^/.]+)(\.m3u8?|\.ts)?$""")

    /** Digits in ASCII whatever the TV's language. */
    private fun pad(value: Int, width: Int): String = String.format(Locale.ROOT, "%0${width}d", value)
}
