// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.app.Application
import android.content.Context
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.ChannelLineup
import ca.gureet.plasma.core.data.ChannelNumbering
import ca.gureet.plasma.core.data.EngineReason
import ca.gureet.plasma.core.data.PlasmaData
import ca.gureet.plasma.core.data.RememberedSource
import ca.gureet.plasma.core.data.ReminderClock
import ca.gureet.plasma.core.data.Source
import ca.gureet.plasma.core.data.SourceType
import ca.gureet.plasma.core.data.TuneMemory
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.BufferSize
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.core.network.UserAgents
import ca.gureet.plasma.feature.settings.ChannelSoundInfo
import ca.gureet.plasma.feature.settings.DuplicateNumbersState
import ca.gureet.plasma.feature.settings.SourceSettings
import ca.gureet.plasma.live.ChannelSummary
import ca.gureet.plasma.live.LiveWorld
import ca.gureet.plasma.live.channelSummary
import ca.gureet.plasma.live.duplicateNumbersState
import ca.gureet.plasma.live.engineOf
import ca.gureet.plasma.live.liveWorld
import ca.gureet.plasma.live.sourceHeaders
import ca.gureet.plasma.live.throughUdpxy
import ca.gureet.plasma.live.toInfo
import ca.gureet.plasma.live.toPlayback
import ca.gureet.plasma.player.AudioRoutes
import ca.gureet.plasma.player.ChannelAudio
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
import kotlinx.coroutines.flow.first
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

