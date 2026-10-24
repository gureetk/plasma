// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.AppGraph
import ca.gureet.plasma.R
import ca.gureet.plasma.SleepState
import ca.gureet.plasma.SourceTest
import ca.gureet.plasma.unlockFor
import ca.gureet.plasma.withApps
import ca.gureet.plasma.feature.profiles.ParentalPinScreen
import ca.gureet.plasma.feature.profiles.PinRequest
import ca.gureet.plasma.core.data.AddedSource
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.InvalidMacAddress
import ca.gureet.plasma.core.data.NoLiveChannels
import ca.gureet.plasma.core.data.PlaylistRefused
import ca.gureet.plasma.core.data.Programme
import ca.gureet.plasma.core.data.Source
import ca.gureet.plasma.core.data.SourceDetails
import ca.gureet.plasma.core.data.SourcePlayback
import ca.gureet.plasma.core.design.component.SourceChoice
import ca.gureet.plasma.core.model.ContentRatings
import ca.gureet.plasma.feature.catchup.CatchUpActions
import ca.gureet.plasma.feature.catchup.CatchUpScreen
import ca.gureet.plasma.feature.guide.GuideActions
import ca.gureet.plasma.feature.guide.GuideScreen
import ca.gureet.plasma.feature.guide.GuideStale
import ca.gureet.plasma.feature.home.HomeActions
import ca.gureet.plasma.feature.home.HomeSamples
import ca.gureet.plasma.feature.home.PosterItem
import ca.gureet.plasma.feature.home.HomeScreen
import ca.gureet.plasma.feature.livetv.LivePlaying
import ca.gureet.plasma.feature.livetv.LiveSound
import ca.gureet.plasma.feature.livetv.LiveTvActions
import ca.gureet.plasma.feature.livetv.LiveTvScreen
import ca.gureet.plasma.feature.player.PlayerActions
import ca.gureet.plasma.feature.player.PlayerOverlay
import ca.gureet.plasma.feature.player.PlayerProblem
import ca.gureet.plasma.feature.player.PlayerScreen
import ca.gureet.plasma.feature.player.PlayerStatus
import ca.gureet.plasma.feature.player.SleepInfo
import ca.gureet.plasma.feature.search.SearchActions
import ca.gureet.plasma.feature.search.SearchFocus
import ca.gureet.plasma.feature.search.SearchResults
import ca.gureet.plasma.feature.search.SearchScreen
import ca.gureet.plasma.feature.settings.AddSourceForm
import ca.gureet.plasma.feature.settings.AdvancedFields
import ca.gureet.plasma.feature.settings.AddSourceState
import ca.gureet.plasma.feature.settings.ChannelSoundInfo
import ca.gureet.plasma.feature.settings.ConnectOutcome
import ca.gureet.plasma.feature.settings.ConnectProblem
import ca.gureet.plasma.feature.settings.HomeScreenFacts
import ca.gureet.plasma.feature.settings.LocalChannelChoice
import ca.gureet.plasma.feature.settings.SettingsFacts
import ca.gureet.plasma.feature.settings.SettingsSamples
import ca.gureet.plasma.feature.settings.SourceHealth
import ca.gureet.plasma.feature.settings.SourceInfo
import ca.gureet.plasma.feature.settings.SourcePlaybackInfo
import ca.gureet.plasma.feature.settings.SourceType
import ca.gureet.plasma.player.EngineId
import ca.gureet.plasma.player.LiveFailure
import ca.gureet.plasma.player.LiveStatus
import ca.gureet.plasma.player.PictureSize
import ca.gureet.plasma.player.StreamKeys
import ca.gureet.plasma.source.m3u.NotAPlaylist
import ca.gureet.plasma.source.stalker.StalkerException
import ca.gureet.plasma.source.stalker.StalkerPortal
import ca.gureet.plasma.source.xtream.XtreamException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ca.gureet.plasma.core.data.SourceType as DataSourceType

/*
 * The live screens once a source is added: the same screens as on the sample data, with states
 * from the data layer and actions that change it.
 */

