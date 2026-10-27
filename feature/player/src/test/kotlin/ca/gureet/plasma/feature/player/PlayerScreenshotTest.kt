// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.testing.ScreenshotTest
import ca.gureet.plasma.player.VideoSlot
import org.junit.Test

/** The player against boards 09, 10 and 11 (docs/design/screens). */
class PlayerScreenshotTest : ScreenshotTest() {
    @Test fun overlay() {
        screen { PlayerScreen(PlayerSamples.state, PlayerActions(), initialOverlay = PlayerOverlay.Controls, autoHide = false) }
        capture("09-player")
    }

    @Test fun options() {
        screen { PlayerScreen(PlayerSamples.state, PlayerActions(), initialOverlay = PlayerOverlay.Options, autoHide = false) }
        capture("10-player-options")
    }

    /** Player Options › Audio and Subtitles (brief 4.10), which board 10 doesn't draw. */
    @Test fun audioAndSubtitles() {
        screen { PlayerScreen(PlayerSamples.state, PlayerActions(), initialOverlay = PlayerOverlay.Tracks, autoHide = false) }
        capture("10-player-options-audio-and-subtitles")
    }

    /** Player Options › Sleep Timer (brief 4.10), which board 10 doesn't draw. */
    @Test fun sleepTimer() {
        screen { PlayerScreen(PlayerSamples.state, PlayerActions(), initialOverlay = PlayerOverlay.Sleep, autoHide = false) }
        capture("10-player-options-sleep-timer")
    }

    /** Sleep Timer's last minute, over the picture. */
    @Test fun sleepNotice() {
        val channel = PlayerSamples.state.let { state -> state.channels.first { it.id == state.currentChannelId } }
        screen {
            Box(Modifier.fillMaxSize()) {
                VideoSlot(channel.frame, Modifier.fillMaxSize())
                SleepToast(onKeepWatching = {}, onStopNow = {})
            }
        }
        capture("10-player-options-sleep-notice")
    }

    /** What Sleep Timer leaves, before its words fade. */
    @Test fun asleep() {
        screen { AsleepScreen(onWake = {}, fade = false) }
        capture("10-player-options-asleep")
    }

    /** A movie in the player: the VOD variant of board 09 (brief 4.9). */
    @Test fun movie() {
        screen { VodPlayerScreen(PlayerSamples.movie, VodPlayerActions(), autoHide = false) }
        capture("09-player-movie")
    }

    /** An episode's last seconds, with Up Next (brief 4.9 and 5.9). */
    @Test fun upNext() {
        screen { VodPlayerScreen(PlayerSamples.episode.let { it.copy(upNext = it.upNext?.copy(countdown = 8)) }, VodPlayerActions(onNext = {})) }
        compose.mainClock.advanceTimeBy(PlasmaMotion.OVERLAY_HIDE_MS + 100)
        compose.waitForIdle()
        capture("09-player-up-next")
    }

    @Test fun zapBannerAndReminder() {
        screen(Sample.zapClock) { PlayerScreen(PlayerSamples.zapState, PlayerActions(), initialOverlay = PlayerOverlay.Zap, autoHide = false) }
        capture("11-zap-banner-and-reminder")
    }
}
