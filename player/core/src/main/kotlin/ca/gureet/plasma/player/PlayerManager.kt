// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.view.SurfaceView
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.BufferSize
import ca.gureet.plasma.core.model.ChannelTracks
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    /** Preferred Language, Skip Audio Description and Subtitles: how a stream's tracks are picked (5.4.6). */
    val tracks: TrackPrefs = TrackPrefs(),
    /** Tunneled Playback (Advanced, 4.27): for the Android player only. */
    val tunneling: Boolean = false,
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

    /** Audio and Subtitles: [track] was picked to play on the channel (5.4.6). */
    data class AudioChosen(override val channelId: String, val track: MediaTrack) : PlayerEvent

    /** Audio and Subtitles: [track] was picked to show on the channel, or subtitles turned off when it's null. */
    data class SubtitlesChosen(override val channelId: String, val track: MediaTrack?) : PlayerEvent
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
    /** Three stalls within this long hand the stream to the next engine (5.4.3). */
    private val stallWindowMs: Long = STALL_WINDOW_MS,
    /** The wall clock, for where live TV is in the rewind buffer (5.5). */
    private val clock: () -> Long = System::currentTimeMillis,
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

    private val _afterFallback = MutableStateFlow(false)

    /**
     * An engine took over from one that couldn't play the stream, and hasn't yet played it for
     * 30 s: the stream info's dot is amber till then (brief 4.10 and 5.4.3).
     */
    val afterFallback: StateFlow<Boolean> = _afterFallback.asStateFlow()

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

    /** The sound playing, once the engine knows it: whether it can pass through (5.4.6). */
    val audio: StateFlow<AudioInfo?> = active.flatMapLatest { it.audio }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The picture playing, once the engine knows it: its size, rate and format, for Player Options (4.10). */
    val video: StateFlow<VideoInfo?> = active.flatMapLatest { it.video }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The sound and subtitle tracks of what's on screen, and which are on (Audio and Subtitles, 4.10). */
    val tracks: StateFlow<MediaTracks> = active.flatMapLatest { it.tracks }.stateIn(scope, SharingStarted.Eagerly, MediaTracks())

    /**
     * A recording played as it's written (chase play, brief 5.6): the ms into it the engine
     * started reading at, where a seek is going until the engine opens there, and how much of it
     * there is so far; 0 and null otherwise.
     */
    private val chaseBase = MutableStateFlow(0L)
    private val chaseSeek = MutableStateFlow<Long?>(null)
    private val chaseLength = MutableStateFlow<Long?>(null)

    /** The recording played as it's written, while one is, and the seek in it under way. */
    @Volatile private var chasing: Chase? = null
    private var chaseSeeking: Job? = null

    /** How far into the movie or episode on screen it is, in ms (brief 4.9). */
    val position: StateFlow<Long> = combine(active.flatMapLatest { it.position }, chaseBase, chaseSeek) { at, base, seeking -> seeking ?: (base + at) }
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    /** How long the movie or episode on screen is, in ms; 0 until the engine knows. A recording being written grows. */
    val length: StateFlow<Long> = combine(active.flatMapLatest { it.length }, chaseLength) { engine, chased -> chased ?: engine }
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    override val cues: StateFlow<List<SubtitleCue>> = active.flatMapLatest { it.cues }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Where the movie or episode on screen starts when it opens: where it was left, and after a
     * new engine, a retry or standby, where it had got to.
     */
    private var vodStart = 0L

    /** The movie or episode [vodStart] is for. */
    private var vodFor: String? = null

    /**
     * How fast the stream on screen arrives through the hub, in bits a second over the last few
     * seconds (Player Options' stream info, 4.10); null for a stream that goes straight to the engine.
     */
    val bitrate: Flow<Long?> = flow {
        val samples = ArrayDeque<Pair<Long, Long>>()
        var sampled: HubSession? = null
        while (true) {
            val open = session
            if (open !== sampled) {
                samples.clear()
                sampled = open
            }
            if (open == null) {
                emit(null)
            } else {
                val now = System.nanoTime()
                samples.addLast(now to open.bytesReceived)
                while (samples.size > BITRATE_SAMPLES) samples.removeFirst()
                val (firstAt, firstBytes) = samples.first()
                val seconds = (now - firstAt) / NANOS_PER_SECOND
                emit(if (seconds >= 1.0) ((open.bytesReceived - firstBytes) * BITS_PER_BYTE / seconds).toLong() else null)
            }
            delay(BITRATE_EVERY_MS)
        }
    }

    override val aspectRatio: StateFlow<Float?> = active.flatMapLatest { it.video }.map { video ->
        video?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() * it.sarNum.coerceAtLeast(1) / (it.height.toFloat() * it.sarDen.coerceAtLeast(1)) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private var current: String? = null
    private var muted = true
    private var session: HubSession? = null

    /** What the open [session] was resolved to, for its source's own choices when it's taken up again. */
    private var sessionTarget: StreamTarget? = null

    /** The channel the open [session] plays: right after a zap, it's still the one before's. */
    private var sessionChannel: String? = null

    /** The channel playing's own sound: its level, and its Audio Output over the source's (5.4.6). */
    private var channelAudio = ChannelAudio()
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

    /**
     * The surfaces the picture can show on, the newest last: the screen's own, and the small
     * window's while it's open (brief 5.20). The picture shows on the newest; when that one goes,
     * it goes back to the one before, without the stream stopping.
     */
    private val surfaces = Surfaces<Pair<SurfaceView, SurfaceView?>> { a, b -> a.first === b.first }

    override fun attach(view: SurfaceView, subtitles: SurfaceView?) {
        surfaces.add(view to subtitles)
        showOn(view, subtitles)
    }

    override fun detach(view: SurfaceView) {
        val under = surfaces.remove(view to null) ?: return
        active.value.detach()
        this.view.value = null
        subtitlesView = null
        under.value?.let { (video, subtitles) -> showOn(video, subtitles) }
    }

    private fun showOn(view: SurfaceView, subtitles: SurfaceView?) {
        this.view.value = view
        subtitlesView = subtitles
        active.value.attach(view, subtitles)
    }

    /** The transparent surface over the video, for engines that draw subtitles apart (5.4.4). */
    private var subtitlesView: SurfaceView? = null

    override fun show(channelId: String?, muted: Boolean) {
        setMuted(muted)
        // The channel already playing carries on: a preview going full screen never restarts.
        if (channelId == current && _status.value !is LiveStatus.Failed) return
        current = channelId
        job?.cancel()
        chosenEngine = null
        switchTo.value = null
        stopChasing()
        // Something new on screen: there's nothing to bring back any more.
        if (channelId != null) paused = null
        // Another channel, or none, starts at its live edge (5.5).
        shiftTo(null)
        if (channelId == null) {
            stopPlayback()
            releaseHeld()
            _playingFrom.value = null
            _status.value = LiveStatus.Idle
            return
        }
        // A movie shown without [playVod] saying where starts at its start.
        if (StreamKeys.isVod(channelId) && channelId != vodFor) {
            vodFor = channelId
            vodStart = 0
        }
        job = scope.launch { play(channelId) }
    }

    /** Whether the sound is off, as for Home's preview (brief 4.1). */
    val isMuted: Boolean get() = muted

    /**
     * Turns the sound off or on, whatever plays or is about to: the small window's sound, and
     * another app taking it (5.20).
     */
    fun setMuted(muted: Boolean) {
        if (this.muted == muted) return
        this.muted = muted
        active.value.setVolume(if (muted) 0 else FULL_VOLUME)
    }

    /** The player is full screen: the TV may switch to the video's frame rate (Match Frame Rate). */
    fun setFullScreen(on: Boolean) {
        fullScreen.value = on
    }

    /**
     * Pauses or plays on (brief 4.9). A program from the archive, a movie or an episode pauses.
     * Live TV pauses into the rewind buffer (5.5), when Settings has one: the stream goes on into
     * it, and playing on carries on from where the picture stopped.
     */
    fun setPaused(paused: Boolean) {
        val key = current ?: return
        if (StreamKeys.archiveStart(key) == null && !StreamKeys.isVod(key)) {
            if (paused) pauseLive() else resumeLive()
            return
        }
        if (paused) active.value.pause() else active.value.play()
    }

    /**
     * Pauses live TV into the rewind buffer (brief 5.5); false when there's none (Rewind Buffer
     * off, or a stream that doesn't come through the hub).
     */
    fun pauseLive(): Boolean {
        val open = liveSession() ?: return false
        if (!open.canHold) return false
        val now = clock()
        shiftTo(BehindLive(pictureTime(now), now, paused = true))
        active.value.pause()
        // Started off the main thread: it writes what memory holds to storage first.
        scope.launch(Dispatchers.IO) { open.hold() }
        return true
    }

    /**
     * Plays on from where live TV was paused, from the same stream. Paused for about as long as the
     * buffer keeps, or longer, it plays on from a little after the oldest moment the buffer holds.
     */
    fun resumeLive() {
        val paused = _behindLive.value?.takeIf { it.paused } ?: return
        val now = clock()
        val open = liveSession()
        val rewind = open?.rewind
        val since = rewind?.since
        if (open != null && rewind != null && since != null) {
            // Where the player stopped reading: let go of already, or about to be.
            val stoppedAt = rewind.timeAt(open.served)
            if (stoppedAt == null || rewind.full && stoppedAt < since + OLDEST_MARGIN_MS) {
                val position = rewind.positionAt(since + OLDEST_MARGIN_MS)
                if (position != null) {
                    shiftTo(BehindLive(rewind.timeAt(position) ?: since, now, paused = false))
                    reopen.tryEmit(Reopen(open.urlAt(position)))
                    return
                }
            }
        }
        shiftTo(paused.copy(since = now, paused = false))
        active.value.play()
    }

    /**
     * Holds live TV on its picture when there's no rewind buffer, for the archive to play on from
     * that moment (brief 5.5): the engine pauses, and whatever plays next takes over.
     */
    fun holdPicture() {
        if (current == null) return
        active.value.pause()
    }

    /**
     * Live TV from [behindMs] behind live, out of the rewind buffer (brief 5.5): the engine opens
     * the buffer at the keyframe at or before then. The answer is how far behind it landed, or null
     * when there's no buffer, or it doesn't reach back that far and [orOldest] is false; with
     * [orOldest], it lands on the oldest moment held instead.
     */
    suspend fun seekLive(behindMs: Long, orOldest: Boolean = false): Long? {
        val open = liveSession() ?: return null
        if (behindMs <= LIVE_EDGE_MS) {
            goLive()
            return 0
        }
        if (!withContext(Dispatchers.IO) { open.hold() }) return null
        // The picture may have moved on while the buffer started.
        if (liveSession() !== open) return null
        val now = clock()
        val rewind = open.rewind ?: return null
        val oldest = rewind.since ?: return null
        val target = now - behindMs
        if (target < oldest && !orOldest) return null
        // A buffer that's letting its oldest moments go: a little after them, so it doesn't lose them as it plays.
        val earliest = if (rewind.full) oldest + OLDEST_MARGIN_MS else oldest
        val position = rewind.positionAt(maxOf(target, earliest)) ?: return null
        val landed = rewind.timeAt(position) ?: target
        shiftTo(BehindLive(landed, now, paused = false))
        reopen.tryEmit(Reopen(open.urlAt(position)))
        return now - landed
    }

    /** Go Live (brief 5.5): back to the live edge from the rewind buffer. */
    fun goLive() {
        val open = liveSession() ?: return
        if (_behindLive.value == null) return
        shiftTo(null)
        reopen.tryEmit(Reopen(open.liveUrl))
    }

    /**
     * How far back live TV can be rewound now, in ms (brief 5.5): the rewind buffer's reach, or
     * before a pause starts it, memory's. Null without a buffer.
     */
    fun rewindReachMs(): Long? = liveSession()?.rewindSince?.let { (clock() - it).coerceAtLeast(0) }

    /** The hub's session for the live channel on screen, if it plays through the hub. */
    private fun liveSession(): HubSession? {
        val key = current ?: return null
        if (StreamKeys.archiveStart(key) != null || StreamKeys.isVod(key)) return null
        return session?.takeIf { sessionChannel == key }
    }

    /** The wall-clock time the picture shows at [now]. */
    private fun pictureTime(now: Long): Long = _behindLive.value?.pictureAt(now) ?: now

    private val _behindLive = MutableStateFlow<BehindLive?>(null)

    /** Live TV played from the rewind buffer (brief 5.5), paused or behind; null at the live edge. */
    val behindLive: StateFlow<BehindLive?> = _behindLive.asStateFlow()

    private fun shiftTo(shift: BehindLive?) {
        _behindLive.value = shift
    }

    /** Where to open the stream next, after a seek or Go Live, on the same connection. */
    private val reopen = MutableSharedFlow<Reopen>(extraBufferCapacity = 1)

    /** Opening the stream again at [url]; in a recording being written, at [chasedFrom] ms into it. */
    private class Reopen(val url: String, val chasedFrom: Long? = null)

    /**
     * Plays the movie or episode [key] ([StreamKeys.movie], [StreamKeys.episode]) from [startMs]
     * in, with sound (brief 5.9). Asked for again, it starts again from there.
     */
    fun playVod(key: String, startMs: Long) {
        vodStart = startMs.coerceAtLeast(0)
        vodFor = key
        current = null
        show(key, muted = false)
    }

    /**
     * Jumps to [ms] into the movie or episode on screen (brief 4.9). A recording being written is
     * opened again at the keyframe at or before then (chase play, 5.6).
     */
    fun seekTo(ms: Long) {
        val key = current ?: return
        if (!StreamKeys.isVod(key)) return
        val to = ms.coerceAtLeast(0)
        val chase = chasing
        if (chase == null) {
            active.value.seekTo(to)
            return
        }
        // Where it's going shows until the engine opens there.
        chaseSeek.value = to
        chaseSeeking?.cancel()
        chaseSeeking = scope.launch {
            val found = withContext(Dispatchers.IO) { chase.seek(to) }
            if (chasing !== chase) return@launch
            if (found == null) {
                chaseSeek.value = null
                return@launch
            }
            reopen.tryEmit(Reopen(chase.urlAt(found.first), chasedFrom = found.second))
        }
    }

    /** Nothing plays as it's written any more: something else is on screen (chase play, 5.6). */
    private fun stopChasing() {
        chasing = null
        chaseSeeking?.cancel()
        chaseSeek.value = null
        chaseBase.value = 0
        chaseLength.value = null
    }

    /** Where the movie or episode on screen has got to, to start from when it opens again. */
    private fun keepPlace(key: String?) {
        if (key == null || !StreamKeys.isVod(key)) return
        (chaseSeek.value ?: (chaseBase.value + active.value.position.value)).takeIf { it > 0 }?.let { vodStart = it }
    }

    /** Tunes the channel again, as Try Again does after an error; a movie carries on where it was. */
    fun retry() {
        val channelId = current ?: return
        keepPlace(channelId)
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

    /** A source picked for a channel that isn't on screen: it goes first when the channel next plays. */
    private var chosenSource: Pair<String, String>? = null

    /**
     * Switch Source from a channel's options (brief 4.10 and 5.1): [channelId] plays from
     * [candidate] now if it's the channel on screen, or else the next time it plays.
     */
    fun switchSource(channelId: String, candidate: String) {
        if (current == channelId) {
            chosenSource = null
            switchSource(candidate)
        } else {
            chosenSource = channelId to candidate
        }
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
        keepPlace(channelId)
        paused = channelId
        // Back from standby, live TV plays from the live edge (5.20).
        shiftTo(null)
        current = null
        val from = _playingFrom.value
        _playingFrom.value = null
        job?.cancel()
        job = null
        active.value.stop()
        val open = session
        val target = sessionTarget
        // Turned off right after a zap, the connection open is the channel before's: it goes.
        val forThisChannel = sessionChannel == channelId
        session = null
        sessionTarget = null
        sessionChannel = null
        releaseHeld()
        if (open != null && graceMs > 0 && forThisChannel) {
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
        // Picked in a channel's options before it was on screen (Switch Source, 5.1).
        val picked = first ?: chosenSource?.takeIf { it.first == channelId }?.second
        chosenSource = null
        _status.value = LiveStatus.Opening(channelId)
        _afterFallback.value = false
        val settings = prefs()
        // The channel's own engine, from Switch Player or a fallback that worked (5.4.3).
        val ownEngine = chosenEngine ?: channelEngine(channelId)
        chosenEngine = null
        // Its own volume level goes in before its first sound, so a zap doesn't jump in loudness.
        channelAudio = audioOf(channelId)

        // Back on the channel whose connection was kept: it carries on from the live edge (5.20).
        val kept = held?.takeIf { it.channelId == channelId && it.session.isUsable }
        if (kept != null) {
            held = null
            kept.release.cancel()
            stopPlayback()
            session = kept.session
            sessionTarget = kept.target
            sessionChannel = channelId
            _playingFrom.value = kept.from
            val candidates = kept.from?.candidates ?: listOf(channelId)
            val candidate = kept.from?.candidate ?: channelId
            // When its source fails, the channel's other sources take over, as on any tune (5.1).
            val others = candidates - candidate
            val outcome = coroutineScope {
                val reports = launch { reportPlaying(channelId, candidate) }
                var player = kept.engine
                var result: Outcome
                while (true) {
                    useEngine(playerFor(player) ?: engine)
                    result = watch(
                        channelId, kept.session.url, direct = null, settings.forTarget(kept.target), kept.session.state,
                        failOver = settings.failOver && others.isNotEmpty(),
                    )
                    if (result != Outcome.EngineSwitch) break
                    // Switch Player, on the same connection.
                    player = switchTo.value ?: player
                    switchTo.value = null
                }
                reports.cancel()
                result
            }
            if (outcome != Outcome.SourceFailed) return
            stopPlayback()
            onEvent(PlayerEvent.SourceFailed(channelId, candidate, others.first()))
            playCandidates(channelId, candidates, others, settings, ownEngine)
            return
        }
        // Another channel: the kept connection goes first, for accounts with one connection (5.1).
        releaseHeld()

        // A merged channel's candidates in turn: when a source fails, the next one takes over (5.1).
        // The one picked in Switch Source goes first.
        val candidates = candidatesOf(channelId)
        val order = if (picked != null && picked in candidates) listOf(picked) + (candidates - picked) else candidates
        playCandidates(channelId, candidates, order, settings, ownEngine, opening = false)
    }

    /**
     * Plays [order], some of [channelId]'s [candidates], in turn: when a source fails, the next one
     * takes over (5.1). The first opens as the tune does, unless it's [opening] again.
     */
    private suspend fun playCandidates(
        channelId: String,
        candidates: List<String>,
        order: List<String>,
        settings: PlaybackPrefs,
        ownEngine: EngineId?,
        opening: Boolean = true,
    ) {
        for ((index, candidate) in order.withIndex()) {
            val more = settings.failOver && index < order.lastIndex
            if (index > 0 || opening) _status.value = LiveStatus.Opening(channelId)
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

    /** [channelId]'s own sound, or none. */
    private suspend fun audioOf(channelId: String): ChannelAudio = try {
        resolver.audioFor(channelId) ?: ChannelAudio()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        ChannelAudio()
    }

    /**
     * Audio and Subtitles (brief 4.10): the channel on screen plays the sound track [id] now, and
     * its next tunes start with it (5.4.6). Keeping it is the app's.
     */
    fun selectAudio(id: String) {
        val key = current ?: return
        val track = tracks.value.audio.firstOrNull { it.id == id } ?: return
        active.value.selectAudio(id)
        channelAudio = channelAudio.copy(tracks = (channelAudio.tracks ?: ChannelTracks()).copy(audio = track.pick()))
        onEvent(PlayerEvent.AudioChosen(key, track))
    }

    /** Audio and Subtitles: the channel on screen shows the subtitle track [id], or none when it's null. */
    fun selectSubtitles(id: String?) {
        val key = current ?: return
        val track = if (id == null) null else tracks.value.subtitles.firstOrNull { it.id == id } ?: return
        active.value.selectSubtitles(track?.id)
        channelAudio = channelAudio.copy(tracks = (channelAudio.tracks ?: ChannelTracks()).copy(subtitles = track?.pick(), subtitlesOff = track == null))
        onEvent(PlayerEvent.SubtitlesChosen(key, track))
    }

    /**
     * Channel options › Volume Level (brief 5.4.6): [channelId]'s level is [db] now, while it
     * plays, as the bar changes it. Keeping it is the app's.
     */
    fun setLevel(channelId: String, db: Int) {
        if (current?.let(StreamKeys::channelOf) != channelId) return
        channelAudio = channelAudio.copy(levelDb = db)
        active.value.setLevel(db)
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
                // Shared by channel, so a recording of it uses the same connection (5.6).
                hub.open(UpstreamRequest(target.url, target.headers, target.budget, shareKey = target.channelId))
            } catch (limit: ConnectionLimitReached) {
                // This source's connections are all in use; another source has its own (5.1).
                if (failOver) return Outcome.SourceFailed
                fail(channelId, LiveFailure.ConnectionLimit)
                return Outcome.Done
            }
            session = opened
            sessionTarget = target
            sessionChannel = channelId
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
                if (failed != null && failed != run.id) {
                    onEvent(PlayerEvent.SwitchedEngine(channelId, failed, run.id))
                    _afterFallback.value = true
                }
                useEngine(player)
                val outcome = watch(
                    channelId, opened.url, direct = null, chosen.copy(decoding = run.decoding), opened.state, failOver,
                    engineFallback = index < runs.lastIndex, waitForPicture = !target.radio, reportWorked = failed != null,
                    received = { opened.bytesReceived },
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
                        _afterFallback.value = false
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
        handoff.withLock {
            val old = active.value
            if (old === next) return@withLock
            // A handoff always finishes, even when a zap cuts its tune short: otherwise the old
            // engine would stay in charge without the surface, and the next tune on it would have
            // sound and no picture. The next tune's handoff waits for it.
            withContext(NonCancellable) {
                old.stop()
                old.detach()
                delay(HANDOFF_MS)
                active.value = next
                view.value?.let { next.attach(it, subtitlesView) }
                next.setVolume(if (muted) 0 else FULL_VOLUME)
            }
        }
        // A tune cut short during the handoff goes no further.
        currentCoroutineContext().ensureActive()
    }

    /** One handoff at a time. */
    private val handoff = Mutex()

    private enum class Outcome { Done, Playlist, SourceFailed, EngineFailed, EngineSwitch }

    /**
     * Plays [url] and follows it until it fails, or until the hub finds a playlist. The hub says
     * whether data arrives, the engine whether it plays: together they tell a source fault from an
     * engine fault (brief 5.4.9). [direct] is the target when the engine opens the provider itself.
     *
     * With [engineFallback], an engine that fails, or shows no picture within the connection
     * timeout ([waitForPicture]), while the hub has data, ends it with [Outcome.EngineFailed] for
     * the next engine. So does one that stalls three times within a minute while the hub's data
     * keeps coming, by the count of bytes it has [received]. [reportWorked] says when a fallback
     * engine has played for 30 s.
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
        received: (() -> Long)? = null,
    ): Outcome = coroutineScope {
        val player = active.value
        // Opened afresh, as after a switch of engine: from the live edge (5.5).
        shiftTo(null)
        player.setVolume(if (muted) 0 else FULL_VOLUME)
        player.setLevel(channelAudio.levelDb)
        val start = if (StreamKeys.isVod(channelId)) vodStart else 0
        // A recording being written opens at the keyframe for where it starts, and grows (5.6);
        // where it starts shows until it's open.
        val chase = direct?.chase
        chasing = chase
        if (chase != null) chaseSeek.value = start
        val landed = chase?.let { withContext(Dispatchers.IO) { it.seek(start) } }
        chaseLength.value = chase?.let { withContext(Dispatchers.IO) { it.writtenMs() } }
        val request = PlayRequest(
            url = if (chase != null) chase.urlAt(landed?.first ?: 0) else url,
            live = StreamKeys.archiveStart(channelId) == null && !StreamKeys.isVod(channelId),
            buffer = settings.buffer,
            decoding = settings.decoding,
            deinterlacing = settings.deinterlacing,
            audioOutput = settings.audioOutput,
            userAgent = direct?.header("User-Agent"),
            referrer = direct?.header("Referer"),
            startMs = if (chase != null) 0 else start,
            tunneling = settings.tunneling,
        )
        player.open(request)
        // The engine counts from where it opened: 0 in a recording being written.
        chaseBase.value = landed?.second ?: 0
        chaseSeek.value = null
        player.play()
        // Its sound and subtitles, as the channel's picks and the language rules say (5.4.6).
        var picking = launch { pickTracks(player, settings.tracks) }
        // Opening again where a seek in the rewind buffer, or Go Live, says (5.5): its buffering
        // is no stall, and the tracks are picked again as it lists them again.
        var reopened = false
        val growing = chase?.let { chased ->
            launch {
                while (true) {
                    delay(CHASE_LENGTH_MS)
                    chaseLength.value = withContext(Dispatchers.IO) { chased.writtenMs() }
                }
            }
        }
        val reopening = if ((upstream != null && direct == null) || chase != null) {
            launch {
                reopen.collect { at ->
                    reopened = true
                    player.open(request.copy(url = at.url))
                    // A seek in a recording being written: the engine counts from where it landed.
                    // The seek's place shows until now, so nothing in between does.
                    at.chasedFrom?.let { landed ->
                        chaseBase.value = landed
                        chaseSeek.value = null
                    }
                    player.play()
                    picking.cancel()
                    picking = launch { pickTracks(player, settings.tracks) }
                }
            }
        } else {
            null
        }
        val worked = if (reportWorked) {
            launch {
                _status.first { it == LiveStatus.Playing(channelId) }
                delay(engineWorkedMs)
                onEvent(PlayerEvent.EngineWorked(channelId, player.id))
                _afterFallback.value = false
            }
        } else {
            null
        }
        // A stream that sends data and still has no picture past the connection timeout, and the
        // time its buffer takes to fill, is the engine's fault (5.4.3). The clock starts with the
        // first data, so a slow provider isn't taken for it.
        val late = if (engineFallback && waitForPicture && upstream != null) {
            flow {
                emit(false)
                upstream.first { it == UpstreamState.Streaming }
                delay(settings.connectionTimeoutMs + settings.buffer.millis)
                emit(true)
            }
        } else {
            flowOf(false)
        }
        // Once there's been a picture, losing it for a moment (a new video format, a reconnect)
        // isn't the engine failing.
        var seenPicture = false
        // Stalls (5.4.3): the engine buffering again after it has played, with the hub's data
        // still coming. Each counts once, and three within a minute are the engine's fault.
        val counted = received.takeIf { engineFallback }
        val arrivals = ArrayDeque<Long>()
        val sampling = counted?.let { bytes -> launch { sampleArrivals(bytes, arrivals) } }
        val stalls = ArrayDeque<Long>()
        var playedSinceStall = false

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
            if (picture) seenPicture = true
            val noPicture = overdue && !seenPicture && played != EngineState.Paused
            if (engineFallback && dataArrives && (played is EngineState.Failed || noPicture)) {
                outcome = Outcome.EngineFailed
                return@transformWhile false
            }
            if (played == EngineState.Playing) playedSinceStall = true
            // Paused, or opened again in the rewind buffer: the buffering that follows is no stall.
            if (played == EngineState.Paused || reopened) {
                playedSinceStall = false
                reopened = false
            }
            if (played is EngineState.Buffering && playedSinceStall) {
                playedSinceStall = false
                if (counted != null && dataArrives && dataKeptComing(arrivals, counted)) {
                    val at = System.nanoTime() / NANOS_PER_MS
                    stalls.addLast(at)
                    while (at - stalls.first() > stallWindowMs) stalls.removeFirst()
                    if (stalls.size >= STALLS) {
                        outcome = Outcome.EngineFailed
                        return@transformWhile false
                    }
                }
            }
            val status = liveStatusOf(channelId, hub, played)
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
        picking.cancel()
        sampling?.cancel()
        reopening?.cancel()
        growing?.cancel()
        // Handing over to another engine or source: this one stops, the hub's connection stays. A
        // movie carries on in the next one where it had got to.
        if (outcome == Outcome.EngineFailed || outcome == Outcome.EngineSwitch) {
            keepPlace(channelId)
            player.stop()
        }
        // A seek that hadn't opened yet goes no further.
        if (chase != null) {
            chaseSeeking?.cancel()
            chaseSeek.value = null
        }
        outcome
    }

    private data class Observed(val hub: UpstreamState, val played: EngineState, val picture: Boolean, val overdue: Boolean, val pick: EngineId?)

    /** Keeps the hub's count of bytes [received] in [arrivals], every half second over the last two. */
    private suspend fun sampleArrivals(received: () -> Long, arrivals: ArrayDeque<Long>) {
        while (true) {
            arrivals.addLast(received())
            if (arrivals.size > ARRIVAL_SAMPLES) arrivals.removeFirst()
            delay(ARRIVAL_EVERY_MS)
        }
    }

    /**
     * The hub's data kept coming up to now: more arrived in the last second, and in the second
     * before. An engine that stalls then has the stream to hand; one that stalls while the
     * provider is slow doesn't, and isn't at fault (5.4.3).
     */
    private fun dataKeptComing(arrivals: ArrayDeque<Long>, received: () -> Long): Boolean {
        if (arrivals.size < ARRIVAL_SAMPLES) return false
        val now = received()
        val secondAgo = arrivals[arrivals.size - ARRIVAL_SAMPLES / 2]
        val twoSecondsAgo = arrivals[arrivals.size - ARRIVAL_SAMPLES]
        return now > secondAgo && secondAgo > twoSecondsAgo
    }

    /**
     * Once [player] plays and knows the stream's tracks, switches to the sound and subtitles the
     * channel's own picks, then [prefs], ask for (5.4.6 and 5.4.7). Once a stream.
     */
    private suspend fun pickTracks(player: PlayerEngine, prefs: TrackPrefs) {
        player.state.first { it == EngineState.Playing }
        withTimeoutOrNull(TRACKS_WAIT_MS) { player.tracks.first { it.audio.isNotEmpty() || it.subtitles.isNotEmpty() } } ?: return
        // A stream lists its tracks as it starts: the rest come a moment after the first.
        delay(TRACKS_SETTLE_MS)
        val choice = chooseTracks(player.tracks.value, prefs, channelAudio.tracks)
        choice.audioId?.let(player::selectAudio)
        if (choice.changeSubtitles) player.selectSubtitles(choice.subtitleId)
    }

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

    private fun fail(channelId: String, failure: LiveFailure) {
        stopPlayback()
        _status.value = LiveStatus.Failed(channelId, failure)
    }

    private fun stopPlayback() {
        active.value.stop()
        session?.close()
        session = null
        sessionTarget = null
        sessionChannel = null
    }

    /**
     * The source's own Buffer Size, Audio Output and Use Stream Hub, where it has them (Playback for
     * This Source, 4.20), and the channel's own Audio Output over both (Decode This Channel, 5.4.6).
     */
    private fun PlaybackPrefs.forTarget(target: StreamTarget?): PlaybackPrefs {
        val sourced = if (target == null) {
            this
        } else {
            copy(buffer = target.buffer ?: buffer, audioOutput = target.audioOutput ?: audioOutput, throughHub = target.useHub ?: throughHub)
        }
        return channelAudio.output?.let { sourced.copy(audioOutput = it) } ?: sourced
    }

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
        /** This close to live, a seek in the rewind buffer goes live instead (5.5). */
        const val LIVE_EDGE_MS = 3_000L

        /** Played from the oldest moments of a full rewind buffer, the picture starts this far after them. */
        const val OLDEST_MARGIN_MS = 10_000L

        /** How often a recording being written says how much of it there is (chase play, 5.6). */
        const val CHASE_LENGTH_MS = 1_000L

        const val FULL_VOLUME = 100

        /** A candidate that plays this long works (Remember What Works per Channel, brief 5.1). */
        const val PLAYED_LONG_MS = 10 * 60_000L

        /** A fallback engine that plays this long is the one for the channel (5.4.3). */
        const val ENGINE_WORKED_MS = 30_000L

        /** Stalls within [STALL_WINDOW_MS] that hand the stream to the next engine (5.4.3). */
        const val STALLS = 3
        const val STALL_WINDOW_MS = 60_000L

        // The hub's count of bytes, every half second over the last two, while stalls are counted.
        const val ARRIVAL_EVERY_MS = 500L
        const val ARRIVAL_SAMPLES = 4
        const val NANOS_PER_MS = 1_000_000L

        /** Time for one engine's decoder to let go of the surface before the next takes it. */
        const val HANDOFF_MS = 250L

        /** Fallback's order (5.4.3). */
        val ENGINE_ORDER = listOf(EngineId.Vlc, EngineId.Mpv, EngineId.Media3)

        /** How long a stream that plays may take to list its tracks before they're left as they are. */
        const val TRACKS_WAIT_MS = 5_000L

        /** The time from a stream's first track to its last. */
        const val TRACKS_SETTLE_MS = 300L

        // The bitrate, from the hub's count of bytes each second, over the last five seconds.
        const val BITRATE_EVERY_MS = 1_000L
        const val BITRATE_SAMPLES = 6
        const val BITS_PER_BYTE = 8
        const val NANOS_PER_SECOND = 1e9
    }
}

/** What the hub and the engine say together, as the screen shows it (brief 5.4.9). */
internal fun liveStatusOf(channelId: String, hub: UpstreamState, played: EngineState): LiveStatus = when {
    hub is UpstreamState.Failed -> LiveStatus.Failed(channelId, failureOf(hub.failure))
    hub is UpstreamState.Reconnecting -> LiveStatus.Reconnecting(channelId, hub.attempt)
    played is EngineState.Failed -> LiveStatus.Failed(channelId, LiveFailure.CantPlay)
    // A program from the archive ends, and so do movies; live TV doesn't, so a live stream
    // that does has stopped coming from the provider.
    played == EngineState.Ended && (StreamKeys.archiveStart(channelId) != null || StreamKeys.isVod(channelId)) -> LiveStatus.Ended(channelId)
    played == EngineState.Ended -> LiveStatus.Failed(channelId, LiveFailure.SourceUnavailable)
    played == EngineState.Playing || played == EngineState.Paused -> LiveStatus.Playing(channelId)
    else -> LiveStatus.Opening(channelId)
}

private fun failureOf(failure: StreamFailure): LiveFailure = when (failure) {
    is StreamFailure.Refused -> LiveFailure.Refused
    StreamFailure.NotVideo -> LiveFailure.NotVideo
    else -> LiveFailure.SourceUnavailable
}
