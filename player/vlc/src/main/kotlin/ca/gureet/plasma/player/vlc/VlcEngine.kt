// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.vlc

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.Deinterlacing
import ca.gureet.plasma.core.model.VideoDecoding
import ca.gureet.plasma.player.EngineId
import ca.gureet.plasma.player.EngineState
import ca.gureet.plasma.player.PlayRequest
import ca.gureet.plasma.player.PlayerEngine
import ca.gureet.plasma.player.VideoInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import java.util.concurrent.Executors

/**
 * The default engine (brief 5.4): libVLC 3.7, whose own MPEG-TS demuxer copes best with messy
 * broadcast streams. It draws only video, into Plasma's one surface; its interface never shows.
 *
 * libVLC's `stop()` can block while a stream closes, so every call into the player runs, in order,
 * on one background thread. Views are attached on the main thread, where libVLC's events arrive too.
 */
class VlcEngine(context: Context) : PlayerEngine {
    override val id = EngineId.Vlc

    private val libVlc = LibVLC(context.applicationContext, OPTIONS)
    private val player = MediaPlayer(libVlc)
    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "plasma-vlc").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _video = MutableStateFlow<VideoInfo?>(null)
    override val video: StateFlow<VideoInfo?> = _video.asStateFlow()

    private val _hasPicture = MutableStateFlow(false)
    override val hasPicture: StateFlow<Boolean> = _hasPicture.asStateFlow()

    private var view: SurfaceView? = null
    private val onLayout = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> resize(v.width, v.height) }

    /** The volume asked for: applied again as each stream starts, so Home's picture stays muted. */
    @Volatile private var volume = MAX_VOLUME

    // libVLC's events don't say which media they're about. An event from the stream before can
    // arrive after the next one is opened (an error as it closes, say), so events count only once
    // the worker has handed the player the media opened last.
    private var opened = 0
    private var current = 0

    init {
        player.setEventListener { event ->
            if (current != opened) return@setEventListener
            when (event.type) {
                MediaPlayer.Event.Opening -> _state.value = EngineState.Opening
                MediaPlayer.Event.Buffering ->
                    _state.value = if (event.buffering < 100f) EngineState.Buffering(event.buffering) else EngineState.Playing
                MediaPlayer.Event.Playing -> {
                    _state.value = EngineState.Playing
                    applyVolume()
                }
                MediaPlayer.Event.Paused -> _state.value = EngineState.Paused
                MediaPlayer.Event.EndReached -> _state.value = EngineState.Ended
                MediaPlayer.Event.EncounteredError -> _state.value = EngineState.Failed("VLC couldn't play the stream")
                MediaPlayer.Event.Stopped -> if (_state.value !is EngineState.Failed && _state.value != EngineState.Ended) _state.value = EngineState.Idle
                MediaPlayer.Event.Vout -> {
                    _hasPicture.value = event.voutCount > 0
                    readVideoTrack()
                }
                MediaPlayer.Event.ESAdded -> applyVolume()
                MediaPlayer.Event.ESSelected -> {
                    readVideoTrack()
                    applyVolume()
                }
            }
        }
    }

    /** The sound output can come and go with a stream, and a volume set while it's gone is lost. */
    private fun applyVolume() = onWorker { player.setVolume(volume) }

    /** For work that events start: an event that arrives after release() has nothing left to do. */
    private fun onWorker(block: () -> Unit) {
        if (!worker.isShutdown) worker.execute(block)
    }

    override fun attach(view: SurfaceView) {
        detach()
        this.view = view
        attachViews(view)
        view.holder.addCallback(surfaceBack)
        view.addOnLayoutChangeListener(onLayout)
        resize(view.width, view.height)
    }

    override fun detach() {
        val attached = view ?: return
        attached.holder.removeCallback(surfaceBack)
        attached.removeOnLayoutChangeListener(onLayout)
        val vout = player.getVLCVout()
        if (vout.areViewsAttached()) vout.detachViews()
        view = null
    }

    /** Until its surface exists, libVLC holds back play() and starts it once the surface is there. */
    private fun attachViews(view: SurfaceView) {
        val vout = player.getVLCVout()
        vout.setVideoView(view)
        vout.attachViews(onNewLayout)
    }

    /**
     * libVLC lets go of the views when their surface goes, as it does while Plasma is in the
     * background. They're attached again straight after, so they wait for the next surface and a
     * channel that plays again before it exists starts with its picture, not only sound.
     */
    private val surfaceBack = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = reattach()

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

        // Posted, so libVLC's own callback has let go of the views and the surface is gone.
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            main.post(::reattach)
        }
    }

    private fun reattach() {
        val attached = view ?: return
        if (!player.getVLCVout().areViewsAttached()) attachViews(attached)
    }

    override fun setWindowSize(width: Int, height: Int) = resize(width, height)

    private fun resize(width: Int, height: Int) {
        if (width > 0 && height > 0) player.getVLCVout().setWindowSize(width, height)
    }

    override fun open(request: PlayRequest) {
        _hasPicture.value = false
        _video.value = null
        _state.value = EngineState.Opening
        val generation = ++opened
        worker.execute {
            val media = Media(libVlc, Uri.parse(request.url))
            when (request.decoding) {
                VideoDecoding.Hardware -> media.setHWDecoderEnabled(true, false)
                VideoDecoding.HardwareCopyBack -> {
                    media.setHWDecoderEnabled(true, false)
                    media.addOption(":no-mediacodec-dr")
                }
                VideoDecoding.Software -> media.setHWDecoderEnabled(false, false)
            }
            // The buffer is also a delay for live TV (brief 5.4.8).
            media.addOption(":network-caching=${request.buffer.millis}")
            media.addOption(":live-caching=${request.buffer.millis}")
            media.addOption(
                ":deinterlace=" + when (request.deinterlacing) {
                    Deinterlacing.Auto -> -1
                    Deinterlacing.Off -> 0
                    Deinterlacing.Always -> 1
                },
            )
            // Used when VLC deinterlaces itself (copy-back or software); the SoC does it otherwise (5.4.5).
            media.addOption(":deinterlace-mode=yadif2x")
            // Only streams that skip the hub need these; the hub sends the source's own (5.4.11).
            request.userAgent?.let { media.addOption(":http-user-agent=$it") }
            request.referrer?.let { media.addOption(":http-referrer=$it") }
            // Passthrough sends Dolby and DTS untouched; VLC decodes a format the output turns down (5.4.6).
            player.setAudioDigitalOutputEnabled(request.audioOutput != AudioOutput.DecodeToPcm)
            player.setMedia(media)
            // The player holds its own reference. Never call player.media to check for one: that
            // takes a reference too, and one left unreleased crashes libVLC later.
            media.release()
            // Events posted from here on are about this media.
            main.post { if (opened == generation) current = generation }
        }
    }

    override fun play() {
        // Views that let go of a destroyed surface are attached again first, so play() waits for
        // the new surface instead of starting with sound and no picture.
        view?.let { if (!player.getVLCVout().areViewsAttached()) attachViews(it) }
        worker.execute { player.play() }
    }

    override fun pause() = worker.execute { player.pause() }

    override fun stop() {
        _hasPicture.value = false
        worker.execute { player.stop() }
    }

    override fun setVolume(percent: Int) {
        volume = percent.coerceIn(0, MAX_VOLUME)
        applyVolume()
    }

    override fun release() {
        detach()
        worker.execute {
            player.setEventListener(null)
            player.stop()
            player.release()
            libVlc.release()
        }
        worker.shutdown()
    }

    /** VLC says how big the picture is and its sample aspect ratio, for Fit (5.4.4). */
    private val onNewLayout = IVLCVout.OnNewVideoLayoutListener { _, width, height, visibleWidth, visibleHeight, sarNum, sarDen ->
        val w = if (visibleWidth > 0) visibleWidth else width
        val h = if (visibleHeight > 0) visibleHeight else height
        if (w > 0 && h > 0) {
            _video.value = (_video.value ?: VideoInfo(w, h)).copy(width = w, height = h, sarNum = sarNum.coerceAtLeast(1), sarDen = sarDen.coerceAtLeast(1))
        }
    }

    private fun readVideoTrack() {
        onWorker {
            val track = player.currentVideoTrack ?: return@onWorker
            val frameRate = if (track.frameRateDen > 0) track.frameRateNum.toFloat() / track.frameRateDen else null
            main.post {
                val current = _video.value
                _video.value = VideoInfo(
                    width = current?.width ?: track.width,
                    height = current?.height ?: track.height,
                    sarNum = current?.sarNum ?: track.sarNum.coerceAtLeast(1),
                    sarDen = current?.sarDen ?: track.sarDen.coerceAtLeast(1),
                    frameRate = frameRate?.takeIf { it > 0f },
                    codec = track.codec,
                )
            }
        }
    }

    companion object {
        private const val MAX_VOLUME = 100

        /** libVLC's version, for the user agent providers see (brief 5.4.2): "3.0.21 Vetinari". */
        fun version(): String = runCatching { LibVLC.version().substringBefore(' ') }.getOrDefault("")

        private val OPTIONS = arrayListOf(
            // Errors only: engines log the URLs they open (brief 6.10).
            "--verbose=0",
            // Keeps the sound's pitch when VLC catches up with a live stream.
            "--audio-time-stretch",
            // For the streams that go straight to VLC; the hub reconnects its own (5.4.8).
            "--http-reconnect",
        )
    }
}
