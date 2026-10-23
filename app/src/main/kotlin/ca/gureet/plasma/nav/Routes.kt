// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.nav

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

@Serializable data object AppsRoute

@Serializable data object ProfilesRoute

@Serializable data object RecordingsRoute

/** Catch-up, on [channelId] when it keeps an archive (green on a channel in Live TV). */
@Serializable data class CatchUpRoute(val channelId: String? = null)

/**
 * Settings on one of its pages ([SettingsPage]'s name), with focus on the row [item] when there is
 * one. Each page opens over the one before it, so Back walks back up.
 */
@Serializable data class SettingsRoute(val page: String = SettingsPage.Main.name, val item: String? = null)

/**
 * Live TV on [channelId], zapping through the list [listId] it was tuned from (brief 5.2): a group's
 * id, or "all", "favorites" or "recent". A locked channel asks for the PIN first.
 */
/** The player on a channel, zapping through a list; [from] plays a program from the archive (epoch seconds). */
@Serializable data class PlayerRoute(val channelId: String, val listId: String? = null, val from: Long? = null)

@Serializable data object MultiviewRoute

/** Playing a movie or an episode; the VOD player arrives with the engines (M1, M4). */
@Serializable data class VodPlayerRoute(val title: String)

/** Settings › Channels › Duplicate Numbers. */
@Serializable data object DuplicateNumbersRoute

/** Settings › Portals & Playlists › Add Portal or Playlist. */
@Serializable data object AddSourceRoute

/** Settings › Launcher & Home › Use Plasma as Home Screen › Replace Google TV Home. */
@Serializable data object ReplaceHomeRoute

/** The PIN for a profile that has one, from Who's Watching. */
@Serializable data class ProfilePinRoute(val profileId: String)

/** A screen that isn't built yet, titled [title]; [back] names where Back returns to. */
@Serializable data class PendingRoute(val title: String, val back: String? = null)

/** Settings › Remote & Buttons › Remote Key Test. */
@Serializable data object KeyTestRoute

/** Settings › Remote & Buttons › Learn a Key. */
@Serializable data object LearnKeyRoute
