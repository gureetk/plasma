// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.search

import androidx.compose.ui.graphics.TransformOrigin
import ca.gureet.plasma.core.fixtures.Sample

/**
 * Search over the sample world (board 03, brief appendix A). Locked channels never show up in
 * results. The real search arrives with the guide database in M2.
 */
object SearchSamples {
    private fun result(
        program: Sample.Program,
        kind: ProgramResultKind,
        art: String? = program.channel.still,
        zoom: Float = 1f,
        pivot: TransformOrigin = TransformOrigin.Center,
    ) = ProgramResult(
        id = "${program.channel.id}@${program.start.toEpochSecond()}",
        title = program.title,
        kind = kind,
        channelId = program.channel.id,
        channelNumber = program.channel.number.toString(),
        channelName = program.channel.name,
        logo = program.channel.logo,
        art = art,
        start = program.start.toInstant(),
        end = program.end.toInstant(),
        artAlignment = program.channel.stillAlignment,
        artZoom = zoom,
        zoomPivot = pivot,
        recordingScheduled = program.recordingScheduled,
    )

    private fun on(channel: Sample.Channel, title: String) =
        (Sample.schedule[channel].orEmpty() + Sample.pastPrograms).first { it.channel == channel && it.title == title }

    /** What can be found: board 03's four "night" programs first, then others from the guide. */
    private val programs: List<ProgramResult> by lazy {
        with(Sample) {
            listOf(
                result(on(velocity, "Endurance 24: Night Stint"), ProgramResultKind.Live, art = velocity.still),
                result(pastPrograms.first { it.title == "Late Night Sessions" }, ProgramResultKind.CatchUp),
                result(on(arena1, "Fight Night: Main Card"), ProgramResultKind.Upcoming, art = null),
                result(on(arena1, "Tuesday Night Football"), ProgramResultKind.CatchUp, zoom = 2f, pivot = TransformOrigin(0.5f, 0.71f)),
                result(harbourCity, ProgramResultKind.Live),
                result(on(northline, "Business Wrap"), ProgramResultKind.Live),
                result(on(wildframe, "Kingdoms of Ice, Episode 3"), ProgramResultKind.Live),
                result(on(atlas, "The Quiz Hour"), ProgramResultKind.Live),
                result(on(atlas, "Baker's Dozen"), ProgramResultKind.Upcoming),
                result(on(arena1, "Match of the Week Extra"), ProgramResultKind.Upcoming),
                result(on(wildframe, "Kingdoms of Ice, Episode 2"), ProgramResultKind.CatchUp),
            )
        }
    }

    private val titles: List<TitleResult> by lazy {
        Sample.movies.map { TitleResult("movie:${it.id}", it.title, it.poster, series = false) } +
            Sample.series.map { TitleResult("series:${it.id}", it.title, it.poster, series = true) }
    }

    /** Maya's recent searches. */
    val recent = listOf("arena sports 1")

    fun search(query: String): SearchResults {
        if (query.isBlank()) return SearchResults()
        return SearchResults(
            programs = rankResults(programs, query, { it.title }, { it.kind == ProgramResultKind.Live }),
            titles = rankResults(titles, query, { it.title }),
            suggestions = completions(programs.map { it.title } + titles.map { it.title }, query),
        )
    }
}
