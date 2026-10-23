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
)

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
    private val frameRates: FrameRateControl = FrameRateControl.None,
) : LiveVideo {
    private val _status = MutableStateFlow<LiveStatus>(LiveStatus.Idle)
    override val status: StateFlow<LiveStatus> = _status.asStateFlow()

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
    private var job: Job? = null
    private val view = MutableStateFlow<SurfaceView?>(null)
    private val fullScreen = MutableStateFlow(false)

    /** The channel to bring back when Plasma returns to the front or the TV turns on (5.20). */
    private var paused: String? = null

    /** A connection kept for a while after stopping, so coming straight back doesn't reconnect (5.20). */
    private var held: Held? = null

    private class Held(val channelId: String, val session: HubSession, val release: Job)

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
     * Plasma left the front, or the TV turned off (brief 5.20): the picture and sound stop at once.
     * The provider connection stays open for [graceMs], so coming straight back carries on at the
     * live edge without reconnecting; then it's let go, which frees it for the account.
     */
    fun standby(graceMs: Long) {
        val channelId = current ?: return
        paused = channelId
        current = null
        job?.cancel()
        job = null
        engine.stop()
        val open = session
        session = null
        releaseHeld()
        if (open != null && graceMs > 0) {
            held = Held(channelId, open, scope.launch { delay(graceMs); releaseHeld() })
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

    private suspend fun play(channelId: String) {
        _status.value = LiveStatus.Opening(channelId)
        val settings = prefs()

        // Back on the channel whose connection was kept: it carries on from the live edge (5.20).
        val kept = held?.takeIf { it.channelId == channelId && it.session.isUsable }
        if (kept != null) {
            held = null
            kept.release.cancel()
            stopPlayback()
            session = kept.session
            watch(channelId, kept.session.url, direct = null, settings, kept.session.state)
            return
        }
        // Another channel: the kept connection goes first, for accounts with one connection (5.1).
        releaseHeld()

        val target = try {
            resolver.resolve(channelId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            null
        }
        if (target == null) {
            fail(channelId, LiveFailure.NoStream)
            return
        }
        stopPlayback()
        if (target.throughHub && settings.throughHub) {
            val opened = try {
                hub.open(UpstreamRequest(target.url, target.headers, target.budget))
            } catch (limit: ConnectionLimitReached) {
                fail(channelId, LiveFailure.ConnectionLimit)
                return
            }
            session = opened
            if (watch(channelId, opened.url, direct = null, settings, opened.state) == Outcome.Done) return
            // The address led to an HLS or DASH playlist, not a stream: the engine fetches it itself (5.4.2).
            stopPlayback()
        }
        watch(channelId, target.url, direct = target, settings, upstream = null)
    }

    private enum class Outcome { Done, Playlist }

    /**
     * Plays [url] and follows it until it fails, or until the hub finds a playlist. The hub says
     * whether data arrives, the engine whether it plays: together they tell a source fault from an
     * engine fault (brief 5.4.9). [direct] is the target when the engine opens the provider itself.
     */
    private suspend fun watch(channelId: String, url: String, direct: StreamTarget?, settings: PlaybackPrefs, upstream: Flow<UpstreamState>?): Outcome {
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
        const val FULL_VOLUME = 100
    }
}
