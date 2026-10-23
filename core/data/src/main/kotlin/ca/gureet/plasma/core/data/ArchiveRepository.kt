// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.source.m3u.Catchup
import ca.gureet.plasma.source.m3u.CatchupMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Where programs play from the provider's archive (catch-up and Start Over, brief 5.5): Xtream's
 * timeshift address (7.2), or a playlist channel's catch-up template (7.3).
 */
class ArchiveRepository(
    private val sources: SourceRepository,
    private val channels: ChannelRepository,
    private val guide: GuideRepository,
    private val clock: PlasmaClock,
) {
    /**
     * The address of [channelId]'s archive from [start], or null when the channel keeps no
     * archive, the archive no longer reaches back that far, or the playlist doesn't say how to
     * reach it. It runs to the end of the program on at [start], from the guide. Carries the
     * credentials.
     */
    suspend fun streamOf(channelId: String, start: Instant): ChannelStream? {
        val channel = channels.get(channelId) ?: return null
        val days = channel.catchupDays?.takeIf { it > 0 } ?: return null
        val now = clock.now()
        if (!start.isBefore(now) || start.isBefore(now.minus(Duration.ofDays(days.toLong())))) return null
        val source = sources.get(channel.sourceId) ?: return null
        val end = channel.epgKey
            ?.let { key -> guide.forChannel(key, start, start.plusSeconds(1)).firstOrNull()?.end }
            ?: start.plus(UNKNOWN_LENGTH)
        return when (source.type) {
            SourceType.Xtream -> {
                val minutes = Duration.between(start, end).plusSeconds(59).toMinutes().toInt()
                val url = sources.xtreamClient(source.id).timeshiftUrl(channel.key, start, minutes, zoneOf(source.serverTimeZone))
                ChannelStream(url, source.userAgent, referrer = null)
            }
            SourceType.M3u -> {
                val live = channels.streamOf(channelId) ?: return null
                val mode = Catchup.modeOf(live.catchupType) ?: when {
                    live.catchupSource != null -> CatchupMode.Default
                    XTREAM_LIVE.containsMatchIn(live.url) -> CatchupMode.Xc
                    else -> CatchupMode.Shift
                }
                // Playlist templates write their times in UTC, as IPTV Simple reads them, moved by the
                // source's Catch-up Time Offset for a server that reads them in its own time zone.
                val zone = ZoneOffset.ofTotalSeconds(source.playback.catchupOffsetMinutes.coerceIn(-MAX_OFFSET_MINUTES, MAX_OFFSET_MINUTES) * SECONDS_PER_MINUTE)
                val url = Catchup.url(mode, live.catchupSource, live.url, start, end, now, zone, programmeId = null) ?: return null
                ChannelStream(url, live.userAgent ?: source.userAgent, live.referrer)
            }
        }
    }

    private fun zoneOf(id: String?): ZoneId = id?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC

    private companion object {
        /** A program the guide doesn't know runs for an hour. */
        val UNKNOWN_LENGTH: Duration = Duration.ofHours(1)

        /** A zone offset reaches 18 hours either way. */
        const val MAX_OFFSET_MINUTES = 18 * 60
        const val SECONDS_PER_MINUTE = 60

        /** An Xtream-style live address, `…/user/password/1234.ts`, whose archive sits beside it. */
        val XTREAM_LIVE = Regex("""^https?://[^/]+/(?:live/)?[^/]+/[^/]+/\d+(\.ts|\.m3u8?)?$""")
    }
}
