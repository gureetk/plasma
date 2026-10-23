// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.view.SurfaceView
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.BufferSize
import ca.gureet.plasma.core.model.Deinterlacing
import ca.gureet.plasma.core.model.FrameRateMatch
import ca.gureet.plasma.core.model.IfStreamFails
import ca.gureet.plasma.core.model.VideoDecoding
import ca.gureet.plasma.stream.ConnectionLimitReached
import ca.gureet.plasma.stream.HubSession
import ca.gureet.plasma.stream.StreamFailure
import ca.gureet.plasma.stream.StreamHub
import ca.gureet.plasma.stream.UpstreamRequest
import ca.gureet.plasma.stream.UpstreamState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

/** Settings › Playback and Audio as the player needs them (brief 4.27 and 4.28). */
data class PlaybackPrefs(
    val buffer: BufferSize = BufferSize.Medium,
    val decoding: VideoDecoding = VideoDecoding.Hardware,
    val deinterlacing: Deinterlacing = Deinterlacing.Auto,
    /** Auto is settled before it gets here: passthrough when the TV or sound system takes Dolby or DTS. */
    val audioOutput: AudioOutput = AudioOutput.Passthrough,
    val frameRate: FrameRateMatch = FrameRateMatch.SeamlessOnly,
    /** Play Live TV Through Stream Hub (5.4.2), the kill switch. */
    val throughHub: Boolean = true,
    /** Fail Over to Backup (5.1): a source that fails hands the channel to the next candidate. */
    val failOver: Boolean = true,
    /** Player Engine (5.4.3): what a channel starts with when it has no engine of its own. */
    val engine: EngineId = EngineId.Vlc,
    /** If a Stream Won't Play (5.4.3): the engines that take over from one that can't play a stream. */
    val ifStreamFails: IfStreamFails = IfStreamFails.TryMpvThenAndroidPlayer,
    /** Connection Timeout (5.4.3): how long a picture may take before the engine is at fault. */
    val connectionTimeoutMs: Long = 15_000,
)

/**
 * Where the channel [key] on screen plays from (brief 5.1): [candidate], one of [candidates], each
 * a key [StreamResolver.resolve] takes. Switch Source lists the [candidates].
 */
data class PlayingFrom(val key: String, val candidates: List<String>, val candidate: String)

/** What the player tells the app, for its notices and what it remembers per channel (brief 5.1 and 5.4.3). */
sealed interface PlayerEvent {
    /** The key the player shows: a channel, or a programme from its archive. */
    val channelId: String

    /** [candidate]'s source failed, and [next] takes over (failover). */
    data class SourceFailed(override val channelId: String, val candidate: String, val next: String) : PlayerEvent

    /** [candidate]'s picture is on screen. */
    data class Playing(override val channelId: String, val candidate: String) : PlayerEvent

    /** [candidate] has played for ten minutes on this tune. */
    data class PlayedLong(override val channelId: String, val candidate: String) : PlayerEvent

    /** The engine [from] couldn't play the stream, and [to] plays it now (automatic fallback, 5.4.3). */
    data class SwitchedEngine(override val channelId: String, val from: EngineId, val to: EngineId) : PlayerEvent

    /** After a fallback, [engine] has played the channel for 30 s: it's the one for this channel. */
    data class EngineWorked(override val channelId: String, val engine: EngineId) : PlayerEvent

    /** Switch Player: [engine] was picked for the channel (4.10). */
    data class EngineChosen(override val channelId: String, val engine: EngineId) : PlayerEvent
}

