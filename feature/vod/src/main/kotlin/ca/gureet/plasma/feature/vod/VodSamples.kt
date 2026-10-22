// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.vod

import androidx.compose.ui.graphics.Color
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.component.CountedItem
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.fixtures.fx

/** Movies, Series and their detail pages as boards 13–16 draw them, from the sample data (appendix A). */
object VodSamples {
    // ---- Movies ---------------------------------------------------------------------------------

    /** Ratings for the Rating sort; the boards don't show them. */
    private val movieRatings = mapOf(
        "neon" to 7.1f, "lighthouse" to 8.2f, "orchard" to 7.6f, "ember" to 6.8f, "loworbit" to 7.9f, "salt" to 7.4f,
        "carto" to 7.7f, "velvet" to 6.9f, "polar" to 7.3f, "paper" to 7.0f, "nightdesk" to 7.8f, "kite" to 7.2f,
    )

    /** Part watched, for the Continue Watching category and the detail page. */
    private val movieProgress = mapOf("lighthouse" to 0.54f, "kite" to 0.38f, "carto" to 0.12f)

    private fun movieTitle(id: String): VodTitle {
        val movie = Sample.movies.first { it.id == id }
        val newest = Sample.recentlyAdded.indexOfFirst { it.id == id }
        return VodTitle(
            id = movie.id,
            title = movie.title,
            poster = movie.poster,
            caption = VodCaption.Movie(movie.year, movie.genre, movie.minutes),
            year = movie.year,
            rating = movieRatings[id] ?: 0f,
            added = (Sample.recentlyAdded.size - newest).toLong(),
        )
    }

    /** Which sample movies each category lists (the counts are the library's; only these have art). */
    private val movieLists = mapOf(
        "recent" to Sample.recentlyAdded.map { it.id },
        "continue" to listOf("lighthouse", "kite", "carto"),
        "mylist" to listOf("salt", "polar", "carto", "velvet", "orchard", "paper"),
        "uhd" to listOf("neon", "lighthouse", "orchard", "ember", "loworbit", "polar"),
        "action" to listOf("ember", "neon", "loworbit"),
        "animation" to listOf("kite", "paper"),
        "comedy" to listOf("kite", "velvet"),
        "documentary" to listOf("salt", "polar"),
        "drama" to listOf("lighthouse", "orchard", "salt", "nightdesk"),
        "family" to listOf("kite", "paper"),
        "scifi" to listOf("loworbit", "neon"),
        "thriller" to listOf("neon", "polar", "nightdesk"),
        "romance" to listOf("velvet", "salt"),
        "mystery" to listOf("lighthouse", "carto"),
        "crime" to listOf("nightdesk"),
    )

    /** Movies, Recently Added, with The Last Lighthouse focused (board 13). */
    val movies: VodBrowseUiState
        get() = VodBrowseUiState(
            kind = VodKind.Movies,
            categories = Sample.movieCategories.map { CountedItem(it.id, it.name, it.count, it.smart) },
            selectedCategoryId = "recent",
            titles = movieLists.mapValues { (category, ids) ->
                ids.map { id -> movieTitle(id).let { if (category == "continue") it.copy(progress = movieProgress[id]) else it } }
            },
            profileName = Sample.maya.name,
            profileInitial = Sample.maya.initial,
            focusedId = "lighthouse",
        )

