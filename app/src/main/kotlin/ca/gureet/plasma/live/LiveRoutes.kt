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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import ca.gureet.plasma.AppGraph
import ca.gureet.plasma.SourceTest
import ca.gureet.plasma.core.data.AddedSource
import ca.gureet.plasma.core.data.InvalidMacAddress
import ca.gureet.plasma.core.data.NoLiveChannels
import ca.gureet.plasma.core.data.PlaylistRefused
import ca.gureet.plasma.core.data.Programme
import ca.gureet.plasma.core.data.Source
import ca.gureet.plasma.core.data.SourcePlayback
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
import ca.gureet.plasma.feature.player.SourceChoice
import ca.gureet.plasma.feature.search.SearchActions
import ca.gureet.plasma.feature.search.SearchFocus
import ca.gureet.plasma.feature.search.SearchResults
import ca.gureet.plasma.feature.search.SearchScreen
import ca.gureet.plasma.feature.settings.AddSourceForm
import ca.gureet.plasma.feature.settings.AddSourceState
import ca.gureet.plasma.feature.settings.ConnectOutcome
import ca.gureet.plasma.feature.settings.ConnectProblem
import ca.gureet.plasma.feature.settings.SettingsFacts
import ca.gureet.plasma.feature.settings.SettingsSamples
import ca.gureet.plasma.feature.settings.SourceHealth
import ca.gureet.plasma.feature.settings.SourceInfo
import ca.gureet.plasma.feature.settings.SourcePlaybackInfo
import ca.gureet.plasma.feature.settings.SourceType
import ca.gureet.plasma.player.LiveFailure
import ca.gureet.plasma.player.LiveStatus
import ca.gureet.plasma.player.StreamKeys
import ca.gureet.plasma.source.m3u.NotAPlaylist
import ca.gureet.plasma.source.stalker.StalkerDevice
import ca.gureet.plasma.source.stalker.StalkerException
import ca.gureet.plasma.source.stalker.StalkerPortal
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
    val playingFrom by graph.player.playingFrom.collectAsState()
    val sources by graph.data.sources.sources.collectAsState(emptyList())
    val due by graph.reminderClock.due.collectAsState()
    val shownReminder by rememberUpdatedState(due)
    var tuned by remember(channelId) { mutableStateOf(channelId) }
    // Playing from the archive: Start Over, or catch-up from the Guide, Search or Catch-up.
    var archive by remember(channelId, from) { mutableStateOf(from?.let { Archive(it, graph.clock.now()) }) }
    val key = archive?.let { StreamKeys.archive(tuned, it.start) } ?: tuned
    // The archive stopped well before its program's end: a message with Try Again.
    var endedEarly by remember(key) { mutableStateOf(false) }
    LaunchedEffect(tuned) { graph.data.channels.watched(tuned, listId) }
    // Its program may be older than the guide kept around now, for the overlay to show.
    val archived by produceState<Programme?>(null, key) {
        val start = archive?.start
        val epgKey = world?.byId?.get(tuned)?.epgKey
        value = if (start == null || epgKey == null) null else graph.data.guide.forChannel(epgKey, start, start.plusSeconds(1)).firstOrNull()
    }
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
    // Where the channel plays from now: one of a merged channel's sources (brief 5.1). From the
    // archive, only the sources that keep one are in the list.
    val names = sources.associate { it.id to it.name }
    val from = playingFrom?.takeIf { it.key == key }
    val sourceName = names[sourceOf(from?.candidate ?: tuned)]
    val choices = from?.candidates.orEmpty().takeIf { it.size > 1 }.orEmpty().map { candidate ->
        SourceChoice(candidate, names[sourceOf(candidate)] ?: candidate, playing = candidate == from?.candidate)
    }
    val nextSource = choices.getOrNull(choices.indexOfFirst { it.playing } + 1)?.name
    val offerStartOver = graph.choices.offerStartOver
    val state = remember(current, status, tuned, sourceName, choices, due, archive, archived, offerStartOver, endedEarly) {
        val playerStatus = if (endedEarly) PlayerStatus.Failed(PlayerProblem.SourceUnavailable) else statusFor(status, key)
        states.player(current, listId, channelId, playerStatus, sourceName, nextSource, choices)?.let { player ->
            player.copy(
                channels = archive?.let { states.withArchive(player.channels, tuned, key, archived) } ?: player.channels,
                playback = player.playback.copy(behindLiveMs = archive?.behindMs(current.now) ?: 0),
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
                onFavorite = { id, favorite -> graph.scope.launch { graph.data.channels.setFavorite(id, favorite) } },
                onOpenChannel = actions.onOpenChannel,
                onReminderDone = { shownReminder?.let { graph.reminderClock.done(it.id) } },
                onStartOver = { id, start ->
                    if (id == tuned) {
                        val again = archive?.start == start
                        archive = Archive(start, graph.clock.now())
                        // Already on that program from its start: the stream opens there again.
                        if (again) graph.player.retry()
                    }
                },
                onGoLive = { archive = null },
                onSwitchSource = graph.player::switchSource,
                onPause = { paused ->
                    val playing = archive
                    val now = graph.clock.now()
                    when {
                        // Live TV doesn't pause until the rewind buffer (M6).
                        playing == null -> graph.player.setPaused(paused)
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
 * guide in the background. A new source whose channels don't load isn't kept, so Connect can
 * simply be tried again.
 */
suspend fun connect(graph: AppGraph, form: AddSourceForm): ConnectOutcome {
    var added: AddedSource? = null
    var connected = false
    try {
        added = when (form.type) {
            SourceType.Xtream -> graph.data.sources.addXtream(form.server, form.username, form.password, form.name)
            SourceType.M3U -> graph.data.sources.addM3u(form.server, form.name, form.guide)
            SourceType.Stalker -> graph.data.sources.addStalker(form.server, form.mac, form.name)
        }
        val source = added.source
        val imported = graph.data.importer.importChannels(source.id)
        // Primary source on the form: Plasma tries it first (5.1).
        if (form.primary && added.isNew) graph.data.sources.setPrimary(source.id)
        graph.importGuide(source.id)
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
        when (form.type) {
            SourceType.Xtream -> sources.updateXtream(sourceId, form.server, form.username, form.password, form.name)
            SourceType.M3U -> sources.updateM3u(sourceId, form.server, form.name, form.guide)
            SourceType.Stalker -> sources.updateStalker(sourceId, form.server, form.mac, form.name)
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
    )
}

/** A source's kind as Settings names it. */
private fun typeOf(type: DataSourceType): SourceType = when (type) {
    DataSourceType.Xtream -> SourceType.Xtream
    DataSourceType.M3u -> SourceType.M3U
    DataSourceType.Stalker -> SourceType.Stalker
}

/** Why connecting failed, in Add a Source's words; a portal that lists no channels hasn't registered the box. */
private fun problemOf(error: Exception, portal: Boolean = false): ConnectProblem = when (error) {
    is StalkerException.NotStalker -> ConnectProblem.NotStalker
    is StalkerException.Refused -> ConnectProblem.MacRefused
    is StalkerException.Blocked -> ConnectProblem.Blocked
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
 * Settings with the real sources in Portals & Playlists, the real merged channels in Channels and
 * this TV's sound output in Audio; the rest of its world stays sample until its milestone.
 */
fun settingsFacts(
    sources: List<Source>,
    channelCounts: Map<String, Int>,
    tests: Map<String, SourceTest>,
    mergedChannels: Int,
    zone: ZoneId,
    audio: AudioFacts?,
): SettingsFacts {
    var facts = SettingsSamples.facts
    if (audio != null) {
        facts = facts.copy(audioConnection = audio.connection, supportedFormats = audio.supported, unsupportedFormats = audio.unsupported)
    }
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
                // A portal's channels go out as a MAG box (brief 7.1).
                playback = source.playback.toInfo(source.userAgent ?: if (portal) StalkerDevice.DEFAULT_MODEL else null),
            )
        },
        primarySource = sources.first().name,
        mergedChannels = mergedChannels,
    )
}

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
fun SourcePlayback.toInfo(userAgent: String? = null) = SourcePlaybackInfo(
    streamFormat = streamFormat,
    bufferChoice = bufferChoice,
    audioOutputChoice = audioOutputChoice,
    epgOffsetMinutes = epgOffsetMinutes,
    catchupOffsetMinutes = catchupOffsetMinutes,
    userAgent = userAgent,
)

fun SourcePlaybackInfo.toPlayback() = SourcePlayback(
    streamFormat = streamFormat,
    bufferChoice = bufferChoice,
    audioOutputChoice = audioOutputChoice,
    epgOffsetMinutes = epgOffsetMinutes,
    catchupOffsetMinutes = catchupOffsetMinutes,
)

/** The sound output as Settings › Audio words it. */
data class AudioFacts(val connection: String, val supported: List<String>, val unsupported: List<String>)