/** A notice for the corner of the screen (brief 4.35), with its icon, for [millis]. */
data class Notice(val text: String, val icon: PlasmaIcons = PlasmaIcons.SwitchSource, val millis: Long = 4_000)

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
     * Every channel as its source lists it, and with the same channel from several sources made
     * one (brief 5.1), worked out once for the screens, the player and Settings.
     */
    private val lineup: SharedFlow<ChannelLineup> = data.channels.lineup
        .flowOn(Dispatchers.Default)
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    private val channels: Flow<List<Channel>> = lineup.map { it.shown }

    /**
     * Every channel's number and the name it shows under, those merged into another and hidden
     * ones too: Change Number takes only a number none has (brief 5.2).
     */
    val numberOwners: Flow<Map<Int, String>> = lineup
        .map { lineup -> lineup.listed.associate { it.number to it.name } + lineup.shown.associate { it.number to it.name } }
        .flowOn(Dispatchers.Default)

    /** The channels, guide and lists the live screens draw from. */
    val world: StateFlow<LiveWorld?> = liveWorld(data, clock, channels).stateIn(scope, SharingStarted.WhileSubscribed(STOP_SHARING_MS), null)

    /**
     * Duplicate Numbers (brief 4.30) as the channels are. The fix rule is read as the channels
     * come: it changes first, so the channels it moves come with it.
     */
    val duplicateNumbers: Flow<DuplicateNumbersState> = combine(lineup, data.sources.sources) { lineup, sources ->
        duplicateNumbersState(lineup, sources, data.settings.numberingNow().duplicateRule)
    }.flowOn(Dispatchers.Default)

    /** What Settings › Channels counts (4.29): merged channels, duplicates fixed, hidden channels and groups. */
    val channelSummary: Flow<ChannelSummary> = combine(lineup, duplicateNumbers, data.channels.groups) { lineup, duplicates, groups ->
        channelSummary(lineup, duplicates, groups.count { it.hidden })
    }.flowOn(Dispatchers.Default).distinctUntilChanged()

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

        // Channel options › Volume Level and Decode This Channel (5.4.6).
        override suspend fun audioFor(key: String): ChannelAudio {
            val sound = data.playback.soundOf(StreamKeys.channelOf(key))
            return ChannelAudio(sound.levelDb, AudioOutput.DecodeToPcm.takeIf { sound.decoded })
        }
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

    init {
        // A refresh that moved channels the lists show says so (4.35); merged ones don't count.
        scope.launch {
            data.importer.newDuplicates.collect { ids ->
                attempt {
                    val shown = data.channels.lineup.flowOn(Dispatchers.Default).first().shown.mapTo(HashSet()) { it.id }
                    val count = ids.count { it in shown }
                    if (count > 0) {
                        val text = context.resources.getQuantityString(R.plurals.new_duplicates, count, count)
                        _notices.tryEmit(Notice(text, PlasmaIcons.Hash, NEW_NUMBERS_MS))
                    }
                }
            }
        }
    }

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
            engine = engineOf(chosen.playerEngine.ordinal) ?: EngineId.Vlc,
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
        // Picked in the channel's options before it played: it counts as a pick now (5.1).
        val picked = pickedSource?.takeIf { it.first == key }?.second
        pickedSource = null
        if (candidates.size < 2 || StreamKeys.archiveStart(key) != null || !choices.rememberWorks) return candidates
        val plan = try {
            data.memory.plan(key, candidates)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            return candidates
        }
        tune = TuneMemory(data.memory, plan)
        if (picked != null) memoryWork.trySend { data.memory.choose(plan, picked) }
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

    /** The channels with a sound of their own, by name, for Settings › Audio (4.28 and 5.4.6). */
    val channelSounds: Flow<List<ChannelSoundInfo>> = combine(data.playback.sounds, lineup) { sounds, lineup ->
        val names = lineup.listed.associate { it.id to it.name }
        sounds.mapNotNull { sound -> names[sound.channelId]?.let { ChannelSoundInfo(sound.channelId, it, sound.levelDb, sound.decoded) } }
    }

    /** Volume Level: OK keeps [db] as [channelId]'s level (5.4.6). */
    fun keepLevel(channelId: String, db: Int) {
        scope.launch { attempt { data.playback.setLevel(channelId, db) } }
    }

    /** Decode This Channel (5.4.6): its sound is decoded from now on, starting with the stream playing. */
    fun decodeChannel(channelId: String) {
        scope.launch {
            attempt {
                data.playback.decode(channelId)
                if (player.status.value.playingChannel() == channelId) player.retry()
            }
        }
    }

    /** Settings › Audio › Channel Volume Levels: Reset on [channelId], or Reset All. */
    fun resetSound(channelId: String?) {
        scope.launch {
            attempt {
                val playing = if (playerSlot.isInitialized()) player.status.value.playingChannel() else null
                val affected = playing?.takeIf { channelId == null || channelId == it }
                val wasDecoded = affected != null && data.playback.soundOf(affected).decoded
                if (channelId != null) data.playback.resetSound(channelId) else data.playback.resetSounds()
                // What's playing sounds as it should now: passing through again opens it again.
                if (affected != null) {
                    player.setLevel(affected, 0)
                    if (wasDecoded) player.retry()
                }
            }
        }
    }

    /**
     * The channel playing whose sound passes through to the receiver untouched, so a level can't
     * change it (5.4.6): a format the TV's output takes, with Audio Output passing it through.
     */
    fun passingThrough(): Flow<String?> = combine(player.status, player.playingFrom, player.audio, data.playback.sounds) { status, from, audio, sounds ->
        val channelId = status.playingChannel() ?: return@combine null
        val format = audio?.codec?.surround ?: return@combine null
        if (sounds.any { it.channelId == channelId && it.decoded }) return@combine null
        val sourceId = (from?.candidate ?: channelId).substringBefore(':')
        val sourceChoice = data.sources.get(sourceId)?.playback?.audioOutputChoice?.let { AudioOutput.entries.getOrNull(it) }
        val output = outputFor(sourceChoice ?: choices.audioOutput)
        channelId.takeIf { output == AudioOutput.Passthrough && format in AudioRoutes.current(context).supported }
    }

    private fun LiveStatus.playingChannel(): String? = playingKey(this)?.let(StreamKeys::channelOf)

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

    /** A source picked in a channel's options before the channel played: Remember What Works hears of it then. */
    private var pickedSource: Pair<String, String>? = null

    /**
     * Live TV › channel options › Switch Source (brief 4.10 and 5.1): [channelId] plays from
     * [candidate] now if it's the channel playing, or else the next time it plays. Either way,
     * Remember What Works hears it was picked.
     */
    fun switchSourceFor(channelId: String, candidate: String) {
        val current = tune?.takeIf { it.plan.channelId == channelId }
        if (current != null) {
            memoryWork.trySend { data.memory.choose(current.plan, candidate) }
        } else {
            pickedSource = channelId to candidate
        }
        player.switchSource(channelId, candidate)
    }

    /**
     * Live TV › channel options › Change Number (brief 4.35 and 5.2): [channelId] has [number] in
     * the full list from now on, if no channel has it; otherwise a notice says which one does.
     */
    fun changeNumber(channelId: String, number: Int) {
        scope.launch {
            attempt {
                if (data.numbering.choose(channelId, number)) return@attempt
                val owner = numberOwners.first()[number] ?: return@attempt
                _notices.tryEmit(Notice(context.getString(R.string.number_taken_notice, number, owner), icon = PlasmaIcons.Hash))
            }
        }
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

    /** Everything remembered, by the candidate id it's kept under (Live TV's Switch Source, 4.35). */
    val rememberedAll: Flow<Map<String, RememberedSource>> =
        combine(storedChoices, data.memory.observeAll()) { chosen, remembered ->
            if (SettingsChoices(chosen.orEmpty()).rememberWorks) remembered else emptyMap()
        }

    /** Forget What Works, in a channel's options: [channelId] goes back to its own order. */
    fun forgetSourceFor(channelId: String) {
        val candidates = tune?.plan?.takeIf { it.channelId == channelId }?.candidates
            ?: candidatesById.value[channelId]
            ?: return
        memoryWork.trySend { data.memory.forget(candidates) }
    }

    /** Audio Output as the engine takes it: Auto passes through what the TV or sound system takes, and decodes the rest (5.4.6). */
    private fun outputFor(chosen: AudioOutput): AudioOutput = when (chosen) {
        AudioOutput.Auto -> if (AudioRoutes.current(context).takesPassthrough) AudioOutput.Passthrough else AudioOutput.DecodeToPcm
        else -> chosen
    }

    /**
     * Playback for This Source (brief 4.20) over Settings': Player Engine, Buffer Size, Audio
     * Output and Use Stream Hub, and the referrer and extra headers its streams are asked for with.
     */
    private fun StreamTarget.withChoicesOf(source: Source?): StreamTarget {
        val playback = source?.playback ?: return this
        return copy(
            buffer = playback.bufferChoice?.let { BufferSize.entries.getOrNull(it) },
            audioOutput = playback.audioOutputChoice?.let { AudioOutput.entries.getOrNull(it) }?.let(::outputFor),
            engine = playback.engineChoice?.let(::engineOf),
            useHub = playback.useHub,
            headers = sourceHeaders(headers, playback, REFERER),
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
                // Multicast through the source's udpxy proxy, if it has one (5.4.10).
                val url = throughUdpxy(stream.url, source.playback.udpxy)
                StreamTarget(
                    channelId = channelId,
                    url = url,
                    // The playlist's own headers for the channel win over the source's (5.4.11).
                    headers = listOfNotNull(
                        (stream.userAgent ?: source.userAgent)?.let { USER_AGENT to it },
                        stream.referrer?.let { REFERER to it },
                    ).toMap(),
                    // HLS, and udp://, rtmp:// and the like, go straight to the engine; an address that
                    // only turns out to be HLS is handed over by the hub when it finds out.
                    throughHub = url.isHttp() && !url.substringBefore('?').endsWith(".m3u8", ignoreCase = true),
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
     * there, as some portals want (brief 7.1); it stops when something else plays, or nothing. A
     * merged channel can play from a portal under another source's name: it's the source playing
     * that counts.
     */
    private fun watchPortal() {
        if (portalWatch != null) return
        portalWatch = scope.launch {
            combine(player.status, player.playingFrom) { status, from ->
                playingKey(status)?.let { key -> from?.takeIf { it.key == key }?.candidate ?: key }?.let(StreamKeys::channelOf)
            }
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

    /** Something typed on Playback for This Source (brief 4.20): its user agent, referrer and headers, or udpxy proxy. */
    fun typeForSource(key: String, texts: List<String>) {
        val sourceId = SourceSettings.sourceOf(key) ?: return
        scope.launch {
            attempt {
                sourceChoices.withLock {
                    val source = data.sources.get(sourceId) ?: return@withLock
                    data.sources.setPlayback(sourceId, SourceSettings.typed(source.playback.toInfo(), key, texts).toPlayback())
                }
            }
        }
    }

    /**
     * A choice in Settings, stored. A new way of numbering, From your playlists or In playlist
     * order, numbers every channel afresh (brief 5.2); Custom starts from the numbers there are.
     * Merge Duplicate Channels turned on merges channels unmerged on Duplicate Numbers again.
     */
    fun choose(key: String, index: Int) {
        scope.launch {
            attempt {
                data.settings.choose(key, index)
                when {
                    key == SettingKeys.CHANNEL_NUMBERS && index != CUSTOM_NUMBERS -> data.numbering.numberAfresh()
                    key == SettingKeys.MERGE_DUPLICATES && index == ON -> data.settings.mergeAgain()
                }
            }
        }
    }

    /** A choice on Duplicate Numbers (brief 4.30), kept. */
    fun changeNumbers(change: suspend ChannelNumbering.() -> Unit) {
        scope.launch { attempt { data.numbering.change() } }
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

    /** Checks what's old while Plasma is in front, which as the TV's home can be for days. */
    private var refreshing: Job? = null

    /**
     * Plasma is in front again: what's old is refreshed (5.1), and checked again every half hour
     * while it stays there, and the channel that was on plays again.
     */
    fun onActivityStart() {
        refreshing?.cancel()
        refreshing = scope.launch {
            while (true) {
                attempt { data.refreshStale() }
                delay(REFRESH_CHECK_MS)
            }
        }
        standby.onActivityStart()
    }

    /** Plasma left the front, or the TV turned off (5.20). */
    fun onActivityStop() {
        refreshing?.cancel()
        refreshing = null
        standby.onActivityStop()
    }

    private fun String.isHttp() = startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private companion object {
        const val STOP_SHARING_MS = 5_000L
        const val NOTICES = 4

        /** The "New duplicate numbers" notice stays a little longer (brief 4.35). */
        const val NEW_NUMBERS_MS = 6_000L

        /** Channel Numbers' third option, Custom (4.29). */
        const val REFRESH_CHECK_MS = 30 * 60 * 1000L
        const val CUSTOM_NUMBERS = 2

        /** A switch's On, as Settings keeps it. */
        const val ON = 0
        const val USER_AGENT = "User-Agent"
        const val REFERER = "Referer"
    }
}
