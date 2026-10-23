// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.view.SurfaceView
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.BufferSize
import ca.gureet.plasma.core.model.Deinterlacing
import ca.gureet.plasma.core.model.FrameRateMatch
import ca.gureet.plasma.core.model.VideoDecoding
import ca.gureet.plasma.stream.ConnectionLimitReached
import ca.gureet.plasma.stream.HubSession
import ca.gureet.plasma.stream.StreamFailure
import ca.gureet.plasma.stream.StreamHub
import ca.gureet.plasma.stream.UpstreamRequest
import ca.gureet.plasma.stream.UpstreamState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
)

/**
 * Where the channel [key] on screen plays from (brief 5.1): [candidate], one of [candidates], each
 * a key [StreamResolver.resolve] takes. Switch Source lists the [candidates].
 */
data class PlayingFrom(val key: String, val candidates: List<String>, val candidate: String)

/** What the player tells the app about, for its notices and its memory of what works (brief 5.1). */
sealed interface PlayerEvent {
    /** [channelId]'s source failed, and the channel goes on from the candidate [to] (failover). */
    data class SwitchedSource(val channelId: String, val to: String) : PlayerEvent
}

/**
 * Owns the main engine and the video layer, opens streams through the hub, and reports one status
 * whichever engine plays (brief 6.5). Call it on the main thread; [scope] runs there too.
 */
