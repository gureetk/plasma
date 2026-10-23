// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ca.gureet.plasma.core.data.LastWatched
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaTheme
import ca.gureet.plasma.core.design.component.ImageResolver
import ca.gureet.plasma.core.design.component.LocalImageResolver
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.design.component.ReminderToast
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.design.component.rememberNow
import ca.gureet.plasma.core.fixtures.FixtureImages
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.core.model.ShiftedClock
import ca.gureet.plasma.core.model.TvTurnsOn
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
import ca.gureet.plasma.feature.settings.AddSourceActions
import ca.gureet.plasma.feature.settings.AddSourceScreen
import ca.gureet.plasma.feature.settings.AddSourceState
import ca.gureet.plasma.feature.settings.DuplicateNumbersSamples
import ca.gureet.plasma.feature.settings.DuplicateNumbersScreen
import ca.gureet.plasma.feature.settings.ReplaceHomeSamples
import ca.gureet.plasma.feature.settings.ReplaceHomeScreen
import ca.gureet.plasma.feature.settings.SettingsActions
import ca.gureet.plasma.feature.settings.SettingsDestination
import ca.gureet.plasma.feature.settings.SettingsPage
import ca.gureet.plasma.feature.settings.SettingsScreen
import ca.gureet.plasma.feature.settings.SettingsValues
import ca.gureet.plasma.feature.settings.SourceAction
import ca.gureet.plasma.feature.vod.MovieDetailActions
import ca.gureet.plasma.feature.vod.MovieDetailScreen
import ca.gureet.plasma.feature.vod.SeriesDetailActions
import ca.gureet.plasma.feature.vod.SeriesDetailScreen
import ca.gureet.plasma.feature.vod.VodBrowseActions
import ca.gureet.plasma.feature.vod.VodBrowseScreen
import ca.gureet.plasma.feature.vod.VodSamples
import ca.gureet.plasma.live.Lists
import ca.gureet.plasma.live.LiveCatchUp
import ca.gureet.plasma.live.LiveGuide
import ca.gureet.plasma.live.LiveHome
import ca.gureet.plasma.live.LiveLiveTv
import ca.gureet.plasma.live.LiveNames
import ca.gureet.plasma.live.LivePlayer
import ca.gureet.plasma.live.LiveSearchScreen
import ca.gureet.plasma.live.LiveStates
import ca.gureet.plasma.live.audioFacts
import ca.gureet.plasma.live.connect
import ca.gureet.plasma.live.settingsFacts
import ca.gureet.plasma.nav.AddSourceRoute
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
import ca.gureet.plasma.nav.ReplaceHomeRoute
import ca.gureet.plasma.nav.SearchRoute
import ca.gureet.plasma.nav.SeriesDetailRoute
import ca.gureet.plasma.nav.SeriesRoute
import ca.gureet.plasma.nav.SettingsRoute
import ca.gureet.plasma.nav.VodPlayerRoute
import ca.gureet.plasma.player.LiveStatus
import ca.gureet.plasma.player.LocalLiveVideo
import ca.gureet.plasma.player.LocalVideoLayer
import ca.gureet.plasma.player.PlayerManager
import ca.gureet.plasma.player.StreamKeys
import ca.gureet.plasma.player.VideoHost
import ca.gureet.plasma.player.VideoLayerState
import ca.gureet.plasma.ui.IdleHost
import ca.gureet.plasma.ui.PendingScreen
import coil3.ImageLoader
import coil3.compose.rememberAsyncImagePainter
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ca.gureet.plasma.core.design.R as DesignR
import ca.gureet.plasma.feature.settings.R as SettingsR
import ca.gureet.plasma.feature.settings.SourceType as AddSourceType

/**
 * The whole app. Until a source is added it runs on the boards' sample world, with a clock that
 * starts at 20:41 so the sample guide lines up; then on the source's channels and the real time.
 */
