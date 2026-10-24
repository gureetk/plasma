// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingKind
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Where a recording goes in the network folder (brief 5.6, Name Files for Jellyfin), and what goes
 * with it: [media] and the extras, each a path in the folder (parts separated by "/"). Extras
 * marked [Extra.onlyIfMissing] belong to the show or the film, and are written once.
 */
data class CopyPlan(val media: String, val extras: List<Extra> = emptyList()) {
    sealed interface Extra {
        val path: String
        val onlyIfMissing: Boolean

        /** An info file Jellyfin reads. */
        data class Text(override val path: String, val text: String, override val onlyIfMissing: Boolean = false) : Extra

        /** Artwork from the guide, fetched from [url]. */
        data class Image(override val path: String, val url: String, override val onlyIfMissing: Boolean = false) : Extra
    }
}

/**
 * Jellyfin's naming (brief 5.6 and appendix D): a series' episodes in its own folder, by season,
 * with `.nfo` info files and artwork; a film in a folder of its own; the rest under its channel.
 * Characters a Windows share refuses become a space or a dash.
 */
object JellyfinNames {
    const val TV = "TV Recordings"
    const val MOVIES = "Movie Recordings"
    const val OTHER = "Other Recordings"

    /**
     * Where [recording] goes, with Name Files for Jellyfin on ([jellyfin]) or off. A series without
     * episode numbers counts its episodes up within the air year: [episodesIn] gives the episode
     * numbers a season folder has already.
     */
    fun plan(recording: Recording, jellyfin: Boolean, zone: ZoneId, episodesIn: (folder: String) -> List<Int> = { emptyList() }): CopyPlan {
        if (!jellyfin) return CopyPlan(RecordingFiles.nameOf(recording, zone))
        return when {
            recording.kind == RecordingKind.Movie -> movie(recording)
            recording.season != null && recording.episode != null -> episode(recording, zone, recording.season!!, recording.episode!!)
            recording.kind == RecordingKind.Series || recording.ruleId != null -> {
                // No numbers in the guide: the air year as the season, and the next episode in it.
                val year = recording.start.atZone(zone).year
                val folder = "$TV/${clean(recording.title)}/Season $year"
                val next = (episodesIn(folder).maxOrNull() ?: 0) + 1
                episode(recording, zone, year, next)
            }
            else -> other(recording, zone)
        }
    }

    private fun episode(recording: Recording, zone: ZoneId, season: Int, episode: Int): CopyPlan {
        val show = clean(recording.title)
        val numbers = if (season >= YEAR_SEASONS) "S${season}E${"%03d".format(episode)}" else "S${"%02d".format(season)}E${"%02d".format(episode)}"
        val seasonFolder = if (season >= YEAR_SEASONS) "Season $season" else "Season ${"%02d".format(season)}"
        val base = "$TV/$show/$seasonFolder/$show $numbers"
        return CopyPlan(
            media = "$base.ts",
            extras = listOfNotNull(
                CopyPlan.Extra.Text("$TV/$show/tvshow.nfo", tvShowNfo(recording), onlyIfMissing = true),
                recording.image?.let { CopyPlan.Extra.Image("$TV/$show/poster.jpg", it, onlyIfMissing = true) },
                CopyPlan.Extra.Text("$base.nfo", episodeNfo(recording, zone, season, episode)),
                recording.image?.let { CopyPlan.Extra.Image("$base-thumb.jpg", it) },
            ),
        )
    }

    private fun movie(recording: Recording): CopyPlan {
        val name = clean(recording.title)
        return CopyPlan(
            media = "$MOVIES/$name/$name.ts",
            extras = listOfNotNull(
                CopyPlan.Extra.Text("$MOVIES/$name/movie.nfo", movieNfo(recording)),
                recording.image?.let { CopyPlan.Extra.Image("$MOVIES/$name/poster.jpg", it) },
            ),
        )
    }

    private fun other(recording: Recording, zone: ZoneId): CopyPlan {
        val start = STAMP.format(recording.start.atZone(zone))
        return CopyPlan("$OTHER/${clean(recording.channelName)}/${clean("$start ${recording.title}")}.ts")
    }

    private fun tvShowNfo(recording: Recording): String = xml("tvshow") {
        tag("title", recording.title)
    }

    private fun episodeNfo(recording: Recording, zone: ZoneId, season: Int, episode: Int): String = xml("episodedetails") {
        tag("title", recording.subtitle?.takeIf { it.isNotBlank() } ?: recording.title)
        tag("showtitle", recording.title)
        tag("season", season.toString())
        tag("episode", episode.toString())
        tag("aired", DAY.format(recording.start.atZone(zone)))
        recording.description?.let { tag("plot", it) }
        tag("studio", recording.channelName)
    }

    private fun movieNfo(recording: Recording): String = xml("movie") {
        tag("title", recording.title)
        recording.description?.let { tag("plot", it) }
        recording.category?.let { tag("genre", it) }
    }

    private fun xml(root: String, body: StringBuilder.() -> Unit): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        append("<").append(root).append(">\n")
        body()
        append("</").append(root).append(">\n")
    }

    private fun StringBuilder.tag(name: String, value: String) {
        append("  <").append(name).append(">").append(escape(value)).append("</").append(name).append(">\n")
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** A name a Windows share takes, as recordings' own file names are (brief 5.6). */
    private fun clean(name: String): String = RecordingFiles.clean(name).ifBlank { "Untitled" }

    /** Seasons numbered by year, as a series without numbers has: "Season 2026", "S2026E001". */
    private const val YEAR_SEASONS = 1000
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm")
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
}