    private val synopses = mapOf(
        "neon" to "A night-shift paramedic in a flooded city follows a run of impossible calls to the one address that keeps phoning in.",
        "orchard" to "Three sisters come back to the family greenhouse the summer it's sold, and find their mother kept more than plants under the glass.",
        "ember" to "Eight hundred miles, one tank of gas and a borrowed truck: a getaway driver takes a last job across the desert.",
        "loworbit" to "When a repair goes wrong, two astronauts have ninety minutes, one orbit, to find a way home.",
        "paper" to "A young mapmaker draws a kingdom into being, then has to find a way to live in it.",
        "salt" to "A fisherman's widow takes his boat out alone, into the weather that took him.",
        "polar" to "Sixty days without a sunrise. At an Arctic research base, the crew starts counting one person too many.",
        "velvet" to "A singer and a milliner share one evening in 1950s Paris, and the city conspires to keep it going.",
        "nightdesk" to "A reporter on the overnight shift takes a call that turns the morning's front page upside down.",
        "carto" to "An aging surveyor sets out to chart the one valley that has never appeared on a map: the one he grew up in.",
        "kite" to "Two brothers build the biggest kite their town has ever seen, for the festival their father never missed.",
    )

    private val castPool = listOf(
        Person("Mara Ellison", "Nora Hale"), Person("Aden Foley", "Tom Ashby"), Person("June Okafor", "Ruth Penhale"),
        Person("Callum Reyes", "Eli"), Person("Priya Lund", "Dr. Sana Iyer"), Person("Theo Marchetti", "Luca"),
        Person("Hana Sato", "Mika"), Person("Owen Blake", "Harlan"),
    )
    private val directors = listOf("Ines Calloway", "Ravi Okonjo", "Elsa Brandt", "Tomás Weir")

    /** The Last Lighthouse in full (board 14); the other movies from their sample data, with poster backdrops. */
    fun movieDetail(id: String): MovieDetailUiState? {
        val movie = Sample.movies.firstOrNull { it.id == id } ?: return null
        val leftMinutes = movieProgress[id]?.let { (movie.minutes * (1f - it)).toInt() }
        if (id == "lighthouse") {
            val lighthouse = Sample.LastLighthouse
            return MovieDetailUiState(
                id = id,
                title = movie.title,
                titleArt = lighthouse.TITLE_ART,
                backdrop = Backdrop(lighthouse.BACKDROP, Color(lighthouse.PAGE_COLOR), shiftXPx = 60, shiftYPx = -30),
                genres = lighthouse.genres,
                year = movie.year,
                minutes = movie.minutes,
                badges = lighthouse.badges,
                synopsis = lighthouse.SYNOPSIS,
                cast = castPool.take(3),
                director = lighthouse.DIRECTOR,
                leftMinutes = lighthouse.RESUME_LEFT_MINUTES,
                moreLikeThis = lighthouse.moreLikeThis.map(::movieTitle),
                audio = listOf(
                    "English · 5.1 · Dolby Digital Plus",
                    "English · Audio description · Stereo",
                    "French · 5.1 · Dolby Digital Plus",
                ),
                subtitles = listOf("English (CC)", "English", "French", "Spanish"),
                playsWith = "Plays with the Android player · Dolby Vision",
                fileInfo = listOf(
                    "Container" to "Matroska (MKV)",
                    "Video" to "HEVC Main 10 · 3840 × 2160 · 23.976 fps",
                    "HDR" to "Dolby Vision profile 8.1, HDR10 compatible",
                    "Audio" to "E-AC-3 5.1 · 640 kb/s",
                    "Size" to "21.6 GB",
                ),
            )
        }
        val index = Sample.movies.indexOf(movie)
        val uhd = id in movieLists.getValue("uhd")
        return MovieDetailUiState(
            id = id,
            title = movie.title,
            titleArt = null,
            backdrop = Backdrop(movie.poster, PlasmaColors.Bg, blurred = true),
            genres = listOf(movie.genre),
            year = movie.year,
            minutes = movie.minutes,
            badges = listOfNotNull("13+", if (uhd) "4K" else "HD", if (uhd) "HDR" else null, "5.1"),
            synopsis = synopses[id].orEmpty(),
            cast = List(3) { castPool[(index + it * 3) % castPool.size] },
            director = directors[index % directors.size],
            leftMinutes = leftMinutes,
            moreLikeThis = Sample.recentlyAdded.map { it.id }.filter { it != id }.map(::movieTitle),
            audio = listOf("English · 5.1 · Dolby Digital Plus", "English · Stereo · AAC"),
            subtitles = listOf("English (CC)", "Spanish"),
            playsWith = if (uhd) "Plays with the Android player · HDR10" else "Plays with VLC",
            fileInfo = listOf(
                "Container" to "Matroska (MKV)",
                "Video" to if (uhd) "HEVC Main 10 · 3840 × 2160 · 24 fps" else "H.264 High · 1920 × 1080 · 24 fps",
                "Audio" to "E-AC-3 5.1 · 640 kb/s",
            ),
        )
    }

