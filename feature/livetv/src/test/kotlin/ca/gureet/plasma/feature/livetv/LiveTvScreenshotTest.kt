// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.livetv

import android.view.SurfaceView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import ca.gureet.plasma.core.design.LARGE_TEXT_SCALE
import ca.gureet.plasma.core.design.PlasmaTheme
import ca.gureet.plasma.core.fixtures.SampleHost
import ca.gureet.plasma.core.model.LiveListShows
import ca.gureet.plasma.core.testing.ScreenshotTest
import ca.gureet.plasma.player.LiveFailure
import ca.gureet.plasma.player.LiveStatus
import ca.gureet.plasma.player.LiveVideo
import ca.gureet.plasma.player.LocalLiveVideo
import ca.gureet.plasma.player.SubtitleCue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test

/** Live TV against board 07 (docs/design/screens). */
class LiveTvScreenshotTest : ScreenshotTest() {
    @Test fun liveTv() {
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions()) }
        capture("07-live-tv")
    }

    /**
     * The schedule under the preview, as far as the guide goes (the owner's ask, 28 October):
     * scrolled down it, with more below.
     */
    @Test fun schedule() {
        screen { LiveTvScreen(LiveTvSamples.withLongSchedule, LiveTvActions()) }
        press(Key.DirectionRight)
        press(Key.DirectionDown, times = 5)
        capture("07-live-tv-schedule")
    }

    /** Settings › TV Guide › Channel List Shows › Channel Names (the owner's #137). */
    @Test fun channelNames() {
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions(), listShows = LiveListShows.ChannelNames) }
        capture("07-live-tv-channel-names")
    }

    /** Settings › Appearance › Text Size › Large. */
    @Test fun largeText() {
        screen { PlasmaTheme(textScale = LARGE_TEXT_SCALE) { LiveTvScreen(LiveTvSamples.state, LiveTvActions()) } }
        capture("07-live-tv-large-text")
    }

    /** The Volume Level bar (brief 4.35 and 5.4.6) over board 07: it has no board of its own. */
    @Test fun volumeLevel() {
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions()) }
        press(Key.Menu)
        repeat(12) { if (runCatching { focused().assert(hasText("Volume Level")) }.isFailure) press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        press(Key.DirectionRight, times = 3)
        capture("07-live-tv-volume-level")
    }

    /** Channel options › Change Number (brief 4.35): the number pad, with a free number typed. */
    @Test fun changeNumber() {
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions()) }
        press(Key.Menu)
        repeat(12) { if (runCatching { focused().assert(hasText("Change Number")) }.isFailure) press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        listOf(Key.Five, Key.Zero, Key.Five).forEach { press(it) }
        capture("07-live-tv-change-number")
    }

    /** Channel options › Channel Info, on a channel from two sources. */
    @Test fun channelInfo() {
        val state = LiveTvSamples.state.let { s ->
            s.copy(
                channels = s.channels.mapValues { (_, list) ->
                    list.map {
                        if (it.id != "ch105") it else it.copy(sources = listOf(LiveSource("home:105", "Home Portal"), LiveSource("backup:105", "Backup Line")), guideId = "arena1.example")
                    }
                },
            )
        }
        screen { LiveTvScreen(state, LiveTvActions(), playing = LivePlaying("ch105", "home:105")) }
        press(Key.Menu)
        repeat(12) { if (runCatching { focused().assert(hasText("Channel Info")) }.isFailure) press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        capture("07-live-tv-channel-info")
    }

    /** Move in Favorites (brief 4.7): board 04's move mode, down a list. */
    @Test fun moveInFavorites() {
        screen { LiveTvScreen(LiveTvSamples.state.copy(selectedGroupId = "favorites"), LiveTvActions()) }
        press(Key.Menu)
        repeat(12) { if (runCatching { focused().assert(hasText("Move")) }.isFailure) press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        capture("07-live-tv-move")
    }

    /** The bar while the channel's sound passes through to a receiver: Decode This Channel. */
    @Test fun volumeLevelPassingThrough() {
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions(), sound = LiveSound(passingThrough = "ch105")) }
        press(Key.Menu)
        repeat(12) { if (runCatching { focused().assert(hasText("Volume Level")) }.isFailure) press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        capture("07-live-tv-volume-level-passthrough")
    }

    /**
     * A channel opening in the picture, with live video: the picture's background fills it to its
     * rounded corners, and after a moment a ring turns in it, as in the player; it goes once the
     * channel's picture comes.
     */
    @Test fun channelOpening() {
        val channel = LiveTvSamples.state.currentChannelId
        val live = OpeningVideo()
        compose.setContent {
            CompositionLocalProvider(LocalLiveVideo provides live) {
                SampleHost { LiveTvScreen(withLiveVideo, LiveTvActions()) }
            }
        }
        check(live.status.value == LiveStatus.Opening(channel)) { "The picture plays ${live.status.value}" }
        // Not at once, so a quick switch never flashes it.
        compose.onNodeWithContentDescription("Loading").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Loading").assertExists()
        capture("07-live-tv-opening")

        live.status.value = LiveStatus.Playing(channel)
        live.picture.value = channel
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Loading").assertDoesNotExist()
    }

    /** A channel that won't play loses its ring: it isn't opening any more. */
    @Test fun theRingGoesWhenAChannelFails() {
        val channel = LiveTvSamples.state.currentChannelId
        val live = OpeningVideo()
        compose.setContent {
            CompositionLocalProvider(LocalLiveVideo provides live) {
                SampleHost { LiveTvScreen(withLiveVideo, LiveTvActions()) }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Loading").assertExists()

        live.status.value = LiveStatus.Failed(channel, LiveFailure.SourceUnavailable)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Loading").assertDoesNotExist()
    }

    /** Board 07 with live video: each channel's picture plays in the slot, rather than its still. */
    private val withLiveVideo: LiveTvUiState
        get() = LiveTvSamples.state.let { s ->
            s.copy(channels = s.channels.mapValues { (_, list) -> list.map { it.copy(frame = it.frame.copy(channelId = it.id)) } })
        }

    /** Live video whose channels open and stay opening, with no picture, till a test says otherwise. */
    private class OpeningVideo : LiveVideo {
        override val status = MutableStateFlow<LiveStatus>(LiveStatus.Idle)
        override val picture = MutableStateFlow<String?>(null)
        override val aspectRatio: StateFlow<Float?> = MutableStateFlow(null)
        override val cues: StateFlow<List<SubtitleCue>> = MutableStateFlow(emptyList())

        override fun attach(view: SurfaceView, subtitles: SurfaceView?) = Unit

        override fun detach(view: SurfaceView) = Unit

        override fun show(channelId: String?, muted: Boolean) {
            status.value = channelId?.let { LiveStatus.Opening(it) } ?: LiveStatus.Idle
            picture.value = null
        }
    }
}