@Composable
fun LiveHome(graph: AppGraph, states: LiveStates, actions: HomeActions, showApps: Boolean) {
    val world by graph.world.collectAsState()
    // The TV's own apps in the Apps row (brief 4.4 and 5.13).
    val apps by graph.apps.collectAsState()
    // Other apps' items from Android's Play Next row, when Include Other Apps is on, and the
    // Jellyfin server's once signed in (5.19).
    val others by graph.playNextShown.collectAsState(emptyList())
    val jellyfin by graph.jellyfin.shown.collectAsState(emptyList())
    val jellyfinOffline by graph.jellyfin.offline.collectAsState()
    val vod = graph.data.vod
    // Continue Watching and New in Movies, from the sources' movies and series (brief 4.2 and 5.9).
    val continuing by remember(graph) {
        val choices = graph.choices
        vod.continueWatching(continueSince(choices, graph.clock.now()), choices.continueNextEpisodes)
    }.collectAsState(null)
    // A kids profile's only from the categories it sees (5.11).
    val newMovies by remember(graph) { combine(vod.recentMovies(), graph.vodShown) { movies, shown -> movies.filter { shown.has(it.categoryId) } } }.collectAsState(null)
    val resources = LocalResources.current
    val words = remember(resources) { ContinueWords.of(resources) }
    val current = world ?: return
    HomeScreen(
        state = remember(current, continuing, newMovies, words, apps, showApps, others, jellyfin, jellyfinOffline) {
            val choices = graph.choices
            val theirs = others.forContinue(choices.continueNextEpisodes, continueSince(choices, graph.clock.now()))
            states.home(current, HomeSamples.state).copy(
                continueWatching = continueRow(continuing.orEmpty(), theirs, words, jellyfin, jellyfinOffline, graph.jellyfin.playsInPlasma(), graph::appName),
                newMovies = newMovies.orEmpty().take(NEW_MOVIES).map { PosterItem(it.id, it.name, it.poster) },
            ).withApps(apps, showApps)
        },
        actions = remember(actions) {
            HomeActions(
                onTab = actions.onTab,
                onSettings = actions.onSettings,
                onProfile = actions.onProfile,
                onDock = actions.onDock,
                onHero = actions.onHero,
                onChannel = actions.onChannel,
                // Another app's item opens in that app; a Jellyfin item plays in Plasma, or opens
                // in the Jellyfin app if Play Jellyfin Items In says so (5.19).
                onContinue = { id ->
                    playNextKeyOf(id)?.let(graph::openPlayNext)
                        ?: jellyfinIdOf(id)?.let { item -> graph.jellyfin.open(item, play = actions.onContinue) }
                        ?: actions.onContinue(id)
                },
                onMovie = actions.onMovie,
                onSeeAllMovies = actions.onSeeAllMovies,
                onApp = actions.onApp,
                onAllApps = actions.onAllApps,
                onRemoveFavorite = { graph.setFavorite(it, favorite = false) },
                onMoveFavorite = graph::moveFavorite,
                onRemoveContinue = { id -> continuePlayable(id)?.let { (kind, vodId) -> graph.scope.launch { runCatching { vod.removeFromContinue(kind, vodId) } } } },
                onHideOther = { id, wholeApp ->
                    playNextKeyOf(id)?.let { graph.hidePlayNext(it, wholeApp) } ?: jellyfinIdOf(id)?.let(graph.jellyfin::hide)
                },
            )
        },
    )
}

/** Home's New in Movies has this many, then See All. */
private const val NEW_MOVIES = 20

@Composable
fun LiveLiveTv(graph: AppGraph, states: LiveStates, actions: LiveTvActions) {
    val world by graph.world.collectAsState()
    // Channels' own volume levels, and whose sound passes through now (5.4.6).
    val sounds by graph.data.playback.sounds.collectAsState(emptyList())
    val passing by remember(graph) { graph.passingThrough() }.collectAsState(null)
    // What plays in the preview, and from which source, and what each channel starts on (Switch Source, 5.1).
    val from by graph.player.playingFrom.collectAsState()
    val kept by graph.rememberedAll.collectAsState(emptyMap())
    // Every channel's number, for Change Number (5.2).
    val owners by graph.numberOwners.collectAsState(null)
    val current = world ?: return
    val data = graph.data.channels
    LiveTvScreen(
        state = remember(current, owners) { states.liveTv(current, selectedGroupId = null, owners) },
        sound = remember(sounds, passing) { LiveSound(sounds.filter { it.levelDb != 0 }.associate { it.channelId to it.levelDb }, passing) },
        playing = from?.let { LivePlaying(it.key, it.candidate) },
        remembered = remember(current, kept) { states.remembered(current, kept) },
        actions = remember(actions) {
            LiveTvActions(
                onTab = actions.onTab,
                onSettings = actions.onSettings,
                onProfile = actions.onProfile,
                onWatch = actions.onWatch,
                onWatchFrom = actions.onWatchFrom,
                onCatchUp = actions.onCatchUp,
                onMultiview = actions.onMultiview,
                onFavorite = graph::setFavorite,
                onMoveFavorite = graph::moveFavorite,
                onHide = { id, hidden -> graph.scope.launch { data.setHidden(id, hidden) } },
                // With no PIN yet, one is chosen first; unlocking for good needs it (5.12).
                onLock = actions.onLock,
                onRemind = graph::remind,
                onRecord = graph::record,
                onRecordSeries = graph::recordSeries,
                onLevel = graph.player::setLevel,
                onKeepLevel = graph::keepLevel,
                onDecode = graph::decodeChannel,
                onLocalCatchUp = graph::toggleLocalCatchUp,
                onSwitchSource = graph::switchSourceFor,
                onForgetSource = graph::forgetSourceFor,
                onChangeNumber = graph::changeNumber,
            )
        },
    )
}

/**
 * Search on the source's guide (brief 4.3 and 5.10), with the searches a result was opened from
 * as suggestions.
 */
@Composable
fun LiveSearchScreen(graph: AppGraph, actions: SearchActions, profileName: String, initialQuery: String, initialFocus: SearchFocus) {
    val recent by graph.data.search.recent.collectAsState(initial = emptyList())
    val search = remember(graph) {
        LiveSearch(graph.data.search::programmes) { query ->
            // A kids profile finds only what's in the categories it sees (5.11).
            val shown = graph.vodShown.first()
            graph.data.vod.searchMovies(query).filter { shown.has(it.categoryId) }.map { it.searchResult() } +
                graph.data.vod.searchSeries(query).filter { shown.has(it.categoryId) }.map { it.searchResult() }
        }
    }
    SearchScreen(
        search = { query ->
            val world = graph.world.value
            if (world == null) SearchResults() else search.results(world, query, graph.clock.now())
        },
        recentSearches = recent,
        actions = remember(actions) {
            SearchActions(
                onTab = actions.onTab,
                onSettings = actions.onSettings,
                onProfile = actions.onProfile,
                onProgram = actions.onProgram,
                onTitle = actions.onTitle,
                onVoice = actions.onVoice,
                onRemind = graph::remind,
                onRecord = graph::record,
                onRecordSeries = graph::recordSeries,
                onSearched = graph::searched,
            )
        },
        profileName = profileName,
        profileInitial = profileName.take(1).uppercase(),
        initialQuery = initialQuery,
        initialFocus = initialFocus,
    )
}

