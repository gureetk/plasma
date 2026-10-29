// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.SystemClock
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ca.gureet.plasma.core.data.LastWatched
import ca.gureet.plasma.core.data.Playable
import ca.gureet.plasma.core.data.ProfileChoices
import ca.gureet.plasma.core.data.ProfileKind
import ca.gureet.plasma.core.data.Source
import ca.gureet.plasma.core.design.LocalCanRecord
import ca.gureet.plasma.core.design.LocalRemoteMirror
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.LARGE_TEXT_SCALE
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaTheme
import ca.gureet.plasma.core.design.component.ImageResolver
import ca.gureet.plasma.core.design.component.LocalImageResolver
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.design.component.LocalTopTabs
import ca.gureet.plasma.core.design.component.LocalSettingsBadge
import ca.gureet.plasma.core.design.component.NoticeToast
import ca.gureet.plasma.core.design.component.ReminderToast
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.design.component.rememberNow
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.fixtures.FixtureImages
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.core.model.HomeButtonChoice
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.core.model.ShiftedClock
import ca.gureet.plasma.core.model.TvTurnsOn
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
import ca.gureet.plasma.feature.home.PlasmaPictures
import ca.gureet.plasma.feature.home.ScreensaverScreen
import ca.gureet.plasma.feature.livetv.LiveTvActions
import ca.gureet.plasma.feature.livetv.LiveTvSamples
import ca.gureet.plasma.feature.livetv.LiveTvScreen
import ca.gureet.plasma.feature.player.AsleepScreen
import ca.gureet.plasma.feature.player.MultiviewActions
import ca.gureet.plasma.feature.player.MultiviewScreen
import ca.gureet.plasma.feature.player.PlayerActions
import ca.gureet.plasma.feature.player.PlayerSamples
import ca.gureet.plasma.feature.player.PlayerScreen
import ca.gureet.plasma.feature.player.SleepToast
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
import ca.gureet.plasma.feature.settings.DuplicateNumbersActions
import ca.gureet.plasma.feature.settings.DuplicateNumbersSamples
import ca.gureet.plasma.feature.settings.DuplicateNumbersScreen
import ca.gureet.plasma.feature.settings.HomeAction
import ca.gureet.plasma.feature.settings.JellyfinServerChoice
import ca.gureet.plasma.feature.settings.KeyTestScreen
import ca.gureet.plasma.feature.settings.KidSafeGroupInfo
import ca.gureet.plasma.feature.settings.LearnKeyScreen
import ca.gureet.plasma.feature.settings.LockedChannelInfo
import ca.gureet.plasma.feature.settings.LockedGroupInfo
import ca.gureet.plasma.feature.settings.OtherAppsAction
import ca.gureet.plasma.feature.settings.OverAppsAction
import ca.gureet.plasma.feature.settings.UpdateAction
import ca.gureet.plasma.feature.settings.PairedPhone
import ca.gureet.plasma.feature.settings.ProfileSettings
import ca.gureet.plasma.feature.settings.ReplaceHomeActions
import ca.gureet.plasma.feature.settings.ReplaceHomeScreen
import ca.gureet.plasma.feature.settings.SettingsActions
import ca.gureet.plasma.feature.settings.SettingsDestination
import ca.gureet.plasma.feature.settings.SettingsPage
import ca.gureet.plasma.feature.settings.SettingsScreen
import ca.gureet.plasma.feature.settings.SettingsValues
import ca.gureet.plasma.feature.settings.SourceAction
import ca.gureet.plasma.feature.settings.SourceSettings
import ca.gureet.plasma.feature.settings.TvScreensaver
import ca.gureet.plasma.feature.settings.TvScreensaverAction
import ca.gureet.plasma.feature.vod.MovieDetailActions
import ca.gureet.plasma.feature.vod.MovieDetailScreen
import ca.gureet.plasma.feature.vod.SeriesDetailActions
import ca.gureet.plasma.feature.vod.SeriesDetailScreen
import ca.gureet.plasma.feature.vod.VodBrowseActions
import ca.gureet.plasma.feature.vod.VodBrowseScreen
import ca.gureet.plasma.feature.vod.VodKind
import ca.gureet.plasma.feature.vod.VodSamples
import ca.gureet.plasma.live.ChannelSummary
import ca.gureet.plasma.live.Lists
import ca.gureet.plasma.live.LiveCatchUp
import ca.gureet.plasma.live.LiveGuide
import ca.gureet.plasma.live.LiveHome
import ca.gureet.plasma.live.LiveLiveTv
import ca.gureet.plasma.live.LiveMultiview
import ca.gureet.plasma.live.LiveNames
import ca.gureet.plasma.live.LivePlayer
import ca.gureet.plasma.live.LiveSearchScreen
import ca.gureet.plasma.live.LiveStates
import ca.gureet.plasma.live.localNames
import ca.gureet.plasma.live.localChoices
import ca.gureet.plasma.live.LiveMovieDetail
import ca.gureet.plasma.integrations.jellyfin.JellyfinApp
import ca.gureet.plasma.live.CatalogShelf
import ca.gureet.plasma.live.LiveRecordings
import ca.gureet.plasma.live.LiveVodPlayer
import ca.gureet.plasma.live.RecordingShelf
import ca.gureet.plasma.live.LiveSeriesDetail
import ca.gureet.plasma.live.LiveVodBrowse
import ca.gureet.plasma.live.audioFacts
import ca.gureet.plasma.live.connect
import ca.gureet.plasma.live.editState
import ca.gureet.plasma.live.save
import ca.gureet.plasma.live.settingsFacts
import ca.gureet.plasma.nav.AddSourceRoute
import ca.gureet.plasma.nav.AppsRoute
import ca.gureet.plasma.nav.CatchUpRoute
import ca.gureet.plasma.nav.DuplicateNumbersRoute
import ca.gureet.plasma.nav.GuideRoute
import ca.gureet.plasma.nav.HomeRoute
import ca.gureet.plasma.nav.KeyTestRoute
import ca.gureet.plasma.nav.LearnKeyRoute
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
import ca.gureet.plasma.nav.titleRoute
import ca.gureet.plasma.nav.SeriesRoute
import ca.gureet.plasma.nav.SettingsRoute
import ca.gureet.plasma.nav.ChoosePinRoute
import ca.gureet.plasma.nav.UnlockCategoryRoute
import ca.gureet.plasma.nav.UnlockChannelRoute
import ca.gureet.plasma.nav.VodPlayerRoute
import ca.gureet.plasma.player.LiveStatus
import ca.gureet.plasma.player.LocalLiveVideo
import ca.gureet.plasma.player.LocalVideoLayer
import ca.gureet.plasma.player.StreamKeys
import ca.gureet.plasma.player.VideoHost
import ca.gureet.plasma.player.VideoLayerState
import ca.gureet.plasma.launcher.AppArt
import ca.gureet.plasma.launcher.HomeButton
import ca.gureet.plasma.launcher.HomeReason
import ca.gureet.plasma.launcher.HomeSetup
import ca.gureet.plasma.ui.IdleHost
import ca.gureet.plasma.ui.PendingScreen
import coil3.ImageLoader
import coil3.compose.rememberAsyncImagePainter
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ca.gureet.plasma.core.design.R as DesignR
import ca.gureet.plasma.feature.settings.R as SettingsR
import ca.gureet.plasma.feature.vod.R as VodR
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
    // Settings starts from what's stored and stores each change; a source's own settings are kept
    // with the source (brief 4.20).
    val settings = remember {
        SettingsValues(chosen) { key, index ->
            val sourceId = SourceSettings.sourceOf(key)
            val profileField = ProfileSettings.of(key)
            when {
                sourceId != null -> graph.chooseForSource(sourceId, key, index)
                // A profile's picture, kind and ages are kept with the profile (5.11).
                profileField != null -> graph.chooseForProfile(profileField.second, profileField.first, index)
                ProfileSettings.isDefault(key) -> graph.chooseDefaultProfile(index)
                else -> graph.choose(key, index)
            }
        }
    }
    // Each profile's own audio and subtitle choices (5.11): Settings shows those of the profile watching.
    LaunchedEffect(chosen) { ProfileChoices.KEYS.forEach { settings.show(it, chosen[it]) } }
    // The sample world's locked channel opens with the sample PIN; once there's a source, with the
    // PIN chosen in Settings › Parental Controls (5.12).
    val sampleUnlocks = remember(sampleClock) { ParentalUnlocks(sampleClock) }
    val unlocks: ChannelUnlocks = if (live) graph.parental else sampleUnlocks
    // Who's watching (5.11): the top bar's initial and the screens' "Me".
    val profileNow by graph.data.profiles.current.collectAsState(null)
    val names = LiveNames(
        profileName = profileNow?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_name_default),
        allChannels = stringResource(R.string.all_channels),
        favorites = stringResource(R.string.favorites),
        recentlyWatched = stringResource(R.string.recently_watched),
    )
    val states = remember(names, clock, unlocks) { LiveStates(names, clock.zone, unlocks::isUnlocked) }
    // Show Android Apps (Settings › Launcher & Home, brief 5.13): Off takes away the Apps tab and Home's Apps row.
    // Show TV Guide (the owner's choice, 28 October): Off takes away the Guide tab.
    val showApps = SettingsChoices(chosen).showAndroidApps
    val showGuide = SettingsChoices(chosen).showGuide
    val tabs = TopTab.entries.filter { (it != TopTab.Apps || showApps) && (it != TopTab.Guide || showGuide) }
    AppHost(graph, live, clock, tabs = tabs) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        // Settings opened with the PIN locks again at Home (5.12).
        val atHome = entry?.destination?.hasRoute<HomeRoute>() == true
        LaunchedEffect(atHome) { if (atHome) graph.parental.settingsOpen = false }
        // The TV turned off and on again: When the TV Turns On decides what shows (brief 5.13 and 5.20).
        LaunchedEffect(nav, live) {
            graph.standby.turnedOn.collect {
                // Who's Watching first, when it's asked (5.11); its pick lands on Home.
                if (live && !whoIsWatching(graph, nav)) nav.afterTvTurnsOn(graph.choices.tvTurnsOn, graph.data.channels.lastWatched.first())
            }
        }
        // A reminder's Switch Now over another app (brief 5.7): its channel, full screen.
        val watch by graph.watchFromReminder.collectAsState()
        LaunchedEffect(watch, live) {
            val channelId = watch ?: return@LaunchedEffect
            if (live) nav.navigate(PlayerRoute(channelId, Lists.ALL)) { popUpTo(HomeRoute) }
            graph.watchedFromReminder(channelId)
        }
        // A channel tapped on a phone (brief 5.21): full screen, in the list it was tapped in. One
        // tapped while Plasma couldn't come forward isn't opened long after.
        val phoneTune by graph.tuneFromPhone.collectAsState()
        LaunchedEffect(phoneTune, live) {
            val tune = phoneTune ?: return@LaunchedEffect
            if (live && SystemClock.elapsedRealtime() - tune.at < PHONE_TUNE_MS) nav.navigate(PlayerRoute(tune.channelId, tune.listId)) { popUpTo(HomeRoute) }
            graph.tunedFromPhone(tune)
        }
        // Opened by Home or at power-on (brief 5.13): Home Button, or When the TV Turns On, decides.
        val opening by graph.homeOpening.collectAsState()
        LaunchedEffect(opening, live) {
            val now = opening ?: return@LaunchedEffect
            val last = if (live) graph.data.channels.lastWatched.first() else null
            when (now.reason) {
                HomeReason.Home -> nav.afterHomeButton(graph.choices.homeButton, last)
                HomeReason.PowerOn -> if (!live || !whoIsWatching(graph, nav)) nav.afterTvTurnsOn(graph.choices.tvTurnsOn, last)
            }
            graph.handled(now)
        }
        val watching = entry?.destination?.let {
            it.hasRoute<PlayerRoute>() || it.hasRoute<MultiviewRoute>() || it.hasRoute<VodPlayerRoute>()
        } == true
        // Screensaver (Settings › Launcher & Home): how long Plasma waits for a key first, if at all.
        val idleMinutes = SettingsChoices(chosen).screensaverAfter.minutes
        IdleHost(
            enabled = !watching && idleMinutes != null,
            timeoutMs = (idleMinutes ?: 0) * MINUTE_MS,
            // The sample world's records; once there's a source, what's recording now (5.6).
            screensaver = {
                val recordingNow by graph.recordingNow.collectAsState(null)
                ScreensaverScreen(remember(live, recordingNow) { if (live) PlasmaPictures.copy(recording = recordingNow) else HomeSamples.screensaver })
            },
            // Home wakes the screensaver as any key does.
            nudge = opening,
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
                    val actions = remember(nav, graph) { homeActions(nav, graph) }
                    if (live) {
                        LiveHome(graph, states, actions, showApps)
                    } else {
                        // The sample world, with the TV's own apps in the Apps row.
                        val apps by graph.apps.collectAsState()
                        HomeScreen(state = remember(apps, showApps) { HomeSamples.state.withApps(apps, showApps) }, actions = actions)
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
                            onLock = { id, locked -> lockChannel(nav, graph, id, locked) },
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
                            onChannel = { channelId -> nav.navigate(PlayerRoute(channelId, Lists.ALL)) },
                        )
                    }
                    if (live) {
                        LiveGuide(graph, states, actions)
                    } else {
                        GuideScreen(state = remember { GuideSamples.state }, actions = actions)
                    }
                }
                composable<MoviesRoute> {
                    val actions = remember(nav) { vodBrowseActions(nav, series = false) { nav.navigate(MovieDetailRoute(it)) } }
                    if (live) {
                        LiveVodBrowse(graph, VodKind.Movies, names.profileName, actions)
                    } else {
                        VodBrowseScreen(state = remember { VodSamples.movies }, actions = actions)
                    }
                }
                composable<MovieDetailRoute> { entry ->
                    val id = entry.toRoute<MovieDetailRoute>().id
                    val sample = remember(id) { VodSamples.movieDetail(id) }
                    if (sample == null) {
                        // A movie from a source (brief 4.14); its player comes next in M4.
                        val title by produceState<String?>(null, id) { value = graph.data.vod.movie(id)?.name }
                        val back = stringResource(VodR.string.back_movies)
                        LiveMovieDetail(
                            graph = graph,
                            id = id,
                            actions = remember(nav, title) {
                                MovieDetailActions(
                                    onBack = { nav.popBackStack() },
                                    onPlay = { fromStart -> nav.navigate(VodPlayerRoute(title.orEmpty(), KIND_MOVIE, id, fromStart)) },
                                    onTitle = { nav.navigate(MovieDetailRoute(it)) },
                                    onPerson = { nav.navigate(SearchRoute(it)) },
                                )
                            },
                            gone = { PendingScreen(back, backLabel = back, message = stringResource(R.string.movie_gone)) },
                        )
                    } else {
                        MovieDetailScreen(
                            state = sample,
                            actions = remember(nav, sample) {
                                MovieDetailActions(
                                    onBack = { nav.popBackStack() },
                                    onPlay = { nav.navigate(VodPlayerRoute(sample.title)) },
                                    onTrailer = { nav.navigate(VodPlayerRoute(sample.title)) },
                                    onTitle = { nav.navigate(MovieDetailRoute(it)) },
                                    onPerson = { nav.navigate(SearchRoute(it)) },
                                )
                            },
                        )
                    }
                }
                composable<SeriesRoute> {
                    val actions = remember(nav) { vodBrowseActions(nav, series = true) { nav.navigate(SeriesDetailRoute(it)) } }
                    if (live) {
                        LiveVodBrowse(graph, VodKind.Series, names.profileName, actions)
                    } else {
                        VodBrowseScreen(state = remember { VodSamples.series }, actions = actions)
                    }
                }
                composable<SeriesDetailRoute> { entry ->
                    val id = entry.toRoute<SeriesDetailRoute>().id
                    val sample = remember(id) { VodSamples.seriesDetail(id) }
                    if (sample == null) {
                        // A series from a source (brief 4.16); its player comes next in M4.
                        val title by produceState<String?>(null, id) { value = graph.data.vod.series(id)?.name }
                        val back = stringResource(VodR.string.back_series)
                        LiveSeriesDetail(
                            graph = graph,
                            id = id,
                            actions = remember(nav) { SeriesDetailActions(onBack = { nav.popBackStack() }) },
                            onEpisode = { episodeId, fromStart -> nav.navigate(VodPlayerRoute(title.orEmpty(), KIND_EPISODE, episodeId, fromStart)) },
                            gone = { PendingScreen(back, backLabel = back, message = stringResource(R.string.series_gone)) },
                        )
                    } else {
                        SeriesDetailScreen(
                            state = sample,
                            actions = remember(nav, sample) {
                                SeriesDetailActions(
                                    onBack = { nav.popBackStack() },
                                    onPlay = { _, _ -> nav.navigate(VodPlayerRoute(sample.title)) },
                                    onTrailer = { nav.navigate(VodPlayerRoute(sample.title)) },
                                )
                            },
                        )
                    }
                }
                composable<VodPlayerRoute> { entry ->
                    val route = entry.toRoute<VodPlayerRoute>()
                    // A source's movies and episodes, or a Jellyfin server's (5.19).
                    val jellyfin = route.kind == KIND_JELLYFIN
                    val shelf = remember(route.kind) {
                        when (route.kind) {
                            KIND_MOVIE -> CatalogShelf(graph.data.vod, Playable.Kind.Movie)
                            KIND_EPISODE -> CatalogShelf(graph.data.vod, Playable.Kind.Episode)
                            KIND_JELLYFIN -> graph.jellyfin.shelf
                            KIND_RECORDING -> RecordingShelf(graph.data.recordings)
                            else -> null
                        }
                    }
                    if (shelf == null || route.id == null) {
                        // The sample data's movies have nothing to play.
                        PendingScreen(route.title, backLabel = null)
                    } else {
                        LiveVodPlayer(
                            graph = graph,
                            shelf = shelf,
                            id = route.id,
                            fromStart = route.fromStart,
                            onBack = { nav.popBackStack() },
                            // The next episode takes this one's place, so Back goes to the series.
                            onEpisode = { next ->
                                nav.navigate(VodPlayerRoute(route.title, if (jellyfin) KIND_JELLYFIN else KIND_EPISODE, next)) { popUpTo<VodPlayerRoute> { inclusive = true } }
                            },
                            gone = {
                                PendingScreen(
                                    route.title,
                                    backLabel = null,
                                    message = when {
                                        jellyfin -> graph.jellyfin.problemOf(route.id)
                                        route.kind == KIND_RECORDING -> stringResource(R.string.recording_gone)
                                        else -> stringResource(R.string.movie_gone)
                                    },
                                )
                            },
                        )
                    }
                }
                composable<AppsRoute> {
                    // The TV's own apps, in the sample world too.
                    val appsNav = remember(nav) {
                        AppsNav(onTab = { nav.openTab(it) }, onSettings = { nav.navigate(SettingsRoute()) }, onProfile = { nav.navigate(ProfilesRoute) })
                    }
                    if (live) {
                        AppsHost(graph, names.profileName, names.profileName.take(1).uppercase(), appsNav)
                    } else {
                        AppsHost(graph, Sample.maya.name, Sample.maya.initial, appsNav)
                    }
                }
                composable<ProfilesRoute> {
                    val actions = remember(nav, live) {
                        ProfilesActions(
                            onPick = { profile ->
                                when {
                                    profile.pin -> nav.navigate(ProfilePinRoute(profile.id))
                                    // The profile watches, from its own Home (brief 3.4, I).
                                    live -> {
                                        graph.scope.launch { graph.data.profiles.choose(profile.id) }
                                        nav.goHome()
                                    }
                                    else -> nav.popBackStack()
                                }
                            },
                            onAdd = { nav.navigate(SettingsRoute(SettingsPage.Profiles.name, item = "add-profile")) },
                            onManage = { nav.navigate(SettingsRoute(SettingsPage.Profiles.name)) },
                        )
                    }
                    if (live) {
                        LiveProfiles(graph, actions)
                    } else {
                        ProfilesScreen(state = remember { ProfilesSamples.state }, actions = actions)
                    }
                }
                composable<RecordingsRoute> {
                    if (live) {
                        // What's recorded and coming up (brief 5.6).
                        LiveRecordings(
                            graph = graph,
                            onPlay = { nav.navigate(VodPlayerRoute(it.title, KIND_RECORDING, it.id)) },
                            onShowInGuide = { _, _ -> nav.navigate(GuideRoute) },
                        )
                    } else {
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
                                    onKeep = { id, program -> graph.keepAsRecording(id, program.id) },
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
                    val page = SettingsPage.entries.firstOrNull { it.name == route.page } ?: SettingsPage.Main
                    // Lock Settings, and Parental Controls' own page, ask for the PIN first (5.12).
                    val gateBack = if (page == SettingsPage.Parental) stringResource(DesignR.string.settings) else backLabel(nav)
                    if (!settingsOpened(graph, settings, page, gateBack)) return@composable
                    val context = LocalContext.current
                    val sources by graph.data.sources.sources.collectAsState(emptyList())
                    val counts by graph.data.channels.countsBySource.collectAsState(emptyMap())
                    val tests by graph.tests.collectAsState()
                    val summary by graph.channelSummary.collectAsState(ChannelSummary())
                    val sounds by graph.channelSounds.collectAsState(emptyList())
                    val apps by graph.apps.collectAsState()
                    val audio = audioFacts()
                    var homeChanges by remember { mutableIntStateOf(0) }
                    val home = rememberHomeScreenFacts(homeChanges)
                    val onHome = rememberHomeActions(graph) { homeChanges++ }
                    var screensaverChanges by remember { mutableIntStateOf(0) }
                    val tvScreensaver = rememberTvScreensaverFacts(screensaverChanges)
                    val onTvScreensaver = rememberTvScreensaverActions(graph) { screensaverChanges++ }
                    // Other apps' Play Next items for Continue Watching (5.19).
                    var otherAppsChanges by remember { mutableIntStateOf(0) }
                    val playNextAllowed = rememberPlayNextAllowed(graph, otherAppsChanges)
                    val onOtherApps = rememberOtherAppsActions(graph) { otherAppsChanges++ }
                    val playNext by graph.playNextRow.collectAsState()
                    val playNextShown by graph.playNextShown.collectAsState(emptyList())
                    val hiddenPlayNextApps by graph.data.settings.hiddenPlayNextApps.collectAsState(emptySet())
                    // The profile's Jellyfin server (5.19), and whether it may be asked to look for recordings (5.6).
                    val jellyfinAccount by graph.jellyfin.account.collectAsState()
                    val jellyfinShown by graph.jellyfin.shown.collectAsState(emptyList())
                    val jellyfinAdmin by graph.jellyfin.administrator.collectAsState()
                    // Where recordings are copied (4.34).
                    val networkFolder by graph.data.networkFolders.folder.collectAsState(null)
                    // Reminders over other apps (5.7).
                    var overAppsChanges by remember { mutableIntStateOf(0) }
                    val overApps = rememberRemindersOverApps(graph, overAppsChanges)
                    val onOverApps = rememberOverAppsActions(graph) { overAppsChanges++ }
                    // Plasma's own updates (5.18).
                    var updateChanges by remember { mutableIntStateOf(0) }
                    val update = rememberUpdateFacts(graph, updateChanges)
                    val onUpdate = rememberUpdateActions(graph) { updateChanges++ }
                    // The drive the rewind buffer goes to, as Save To has it (4.31).
                    val storage = remember(settings) { graph.storage() }
                    // Local catch-up's channels, and what it and the recordings take (4.31 and 5.5).
                    val world by graph.world.collectAsState()
                    val localIds by graph.data.settings.localCatchUp.collectAsState(emptySet())
                    val recordings by graph.data.recordings.recordings.collectAsState(emptyList())
                    val localBytes by produceState(0L, localIds, settings) { value = withContext(Dispatchers.IO) { graph.localCatchUpBytes() } }
                    val drives = LocalResources.current.getStringArray(SettingsR.array.save_to_options)
                    // Parental Controls (5.12): whether there's a PIN, the channels and groups locked, and the adult words.
                    val pinSet by graph.parental.pinSet.collectAsState()
                    val lockedChannels by graph.lockedChannels.collectAsState(emptyList())
                    val groupLocks by graph.groupLocks.collectAsState(null)
                    val adultWords by graph.data.settings.adultWords.collectAsState(null)
                    val kidWords by graph.data.settings.kidWords.collectAsState(null)
                    // Profiles (5.11): who they are, who's watching, and who watches when the TV wakes.
                    val profiles by graph.data.profiles.all.collectAsState(null)
                    val defaultProfile by graph.data.settings.defaultProfileId.collectAsState(null)
                    // The phone remote (5.21): its address and code while it runs, and the phones paired.
                    val phoneInvite by graph.phoneRemote.invite.collectAsState()
                    val phoneAvailable by graph.phoneRemote.available.collectAsState()
                    val phonesConnected by graph.phoneRemote.connected.collectAsState()
                    val phones by graph.data.settings.phones.collectAsState(emptyList())
                    SettingsScreen(
                        page = page,
                        values = settings,
                        actions = remember(nav, context, onHome, onTvScreensaver, onOtherApps, onOverApps, onUpdate) {
                            settingsActions(nav, context, graph, onHome, onTvScreensaver, onOtherApps, onOverApps, onUpdate)
                        },
                        facts = remember(
                            sources, counts, tests, summary, sounds, clock, audio, home, apps, tvScreensaver, playNextAllowed, playNext, playNextShown,
                            hiddenPlayNextApps, overApps, jellyfinAccount, jellyfinShown, update, storage, world, localIds, recordings, localBytes,
                            jellyfinAdmin, networkFolder, pinSet, lockedChannels, groupLocks, adultWords, kidWords, profiles, profileNow, defaultProfile,
                            phoneInvite, phoneAvailable, phonesConnected, phones,
                        ) {
                            settingsFacts(sources, counts, tests, summary, clock.zone, audio, sounds, home).let { facts ->
                                facts.copy(
                                    hiddenApps = apps.hiddenApps(),
                                    tvScreensaver = tvScreensaver ?: facts.tvScreensaver.copy(state = TvScreensaver.Unknown),
                                    playNextAllowed = playNextAllowed,
                                    playNextApps = playNextApps(playNext, hiddenPlayNextApps, graph::appName),
                                    playNextItems = playNextShown.size,
                                    jellyfinConnected = jellyfinAccount != null,
                                    jellyfinAdmin = jellyfinAdmin,
                                    jellyfinServer = jellyfinAccount?.let { JellyfinServerChoice(it.server, it.serverName).where }.orEmpty(),
                                    jellyfinUser = jellyfinAccount?.userName.orEmpty(),
                                    jellyfinItems = jellyfinShown.size,
                                    jellyfinAppInstalled = JellyfinApp.installed(context),
                                    pictureInPicture = graph.smallWindow.supported,
                                    remindersOverApps = overApps,
                                    version = graph.updates.version,
                                    update = update,
                                    driveName = drives[if (storage.onUsb) 0 else 1],
                                    storageOnTv = !storage.onUsb,
                                    driveFree = storage.free,
                                    driveTotal = storage.total,
                                    // Only what's on the drive: a recording After Copying let go of is on the network folder.
                                    recordingsBytes = recordings.filter { it.file != null }.sumOf { it.sizeBytes },
                                    localCatchUpBytes = localBytes,
                                    rewindBytes = storage.rewindBytes,
                                    localChannels = localNames(world, localIds),
                                    localOnPrimary = localIds.count { id -> world?.byId?.get(id)?.sourceId == sources.firstOrNull()?.id },
                                    localChoices = localChoices(world, localIds, sources),
                                    networkFolder = networkFolder?.shown.orEmpty(),
                                    networkFolderFree = networkFolder?.freeBytes ?: 0,
                                    pinSet = pinSet == true,
                                    // The sample world's until there's a source.
                                    lockedChannels = if (live) lockedChannels.map { LockedChannelInfo(it.id, it.number, it.name) } else facts.lockedChannels,
                                    lockedGroups = if (live) lockedGroups(groupLocks, sources) else facts.lockedGroups,
                                    adultWords = adultWords?.toString() ?: facts.adultWords,
                                    kidSafeGroups = if (live) kidSafeGroups(groupLocks, sources) else facts.kidSafeGroups,
                                    kidWords = kidWords?.toString() ?: facts.kidWords,
                                    profiles = if (live) profiles?.let(::profileInfos) ?: facts.profiles else facts.profiles,
                                    currentProfile = if (live) profileNow?.id ?: facts.currentProfile else facts.currentProfile,
                                    defaultProfile = if (live) defaultProfile else facts.defaultProfile,
                                    address = phoneInvite?.address?.substringBeforeLast(':') ?: facts.address,
                                    phonePort = phoneInvite?.address?.substringAfterLast(':')?.toIntOrNull() ?: facts.phonePort,
                                    phoneCode = phoneInvite?.code?.chunked(PHONE_CODE_GROUP)?.joinToString(" ").orEmpty(),
                                    phoneQr = phoneInvite?.qr,
                                    phoneAvailable = phoneAvailable,
                                    phoneStarting = phoneInvite == null,
                                    pairedPhones = phones.sortedByDescending { it.lastSeen }.map { phone ->
                                        PairedPhone(phone.name, phone.id in phonesConnected, Instant.ofEpochMilli(phone.lastSeen).atZone(clock.zone), phone.id)
                                    },
                                )
                            }
                        },
                        initialItem = route.item,
                        sourceId = route.source,
                        profileId = route.profile,
                    )
                }
                composable<PendingRoute> { entry ->
                    val route = entry.toRoute<PendingRoute>()
                    PendingScreen(route.title, backLabel = route.back)
                }
                composable<AddSourceRoute> { entry ->
                    val editId = entry.toRoute<AddSourceRoute>().editId
                    // A phone can fill the form in while it's open (brief 5.15).
                    val pairing = rememberPhonePairing(graph)
                    val phone by pairing.pairing.collectAsState()
                    val sent by pairing.sent.collectAsState()
                    if (editId == null) {
                        // A blank Xtream form, with this TV's MAC address ready for a portal: the
                        // board's half-filled portal is for the screenshot test.
                        val deviceMac by produceState<String?>(null) { value = graph.data.settings.deviceMac() }
                        deviceMac?.let { mac ->
                            AddSourceScreen(
                                state = remember(mac) { AddSourceState.blank(AddSourceType.Xtream, primary = !live, deviceMac = mac) },
                                actions = remember(nav) {
                                    AddSourceActions(
                                        onDone = { nav.popBackStack() },
                                        onType = pairing::setType,
                                        onConnect = { form -> connect(graph, form) },
                                    )
                                },
                                phone = phone,
                                sent = sent,
                                usualRefreshHours = graph.choices.refreshHours,
                            )
                        }
                    } else {
                        // Edit on a source (brief 4.20): its details, once they're read.
                        val editing by produceState<AddSourceState?>(null, editId) {
                            value = editState(graph, editId)
                            if (value == null) nav.popBackStack()
                        }
                        editing?.let { state ->
                            AddSourceScreen(
                                state = state,
                                actions = remember(nav, editId) {
                                    AddSourceActions(
                                        onDone = { nav.popBackStack() },
                                        onType = pairing::setType,
                                        onConnect = { form -> save(graph, editId, form) },
                                    )
                                },
                                phone = phone,
                                sent = sent,
                                usualRefreshHours = graph.choices.refreshHours,
                            )
                        }
                    }
                }
                composable<ReplaceHomeRoute> {
                    val context = LocalContext.current
                    val replacer = remember(context) { HomeReplacer(context, graph.scope, PhonePairingHost.debugTexts(context), graph::adbKey) }
                    DisposableEffect(replacer) {
                        replacer.start()
                        onDispose { replacer.stop() }
                    }
                    val progress by replacer.progress.collectAsState()
                    val overrideOn by HomeButton.running.collectAsState()
                    // The commands name this build's own Home activity, and this TV's launchers.
                    val homeActivity = "${context.packageName}/$HOME_ACTIVITY"
                    val launchers = remember(context) { HomeSetup.read(context).launchers.map { it.packageName } }
                    ReplaceHomeScreen(
                        state = replaceHomeState(progress, homeActivity, launchers, overrideOn),
                        actions = remember(replacer, nav) {
                            ReplaceHomeActions(
                                onOpenDeveloperOptions = { context.startActivitySafely(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
                                onReplace = { replacer.replace(homeActivity, launchers) },
                                onTurnOverrideOff = {
                                    HomeButton.turnOff()
                                    nav.popBackStack()
                                },
                                onDone = { nav.popBackStack() },
                            )
                        },
                    )
                }
                composable<KeyTestRoute> {
                    val learned by graph.keys.learned.collectAsState()
                    KeysAsTheyCome(graph)
                    KeyTestScreen(learned, backLabel = stringResource(SettingsR.string.remote), onBack = { nav.popBackStack() })
                }
                composable<LearnKeyRoute> {
                    val learned by graph.keys.learned.collectAsState()
                    KeysAsTheyCome(graph)
                    LearnKeyScreen(
                        learned = learned,
                        backLabel = stringResource(SettingsR.string.remote),
                        onLearn = { id, code -> graph.scope.launch { graph.data.settings.learnKey(id, code) } },
                        onForget = { id -> graph.scope.launch { graph.data.settings.forgetKey(id) } },
                    )
                }
                composable<DuplicateNumbersRoute> {
                    if (live) {
                        val state by graph.duplicateNumbers.collectAsState(null)
                        state?.let { DuplicateNumbersScreen(it, actions = remember(graph) { duplicateNumbersActions(graph) }) }
                    } else {
                        DuplicateNumbersScreen(remember { DuplicateNumbersSamples.state })
                    }
                }
                composable<ChoosePinRoute> { entry ->
                    val route = entry.toRoute<ChoosePinRoute>()
                    val back = stringResource(
                        when {
                            route.lockChannel != null -> DesignR.string.tab_live_tv
                            route.profile != null -> SettingsR.string.profiles
                            else -> SettingsR.string.parental
                        },
                    )
                    ChoosePin(graph, settings, route, back) { nav.popBackStack() }
                }
                composable<UnlockChannelRoute> { entry ->
                    UnlockChannel(graph, entry.toRoute<UnlockChannelRoute>().channelId, stringResource(DesignR.string.tab_live_tv)) { nav.popBackStack() }
                }
                composable<UnlockCategoryRoute> { entry ->
                    val route = entry.toRoute<UnlockCategoryRoute>()
                    val back = stringResource(if (route.series) DesignR.string.tab_series else DesignR.string.tab_movies)
                    UnlockCategory(graph, route.categoryId, route.name, back) { nav.popBackStack() }
                }
                composable<ProfilePinRoute> { entry ->
                    val profileId = entry.toRoute<ProfilePinRoute>().profileId
                    val back = stringResource(SettingsR.string.profiles)
                    if (live) {
                        ProfilePin(graph, profileId, back) { nav.goHome() }
                        return@composable
                    }
                    val profile = remember(entry) { Sample.profiles.first { it.id == profileId } }
                    ParentalPinScreen(
                        request = PinRequest.Profile(profile.name),
                        backLabel = back,
                        checkPin = { it == ProfilesSamples.PIN },
                        onUnlocked = { nav.popBackStack(ProfilesRoute, inclusive = true) },
                    )
                }
                composable<PlayerRoute> { entry ->
                    val route = entry.toRoute<PlayerRoute>()
                    val channelId = route.channelId
                    // The row under the overlay's controls has the Guide while the top bar does.
                    val showGuide = remember(entry) { graph.choices.showGuide }
                    val actions = remember(nav, showGuide) {
                        PlayerActions(
                            onBack = { nav.popBackStack() },
                            onChannelList = { nav.navigate(LiveTvRoute) },
                            onMultiview = { nav.navigate(MultiviewRoute) },
                            onSearch = { nav.navigate(SearchRoute()) },
                            onGuide = if (showGuide) ({ nav.navigate(GuideRoute) }) else null,
                            onOpenChannel = { id -> nav.navigate(PlayerRoute(id, Lists.ALL)) { popUpTo<PlayerRoute> { inclusive = true } } },
                        )
                    }
                    if (live) {
                        // A locked channel asks for the PIN first, zapped onto too (5.12).
                        LivePlayer(graph, states, channelId, route.listId ?: Lists.ALL, route.from?.let(Instant::ofEpochSecond), actions, backLabel(nav))
                        return@composable
                    }
                    val locked = remember(channelId) { Sample.channels.firstOrNull { it.id == channelId && it.locked } }
                    if (locked != null && !unlocks.isUnlocked(channelId)) {
                        ParentalPinScreen(
                            request = remember(locked) { ProfilesSamples.lockedProgram(locked) },
                            backLabel = backLabel(nav),
                            checkPin = { it == ProfilesSamples.PIN },
                            onUnlocked = { unlocks.unlock(channelId, Sample.nowOn(locked)?.end?.toInstant(), it) },
                            unlockFor = graph.unlockFor,
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
                    // Live tiles once there's a source (5.8); the board's stills until then.
                    if (live) {
                        LiveMultiview(graph) { channelId, listId -> nav.fullScreenFromMultiview(PlayerRoute(channelId, listId)) }
                        return@composable
                    }
                    MultiviewScreen(
                        state = remember { PlayerSamples.multiview },
                        actions = remember(nav) {
                            MultiviewActions(onFullScreen = { channelId -> nav.fullScreenFromMultiview(PlayerRoute(channelId)) })
                        },
                    )
                }
            }
        }
        if (live && entry?.destination?.hasRoute<PlayerRoute>() != true) {
            ReminderPopup(graph, states) { channelId -> nav.navigate(PlayerRoute(channelId, Lists.ALL)) }
        }
        NoticePopup(graph)
        SleepPopup(graph)
        // Google TV Home turned back on by a TV update, where Plasma had replaced it (5.13).
        HomeCameBackPopup(graph) { choice ->
            when (choice) {
                HomeBackChoice.ReplaceAgain -> nav.navigate(ReplaceHomeRoute)
                HomeBackChoice.UseOverride -> nav.navigate(SettingsRoute(SettingsPage.HomeScreen.name, item = "override"))
            }
        }
    }
}

/**
 * Sleep Timer over any screen (brief 4.10): in its last minute, the notice with Keep Watching and
 * Stop Now, which Back puts away; then, once it has stopped the channel, the dark screen, which
 * any button wakes. Each is a window of its own, so the screen underneath keeps its focus.
 */
@Composable
private fun SleepPopup(graph: AppGraph) {
    val state by graph.sleep.state.collectAsState()
    // The notice put away with Back, for the time it was about.
    var putAway by remember { mutableStateOf<Instant?>(null) }
    when (val sleep = state) {
        is SleepState.Set -> if (sleep.warning && putAway != sleep.at) {
            Popup(onDismissRequest = { putAway = sleep.at }, properties = PopupProperties(focusable = true)) {
                PhoneKeysHere(graph)
                Box(Modifier.fillMaxSize()) {
                    SleepToast(onKeepWatching = graph.sleep::keepWatching, onStopNow = graph.sleep::sleepNow)
                }
            }
        }
        SleepState.Asleep -> Popup(onDismissRequest = graph.sleep::wake, properties = PopupProperties(focusable = true)) {
            PhoneKeysHere(graph)
            AsleepScreen(onWake = graph.sleep::wake)
        }
        SleepState.Off -> Unit
    }
}

/**
 * The latest notice, at the top right for four seconds over any screen (brief 4.35). It takes no
 * keys: the screen underneath keeps them.
 */
@Composable
private fun NoticePopup(graph: AppGraph) {
    var shown by remember { mutableStateOf<Notice?>(null) }
    val resources = LocalResources.current
    LaunchedEffect(graph) {
        // Plasma just updated itself (5.18): it says so first.
        val updated = graph.updates.takeUpdated()?.let { Notice(resources.getString(R.string.updated_to, it), PlasmaIcons.Download) }
        (if (updated != null) merge(flowOf(updated), graph.notices) else graph.notices).collectLatest { notice ->
            shown = notice
            delay(notice.millis)
            shown = null
        }
    }
    val notice = shown ?: return
    Popup(properties = PopupProperties(focusable = false)) {
        Box(Modifier.fillMaxSize()) {
            NoticeToast(notice.text, notice.icon)
        }
    }
}

/**
 * Add a Source's local server for phones (brief 5.15): it answers while the screen is on the TV,
 * and stops when the screen closes or Plasma leaves the front.
 */
@Composable
private fun rememberPhonePairing(graph: AppGraph): PhonePairingHost {
    val context = LocalContext.current
    val host = remember(graph) { PhonePairingHost(graph.scope, PhonePairingHost.texts(context)) }
    LifecycleStartEffect(host) {
        host.start()
        onStopOrDispose { host.close() }
    }
    return host
}

/** The key test and Learn a Key need keys as the remote sends them, not as learned keys act. */
@Composable
private fun KeysAsTheyCome(graph: AppGraph) {
    DisposableEffect(graph) {
        graph.keys.asTheyCome = true
        onDispose { graph.keys.asTheyCome = false }
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
        PhoneKeysHere(graph)
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

/** [PlasmaTheme] with Settings › Appearance's accent, Text Size and Reduce Motion, as they change (brief 5.16). */
@Composable
fun PlasmaAppTheme(graph: AppGraph, content: @Composable () -> Unit) {
    val stored by graph.storedChoices.collectAsState()
    val choices = SettingsChoices(stored.orEmpty())
    PlasmaTheme(
        accent = PlasmaColors.accent(choices.accent),
        reduceMotion = choices.reduceMotion,
        textScale = if (choices.largeText) LARGE_TEXT_SCALE else 1f,
        content = content,
    )
}

/**
 * Everything a screen expects around it: the theme, the clock, images (the sample art, and URLs
 * through Coil), the video layer, and live video once there's a source.
 */
@Composable
private fun AppHost(graph: AppGraph, live: Boolean, clock: PlasmaClock, tabs: List<TopTab>, content: @Composable () -> Unit) {
    val video = remember { VideoLayerState() }
    val images = remember(graph) { AppImages(graph.images) }
    val update by graph.updates.state.collectAsState()
    // A guest can't manage recordings (5.11).
    val profile by graph.data.profiles.current.collectAsState(null)
    PlasmaAppTheme(graph) {
        CompositionLocalProvider(
            LocalPlasmaClock provides clock,
            LocalImageResolver provides images,
            LocalVideoLayer provides video,
            LocalLiveVideo provides if (live) graph.player else null,
            LocalTopTabs provides tabs,
            // An update waits in Settings › About (5.18).
            LocalSettingsBadge provides ((update as? UpdateState.Ready)?.problem == null && update is UpdateState.Ready),
            // The phone remote sees the field taking text and the colour keys' labels (5.21).
            LocalRemoteMirror provides graph.remoteMirror,
            LocalCanRecord provides (!live || profile?.kind != ProfileKind.Guest),
        ) {
            if (live) KeepScreenOnWhilePlaying(graph)
            VideoHost(video, content)
        }
    }
}

/**
 * The TV's own screensaver would stop Plasma, and the channel with it (brief 5.20), so the screen
 * stays on while live video opens or plays, and while Multiview's tiles do. Plasma's screensaver
 * still comes after ten idle minutes away from the player.
 */
@Composable
private fun KeepScreenOnWhilePlaying(graph: AppGraph) {
    val status by graph.player.status.collectAsState()
    val held by graph.screenHeld.collectAsState()
    val view = LocalView.current
    val playing = status is LiveStatus.Opening || status is LiveStatus.Playing || status is LiveStatus.Reconnecting || held > 0
    DisposableEffect(view, playing) {
        view.keepScreenOn = playing
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Sample art by its `fixture://` name, apps' banners and icons from the apps themselves, and
 * channel logos and other pictures from the network.
 */
internal class AppImages(private val loader: ImageLoader) : ImageResolver {
    @Composable
    override fun painter(model: String): Painter? = when {
        model.startsWith(FixtureImages.SCHEME) -> FixtureImages.resolver.painter(model)
        AppArt.isAppArt(model) -> appArt(model)
        else -> rememberAsyncImagePainter(model, loader)
    }
}

/** An app's banner or icon (brief 4.4), read from the app off the main thread the first time. */
@Composable
private fun appArt(model: String): Painter? {
    val context = LocalContext.current
    val read = remember(model) { mutableStateOf(AppArt.cached(model)) }
    LaunchedEffect(model) {
        if (read.value == null) read.value = withContext(Dispatchers.IO) { AppArt.load(context, model) }
    }
    val bitmap = read.value
    return remember(bitmap) { bitmap?.let { BitmapPainter(it.asImageBitmap()) } }
}

/** What a [VodPlayerRoute] plays. */
private const val KIND_MOVIE = StreamKeys.KIND_MOVIE
private const val KIND_EPISODE = StreamKeys.KIND_EPISODE
private const val KIND_JELLYFIN = StreamKeys.KIND_JELLYFIN
private const val KIND_RECORDING = StreamKeys.KIND_RECORDING

private const val MINUTE_MS = 60_000L

/** A channel tapped on a phone opens if Plasma comes forward within this long (5.21). */
private const val PHONE_TUNE_MS = 15_000L

/** The phone remote's code in groups of three, as the TV shows it: "7KQ 4MZ" (4.33). */
private const val PHONE_CODE_GROUP = 3

/** Plasma's Home activity, as Android and the commands for a computer name it (brief 5.13). */
private val HOME_ACTIVITY = "${MainActivity::class.java.name.substringBeforeLast('.')}.HomeActivity"

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
    onTitle = { nav.navigate(titleRoute(it)) },
    onChannel = { nav.navigate(PlayerRoute(it.id)) },
)

private fun vodBrowseActions(nav: NavHostController, series: Boolean, onOpen: (String) -> Unit) = VodBrowseActions(
    onTab = { nav.openTab(it) },
    onSettings = { nav.navigate(SettingsRoute()) },
    onProfile = { nav.navigate(ProfilesRoute) },
    onOpen = onOpen,
    // A locked category opens with the PIN (5.12).
    onUnlock = { categoryId, name -> nav.navigate(UnlockCategoryRoute(categoryId, name, series)) },
)

/**
 * Settings' pages open over each other. The profile editor comes with profiles (M7).
 */
private fun settingsActions(
    nav: NavHostController,
    context: Context,
    graph: AppGraph,
    onHome: (HomeAction) -> Unit,
    onTvScreensaver: (TvScreensaverAction) -> Unit,
    onOtherApps: (OtherAppsAction) -> Unit,
    onOverApps: (OverAppsAction) -> Unit,
    onUpdate: (UpdateAction) -> Unit,
) = SettingsActions(
    onOpen = { nav.navigate(SettingsRoute(it.name)) },
    onSource = { sourceId, action ->
        when (action) {
            SourceAction.Edit -> nav.navigate(AddSourceRoute(editId = sourceId))
            SourceAction.RefreshNow -> graph.refresh(sourceId)
            SourceAction.SetPrimary -> graph.setPrimary(sourceId)
            SourceAction.Test -> graph.testSource(sourceId)
            SourceAction.Playback -> nav.navigate(SettingsRoute(SettingsPage.SourcePlayback.name, source = sourceId))
            SourceAction.Disable -> graph.setEnabled(sourceId, false)
            SourceAction.Enable -> graph.setEnabled(sourceId, true)
            SourceAction.Delete -> graph.scope.launch { graph.data.sources.remove(sourceId) }
        }
    },
    onForgetPlayers = graph::forgetPlayers,
    onResetSound = graph::resetSound,
    onSourceText = graph::typeForSource,
    onHome = onHome,
    onShowApp = { graph.setAppHidden(it, hidden = false) },
    onTvScreensaver = onTvScreensaver,
    onOtherApps = onOtherApps,
    jellyfin = graph.jellyfin,
    onForgetJellyfin = graph.jellyfin::forget,
    onOverApps = onOverApps,
    onUpdate = onUpdate,
    onLocalCatchUp = { channelId, on -> graph.scope.launch { graph.setLocalCatchUp(channelId, on) } },
    onClearLocal = graph::clearLocalCatchUp,
    networkFolder = graph.networkFolder,
    onForgetNetworkFolder = graph::forgetNetworkFolder,
    onGo = { destination ->
        fun pending(@StringRes title: Int, @StringRes back: Int) = PendingRoute(context.getString(title), context.getString(back))
        nav.navigate(
            when (destination) {
                SettingsDestination.DuplicateNumbers -> DuplicateNumbersRoute
                SettingsDestination.ReplaceHome -> ReplaceHomeRoute
                SettingsDestination.AddSource -> AddSourceRoute()
                SettingsDestination.Profiles -> pending(R.string.edit_profile, SettingsR.string.profiles)
                SettingsDestination.KeyTest -> KeyTestRoute
                SettingsDestination.LearnKey -> LearnKeyRoute
                SettingsDestination.SetPin -> ChoosePinRoute()
                SettingsDestination.ChangePin -> ChoosePinRoute(change = true)
                SettingsDestination.LockSettings -> ChoosePinRoute(lockSettings = true)
            },
        )
    },
    onLockChannel = { channelId, locked -> graph.scope.launch { graph.data.channels.setLocked(channelId, locked) } },
    onTurnOffParental = { graph.scope.launch { graph.parental.setPin(null) } },
    onLockGroup = { groupId, locked -> graph.scope.launch { graph.data.channels.setGroupLocked(groupId, locked) } },
    onAdultWords = { text -> graph.scope.launch { graph.data.settings.setAdultWords(text) } },
    onKidSafeGroup = { groupId, kidSafe -> graph.scope.launch { graph.data.channels.setGroupKidSafe(groupId, kidSafe) } },
    onKidWords = { text -> graph.scope.launch { graph.data.settings.setKidWords(text) } },
    onProfile = { profileId -> nav.navigate(SettingsRoute(SettingsPage.Profile.name, profile = profileId)) },
    onAddProfile = { name -> graph.scope.launch { graph.addProfile(name) } },
    onProfileName = { profileId, name ->
        graph.scope.launch {
            graph.data.profiles.get(profileId)?.let { graph.data.profiles.update(profileId, name, it.avatar, it.kind, it.maxAge) }
        }
    },
    onProfilePin = { profileId -> nav.navigate(ChoosePinRoute(profile = profileId)) },
    onRemoveProfilePin = { profileId -> graph.scope.launch { graph.data.profiles.setPin(profileId, null) } },
    onDeleteProfile = { profileId ->
        graph.scope.launch { graph.data.profiles.delete(profileId) }
        nav.popBackStack()
    },
    // The phone remote's paired phones (5.21).
    onRenamePhone = { phoneId, name -> graph.phoneRemote.rename(phoneId, name) },
    onForgetPhone = { phoneId -> graph.phoneRemote.forget(phoneId) },
)

/**
 * Locked Groups (5.12): every group, whether its channels need the PIN, and whether it's adult;
 * with more than one source, each says which it's from.
 */
private fun lockedGroups(locks: GroupLocks?, sources: List<Source>): List<LockedGroupInfo> {
    locks ?: return emptyList()
    val names = sources.associate { it.id to it.name }
    return locks.groups.map { group ->
        LockedGroupInfo(group.id, group.name, locks.isLocked(group), locks.isAdult(group), source = names[group.sourceId]?.takeIf { sources.size > 1 })
    }
}

/**
 * Kid-Safe Groups (5.11): the groups that aren't locked, whether kids profiles see them, and
 * whether that's by Kids Words; with more than one source, each says which it's from.
 */
private fun kidSafeGroups(locks: GroupLocks?, sources: List<Source>): List<KidSafeGroupInfo> {
    locks ?: return emptyList()
    val names = sources.associate { it.id to it.name }
    return locks.groups.filter { !locks.isLocked(it) }.map { group ->
        KidSafeGroupInfo(group.id, group.name, locks.isKidSafe(group), locks.hasKidWord(group), source = names[group.sourceId]?.takeIf { sources.size > 1 })
    }
}

/** Each choice on Duplicate Numbers is kept, so it survives refreshes (brief 4.30). */
private fun duplicateNumbersActions(graph: AppGraph) = DuplicateNumbersActions(
    onKeep = { conflict -> graph.changeNumbers { keep(conflict.id) } },
    onChoose = { conflict, number -> graph.changeNumbers { choose(conflict.id, number) } },
    onSwap = { conflict -> graph.changeNumbers { swap(conflict.id) } },
    onRule = { rule -> graph.changeNumbers { setRule(rule) } },
    onUnmerge = { channel -> graph.changeNumbers { unmerge(channel.id) } },
)

private fun homeActions(nav: NavHostController, graph: AppGraph) = HomeActions(
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
    onApp = graph::openApp,
    onAllApps = { nav.openTab(TopTab.Apps) },
)

/**
 * A Continue Watching item opens by its kind (`kind:id…`): a source's movie or episode plays, as
 * does a Jellyfin server's (`vod:jellyfin:…`, 5.19); a sample series or movie opens its page,
 * catch-up its channel. Other apps' items open in their apps before they get here; the sample
 * row's Jellyfin movie opens Plasma's page for it.
 */
private fun NavHostController.openContinue(item: String) {
    // A movie or episode from a source or a Jellyfin server plays from where it was left (brief 5.9).
    StreamKeys.vodOf(item)?.let { (kind, id) ->
        navigate(VodPlayerRoute("", kind, id))
        return
    }
    val (kind, id) = item.split(':').let { it[0] to it.getOrElse(1) { "" } }
    when (kind) {
        "series" -> navigate(SeriesDetailRoute(id))
        "catchup" -> navigate(PlayerRoute(id))
        else -> navigate(MovieDetailRoute(id))
    }
}

/** Home, with everything that was over it gone: where a profile picked on Who's Watching lands (brief 3.4, I). */
private fun NavHostController.goHome() {
    popBackStack(HomeRoute, inclusive = false)
}

/**
 * Full Screen from Multiview (brief 4.12): the player on the tile's channel, in place of Multiview,
 * and of the player Multiview was opened from, so Back doesn't go to the channel before.
 */
private fun NavHostController.fullScreenFromMultiview(route: PlayerRoute) {
    val fromPlayer = previousBackStackEntry?.destination?.hasRoute<PlayerRoute>() == true
    navigate(route) {
        if (fromPlayer) popUpTo<PlayerRoute> { inclusive = true } else popUpTo(MultiviewRoute) { inclusive = true }
    }
}

/** What shows when the TV comes back on (Settings › Launcher & Home, brief 5.13). */
private fun NavHostController.afterTvTurnsOn(choice: TvTurnsOn, last: LastWatched?) {
    when (choice) {
        TvTurnsOn.LastChannel -> {
            // Already on the player, in Multiview, or watching a movie: it plays again where it is.
            val playing = currentDestination?.let { it.hasRoute<PlayerRoute>() || it.hasRoute<MultiviewRoute>() || it.hasRoute<VodPlayerRoute>() } == true
            if (last == null || playing) return
            navigate(PlayerRoute(last.channelId, last.listId)) { popUpTo(HomeRoute) }
        }
        TvTurnsOn.Home -> popBackStack(HomeRoute, inclusive = false)
        TvTurnsOn.Guide -> openTab(TopTab.Guide)
    }
}

/**
 * What opens when Home is pressed (Settings › Launcher & Home › Home Button, brief 4.22): Plasma
 * Home, the last channel full screen, Live TV's channel list or the Guide.
 */
private fun NavHostController.afterHomeButton(choice: HomeButtonChoice, last: LastWatched?) {
    when (choice) {
        HomeButtonChoice.PlasmaHome -> popBackStack(HomeRoute, inclusive = false)
        HomeButtonChoice.LastChannel -> when {
            // Nothing watched yet: Home it is.
            last == null -> popBackStack(HomeRoute, inclusive = false)
            currentDestination?.hasRoute<PlayerRoute>() == true -> Unit
            else -> navigate(PlayerRoute(last.channelId, last.listId)) { popUpTo(HomeRoute) }
        }
        HomeButtonChoice.ChannelList -> openTab(TopTab.LiveTv)
        HomeButtonChoice.Guide -> openTab(TopTab.Guide)
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
