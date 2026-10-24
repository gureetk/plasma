// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.mpv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.Deinterlacing
import ca.gureet.plasma.core.model.VideoDecoding
import ca.gureet.plasma.player.AudioCodec
import ca.gureet.plasma.player.AudioInfo
import ca.gureet.plasma.player.EngineId
import ca.gureet.plasma.player.EngineState
import ca.gureet.plasma.player.PlayRequest
import ca.gureet.plasma.player.PlayerEngine
import ca.gureet.plasma.player.VideoInfo
import ca.gureet.plasma.player.VolumeLevels
import dev.jdtech.mpv.MPVLib
import dev.jdtech.mpv.MPVLib.MpvEvent
import dev.jdtech.mpv.MPVLib.MpvFormat
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The backup engine (brief 5.4): libmpv, for streams VLC can't play. Like VLC it draws only video,
 * into Plasma's one surface, and its own interface never shows. Hardware decoding renders straight
 * into the surface (`vo=mediacodec_embed`); copy-back and software decoding go through mpv's own
 * renderer (`vo=gpu`), which can also deinterlace (5.4.11).
 *
 * Calls are made on the main thread. mpv's events arrive on its own thread and are posted there.
 * Embedded hardware decoding can't fall back to software by itself (6.5), so a failure there is
 * reported and the player tries copy-back next.
 */
class MpvEngine private constructor(private val mpv: MPVLib) : PlayerEngine {
    override val id = EngineId.Mpv

    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _video = MutableStateFlow<VideoInfo?>(null)
    override val video: StateFlow<VideoInfo?> = _video.asStateFlow()

    private val _hasPicture = MutableStateFlow(false)
    override val hasPicture: StateFlow<Boolean> = _hasPicture.asStateFlow()

    private val _audio = MutableStateFlow<AudioInfo?>(null)
    override val audio: StateFlow<AudioInfo?> = _audio.asStateFlow()

    private var view: SurfaceView? = null
    private var surfaceAttached = false

    /** The video output the stream opened last asks for, set once there's a surface to draw on. */
    private var videoOutput = VO_EMBED

    /** The volume asked for: applied again as each stream opens, so Home's picture stays muted. */
    private var volume = MAX_VOLUME

    /** The channel's own level, in dB: mpv takes it as it is, through its volume gain (5.4.6). */
    private var levelDb = 0

    /** mpv's own user agent, for streams that don't bring one. */
    private val defaultUserAgent: String = mpv.getPropertyString("user-agent").orEmpty()

    /**
     * Where the stream opened last has got to. mpv says when a file starts, loads and ends, but not
     * which file: events for a stream opened before can still be on their way, so they count only
     * from this one's start, and an end is checked against what mpv is playing ([playingRequested]).
     */
    private var phase = Phase.Idle
    private var released = false

    /** mpv's playlist entry for the stream opened last, until it's stopped. */
    private var requested: Int? = null