/**
 * Catch-up on the source's channels (board 18, brief 4.18): those whose provider keeps an archive.
 * Each channel's past programs load from the guide as it shows; OK plays one from its start.
 */
@Composable
fun LiveCatchUp(graph: AppGraph, states: LiveStates, channelId: String?, actions: CatchUpActions, empty: @Composable () -> Unit) {
    val world by graph.world.collectAsState()
    var loaded by remember { mutableStateOf(emptyMap<String, List<Programme>>()) }
    var showing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showing) {
        val channel = showing?.let { world?.byId?.get(it) } ?: return@LaunchedEffect
        if (channel.id in loaded) return@LaunchedEffect
        val now = graph.clock.now()
        // As far back as the provider's archive goes, or local catch-up holds (5.5).
        val from = world?.catchUpFrom(channel) ?: now
        val past = channel.epgKey?.let { key ->
            graph.data.guide.forChannel(key, from, now).filter { !it.start.isBefore(from) }
        }.orEmpty()
        loaded = loaded + (channel.id to past)
    }
    val current = world ?: return
    val state = remember(current, loaded) { states.catchUp(current, loaded, channelId) }
    if (state == null) {
        empty()
        return
    }
    CatchUpScreen(
        state = state,
        actions = remember(actions) {
            CatchUpActions(
                onPlay = actions.onPlay,
                onGuide = actions.onGuide,
                onShowInGuide = actions.onShowInGuide,
                onChannel = { showing = it },
            )
        },
    )
}

/**
 * The Guide on the day showing (brief 4.8): its programmes load from the database as a day tab
 * shows, a little either side of midnight, and the group picked filters the channels.
 */
@Composable
fun LiveGuide(graph: AppGraph, states: LiveStates, actions: GuideActions) {
    val world by graph.world.collectAsState()
    val zone = graph.clock.zone
    var day by remember { mutableStateOf(graph.clock.nowZoned().toLocalDate()) }
    var listId by remember { mutableStateOf(Lists.ALL) }
    val programmes by remember(day) {
        val start = day.atStartOfDay(zone).toInstant()
        graph.data.guide.window(start.minus(DAY_MARGIN), start.plus(Duration.ofDays(1)).plus(DAY_MARGIN))
    }.collectAsState(initial = null)
    val sources by graph.data.sources.sources.collectAsState(emptyList())
    val latestEnd by graph.data.guide.latestEnd.collectAsState(initial = null)
    val current = world ?: return
    // A guide with no channels yet has nothing to lay out.
    if (current.channels.none { !it.hidden }) return
    val stale = guideStale(sources, latestEnd, current.now)
    GuideScreen(
        state = remember(current, programmes, stale, listId) { states.guide(current, programmes.orEmpty(), loading = programmes == null, stale, listId) },
        actions = remember(actions) {
            GuideActions(
                onTab = actions.onTab,
                onSettings = actions.onSettings,
                onProfile = actions.onProfile,
                onProgram = actions.onProgram,
                onDay = { day = it },
                onGroupPicked = { listId = it },
                onRefresh = graph::refreshGuides,
                onKeepAsRecording = graph::keepAsRecording,
                onRemind = graph::remind,
                onRecord = graph::record,
                onRecordSeries = graph::recordSeries,
            )
        },
    )
}

/**
 * The guide is out of date (brief 5.3) when it hasn't been updated for two days, or its programmes
 * run out within 12 hours. No source has a guide yet: that's out of date too.
 */
internal fun guideStale(sources: List<Source>, latestEnd: Instant?, now: Instant): GuideStale? {
    if (sources.isEmpty()) return null
    val updated = sources.mapNotNull { it.guideRefreshedAt }.maxOrNull() ?: return GuideStale(null)
    return when {
        Duration.between(updated, now) > STALE_AFTER -> GuideStale(updated)
        latestEnd != null && Duration.between(now, latestEnd) < RUNS_OUT_WITHIN -> GuideStale(updated)
        else -> null
    }
}

/** Programmes just before midnight and just after it belong to the day too, at the window's edges. */
private val DAY_MARGIN: Duration = Duration.ofHours(4)
private val STALE_AFTER: Duration = Duration.ofHours(48)
private val RUNS_OUT_WITHIN: Duration = Duration.ofHours(12)

/**
 * The player on [channelId], zapping through [listId], or a program from its archive starting at
 * [from] (catch-up, brief 5.5). Each channel tuned is noted as watched, for Recently Watched and
 * Last Channel (brief 5.2). A locked channel asks for the PIN first (4.25), zapped onto too: Back
 * goes back to the channel before it, or from the first, to [backLabel].
 */
