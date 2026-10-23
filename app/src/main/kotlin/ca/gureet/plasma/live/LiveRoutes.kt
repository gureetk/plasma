// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ca.gureet.plasma.AppGraph
import ca.gureet.plasma.core.data.AddedSource
import ca.gureet.plasma.core.data.NoLiveChannels
import ca.gureet.plasma.core.data.PlaylistRefused
import ca.gureet.plasma.core.data.Source
import ca.gureet.plasma.feature.guide.GuideActions
import ca.gureet.plasma.feature.guide.GuideScreen
import ca.gureet.plasma.feature.home.HomeActions
import ca.gureet.plasma.feature.home.HomeSamples
import ca.gureet.plasma.feature.home.HomeScreen
import ca.gureet.plasma.feature.livetv.LiveTvActions
import ca.gureet.plasma.feature.livetv.LiveTvScreen
import ca.gureet.plasma.feature.player.PlayerActions
import ca.gureet.plasma.feature.player.PlayerProblem
import ca.gureet.plasma.feature.player.PlayerScreen
import ca.gureet.plasma.feature.player.PlayerStatus
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
import ca.gureet.plasma.source.m3u.NotAPlaylist
import ca.gureet.plasma.source.xtream.XtreamException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
            )
        },
    )
}

@Composable
fun LiveGuide(graph: AppGraph, states: LiveStates, actions: GuideActions) {
    val world by graph.world.collectAsState()
    val current = world ?: return
    // A guide with no channels yet has nothing to lay out.
    if (current.channels.none { !it.hidden }) return
    GuideScreen(state = remember(current) { states.guide(current) }, actions = actions)
}

/**
 * The player on [channelId], zapping through [listId]. Each channel tuned is noted as watched, for
 * Recently Watched and Last Channel (brief 5.2).
 */
@Composable
fun LivePlayer(graph: AppGraph, states: LiveStates, channelId: String, listId: String, actions: PlayerActions) {
    val world by graph.world.collectAsState()
    val status by graph.player.status.collectAsState()
    val sources by graph.data.sources.sources.collectAsState(emptyList())
    var tuned by remember(channelId) { mutableStateOf(channelId) }
    LaunchedEffect(tuned) { graph.data.channels.watched(tuned, listId) }
    // Full screen, the TV may switch to the video's frame rate (Match Frame Rate, brief 5.4.4).
    DisposableEffect(graph) {
        graph.player.setFullScreen(true)
        onDispose { graph.player.setFullScreen(false) }
    }
    val current = world ?: return
    val sourceName = current.byId[tuned]?.sourceId?.let { id -> sources.firstOrNull { it.id == id }?.name }
    val state = remember(current, status, tuned, sourceName) {
        states.player(current, listId, channelId, statusFor(status, tuned), sourceName)
    } ?: return
    PlayerScreen(
        state = state,
        actions = remember(actions, channelId) {
            PlayerActions(
                onBack = actions.onBack,
                onChannelList = actions.onChannelList,
                onMultiview = actions.onMultiview,
                onTune = { tuned = it },
                onRetry = { graph.player.retry() },
                onFavorite = { id, favorite -> graph.scope.launch { graph.data.channels.setFavorite(id, favorite) } },
            )
        },
    )
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
