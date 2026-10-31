// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.livetv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import ca.gureet.plasma.core.design.LARGE_TEXT_SCALE
import ca.gureet.plasma.core.design.PlasmaTheme
import ca.gureet.plasma.core.testing.ScreenshotTest
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
}
