// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.fixtures.SampleHost
import ca.gureet.plasma.core.model.ShiftedClock
import ca.gureet.plasma.feature.apps.AppsActions
import ca.gureet.plasma.feature.apps.AppsSamples
import ca.gureet.plasma.feature.apps.AppsScreen
import ca.gureet.plasma.feature.apps.SystemTile
import ca.gureet.plasma.feature.catchup.CatchUpActions
import ca.gureet.plasma.feature.catchup.CatchUpSamples
import ca.gureet.plasma.feature.catchup.CatchUpScreen
import ca.gureet.plasma.feature.guide.GuideActions
import ca.gureet.plasma.feature.guide.GuideSamples
import ca.gureet.plasma.feature.guide.GuideScreen
import ca.gureet.plasma.feature.guide.ProgramAction
import ca.gureet.plasma.feature.home.DockTarget
import ca.gureet.plasma.feature.home.HeroKind
import ca.gureet.plasma.feature.home.HomeActions
import ca.gureet.plasma.feature.home.HomeSamples
import ca.gureet.plasma.feature.home.HomeScreen
import ca.gureet.plasma.feature.home.ScreensaverScreen
import ca.gureet.plasma.feature.livetv.LiveTvActions
import ca.gureet.plasma.feature.livetv.LiveTvSamples
import ca.gureet.plasma.feature.livetv.LiveTvScreen
import ca.gureet.plasma.feature.player.MultiviewActions
import ca.gureet.plasma.feature.player.MultiviewScreen
import ca.gureet.plasma.feature.player.PlayerActions
import ca.gureet.plasma.feature.player.PlayerSamples
import ca.gureet.plasma.feature.player.PlayerScreen
import ca.gureet.plasma.feature.profiles.ParentalPinScreen
import ca.gureet.plasma.feature.profiles.PinRequest
import ca.gureet.plasma.feature.profiles.ProfilesActions
import ca.gureet.plasma.feature.profiles.ProfilesSamples
import ca.gureet.plasma.feature.profiles.ProfilesScreen
import ca.gureet.plasma.feature.recordings.RecordingsActions
import ca.gureet.plasma.feature.recordings.RecordingsSamples
import ca.gureet.plasma.feature.recordings.RecordingsScreen
import ca.gureet.plasma.feature.search.ProgramResultKind
import ca.gureet.plasma.feature.search.SearchActions
import ca.gureet.plasma.feature.search.SearchFocus
import ca.gureet.plasma.feature.search.SearchSamples
import ca.gureet.plasma.feature.search.SearchScreen
import ca.gureet.plasma.feature.settings.DuplicateNumbersSamples
import ca.gureet.plasma.feature.settings.DuplicateNumbersScreen
import ca.gureet.plasma.feature.settings.SettingsActions
import ca.gureet.plasma.feature.settings.SettingsDestination
import ca.gureet.plasma.feature.settings.SettingsPage
import ca.gureet.plasma.feature.settings.SettingsScreen
import ca.gureet.plasma.feature.settings.SettingsValues
import ca.gureet.plasma.feature.vod.MovieDetailActions
import ca.gureet.plasma.feature.vod.MovieDetailScreen
import ca.gureet.plasma.feature.vod.SeriesDetailActions
import ca.gureet.plasma.feature.vod.SeriesDetailScreen
import ca.gureet.plasma.feature.vod.VodBrowseActions
import ca.gureet.plasma.feature.vod.VodBrowseScreen
import ca.gureet.plasma.feature.vod.VodSamples
import ca.gureet.plasma.nav.AppsRoute
import ca.gureet.plasma.nav.CatchUpRoute
import ca.gureet.plasma.nav.DuplicateNumbersRoute
import ca.gureet.plasma.nav.GuideRoute
import ca.gureet.plasma.nav.HomeRoute
import ca.gureet.plasma.nav.LiveTvRoute
import ca.gureet.plasma.nav.MovieDetailRoute
import ca.gureet.plasma.nav.MoviesRoute
import ca.gureet.plasma.nav.MultiviewRoute
import ca.gureet.plasma.nav.PendingRoute
import ca.gureet.plasma.nav.PlayerRoute
import ca.gureet.plasma.nav.ProfilePinRoute
import ca.gureet.plasma.nav.ProfilesRoute
import ca.gureet.plasma.nav.RecordingsRoute
import ca.gureet.plasma.nav.SearchRoute
import ca.gureet.plasma.nav.SeriesDetailRoute
import ca.gureet.plasma.nav.SeriesRoute
import ca.gureet.plasma.nav.SettingsRoute
import ca.gureet.plasma.nav.VodPlayerRoute
import ca.gureet.plasma.ui.IdleHost
import ca.gureet.plasma.ui.PendingScreen
import ca.gureet.plasma.core.design.R as DesignR
import ca.gureet.plasma.feature.settings.R as SettingsR

