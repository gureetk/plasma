// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.model.ShiftedClock
import ca.gureet.plasma.feature.home.DockTarget
import ca.gureet.plasma.feature.home.HomeActions
import ca.gureet.plasma.feature.home.HomeScreen
import ca.gureet.plasma.feature.home.HeroKind
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.fixtures.SampleHost
import ca.gureet.plasma.feature.guide.GuideActions
import ca.gureet.plasma.feature.guide.GuideSamples
import ca.gureet.plasma.feature.guide.GuideScreen
import ca.gureet.plasma.feature.guide.ProgramAction
import ca.gureet.plasma.feature.home.HomeSamples
import ca.gureet.plasma.feature.livetv.LiveTvActions
import ca.gureet.plasma.feature.livetv.LiveTvSamples
import ca.gureet.plasma.feature.livetv.LiveTvScreen
import ca.gureet.plasma.feature.player.PlayerActions
import ca.gureet.plasma.feature.player.PlayerSamples
import ca.gureet.plasma.feature.player.PlayerScreen
import ca.gureet.plasma.feature.search.ProgramResultKind
import ca.gureet.plasma.feature.search.SearchActions
import ca.gureet.plasma.feature.search.SearchFocus
import ca.gureet.plasma.feature.search.SearchSamples
import ca.gureet.plasma.feature.search.SearchScreen
import ca.gureet.plasma.feature.vod.MovieDetailActions
import ca.gureet.plasma.feature.vod.MovieDetailScreen
import ca.gureet.plasma.feature.vod.SeriesDetailActions
import ca.gureet.plasma.feature.vod.SeriesDetailScreen
import ca.gureet.plasma.feature.vod.VodBrowseActions
import ca.gureet.plasma.feature.vod.VodBrowseScreen
import ca.gureet.plasma.feature.vod.VodSamples
import ca.gureet.plasma.nav.AppsRoute
import ca.gureet.plasma.nav.CatchUpRoute
import ca.gureet.plasma.nav.GuideRoute
import ca.gureet.plasma.nav.HomeRoute
import ca.gureet.plasma.nav.LiveTvRoute
import ca.gureet.plasma.nav.MovieDetailRoute
import ca.gureet.plasma.nav.MoviesRoute
import ca.gureet.plasma.nav.PlayerRoute
import ca.gureet.plasma.nav.ProfilesRoute
import ca.gureet.plasma.nav.RecordingsRoute
import ca.gureet.plasma.nav.SearchRoute
import ca.gureet.plasma.nav.SeriesDetailRoute
import ca.gureet.plasma.nav.SeriesRoute
import ca.gureet.plasma.nav.SettingsRoute
import ca.gureet.plasma.nav.VodPlayerRoute
import ca.gureet.plasma.ui.PendingScreen
import ca.gureet.plasma.core.design.R as DesignR
import ca.gureet.plasma.feature.home.R as HomeR

/**
 * The whole app. Until sources arrive (M1), it runs on the boards' sample data, and its clock
 * starts at 20:41 so the sample guide lines up with what's on screen.
 */
