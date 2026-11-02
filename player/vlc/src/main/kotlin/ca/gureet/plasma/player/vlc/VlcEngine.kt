// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.vlc

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.Deinterlacing
import ca.gureet.plasma.core.model.VideoDecoding
import ca.gureet.plasma.player.AudioInfo
import ca.gureet.plasma.player.EngineCount
import ca.gureet.plasma.player.EngineId
import ca.gureet.plasma.player.EngineState
import ca.gureet.plasma.player.EngineStats
import ca.gureet.plasma.player.KeptOutput
import ca.gureet.plasma.player.MediaTracks
import ca.gureet.plasma.player.NightMode
import ca.gureet.plasma.player.PlayRequest
import ca.gureet.plasma.player.PlayerEngine
import ca.gureet.plasma.player.VideoInfo
import ca.gureet.plasma.player.VolumeLevels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
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

    /** The engine's own sound session, which its sound plays in and Night Mode works on (5.4.6). */
    private val session = context.getSystemService(AudioManager::class.java)?.generateAudioSessionId() ?: AudioManager.ERROR

    // VLC's own timeshift stays idle, as the hub keeps the stream while paused (brief 5.5); should
    // it ever start, it writes to the app's cache, as Android has no /tmp.
    private val libVlc = LibVLC(
        context.applicationContext,
        ArrayList(OPTIONS + "--input-timeshift-path=${context.cacheDir.absolutePath}" + sessionOptions(session)),
    )
    private val player = MediaPlayer(libVlc)
    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "plasma-vlc").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _video = MutableStateFlow<VideoInfo?>(null)
    override val video: StateFlow<VideoInfo?> = _video.asStateFlow()

    private val _hasPicture = MutableStateFlow(false)
    override val hasPicture: StateFlow<Boolean> = _hasPicture.asStateFlow()

    private val _audio = MutableStateFlow<AudioInfo?>(null)
    override val audio: StateFlow<AudioInfo?> = _audio.asStateFlow()

    private val _tracks = MutableStateFlow(MediaTracks())
    override val tracks: StateFlow<MediaTracks> = _tracks.asStateFlow()

    private val _position = MutableStateFlow(0L)
    override val position: StateFlow<Long> = _position.asStateFlow()

    private val _length = MutableStateFlow(0L)
    override val length: StateFlow<Long> = _length.asStateFlow()

    private var view: SurfaceView? = null

    /** Where VLC draws subtitles: with hardware decoding, it can't draw them into the picture (5.4.4). */
    private var subtitlesView: SurfaceView? = null
    private val onLayout = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> resize(v.width, v.height) }

    /** The volume asked for: applied again as each stream starts, so Home's picture stays muted. */
    @Volatile private var volume = MAX_VOLUME

    /** The channel's own level, in dB (brief 5.4.6). */
    @Volatile private var levelDb = 0

    /** Audio Delay, in ms (5.4.6): libVLC forgets it with each stream, so it's set again as each starts. */
    @Volatile private var delayMs = 0

    private val nightMode = NightMode(session)

    /** Stereo Downmix has set libVLC's output to two channels (5.4.6). Used on the worker only. */
    private var stereo = false

    // libVLC's events don't say which media they're about. An event from the stream before can
    // arrive after the next one is opened (an error as it closes, say), so events count only once
    // the worker has handed the player the media opened last.
    private var opened = 0
    private var current = 0

    /**
     * The stream opened last's count for Restart Frozen Streams (#91): its statistics' pictures
     * kept and sound buffers played, as [KeptOutput] counts them at each look; null before the
     * first look, or without statistics.
     */
    private var shown: Long? = null
    private var kept = KeptOutput()

    /** Its statistics at the last look, for Show Freeze Watch. */
    private var counted: Map<EngineCount, Long> = emptyMap()

    /** A look at the statistics is on its way through the worker, and when the last was asked for. */
    private var looking = false
    private var lookedAt = 0L

    /** Hardware decoding was asked for, for the stream opened last: VLC falls back to software by itself. */
    private var hardware = true

    /** Show Stream Stats' last look at the stream opened last (5.4.9), and whether the next is on its way. */
    private var lastStats: EngineStats? = null
    private var lookingAtStats = false

    init {
        player.setEventListener { event ->
            if (current != opened) return@setEventListener
            when (event.type) {
                MediaPlayer.Event.Opening -> {
                    _state.value = EngineState.Opening
                    applyDelay()
                }
                MediaPlayer.Event.Buffering ->
                    _state.value = if (event.buffering < 100f) EngineState.Buffering(event.buffering) else EngineState.Playing
                MediaPlayer.Event.Playing -> {
                    _state.value = EngineState.Playing
                    applyVolume()
                    applyDelay()
                    readTracks()
                }
                MediaPlayer.Event.Paused -> _state.value = EngineState.Paused
                MediaPlayer.Event.EndReached -> _state.value = EngineState.Ended
                MediaPlayer.Event.EncounteredError -> _state.value = EngineState.Failed("VLC couldn't play the stream")
                MediaPlayer.Event.Stopped -> if (_state.value !is EngineState.Failed && _state.value != EngineState.Ended) _state.value = EngineState.Idle
                MediaPlayer.Event.Vout -> {
                    _hasPicture.value = event.voutCount > 0
                    readVideoTrack()
                }
                MediaPlayer.Event.ESAdded -> {
                    applyVolume()
                    applyDelay()
                    readTracks()
                }
                MediaPlayer.Event.ESDeleted -> readTracks()
                MediaPlayer.Event.TimeChanged -> _position.value = event.timeChanged
                MediaPlayer.Event.LengthChanged -> _length.value = event.lengthChanged
                MediaPlayer.Event.ESSelected -> {
                    readVideoTrack()
                    readTracks()
                    applyVolume()
                }
            }
        }
    }

    /** The sound output can come and go with a stream, and a volume set while it's gone is lost. */
    private fun applyVolume() = onWorker { player.setVolume(vlcVolume(volume, levelDb)) }

    /** Audio Delay on the stream playing; libVLC takes it in µs, once the stream has started to open. */
    private fun applyDelay() = onWorker {
        val wanted = delayMs * MICROS_PER_MILLI
        if (player.audioDelay != wanted) player.setAudioDelay(wanted)
    }

    /** For work that events start: an event that arrives after release() has nothing left to do. */
    private fun onWorker(block: () -> Unit) {
        if (!worker.isShutdown) worker.execute(block)
    }

    override fun attach(view: SurfaceView, subtitles: SurfaceView?) {
        detach()
        this.view = view
        subtitlesView = subtitles
        attachViews(view)
        view.holder.addCallback(surfaceBack)
        subtitles?.holder?.addCallback(surfaceBack)
        view.addOnLayoutChangeListener(onLayout)
        resize(view.width, view.height)
    }

    override fun detach() {
        val attached = view ?: return
        attached.holder.removeCallback(surfaceBack)
        subtitlesView?.holder?.removeCallback(surfaceBack)
        attached.removeOnLayoutChangeListener(onLayout)
        val vout = player.getVLCVout()
        if (vout.areViewsAttached()) vout.detachViews()
        view = null
        subtitlesView = null
    }

    /** Until its surface exists, libVLC holds back play() and starts it once the surface is there. */
    private fun attachViews(view: SurfaceView) {
        val vout = player.getVLCVout()
        vout.setVideoView(view)
        subtitlesView?.let(vout::setSubtitlesView)
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
        _audio.value = null
        _tracks.value = MediaTracks()
        _position.value = request.startMs
        _length.value = 0
        _state.value = EngineState.Opening
        shown = null
        kept = KeptOutput()
        counted = emptyMap()
        lookedAt = 0
        lastStats = null
        hardware = request.decoding != VideoDecoding.Software
        val generation = ++opened
        worker.execute {
            val media = Media(libVlc, Uri.parse(request.url))
            // A movie or episode carries on where it was left (brief 5.9).
            if (request.startMs > 0) media.addOption(":start-time=${request.startMs / MILLIS_PER_SECOND}")
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
            route(request)
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

    override fun seekTo(ms: Long) {
        _position.value = ms
        onWorker { player.setTime(ms) }
    }

    override fun stop() {
        _hasPicture.value = false
        worker.execute { player.stop() }
    }

    override fun setVolume(percent: Int) {
        volume = percent.coerceIn(0, MAX_VOLUME)
        applyVolume()
    }

    override fun setLevel(db: Int) {
        levelDb = db.coerceIn(VolumeLevels.MIN_DB, VolumeLevels.MAX_DB)
        applyVolume()
    }

    override fun setNightMode(on: Boolean) = nightMode.set(on)

    override fun setAudioDelay(ms: Int) {
        delayMs = ms
        applyDelay()
    }

    /**
     * Where the sound goes (5.4.6). libVLC picks the output itself, and follows the TV's outputs
     * as they come and go: Dolby and DTS pass through to a system that takes them, unless Audio
     * Output decodes. Stereo Downmix sets libVLC's two-channel output instead. Called on the worker.
     */
    private fun route(request: PlayRequest) {
        val passthrough = request.audioOutput != AudioOutput.DecodeToPcm
        if (request.stereoDownmix) {
            if (!stereo) player.setAudioOutputDevice(STEREO_OUTPUT)
            stereo = true
            return
        }
        if (stereo) {
            stereo = false
            // Back to libVLC's own pick. Setting the output again has it follow the TV's outputs,
            // and as it hands over an output only when its pick changes, it's asked for the other
            // kind first.
            player.setAudioOutput(AUDIO_TRACK)
            player.setAudioDigitalOutputEnabled(!passthrough)
        }
        player.setAudioDigitalOutputEnabled(passthrough)
    }

    override fun selectAudio(id: String) {
        val track = id.toIntOrNull() ?: return
        onWorker { player.setAudioTrack(track) }
        readTracks()
    }

    override fun selectSubtitles(id: String?) {
        val track = id?.toIntOrNull() ?: OFF
        onWorker { player.setSpuTrack(track) }
        readTracks()
    }

    /**
     * What VLC has shown and played of the stream opened last (#91): its statistics' pictures
     * decoded and not lost, and sound buffers played, as [KeptOutput] counts them. Not its pictures
     * decoded alone: when a stream's clock jumps, VLC goes on decoding and drops every picture and
     * sound buffer as too late, so that count kept moving through a freeze on the owner's TV
     * (2 November). Nor its pictures displayed: VLC shows its last picture again several times a
     * second while a stream is stuck, and counts each (1 November). Nor the time VLC has read the
     * stream to. The statistics are read on the worker, as everything that touches the player is,
     * so each call answers with the last look and asks for the next.
     */
    override fun output(): Long? {
        lookAtStats()
        return shown
    }

    /** Show Freeze Watch: VLC's statistics for the stream opened last, at the last look. */
    override fun counts(): Map<EngineCount, Long> {
        lookAtStats()
        return counted
    }

    /**
     * Has the worker read the statistics, at most about once a second: [KeptOutput] wants its
     * looks that far apart, whichever of [output] and [counts] asks.
     */
    private fun lookAtStats() {
        val now = SystemClock.uptimeMillis()
        if (looking || now - lookedAt < MIN_LOOK_MS) return
        looking = true
        lookedAt = now
        val generation = opened
        onWorker {
            val stats = readStats()
            main.post {
                looking = false
                // Only for the stream opened last: one before it has nothing to say.
                if (opened != generation || stats == null) return@post
                shown = kept.look(stats.decodedVideo, stats.lostPictures, stats.playedAbuffers)
                counted = countsOf(stats)
            }
        }
    }

    /**
     * Show Stream Stats (5.4.9): VLC's version and decoding, and the pictures it lost, from its
     * statistics. They're read on the worker, as for [output]: each call answers with the last
     * look and asks for the next. libVLC doesn't say how much it holds ahead.
     */
    override fun stats(): EngineStats? {
        if (!lookingAtStats) {
            lookingAtStats = true
            val generation = opened
            val hardware = hardware
            onWorker {
                val lost = readStats()?.lostPictures?.toLong()
                main.post {
                    lookingAtStats = false
                    if (opened == generation) {
                        lastStats = EngineStats(
                            version = version().takeIf { it.isNotEmpty() },
                            decoder = if (hardware) HARDWARE_DECODER else SOFTWARE_DECODER,
                            hardware = hardware,
                            droppedFrames = lost,
                        )
                    }
                }
            }
        }
        return lastStats
    }

    /** The media's statistics so far, read on the worker; null without them. */
    private fun readStats(): IMedia.Stats? {
        // Taking the media takes a reference: it's let go again here.
        val media = player.media ?: return null
        return try {
            media.stats
        } catch (released: IllegalStateException) {
            null
        } finally {
            media.release()
        }
    }

    override fun release() {
        detach()
        nightMode.release()
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

    /**
     * The sound and subtitle tracks, and which are on (4.10), and the sound playing's format, so
     * Plasma knows whether it passes through (5.4.6). libVLC lists the tracks it can switch to;
     * the media says what each one is.
     */
    private fun readTracks() {
        val generation = opened
        onWorker {
            val audioIds = player.audioTracks.orEmpty().map { it.id }.filter { it != OFF }
            val subtitleIds = player.spuTracks.orEmpty().map { it.id }.filter { it != OFF }
            val playing = player.audioTrack
            val showing = player.spuTrack
            // Taking the media takes a reference: it's let go again here.
            val media = player.media ?: return@onWorker
            val described = try {
                (0 until media.trackCount).mapNotNull(media::getTrack)
            } finally {
                media.release()
            }
            val sounds = described.filterIsInstance<IMedia.AudioTrack>()
            val texts = described.filterIsInstance<IMedia.SubtitleTrack>()
            val tracks = MediaTracks(
                audio = audioIds.map { id -> mediaTrack(id, sounds.firstOrNull { it.id == id }) },
                subtitles = subtitleIds.map { id -> mediaTrack(id, texts.firstOrNull { it.id == id }) },
                audioId = playing.takeIf { it in audioIds }?.toString(),
                subtitleId = showing.takeIf { it in subtitleIds }?.toString(),
            )
            val sound = (sounds.firstOrNull { it.id == playing } ?: sounds.firstOrNull())
                ?.let { AudioInfo(vlcCodec(it.codec), it.channels.takeIf { channels -> channels > 0 }) }
            main.post {
                // Only for the stream opened last: one before it has nothing to say.
                if (opened != generation) return@post
                _tracks.value = tracks
                _audio.value = sound
            }
        }
    }

    companion object {
        private const val MAX_VOLUME = 100
        private const val MILLIS_PER_SECOND = 1_000.0
        private const val MICROS_PER_MILLI = 1_000L

        /** The least time between two looks at the statistics: the player and the overlay each ask once a second. */
        private const val MIN_LOOK_MS = 900L

        /** VLC's statistics as Show Freeze Watch lists them. */
        private fun countsOf(stats: IMedia.Stats): Map<EngineCount, Long> = mapOf(
            EngineCount.PicturesDecoded to stats.decodedVideo.toLong(),
            EngineCount.PicturesShown to stats.displayedPictures.toLong(),
            EngineCount.PicturesDropped to stats.lostPictures.toLong(),
            EngineCount.SoundPlayed to stats.playedAbuffers.toLong(),
            EngineCount.SoundDropped to stats.lostAbuffers.toLong(),
            EngineCount.Discontinuities to stats.demuxDiscontinuity.toLong(),
            EngineCount.Corrupted to stats.demuxCorrupted.toLong(),
        )

        /** libVLC's Android sound output, and its two-channel device (5.4.6). */
        private const val AUDIO_TRACK = "android_audiotrack"
        private const val STEREO_OUTPUT = "stereo"

        /** libVLC's id for no track: subtitles off. */
        private const val OFF = -1

        /** VLC's names for its decoders: Android's MediaCodec, or FFmpeg's in software. */
        private const val HARDWARE_DECODER = "mediacodec"
        private const val SOFTWARE_DECODER = "avcodec"

        /** libVLC's version, for the user agent providers see (brief 5.4.2): "3.0.21 Vetinari". */
        fun version(): String = runCatching { LibVLC.version().substringBefore(' ') }.getOrDefault("")

        /** The sound session libVLC's output plays in, when Android gave one (5.4.6). */
        private fun sessionOptions(session: Int): List<String> = if (session > 0) listOf("--audiotrack-session-id=$session") else emptyList()

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
