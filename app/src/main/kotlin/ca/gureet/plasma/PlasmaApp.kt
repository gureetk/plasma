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
import ca.gureet.plasma.feature.home.HomeSamples
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
            composable<SearchRoute> { PendingScreen(stringResource(DesignR.string.tab_search)) }
            composable<LiveTvRoute> { PendingScreen(stringResource(DesignR.string.tab_live_tv)) }
            composable<GuideRoute> { PendingScreen(stringResource(DesignR.string.tab_guide)) }
            composable<MoviesRoute> { PendingScreen(stringResource(DesignR.string.tab_movies)) }
            composable<MovieDetailRoute> { PendingScreen(it.toRoute<MovieDetailRoute>().id) }
            composable<SeriesRoute> { PendingScreen(stringResource(DesignR.string.tab_series)) }
            composable<SeriesDetailRoute> { PendingScreen(it.toRoute<SeriesDetailRoute>().id) }
            composable<AppsRoute> { PendingScreen(stringResource(DesignR.string.tab_apps)) }
            composable<ProfilesRoute> { PendingScreen(stringResource(R.string.whos_watching)) }
            composable<RecordingsRoute> { PendingScreen(stringResource(HomeR.string.dock_recordings)) }
            composable<CatchUpRoute> { PendingScreen(stringResource(HomeR.string.dock_catch_up)) }
            composable<SettingsRoute> { PendingScreen(stringResource(DesignR.string.settings)) }
            composable<PlayerRoute> { PendingScreen(it.toRoute<PlayerRoute>().channelId) }
        }
    }
}

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
    onContinue = { nav.navigate(MovieDetailRoute(it)) },
    onMovie = { nav.navigate(MovieDetailRoute(it)) },
    onSeeAllMovies = { nav.openTab(TopTab.Movies) },
    onAllApps = { nav.openTab(TopTab.Apps) },
)

/** Top-bar tabs are peers: each opens over Home, and Back from any of them returns to Home. */
private fun NavHostController.openTab(tab: TopTab) {
    val route: Any = when (tab) {
        TopTab.Home -> {
            popBackStack(HomeRoute, inclusive = false)
            return
        }
        TopTab.Search -> SearchRoute
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