    // ---- Series ---------------------------------------------------------------------------------

    /** What the boards don't show about each series: its years, seasons, genre, rating and synopsis. */
    private class SeriesFacts(val years: String, val seasons: Int, val genre: String, val rating: Float, val synopsis: String)

    private val seriesFacts = mapOf(
        "atlas" to SeriesFacts("2024–2026", 3, "Sci-Fi", 8.4f, Sample.AtlasStation.SYNOPSIS),
        "kingsway" to SeriesFacts("2025–2026", 2, "Documentary", 7.9f, "A season inside Harbour City's dressing room, from the first day of training to the last day of the window."),
        "harbour" to SeriesFacts("2023–2026", 3, "Crime", 7.7f, "A night-shift detective works the docks of a city that never quite sleeps."),
        "panel" to SeriesFacts("2022–2026", 5, "Comedy", 7.2f, "Four comedians, one desk and the week's news, every Friday."),
        "northof" to SeriesFacts("2026", 1, "Thriller", 7.5f, "A ranger posted to the edge of the map finds the town she was sent to protect doesn't want to be found."),
        "lastorders" to SeriesFacts("2024–2026", 2, "Comedy", 7.4f, "A comedy in six rounds at the last bar on Main Street."),
        "nightstint" to SeriesFacts("2025", 1, "Documentary", 8.0f, "The drivers, engineers and pit crews who race through the night at the Endurance 24."),
        "smallclaims" to SeriesFacts("2021–2025", 4, "Comedy", 7.0f, "Court is in session: a small-town judge hears the disputes nobody else will."),
        "backstage" to SeriesFacts("2026", 1, "Documentary", 7.3f, "A season behind the curtain of a touring rock show."),
        "heatindex" to SeriesFacts("2024–2026", 3, "Reality", 6.9f, "Twelve cooks, one kitchen and a summer that keeps getting hotter."),
        "quiethours" to SeriesFacts("2025", 1, "Drama", 7.8f, "A drama in eight parts about a hotel night porter and the guests who can't sleep."),
        "tumble" to SeriesFacts("2023–2026", 3, "Kids", 7.1f, "The loudest little town in the valley, and the kids who run it."),
    )

    private fun seriesTitle(id: String): VodTitle {
        val series = Sample.series.first { it.id == id }
        val facts = seriesFacts.getValue(id)
        return VodTitle(
            id = id,
            title = series.title,
            poster = series.poster,
            // Atlas Station is part-watched: its caption offers the episode to resume (board 15).
            caption = if (id == "atlas") VodCaption.ResumeEpisode(2, 4, 18) else VodCaption.Series(facts.years, facts.seasons),
            badge = series.badge,
            progress = series.progress,
            year = facts.years.takeLast(4).toInt(),
            rating = facts.rating,
            added = (Sample.series.size - Sample.series.indexOf(series)).toLong(),
        )
    }

    private val seriesLists = mapOf(
        "new" to Sample.series.map { it.id },
        "continue" to listOf("atlas", "nightstint", "lastorders", "smallclaims"),
        "mylist" to listOf("atlas", "panel", "quiethours", "heatindex"),
        "returning" to listOf("harbour", "smallclaims", "backstage"),
        "comedy" to listOf("lastorders", "smallclaims", "tumble", "panel"),
        "crime" to listOf("harbour", "smallclaims"),
        "documentary" to listOf("nightstint", "backstage", "kingsway"),
        "drama" to listOf("atlas", "harbour", "northof", "quiethours"),
        "kids" to listOf("tumble"),
        "reality" to listOf("heatindex", "backstage", "panel"),
        "scifi" to listOf("atlas"),
        "thriller" to listOf("northof", "harbour"),
        "animation" to listOf("tumble"),
    )

