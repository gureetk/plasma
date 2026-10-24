// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.nav

import ca.gureet.plasma.feature.search.TitleResult
import ca.gureet.plasma.feature.settings.SettingsPage
import kotlinx.serialization.Serializable

/** Plasma's destinations, one per screen in the brief (section 4). */
@Serializable data object HomeRoute

/** Search, optionally already searching for [query] (a name picked in Cast & Crew). */
@Serializable data class SearchRoute(val query: String = "")

@Serializable data object LiveTvRoute

@Serializable data object GuideRoute

@Serializable data object MoviesRoute

@Serializable data class MovieDetailRoute(val id: String)

@Serializable data object SeriesRoute

@Serializable data class SeriesDetailRoute(val id: String)

/** The page of a movie or series found by Search, whose id is its kind and its own id (`movie:home:7101`). */
fun titleRoute(result: TitleResult): Any {
    val id = result.id.substringAfter(':')
    return if (result.series) SeriesDetailRoute(id) else MovieDetailRoute(id)
}

@Serializable data object AppsRoute

@Serializable data object ProfilesRoute

@Serializable data object RecordingsRoute

/** Catch-up, on [channelId] when it keeps an archive (green on a channel in Live TV). */
@Serializable data class CatchUpRoute(val channelId: String? = null)

/**
 * Settings on one of its pages ([SettingsPage]'s name), with focus on the row [item] when there is
 * one; Playback for This Source is for the source [source], and a profile's page for [profile].
 * Each page opens over the one before it, so Back walks back up.
 */
@Serializable data class SettingsRoute(val page: String = SettingsPage.Main.name, val item: String? = null, val source: String? = null, val profile: String? = null)

/**
 * The player on [channelId], zapping through the list [listId] it was tuned from (brief 5.2): a
 * group's id, or "all", "favorites" or "recent". [from] plays a program from the archive (epoch
 * seconds). A locked channel asks for the PIN first.
 */
@Serializable data class PlayerRoute(val channelId: String, val listId: String? = null, val from: Long? = null)

@Serializable data object MultiviewRoute

/**
 * Playing a movie or an episode: [kind] "movie" or "episode", and its [id], from where it was left
 * or, with [fromStart], its start. Without them, the sample data's stand-in for [title].
 */
@Serializable data class VodPlayerRoute(val title: String, val kind: String? = null, val id: String? = null, val fromStart: Boolean = false)

/** Settings › Channels › Duplicate Numbers. */
@Serializable data object DuplicateNumbersRoute

/** Settings › Portals & Playlists › Add Portal or Playlist, or Edit on the source [editId] (brief 4.20). */
@Serializable data class AddSourceRoute(val editId: String? = null)

/** Settings › Launcher & Home › Use Plasma as Home Screen › Replace Google TV Home. */
@Serializable data object ReplaceHomeRoute

/** The PIN for a profile that has one, from Who's Watching. */
@Serializable data class ProfilePinRoute(val profileId: String)

/**
 * Choosing the parental PIN, twice to be sure (brief 5.12), or with [change] a new one. Then
 * [lockChannel] locks, and with [lockSettings] Settings is locked, for those that asked for it
 * first. With [profile], it's that profile's own PIN instead (5.11).
 */
@Serializable data class ChoosePinRoute(val change: Boolean = false, val lockChannel: String? = null, val lockSettings: Boolean = false, val profile: String? = null)

/** Unlocking [channelId] for good, from its options in Live TV: the PIN first (5.12). */
@Serializable data class UnlockChannelRoute(val channelId: String)

/** A screen that isn't built yet, titled [title]; [back] names where Back returns to. */
@Serializable data class PendingRoute(val title: String, val back: String? = null)

/** Settings › Remote & Buttons › Remote Key Test. */
@Serializable data object KeyTestRoute

/** Settings › Remote & Buttons › Learn a Key. */
@Serializable data object LearnKeyRoute
