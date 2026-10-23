// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.app.Application
import android.content.Context
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.EngineReason
import ca.gureet.plasma.core.data.PlasmaData
import ca.gureet.plasma.core.data.RememberedSource
import ca.gureet.plasma.core.data.ReminderClock
import ca.gureet.plasma.core.data.Source
import ca.gureet.plasma.core.data.SourceType
import ca.gureet.plasma.core.data.TuneMemory
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.BufferSize
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.core.model.PlayerEngineChoice
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.core.network.UserAgents
import ca.gureet.plasma.feature.settings.SourceSettings
import ca.gureet.plasma.live.LiveWorld
import ca.gureet.plasma.live.liveWorld
import ca.gureet.plasma.live.toInfo
import ca.gureet.plasma.live.toPlayback
import ca.gureet.plasma.player.AudioRoutes
import ca.gureet.plasma.player.DisplayFrameRates
import ca.gureet.plasma.player.EngineId
import ca.gureet.plasma.player.LiveStatus
import ca.gureet.plasma.player.PlaybackPrefs
import ca.gureet.plasma.player.PlayerEvent
import ca.gureet.plasma.player.PlayerManager
import ca.gureet.plasma.player.StreamKeys
import ca.gureet.plasma.player.StreamResolver
import ca.gureet.plasma.player.StreamTarget
import ca.gureet.plasma.player.mpv.MpvEngine
import ca.gureet.plasma.player.vlc.VlcEngine
import ca.gureet.plasma.source.xtream.StreamFormat
import ca.gureet.plasma.stream.ConnectionBudget
import ca.gureet.plasma.stream.StreamHub
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel as WorkQueue

/** A notice for the corner of the screen (brief 4.35). */
data class Notice(val text: String)

/** How Test Connection went on a source (brief 4.20). */
sealed interface SourceTest {
    data object Testing : SourceTest

    data class Passed(val pingMs: Int) : SourceTest

    data class Failed(val error: Exception) : SourceTest
}

class PlasmaApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.standby.start()
        graph.reminderClock.start()
    }
}

val Context.graph: AppGraph get() = (applicationContext as PlasmaApplication).graph

/**
 * Everything the app shares, built once (brief 6.2, "app: navigation, DI"): the data layer, the
 * stream hub, the player, and image loading. Plain constructors stand in for a DI library.
 */