@Composable
fun LivePlayer(graph: AppGraph, states: LiveStates, channelId: String, listId: String, from: Instant?, actions: PlayerActions, backLabel: String) {
    val world by graph.world.collectAsState()
    val status by graph.player.status.collectAsState()
    val playingFrom by graph.player.playingFrom.collectAsState()
    val sources by graph.data.sources.sources.collectAsState(emptyList())
    val due by graph.reminderClock.due.collectAsState()
    val shownReminder by rememberUpdatedState(due)
    var tuned by remember(channelId) { mutableStateOf(channelId) }
    // Playing from the archive: Start Over, or catch-up from the Guide, Search or Catch-up.
    var archive by remember(channelId, from) { mutableStateOf(from?.let { Archive(it, graph.clock.now()) }) }
    val key = archive?.let { StreamKeys.archive(tuned, it.start) } ?: tuned
    // For the actions, which are made once: the key on screen now, after any zap.
    val currentKey by rememberUpdatedState(key)
    // The archive stopped well before its program's end: a message with Try Again.
    var endedEarly by remember(key) { mutableStateOf(false) }
    // Live TV paused or rewound in the rewind buffer (brief 5.5).
    val behind by graph.player.behindLive.collectAsState()
    // Live TV paused without the rewind buffer, on a channel with an archive: playing on opens the
    // archive from that moment (5.5).
    var heldAt by remember(channelId) { mutableStateOf<Instant?>(null) }
    // Left while held, the picture would stay stopped behind the screens: live TV plays on.
    val stillHeld by rememberUpdatedState(heldAt)
    DisposableEffect(graph) {
        onDispose { if (stillHeld != null) graph.player.retry() }
    }
    // A run of ±10 s presses, or a scrub along the timeline, opens the stream once, where it ends.
    val seeking = remember { arrayOfNulls<Job>(1) }
    // Its program may be older than the guide kept around now, for the overlay to show.
    val archived by produceState<Programme?>(null, key) {
        val start = archive?.start
        val epgKey = world?.byId?.get(tuned)?.epgKey
        value = if (start == null || epgKey == null) null else graph.data.guide.forChannel(epgKey, start, start.plusSeconds(1)).firstOrNull()
    }

    /** What plays on [channel]: from the archive, the program there; live, what's on now. */
    fun playing(known: LiveWorld, channel: Channel): Programme? {
        val start = archive?.start ?: return known.nowNext(channel).now
        return known.programmesOf(channel).firstOrNull { it.isOn(start) } ?: archived?.takeIf { it.isOn(start) }
    }
    // Locked, or playing a program over Age Limit (5.12), and not opened with the PIN; null until
    // the channels are known.
    val parental = graph.parental
    val closed = world?.let { known ->
        known.byId[tuned]?.let { channel -> (channel.locked || known.overLimit(playing(known, channel))) && !parental.isUnlocked(channel.id) } ?: false
    }
    // The channel Back goes to from a locked one's PIN: the last that played.
    val lastOpen = remember(channelId) { arrayOf(channelId) }
    SideEffect { if (closed == false) lastOpen[0] = tuned }
    LaunchedEffect(tuned, closed) { if (closed == false) graph.data.channels.watched(tuned, listId) }
    // A program from the archive that plays to its end, or reaches live TV, goes back to live TV.
    LaunchedEffect(status, key) {
        if ((status as? LiveStatus.Ended)?.channelId != key) return@LaunchedEffect
        val playing = archive ?: return@LaunchedEffect
        val now = graph.clock.now()
        val position = playing.position(now)
        val program = archived?.takeIf { !it.start.isAfter(position) && it.end.isAfter(position) }
            ?: world?.byId?.get(tuned)?.let { channel -> world?.programmesOf(channel)?.firstOrNull { !it.start.isAfter(position) && it.end.isAfter(position) } }
        if (program == null || program.end.isAfter(now) || Duration.between(position, program.end) < NEAR_END) {
            archive = null
        } else {
            // Held where it stopped, for Try Again.
            archive = playing.copy(pausedAt = playing.pausedAt ?: now)
            endedEarly = true
        }
    }
    // Full screen, the TV may switch to the video's frame rate (Match Frame Rate, brief 5.4.4).
    DisposableEffect(graph) {
        graph.player.setFullScreen(true)
        onDispose { graph.player.setFullScreen(false) }
    }
    val current = world ?: return
    if (closed == true) {
        val locked = current.byId.getValue(tuned)
        val program = playing(current, locked)
        val back = lastOpen[0].takeIf { it != tuned }
        BackHandler {
            if (back != null) tuned = back else actions.onBack()
        }
        ParentalPinScreen(
            request = PinRequest.Program(locked.number, locked.name, locked.logo, program?.title ?: locked.name, rating = program?.rating?.let(ContentRatings::badge)),
            backLabel = back?.let { current.byId[it]?.name } ?: backLabel,
            checkPin = parental::check,
            // This Program lasts to its end live; from the archive, an hour.
            onUnlocked = { parental.unlock(locked.id, program?.end?.takeIf { archive == null }, it) },
            tries = parental.tries,
            unlockFor = graph.unlockFor,
        )
        return
    }
    // Where the channel plays from now: one of a merged channel's sources (brief 5.1). From the
    // archive, only the sources that keep one are in the list.
    val names = sources.associate { it.id to it.name }
    val playingNow = playingFrom?.takeIf { it.key == key }
    val sourceName = names[sourceOf(playingNow?.candidate ?: tuned)]
    // The one Remember What Works per Channel keeps comes first, marked (4.35); live TV only.
    val live = playingNow?.candidates?.takeIf { it.size > 1 && archive == null }
    val remembered by remember(live) { live?.let(graph::remembered) ?: flowOf(null) }.collectAsState(null)
    val works = remembered
    val choices = playingNow?.candidates.orEmpty().takeIf { it.size > 1 }.orEmpty()
        .sortedByDescending { it == works?.candidateId }
        .map { candidate ->
            SourceChoice(
                candidate,
                names[sourceOf(candidate)] ?: candidate,
                playing = candidate == playingNow?.candidate,
                remembered = works?.takeIf { it.candidateId == candidate }?.learnedAt,
            )
        }
    val nextSource = choices.getOrNull(choices.indexOfFirst { it.playing } + 1)?.name
    // The engine playing, and the one Switch Player goes to (brief 4.10).
    val engine by graph.player.engineId.collectAsState()
    val engineName = engineName(engine)
    val nextEngineName = graph.player.nextEngine(engine)?.let { engineName(it) }.orEmpty()
    val offerStartOver = graph.choices.offerStartOver
    // Audio and Subtitles, Picture Size and the stream info (brief 4.10), as the player has them now.
    val tracks by graph.player.tracks.collectAsState()
    val video by graph.player.video.collectAsState()
    val sound by graph.player.audio.collectAsState()
    val aspect by graph.player.aspectRatio.collectAsState()
    val bitrate by remember(graph) { graph.player.bitrate }.collectAsState(null)
    val passing by remember(graph) { graph.passingThrough() }.collectAsState(null)
    val sizes by graph.pictureSizes.collectAsState(emptyMap())
    val afterFallback by graph.player.afterFallback.collectAsState()
    val sleep by graph.sleep.state.collectAsState()
    val format = streamFormat(video, tracks.playingAudio, sound, passthrough = passing == StreamKeys.channelOf(key))
    val rate = bitrateText(bitrate)
    val playerStatus = if (endedEarly) PlayerStatus.Failed(PlayerProblem.SourceUnavailable) else statusFor(status, key)
    val base = remember(current, playerStatus, tuned, sourceName, choices, engineName, nextEngineName, due, archive, archived, offerStartOver, endedEarly, closed) {
        states.player(current, listId, channelId, playerStatus, sourceName, nextSource, choices, engineName, nextEngineName)?.let { player ->
            player.copy(
                channels = archive?.let { states.withArchive(player.channels, tuned, key, archived) } ?: player.channels,
                reminder = due?.let { states.reminder(current, it, player.channels) },
                offerStartOver = offerStartOver,
                // Back from a locked channel's PIN, the player opens again on the one before it.
                currentChannelId = tuned,
            )
        }
    } ?: return
    // Paused, the picture falls behind live by the second (5.5).
    val paused = archive?.pausedAt != null || behind?.paused == true || heldAt != null
    val pausedNow by produceState(graph.clock.now(), paused) {
        value = graph.clock.now()
        while (paused) {
            delay(SECOND_MS)
            value = graph.clock.now()
        }
    }
    val at = if (paused) pausedNow else current.now
    // What changes by the second stays out of the rest.
    val state = base.copy(
        playback = base.playback.copy(
            behindLiveMs = archive?.behindMs(at) ?: behind?.behindMs(at.toEpochMilli()) ?: heldAt?.let { Duration.between(it, at).toMillis().coerceAtLeast(0) } ?: 0,
            paused = paused,
            rewindReachMs = graph.player.rewindReachMs() ?: 0,
            tracks = tracks,
            pictureSize = sizes[tuned] ?: PictureSize.Fit,
            pictureAspect = aspect,
            format = format,
            bitrate = rate,
            health = streamHealth(playerStatus, afterFallback),
            sleep = (sleep as? SleepState.Set)?.let { SleepInfo(it.choice, it.at) },
        ),
    )
    PlayerScreen(
        state = state,
        actions = remember(actions, channelId) {
            PlayerActions(
                onBack = actions.onBack,
                onChannelList = actions.onChannelList,
                onMultiview = actions.onMultiview,
                onTune = {
                    tuned = it
                    archive = null
                    heldAt = null
                },
                onRetry = {
                    val playing = archive
                    val now = graph.clock.now()
                    when {
                        playing == null -> graph.player.retry()
                        // The archive stopped early: it opens again where the picture stopped.
                        endedEarly -> archive = Archive(playing.position(now), now)
                        else -> {
                            archive = Archive(playing.start, now)
                            graph.player.retry()
                        }
                    }
                },
                onFavorite = graph::setFavorite,
                onOpenChannel = actions.onOpenChannel,
                onReminderDone = { shownReminder?.let { graph.reminderClock.done(it.id) } },
                onStartOver = { id, start ->
                    if (id == tuned) {
                        heldAt = null
                        val again = archive?.start == start
                        archive = Archive(start, graph.clock.now())
                        // Already on that program from its start: the stream opens there again.
                        if (again) graph.player.retry()
                    }
                },
                onGoLive = {
                    seeking[0]?.cancel()
                    archive = null
                    // Held on its picture without the buffer: the channel opens again at the live edge.
                    if (heldAt != null) {
                        heldAt = null
                        graph.player.retry()
                    } else {
                        graph.player.goLive()
                    }
                },
                onSeek = { behindMs ->
                    seeking[0]?.cancel()
                    seeking[0] = graph.scope.launch {
                        delay(SEEK_SETTLE_MS)
                        val now = graph.clock.now()
                        val held = heldAt
                        heldAt = null
                        val withArchive = hasArchive(graph.world.value, tuned)
                        when {
                            // Back to the live edge.
                            behindMs <= 0 -> {
                                archive = null
                                if (held != null) graph.player.retry() else graph.player.goLive()
                            }
                            // Within a program from the archive: it opens again there.
                            archive != null -> archive = Archive(now.minusMillis(behindMs), now)
                            // Live TV: the rewind buffer, or the archive when the buffer doesn't reach back that far.
                            graph.player.seekLive(behindMs, orOldest = !withArchive) == null && withArchive -> archive = Archive(now.minusMillis(behindMs), now)
                        }
                    }
                },
                onSwitchSource = { chosen -> graph.switchSource(currentKey, chosen) },
                onRecord = graph::record,
                onRecordSeries = graph::recordSeries,
                onForgetSource = { graph.forgetSource(currentKey) },
                onSwitchPlayer = graph.player::switchEngine,
                onAudio = graph.player::selectAudio,
                onSubtitles = graph.player::selectSubtitles,
                onPictureSize = { size -> graph.setPictureSize(currentKey, size) },
                onNextAudio = graph::nextAudio,
                onNextSubtitles = graph::nextSubtitles,
                onSleepTimer = graph.sleep::set,
                onPause = { paused ->
                    val playing = archive
                    val now = graph.clock.now()
                    val held = heldAt
                    when {
                        // Held without the buffer: the archive plays on from where it stopped (5.5).
                        held != null -> if (!paused) {
                            heldAt = null
                            archive = Archive(held, now)
                        }
                        playing == null && !paused -> graph.player.setPaused(false)
                        // Live TV pauses into the rewind buffer; without one, a channel with an
                        // archive holds its picture until the archive takes over.
                        playing == null -> if (!graph.player.pauseLive() && hasArchive(graph.world.value, tuned)) {
                            graph.player.holdPicture()
                            heldAt = now
                        }
                        paused -> {
                            archive = playing.copy(pausedAt = now)
                            graph.player.setPaused(true)
                        }
                        else -> {
                            val pausedFor = playing.pausedAt?.let { Duration.between(it, now) } ?: Duration.ZERO
                            if (pausedFor > REOPEN_AFTER) {
                                // The server may have let the connection go: the archive opens again
                                // where the picture stopped (brief 5.5).
                                archive = Archive(playing.position(now), now)
                            } else {
                                archive = playing.copy(pausedMs = playing.pausedMs + pausedFor.toMillis(), pausedAt = null)
                                graph.player.setPaused(false)
                            }
                        }
                    }
                },
            )
        },
        // A program from the archive opens on the overlay, which shows it; live TV on the zap banner.
        initialOverlay = if (from != null) PlayerOverlay.Controls else PlayerOverlay.Zap,
    )
}

