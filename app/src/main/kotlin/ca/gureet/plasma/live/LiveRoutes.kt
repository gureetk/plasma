// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ca.gureet.plasma.AppGraph
import ca.gureet.plasma.core.data.AddedSource
import ca.gureet.plasma.core.data.NoLiveChannels
import ca.gureet.plasma.core.data.PlaylistRefused
import ca.gureet.plasma.core.data.Programme
import ca.gureet.plasma.core.data.Source
import ca.gureet.plasma.feature.catchup.CatchUpActions
import ca.gureet.plasma.feature.catchup.CatchUpScreen
import ca.gureet.plasma.feature.guide.GuideActions
import ca.gureet.plasma.feature.guide.GuideScreen
import ca.gureet.plasma.feature.guide.GuideStale
import ca.gureet.plasma.feature.home.HomeActions
import ca.gureet.plasma.feature.home.HomeSamples
import ca.gureet.plasma.feature.home.HomeScreen
import ca.gureet.plasma.feature.livetv.LiveTvActions
import ca.gureet.plasma.feature.livetv.LiveTvScreen
import ca.gureet.plasma.feature.player.PlayerActions
import ca.gureet.plasma.feature.player.PlayerOverlay
import ca.gureet.plasma.feature.player.PlayerProblem
import ca.gureet.plasma.feature.player.PlayerScreen
import ca.gureet.plasma.feature.player.PlayerStatus
import ca.gureet.plasma.feature.search.SearchActions
import ca.gureet.plasma.feature.search.SearchFocus
import ca.gureet.plasma.feature.search.SearchResults
import ca.gureet.plasma.feature.search.SearchScreen
import ca.gureet.plasma.feature.settings.AddSourceForm
import ca.gureet.plasma.feature.settings.ConnectOutcome
import ca.gureet.plasma.feature.settings.ConnectProblem
import ca.gureet.plasma.feature.settings.SettingsFacts
import ca.gureet.plasma.feature.settings.SettingsSamples
import ca.gureet.plasma.feature.settings.SourceHealth
import ca.gureet.plasma.feature.settings.SourceInfo
import ca.gureet.plasma.feature.settings.SourceType
import ca.gureet.plasma.player.LiveFailure
import ca.gureet.plasma.player.LiveStatus
import ca.gureet.plasma.player.StreamKeys
import ca.gureet.plasma.source.m3u.NotAPlaylist
import ca.gureet.plasma.source.xtream.XtreamException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ca.gureet.plasma.core.data.SourceType as DataSourceType

/*
 * The live screens once a source is added: the same screens as on the sample data, with states
 * from the data layer and actions that change it.
 */

@Composable
fun LiveHome(graph: AppGraph, states: LiveStates, actions: HomeActions) {
    val world by graph.world.collectAsState()
    val current = world ?: return
    HomeScreen(state = remember(current) { states.home(current, HomeSamples.state) }, actions = actions)
}