class AppGraph(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val clock: PlasmaClock = PlasmaClock.System
    val data: PlasmaData = PlasmaData.create(context, UserAgents.vlc(), clock)

    /** Until a source is added, Plasma shows the boards' sample world; null while it's being checked. */
    val hasSources: StateFlow<Boolean?> = data.sources.sources.map { it.isNotEmpty() }.stateIn(scope, SharingStarted.Eagerly, null)

    /** What's chosen in Settings; null until it's been read. */
    val storedChoices: StateFlow<Map<String, Int>?> = data.settings.choices.stateIn(scope, SharingStarted.Eagerly, null)

    val choices: SettingsChoices get() = SettingsChoices(storedChoices.value.orEmpty())

    /**
     * Every channel, the same channel from several sources made one (brief 5.1), worked out once
     * for the screens, the player and Settings.
     */
    private val channels: SharedFlow<List<Channel>> = data.channels.channels
        .flowOn(Dispatchers.Default)
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    /** The channels, guide and lists the live screens draw from. */
    val world: StateFlow<LiveWorld?> = liveWorld(data, clock, channels).stateIn(scope, SharingStarted.WhileSubscribed(STOP_SHARING_MS), null)

    /** How many channels come from more than one source (Merge Duplicate Channels, 4.29). */
    val mergedCount: Flow<Int> = channels.map { list -> list.count(Channel::isMerged) }.distinctUntilChanged()

    val images: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { data.http })) }
            .build()
    }

    private val hub: StreamHub by lazy { StreamHub(data.http) }

    /**
     * Each merged channel's candidates, under every candidate's id (brief 5.1), kept current so a
     * zap finds them at once.
     */
    private val candidatesById: StateFlow<Map<String, List<String>>> = channels
        .map { channels -> buildMap { for (channel in channels) if (channel.isMerged) for (id in channel.candidates) put(id, channel.candidates) } }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Streams by key, and what to play a merged channel from. */
    private val streams = object : StreamResolver {
        override suspend fun resolve(key: String): StreamTarget? = this@AppGraph.resolve(key)

        override suspend fun candidates(key: String): List<String> = plan(key)

        // Picked in Switch Player, or the one that played after a fallback (5.4.3).
        override suspend fun engineFor(key: String): EngineId? =
            data.playback.engineFor(StreamKeys.channelOf(key))?.let { name -> EngineId.entries.firstOrNull { it.name == name } }
    }

    /**
     * The tune Remember What Works per Channel follows (brief 5.1): a merged channel on live TV,
     * with the setting on. Used on the main thread only.
     */
    private var tune: TuneMemory? = null

    /** What the player reports for [tune], kept one write at a time, in order. */
    private val memoryWork = WorkQueue<suspend () -> Unit>(WorkQueue.UNLIMITED).also { work ->
        scope.launch { for (job in work) attempt { job() } }
    }

    /**
     * libVLC loads with the first live picture, so the sample world never pays for it; libmpv the
     * first time a stream needs it (brief 5.4.3).
     */
    private val playerSlot = lazy {
        PlayerManager(
            VlcEngine(context), hub, streams, ::playbackPrefs, scope,
            onEvent = ::onPlayerEvent,
            backups = mapOf(EngineId.Mpv to { MpvEngine.create(context) }),
            frameRates = DisplayFrameRates,
        )
    }
    val player: PlayerManager by playerSlot

    private val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = NOTICES)

    /** Short notices for the corner of the screen (brief 4.35): "Switched to Backup Line." */
    val notices: SharedFlow<Notice> = _notices.asSharedFlow()

    val standby = Standby(context, { choices }, { if (playerSlot.isInitialized()) player else null })

    val reminderClock = ReminderClock(data.reminders, clock, scope) { standby.isOnScreen }

    val keys = RemoteKeyMap(data.settings, scope)

    private val _tests = MutableStateFlow<Map<String, SourceTest>>(emptyMap())

    /** How Test Connection went on each source (brief 4.20), until it refreshes or Plasma restarts. */
    val tests: StateFlow<Map<String, SourceTest>> = _tests.asStateFlow()

    /** One source's own settings change one at a time. */
    private val sourceChoices = Mutex()

    /**
     * Remind Me, or Cancel Reminder, on a programme by the id the screens give it:
     * "channelId@start in epoch seconds" (brief 5.7).
     */
    fun remind(channelId: String, programmeId: String, on: Boolean) {
        scope.launch {
            attempt {
                if (!on) {
                    data.reminders.cancel(programmeId)
                    return@attempt
                }
                val start = programmeId.substringAfterLast('@').toLongOrNull()?.let(Instant::ofEpochSecond) ?: return@attempt
                val epgKey = data.channels.get(channelId)?.epgKey ?: return@attempt
                val programme = data.guide.forChannel(epgKey, start, start.plusSeconds(1))
                    .firstOrNull { it.start.epochSecond == start.epochSecond } ?: return@attempt
                data.reminders.set(channelId, programme.title, programme.start, programme.end)
            }
        }
    }

    /** Settings › Playback and Audio, read each time a channel opens (brief 4.27 and 4.28). */
    private fun playbackPrefs(): PlaybackPrefs {
        val chosen = choices
        return PlaybackPrefs(
            buffer = chosen.buffer,
            decoding = chosen.decoding,
            deinterlacing = chosen.deinterlacing,
            audioOutput = outputFor(chosen.audioOutput),
            frameRate = chosen.frameRate,
            throughHub = chosen.throughHub,
            failOver = chosen.failOver,
            engine = when (chosen.playerEngine) {
                PlayerEngineChoice.Vlc -> EngineId.Vlc
                PlayerEngineChoice.Mpv -> EngineId.Mpv
                // Not in Plasma yet (M4): live TV starts with VLC.
                PlayerEngineChoice.AndroidPlayer -> EngineId.Media3
            },
            ifStreamFails = chosen.ifStreamFails,
            connectionTimeoutMs = chosen.connectionTimeout.millis,
        )
    }

    /**
     * What to play [key] from: a merged channel's candidates, best first (brief 5.1). A programme
     * from the archive plays from the candidates that keep one.
     */
    private suspend fun candidatesOf(key: String): List<String> {
        val members = candidatesById.value[StreamKeys.channelOf(key)] ?: return listOf(key)
        val start = StreamKeys.archiveStart(key) ?: return members
        return members.filter { id -> (data.channels.get(id)?.catchupDays ?: 0) > 0 }.map { StreamKeys.archive(it, start) }.ifEmpty { listOf(key) }
    }

    /**
     * What to play [key] from, in order (brief 5.1): a merged channel's candidates, with the one
     * Remember What Works per Channel keeps first. A programme from the archive keeps the
     * channel's own order.
     */
    private suspend fun plan(key: String): List<String> {
        val candidates = candidatesOf(key)
        tune = null
        if (candidates.size < 2 || StreamKeys.archiveStart(key) != null || !choices.rememberWorks) return candidates
        val plan = try {
            data.memory.plan(key, candidates)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            return candidates
        }
        tune = TuneMemory(data.memory, plan)
        return plan.order
    }

    private fun onPlayerEvent(event: PlayerEvent) {
        val current = tune?.takeIf { it.plan.channelId == event.channelId }
        when (event) {
            is PlayerEvent.SourceFailed -> {
                // Failover (5.1): a quiet notice of where the channel comes from now, except when
                // the week's check goes straight back to the source that works.
                if (current?.plan?.isQuietSwitch(event.candidate, event.next) != true) noticeSwitchedTo(event.next)
                if (current != null) memoryWork.trySend { current.failed(event.candidate, event.next) }
            }
            is PlayerEvent.Playing -> if (current != null) memoryWork.trySend { current.playing(event.candidate) }
            is PlayerEvent.PlayedLong -> if (current != null) memoryWork.trySend { current.playedLong(event.candidate) }
            // Automatic fallback (5.4.3): "VLC couldn't play Arena Sports 2. Playing it with mpv."
            is PlayerEvent.SwitchedEngine -> scope.launch {
                val name = data.channels.get(StreamKeys.channelOf(event.channelId))?.name ?: return@launch
                _notices.tryEmit(Notice(context.getString(R.string.engine_fallback, engineName(event.from), name, engineName(event.to))))
            }
            is PlayerEvent.EngineWorked -> rememberEngine(event.channelId, event.engine, EngineReason.Fallback)
            is PlayerEvent.EngineChosen -> rememberEngine(event.channelId, event.engine, EngineReason.User)
        }
    }

    /** The engine for a channel, from now on (brief 5.4.3). */
    private fun rememberEngine(key: String, engine: EngineId, reason: EngineReason) {
        scope.launch { attempt { data.playback.rememberEngine(StreamKeys.channelOf(key), engine.name, reason) } }
    }

    private fun engineName(engine: EngineId): String = context.getString(
        when (engine) {
            EngineId.Vlc -> R.string.engine_vlc
            EngineId.Mpv -> R.string.engine_mpv
            EngineId.Media3 -> R.string.engine_media3
        },
    )

    /** Settings › Playback › Forget Players Chosen per Channel (5.4.3), with a notice of how many. */
    fun forgetPlayers() {
        scope.launch {
            attempt {
                val count = data.playback.forgetEngines()
                val text = if (count == 0) {
                    context.getString(R.string.no_players_chosen)
                } else {
                    context.resources.getQuantityString(R.plurals.forgot_players, count, count)
                }
                _notices.tryEmit(Notice(text))
            }
        }
    }

    private fun noticeSwitchedTo(candidate: String) {
        scope.launch {
            val name = data.sources.get(StreamKeys.channelOf(candidate).substringBefore(':'))?.name ?: return@launch
            _notices.tryEmit(Notice(context.getString(R.string.switched_to_source, name)))
        }
    }

    /**
     * Switch Source (brief 4.10 and 5.1): the channel on screen, [key], plays from [candidate],
     * which it remembers when Remember What Works per Channel is on.
     */
    fun switchSource(key: String, candidate: String) {
        val current = tune?.takeIf { it.plan.channelId == key }
        if (current != null) memoryWork.trySend { data.memory.choose(current.plan, candidate) }
        player.switchSource(candidate)
    }

    /** Forget What Works, in Switch Source: the channel on screen, [key], goes back to its own order. */
    fun forgetSource(key: String) {
        val current = tune?.takeIf { it.plan.channelId == key } ?: return
        memoryWork.trySend { data.memory.forget(current.plan.candidates) }
    }

    /** What's remembered for the merged channel with [candidates] (Switch Source, brief 4.35). */
    fun remembered(candidates: List<String>): Flow<RememberedSource?> =
        combine(storedChoices, data.memory.observe(candidates)) { chosen, remembered ->
            remembered.takeIf { SettingsChoices(chosen.orEmpty()).rememberWorks }
        }

    /** Audio Output as the engine takes it: Auto passes through what the TV or sound system takes, and decodes the rest (5.4.6). */
    private fun outputFor(chosen: AudioOutput): AudioOutput = when (chosen) {
        AudioOutput.Auto -> if (AudioRoutes.current(context).takesPassthrough) AudioOutput.Passthrough else AudioOutput.DecodeToPcm
        else -> chosen
    }

    /** Playback for This Source's Buffer Size and Audio Output (brief 4.20), over Settings'. */
    private fun StreamTarget.withChoicesOf(source: Source?): StreamTarget {
        val playback = source?.playback ?: return this
        return copy(
            buffer = playback.bufferChoice?.let { BufferSize.entries.getOrNull(it) },
            audioOutput = playback.audioOutputChoice?.let { AudioOutput.entries.getOrNull(it) }?.let(::outputFor),
        )
    }

    /** A channel's stream, built at play time from its source (brief 6.5). */
    private suspend fun resolve(key: String): StreamTarget? = withContext(Dispatchers.IO) {
        // A program from the archive (catch-up and Start Over, brief 5.5) goes straight to the
        // engine: it's a recording, and the hub's reconnects are for live streams.
        StreamKeys.archiveStart(key)?.let { start ->
            val channelId = StreamKeys.channelOf(key)
            val stream = data.archive.streamOf(channelId, start) ?: return@withContext null
            val source = data.channels.get(channelId)?.let { data.sources.get(it.sourceId) }
            return@withContext StreamTarget(
                channelId = key,
                url = stream.url,
                headers = listOfNotNull(stream.userAgent?.let { USER_AGENT to it }, stream.referrer?.let { REFERER to it }).toMap(),
                throughHub = false,
            ).withChoicesOf(source)
        }
        val channelId = key
        val channel = data.channels.get(channelId) ?: return@withContext null
        val source = data.sources.get(channel.sourceId) ?: return@withContext null
        val target = when (source.type) {
            SourceType.Xtream -> {
                val format = StreamFormat.entries.firstOrNull { it.extension == source.streamFormat } ?: StreamFormat.Ts
                StreamTarget(
                    channelId = channelId,
                    url = data.sources.xtreamClient(source.id).liveUrl(channel.key, format),
                    headers = listOfNotNull(source.userAgent?.let { USER_AGENT to it }).toMap(),
                    // HLS manages its own segments, so it goes straight to the engine (5.4.2).
                    throughHub = format == StreamFormat.Ts,
                    budget = source.maxConnections?.let { ConnectionBudget(source.id, it) },
                ).withChoicesOf(source)
            }
            SourceType.Stalker -> {
                // The portal hands out an address that expires, so it's asked for at each tune (brief 7.1).
                val cmd = data.channels.streamOf(channelId)?.url ?: return@withContext null
                val portal = data.sources.stalkerClient(source.id)
                val url = portal.createLink(cmd)
                watchPortal()
                StreamTarget(
                    channelId = channelId,
                    url = url,
                    headers = portal.playbackHeaders,
                    throughHub = url.isHttp() && !url.substringBefore('?').endsWith(".m3u8", ignoreCase = true),
                ).withChoicesOf(source)
            }
            SourceType.M3u -> {
                val stream = data.channels.streamOf(channelId) ?: return@withContext null
                StreamTarget(
                    channelId = channelId,
                    url = stream.url,
                    // The playlist's own headers for the channel win over the source's (5.4.11).
                    headers = listOfNotNull(
                        (stream.userAgent ?: source.userAgent)?.let { USER_AGENT to it },
                        stream.referrer?.let { REFERER to it },
                    ).toMap(),
                    // HLS, and udp://, rtmp:// and the like, go straight to the engine; an address that
                    // only turns out to be HLS is handed over by the hub when it finds out.
                    throughHub = stream.url.isHttp() && !stream.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true),
                ).withChoicesOf(source)
            }
        }
        // A radio channel has no picture to wait for (5.4.3).
        target.copy(radio = channel.isRadio)
    }

    /** The portal watchdog runs once a portal's channel has played. */
    private var portalWatch: Job? = null

    /**
     * While a portal's channel plays, tells the portal every minute or so that the box is still
     * there, as some portals want (brief 7.1); it stops when something else plays, or nothing.
     */
    private fun watchPortal() {
        if (portalWatch != null) return
        portalWatch = scope.launch {
            player.status
                .map { status -> playingKey(status)?.let(StreamKeys::channelOf) }
                .distinctUntilChanged()
                .collectLatest { channelId ->
                    val sourceId = channelId?.let { data.channels.get(it) }?.sourceId ?: return@collectLatest
                    if (data.sources.get(sourceId)?.type != SourceType.Stalker) return@collectLatest
                    val portal = data.sources.stalkerClient(sourceId)
                    while (true) {
                        delay(portal.watchdogInterval.toMillis())
                        attempt { portal.watchdog() }
                    }
                }
        }
    }

    private fun playingKey(status: LiveStatus): String? = when (status) {
        is LiveStatus.Opening -> status.channelId
        is LiveStatus.Playing -> status.channelId
        is LiveStatus.Reconnecting -> status.channelId
        else -> null
    }

    /** The guide follows a new source's channels, in the background (brief 6.4). */
    fun importGuide(sourceId: String) {
        scope.launch { attempt { data.importer.importGuide(sourceId) } }
    }

    /** Refresh on the Guide's out-of-date line (brief 4.8): every source's guide again, but those turned off. */
    fun refreshGuides() {
        scope.launch { data.sources.all().filter { it.enabled }.forEach { attempt { data.importer.importGuide(it.id) } } }
    }

    /** Refresh Now on a source (brief 4.20): its channels, then its guide. A test before it no longer says how it is. */
    fun refresh(sourceId: String) {
        _tests.update { it - sourceId }
        scope.launch {
            if (attempt { data.importer.importChannels(sourceId) }) attempt { data.importer.importGuide(sourceId) }
        }
    }

    /** Test Connection on a source (brief 4.20): signs in, or asks for the playlist, and times it. */
    fun testSource(sourceId: String) {
        if (_tests.value[sourceId] == SourceTest.Testing) return
        _tests.update { it + (sourceId to SourceTest.Testing) }
        scope.launch {
            val result = try {
                SourceTest.Passed(data.sources.check(sourceId).toMillis().toInt())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                SourceTest.Failed(failed)
            }
            _tests.update { it + (sourceId to result) }
        }
    }

    /** Set as Primary (brief 4.20): the source Plasma tries first. */
    fun setPrimary(sourceId: String) {
        scope.launch { attempt { data.sources.setPrimary(sourceId) } }
    }

    /** Disable or Enable (brief 4.20). A source turned on again catches up if it's out of date. */
    fun setEnabled(sourceId: String, enabled: Boolean) {
        scope.launch {
            if (attempt { data.sources.setEnabled(sourceId, enabled) } && enabled) data.refreshStale()
        }
    }

    /**
     * A choice on one source's own settings, by the key and option Settings gives it: Playback for
     * This Source (brief 4.20) or its guide's time offset (5.16).
     */
    fun chooseForSource(sourceId: String, key: String, index: Int) {
        scope.launch {
            attempt {
                sourceChoices.withLock {
                    val source = data.sources.get(sourceId) ?: return@withLock
                    data.sources.setPlayback(sourceId, SourceSettings.chosen(source.playback.toInfo(), key, index).toPlayback())
                }
            }
        }
    }

    /** A Search result was opened: the query joins the recent searches (brief 5.10). */
    fun searched(query: String) {
        scope.launch { attempt { data.search.remember(query) } }
    }

    /**
     * Runs work whose failure leaves things as they were, or is noted already: an import notes it
     * on the source, for Settings to show.
     */
    private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        false
    }

    /** Plasma is in front again: what's old is refreshed (5.1) and the channel that was on plays again. */
    fun onActivityStart() {
        scope.launch { data.refreshStale() }
        standby.onActivityStart()
    }

    /** Plasma left the front, or the TV turned off (5.20). */
    fun onActivityStop() = standby.onActivityStop()

    private fun String.isHttp() = startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private companion object {
        const val STOP_SHARING_MS = 5_000L
        const val NOTICES = 4
        const val USER_AGENT = "User-Agent"
        const val REFERER = "Referer"
    }
}