@Composable
fun PlasmaApp() {
    val context = LocalContext.current
    val graph = remember(context) { context.graph }
    val hasSources by graph.hasSources.collectAsState()
    // Until the database says whether there's a source, there's nothing to choose between.
    val stored by graph.storedChoices.collectAsState()
    val live = hasSources ?: return
    val chosen = stored ?: return
    val sampleClock = remember { ShiftedClock(Sample.now, Sample.zone) }
    val clock = if (live) graph.clock else sampleClock
    // Settings starts from what's stored and stores each change.
    val settings = remember { SettingsValues(chosen) { key, index -> graph.scope.launch { graph.data.settings.choose(key, index) } } }
    val unlocks = remember(clock) { ParentalUnlocks(clock) }
    val names = LiveNames(
        profileName = stringResource(R.string.default_profile),
        allChannels = stringResource(R.string.all_channels),
        favorites = stringResource(R.string.favorites),
        recentlyWatched = stringResource(R.string.recently_watched),
    )
    val states = remember(names, clock, unlocks) { LiveStates(names, clock.zone, unlocks::isUnlocked) }
    AppHost(graph, live, clock) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        // The TV turned off and on again: When the TV Turns On decides what shows (brief 5.13 and 5.20).
        LaunchedEffect(nav, live) {
            graph.standby.turnedOn.collect {
                if (live) nav.afterTvTurnsOn(graph.choices.tvTurnsOn, graph.data.channels.lastWatched.first())
            }
        }
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
                    val actions = remember(nav) { homeActions(nav) }
                    if (live) {
                        LiveHome(graph, states, actions)
                    } else {
                        HomeScreen(state = remember { HomeSamples.state }, actions = actions)
                    }
                }
                composable<SearchRoute> { entry ->
                    val query = entry.toRoute<SearchRoute>().query
                    val actions = remember(nav) { searchActions(nav) }
                    val focus = if (query.isEmpty()) SearchFocus.Keyboard else SearchFocus.Results
                    if (live) {
                        LiveSearchScreen(graph, actions, names.profileName, query, focus)
                    } else {
                        SearchScreen(
                            search = SearchSamples::search,
                            recentSearches = SearchSamples.recent,
                            actions = actions,
                            profileName = Sample.maya.name,
                            profileInitial = Sample.maya.initial,
                            initialQuery = query,
                            initialFocus = focus,
                        )
                    }
                }
                composable<LiveTvRoute> {
                    val actions = remember(nav) {
                        LiveTvActions(
                            onTab = { nav.openTab(it) },
                            onSettings = { nav.navigate(SettingsRoute()) },
                            onProfile = { nav.navigate(ProfilesRoute) },
                            onWatch = { nav.navigate(PlayerRoute(it)) },
                            onWatchFrom = { channelId, groupId -> nav.navigate(PlayerRoute(channelId, groupId)) },
                            onCatchUp = { nav.navigate(CatchUpRoute(it)) },
                            onMultiview = { nav.navigate(MultiviewRoute) },
                        )
                    }
                    if (live) {
                        LiveLiveTv(graph, states, actions)
                    } else {
                        LiveTvScreen(state = remember { LiveTvSamples.state }, actions = actions)
                    }
                }
                composable<GuideRoute> {
                    val actions = remember(nav) {
                        GuideActions(
                            onTab = { nav.openTab(it) },
                            onSettings = { nav.navigate(SettingsRoute()) },
                            onProfile = { nav.navigate(ProfilesRoute) },
                            onProgram = { channelId, programId, action ->
                                when (action) {
                                    ProgramAction.Play -> nav.navigate(PlayerRoute(channelId, Lists.ALL))
                                    // A past program on a channel with an archive plays from its start (brief 4.8).
                                    ProgramAction.PlayCatchUp -> nav.navigate(PlayerRoute(channelId, Lists.ALL, from = StreamKeys.archiveStart(programId)?.epochSecond))
                                    else -> Unit
                                }
                            },
                        )
                    }
                    if (live) {
                        LiveGuide(graph, states, actions)
                    } else {
                        GuideScreen(state = remember { GuideSamples.state }, actions = actions)
                    }
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
                    if (live) {
                        val back = backLabel(nav)
                        LiveCatchUp(
                            graph = graph,
                            states = states,
                            channelId = channelId,
                            actions = remember(nav) {
                                CatchUpActions(
                                    // A program from the archive, from its start (brief 4.18).
                                    onPlay = { id, program -> nav.navigate(PlayerRoute(id, Lists.ALL, from = program.start.toEpochSecond())) },
                                    onGuide = { nav.navigate(GuideRoute) },
                                    onShowInGuide = { _, _ -> nav.navigate(GuideRoute) },
                                )
                            },
                            empty = { PendingScreen(stringResource(R.string.catch_up), back, stringResource(R.string.no_catch_up)) },
                        )
                        return@composable
                    }
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
                    val sources by graph.data.sources.sources.collectAsState(emptyList())
                    val world by graph.world.collectAsState()
                    val counts = world?.channels?.groupingBy { it.sourceId }?.eachCount().orEmpty()
                    val audio = audioFacts()
                    SettingsScreen(
                        page = SettingsPage.entries.firstOrNull { it.name == route.page } ?: SettingsPage.Main,
                        values = settings,
                        actions = remember(nav, context) { settingsActions(nav, context, graph) },
                        facts = remember(sources, counts, clock, audio) { settingsFacts(sources, counts, clock.zone, audio) },
                        initialItem = route.item,
                    )
                }
                composable<PendingRoute> { entry ->
                    val route = entry.toRoute<PendingRoute>()
                    PendingScreen(route.title, backLabel = route.back)
                }
                composable<AddSourceRoute> {
                    // A blank Xtream form: the board's half-filled portal is for the screenshot test.
                    AddSourceScreen(
                        state = remember { AddSourceState.blank(AddSourceType.Xtream, primary = !live) },
                        actions = remember(nav) {
                            AddSourceActions(
                                onDone = { nav.popBackStack() },
                                onConnect = { form -> connect(graph, form) },
                            )
                        },
                    )
                }
                composable<ReplaceHomeRoute> {
                    val context = LocalContext.current
                    ReplaceHomeScreen(
                        // The commands name this build's own Home activity.
                        state = remember(context) {
                            ReplaceHomeSamples.state.copy(homeActivity = "${context.packageName}/${MainActivity::class.java.name}")
                        },
                        onOpenDeveloperOptions = { context.startActivitySafely(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
                    )
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
                    val route = entry.toRoute<PlayerRoute>()
                    val channelId = route.channelId
                    val actions = remember(nav) {
                        PlayerActions(
                            onBack = { nav.popBackStack() },
                            onChannelList = { nav.navigate(LiveTvRoute) },
                            onMultiview = { nav.navigate(MultiviewRoute) },
                            onOpenChannel = { id -> nav.navigate(PlayerRoute(id, Lists.ALL)) { popUpTo<PlayerRoute> { inclusive = true } } },
                        )
                    }
                    if (live) {
                        // A locked channel asks for the PIN first; the profiles' own PIN comes with M7.
                        val world by graph.world.collectAsState()
                        val channel = world?.byId?.get(channelId)
                        if (channel != null && channel.locked && !unlocks.isUnlocked(channelId)) {
                            val now = world?.nowNext(channel)?.now
                            ParentalPinScreen(
                                request = PinRequest.Program(channel.number, channel.name, channel.logo, now?.title ?: channel.name, rating = null),
                                backLabel = backLabel(nav),
                                checkPin = { it == ProfilesSamples.PIN },
                                onUnlocked = { unlocks.unlock(channelId, now?.end, it) },
                            )
                        } else {
                            LivePlayer(graph, states, channelId, route.listId ?: Lists.ALL, route.from?.let(Instant::ofEpochSecond), actions)
                        }
                        return@composable
                    }
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
                        actions = actions,
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
        if (live && entry?.destination?.hasRoute<PlayerRoute>() != true) {
            ReminderPopup(graph, states) { channelId -> nav.navigate(PlayerRoute(channelId, Lists.ALL)) }
        }
    }
}

/**
 * A reminder coming due, over any screen but the player, which shows its own (brief 4.11 and 5.7).
 * It's a window of its own: it takes the keys while it shows, and the screen underneath keeps its
 * focus for when it closes.
 */
@Composable
private fun ReminderPopup(graph: AppGraph, states: LiveStates, onSwitch: (channelId: String) -> Unit) {
    val due by graph.reminderClock.due.collectAsState()
    val world by graph.world.collectAsState()
    val now by rememberNow()
    val shown = due ?: return
    val reminder = world?.let { states.reminder(it, shown) } ?: return
    Popup(onDismissRequest = { graph.reminderClock.done(shown.id) }, properties = PopupProperties(focusable = true)) {
        Box(Modifier.fillMaxSize()) {
            ReminderToast(
                reminder = reminder,
                now = now.toInstant(),
                onSwitch = {
                    graph.reminderClock.done(shown.id)
                    onSwitch(reminder.channelId)
                },
                onDismiss = { graph.reminderClock.done(shown.id) },
            )
        }
    }
}

/**
 * Everything a screen expects around it: the theme, the clock, images (the sample art, and URLs
 * through Coil), the video layer, and live video once there's a source.
 */
@Composable
private fun AppHost(graph: AppGraph, live: Boolean, clock: PlasmaClock, content: @Composable () -> Unit) {
    val video = remember { VideoLayerState() }
    val images = remember(graph) { AppImages(graph.images) }
    PlasmaTheme {
        CompositionLocalProvider(
            LocalPlasmaClock provides clock,
            LocalImageResolver provides images,
            LocalVideoLayer provides video,
            LocalLiveVideo provides if (live) graph.player else null,
        ) {
            if (live) KeepScreenOnWhilePlaying(graph.player)
            VideoHost(video, content)
        }
    }
}

/**
 * The TV's own screensaver would stop Plasma, and the channel with it (brief 5.20), so the screen
 * stays on while live video opens or plays. Plasma's screensaver still comes after ten idle minutes
 * away from the player.
 */
@Composable
private fun KeepScreenOnWhilePlaying(player: PlayerManager) {
    val status by player.status.collectAsState()
    val view = LocalView.current
    val playing = status is LiveStatus.Opening || status is LiveStatus.Playing || status is LiveStatus.Reconnecting
    DisposableEffect(view, playing) {
        view.keepScreenOn = playing
        onDispose { view.keepScreenOn = false }
    }
}

/** Sample art by its `fixture://` name; channel logos and other pictures from the network. */
private class AppImages(private val loader: ImageLoader) : ImageResolver {
    @Composable
    override fun painter(model: String): Painter? =
        if (model.startsWith(FixtureImages.SCHEME)) FixtureImages.resolver.painter(model) else rememberAsyncImagePainter(model, loader)
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
        // Live programs play, catch-up ones from their start; one coming up opens Remind Me and Record on Search itself.
        when (result.kind) {
            ProgramResultKind.Live -> nav.navigate(PlayerRoute(result.channelId))
            ProgramResultKind.CatchUp -> nav.navigate(PlayerRoute(result.channelId, from = result.start.epochSecond))
            ProgramResultKind.Upcoming -> Unit
        }
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
 * Settings' pages open over each other. The profile editor comes with profiles (M7).
 */
private fun settingsActions(nav: NavHostController, context: Context, graph: AppGraph) = SettingsActions(
    onOpen = { nav.navigate(SettingsRoute(it.name)) },
    onSource = { sourceId, action ->
        when (action) {
            SourceAction.RefreshNow -> graph.refresh(sourceId)
            SourceAction.Delete -> graph.scope.launch { graph.data.sources.remove(sourceId) }
        }
    },
    onGo = { destination ->
        fun pending(@StringRes title: Int, @StringRes back: Int) = PendingRoute(context.getString(title), context.getString(back))
        nav.navigate(
            when (destination) {
                SettingsDestination.DuplicateNumbers -> DuplicateNumbersRoute
                SettingsDestination.ReplaceHome -> ReplaceHomeRoute
                SettingsDestination.AddSource -> AddSourceRoute
                SettingsDestination.Profiles -> pending(R.string.edit_profile, SettingsR.string.profiles)
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
    onChannel = { nav.navigate(PlayerRoute(it, Lists.FAVORITES)) },
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

/** What shows when the TV comes back on (Settings › Launcher & Home, brief 5.13). */
private fun NavHostController.afterTvTurnsOn(choice: TvTurnsOn, last: LastWatched?) {
    when (choice) {
        TvTurnsOn.LastChannel -> {
            // Already on the player: the channel plays again where it is.
            if (last == null || currentDestination?.hasRoute<PlayerRoute>() == true) return
            navigate(PlayerRoute(last.channelId, last.listId)) { popUpTo(HomeRoute) }
        }
        TvTurnsOn.Home -> popBackStack(HomeRoute, inclusive = false)
        TvTurnsOn.Guide -> openTab(TopTab.Guide)
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
