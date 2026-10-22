// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.nav

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

@Serializable data object SettingsRoute

@Serializable data class PlayerRoute(val channelId: String)

@Serializable data object MultiviewRoute

/** Playing a movie or an episode; the VOD player arrives with the engines (M1, M4). */
@Serializable data class VodPlayerRoute(val title: String)
