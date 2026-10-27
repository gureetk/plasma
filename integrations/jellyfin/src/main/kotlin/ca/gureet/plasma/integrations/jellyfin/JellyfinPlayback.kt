// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.integrations.jellyfin

/** A file's picture, as its Jellyfin server probed it (brief 4.14). */
data class JellyfinVideo(
    val codec: String?,
    val profile: String?,
    val width: Int?,
    val height: Int?,
    val frameRate: Float?,
    /** "Dolby Vision", "HDR10+", "HDR10" or "HLG"; null for SDR or unknown. */
    val hdr: String?,
)

/** A file's main sound. */
data class JellyfinAudio(
    val codec: String?,
    val channels: Int?,
    /** ISO 639-2, as the file tags it: "eng". */
    val language: String?,
    /** Bits a second. */
    val bitRate: Int?,
)

/** A movie or an episode, as Plasma's player shows and plays it (brief 5.19). */
data class JellyfinTitle(
    val id: String,
    val kind: JellyfinKind,
    /** The movie, or the series an episode belongs to. */
    val title: String,
    val seriesId: String? = null,
    val episodeTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    /** A backdrop, or else its own picture, to show while the picture opens. */
    val art: String? = null,
    val durationMs: Long = 0,
    /** Where it was left. */
    val positionMs: Long = 0,
    val video: JellyfinVideo? = null,
    val audio: JellyfinAudio? = null,
    /** Which of the item's files plays: its first media source. */
    val mediaSourceId: String? = null,
)