/** Whether [channelId] has the provider's archive, for catch-up past the rewind buffer (5.5). */
private fun hasArchive(world: LiveWorld?, channelId: String): Boolean = world?.byId?.get(channelId)?.let(world::hasCatchUp) == true

/** How long a run of seeks rests before the stream opens where it ended. */
private const val SEEK_SETTLE_MS = 400L

/** Paused, how far behind live the picture is moves on each second. */
private const val SECOND_MS = 1_000L

/** The source a channel's id, or its key in the archive, names: "sourceId:key". */
private fun sourceOf(key: String): String = StreamKeys.channelOf(key).substringBefore(':')

/**
 * Playing from the archive: from [start], since [since], paused for [pausedMs] so far, and paused
 * now since [pausedAt].
 */
private data class Archive(val start: Instant, val since: Instant, val pausedMs: Long = 0, val pausedAt: Instant? = null) {
    /** How far behind live the picture is at [now]: where it started, and every pause since. */
    fun behindMs(now: Instant): Long =
        (Duration.between(start, since).toMillis() + pausedMs + (pausedAt?.let { Duration.between(it, now).toMillis() } ?: 0)).coerceAtLeast(0)

    /** Where the picture is at [now]. */
    fun position(now: Instant): Instant = now.minusMillis(behindMs(now))
}

