// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.media3

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.player.AudioInfo
import ca.gureet.plasma.player.EngineId
import ca.gureet.plasma.player.EngineState
import ca.gureet.plasma.player.MediaTracks
import ca.gureet.plasma.player.PlayRequest
import ca.gureet.plasma.player.PlayerEngine
import ca.gureet.plasma.player.SubtitleCue
import ca.gureet.plasma.player.VideoInfo
import ca.gureet.plasma.player.VolumeLevels
import ca.gureet.plasma.player.audioCodecOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The Android player (brief 5.4): Media3's ExoPlayer, for Dolby Vision and HDR10+ movies, which VLC
 * and mpv can't show, and as the last engine to try. Like the others it draws only video, into
 * Plasma's one surface, and its own interface never shows. Its subtitles come out as cues, which
 * Plasma draws over the picture (5.4.4).
 *
 * Each stream gets a player of its own, built for what it asks (its buffer, whether its sound is
 * decoded, its headers), so nothing from the stream before can reach it. Calls are made on the main
 * thread, where the player's events arrive too.
 */
@OptIn(UnstableApi::class)
class Media3Engine private constructor(private val context: Context, private val http: Call.Factory) : PlayerEngine {
    override val id = EngineId.Media3

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

    private val _cues = MutableStateFlow<List<SubtitleCue>>(emptyList())
    override val cues: StateFlow<List<SubtitleCue>> = _cues.asStateFlow()

    /** The player for the stream opened last; none before the first, and once it's stopped. */
    private var player: ExoPlayer? = null

    /** Whether that stream is live TV, which has no length or position to report. */
    private var live = true

    private var view: SurfaceView? = null

    /** How to pick each of the stream's sound and subtitle tracks, by id. */
    private var picks: Map<String, TrackSelectionOverride> = emptyMap()

    /** The volume asked for: applied to each stream's player, so Home's picture stays muted. */
    private var volume = MAX_VOLUME

    /** The channel's own level, in dB (5.4.6): the player's volume cuts, and [boost] raises. */
    private var levelDb = 0

    /** One sound session for every stream, which [boost] works on. */
    private val audioSession: Int = context.getSystemService(AudioManager::class.java)?.generateAudioSessionId() ?: AudioManager.ERROR

    /** Raises the sound for a level above 0 dB, which the player's volume can't (5.4.6). */
    private var boost: LoudnessEnhancer? = null

    private var released = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = updateState()

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = updateState()

