// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.view.SurfaceView
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.BufferSize
import ca.gureet.plasma.core.model.Deinterlacing
import ca.gureet.plasma.core.model.VideoDecoding
import kotlinx.coroutines.flow.StateFlow

/** The engines Plasma can play with (brief 5.4). M1 has VLC; mpv and Media3 follow. */
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

    /** Draw into [view]. Moving or resizing it later never restarts playback. */
    fun attach(view: SurfaceView)

    fun detach()

    /** The video layer's size in pixels, after it moves or resizes. */
    fun setWindowSize(width: Int, height: Int)

    fun open(request: PlayRequest)

    fun play()

    fun pause()

    fun stop()

    /** 0 to 100; 0 for the muted picture behind Home (Settings › Launcher & Home). */
    fun setVolume(percent: Int)

    fun release()
}