    /** Series, New Episodes, with Atlas Station focused (board 15). */
    val series: VodBrowseUiState
        get() = VodBrowseUiState(
            kind = VodKind.Series,
            categories = Sample.seriesCategories.map { CountedItem(it.id, it.name, it.count, it.smart) },
            selectedCategoryId = "new",
            titles = seriesLists.mapValues { (_, ids) -> ids.map(::seriesTitle) },
            profileName = Sample.maya.name,
            profileInitial = Sample.maya.initial,
            focusedId = "atlas",
        )

    private fun still(n: Int) = fx("ep_atlas_s2e$n")

    /** Season 1 watched, season 2 as board 16 has it (E4 part-watched), season 3 new. */
    private val atlasSeasons: List<SeasonItem>
        get() = listOf(
            SeasonItem(
                1,
                listOf("Launch Window", "Station Keeping", "Ballast", "Cold Start", "Night Shift", "Debris Field", "Terminator Line", "Blackout")
                    .mapIndexed { i, title -> EpisodeItem(i + 1, title, still(listOf(5, 7, 2, 8, 6, 1, 3, 4)[i]), 48 + (i * 5) % 11, watched = true) },
            ),
            SeasonItem(
                2,
                Sample.AtlasStation.season2.map { EpisodeItem(it.number, it.title, it.still, it.minutes, it.watched, it.leftMinutes) },
            ),
            SeasonItem(
                3,
                listOf("Aphelion", "Drift", "Low Light").mapIndexed { i, title -> EpisodeItem(i + 1, title, still(listOf(6, 8, 1)[i]), 52 + i * 3) },
            ),
        )

    private val episodeTitles = listOf("Pilot", "Second Chances", "The Long Way Round", "Open Water", "Crossing Lines", "Homecoming")
    private val episodeArt = listOf("stadium2", "city2", "concert2", "aurora2", "motor2", "orbit2")

    /** Atlas Station in full (board 16); the other series from their sample data. */
    fun seriesDetail(id: String): SeriesDetailUiState? {
        val series = Sample.series.firstOrNull { it.id == id } ?: return null
        val facts = seriesFacts.getValue(id)
        if (id == "atlas") {
            val atlas = Sample.AtlasStation
            return SeriesDetailUiState(
                id = id,
                title = series.title,
                titleArt = atlas.TITLE_ART,
                backdrop = Backdrop(atlas.BACKDROP, Color(atlas.PAGE_COLOR), shiftXPx = 180, shiftYPx = -150),
                genres = atlas.genres,
                years = atlas.YEARS,
                badges = atlas.badges,
                synopsis = atlas.SYNOPSIS,
                seasons = atlasSeasons,
            )
        }
        val offset = Sample.series.indexOf(series)
        return SeriesDetailUiState(
            id = id,
            title = series.title,
            titleArt = null,
            backdrop = Backdrop(series.poster, PlasmaColors.Bg, blurred = true),
            genres = listOf(facts.genre),
            years = facts.years,
            badges = listOf("13+", "HD", "5.1"),
            synopsis = facts.synopsis,
            // Earlier seasons watched; the latest one not started.
            seasons = (1..facts.seasons).map { number ->
                SeasonItem(
                    number,
                    episodeTitles.mapIndexed { i, title ->
                        EpisodeItem(i + 1, title, fx(episodeArt[(i + offset) % episodeArt.size]), 44 + (i * 7) % 15, watched = number < facts.seasons)
                    },
                )
            },
        )
    }
}
