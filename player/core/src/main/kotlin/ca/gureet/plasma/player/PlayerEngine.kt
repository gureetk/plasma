// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.view.SurfaceView
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.BufferSize
import ca.gureet.plasma.core.model.Deinterlacing
import ca.gureet.plasma.core.model.VideoDecoding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The engines Plasma can play with (brief 5.4): VLC, mpv, and Media3, the Android player. */
enum class EngineId { Vlc, Mpv, Media3 }

/** What an engine is asked to play (brief 6.5, `PlayRequest`). */
data class PlayRequest(
    /** A local stream hub URL for live TS; the provider's URL for HLS and VOD. */
    val url: String,
    val live: Boolean = true,
    val buffer: BufferSize = BufferSize.Medium,
    val decoding: VideoDecoding = VideoDecoding.Hardware,
    val deinterlacing: Deinterlacing = Deinterlacing.Auto,
    /** Auto has been settled by now: passthrough when the TV or sound system takes Dolby or DTS. */
    val audioOutput: AudioOutput = AudioOutput.Passthrough,
    /** Only for streams that go straight to the engine; the hub sends its own (5.4.11). */
    val userAgent: String? = null,
    val referrer: String? = null,
    /** Where a movie or episode starts, in ms: where it was left (brief 5.9). */
    val startMs: Long = 0,
    /** Tunneled Playback (Settings › Playback › Advanced, brief 4.27): the Android player only. */
    val tunneling: Boolean = false,
)

sealed interface EngineState {
    data object Idle : EngineState

    data object Opening : EngineState

    data class Buffering(val percent: Float) : EngineState

    data object Playing : EngineState

    data object Paused : EngineState

    data object Ended : EngineState

    /** The engine couldn't play what it was given (brief 5.4.9, an engine fault). */
    data class Failed(val detail: String) : EngineState
}

/** The picture an engine reports (brief 6.5, `VideoInfo`). */
data class VideoInfo(
    val width: Int,
    val height: Int,
    /** Sample aspect ratio, so anamorphic SD shows correctly (5.4.4). */
    val sarNum: Int = 1,
    val sarDen: Int = 1,
    /** Frames per second, when the stream says. */
    val frameRate: Float? = null,
    val codec: String? = null,
)

/** The sound's format, as far as passing it through goes (brief 5.4.6). */
enum class AudioCodec {
    Ac3,
    EAc3,
    Dts,
    TrueHd,

    /** AAC, MPEG audio and the rest: always decoded. */
    Other,
    ;

    /** The format Settings › Audio's passthrough list calls it, if it can pass through. */
    val surround: SurroundFormat?
        get() = when (this) {
            Ac3 -> SurroundFormat.DolbyDigital
            EAc3 -> SurroundFormat.DolbyDigitalPlus
            Dts -> SurroundFormat.Dts
            TrueHd -> SurroundFormat.DolbyTrueHd
            Other -> null
        }
}

/** The sound an engine reports for what it plays. */
data class AudioInfo(val codec: AudioCodec, val channels: Int? = null)

/**
 * A sound or subtitle track in what's playing (brief 4.10, 5.4.6 and 5.4.7), as the engine reports
 * it. [id] is the engine's own, for [PlayerEngine.selectAudio] and [PlayerEngine.selectSubtitles].
 */
data class MediaTrack(
    val id: String,
    /** The language the stream gives it, as a code ("eng", "en", "fre"); null when it gives none. */
    val language: String? = null,
    /** The stream's own name for it: "English", "AD", "Commentary". */
    val title: String? = null,
    /** "ac3", "aac", "dvbsub", "telx"… */
    val codec: String? = null,
    /** Sound only: how many channels, when it's known. */
    val channels: Int? = null,
    /** Subtitles only: shown just where the spoken language isn't the viewer's (forced subtitles). */
    val forced: Boolean = false,
)

/** The tracks of what's playing, and which are on. */
data class MediaTracks(
    val audio: List<MediaTrack> = emptyList(),
    val subtitles: List<MediaTrack> = emptyList(),
    /** The sound track playing, by id. */
    val audioId: String? = null,
    /** The subtitle track showing, by id; null when subtitles are off. */
    val subtitleId: String? = null,
) {
    val playingAudio: MediaTrack? get() = audio.firstOrNull { it.id == audioId }
    val showingSubtitles: MediaTrack? get() = subtitles.firstOrNull { it.id == subtitleId }
}

/** A channel's own volume level, from −6 to +6 dB in 1 dB steps (brief 5.4.6). */
object VolumeLevels {
    const val MIN_DB = -6
    const val MAX_DB = 6
}

/**
 * The only thing the rest of Plasma knows about an engine (brief 6.5). Engines draw into the one
 * [SurfaceView] of the video layer (5.4.4) and are invisible otherwise: no engine interface ever
 * shows. Calls are made on the main thread.
 */
interface PlayerEngine {
    val id: EngineId

    val state: StateFlow<EngineState>

    val video: StateFlow<VideoInfo?>

    /** True once a picture is on screen for what was last opened. */
    val hasPicture: StateFlow<Boolean>

    /** The sound playing, once the engine knows it. */
    val audio: StateFlow<AudioInfo?>

    /** The sound and subtitle tracks of what's playing, and which are on (brief 4.10). */
    val tracks: StateFlow<MediaTracks>

    /** How far into a movie or episode it is, in ms (brief 4.9); 0 before it starts. */
    val position: StateFlow<Long>

    /** How long a movie or episode is, in ms, once the engine knows; 0 till then, and for live TV. */
    val length: StateFlow<Long>

    /**
     * The subtitles on screen, for engines that hand them out rather than draw them: the Android
     * player's cues, which Plasma draws over the picture (brief 5.4.4). None for VLC and mpv.
     */
    val cues: StateFlow<List<SubtitleCue>> get() = NO_CUES

    /**
     * Draw into [view]. Moving or resizing it later never restarts playback. [subtitles] is a
     * transparent surface over it, the same size, for engines that can't draw subtitles into the
     * picture (VLC with hardware decoding, brief 5.4.4).
     */
    fun attach(view: SurfaceView, subtitles: SurfaceView? = null)

    fun detach()

    /** The video layer's size in pixels, after it moves or resizes. */
    fun setWindowSize(width: Int, height: Int)

    fun open(request: PlayRequest)

    fun play()

    fun pause()

    fun stop()

    /** Jumps to [ms] into the movie or episode playing. */
    fun seekTo(ms: Long)

    /** 0 to 100; 0 for the muted picture behind Home (Settings › Launcher & Home). */
    fun setVolume(percent: Int)

    /**
     * A channel's own level in dB, [VolumeLevels.MIN_DB] to [VolumeLevels.MAX_DB], on sound the
     * engine decodes (brief 5.4.6). It stays until it's set again; set before a stream opens, it
     * applies from its first sound.
     */
    fun setLevel(db: Int)

    /** Plays the sound track [id], one of [tracks]' audio. */
    fun selectAudio(id: String)

    /** Shows the subtitle track [id], one of [tracks]' subtitles; null turns subtitles off. */
    fun selectSubtitles(id: String?)

    fun release()
}

/** The cues of an engine that draws its own subtitles. */
private val NO_CUES: StateFlow<List<SubtitleCue>> = MutableStateFlow<List<SubtitleCue>>(emptyList()).asStateFlow()