/** An archive that stops this close to its program's end has played it out. */
private val NEAR_END: Duration = Duration.ofMinutes(2)

/** A pause longer than this opens the archive again on resuming, in case the server let it go. */
private val REOPEN_AFTER: Duration = Duration.ofMinutes(1)

/** The player's status for the channel on screen; another channel's status means it's still opening. */
internal fun statusFor(status: LiveStatus, channelId: String): PlayerStatus = when {
    status is LiveStatus.Playing && status.channelId == channelId -> PlayerStatus.Playing
    status is LiveStatus.Reconnecting && status.channelId == channelId -> PlayerStatus.Reconnecting
    status is LiveStatus.Failed && status.channelId == channelId -> PlayerStatus.Failed(
        when (status.failure) {
            LiveFailure.ConnectionLimit -> PlayerProblem.ConnectionLimit
            LiveFailure.Refused -> PlayerProblem.Refused
            LiveFailure.SourceUnavailable -> PlayerProblem.SourceUnavailable
            LiveFailure.NotVideo -> PlayerProblem.NotVideo
            LiveFailure.CantPlay -> PlayerProblem.CantPlay
            LiveFailure.NoStream -> PlayerProblem.NoStream
        },
    )
    else -> PlayerStatus.Opening
}

/**
 * Connect on Add a Source (brief 4.21 and 6.4): save the source, load its channels, and load the
 * guide in the background. A new source whose channels don't load isn't kept, so Connect can
 * simply be tried again.
 */
