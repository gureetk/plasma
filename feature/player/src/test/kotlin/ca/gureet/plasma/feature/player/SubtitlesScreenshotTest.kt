// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import ca.gureet.plasma.core.fixtures.SampleHost
import ca.gureet.plasma.core.testing.ScreenshotTest
import ca.gureet.plasma.player.CueAlign
import ca.gureet.plasma.player.CueAnchor
import ca.gureet.plasma.player.CuePlace
import ca.gureet.plasma.player.LiveStatus
import ca.gureet.plasma.player.LiveVideo
import ca.gureet.plasma.player.LocalLiveVideo
import ca.gureet.plasma.player.SubtitleCue
import ca.gureet.plasma.player.VideoFrame
import ca.gureet.plasma.player.VideoSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test

/**
 * The Android player's subtitles, which Plasma draws over the picture (brief 5.4.4 and 5.4.7):
 * white with a soft shadow, sized to the picture, where each cue says.
 */
class SubtitlesScreenshotTest : ScreenshotTest() {
    /** A movie playing in a 2.39:1 picture, letterboxed in the full screen, with its cues. */
    private class Playing(cues: List<SubtitleCue>) : LiveVideo {
        override val status: StateFlow<LiveStatus> = MutableStateFlow(LiveStatus.Playing(KEY))
        override val picture: StateFlow<String?> = MutableStateFlow(KEY)
        override val aspectRatio: StateFlow<Float?> = MutableStateFlow(SCOPE)
        override val cues: StateFlow<List<SubtitleCue>> = MutableStateFlow(cues)

        override fun attach(view: SurfaceView, subtitles: SurfaceView?) = Unit

        override fun detach(view: SurfaceView) = Unit

        override fun show(channelId: String?, muted: Boolean) = Unit
    }

    @Test fun cuesOverThePicture() {
        val cues = listOf(
            SubtitleCue.Text("The light's still burning.\nSomeone's been keeping it."),
            SubtitleCue.Text("KEEPER'S LOG — 1987", CuePlace(x = 0.5f, xAnchor = CueAnchor.Middle, y = 0.1f), CueAlign.Center),
            SubtitleCue.Text("[WIND HOWLING]", CuePlace(x = 0.1f, row = 0), CueAlign.Start),
        )
        compose.setContent {
            CompositionLocalProvider(LocalLiveVideo provides Playing(cues)) {
                SampleHost { VideoSlot(VideoFrame(still = null, channelId = KEY), Modifier.fillMaxSize()) }
            }
        }
        capture("09-player-subtitles")
    }

    private companion object {
        const val KEY = "vod:movie:home:7101"
        const val SCOPE = 2.39f
    }
}