@Composable
fun LiveLiveTv(graph: AppGraph, states: LiveStates, actions: LiveTvActions) {
    val world by graph.world.collectAsState()
    val current = world ?: return
    val data = graph.data.channels
    LiveTvScreen(
        state = remember(current) { states.liveTv(current, selectedGroupId = null) },
        actions = remember(actions) {
            LiveTvActions(
                onTab = actions.onTab,
                onSettings = actions.onSettings,
                onProfile = actions.onProfile,
                onWatch = actions.onWatch,
                onWatchFrom = actions.onWatchFrom,
                onCatchUp = actions.onCatchUp,
                onMultiview = actions.onMultiview,
                onFavorite = { id, favorite -> graph.scope.launch { data.setFavorite(id, favorite) } },
                onHide = { id, hidden -> graph.scope.launch { data.setHidden(id, hidden) } },
                onLock = { id, locked -> graph.scope.launch { data.setLocked(id, locked) } },
                onRemind = graph::remind,
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
    val search = remember(graph) { LiveSearch(graph.data.search::programmes) }
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
        val past = channel.epgKey?.let { key ->
            graph.data.guide.forChannel(key, now.minus(Duration.ofDays((channel.catchupDays ?: 0).toLong())), now)
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
                onRemind = graph::remind,
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
 * Last Channel (brief 5.2).
 */
@Composable
fun LivePlayer(graph: AppGraph, states: LiveStates, channelId: String, listId: String, from: Instant?, actions: PlayerActions) {
    val world by graph.world.collectAsState()
    val status by graph.player.status.collectAsState()
    val sources by graph.data.sources.sources.collectAsState(emptyList())
    val due by graph.reminderClock.due.collectAsState()
    var tuned by remember(channelId) { mutableStateOf(channelId) }
    // Playing from the archive: Start Over, or catch-up from the Guide, Search or Catch-up.
    var archive by remember(channelId, from) { mutableStateOf(from?.let { Archive(it, graph.clock.now()) }) }
    val key = archive?.let { StreamKeys.archive(tuned, it.start) } ?: tuned
    LaunchedEffect(tuned) { graph.data.channels.watched(tuned, listId) }
    // A program from the archive that plays to its end goes back to live TV.
    LaunchedEffect(status, key) {
        if ((status as? LiveStatus.Ended)?.channelId == key) archive = null
    }
    // Its program may be older than the guide kept around now, for the overlay to show.
    val archived by produceState<Programme?>(null, key) {
        val start = archive?.start
        val epgKey = world?.byId?.get(tuned)?.epgKey
        value = if (start == null || epgKey == null) null else graph.data.guide.forChannel(epgKey, start, start.plusSeconds(1)).firstOrNull()
    }
    // Full screen, the TV may switch to the video's frame rate (Match Frame Rate, brief 5.4.4).
    DisposableEffect(graph) {
        graph.player.setFullScreen(true)
        onDispose { graph.player.setFullScreen(false) }
    }
    val current = world ?: return
    val sourceName = current.byId[tuned]?.sourceId?.let { id -> sources.firstOrNull { it.id == id }?.name }
    val offerStartOver = graph.choices.offerStartOver
    val state = remember(current, status, tuned, sourceName, due, archive, archived, offerStartOver) {
        states.player(current, listId, channelId, statusFor(status, key), sourceName)?.let { player ->
            player.copy(
                channels = archive?.let { states.withArchive(player.channels, tuned, key, archived) } ?: player.channels,
                playback = player.playback.copy(behindLiveMs = archive?.behindMs ?: 0),
                reminder = due?.let { states.reminder(current, it, player.channels) },
                offerStartOver = offerStartOver,
            )
        }
    } ?: return
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
                },
                onRetry = { graph.player.retry() },
                onFavorite = { id, favorite -> graph.scope.launch { graph.data.channels.setFavorite(id, favorite) } },
                onOpenChannel = actions.onOpenChannel,
                onReminderDone = graph.reminderClock::done,
                onStartOver = { id, start -> if (id == tuned) archive = Archive(start, graph.clock.now()) },
                onGoLive = { archive = null },
                onPause = graph.player::setPaused,
            )
        },
        // A program from the archive opens on the overlay, which shows it; live TV on the zap banner.
        initialOverlay = if (from != null) PlayerOverlay.Controls else PlayerOverlay.Zap,
    )
}

/** Playing from the archive: from [start] in the program, since [since], so this far behind live. */
private data class Archive(val start: Instant, val since: Instant) {
    val behindMs: Long get() = Duration.between(start, since).toMillis().coerceAtLeast(0)
}

/** The player's status for the channel on screen; another channel's status means it's still opening. */
private fun statusFor(status: LiveStatus, channelId: String): PlayerStatus = when {
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
 * guide in the background. Xtream Codes logins and M3U playlists for now; Stalker comes in M3. A
 * new source whose channels don't load isn't kept, so Connect can simply be tried again.
 */
suspend fun connect(graph: AppGraph, form: AddSourceForm): ConnectOutcome {
    if (form.type == SourceType.Stalker) return ConnectOutcome.Failed(ConnectProblem.NotSupportedYet)
    var added: AddedSource? = null
    var connected = false
    try {
        added = if (form.type == SourceType.Xtream) {
            graph.data.sources.addXtream(form.server, form.username, form.password, form.name)
        } else {
            graph.data.sources.addM3u(form.server, form.name, form.guide)
        }
        val source = added.source
        val imported = graph.data.importer.importChannels(source.id)
        graph.importGuide(source.id)
        connected = true
        return ConnectOutcome.Connected(
            name = source.name,
            activeUntil = source.expiresAt?.atZone(ZoneId.systemDefault())?.toLocalDate(),
            connections = source.maxConnections,
            channels = imported.channels,
            hasAccount = form.type == SourceType.Xtream,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        return ConnectOutcome.Failed(problemOf(error))
    } finally {
        // Also when Add a Source is left while it connects.
        if (!connected) added?.takeIf { it.isNew }?.let { withContext(NonCancellable) { graph.data.sources.remove(it.source.id) } }
    }
}

private fun problemOf(error: Exception): ConnectProblem = when (error) {
    is XtreamException.WrongLogin -> ConnectProblem.WrongLogin
    is XtreamException.NotActive -> ConnectProblem.NotActive
    is XtreamException.NotXtream -> ConnectProblem.NotXtream
    is NotAPlaylist -> ConnectProblem.NotPlaylist
    is NoLiveChannels -> ConnectProblem.NoChannels
    is PlaylistRefused -> ConnectProblem.Refused
    else -> ConnectProblem.CantReach
}

/**
 * Settings with the real sources in Portals & Playlists and this TV's sound output in Audio; the
 * rest of its world stays sample until its milestone.
 */
fun settingsFacts(sources: List<Source>, channelCounts: Map<String, Int>, zone: ZoneId, audio: AudioFacts?): SettingsFacts {
    var facts = SettingsSamples.facts
    if (audio != null) {
        facts = facts.copy(audioConnection = audio.connection, supportedFormats = audio.supported, unsupportedFormats = audio.unsupported)
    }
    if (sources.isEmpty()) return facts
    return facts.copy(
        sources = sources.map { source ->
            val m3u = source.type == DataSourceType.M3u
            SourceInfo(
                id = source.id,
                name = source.name,
                type = if (m3u) SourceType.M3U else SourceType.Xtream,
                host = source.server.toHttpUrlOrNull()?.host ?: source.server,
                health = if (source.lastError != null) SourceHealth.RefreshFailed else SourceHealth.Connected(pingMs = null),
                server = source.server,
                username = source.username,
                activeUntil = source.expiresAt?.atZone(zone)?.toLocalDate(),
                connectionsInUse = source.activeConnections,
                connectionLimit = source.maxConnections,
                updated = (source.channelsRefreshedAt ?: source.guideRefreshedAt ?: Instant.EPOCH).atZone(zone),
                channels = channelCounts[source.id] ?: 0,
            )
        },
        primarySource = sources.first().name,
    )
}

/** The sound output as Settings › Audio words it. */
data class AudioFacts(val connection: String, val supported: List<String>, val unsupported: List<String>)