/**
 * The whole app. Until sources arrive (M1), it runs on the boards' sample data, and its clock
 * starts at 20:41 so the sample guide lines up with what's on screen.
 */
@Composable
fun PlasmaApp() {
    val clock = remember { ShiftedClock(Sample.now, Sample.zone) }
    // What's been chosen in Settings lasts while the app runs; saving it comes with the sources (M1).
    val settings = remember { SettingsValues() }
    val unlocks = remember { ParentalUnlocks(clock) }
    SampleHost(clock) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val watching = entry?.destination?.let {
            it.hasRoute<PlayerRoute>() || it.hasRoute<MultiviewRoute>() || it.hasRoute<VodPlayerRoute>()
        } == true
        IdleHost(
            enabled = !watching,
            timeoutMs = IDLE_MS,
            screensaver = { ScreensaverScreen(remember { HomeSamples.screensaver }) },
        ) {
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
                                onSettings = { nav.navigate(SettingsRoute()) },
                                onProfile = { nav.navigate(ProfilesRoute) },
                                onWatch = { nav.navigate(PlayerRoute(it)) },
                                onCatchUp = { nav.navigate(CatchUpRoute(it)) },
                                onMultiview = { nav.navigate(MultiviewRoute) },
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
                                onSettings = { nav.navigate(SettingsRoute()) },
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
                composable<AppsRoute> {
                    val context = LocalContext.current
                    AppsScreen(
                        state = remember { AppsSamples.state },
                        actions = remember(nav, context) {
                            AppsActions(
                                onTab = { nav.openTab(it) },
                                onSettings = { nav.navigate(SettingsRoute()) },
                                onProfile = { nav.navigate(ProfilesRoute) },
                                onSystem = { tile ->
                                    // The sample apps aren't installed, but the TV's own settings are there to open.
                                    if (tile == SystemTile.DeviceSettings) context.startActivitySafely(Intent(Settings.ACTION_SETTINGS))
                                },
                            )
                        },
                    )
                }
                composable<ProfilesRoute> {
                    ProfilesScreen(
                        state = remember { ProfilesSamples.state },
                        actions = remember(nav) {
                            ProfilesActions(
                                onPick = { profile -> if (profile.pin) nav.navigate(ProfilePinRoute(profile.id)) else nav.popBackStack() },
                                onAdd = { nav.navigate(SettingsRoute(SettingsPage.Profiles.name, item = "add-profile")) },
                                onManage = { nav.navigate(SettingsRoute(SettingsPage.Profiles.name)) },
                            )
                        },
                    )
                }
                composable<RecordingsRoute> {
                    RecordingsScreen(
                        state = remember { RecordingsSamples.state },
                        actions = remember(nav) {
                            RecordingsActions(
                                onPlay = { nav.navigate(VodPlayerRoute(it.title)) },
                                onShowInGuide = { _, _ -> nav.navigate(GuideRoute) },
                            )
                        },
                    )
                }
                composable<CatchUpRoute> { entry ->
                    val channelId = entry.toRoute<CatchUpRoute>().channelId
                    CatchUpScreen(
                        state = remember(channelId) {
                            CatchUpSamples.state.let { s -> if (s.channels.any { it.id == channelId }) s.copy(selectedChannelId = channelId!!) else s }
                        },
                        actions = remember(nav) {
                            CatchUpActions(
                                onPlay = { _, program -> nav.navigate(VodPlayerRoute(program.title)) },
                                onGuide = { nav.navigate(GuideRoute) },
                                onShowInGuide = { _, _ -> nav.navigate(GuideRoute) },
                            )
                        },
                    )
                }
                composable<SettingsRoute> { entry ->
                    val route = entry.toRoute<SettingsRoute>()
                    val context = LocalContext.current
                    SettingsScreen(
                        page = SettingsPage.entries.firstOrNull { it.name == route.page } ?: SettingsPage.Main,
                        values = settings,
                        actions = remember(nav, context) { settingsActions(nav, context) },
                        initialItem = route.item,
                    )
                }
                composable<PendingRoute> { entry ->
                    val route = entry.toRoute<PendingRoute>()
                    PendingScreen(route.title, backLabel = route.back)
                }
                composable<DuplicateNumbersRoute> {
                    DuplicateNumbersScreen(remember { DuplicateNumbersSamples.state })
                }
                composable<ProfilePinRoute> { entry ->
                    val profile = remember(entry) { Sample.profiles.first { it.id == entry.toRoute<ProfilePinRoute>().profileId } }
                    ParentalPinScreen(
                        request = PinRequest.Profile(profile.name),
                        backLabel = stringResource(SettingsR.string.profiles),
                        checkPin = { it == ProfilesSamples.PIN },
                        onUnlocked = { nav.popBackStack(ProfilesRoute, inclusive = true) },
                    )
                }
                composable<PlayerRoute> { entry ->
                    val channelId = entry.toRoute<PlayerRoute>().channelId
                    val locked = remember(channelId) { Sample.channels.firstOrNull { it.id == channelId && it.locked } }
                    if (locked != null && !unlocks.isUnlocked(channelId)) {
                        ParentalPinScreen(
                            request = remember(locked) { ProfilesSamples.lockedProgram(locked) },
                            backLabel = backLabel(nav),
                            checkPin = { it == ProfilesSamples.PIN },
                            onUnlocked = { unlocks.unlock(channelId, Sample.nowOn(locked)?.end?.toInstant(), it) },
                        )
                        return@composable
                    }
                    PlayerScreen(
                        state = remember(channelId) {
                            PlayerSamples.state.let { s -> if (s.channels.any { it.id == channelId }) s.copy(currentChannelId = channelId) else s }
                        },
                        actions = remember(nav) {
                            PlayerActions(
                                onBack = { nav.popBackStack() },
                                onChannelList = { nav.navigate(LiveTvRoute) },
                                onMultiview = { nav.navigate(MultiviewRoute) },
                            )
                        },
                    )
                }
                composable<MultiviewRoute> {
                    MultiviewScreen(
                        state = remember { PlayerSamples.multiview },
                        actions = remember(nav) {
                            MultiviewActions(
                                onFullScreen = { channelId ->
                                    nav.navigate(PlayerRoute(channelId)) { popUpTo(MultiviewRoute) { inclusive = true } }
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

/** How long Plasma waits for a key press before the screensaver shows (Settings › Launcher & Home). */
private const val IDLE_MS = 10 * 60 * 1_000L

/** Opens another app's screen; on a TV without it, nothing happens. */
private fun Context.startActivitySafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Nothing to open on this device.
    }
}

private fun searchActions(nav: NavHostController) = SearchActions(
    onTab = { nav.openTab(it) },
    onSettings = { nav.navigate(SettingsRoute()) },
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
    onSettings = { nav.navigate(SettingsRoute()) },
    onProfile = { nav.navigate(ProfilesRoute) },
    onOpen = onOpen,
)

/**
 * Settings' pages open over each other. Add a Source, Home Screen setup, the profile editor and
 * Phone Remote are still being built.
 */
private fun settingsActions(nav: NavHostController, context: Context) = SettingsActions(
    onOpen = { nav.navigate(SettingsRoute(it.name)) },
    onGo = { destination ->
        fun pending(@StringRes title: Int, @StringRes back: Int) = PendingRoute(context.getString(title), context.getString(back))
        nav.navigate(
            when (destination) {
                SettingsDestination.DuplicateNumbers -> DuplicateNumbersRoute
                SettingsDestination.AddSource -> pending(SettingsR.string.add_source, SettingsR.string.sources)
                SettingsDestination.HomeScreenSetup -> pending(SettingsR.string.use_as_home, SettingsR.string.launcher)
                SettingsDestination.Profiles -> pending(R.string.edit_profile, SettingsR.string.profiles)
                SettingsDestination.PhoneRemote -> pending(SettingsR.string.phone_remote, SettingsR.string.remote)
            },
        )
    },
)

private fun homeActions(nav: NavHostController) = HomeActions(
    onTab = { nav.openTab(it) },
    onSettings = { nav.navigate(SettingsRoute()) },
    onProfile = { nav.navigate(ProfilesRoute) },
    onDock = { target ->
        nav.navigate(
            when (target) {
                DockTarget.LiveTv -> LiveTvRoute
                DockTarget.Guide -> GuideRoute
                DockTarget.Movies -> MoviesRoute
                DockTarget.Series -> SeriesRoute
                DockTarget.CatchUp -> CatchUpRoute()
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

/** Where Back goes from a screen opened over [nav]'s current one, for its "‹ Live TV" header. */
@Composable
private fun backLabel(nav: NavHostController): String {
    val previous = nav.previousBackStackEntry?.destination
    return stringResource(
        when {
            previous == null -> DesignR.string.tab_home
            previous.hasRoute<LiveTvRoute>() -> DesignR.string.tab_live_tv
            previous.hasRoute<GuideRoute>() -> DesignR.string.tab_guide
            previous.hasRoute<SearchRoute>() -> DesignR.string.tab_search
            else -> DesignR.string.tab_home
        },
    )
}