@Composable
fun PlasmaApp() {
    val clock = remember { ShiftedClock(Sample.now, Sample.zone) }
    SampleHost(clock) {
        val nav = rememberNavController()
        NavHost(
            navController = nav,
            startDestination = HomeRoute,
            enterTransition = { fadeIn(PlasmaMotion.screen()) },
            exitTransition = { fadeOut(PlasmaMotion.screen()) },
            popEnterTransition = { fadeIn(PlasmaMotion.screen()) },
            popExitTransition = { fadeOut(PlasmaMotion.screen()) },
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    state = remember { HomeSamples.state },
                    actions = remember(nav) { homeActions(nav) },
                )
            }
            composable<SearchRoute> { entry ->
                val query = entry.toRoute<SearchRoute>().query
                SearchScreen(
                    search = SearchSamples::search,
                    recentSearches = SearchSamples.recent,
                    actions = remember(nav) { searchActions(nav) },
                    profileName = Sample.maya.name,
                    profileInitial = Sample.maya.initial,
                    initialQuery = query,
                    initialFocus = if (query.isEmpty()) SearchFocus.Keyboard else SearchFocus.Results,
                )
            }
            composable<LiveTvRoute> {
                LiveTvScreen(
                    state = remember { LiveTvSamples.state },
                    actions = remember(nav) {
                        LiveTvActions(
                            onTab = { nav.openTab(it) },
                            onSettings = { nav.navigate(SettingsRoute) },
                            onProfile = { nav.navigate(ProfilesRoute) },
                            onWatch = { nav.navigate(PlayerRoute(it)) },
                            onCatchUp = { nav.navigate(CatchUpRoute) },
                        )
                    },
                )
            }
            composable<GuideRoute> {
                GuideScreen(
                    state = remember { GuideSamples.state },
                    actions = remember(nav) {
                        GuideActions(
                            onTab = { nav.openTab(it) },
                            onSettings = { nav.navigate(SettingsRoute) },
                            onProfile = { nav.navigate(ProfilesRoute) },
                            onProgram = { channelId, _, action ->
                                if (action == ProgramAction.Play || action == ProgramAction.PlayCatchUp) nav.navigate(PlayerRoute(channelId))
                            },
                        )
                    },
                )
            }
            composable<MoviesRoute> {
                VodBrowseScreen(
                    state = remember { VodSamples.movies },
                    actions = remember(nav) { vodBrowseActions(nav) { nav.navigate(MovieDetailRoute(it)) } },
                )
            }
            composable<MovieDetailRoute> { entry ->
                val id = entry.toRoute<MovieDetailRoute>().id
                val state = remember(id) { VodSamples.movieDetail(id) }
                if (state == null) {
                    PendingScreen(id)
                } else {
                    MovieDetailScreen(
                        state = state,
                        actions = remember(nav, state) {
                            MovieDetailActions(
                                onBack = { nav.popBackStack() },
                                onPlay = { nav.navigate(VodPlayerRoute(state.title)) },
                                onTrailer = { nav.navigate(VodPlayerRoute(state.title)) },
                                onTitle = { nav.navigate(MovieDetailRoute(it)) },
                                onPerson = { nav.navigate(SearchRoute(it)) },
                            )
                        },
                    )
                }
            }
            composable<SeriesRoute> {
                VodBrowseScreen(
                    state = remember { VodSamples.series },
                    actions = remember(nav) { vodBrowseActions(nav) { nav.navigate(SeriesDetailRoute(it)) } },
                )
            }
            composable<SeriesDetailRoute> { entry ->
                val id = entry.toRoute<SeriesDetailRoute>().id
                val state = remember(id) { VodSamples.seriesDetail(id) }
                if (state == null) {
                    PendingScreen(id)
                } else {
                    SeriesDetailScreen(
                        state = state,
                        actions = remember(nav, state) {
                            SeriesDetailActions(
                                onBack = { nav.popBackStack() },
                                onPlay = { nav.navigate(VodPlayerRoute(state.title)) },
                                onTrailer = { nav.navigate(VodPlayerRoute(state.title)) },
                            )
                        },
                    )
                }
            }
            composable<VodPlayerRoute> { PendingScreen(it.toRoute<VodPlayerRoute>().title, backLabel = null) }
            composable<AppsRoute> { PendingScreen(stringResource(DesignR.string.tab_apps)) }
            composable<ProfilesRoute> { PendingScreen(stringResource(R.string.whos_watching)) }
            composable<RecordingsRoute> { PendingScreen(stringResource(HomeR.string.dock_recordings)) }
            composable<CatchUpRoute> { PendingScreen(stringResource(HomeR.string.dock_catch_up)) }
            composable<SettingsRoute> { PendingScreen(stringResource(DesignR.string.settings)) }
            composable<PlayerRoute> { entry ->
                val channelId = entry.toRoute<PlayerRoute>().channelId
                PlayerScreen(
                    state = remember(channelId) {
                        PlayerSamples.state.let { s -> if (s.channels.any { it.id == channelId }) s.copy(currentChannelId = channelId) else s }
                    },
                    actions = remember(nav) {
                        PlayerActions(
                            onBack = { nav.popBackStack() },
                            onChannelList = { nav.navigate(LiveTvRoute) },
                        )
                    },
                )
            }
        }
    }
}