class PlayerManager(
    private val engine: PlayerEngine,
    private val hub: StreamHub,
    private val resolver: StreamResolver,
    private val prefs: () -> PlaybackPrefs,
    private val scope: CoroutineScope,
    private val onEvent: (PlayerEvent) -> Unit = {},
    private val frameRates: FrameRateControl = FrameRateControl.None,
) : LiveVideo {
    private val _status = MutableStateFlow<LiveStatus>(LiveStatus.Idle)
    override val status: StateFlow<LiveStatus> = _status.asStateFlow()

    private val _playingFrom = MutableStateFlow<PlayingFrom?>(null)

    /** Where the channel on screen plays from: one of a merged channel's candidates (brief 5.1). */
    val playingFrom: StateFlow<PlayingFrom?> = _playingFrom.asStateFlow()

    override val picture: StateFlow<String?> = combine(engine.hasPicture, _status) { hasPicture, status ->
        when {
            !hasPicture -> null
            status is LiveStatus.Playing -> status.channelId
            status is LiveStatus.Reconnecting -> status.channelId
            else -> null
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    override val aspectRatio: StateFlow<Float?> = engine.video.map { video ->
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

    private class Held(val channelId: String, val from: PlayingFrom?, val session: HubSession, val target: StreamTarget?, val release: Job)

    init {
        // Match Frame Rate is for the full-screen player, not Home's picture (brief 5.4.4). A new
        // channel's rate is unknown for a moment; the last one stays asked for until it's known,
        // so zapping between channels at the same rate never switches the TV twice.
        scope.launch {
            var lastRate: Float? = null
            combine(fullScreen, engine.video.map { it?.frameRate }.distinctUntilChanged(), view) { full, rate, surface ->
                if (rate != null) lastRate = rate
                surface to lastRate.takeIf { full }
            }.distinctUntilChanged().collect { (surface, rate) -> frameRates.apply(surface, rate, prefs().frameRate) }
        }
    }

    override fun attach(view: SurfaceView) {
        this.view.value = view
        engine.attach(view)
    }

    override fun detach(view: SurfaceView) {
        if (this.view.value !== view) return
        engine.detach()
        this.view.value = null
    }

    override fun show(channelId: String?, muted: Boolean) {
        if (this.muted != muted) {
            this.muted = muted
            engine.setVolume(if (muted) 0 else FULL_VOLUME)
        }
        // The channel already playing carries on: a preview going full screen never restarts.
        if (channelId == current && _status.value !is LiveStatus.Failed) return
        current = channelId
        job?.cancel()
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
        if (paused) engine.pause() else engine.play()
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
        job?.cancel()
        releaseHeld()
        job = scope.launch { play(channelId, first = candidate) }
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
        engine.stop()
        val open = session
        val target = sessionTarget
        session = null
        sessionTarget = null
        releaseHeld()
        if (open != null && graceMs > 0) {
            held = Held(channelId, from, open, target, scope.launch { delay(graceMs); releaseHeld() })
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
        engine.release()
    }

    private suspend fun play(channelId: String, first: String? = null) {
        _status.value = LiveStatus.Opening(channelId)
        val settings = prefs()

        // Back on the channel whose connection was kept: it carries on from the live edge (5.20).
        val kept = held?.takeIf { it.channelId == channelId && it.session.isUsable }
        if (kept != null) {
            held = null
            kept.release.cancel()
            stopPlayback()
            session = kept.session
            sessionTarget = kept.target
            _playingFrom.value = kept.from
            watch(channelId, kept.session.url, direct = null, settings.forTarget(kept.target), kept.session.state)
            return
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
            if (playCandidate(channelId, candidate, settings, failOver = more) == Outcome.Done) return
            onEvent(PlayerEvent.SwitchedSource(channelId, order[index + 1]))
        }
    }

    /** What to try for [channelId], best first: itself, or a merged channel's candidates. */
    private suspend fun candidatesOf(channelId: String): List<String> = try {
        resolver.candidates(channelId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        emptyList()
    }.distinct().ifEmpty { listOf(channelId) }

    /**
     * Plays [candidate] as [channelId] until it ends or fails. [Outcome.SourceFailed] means its
     * source failed and, with [failOver], the next candidate should take over; it's never shown.
     */
    private suspend fun playCandidate(channelId: String, candidate: String, settings: PlaybackPrefs, failOver: Boolean): Outcome {
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
            when (watch(channelId, opened.url, direct = null, chosen, opened.state, failOver)) {
                Outcome.Done -> return Outcome.Done
                Outcome.SourceFailed -> {
                    stopPlayback()
                    return Outcome.SourceFailed
                }
                // The address led to an HLS or DASH playlist, not a stream: the engine fetches it itself (5.4.2).
                Outcome.Playlist -> stopPlayback()
            }
        }
        val outcome = watch(channelId, target.url, direct = target, chosen, upstream = null, failOver)
        if (outcome == Outcome.SourceFailed) stopPlayback()
        return outcome
    }

    private enum class Outcome { Done, Playlist, SourceFailed }

    /**
     * Plays [url] and follows it until it fails, or until the hub finds a playlist. The hub says
     * whether data arrives, the engine whether it plays: together they tell a source fault from an
     * engine fault (brief 5.4.9). [direct] is the target when the engine opens the provider itself.
     */
    private suspend fun watch(
        channelId: String,
        url: String,
        direct: StreamTarget?,
        settings: PlaybackPrefs,
        upstream: Flow<UpstreamState>?,
        failOver: Boolean = false,
    ): Outcome {
        engine.setVolume(if (muted) 0 else FULL_VOLUME)
        engine.open(
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
        engine.play()

        var outcome = Outcome.Done
        combine(upstream ?: flowOf(UpstreamState.Streaming), engine.state) { hub, played -> hub to played }
            .transformWhile { (hub, played) ->
                // A playlist behind the address, or one the hub can't open at all (udp://, rtmp://):
                // the engine gets it from the provider itself.
                if (hub is UpstreamState.Failed && (hub.failure == StreamFailure.Playlist || hub.failure is StreamFailure.Unusable)) {
                    outcome = Outcome.Playlist
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
            }
            .collect { status ->
                // The connection goes before the failure shows, so a retry or a zap finds it free.
                if (status is LiveStatus.Failed || status is LiveStatus.Ended) stopPlayback()
                _status.value = status
            }
        return outcome
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
        engine.stop()
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
    }
}