    private enum class Phase { Idle, Requested, Starting, Loaded }

    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) = Unit

        override fun eventProperty(property: String, value: Long) = post { onLong(property, value) }

        override fun eventProperty(property: String, value: Double) = post { onDouble(property, value) }

        override fun eventProperty(property: String, value: Boolean) = post { onFlag(property, value) }

        override fun eventProperty(property: String, value: String) = post { onString(property, value) }

        override fun event(eventId: Int) = post { onEvent(eventId) }
    }

    init {
        mpv.addObserver(observer)
        for ((property, format) in OBSERVED) mpv.observeProperty(property, format)
    }

    private fun post(block: () -> Unit) {
        main.post { if (!released) block() }
    }

    private fun onEvent(eventId: Int) {
        when (eventId) {
            MpvEvent.MPV_EVENT_START_FILE -> when {
                phase == Phase.Requested -> phase = Phase.Starting
                // An end meant for the stream before was taken for this one's: this one is starting after all.
                phase == Phase.Idle && playingRequested() -> {
                    phase = Phase.Starting
                    _state.value = EngineState.Opening
                }
            }
            MpvEvent.MPV_EVENT_FILE_LOADED -> if (phase == Phase.Starting) phase = Phase.Loaded
            MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> if (phase == Phase.Loaded && _state.value != EngineState.Paused) _state.value = EngineState.Playing
            // The picture is there once mpv's video output is set up for it.
            MpvEvent.MPV_EVENT_VIDEO_RECONFIG -> if (phase == Phase.Loaded) {
                val width = mpv.getPropertyInt("video-params/dw") ?: 0
                val height = mpv.getPropertyInt("video-params/dh") ?: 0
                if (width > 0 && height > 0) showing((_video.value ?: VideoInfo(0, 0)).copy(width = width, height = height))
            }
            MpvEvent.MPV_EVENT_END_FILE -> when {
                phase != Phase.Starting && phase != Phase.Loaded -> Unit
                // The stream opened before this one ended; this one is on its way.
                playingRequested() -> {
                    phase = Phase.Requested
                    _hasPicture.value = false
                    _video.value = null
                    _state.value = EngineState.Opening
                }
                // It ended before it loaded: mpv couldn't open it.
                phase == Phase.Starting -> {
                    phase = Phase.Idle
                    _hasPicture.value = false
                    _state.value = EngineState.Failed("mpv couldn't play the stream")
                }
                else -> {
                    phase = Phase.Idle
                    _hasPicture.value = false
                    _state.value = EngineState.Ended
                }
            }
        }
    }

    /** mpv is opening or playing the stream asked for last, not one before it. */
    private fun playingRequested(): Boolean {
        val entry = requested ?: return false
        return mpv.getPropertyInt("playlist-playing-pos") == 0 && mpv.getPropertyInt("playlist/0/id") == entry
    }

    /** [video] has its size: mpv's output is drawing it. */
    private fun showing(video: VideoInfo) {
        _video.value = video
        if (video.width > 0 && video.height > 0) _hasPicture.value = true
    }

    private fun onFlag(property: String, value: Boolean) {
        if (phase != Phase.Loaded) return
        if (property == "paused-for-cache" && _state.value != EngineState.Paused) _state.value = if (value) EngineState.Buffering(0f) else EngineState.Playing
    }

    // With a window forced, mpv's output stays set up between streams, so the picture is known by
    // the video's size, which every stream sets anew.
    private fun onLong(property: String, value: Long) {
        if (phase != Phase.Loaded || value <= 0) return
        if (property == "audio-params/channel-count") {
            _audio.value = (_audio.value ?: AudioInfo(AudioCodec.Other)).copy(channels = value.toInt())
            return
        }
        val current = _video.value ?: VideoInfo(0, 0)
        showing(
            when (property) {
                "video-params/dw" -> current.copy(width = value.toInt())
                "video-params/dh" -> current.copy(height = value.toInt())
                else -> return
            },
        )
    }

    private fun onDouble(property: String, value: Double) {
        if (phase != Phase.Loaded || property != "container-fps" || value <= 0) return
        _video.value = (_video.value ?: VideoInfo(0, 0)).copy(frameRate = value.toFloat())
    }

    private fun onString(property: String, value: String) {
        if (phase != Phase.Loaded) return
        when (property) {
            "video-codec" -> _video.value = (_video.value ?: VideoInfo(0, 0)).copy(codec = value)
            "audio-codec-name" -> _audio.value = (_audio.value ?: AudioInfo(AudioCodec.Other)).copy(codec = mpvCodec(value))
        }
    }

    override fun attach(view: SurfaceView) {
        detach()
        this.view = view
        view.holder.addCallback(surfaces)
        if (view.holder.surface?.isValid == true) attachSurface(view.holder)
    }

    override fun detach() {
        val attached = view ?: return
        attached.holder.removeCallback(surfaces)
        detachSurface()
        view = null
    }

    private val surfaces = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = attachSurface(holder)

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = setWindowSize(width, height)

        // mpv lets go of the surface before it's destroyed (5.4.4).
        override fun surfaceDestroyed(holder: SurfaceHolder) = detachSurface()
    }

    private fun attachSurface(holder: SurfaceHolder) {
        if (surfaceAttached) return
        mpv.attachSurface(holder.surface)
        // mpv draws only once it has a window to draw into.
        mpv.setOptionString("force-window", "yes")
        mpv.setPropertyString("vo", videoOutput)
        surfaceAttached = true
        val frame = holder.surfaceFrame
        setWindowSize(frame.width(), frame.height())
    }

    private fun detachSurface() {
        if (!surfaceAttached) return
        mpv.setPropertyString("vo", "null")
        mpv.setOptionString("force-window", "no")
        mpv.detachSurface()
        surfaceAttached = false
    }

    override fun setWindowSize(width: Int, height: Int) {
        if (width > 0 && height > 0) mpv.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun open(request: PlayRequest) {
        _hasPicture.value = false
        _video.value = null
        _audio.value = null
        _state.value = EngineState.Opening
        phase = Phase.Requested
        // Hardware decoding draws straight into the surface; the others through mpv's renderer.
        val (hwdec, output) = when (request.decoding) {
            VideoDecoding.Hardware -> "mediacodec" to VO_EMBED
            VideoDecoding.HardwareCopyBack -> "mediacodec-copy" to VO_GPU
            VideoDecoding.Software -> "no" to VO_GPU
        }
        mpv.setPropertyString("hwdec", hwdec)
        // Embedded decoding can't fall back to software; it reports instead, and the player moves on.
        mpv.setPropertyString("vd-lavc-software-fallback", if (output == VO_EMBED) "no" else "yes")
        if (output != videoOutput) {
            videoOutput = output
            if (surfaceAttached) mpv.setPropertyString("vo", output)
        }
        // mpv deinterlaces only through its own renderer; in embedded mode the TV's chip does it (5.4.5).
        mpv.setPropertyString(
            "deinterlace",
            when (request.deinterlacing) {
                Deinterlacing.Auto -> "auto"
                Deinterlacing.Off -> "no"
                Deinterlacing.Always -> "yes"
            },
        )
        // The buffer is also a delay for live TV (5.4.8).
        val seconds = request.buffer.millis / MILLIS_PER_SECOND
        mpv.setPropertyDouble("demuxer-readahead-secs", seconds)
        mpv.setPropertyDouble("cache-pause-wait", seconds)
        // Passthrough sends Dolby and DTS untouched to the TV or sound system (5.4.6).
        mpv.setPropertyString("audio-spdif", if (request.audioOutput == AudioOutput.DecodeToPcm) "" else PASSTHROUGH)
        // Only streams that skip the hub need these; the hub sends the source's own (5.4.11).
        mpv.setPropertyString("user-agent", request.userAgent ?: defaultUserAgent)
        mpv.setPropertyString("referrer", request.referrer.orEmpty())
        mpv.setPropertyInt("volume", volume)
        mpv.setPropertyDouble("volume-gain", levelDb.toDouble())
        mpv.setPropertyBoolean("pause", false)
        mpv.command(arrayOf("loadfile", request.url, "replace"))
        // The playlist now holds only this stream, which tells its events from the stream before's.
        requested = mpv.getPropertyInt("playlist/0/id")
    }

    override fun play() {
        mpv.setPropertyBoolean("pause", false)
        if (_state.value == EngineState.Paused) _state.value = EngineState.Playing
    }

    override fun pause() {
        mpv.setPropertyBoolean("pause", true)
        if (phase == Phase.Loaded) _state.value = EngineState.Paused
    }

    override fun stop() {
        phase = Phase.Idle
        requested = null
        _hasPicture.value = false
        mpv.command(arrayOf("stop"))
        _state.value = EngineState.Idle
    }

    override fun setVolume(percent: Int) {
        volume = percent.coerceIn(0, MAX_VOLUME)
        mpv.setPropertyInt("volume", volume)
    }

    override fun setLevel(db: Int) {
        levelDb = db.coerceIn(VolumeLevels.MIN_DB, VolumeLevels.MAX_DB)
        mpv.setPropertyDouble("volume-gain", levelDb.toDouble())
    }

    override fun release() {
        detach()
        released = true
        mpv.removeObserver(observer)
        // Shutting mpv down can wait for its threads, so it happens off the main thread.
        val closer = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "plasma-mpv-release").apply { isDaemon = true } }
        closer.execute { mpv.destroy() }
        closer.shutdown()
    }

    companion object {
        private const val MAX_VOLUME = 100
        private const val MILLIS_PER_SECOND = 1000.0
        private const val VO_EMBED = "mediacodec_embed"
        private const val VO_GPU = "gpu"

        /** The formats passthrough sends as they are (brief 5.4.6). */
        private const val PASSTHROUGH = "ac3,eac3,dts,dts-hd,truehd"

        private val OBSERVED = listOf(
            "paused-for-cache" to MpvFormat.MPV_FORMAT_FLAG,
            "video-params/dw" to MpvFormat.MPV_FORMAT_INT64,
            "video-params/dh" to MpvFormat.MPV_FORMAT_INT64,
            "container-fps" to MpvFormat.MPV_FORMAT_DOUBLE,
            "video-codec" to MpvFormat.MPV_FORMAT_STRING,
            "audio-codec-name" to MpvFormat.MPV_FORMAT_STRING,
            "audio-params/channel-count" to MpvFormat.MPV_FORMAT_INT64,
        )


        /**
         * libmpv, set up as Plasma's backup engine; null when it can't load on this TV. It loads
         * the first time it's needed, never with the app (brief 5.4.3).
         */
        fun create(context: Context): MpvEngine? {
            val mpv = try {
                MPVLib.create(context.applicationContext) ?: return null
            } catch (missing: LinkageError) {
                return null
            } catch (failed: RuntimeException) {
                return null
            }
            with(mpv) {
                // Video: MediaCodec into the surface, mpv's OpenGL ES renderer otherwise.
                setOptionString("vo", VO_EMBED)
                setOptionString("gpu-context", "android")
                setOptionString("opengl-es", "yes")
                setOptionString("hwdec", "mediacodec")
                setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
                setOptionString("ao", "audiotrack,opensles")
                // Plasma draws every control, and keeps mpv waiting between streams.
                setOptionString("input-default-bindings", "no")
                setOptionString("idle", "yes")
                setOptionString("keep-open", "no")
                setOptionString("force-window", "no")
                setOptionString("save-position-on-quit", "no")
                // Its cache stands in for VLC's network caching (5.4.11).
                setOptionString("cache", "yes")
                setOptionString("cache-pause-initial", "yes")
                setOptionString("demuxer-max-bytes", "32MiB")
                setOptionString("demuxer-max-back-bytes", "8MiB")
                // For the streams that go straight to mpv; the hub reconnects its own (5.4.8).
                setOptionString("stream-lavf-o", "reconnect=1,reconnect_streamed=1")
                // Errors only: engines log the URLs they open (brief 6.10).
                setOptionString("msg-level", "all=error")
            }
            return try {
                mpv.init()
                MpvEngine(mpv)
            } catch (failed: RuntimeException) {
                null
            }
        }
    }
}