suspend fun connect(graph: AppGraph, form: AddSourceForm): ConnectOutcome {
    var added: AddedSource? = null
    var connected = false
    try {
        val details = detailsOf(form)
        added = when (form.type) {
            SourceType.Xtream -> graph.data.sources.addXtream(form.server, form.username, form.password, form.name, details)
            SourceType.M3U -> graph.data.sources.addM3u(form.server, form.name, form.guide, details)
            SourceType.Stalker -> graph.data.sources.addStalker(form.server, form.mac, form.name, details)
        }
        val source = added.source
        val imported = graph.data.importer.importChannels(source.id)
        // Primary source on the form: Plasma tries it first (5.1).
        if (form.primary && added.isNew) graph.data.sources.setPrimary(source.id)
        graph.importTheRest(source.id)
        connected = true
        return ConnectOutcome.Connected(
            name = source.name,
            activeUntil = source.expiresAt?.atZone(ZoneId.systemDefault())?.toLocalDate(),
            connections = source.maxConnections,
            channels = imported.channels,
            hasAccount = form.type != SourceType.M3U,
            knowsConnections = form.type == SourceType.Xtream,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        return ConnectOutcome.Failed(problemOf(error, form.type == SourceType.Stalker))
    } finally {
        // Also when Add a Source is left while it connects.
        if (!connected) added?.takeIf { it.isNew }?.let { withContext(NonCancellable) { graph.data.sources.remove(it.source.id) } }
    }
}

/**
 * Save on Edit (brief 4.20): the new details are checked and the channels load again; if that
 * fails, the source stays as it was.
 */
suspend fun save(graph: AppGraph, sourceId: String, form: AddSourceForm): ConnectOutcome = try {
    val sources = graph.data.sources
    val imported = sources.keepingOnFailure(sourceId) {
        val details = detailsOf(form)
        when (form.type) {
            SourceType.Xtream -> sources.updateXtream(sourceId, form.server, form.username, form.password, form.name, details)
            SourceType.M3U -> sources.updateM3u(sourceId, form.server, form.name, form.guide, details)
            SourceType.Stalker -> sources.updateStalker(sourceId, form.server, form.mac, form.name, details)
        }
        graph.data.importer.importChannels(sourceId)
    }
    graph.importGuide(sourceId)
    val source = sources.get(sourceId)
    ConnectOutcome.Connected(
        name = source?.name ?: form.name,
        activeUntil = source?.expiresAt?.atZone(ZoneId.systemDefault())?.toLocalDate(),
        connections = source?.maxConnections,
        channels = imported.channels,
        hasAccount = form.type != SourceType.M3U,
        knowsConnections = form.type == SourceType.Xtream,
    )
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    ConnectOutcome.Failed(problemOf(error, form.type == SourceType.Stalker))
}

/** What Edit's form starts with, or null when the source has gone. */
suspend fun editState(graph: AppGraph, sourceId: String): AddSourceState? {
    val source = graph.data.sources.editable(sourceId) ?: return null
    return AddSourceState.edit(
        type = typeOf(source.type),
        name = source.name,
        address = source.address,
        username = source.username,
        guide = source.guideUrls,
        mac = source.mac,
        deviceMac = graph.data.settings.deviceMac(),
        advanced = advancedOf(source.details),
    )
}

/** Add a Source › Advanced's fields (brief 4.21) as the data layer keeps them; an empty one is the default. */
internal fun detailsOf(form: AddSourceForm): SourceDetails {
    val typed = form.advanced
    return SourceDetails(
        userAgent = typed[AdvancedFields.USER_AGENT],
        stbModel = typed[AdvancedFields.STB_MODEL]?.takeIf { it != AdvancedFields.STB_MODELS.first() },
        serialNumber = typed[AdvancedFields.SERIAL],
        deviceId = typed[AdvancedFields.DEVICE_ID],
        deviceId2 = typed[AdvancedFields.DEVICE_ID2],
        signature = typed[AdvancedFields.SIGNATURE],
        timeZone = typed[AdvancedFields.TIME_ZONE],
        login = typed[AdvancedFields.LOGIN],
        // A login keeps its password; on Edit, an empty one keeps the one stored.
        password = typed[AdvancedFields.LOGIN_PASSWORD].orEmpty().takeIf { typed[AdvancedFields.LOGIN] != null },
        streamFormat = typed[AdvancedFields.FORMAT]?.takeIf { it in AdvancedFields.FORMATS },
        extraGuide = typed[AdvancedFields.EXTRA_GUIDE],
        refreshHours = typed[AdvancedFields.REFRESH]?.toIntOrNull()?.takeIf { it in AdvancedFields.REFRESH_HOURS },
    )
}

/** What Edit's Advanced starts with: a source's details, without its password. */
internal fun advancedOf(details: SourceDetails): Map<String, String> = listOfNotNull(
    details.userAgent?.let { AdvancedFields.USER_AGENT to it },
    details.stbModel?.let { AdvancedFields.STB_MODEL to it },
    details.serialNumber?.let { AdvancedFields.SERIAL to it },
    details.deviceId?.let { AdvancedFields.DEVICE_ID to it },
    details.deviceId2?.let { AdvancedFields.DEVICE_ID2 to it },
    details.signature?.let { AdvancedFields.SIGNATURE to it },
    details.timeZone?.let { AdvancedFields.TIME_ZONE to it },
    details.login?.let { AdvancedFields.LOGIN to it },
    details.streamFormat?.let { AdvancedFields.FORMAT to it },
    details.extraGuide?.let { AdvancedFields.EXTRA_GUIDE to it },
    details.refreshHours?.let { AdvancedFields.REFRESH to it.toString() },
).toMap()

/** A source's kind as Settings names it. */
internal fun typeOf(type: DataSourceType): SourceType = when (type) {
    DataSourceType.Xtream -> SourceType.Xtream
    DataSourceType.M3u -> SourceType.M3U
    DataSourceType.Stalker -> SourceType.Stalker
}

/** Why connecting failed, in Add a Source's words; a portal that lists no channels hasn't registered the box. */
private fun problemOf(error: Exception, portal: Boolean = false): ConnectProblem = when (error) {
    is StalkerException.NotStalker -> ConnectProblem.NotStalker
    is StalkerException.Refused -> ConnectProblem.MacRefused
    is StalkerException.Blocked -> ConnectProblem.Blocked
    is StalkerException.WrongLogin -> ConnectProblem.WrongLogin
    is InvalidMacAddress -> ConnectProblem.NotMac
    is NoLiveChannels -> if (portal) ConnectProblem.MacRefused else ConnectProblem.NoChannels
    is XtreamException.WrongLogin -> ConnectProblem.WrongLogin
    is XtreamException.NotActive -> ConnectProblem.NotActive
    is XtreamException.NotXtream -> ConnectProblem.NotXtream
    is NotAPlaylist -> ConnectProblem.NotPlaylist
    is PlaylistRefused -> ConnectProblem.Refused
    else -> ConnectProblem.CantReach
}

/**
 * Settings with the real sources in Portals & Playlists, the real counts in Channels and this
 * TV's sound output in Audio; the rest of its world stays sample until its milestone.
 */
fun settingsFacts(
    sources: List<Source>,
    channelCounts: Map<String, Int>,
    tests: Map<String, SourceTest>,
    channels: ChannelSummary,
    zone: ZoneId,
    audio: AudioFacts?,
    sounds: List<ChannelSoundInfo> = emptyList(),
    home: HomeScreenFacts? = null,
): SettingsFacts {
    var facts = SettingsSamples.facts
    if (audio != null) {
        facts = facts.copy(audioConnection = audio.connection, supportedFormats = audio.supported, unsupportedFormats = audio.unsupported)
    }
    // The TV's Home button is real before any source is added (brief 4.23 in first run).
    if (home != null) facts = facts.copy(home = home)
    if (sources.isEmpty()) return facts
    return facts.copy(
        // They come in priority order: the first is the primary source.
        sources = sources.mapIndexed { index, source ->
            val portal = source.type == DataSourceType.Stalker
            SourceInfo(
                id = source.id,
                name = source.name,
                type = typeOf(source.type),
                host = source.server.toHttpUrlOrNull()?.host ?: source.server,
                health = healthOf(source, tests[source.id]),
                // A portal's address as people type it, with its c/ page.
                server = if (portal) StalkerPortal.of(source.server)?.referer?.toString() ?: source.server else source.server,
                mac = source.mac,
                username = source.username,
                activeUntil = source.expiresAt?.atZone(zone)?.toLocalDate(),
                connectionsInUse = source.activeConnections,
                connectionLimit = source.maxConnections,
                updated = (source.channelsRefreshedAt ?: source.guideRefreshedAt ?: Instant.EPOCH).atZone(zone),
                channels = channelCounts[source.id] ?: 0,
                primary = index == 0,
                enabled = source.enabled,
                accountStatus = source.status,
                formats = source.outputFormats,
                timeZone = source.serverTimeZone,
                playback = source.playback.toInfo(),
            )
        },
        primarySource = sources.first().name,
        mergedChannels = channels.merged,
        duplicatesFixed = channels.duplicatesFixed,
        hiddenChannels = channels.hiddenChannels,
        hiddenGroups = channels.hiddenGroups,
        channelSounds = sounds,
    )
}

/**
 * Local Catch-up Channels (4.31): the channels without catch-up of their own, in number order,
 * ticked when they're [chosen], each with its source's name and connection limit.
 */
fun localChoices(world: LiveWorld?, chosen: Set<String>, sources: List<Source>): List<LocalChannelChoice> {
    world ?: return emptyList()
    val byId = sources.associateBy { it.id }
    return world.channels.filter { !it.hidden && (it.catchupDays ?: 0) == 0 }.map { channel ->
        val source = byId[channel.sourceId]
        LocalChannelChoice(channel.id, channel.number, channel.name, source?.name ?: channel.sourceId, source?.maxConnections, channel.id in chosen)
    }
}

/** The names of local catch-up's [chosen] channels, in number order, as its row shows them (4.31). */
fun localNames(world: LiveWorld?, chosen: Set<String>): List<String> =
    chosen.mapNotNull { id -> world?.byId?.get(id) }.sortedBy { it.number }.map { it.name }

/**
 * How a source is doing, as Portals & Playlists shows it: off, being tested, how its last test or
 * refresh went, or connected.
 */
private fun healthOf(source: Source, test: SourceTest?): SourceHealth = when {
    !source.enabled -> SourceHealth.Off
    test == SourceTest.Testing -> SourceHealth.Testing
    test is SourceTest.Failed -> SourceHealth.TestFailed(problemOf(test.error, portal = source.type == DataSourceType.Stalker))
    source.lastError != null -> SourceHealth.RefreshFailed
    test is SourceTest.Passed -> SourceHealth.Connected(pingMs = test.pingMs)
    else -> SourceHealth.Connected(pingMs = null)
}

/** Playback for This Source as Settings shows it, with the user agent the source asks for. */
fun SourcePlayback.toInfo() = SourcePlaybackInfo(
    streamFormat = streamFormat,
    bufferChoice = bufferChoice,
    audioOutputChoice = audioOutputChoice,
    epgOffsetMinutes = epgOffsetMinutes,
    catchupOffsetMinutes = catchupOffsetMinutes,
    userAgent = userAgent,
    engineChoice = engineChoice,
    useHub = useHub,
    referrer = referrer,
    extraHeaders = extraHeaders,
    udpxy = udpxy,
)

fun SourcePlaybackInfo.toPlayback() = SourcePlayback(
    streamFormat = streamFormat,
    bufferChoice = bufferChoice,
    audioOutputChoice = audioOutputChoice,
    epgOffsetMinutes = epgOffsetMinutes,
    catchupOffsetMinutes = catchupOffsetMinutes,
    engineChoice = engineChoice,
    useHub = useHub,
    userAgent = userAgent,
    referrer = referrer,
    extraHeaders = extraHeaders,
    udpxy = udpxy,
)

/** The sound output as Settings › Audio words it. */
data class AudioFacts(val connection: String, val supported: List<String>, val unsupported: List<String>)

/** An engine as Plasma names it: "VLC", "mpv", "Android player" (brief 4.10). */
@Composable
internal fun engineName(engine: EngineId): String = stringResource(
    when (engine) {
        EngineId.Vlc -> R.string.engine_vlc
        EngineId.Mpv -> R.string.engine_mpv
        EngineId.Media3 -> R.string.engine_media3
    },
)
