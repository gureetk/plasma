// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.PlasmaClock
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The guide's fallback for now and next (brief 5.3): for a channel the full guide doesn't cover,
 * the next few programmes its Xtream server's `get_short_epg` gives. They're asked for when the
 * channel is looked at (Live TV's preview, the player) and kept in memory, asked for again after
 * half an hour. Other sources have no such call: their channels just have no guide.
 *
 * Its times are the server's Unix times, which are absolute, so a source's guide time correction,
 * meant for XMLTV, doesn't move them.
 */
class ShortGuide internal constructor(
    private val db: PlasmaDatabase,
    private val sources: SourceRepository,
    private val clock: PlasmaClock,
) {
    private val _programmes = MutableStateFlow<Map<String, List<Programme>>>(emptyMap())

    /** What's been fetched, by channel id, each in time order. */
    val programmes: StateFlow<Map<String, List<Programme>>> = _programmes.asStateFlow()

    /** When each channel was last asked for, in Unix ms. */
    private val asked = ConcurrentHashMap<String, Long>()

    /**
     * Asks [channel]'s server for its next few programmes, unless it was asked in the last half
     * hour, or in the last few minutes after a failure. A failure stays quiet: the channel just
     * has no guide yet.
     */
    suspend fun fetch(channel: Channel) {
        val now = clock.now().toEpochMilli()
        val last = asked[channel.id]
        if (last != null && now - last < FRESH_MS) return
        asked[channel.id] = now
        val source = db.sources().get(channel.sourceId) ?: return
        if (SourceType.of(source.type) != SourceType.Xtream) return
        val listings = try {
            sources.xtreamClient(source).shortEpg(channel.key, LIMIT)
        } catch (cancelled: CancellationException) {
            asked.remove(channel.id)
            throw cancelled
        } catch (failed: Exception) {
            // Asked again in a few minutes rather than half an hour.
            asked[channel.id] = now - FRESH_MS + RETRY_MS
            return
        }
        val key = KEY_PREFIX + channel.id
        val programmes = listings
            .filter { it.end.isAfter(it.start) && it.end.toEpochMilli() > now }
            .sortedBy { it.start }
            .map {
                Programme(
                    epgKey = key,
                    start = it.start,
                    end = it.end,
                    title = it.title,
                    subtitle = null,
                    description = it.description,
                    category = null,
                    icon = null,
                    season = null,
                    episode = null,
                    isNew = false,
                )
            }
        _programmes.update { it + (channel.id to programmes) }
    }

    companion object {
        /** What a fetched programme's [Programme.epgKey] starts with, before its channel's id. */
        const val KEY_PREFIX = "short:"

        /** How many programmes to ask for: the evening ahead, as far as the server goes. */
        const val LIMIT = 12

        private const val FRESH_MS = 30 * 60_000L
        private const val RETRY_MS = 5 * 60_000L
    }
}
