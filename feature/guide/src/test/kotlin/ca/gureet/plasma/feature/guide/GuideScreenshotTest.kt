// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.guide

import android.view.SurfaceView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithContentDescription
import ca.gureet.plasma.core.fixtures.SampleHost
import ca.gureet.plasma.core.testing.ScreenshotTest
import ca.gureet.plasma.player.LiveStatus
import ca.gureet.plasma.player.LiveVideo
import ca.gureet.plasma.player.LocalLiveVideo
import ca.gureet.plasma.player.SubtitleCue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test

/** The Guide against board 08 (docs/design/screens). */
class GuideScreenshotTest : ScreenshotTest() {
    @Test fun guide() {
        screen { GuideScreen(GuideSamples.state, GuideActions()) }
        capture("08-guide")
    }

    /** A day still loading after a moment (#158): programs' shapes in each row. */
    @Test fun aDayStillLoading() {
        val base = GuideSamples.state
        screen { GuideScreen(base.copy(loading = true, channels = base.channels.map { it.copy(programs = emptyList()) }), GuideActions()) }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        capture("08-guide-loading")
    }

    /**
     * The picture while its channel opens, with live video: after a moment a ring turns in it, as
     * in the player.
     */
    @Test fun channelOpening() {
        val live = OpeningVideo()
        compose.setContent {
            CompositionLocalProvider(LocalLiveVideo provides live) {
                SampleHost { GuideScreen(withLiveVideo, GuideActions()) }
            }
        }
        // Not at once, so a quick switch never flashes it.
        compose.onNodeWithContentDescription("Loading").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Loading").assertExists()
        capture("08-guide-opening")
    }

    /** Board 08 with live video: each channel's picture plays in the slot, rather than its still. */
    private val withLiveVideo: GuideUiState
        get() = GuideSamples.state.let { s ->
            s.copy(
                channels = s.channels.map { it.copy(frame = it.frame.copy(channelId = it.id)) },
                playing = s.playing?.let { it.copy(frame = it.frame.copy(channelId = it.id)) },
            )
        }

    /** Live video whose channels open and stay opening, with no picture. */
    private class OpeningVideo : LiveVideo {
        override val status = MutableStateFlow<LiveStatus>(LiveStatus.Idle)
        override val picture: StateFlow<String?> = MutableStateFlow(null)
        override val aspectRatio: StateFlow<Float?> = MutableStateFlow(null)
        override val cues: StateFlow<List<SubtitleCue>> = MutableStateFlow(emptyList())

        override fun attach(view: SurfaceView, subtitles: SurfaceView?) = Unit

        override fun detach(view: SurfaceView) = Unit

        override fun show(channelId: String?, muted: Boolean) {
            status.value = channelId?.let { LiveStatus.Opening(it) } ?: LiveStatus.Idle
        }
    }
}