/**
 * Owns the engines and the video layer, opens streams through the hub, and reports one status
 * whichever engine plays (brief 6.5). VLC is [engine], there from the start; [backups] make the
 * others the first time they're needed, or return null when they can't load (5.4.3). Call it on
 * the main thread; [scope] runs there too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerManager(
    private val engine: PlayerEngine,
    private val hub: StreamHub,
    private val resolver: StreamResolver,
    private val prefs: () -> PlaybackPrefs,
    private val scope: CoroutineScope,
    private val onEvent: (PlayerEvent) -> Unit = {},
    /** How long a candidate plays before [PlayerEvent.PlayedLong]. */
    private val playedLongMs: Long = PLAYED_LONG_MS,
    private val backups: Map<EngineId, () -> PlayerEngine?> = emptyMap(),
    /** How long a fallback engine plays before [PlayerEvent.EngineWorked]. */
    private val engineWorkedMs: Long = ENGINE_WORKED_MS,
    // Last, so tests can pass it as a trailing lambda.
    private val frameRates: FrameRateControl = FrameRateControl.None,
) : LiveVideo {
    private val _status = MutableStateFlow<LiveStatus>(LiveStatus.Idle)
    override val status: StateFlow<LiveStatus> = _status.asStateFlow()

    private val _playingFrom = MutableStateFlow<PlayingFrom?>(null)

    /** Where the channel on screen plays from: one of a merged channel's candidates (brief 5.1). */
    val playingFrom: StateFlow<PlayingFrom?> = _playingFrom.asStateFlow()

    /** The engine drawing into the video layer now. */
    private val active = MutableStateFlow(engine)

    /** The engine playing now, for Player Options' Switch Player (brief 4.10). */
    val engineId: StateFlow<EngineId> = active.map { it.id }.stateIn(scope, SharingStarted.Eagerly, engine.id)

    /** The engines made so far, released with the player; null for one that couldn't load. */
    private val made = mutableMapOf<EngineId, PlayerEngine?>(engine.id to engine)

    override val picture: StateFlow<String?> = combine(active.flatMapLatest { it.hasPicture }, _status) { hasPicture, status ->
        when {
            !hasPicture -> null
            status is LiveStatus.Playing -> status.channelId
            status is LiveStatus.Reconnecting -> status.channelId
            else -> null
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    override val aspectRatio: StateFlow<Float?> = active.flatMapLatest { it.video }.map { video ->
        video?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() * it.sarNum.coerceAtLeast(1) / (it.height.toFloat() * it.sarDen.coerceAtLeast(1)) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private var current: String? = null
    private var muted = true
    private var session: HubSession? = null

    /** What the open [session] was resolved to, for its source's own choices when it's taken up again. */
    private var sessionTarget: StreamTarget? = null
    private var job: Job? = null
    private val view = MutableStateFlow<SurfaceView?>(null)
    private val fullScreen = MutableStateFlow(false)

    /** The channel to bring back when Plasma returns to the front or the TV turns on (5.20). */
    private var paused: String? = null

    /** A connection kept for a while after stopping, so coming straight back doesn't reconnect (5.20). */
    private var held: Held? = null

    private class Held(val channelId: String, val from: PlayingFrom?, val engine: EngineId, val session: HubSession, val target: StreamTarget?, val release: Job)

    /** Switch Player's pick, for the stream playing now. */
    private val switchTo = MutableStateFlow<EngineId?>(null)

    /** Switch Player's pick, for a channel that has to be tuned again. */
    private var chosenEngine: EngineId? = null

    init {
        // Match Frame Rate is for the full-screen player, not Home's picture (brief 5.4.4). A new
        // channel's rate is unknown for a moment; the last one stays asked for until it's known,
        // so zapping between channels at the same rate never switches the TV twice.
        scope.launch {
            var lastRate: Float? = null
            combine(fullScreen, active.flatMapLatest { it.video }.map { it?.frameRate }.distinctUntilChanged(), view) { full, rate, surface ->
                if (rate != null) lastRate = rate
                surface to lastRate.takeIf { full }
            }.distinctUntilChanged().collect { (surface, rate) -> frameRates.apply(surface, rate, prefs().frameRate) }
        }
    }

    override fun attach(view: SurfaceView) {
        this.view.value = view
        active.value.attach(view)
    }

    override fun detach(view: SurfaceView) {
        if (this.view.value !== view) return
        active.value.detach()
        this.view.value = null
    }

    override fun show(channelId: String?, muted: Boolean) {
        if (this.muted != muted) {
            this.muted = muted
            active.value.setVolume(if (muted) 0 else FULL_VOLUME)
        }
        // The channel already playing carries on: a preview going full screen never restarts.
        if (channelId == current && _status.value !is LiveStatus.Failed) return
        current = channelId
        job?.cancel()
        chosenEngine = null
        switchTo.value = null
        // Something new on screen: there's nothing to bring back any more.
        if (channelId != null) paused = null
        if (channelId == null) {
            stopPlayback()
            releaseHeld()
            _playingFrom.value = null
            _status.value = LiveStatus.Idle
            return
        }
        job = scope.launch { play(channelId) }
    }

    /** The player is full screen: the TV may switch to the video's frame rate (Match Frame Rate). */
    fun setFullScreen(on: Boolean) {
        fullScreen.value = on
    }

    /**
     * Pauses or plays on (brief 4.9). Only a program from the archive pauses for now; live TV
     * waits for the rewind buffer (M6), so pausing it does nothing.
     */
    fun setPaused(paused: Boolean) {
        val key = current ?: return
        if (StreamKeys.archiveStart(key) == null) return
        if (paused) active.value.pause() else active.value.play()
    }

    /** Tunes the channel again, as Try Again does after an error. */
    fun retry() {
        val channelId = current ?: return
        current = null
        show(channelId, muted)
    }

    /**
     * Switch Source (brief 4.10): the channel on screen plays from [candidate], one of
     * [PlayingFrom.candidates]; failover walks the others after it.
     */
    fun switchSource(candidate: String) {
        val channelId = current ?: return
        switchTo.value = null
        job?.cancel()
        releaseHeld()
        job = scope.launch { play(channelId, first = candidate) }
    }

    /**
     * Switch Player (brief 4.10): the channel on screen plays with the next engine, VLC → mpv →
     * Android player, on the same connection to the provider. The app remembers the pick.
     */
    fun switchEngine() {
        val channelId = current ?: return
        val next = nextEngine() ?: return
        onEvent(PlayerEvent.EngineChosen(channelId, next))
        if (job?.isActive == true && _status.value !is LiveStatus.Failed) {
            switchTo.value = next
            return
        }
        // Nothing's playing to switch: the channel is tuned again with it.
        chosenEngine = next
        job?.cancel()
        releaseHeld()
        job = scope.launch { play(channelId) }
    }

    /** The engine after [id] in VLC → mpv → Android player, round again, that this TV has. */
    fun nextEngine(id: EngineId = active.value.id): EngineId? {
        val from = ENGINE_ORDER.indexOf(id)
        return (1 until ENGINE_ORDER.size).map { ENGINE_ORDER[(from + it) % ENGINE_ORDER.size] }.firstOrNull(::offers)
    }

    /** This TV has [id]: it's built in, and hasn't failed to load. */
    private fun offers(id: EngineId): Boolean = when {
        id == engine.id -> true
        id !in backups -> false
        else -> id !in made || made[id] != null
    }

    /**
     * Plasma left the front, or the TV turned off (brief 5.20): the picture and sound stop at once.
     * The provider connection stays open for [graceMs], so coming straight back carries on at the
     * live edge without reconnecting; then it's let go, which frees it for the account.
     */
    fun standby(graceMs: Long) {
        val channelId = current ?: return
        paused = channelId
        current = null
        val from = _playingFrom.value
        _playingFrom.value = null
        job?.cancel()
        job = null
        active.value.stop()
        val open = session
        val target = sessionTarget
        session = null
        sessionTarget = null
        releaseHeld()
        if (open != null && graceMs > 0) {
            held = Held(channelId, from, active.value.id, open, target, scope.launch { delay(graceMs); releaseHeld() })
        } else {
            open?.close()
        }
        _status.value = LiveStatus.Idle
    }

    /** Plasma is in front again, or the TV is on: the channel that was playing plays again (5.20). */
    fun resume() {
        val channelId = paused ?: return
        paused = null
        show(channelId, muted)
    }

    fun release() {
        job?.cancel()
        stopPlayback()
        releaseHeld()
        made.values.filterNotNull().forEach { it.release() }
    }

    private suspend fun play(channelId: String, first: String? = null) {
        _status.value = LiveStatus.Opening(channelId)
        val settings = prefs()
        // The channel's own engine, from Switch Player or a fallback that worked (5.4.3).
        val ownEngine = chosenEngine ?: channelEngine(channelId)
        chosenEngine = null

        // Back on the channel whose connection was kept: it carries on from the live edge (5.20).
        val kept = held?.takeIf { it.channelId == channelId && it.session.isUsable }
        if (kept != null) {
            held = null
            kept.release.cancel()
            stopPlayback()
            session = kept.session
            sessionTarget = kept.target
            _playingFrom.value = kept.from
            var player = kept.engine
            while (true) {
                useEngine(playerFor(player) ?: engine)
                if (watch(channelId, kept.session.url, direct = null, settings.forTarget(kept.target), kept.session.state) != Outcome.EngineSwitch) return
                // Switch Player, on the same connection.
                player = switchTo.value ?: player
                switchTo.value = null
            }
        }
        // Another channel: the kept connection goes first, for accounts with one connection (5.1).
        releaseHeld()

        // A merged channel's candidates in turn: when a source fails, the next one takes over (5.1).
        // The one picked in Switch Source goes first.
        val candidates = candidatesOf(channelId)
        val order = if (first != null && first in candidates) listOf(first) + (candidates - first) else candidates
        for ((index, candidate) in order.withIndex()) {
            val more = settings.failOver && index < order.lastIndex
            if (index > 0) _status.value = LiveStatus.Opening(channelId)
            _playingFrom.value = PlayingFrom(channelId, candidates, candidate)
            val outcome = coroutineScope {
                val reports = launch { reportPlaying(channelId, candidate) }
                playCandidate(channelId, candidate, settings, failOver = more, ownEngine).also { reports.cancel() }
            }
            if (outcome == Outcome.Done) return
            onEvent(PlayerEvent.SourceFailed(channelId, candidate, order[index + 1]))
        }
    }

    /** Says when [candidate]'s picture is up, and when it has played ten minutes (brief 5.1). */
    private suspend fun reportPlaying(channelId: String, candidate: String) {
        _status.first { it == LiveStatus.Playing(channelId) }
        onEvent(PlayerEvent.Playing(channelId, candidate))
        delay(playedLongMs)
        onEvent(PlayerEvent.PlayedLong(channelId, candidate))
    }

    /** What to try for [channelId], best first: itself, or a merged channel's candidates. */
    private suspend fun candidatesOf(channelId: String): List<String> = try {
        resolver.candidates(channelId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        emptyList()
    }.distinct().ifEmpty { listOf(channelId) }

    /** The engine [channelId] has of its own, if any. */
    private suspend fun channelEngine(channelId: String): EngineId? = try {
        resolver.engineFor(channelId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        null
    }

    /**
     * Plays [candidate] as [channelId] until it ends or fails. [Outcome.SourceFailed] means its
     * source failed and, with [failOver], the next candidate should take over; it's never shown.
     * The channel's [ownEngine], the source's or Settings' plays it first; when it can't play what
     * the hub delivers, the next engine tries the same stream (5.4.3).
     */
    private suspend fun playCandidate(channelId: String, candidate: String, settings: PlaybackPrefs, failOver: Boolean, ownEngine: EngineId?): Outcome {
        val target = try {
            resolver.resolve(candidate)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            null
        }
        if (target == null) {
            if (failOver) return Outcome.SourceFailed
            fail(channelId, LiveFailure.NoStream)
            return Outcome.Done
        }
        stopPlayback()
        val chosen = settings.forTarget(target)
        var runs = runsFrom(ownEngine ?: target.engine ?: settings.engine, chosen)
        if (target.throughHub && chosen.throughHub) {
            val opened = try {
                hub.open(UpstreamRequest(target.url, target.headers, target.budget))
            } catch (limit: ConnectionLimitReached) {
                // This source's connections are all in use; another source has its own (5.1).
                if (failOver) return Outcome.SourceFailed
                fail(channelId, LiveFailure.ConnectionLimit)
                return Outcome.Done
            }
            session = opened
            sessionTarget = target
            var index = 0
            // The engine that couldn't play, until the next one is there to say so.
            var failed: EngineId? = null
            hub@ while (true) {
                val run = runs[index]
                val player = playerFor(run.id)
                if (player == null) {
                    // It couldn't load on this TV: the engines after it, or VLC when none is left
                    // and VLC hasn't had its turn (5.4.3).
                    runs = runs.take(index) + runs.drop(index).filter { it.id != run.id }
                    if (index > runs.lastIndex) {
                        if (runs.any { it.id == engine.id }) {
                            fail(channelId, LiveFailure.CantPlay)
                            return Outcome.Done
                        }
                        runs = runs + EngineRun(engine.id, chosen.decoding)
                    }
                    continue
                }
                // mpv's copy-back is the same engine, so no notice for it.
                if (failed != null && failed != run.id) onEvent(PlayerEvent.SwitchedEngine(channelId, failed, run.id))
                useEngine(player)
                val outcome = watch(
                    channelId, opened.url, direct = null, chosen.copy(decoding = run.decoding), opened.state, failOver,
                    engineFallback = index < runs.lastIndex, waitForPicture = !target.radio, reportWorked = failed != null,
                )
                when (outcome) {
                    Outcome.Done -> return Outcome.Done
                    Outcome.SourceFailed -> {
                        stopPlayback()
                        return Outcome.SourceFailed
                    }
                    // The engine can't play what the hub delivers: the next one, on the same
                    // connection (5.4.3).
                    Outcome.EngineFailed -> {
                        failed = run.id
                        index++
                    }
                    Outcome.EngineSwitch -> {
                        runs = runsFrom(switchTo.value ?: run.id, chosen)
                        switchTo.value = null
                        index = 0
                        failed = null
                    }
                    // The address led to an HLS or DASH playlist, not a stream: the engine fetches it itself (5.4.2).
                    Outcome.Playlist -> {
                        stopPlayback()
                        break@hub
                    }
                }
            }
        }
        // Straight to the engine: a fault can't be told from the source's, so failover handles it.
        while (true) {
            val run = runs.first()
            useEngine(playerFor(run.id) ?: engine)
            val outcome = watch(channelId, target.url, direct = target, chosen.copy(decoding = run.decoding), upstream = null, failOver)
            if (outcome != Outcome.EngineSwitch) {
                if (outcome == Outcome.SourceFailed) stopPlayback()
                return outcome
            }
            runs = runsFrom(switchTo.value ?: run.id, chosen)
            switchTo.value = null
        }
    }

    /** An engine, and how it decodes: mpv's embedded decoding retries in copy-back (5.4.3). */
    private data class EngineRun(val id: EngineId, val decoding: VideoDecoding)

    /**
     * The engines to try, [first] first, then the ones If a Stream Won't Play allows, in the order
     * VLC → mpv → Android player. mpv in embedded hardware decoding is tried again in copy-back
     * before moving on, since it can't fall back by itself (6.5).
     */
    private fun runsFrom(first: EngineId, settings: PlaybackPrefs): List<EngineRun> {
        val start = if (offers(first)) first else engine.id
        val order = buildList {
            add(start)
            when (settings.ifStreamFails) {
                IfStreamFails.TryMpvThenAndroidPlayer -> addAll(ENGINE_ORDER.drop(ENGINE_ORDER.indexOf(start) + 1))
                IfStreamFails.TryAndroidPlayerOnly -> if (start != EngineId.Media3) add(EngineId.Media3)
                IfStreamFails.ShowError -> Unit
            }
        }.filter { it == start || offers(it) }
        return order.flatMap { id ->
            val retry = id == EngineId.Mpv && settings.decoding == VideoDecoding.Hardware && settings.ifStreamFails != IfStreamFails.ShowError
            listOfNotNull(EngineRun(id, settings.decoding), if (retry) EngineRun(id, VideoDecoding.HardwareCopyBack) else null)
        }
    }

    /** The engine for [id], made now if it's the first time; null when it can't load. */
    private fun playerFor(id: EngineId): PlayerEngine? {
        if (id in made) return made[id]
        val made = backups[id]?.let { make ->
            try {
                make()
            } catch (failed: RuntimeException) {
                null
            }
        }
        this.made[id] = made
        return made
    }

    /**
     * Hands the video layer and the sound to [next] (5.4.4): the old engine stops and lets go of
     * the surface first, and gets a moment to do it before the new one takes it.
     */
    private suspend fun useEngine(next: PlayerEngine) {
        val old = active.value
        if (old === next) return
        old.stop()
        old.detach()
        delay(HANDOFF_MS)
        active.value = next
        view.value?.let(next::attach)
        next.setVolume(if (muted) 0 else FULL_VOLUME)
    }

    private enum class Outcome { Done, Playlist, SourceFailed, EngineFailed, EngineSwitch }

    /**
     * Plays [url] and follows it until it fails, or until the hub finds a playlist. The hub says
     * whether data arrives, the engine whether it plays: together they tell a source fault from an
     * engine fault (brief 5.4.9). [direct] is the target when the engine opens the provider itself.
     *
     * With [engineFallback], an engine that fails, or shows no picture within the connection
     * timeout ([waitForPicture]), while the hub has data, ends it with [Outcome.EngineFailed] for
     * the next engine. [reportWorked] says when a fallback engine has played for 30 s.
     */
    private suspend fun watch(
        channelId: String,
        url: String,
        direct: StreamTarget?,
        settings: PlaybackPrefs,
        upstream: Flow<UpstreamState>?,
        failOver: Boolean = false,
        engineFallback: Boolean = false,
        waitForPicture: Boolean = false,
        reportWorked: Boolean = false,
    ): Outcome = coroutineScope {
        val player = active.value
        player.setVolume(if (muted) 0 else FULL_VOLUME)
        player.open(
            PlayRequest(
                url = url,
                live = StreamKeys.archiveStart(channelId) == null,
                buffer = settings.buffer,
                decoding = settings.decoding,
                deinterlacing = settings.deinterlacing,
                audioOutput = settings.audioOutput,
                userAgent = direct?.header("User-Agent"),
                referrer = direct?.header("Referer"),
            ),
        )
        player.play()
        val worked = if (reportWorked) {
            launch {
                _status.first { it == LiveStatus.Playing(channelId) }
                delay(engineWorkedMs)
                onEvent(PlayerEvent.EngineWorked(channelId, player.id))
            }
        } else {
            null
        }
        // Past the connection timeout, a stream with no picture is the engine's fault (5.4.3).
        val late = if (engineFallback && waitForPicture) {
            flow {
                emit(false)
                delay(settings.connectionTimeoutMs)
                emit(true)
            }
        } else {
            flowOf(false)
        }

        var outcome = Outcome.Done
        combine(upstream ?: flowOf(UpstreamState.Streaming), player.state, player.hasPicture, late, switchTo) { hub, played, picture, overdue, pick ->
            Observed(hub, played, picture, overdue, pick)
        }.transformWhile { (hub, played, picture, overdue, pick) ->
            if (pick != null) {
                outcome = Outcome.EngineSwitch
                return@transformWhile false
            }
            // A playlist behind the address, or one the hub can't open at all (udp://, rtmp://):
            // the engine gets it from the provider itself.
            if (hub is UpstreamState.Failed && (hub.failure == StreamFailure.Playlist || hub.failure is StreamFailure.Unusable)) {
                outcome = Outcome.Playlist
                return@transformWhile false
            }
            // Data arrives, but the engine can't play it: the next engine tries.
            val dataArrives = upstream != null && hub == UpstreamState.Streaming
            val noPicture = overdue && !picture && played != EngineState.Paused
            if (engineFallback && dataArrives && (played is EngineState.Failed || noPicture)) {
                outcome = Outcome.EngineFailed
                return@transformWhile false
            }
            val status = statusOf(channelId, hub, played)
            // The source failed, and another candidate can take over: the failure isn't shown.
            if (failOver && status is LiveStatus.Failed && isSourceFault(status.failure, direct != null)) {
                outcome = Outcome.SourceFailed
                return@transformWhile false
            }
            emit(status)
            status !is LiveStatus.Failed && status !is LiveStatus.Ended
        }.collect { status ->
            // The connection goes before the failure shows, so a retry or a zap finds it free.
            if (status is LiveStatus.Failed || status is LiveStatus.Ended) stopPlayback()
            _status.value = status
        }
        worked?.cancel()
        // Handing over to another engine or source: this one stops, the hub's connection stays.
        if (outcome == Outcome.EngineFailed || outcome == Outcome.EngineSwitch) player.stop()
        outcome
    }

    private data class Observed(val hub: UpstreamState, val played: EngineState, val picture: Boolean, val overdue: Boolean, val pick: EngineId?)

    /**
     * The source's fault, not the engine's (5.1): it timed out, refused, sent a web page or
     * stopped sending. Without the hub in between, a stream the engine can't open counts too, since
     * nothing tells the two apart.
     */
    private fun isSourceFault(failure: LiveFailure, direct: Boolean): Boolean = when (failure) {
        LiveFailure.SourceUnavailable, LiveFailure.Refused, LiveFailure.NotVideo -> true
        LiveFailure.CantPlay -> direct
        LiveFailure.ConnectionLimit, LiveFailure.NoStream -> false
    }

    private fun statusOf(channelId: String, hub: UpstreamState, played: EngineState): LiveStatus = when {
        hub is UpstreamState.Failed -> LiveStatus.Failed(channelId, failureOf(hub.failure))
        hub is UpstreamState.Reconnecting -> LiveStatus.Reconnecting(channelId, hub.attempt)
        played is EngineState.Failed -> LiveStatus.Failed(channelId, LiveFailure.CantPlay)
        // A program from the archive ends; live TV doesn't, so a live stream that does has stopped
        // coming from the provider.
        played == EngineState.Ended && StreamKeys.archiveStart(channelId) != null -> LiveStatus.Ended(channelId)
        played == EngineState.Ended -> LiveStatus.Failed(channelId, LiveFailure.SourceUnavailable)
        played == EngineState.Playing || played == EngineState.Paused -> LiveStatus.Playing(channelId)
        else -> LiveStatus.Opening(channelId)
    }

    private fun failureOf(failure: StreamFailure): LiveFailure = when (failure) {
        is StreamFailure.Refused -> LiveFailure.Refused
        StreamFailure.NotVideo -> LiveFailure.NotVideo
        else -> LiveFailure.SourceUnavailable
    }

    private fun fail(channelId: String, failure: LiveFailure) {
        stopPlayback()
        _status.value = LiveStatus.Failed(channelId, failure)
    }

    private fun stopPlayback() {
        active.value.stop()
        session?.close()
        session = null
        sessionTarget = null
    }

    /** The source's own Buffer Size and Audio Output, where it has them (Playback for This Source, 4.20). */
    private fun PlaybackPrefs.forTarget(target: StreamTarget?): PlaybackPrefs =
        if (target == null) this else copy(buffer = target.buffer ?: buffer, audioOutput = target.audioOutput ?: audioOutput)

    private fun releaseHeld() {
        val kept = held ?: return
        held = null
        kept.release.cancel()
        kept.session.close()
    }

    private val HubSession.isUsable: Boolean
        get() = !isClosed && state.value.let { it !is UpstreamState.Failed && it != UpstreamState.Closed }

    private fun StreamTarget.header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private companion object {
        const val FULL_VOLUME = 100

        /** A candidate that plays this long works (Remember What Works per Channel, brief 5.1). */
        const val PLAYED_LONG_MS = 10 * 60_000L

        /** A fallback engine that plays this long is the one for the channel (5.4.3). */
        const val ENGINE_WORKED_MS = 30_000L

        /** Time for one engine's decoder to let go of the surface before the next takes it. */
        const val HANDOFF_MS = 250L

        /** Fallback's order (5.4.3). */
        val ENGINE_ORDER = listOf(EngineId.Vlc, EngineId.Mpv, EngineId.Media3)
    }
}