private fun searchActions(nav: NavHostController) = SearchActions(
    onTab = { nav.openTab(it) },
    onSettings = { nav.navigate(SettingsRoute) },
    onProfile = { nav.navigate(ProfilesRoute) },
    onProgram = { result ->
        // Live and catch-up programs play; an upcoming one will offer a reminder (M2).
        if (result.kind != ProgramResultKind.Upcoming) nav.navigate(PlayerRoute(result.channelId))
    },
    onTitle = { result ->
        val id = result.id.substringAfter(':')
        nav.navigate(if (result.series) SeriesDetailRoute(id) else MovieDetailRoute(id))
    },
)

private fun vodBrowseActions(nav: NavHostController, onOpen: (String) -> Unit) = VodBrowseActions(
    onTab = { nav.openTab(it) },
    onSettings = { nav.navigate(SettingsRoute) },
    onProfile = { nav.navigate(ProfilesRoute) },
    onOpen = onOpen,
)

private fun homeActions(nav: NavHostController) = HomeActions(
    onTab = { nav.openTab(it) },
    onSettings = { nav.navigate(SettingsRoute) },
    onProfile = { nav.navigate(ProfilesRoute) },
    onDock = { target ->
        nav.navigate(
            when (target) {
                DockTarget.LiveTv -> LiveTvRoute
                DockTarget.Guide -> GuideRoute
                DockTarget.Movies -> MoviesRoute
                DockTarget.Series -> SeriesRoute
                DockTarget.CatchUp -> CatchUpRoute
                DockTarget.Recordings -> RecordingsRoute
            },
        )
    },
    onHero = { item ->
        when (item.kind) {
            HeroKind.Live, HeroKind.Reminder -> nav.navigate(PlayerRoute(item.id.substringAfter(':')))
            HeroKind.ContinueWatching -> nav.navigate(SeriesDetailRoute(item.id.substringAfter(':')))
            HeroKind.NewMovie -> nav.navigate(MovieDetailRoute(item.id.substringAfter(':')))
        }
    },
    onChannel = { nav.navigate(PlayerRoute(it)) },
    onContinue = { nav.openContinue(it) },
    onMovie = { nav.navigate(MovieDetailRoute(it)) },
    onSeeAllMovies = { nav.openTab(TopTab.Movies) },
    onAllApps = { nav.openTab(TopTab.Apps) },
)

/**
 * A Continue Watching item opens by its kind (`kind:id…`): a series or movie its page, catch-up its
 * channel. Items from other apps open in that app once their sources arrive (M5); until then a
 * Jellyfin movie opens Plasma's page for it.
 */
private fun NavHostController.openContinue(item: String) {
    val (kind, id) = item.split(':').let { it[0] to it.getOrElse(1) { "" } }
    when (kind) {
        "series" -> navigate(SeriesDetailRoute(id))
        "catchup" -> navigate(PlayerRoute(id))
        else -> navigate(MovieDetailRoute(id))
    }
}

/** Top-bar tabs are peers: each opens over Home, and Back from any of them returns to Home. */
private fun NavHostController.openTab(tab: TopTab) {
    val route: Any = when (tab) {
        TopTab.Home -> {
            popBackStack(HomeRoute, inclusive = false)
            return
        }
        TopTab.Search -> SearchRoute()
        TopTab.LiveTv -> LiveTvRoute
        TopTab.Guide -> GuideRoute
        TopTab.Movies -> MoviesRoute
        TopTab.Series -> SeriesRoute
        TopTab.Apps -> AppsRoute
    }
    navigate(route) {
        popUpTo(HomeRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
