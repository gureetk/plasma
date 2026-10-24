// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.view.SurfaceView
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.VideoDecoding
import ca.gureet.plasma.stream.ConnectionLimitReached
import ca.gureet.plasma.stream.HubSession
import ca.gureet.plasma.stream.StreamHub
import ca.gureet.plasma.stream.UpstreamRequest
import ca.gureet.plasma.stream.UpstreamState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One Multiview tile (brief 4.12 and 5.8): an engine of its own, VLC with hardware decoding and
 * decoded sound, so the sound moves between tiles at once, and a connection of its own through the
 * stream hub, which joins the one already open for the channel. A stream it can't play shows the
 * tile's error card rather than falling back, and frees its connection.
 *
 * Calls are made on the main thread.
 */
class TilePlayer(
    private val engine: PlayerEngine,
    private val hub: StreamHub,
    private val streams: StreamResolver,
    private val scope: CoroutineScope,
    private val prefs: () -> PlaybackPrefs = { PlaybackPrefs() },
) {
    private val _status = MutableStateFlow<LiveStatus>(LiveStatus.Idle)

    /** What the tile shows: opening, playing, or why it can't. */
    val status: StateFlow<LiveStatus> = _status.asStateFlow()

    /** The picture playing, once the engine knows it: for the one-4K-tile rule (5.8). */
    val video: StateFlow<VideoInfo?> get() = engine.video

    /** The channel the tile plays, or tried to; null when it's stopped. */
    var channelId: String? = null
        private set

    private var job: Job? = null
    private var session: HubSession? = null
    private var sound = false

    fun attach(view: SurfaceView) = engine.attach(view)

    fun detach() = engine.detach()

    fun setWindowSize(width: Int, height: Int) = engine.setWindowSize(width, height)

    /** Plays [channelId] in the tile; the same channel playing already carries on. */
    fun play(channelId: String) {
        if (channelId == this.channelId && _status.value !is LiveStatus.Failed && _status.value != LiveStatus.Idle) return
        stopPlayback()
        this.channelId = channelId
        _status.value = LiveStatus.Opening(channelId)
        job = scope.launch { tune(channelId) }
    }

    /** The tile with the sound is heard; the others are muted (4.12). */
    fun setSound(on: Boolean) {
        sound = on
        engine.setVolume(if (on) FULL_VOLUME else 0)
    }

    /** Stops the picture and lets the connection go. */
    fun stop() {
        stopPlayback()
        channelId = null
        _status.value = LiveStatus.Idle
    }

    /**
     * Stops the picture but keeps the connection for [holdMs], so the main player going full screen
     * on the channel joins it rather than connecting again (5.8).
     */
    fun handOff(holdMs: Long) {
        job?.cancel()
        job = null
        engine.stop()
        val kept = session ?: return
        session = null
        scope.launch {
            delay(holdMs)
            withContext(Dispatchers.IO) { kept.close() }
        }
    }

    fun release() {
        stop()
        engine.release()
    }

    private suspend fun tune(channelId: String) {
        val target = try {
            streams.resolve(channelId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            null
        }
        if (target == null) return fail(channelId, LiveFailure.NoStream)
        val settings = prefs()
        val url: String
        val upstream = if (target.throughHub && target.useHub != false && settings.throughHub) {
            val opened = try {
                // Shared by channel: the main player's connection, or a recording's, if it's open (5.6).
                withContext(Dispatchers.IO) { hub.open(UpstreamRequest(target.url, target.headers, target.budget, shareKey = target.channelId, viewing = false)) }
            } catch (limit: ConnectionLimitReached) {
                return fail(channelId, LiveFailure.ConnectionLimit)
            }
            session = opened
            url = opened.url
            opened.state
        } else {
            url = target.url
            null
        }
        streams.audioFor(channelId)?.let { engine.setLevel(it.levelDb) }
        engine.setVolume(if (sound) FULL_VOLUME else 0)
        engine.open(
            PlayRequest(
                url = url,
                live = true,
                buffer = target.buffer ?: settings.buffer,
                decoding = VideoDecoding.Hardware,
                deinterlacing = settings.deinterlacing,
                audioOutput = AudioOutput.DecodeToPcm,
                userAgent = if (upstream == null) target.header("User-Agent") else null,
                referrer = if (upstream == null) target.header("Referer") else null,
            ),
        )
        engine.play()
        // Data arriving with no picture past the connection timeout and the buffer: the decoder
        // couldn't start, as when the TV has no more of them (5.8).
        val late = if (upstream != null && !target.radio) {
            flow {
                emit(false)
                upstream.first { it == UpstreamState.Streaming }
                delay(settings.connectionTimeoutMs + settings.buffer.millis)
                emit(true)
            }
        } else {
            flowOf(false)
        }
        var seenPicture = false
        combine(upstream ?: flowOf(UpstreamState.Streaming), engine.state, engine.hasPicture, late) { hub, played, picture, overdue ->
            if (picture) seenPicture = true
            if (overdue && !seenPicture) LiveStatus.Failed(channelId, LiveFailure.CantPlay) else liveStatusOf(channelId, hub, played)
        }.transformWhile { status ->
            emit(status)
            status !is LiveStatus.Failed
        }.collect { status ->
            if (status is LiveStatus.Failed) {
                fail(channelId, status.failure)
            } else {
                _status.value = status
            }
        }
    }

    private fun fail(channelId: String, failure: LiveFailure) {
        // The connection goes before the card shows, so another tile, or a retry, finds it free.
        engine.stop()
        session?.close()
        session = null
        _status.value = LiveStatus.Failed(channelId, failure)
    }

    private fun stopPlayback() {
        job?.cancel()
        job = null
        engine.stop()
        session?.close()
        session = null
    }

    private fun StreamTarget.header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private companion object {
        const val FULL_VOLUME = 100
    }
}