        override fun onPlayerError(error: PlaybackException) {
            val playing = player ?: return
            // A live HLS stream that fell behind its window carries on at the live edge.
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                playing.seekToDefaultPosition()
                playing.prepare()
                return
            }
            _hasPicture.value = false
            _cues.value = emptyList()
            _state.value = EngineState.Failed("The Android player couldn't play the stream: ${error.errorCodeName}")
        }

        override fun onRenderedFirstFrame() {
            _hasPicture.value = true
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width <= 0 || videoSize.height <= 0) return
            val (sarNum, sarDen) = sampleAspect(videoSize.pixelWidthHeightRatio)
            _video.value = (_video.value ?: VideoInfo(0, 0)).copy(width = videoSize.width, height = videoSize.height, sarNum = sarNum, sarDen = sarDen)
        }

        override fun onTracksChanged(tracks: Tracks) = readTracks(tracks)

        override fun onCues(cueGroup: CueGroup) {
            _cues.value = cueGroup.cues.mapNotNull(::subtitleCueOf)
        }
    }

    private val formats = object : AnalyticsListener {
        override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
            _video.value = (_video.value ?: VideoInfo(format.width.coerceAtLeast(0), format.height.coerceAtLeast(0))).copy(
                frameRate = format.frameRate.takeIf { it > 0f },
                codec = codecName(format),
            )
        }

        override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
            _audio.value = AudioInfo(audioCodecOf(codecName(format)), format.channelCount.takeIf { it != Format.NO_VALUE && it > 0 })
        }
    }

    /** Where a movie or episode has got to, a few times a second while it plays (brief 4.9). */
    private val ticker = object : Runnable {
        override fun run() {
            val playing = player ?: return
            _position.value = playing.currentPosition.coerceAtLeast(0)
            _length.value = playing.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0
            main.postDelayed(this, TICK_MS)
        }
    }

    override fun attach(view: SurfaceView, subtitles: SurfaceView?) {
        detach()
        this.view = view
        player?.setVideoSurfaceView(view)
    }

    override fun detach() {
        val attached = view ?: return
        player?.clearVideoSurfaceView(attached)
        view = null
    }

    // The player scales the picture to the surface, which Plasma sizes as Picture Size says (5.4.4).
    override fun setWindowSize(width: Int, height: Int) = Unit

    override fun open(request: PlayRequest) {
        releasePlayer()
        _hasPicture.value = false
        _video.value = null
        _audio.value = null
        _tracks.value = MediaTracks()
        _cues.value = emptyList()
        _position.value = request.startMs
        _length.value = 0
        _state.value = EngineState.Opening
        live = request.live
        val made = try {
            build(request)
        } catch (failed: RuntimeException) {
            _state.value = EngineState.Failed("The Android player couldn't start: ${failed.javaClass.simpleName}")
            return
        }
        player = made
        view?.let(made::setVideoSurfaceView)
        applyVolume()
        val item = MediaItem.Builder()
            .setUri(request.url)
            .apply { if (isHls(request.url)) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        // A movie or episode carries on where it was left (brief 5.9); live TV from its live edge.
        if (request.startMs > 0) made.setMediaItem(item, request.startMs) else made.setMediaItem(item)
        made.prepare()
        if (!live) main.postDelayed(ticker, TICK_MS)
    }

    /**
     * A player for [request]: Media3's own decoders and extractors, its sound passed through or
     * decoded as Audio Output says (5.4.6), Buffer Size before it starts (5.4.11), subtitles off
     * until the player picks them (5.4.7), and the frame rate left to Plasma (5.4.5).
     */
    private fun build(request: PlayRequest): ExoPlayer {
        val decode = request.audioOutput == AudioOutput.DecodeToPcm
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
                val sink = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                    .build()
                return if (decode) DecodedOnly(sink) else sink
            }
        }.setEnableDecoderFallback(true)
        val upstream = OkHttpDataSource.Factory(http)
            .setUserAgent(request.userAgent)
            .setDefaultRequestProperties(listOfNotNull(request.referrer?.let { REFERER to it }).toMap())
        // Broadcast streams can lack IDR frames and access unit delimiters, and Blu-ray rips carry DTS.
        val extractors = DefaultExtractorsFactory().setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS,
        )
        val buffer = request.buffer.millis
        val minimum = maxOf(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS, buffer * 2)
        val loading = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minimum, maxOf(DefaultLoadControl.DEFAULT_MAX_BUFFER_MS, minimum), buffer, buffer * 2)
            .build()
        val selector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setTunnelingEnabled(request.tunneling)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true),
            )
        }
        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, upstream), extractors))
            .setTrackSelector(selector)
            .setLoadControl(loading)
            .setAudioAttributes(MOVIE_SOUND, false)
            .setHandleAudioBecomingNoisy(false)
            .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
            .setUsePlatformDiagnostics(false)
            .build()
            .apply {
                if (audioSession != AudioManager.ERROR) setAudioSessionId(audioSession)
                addListener(listener)
                addAnalyticsListener(formats)
            }
    }

    private fun updateState() {
        val playing = player ?: return
        // A failure stays until the next stream opens.
        if (_state.value is EngineState.Failed) return
        _state.value = when (playing.playbackState) {
            Player.STATE_BUFFERING -> EngineState.Buffering(0f)
            Player.STATE_READY -> if (playing.playWhenReady) EngineState.Playing else EngineState.Paused
            Player.STATE_ENDED -> EngineState.Ended
            else -> return
        }
    }

    /** The sound and subtitle tracks, and which are on (4.10). */
    private fun readTracks(tracks: Tracks) {
        val list = trackListOf(tracks)
        picks = list.picks
        _tracks.value = list.tracks
    }

    /**
     * What the player has shown and played of the stream opened last (Restart Frozen Streams,
     * #91): its decoders' counts of video frames rendered and sound handed to the TV, which move
     * on only as the picture and sound do. Frames skipped or dropped don't count, and nor does the
     * position, which the clock can carry on while frames wait. Null before a stream opens.
     */
    override fun output(): Long? {
        val playing = player ?: return null
        return listOfNotNull(playing.videoDecoderCounters, playing.audioDecoderCounters).sumOf { counters ->
            // They're kept on the player's own thread.
            counters.ensureUpdated()
            counters.renderedOutputBufferCount.toLong()
        }
    }

    override fun play() {
        player?.playWhenReady = true
    }

    override fun pause() {
        player?.playWhenReady = false
    }

    override fun seekTo(ms: Long) {
        _position.value = ms
        player?.seekTo(ms)
    }

    /** Stops, and lets go of the decoders, until the next stream opens. */
    override fun stop() {
        releasePlayer()
        _hasPicture.value = false
        _cues.value = emptyList()
        _state.value = EngineState.Idle
    }

    private fun releasePlayer() {
        main.removeCallbacks(ticker)
        val playing = player ?: return
        player = null
        picks = emptyMap()
        playing.removeListener(listener)
        playing.removeAnalyticsListener(formats)
        view?.let(playing::clearVideoSurfaceView)
        playing.release()
    }

    override fun setVolume(percent: Int) {
        volume = percent.coerceIn(0, MAX_VOLUME)
        applyVolume()
    }

    override fun setLevel(db: Int) {
        levelDb = db.coerceIn(VolumeLevels.MIN_DB, VolumeLevels.MAX_DB)
        applyVolume()
    }

    /** The volume, with the channel's level: a cut through the player, a boost on the session (5.4.6). */
    private fun applyVolume() {
        val cut = if (levelDb < 0) DB_BASE.pow(levelDb / DB_PER_DECADE) else 1f
        player?.volume = volume / MAX_VOLUME.toFloat() * cut
        val raise = levelDb.coerceAtLeast(0)
        if (raise == 0) {
            boost?.let { runCatching { it.enabled = false } }
            return
        }
        if (audioSession == AudioManager.ERROR) return
        // Not every TV has the effect; the sound then stays at its own level.
        val effect = boost ?: runCatching { LoudnessEnhancer(audioSession) }.getOrNull()?.also { boost = it } ?: return
        runCatching {
            effect.setTargetGain(raise * MILLIBELS_PER_DB)
            effect.enabled = true
        }
    }

    override fun selectAudio(id: String) {
        val pick = picks[id] ?: return
        val playing = player ?: return
        playing.trackSelectionParameters = playing.trackSelectionParameters.buildUpon().setOverrideForType(pick).build()
    }

    override fun selectSubtitles(id: String?) {
        val playing = player ?: return
        val choice = playing.trackSelectionParameters.buildUpon()
        if (id == null) {
            choice.clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val pick = picks[id] ?: return
            choice.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(pick)
        }
        playing.trackSelectionParameters = choice.build()
    }

    override fun release() {
        if (released) return
        released = true
        detach()
        releasePlayer()
        boost?.let { runCatching { it.release() } }
        boost = null
    }

    /**
     * The sound sink when Audio Output decodes (5.4.6): it takes only PCM, so Dolby and DTS are
     * decoded rather than passed through, whatever the TV or sound system takes.
     */
    private class DecodedOnly(sink: AudioSink) : ForwardingAudioSink(sink) {
        override fun supportsFormat(format: Format): Boolean = isPcm(format) && super.supportsFormat(format)

        override fun getFormatSupport(format: Format): Int = if (isPcm(format)) super.getFormatSupport(format) else AudioSink.SINK_FORMAT_UNSUPPORTED

        override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport =
            if (isPcm(format)) super.getFormatOffloadSupport(format) else AudioOffloadSupport.DEFAULT_UNSUPPORTED

        private fun isPcm(format: Format) = format.sampleMimeType == MimeTypes.AUDIO_RAW
    }

    companion object {
        private const val MAX_VOLUME = 100
        private const val TICK_MS = 500L
        private const val MILLIBELS_PER_DB = 100
        private const val DB_BASE = 10f
        private const val DB_PER_DECADE = 20f
        private const val REFERER = "Referer"

        private val MOVIE_SOUND = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()

        /**
         * The Android player, made the first time it's needed (brief 5.4.3); null when Media3 can't
         * load on this TV. Its streams' requests go through [http], with Plasma's user agent.
         */
        fun create(context: Context, http: Call.Factory): Media3Engine? = try {
            // Errors only, as the other engines log: what it opens has a provider's login in it (brief 6.10).
            Log.setLogLevel(Log.LOG_LEVEL_ERROR)
            Media3Engine(context.applicationContext, http)
        } catch (missing: LinkageError) {
            null
        } catch (failed: RuntimeException) {
            null
        }

        /** An HLS playlist, by its address: Media3 picks how to read a stream from it. */
        internal fun isHls(url: String): Boolean {
            val path = url.substringBefore('#')
            return path.substringBefore('?').endsWith(".m3u8", ignoreCase = true) || path.contains("extension=m3u8", ignoreCase = true)
        }

        /** A pixel's width over its height as a fraction, as the other engines report it: 1.333 is 4:3. */
        internal fun sampleAspect(ratio: Float): Pair<Int, Int> {
            if (ratio <= 0f || ratio == 1f) return 1 to 1
            return (ratio * SAR_SCALE).roundToInt().coerceAtLeast(1) to SAR_SCALE
        }

        private const val SAR_SCALE = 1_000
    }
}
