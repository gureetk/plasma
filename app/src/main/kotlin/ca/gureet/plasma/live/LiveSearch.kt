// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.Programme
import ca.gureet.plasma.core.data.ReminderRepository
import ca.gureet.plasma.feature.search.ChannelResult
import ca.gureet.plasma.feature.search.ProgramResult
import ca.gureet.plasma.feature.search.ProgramResultKind
import ca.gureet.plasma.feature.search.SearchResults
import ca.gureet.plasma.feature.search.TitleResult
import ca.gureet.plasma.feature.search.completions
import ca.gureet.plasma.feature.search.matchBand
import java.time.Duration
import java.time.Instant

/**
 * Search on the source's channels and guide (brief 4.3 and 5.10): the channels whose names match,
 * with a guide or not; programs on now, coming up in the next seven days, and in each channel's
 * catch-up window, found by title, subtitle or the channel's name. Hidden and locked channels stay
 * out. Then the sources' movies and series, by name.
 */
internal class LiveSearch(
    /** The guide's programs for a query, between two times: the search index (5.10). */
    private val programmes: suspend (query: String, from: Instant, to: Instant) -> List<Programme>,
    /** The movies and series whose names match a query. */
    private val titles: suspend (query: String) -> List<TitleResult> = { emptyList() },
) {
    suspend fun results(world: LiveWorld, query: String, now: Instant): SearchResults {
        if (query.isBlank()) return SearchResults()
        val channels = world.channels.filter { !it.hidden && !it.locked }
        // A guide channel's programs show once, on its first channel by number.
        val byGuide = channels.filter { it.epgKey != null }.groupBy { it.epgKey!! }.mapValues { (_, same) -> same.minBy { it.number } }
        // As far back as any channel's past plays: an archive's days, or local catch-up (5.5).
        val earliest = channels.mapNotNull { world.catchUpFrom(it) }.minOrNull() ?: now

        val found = LinkedHashMap<String, Found>()
        fun add(result: ProgramResult, band: Int) {
            val before = found[result.id]
            if (before == null || band < before.band) found[result.id] = Found(result, band)
        }
        for (programme in programmes(query, minOf(earliest, now), now.plus(AHEAD))) {
            val channel = byGuide[programme.epgKey] ?: continue
            val kind = kindOf(programme, channel, now, world) ?: continue
            // The title's match first, then the subtitle's; the index also finds the words in another order.
            val band = matchBand(programme.title, query) ?: programme.subtitle?.let { matchBand(it, query) }?.plus(SUBTITLE) ?: ANY_ORDER
            add(result(programme, channel, kind, world), band)
        }
        // A channel's name finds what's on it now.
        for (channel in channels) {
            val band = matchBand(channel.name, query) ?: continue
            val on = world.nowNext(channel).now ?: continue
            add(result(on, channel, ProgramResultKind.Live, world), band)
        }
        val ranked = found.values
            .sortedWith(compareBy<Found> { it.band }.thenBy { if (it.result.kind == ProgramResultKind.Live) 0 else 1 })
            .map { it.result }
            .take(SHOWN)
        val names = ranked.map { it.title } + channels.filter { matchBand(it.name, query) != null }.map { it.name }
        // A name that starts with the query first; the index also finds its words in another order.
        val movies = titles(query).sortedBy { matchBand(it.title, query) ?: ANY_ORDER }.take(SHOWN)
        // The channels themselves, best match first, then by number (the owner, 28 October).
        val named = channels.mapNotNull { channel -> matchBand(channel.name, query)?.let { channel to it } }
            .sortedWith(compareBy<Pair<Channel, Int>> { it.second }.thenBy { it.first.number })
            .take(SHOWN)
            .map { (channel, _) -> ChannelResult(channel.id, channel.number.toString(), channel.name, channel.logo, world.nowNext(channel).now?.title) }
        return SearchResults(channels = named, programs = ranked, titles = movies, suggestions = completions(names + movies.map { it.title }, query))
    }

    private class Found(val result: ProgramResult, val band: Int)

    private fun kindOf(programme: Programme, channel: Channel, now: Instant, world: LiveWorld): ProgramResultKind? = when {
        programme.start.isAfter(now) -> ProgramResultKind.Upcoming
        programme.end.isAfter(now) -> ProgramResultKind.Live
        // Over: only a channel whose archive, or local catch-up, still holds it can play it.
        world.catchUpFrom(channel)?.let { from -> !programme.start.isBefore(from) } == true -> ProgramResultKind.CatchUp
        else -> null
    }

    private fun result(programme: Programme, channel: Channel, kind: ProgramResultKind, world: LiveWorld): ProgramResult {
        val id = ReminderRepository.idOf(channel.id, programme.start)
        return ProgramResult(
            id = id,
            title = programme.title,
            kind = kind,
            channelId = channel.id,
            channelNumber = channel.number.toString(),
            channelName = channel.name,
            logo = channel.logo,
            art = programme.icon,
            start = programme.start,
            end = programme.end,
            reminder = id in world.reminderIds,
            recordingScheduled = id in world.recordingIds,
            seriesRule = world.hasSeriesRule(programme.title, channel.id),
        )
    }

    private companion object {
        /** How far ahead Search looks (brief 4.3). */
        val AHEAD: Duration = Duration.ofDays(7)
        const val SUBTITLE = 3
        const val ANY_ORDER = 6
        const val SHOWN = 50
    }
}
