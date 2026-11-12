// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ca.gureet.plasma.backup.AfterRestoreChecks
import ca.gureet.plasma.core.data.ChannelGroup
import ca.gureet.plasma.feature.settings.PickChoice
import ca.gureet.plasma.core.data.LastWatched
import ca.gureet.plasma.core.data.Playable
import ca.gureet.plasma.core.data.ProfileChoices
import ca.gureet.plasma.core.data.ProfileKind
import ca.gureet.plasma.core.data.Source
import ca.gureet.plasma.core.design.KeyChoices
import ca.gureet.plasma.core.design.LARGE_TEXT_SCALE
import ca.gureet.plasma.core.design.LocalCanRecord
import ca.gureet.plasma.core.design.LocalKeyChoices
import ca.gureet.plasma.core.design.LocalLeaving
import ca.gureet.plasma.core.design.LocalShowChannelNumbers
import ca.gureet.plasma.core.design.Overlay
import ca.gureet.plasma.core.design.LocalRemoteMirror
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaTheme
import ca.gureet.plasma.core.design.SettlingScreen
import ca.gureet.plasma.core.design.rememberLast
import ca.gureet.plasma.core.design.component.LocalImageResolver
import ca.gureet.plasma.core.design.component.LocalLaunchGate
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.design.component.LocalTopTabs
import ca.gureet.plasma.core.design.component.LocalSettingsBadge
import ca.gureet.plasma.core.design.component.NoticeToast
import ca.gureet.plasma.core.design.component.ReminderToast
import ca.gureet.plasma.core.design.component.SizedImageResolver
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.design.component.rememberNow
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.fixtures.FixtureImages
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.model.ChannelUpGoesTo
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.core.model.HomeButtonChoice
import ca.gureet.plasma.core.model.HomeRow
import ca.gureet.plasma.core.model.LongPressOk
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.core.model.ShiftedClock
import ca.gureet.plasma.core.model.TvTurnsOn
import ca.gureet.plasma.core.model.UpDownInPlayer
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
import ca.gureet.plasma.feature.settings.HiddenGroupInfo
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
import ca.gureet.plasma.live.withCount
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
import ca.gureet.plasma.live.addSource
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
import ca.gureet.plasma.nav.SetupRoute
import ca.gureet.plasma.nav.ChoosePinRoute
import ca.gureet.plasma.nav.ForgotPinRoute
import ca.gureet.plasma.nav.RecoveryCodeRoute
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
import ca.gureet.plasma.ui.OverlayPopup
import ca.gureet.plasma.ui.PendingScreen
import coil3.ImageLoader
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale
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
    val firstRun by graph.firstRun.collectAsState()
    val storedRows by graph.homeRowOrder.collectAsState()
    val live = hasSources ?: return
    val chosen = stored ?: return
    val rowOrder = storedRows ?: return
    // First-time setup opens instead of Home until it's finished or skipped (#85). Decided as the
    // screen opens: a source added during setup doesn't take it away.
    val opensSetup = firstRun?.opensSetup ?: return
    val startsInSetup = remember { opensSetup }
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
    val profileNow by graph.currentProfile.collectAsState()
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
    // Live TV Details (Settings › TV Guide, the owner's choice of 30 October): whose schedule shows under Live TV's picture.
    val liveDetails = SettingsChoices(chosen).liveDetails
    // Channel List Shows (Settings › TV Guide, the owner's #137): what Live TV's rows lead with.
    val liveListShows = SettingsChoices(chosen).liveListShows
    // Home Rows (Settings › Launcher & Home, the owner's #80): Home's rows in their order, those on; Apps only with Show Android Apps.
    // Kept from one recomposition to the next while they're the same, so the screens below, and the
    // app's navigation, aren't made again each time something here changes (Snappier).
    val homeRows = remember(chosen, rowOrder) { SettingsChoices(chosen).homeRows(rowOrder) }
    val tabs = remember(showApps, showGuide) { TopTab.entries.filter { (it != TopTab.Apps || showApps) && (it != TopTab.Guide || showGuide) } }
    AppHost(graph, live, clock, tabs = tabs) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        // Forgot PIN, from whichever PIN screen asks (#112).
        DisposableEffect(graph, nav) {
            graph.openForgotPin = { route -> nav.navigate(route) }
            onDispose { graph.openForgotPin = null }
        }
        // Settings opened with the PIN locks again at Home (5.12).
        val atHome = entry?.destination?.hasRoute<HomeRoute>() == true
        LaunchedEffect(atHome) { if (atHome) graph.parental.settingsOpen = false }
        // The TV turned off and on again: When the TV Turns On decides what shows (brief 5.13 and 5.20).
        LaunchedEffect(nav, live) {
            graph.standby.turnedOn.collect {
                // First-time setup stays on screen until it's done (#85).
                if (nav.inSetup()) return@collect
                // Who's Watching first, when it's asked (5.11); its pick lands on Home.
                if (live && !whoIsWatching(graph, nav)) nav.afterTvTurnsOn(graph.choices.tvTurnsOn, graph.data.channels.lastWatched.first())
            }
        }
        // After a restore, what its settings need of Android is asked for (#141).
        AfterRestoreChecks(graph)
        // Recordings' files that aren't Plasma's to delete: Android asks first (#161).
        RecordingDeleteRequests(graph)
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
            // Home keeps first-time setup on screen, and the screen opened from it, until it's done:
            // Plasma may be the TV's Home already (#85).
            if (nav.inSetup()) {
                graph.handled(now)
                return@LaunchedEffect
            }
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
        // The launch screen goes once the first screen has what it shows (#158): with a source,
        // once the channels are in, and a channel at once; the sample world and setup as they open.
        val launchGate = LocalLaunchGate.current
        LaunchedEffect(launchGate, entry != null, watching, live) {
            if (launchGate.isReady || entry == null) return@LaunchedEffect
            if (watching) {
                launchGate.readyNow()
                return@LaunchedEffect
            }
            if (live) graph.world.first { it != null }
            launchGate.ready()
        }
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
            // A 200 ms crossfade between screens; the one coming in settles upward ([screen]).
            NavHost(
                navController = nav,
                startDestination = if (startsInSetup) SetupRoute() else HomeRoute,
                enterTransition = { fadeIn(PlasmaMotion.screen()) },
                exitTransition = { fadeOut(PlasmaMotion.screen()) },
                popEnterTransition = { fadeIn(PlasmaMotion.screen()) },
                popExitTransition = { fadeOut(PlasmaMotion.screen()) },
            ) {
                screen<HomeRoute> {
                    val actions = remember(nav, graph, live) { homeActions(nav, graph, live) }
                    if (live) {
                        LiveHome(graph, states, actions, showApps, showGuide, homeRows)
                    } else {
                        // The sample world, with the TV's own apps in the Apps row.
                        val apps by graph.apps.collectAsState()
                        HomeScreen(
                            state = remember(apps, showApps, showGuide, homeRows) { HomeSamples.state.withApps(apps, showApps).copy(showGuide = showGuide, rows = homeRows) },
                            actions = actions,
                        )
                    }
                }
                // First-time setup (#85), at the first start or from Settings › About.
                screen<SetupRoute> { entry ->
                    SetupHost(graph, settings, nav, again = entry.toRoute<SetupRoute>().again)
                }
                screen<SearchRoute> { entry ->
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
                screen<LiveTvRoute> {
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
                        LiveLiveTv(graph, states, actions, liveDetails, liveListShows)
                    } else {
                        LiveTvScreen(state = remember { LiveTvSamples.state }, actions = actions, details = liveDetails, listShows = liveListShows)
                    }
                }
                screen<GuideRoute> {
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
                screen<MoviesRoute> {
                    val actions = remember(nav) { vodBrowseActions(nav, series = false) { nav.navigate(MovieDetailRoute(it)) } }
                    if (live) {
                        LiveVodBrowse(graph, VodKind.Movies, names.profileName, actions)
                    } else {
                        VodBrowseScreen(state = remember { VodSamples.movies }, actions = actions)
                    }
                }
                screen<MovieDetailRoute> { entry ->
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
                screen<SeriesRoute> {
                    val actions = remember(nav) { vodBrowseActions(nav, series = true) { nav.navigate(SeriesDetailRoute(it)) } }
                    if (live) {
                        LiveVodBrowse(graph, VodKind.Series, names.profileName, actions)
                    } else {
                        VodBrowseScreen(state = remember { VodSamples.series }, actions = actions)
                    }
                }
                screen<SeriesDetailRoute> { entry ->
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
                screen<AppsRoute> {
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
                screen<ProfilesRoute> {
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
                screen<RecordingsRoute> {
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
                screen<CatchUpRoute> { entry ->
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
                        return@screen
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
                screen<SettingsRoute> { entry ->
                    val route = entry.toRoute<SettingsRoute>()
                    val page = SettingsPage.entries.firstOrNull { it.name == route.page } ?: SettingsPage.Main
                    // Opened from first-time setup: this TV's own profiles, as setup shows them (#85).
                    val own = live || route.back != null
                    // Lock Settings, and Parental Controls' own page, ask for the PIN first (5.12).
                    val gateBack = route.back ?: if (page == SettingsPage.Parental) stringResource(DesignR.string.settings) else backLabel(nav)
                    if (!settingsOpened(graph, settings, page, gateBack)) return@screen
                    // About › Reset Settings, with parental controls on: the PIN first (#159).
                    var resetPin by remember { mutableStateOf(false) }
                    if (resetPin) {
                        ResetSettingsPin(graph, stringResource(SettingsR.string.about), onRight = {
                            resetPin = false
                            graph.resetSettings()
                        }, onBack = { resetPin = false })
                        return@screen
                    }
                    val onResetSettings = remember(graph) { { if (resetNeedsPin(graph)) resetPin = true else graph.resetSettings() } }
                    val context = LocalContext.current
                    // Kept by the app, so the page opens on them (docs/decisions.md, Snappier).
                    val sources = graph.sourcesNow.collectAsState().value.orEmpty()
                    val counts = graph.channelCountsNow.collectAsState().value.orEmpty()
                    val library = graph.libraryNow.collectAsState().value.orEmpty()
                    val loading by graph.loading.collectAsState()
                    val tests by graph.tests.collectAsState()
                    val summary by graph.channelSummary.collectAsState()
                    val sounds by graph.channelSounds.collectAsState()
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
                    // Find Recordings, after asking Android where it has to (#161).
                    val onFindRecordings = rememberFindRecordings(graph)
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
                    val lockedChannels by graph.lockedChannels.collectAsState()
                    val groupLocks by graph.groupLocks.collectAsState(null)
                    // Channels › Hidden Channels and Hidden Groups (4.29).
                    val hiddenChannels by graph.hiddenChannels.collectAsState()
                    val adultWords by graph.data.settings.adultWords.collectAsState(null)
                    val kidWords by graph.data.settings.kidWords.collectAsState(null)
                    // Profiles (5.11): who they are, who's watching, and who watches when the TV wakes.
                    val profiles by graph.profilesNow.collectAsState()
                    val defaultProfile by graph.data.settings.defaultProfileId.collectAsState(null)
                    // The phone remote (5.21): its address and code while it runs, and the phones paired.
                    val phoneInvite by graph.phoneRemote.invite.collectAsState()
                    val phoneAvailable by graph.phoneRemote.available.collectAsState()
                    val phonesConnected by graph.phoneRemote.connected.collectAsState()
                    val phones by graph.data.settings.phones.collectAsState(emptyList())
                    // Who's watching, read here rather than taken from above, so a channel played
                    // (which the profile remembers) doesn't make the app's navigation again.
                    val watching by graph.currentProfile.collectAsState()
                    // What goes through every channel, group or recording, or asks Android, is worked
                    // out only when what it's made from changes, not each time one of the page's
                    // many facts arrives as it opens (docs/decisions.md, Snappier).
                    val jellyfinAppInstalled = rememberJellyfinAppInstalled()
                    val playNextAppsNow = remember(playNext, hiddenPlayNextApps) { playNextApps(playNext, hiddenPlayNextApps, graph::appName) }
                    val recordingsBytes = remember(recordings) { recordings.filter { it.file != null }.sumOf { it.sizeBytes } }
                    val everyChannel = world?.channels
                    val localNamesNow = remember(everyChannel, localIds) { localNames(world, localIds) }
                    val localOnPrimary = remember(everyChannel, localIds, sources) { localIds.count { id -> world?.byId?.get(id)?.sourceId == sources.firstOrNull()?.id } }
                    val localChoicesNow = remember(everyChannel, localIds, sources) { localChoices(world, localIds, sources) }
                    val lockedChannelsNow = remember(lockedChannels) { lockedChannels.map { LockedChannelInfo(it.id, it.number, it.name) } }
                    val lockedGroupsNow = remember(groupLocks, sources) { lockedGroups(groupLocks, sources) }
                    val kidSafeGroupsNow = remember(groupLocks, sources) { kidSafeGroups(groupLocks, sources) }
                    val hiddenChannelsNow = remember(hiddenChannels) { hiddenChannels.map { LockedChannelInfo(it.id, it.number, it.name) } }
                    val hiddenGroupsNow = remember(groupLocks, sources) { hiddenGroups(groupLocks, sources) }
                    // Upcoming Events' From (#110): the groups and channels that are shown, to pick.
                    val everyGroup = world?.groups
                    val upcomingGroupsNow = remember(everyGroup, sources) { upcomingGroups(everyGroup.orEmpty(), sources) }
                    val upcomingChannelsNow = remember(everyChannel) { everyChannel.orEmpty().filter { !it.hidden }.map { PickChoice(it.id, it.name, it.number) } }
                    SettingsScreen(
                        page = page,
                        values = settings,
                        actions = remember(nav, context, onHome, onTvScreensaver, onOtherApps, onOverApps, onUpdate, onResetSettings, onFindRecordings) {
                            settingsActions(nav, context, graph, onHome, onTvScreensaver, onOtherApps, onOverApps, onUpdate, onResetSettings, onFindRecordings)
                        },
                        facts = remember(
                            sources, counts, library, loading, tests, summary, sounds, clock, audio, home, apps, tvScreensaver, playNextAllowed, playNextAppsNow, playNextShown,
                            overApps, jellyfinAccount, jellyfinShown, jellyfinAppInstalled, update, storage, recordingsBytes, localBytes, localNamesNow, localOnPrimary,
                            localChoicesNow, jellyfinAdmin, networkFolder, pinSet, lockedChannelsNow, lockedGroupsNow, kidSafeGroupsNow, adultWords, kidWords, profiles,
                            hiddenChannelsNow, hiddenGroupsNow, upcomingGroupsNow, upcomingChannelsNow,
                            watching, defaultProfile, phoneInvite, phoneAvailable, phonesConnected, phones, rowOrder,
                        ) {
                            settingsFacts(sources, counts, tests, summary, clock.zone, audio, sounds, home, loading, library).let { facts ->
                                facts.copy(
                                    hiddenApps = apps.hiddenApps(),
                                    tvScreensaver = tvScreensaver ?: facts.tvScreensaver.copy(state = TvScreensaver.Unknown),
                                    playNextAllowed = playNextAllowed,
                                    playNextApps = playNextAppsNow,
                                    playNextItems = playNextShown.size,
                                    jellyfinConnected = jellyfinAccount != null,
                                    jellyfinAdmin = jellyfinAdmin,
                                    jellyfinServer = jellyfinAccount?.let { JellyfinServerChoice(it.server, it.serverName).where }.orEmpty(),
                                    jellyfinUser = jellyfinAccount?.userName.orEmpty(),
                                    jellyfinItems = jellyfinShown.size,
                                    jellyfinAppInstalled = jellyfinAppInstalled,
                                    pictureInPicture = graph.smallWindow.supported,
                                    remindersOverApps = overApps,
                                    version = graph.updates.version,
                                    update = update,
                                    driveName = drives[if (storage.onUsb) 0 else 1],
                                    storageOnTv = !storage.onUsb,
                                    driveFree = storage.free,
                                    driveTotal = storage.total,
                                    // Only what's on the drive: a recording After Copying let go of is on the network folder.
                                    recordingsBytes = recordingsBytes,
                                    localCatchUpBytes = localBytes,
                                    rewindBytes = storage.rewindBytes,
                                    localChannels = localNamesNow,
                                    localOnPrimary = localOnPrimary,
                                    localChoices = localChoicesNow,
                                    networkFolder = networkFolder?.shown.orEmpty(),
                                    networkFolderFree = networkFolder?.freeBytes ?: 0,
                                    recordingsLast = graph.recordingsLast(),
                                    pinSet = pinSet == true,
                                    // The sample world's until there's a source.
                                    lockedChannels = if (live) lockedChannelsNow else facts.lockedChannels,
                                    lockedGroups = if (live) lockedGroupsNow else facts.lockedGroups,
                                    hiddenChannelList = if (live) hiddenChannelsNow else facts.hiddenChannelList,
                                    groupsToHide = if (live) hiddenGroupsNow else facts.groupsToHide,
                                    adultWords = adultWords?.toString() ?: facts.adultWords,
                                    kidSafeGroups = if (live) kidSafeGroupsNow else facts.kidSafeGroups,
                                    upcomingGroups = if (live) upcomingGroupsNow else facts.upcomingGroups,
                                    upcomingChannels = if (live) upcomingChannelsNow else facts.upcomingChannels,
                                    kidWords = kidWords?.toString() ?: facts.kidWords,
                                    profiles = if (own) profiles?.let(::profileInfos) ?: facts.profiles else facts.profiles,
                                    currentProfile = if (own) watching?.id ?: facts.currentProfile else facts.currentProfile,
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
                                    homeRowOrder = rowOrder,
                                )
                            }
                        },
                        initialItem = route.item,
                        sourceId = route.source,
                        profileId = route.profile,
                        backLabel = route.back,
                    )
                }
                screen<PendingRoute> { entry ->
                    val route = entry.toRoute<PendingRoute>()
                    PendingScreen(route.title, backLabel = route.back)
                }
                screen<AddSourceRoute> { entry ->
                    val route = entry.toRoute<AddSourceRoute>()
                    val editId = route.editId
                    // A phone can fill the form in while it's open (brief 5.15).
                    val pairing = rememberPhonePairing(graph)
                    val phone by pairing.pairing.collectAsState()
                    val sent by pairing.sent.collectAsState()
                    if (editId == null) {
                        // A blank Xtream form, with this TV's MAC address ready for a portal: the
                        // board's half-filled portal is for the screenshot test. First-time setup
                        // opens it on the Stalker tab (brief 3.4, A2).
                        val kind = AddSourceType.entries.firstOrNull { it.name == route.kind } ?: AddSourceType.Xtream
                        val deviceMac by produceState<String?>(null) { value = graph.data.settings.deviceMac() }
                        deviceMac?.let { mac ->
                            AddSourceScreen(
                                state = remember(mac) { AddSourceState.blank(kind, primary = !live, deviceMac = mac) },
                                actions = remember(nav) {
                                    AddSourceActions(
                                        onDone = { nav.popBackStack() },
                                        onType = pairing::setType,
                                        onAdd = { form -> addSource(graph, form) },
                                    )
                                },
                                phone = phone,
                                sent = sent,
                                usualRefreshHours = graph.choices.refreshHours,
                                backLabel = route.back,
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
                                backLabel = route.back,
                            )
                        }
                    }
                }
                screen<ReplaceHomeRoute> {
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
                screen<KeyTestRoute> {
                    val learned by graph.keys.learned.collectAsState()
                    KeysAsTheyCome(graph)
                    KeyTestScreen(learned, backLabel = stringResource(SettingsR.string.remote), onBack = { nav.popBackStack() })
                }
                screen<LearnKeyRoute> {
                    val learned by graph.keys.learned.collectAsState()
                    KeysAsTheyCome(graph)
                    LearnKeyScreen(
                        learned = learned,
                        backLabel = stringResource(SettingsR.string.remote),
                        onLearn = { id, code -> graph.scope.launch { graph.data.settings.learnKey(id, code) } },
                        onForget = { id -> graph.scope.launch { graph.data.settings.forgetKey(id) } },
                    )
                }
                screen<DuplicateNumbersRoute> { entry ->
                    // From first-time setup, its back link names the step (#85).
                    val back = entry.toRoute<DuplicateNumbersRoute>().back
                    if (live) {
                        val state by graph.duplicateNumbers.collectAsState(null)
                        state?.let { DuplicateNumbersScreen(it, actions = remember(graph) { duplicateNumbersActions(graph) }, backLabel = back) }
                    } else {
                        DuplicateNumbersScreen(remember { DuplicateNumbersSamples.state }, backLabel = back)
                    }
                }
                screen<ChoosePinRoute> { entry ->
                    val route = entry.toRoute<ChoosePinRoute>()
                    val back = route.back ?: stringResource(
                        when {
                            route.lockChannel != null -> DesignR.string.tab_live_tv
                            route.profile != null -> SettingsR.string.profiles
                            else -> SettingsR.string.parental
                        },
                    )
                    ChoosePin(graph, settings, route, back) { nav.popBackStack() }
                }
                screen<ForgotPinRoute> { entry ->
                    val route = entry.toRoute<ForgotPinRoute>()
                    ForgotPin(graph, settings, route, route.back ?: stringResource(SettingsR.string.parental)) { nav.popBackStack() }
                }
                screen<RecoveryCodeRoute> {
                    NewRecoveryCode(graph) { nav.popBackStack() }
                }
                screen<UnlockChannelRoute> { entry ->
                    UnlockChannel(graph, entry.toRoute<UnlockChannelRoute>().channelId, stringResource(DesignR.string.tab_live_tv)) { nav.popBackStack() }
                }
                screen<UnlockCategoryRoute> { entry ->
                    val route = entry.toRoute<UnlockCategoryRoute>()
                    val back = stringResource(if (route.series) DesignR.string.tab_series else DesignR.string.tab_movies)
                    UnlockCategory(graph, route.categoryId, route.name, back) { nav.popBackStack() }
                }
                screen<ProfilePinRoute> { entry ->
                    val profileId = entry.toRoute<ProfilePinRoute>().profileId
                    val back = stringResource(SettingsR.string.profiles)
                    if (live) {
                        ProfilePin(graph, profileId, back) { nav.goHome() }
                        return@screen
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
                        // The PIN pad fades in as it rises (brief 2.6).
                        Overlay(visible = true, appear = true) {
                            ParentalPinScreen(
                                request = remember(locked) { ProfilesSamples.lockedProgram(locked) },
                                backLabel = backLabel(nav),
                                checkPin = { it == ProfilesSamples.PIN },
                                onUnlocked = { unlocks.unlock(channelId, Sample.nowOn(locked)?.end?.toInstant(), it) },
                                unlockFor = graph.unlockFor,
                            )
                        }
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
            // Switch Automatically waits during a movie, an episode or Multiview (#115).
            val waits = entry?.destination?.let { it.hasRoute<VodPlayerRoute>() || it.hasRoute<MultiviewRoute>() } == true
            ReminderPopup(graph, states, waits) { channelId -> nav.navigate(PlayerRoute(channelId, Lists.ALL)) }
        }
        NoticePopup(graph)
        // Show Freeze Watch (Settings › Playback › Advanced): over every screen while it's on.
        FreezeWatchPopup(graph)
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
    val warning = (state as? SleepState.Set)?.takeIf { it.warning && putAway != it.at }
    // It comes and goes as a toast does (brief 2.6).
    OverlayPopup(visible = warning != null, focusable = true, onDismissRequest = { warning?.let { putAway = it.at } }) {
        if (!LocalLeaving.current) PhoneKeysHere(graph)
        SleepToast(onKeepWatching = graph.sleep::keepWatching, onStopNow = graph.sleep::sleepNow)
    }
    if (state == SleepState.Asleep) {
        Popup(onDismissRequest = graph.sleep::wake, properties = PopupProperties(focusable = true)) {
            PhoneKeysHere(graph)
            AsleepScreen(onWake = graph.sleep::wake)
        }
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
    // It fades in as it rises, and out with the words it had (brief 2.6).
    val notice = rememberLast(shown)
    OverlayPopup(visible = shown != null, focusable = false) {
        if (notice != null) NoticeToast(notice.text, notice.icon)
    }
}

/**
 * Add a Source's local server for phones (brief 5.15): it answers while the screen is on the TV,
 * and stops when the screen closes or Plasma leaves the front.
 */
@Composable
private fun rememberPhonePairing(graph: AppGraph): PhonePairingHost {
    val context = LocalContext.current
    val host = remember(graph) {
        PhonePairingHost(graph.scope, PhonePairingHost.texts(context)) { name, bytes ->
            runCatching { graph.data.sources.keepPlaylist(name, bytes) }.getOrNull()
        }
    }
    LifecycleStartEffect(host) {
        host.start()
        onStopOrDispose { host.close() }
    }
    return host
}

/**
 * Whether the Jellyfin app is on this TV, for Play Jellyfin Items In (5.19): asked as a Settings page
 * opens and each time Plasma comes back to the front, rather than at each of the page's updates.
 */
@Composable
private fun rememberJellyfinAppInstalled(): Boolean {
    val context = LocalContext.current
    var installed by remember(context) { mutableStateOf(JellyfinApp.installed(context)) }
    LifecycleResumeEffect(context) {
        installed = JellyfinApp.installed(context)
        onPauseOrDispose { }
    }
    return installed
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
private fun ReminderPopup(graph: AppGraph, states: LiveStates, waits: Boolean, onSwitch: (channelId: String) -> Unit) {
    val due by graph.reminderClock.due.collectAsState()
    val world by graph.world.collectAsState()
    val now by rememberNow()
    // Switch Automatically's count (#115), unless something holds it back.
    val count by graph.reminderCountdown.count.collectAsState()
    val current = due?.let { reminding ->
        world?.let { states.reminder(it, reminding) }
            ?.withCount(reminding, count) { waits || graph.switchNeedsAConnection(reminding.channelId) }
            ?.let { reminding to it }
    }
    // It fades in as it rises, and out with what it was about once it's acted on (brief 2.6).
    val held = rememberLast(current)
    OverlayPopup(visible = current != null, focusable = true, onDismissRequest = { current?.let { graph.reminderClock.done(it.first.id) } }) {
        if (!LocalLeaving.current) PhoneKeysHere(graph)
        val (shown, reminder) = held ?: return@OverlayPopup
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

/**
 * [PlasmaTheme] with Settings › Appearance's accent, Text Size, Reduce Motion and Show Channel
 * Numbers, and how the remote's keys work (Remote & Buttons, and Up/Down in Player), as they change
 * (brief 5.16).
 */
@Composable
fun PlasmaAppTheme(graph: AppGraph, content: @Composable () -> Unit) {
    val stored by graph.storedChoices.collectAsState()
    val choices = SettingsChoices(stored.orEmpty())
    val keys = remember(choices) {
        KeyChoices(
            numberEntryDelayMs = choices.numberEntryDelay.millis,
            longPressOk = choices.longPressOk,
            holdOkNumberPad = choices.longPressOkDoes == LongPressOk.NumberPad,
            channelUpToNext = choices.channelUpGoesTo == ChannelUpGoesTo.NextNumber,
            upDownChangesChannel = choices.upDownInPlayer == UpDownInPlayer.ChangeChannel,
        )
    }
    PlasmaTheme(
        accent = PlasmaColors.accent(choices.accent),
        reduceMotion = choices.reduceMotion,
        textScale = if (choices.largeText) LARGE_TEXT_SCALE else 1f,
        lighterEffects = choices.lighterEffects,
    ) {
        CompositionLocalProvider(
            LocalKeyChoices provides keys,
            LocalShowChannelNumbers provides choices.showChannelNumbers,
            content = content,
        )
    }
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
    val profile by graph.currentProfile.collectAsState()
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
 * channel logos and other pictures from the network. Coil decodes a picture at its full size unless
 * it's told the size it's drawn at, and a provider's poster or backdrop is often several times its
 * place on the screen: posters and logos are decoded for their places, and the rest no larger than
 * needed to cover the screen (docs/decisions.md, Snappier).
 */
/** How long a picture takes to fade in as it arrives (#158): the brief's 200 ms (2.6). */
private const val PICTURE_FADE_MS = 200

internal class AppImages(private val loader: ImageLoader) : SizedImageResolver {
    @Composable
    override fun painter(model: String): Painter? = when {
        model.startsWith(FixtureImages.SCHEME) -> FixtureImages.resolver.painter(model)
        AppArt.isAppArt(model) -> appArt(model)
        else -> {
            val screen = LocalResources.current.displayMetrics
            networkPainter(model, IntSize(screen.widthPixels, screen.heightPixels), Scale.FILL)
        }
    }

    @Composable
    override fun painter(model: String, size: IntSize, contentScale: ContentScale): Painter? = when {
        model.startsWith(FixtureImages.SCHEME) || AppArt.isAppArt(model) || contentScale == ContentScale.None -> painter(model)
        else -> networkPainter(model, size, if (contentScale == ContentScale.Fit || contentScale == ContentScale.Inside) Scale.FIT else Scale.FILL)
    }

    /**
     * A picture from the network, decoded to cover [size] ([Scale.FILL]) or to fit within it
     * ([Scale.FIT]), or a little larger. One that has to come from the network or the disk fades in
     * over 200 ms as it arrives (#158, brief 2.6); one already in memory shows at once.
     */
    @Composable
    private fun networkPainter(model: String, size: IntSize, scale: Scale): Painter {
        val context = LocalContext.current
        val request = remember(context, model, size, scale) {
            ImageRequest.Builder(context)
                .data(model)
                .size(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1))
                .scale(scale)
                .crossfade(PICTURE_FADE_MS)
                .build()
        }
        return rememberAsyncImagePainter(request, loader)
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
    onResetSettings: () -> Unit,
    onFindRecordings: () -> Unit,
) = SettingsActions(
    onOpen = { nav.navigate(SettingsRoute(it.name)) },
    onSource = sourceAction(nav, graph),
    onForgetPlayers = graph::forgetPlayers,
    onResetSound = graph::resetSound,
    onSourceText = graph::typeForSource,
    onHome = onHome,
    onShowApp = { graph.setAppHidden(it, hidden = false) },
    onTvScreensaver = onTvScreensaver,
    // Home Rows: the rows' new order, kept (the owner's #80).
    onHomeRows = { rows -> graph.scope.launch { graph.data.settings.setHomeRowOrder(rows) } },
    onOtherApps = onOtherApps,
    jellyfin = graph.jellyfin,
    onForgetJellyfin = graph.jellyfin::forget,
    onOverApps = onOverApps,
    onUpdate = onUpdate,
    onSaveDiagnostics = graph::saveDiagnosticsToUsb,
    diagnosticsPhone = graph::diagnosticsPhone,
    backups = graph.backups,
    onResetSettings = onResetSettings,
    onLocalCatchUp = { channelId, on -> graph.scope.launch { graph.setLocalCatchUp(channelId, on) } },
    onClearLocal = graph::clearLocalCatchUp,
    networkFolder = graph.networkFolder,
    onForgetNetworkFolder = graph::forgetNetworkFolder,
    // The TV's own settings, a channel playing going to the small window (#167).
    onTvSettings = graph::openDeviceSettings,
    onFindRecordings = onFindRecordings,
    onGo = { destination -> settingsDestination(nav, context, destination) },
    onHideChannel = { channelId, hidden -> graph.scope.launch { graph.data.channels.setHidden(channelId, hidden) } },
    onHideGroup = { groupId, hidden -> graph.scope.launch { graph.data.channels.setGroupHidden(groupId, hidden) } },
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
    onRemoveProfilePin = { profileId ->
        graph.scope.launch {
            graph.data.profiles.setPin(profileId, null)
            // Its recovery code goes with it (#112).
            graph.data.settings.forgetRecovery(profileId)
        }
    },
    onDeleteProfile = { profileId ->
        graph.scope.launch {
            graph.data.profiles.delete(profileId)
            graph.data.settings.forgetRecovery(profileId)
        }
        nav.popBackStack()
    },
    // The phone remote's paired phones (5.21).
    onRenamePhone = { phoneId, name -> graph.phoneRemote.rename(phoneId, name) },
    onForgetPhone = { phoneId -> graph.phoneRemote.forget(phoneId) },
    // A source's Categories (the owner's ask, 1 November).
    categories = AppCategories(graph),
)

/** A screen a Settings row leads to, over Settings: Back returns to the row. */
internal fun settingsDestination(nav: NavHostController, context: Context, destination: SettingsDestination) {
    fun pending(@StringRes title: Int, @StringRes back: Int) = PendingRoute(context.getString(title), context.getString(back))
    nav.navigate(
        when (destination) {
            SettingsDestination.DuplicateNumbers -> DuplicateNumbersRoute()
            SettingsDestination.ReplaceHome -> ReplaceHomeRoute
            SettingsDestination.AddSource -> AddSourceRoute()
            SettingsDestination.Profiles -> pending(R.string.edit_profile, SettingsR.string.profiles)
            SettingsDestination.KeyTest -> KeyTestRoute
            SettingsDestination.LearnKey -> LearnKeyRoute
            SettingsDestination.SetPin -> ChoosePinRoute()
            SettingsDestination.ChangePin -> ChoosePinRoute(change = true)
            SettingsDestination.LockSettings -> ChoosePinRoute(lockSettings = true)
            SettingsDestination.RecoveryCode -> RecoveryCodeRoute
            // First-time setup again, from About (#85): Back on its welcome comes back here.
            SettingsDestination.Setup -> SetupRoute(again = true)
        },
    )
}

/**
 * One of a source's actions on Portals & Playlists (brief 4.20). Opened from first-time setup,
 * the screens they open name the step, [back], as where Back goes (#85).
 */
internal fun sourceAction(nav: NavHostController, graph: AppGraph, back: String? = null): (String, SourceAction) -> Unit = { sourceId, action ->
    when (action) {
        SourceAction.Edit -> nav.navigate(AddSourceRoute(editId = sourceId, back = back))
        SourceAction.RefreshNow -> graph.refresh(sourceId)
        SourceAction.SetPrimary -> graph.setPrimary(sourceId)
        SourceAction.Test -> graph.testSource(sourceId)
        SourceAction.Playback -> nav.navigate(SettingsRoute(SettingsPage.SourcePlayback.name, source = sourceId, back = back))
        // Its categories, by kind (the owner's ask, 1 November).
        SourceAction.LiveCategories -> nav.navigate(SettingsRoute(SettingsPage.LiveCategories.name, source = sourceId, back = back))
        SourceAction.MovieCategories -> nav.navigate(SettingsRoute(SettingsPage.MovieCategories.name, source = sourceId, back = back))
        SourceAction.SeriesCategories -> nav.navigate(SettingsRoute(SettingsPage.SeriesCategories.name, source = sourceId, back = back))
        SourceAction.Disable -> graph.setEnabled(sourceId, false)
        SourceAction.Enable -> graph.setEnabled(sourceId, true)
        SourceAction.Delete -> graph.scope.launch { graph.data.sources.remove(sourceId) }
    }
}

/**
 * Locked Groups (5.12): every group, whether its channels need the PIN, and whether it's adult;
 * with more than one source, each says which it's from.
 */
private fun lockedGroups(locks: GroupLocks?, sources: List<Source>): List<LockedGroupInfo> {
    locks ?: return emptyList()
    val names = sources.associate { it.id to it.name }
    // The user's own groups lock nothing: their channels lock with their own groups.
    return locks.groups.filter { !it.custom }.map { group ->
        LockedGroupInfo(group.id, group.name, locks.isLocked(group), locks.isAdult(group), source = names[group.sourceId]?.takeIf { sources.size > 1 })
    }
}

/**
 * Hidden Groups (4.29): every group, and whether it's hidden; with more than one source, each says
 * which it's from.
 */
private fun hiddenGroups(locks: GroupLocks?, sources: List<Source>): List<HiddenGroupInfo> {
    locks ?: return emptyList()
    val names = sources.associate { it.id to it.name }
    // The user's own groups are shown or hidden on their source's Categories page.
    return locks.groups.filter { !it.custom }.map { group -> HiddenGroupInfo(group.id, group.name, group.hidden, source = names[group.sourceId]?.takeIf { sources.size > 1 }) }
}

/**
 * Upcoming Events' From › Groups (#110): the groups that are shown, the user's own too; with more
 * than one source, each says which it's from.
 */
private fun upcomingGroups(groups: List<ChannelGroup>, sources: List<Source>): List<PickChoice> {
    val names = sources.associate { it.id to it.name }
    return groups.filter { !it.hidden }.map { group -> PickChoice(group.id, group.name, source = names[group.sourceId]?.takeIf { sources.size > 1 }) }
}

/**
 * Kid-Safe Groups (5.11): the groups that aren't locked, whether kids profiles see them, and
 * whether that's by Kids Words; with more than one source, each says which it's from.
 */
private fun kidSafeGroups(locks: GroupLocks?, sources: List<Source>): List<KidSafeGroupInfo> {
    locks ?: return emptyList()
    val names = sources.associate { it.id to it.name }
    return locks.groups.filter { !it.custom && !locks.isLocked(it) }.map { group ->
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

/**
 * [channelId] full screen from [listId] with Live TV under it, as if played from there: Home's
 * banner is a way into Live TV, so Back from the channel goes to Live TV, on that list with the
 * channel picked and playing in the corner (the owner on #145, 4 November).
 */
private fun NavHostController.watchFromLiveTv(channelId: String, listId: String) {
    navigate(LiveTvRoute)
    navigate(PlayerRoute(channelId, listId))
}

/** What Home does; [live] once there's a source, where recordings play from their files. */
private fun homeActions(nav: NavHostController, graph: AppGraph, live: Boolean) = HomeActions(
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
            // The channel playing behind the banner, in the list it was last played in (#100), and a
            // reminder's in All Channels: both as Live TV plays them, so Back goes there (#145).
            HeroKind.Live -> item.id.substringAfter(':').let { id -> nav.watchFromLiveTv(id, graph.world.value?.lastListOf(id) ?: Lists.ALL) }
            HeroKind.Reminder -> nav.watchFromLiveTv(item.id.substringAfter(':'), Lists.ALL)
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
    // The rows Home Rows adds (the owner's #80): a series' page; a program on now, full screen;
    // a recording, played as Recordings plays it (the sample world's by title).
    onSeries = { nav.navigate(SeriesDetailRoute(it)) },
    onSeeAllSeries = { nav.openTab(TopTab.Series) },
    onUpcoming = { nav.navigate(PlayerRoute(it)) },
    onRecording = { item -> nav.navigate(if (live) VodPlayerRoute(item.title, KIND_RECORDING, item.id) else VodPlayerRoute(item.title)) },
    onSeeAllRecordings = { nav.navigate(RecordingsRoute) },
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

/**
 * A destination that settles 16 dp upward as it fades in, over the screen it replaces fading out
 * (brief 2.6); with Reduce Motion it only fades. The player and Multiview fill the screen with
 * live video, which can't move with them, so they're plain `composable`s and only fade.
 */
private inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        val scope = this
        SettlingScreen { content(scope, entry) }
    }
}
