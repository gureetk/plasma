// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
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

    /** Down from the controls: the row under them (the owner's ask, after TiviMate's). */
    @Test fun more() {
        screen { PlayerScreen(PlayerSamples.state, PlayerActions(onSearch = {}, onGuide = {}), initialOverlay = PlayerOverlay.Controls, autoHide = false) }
        press(Key.DirectionDown)
        capture("09-player-more")
    }

    /** A channel without a guide: its name, and what the stream is, beside the controls. */
    @Test fun noGuide() {
        val state = PlayerSamples.state.let { s ->
            s.copy(
                channels = s.channels.map { if (it.id == s.currentChannelId) it.copy(programs = emptyList()) else it },
                playback = s.playback.copy(streamBadges = listOf("1920×1080", "50 FPS", "AC-3 5.1", "CC")),
            )
        }
        screen { PlayerScreen(state, PlayerActions(), initialOverlay = PlayerOverlay.Controls, autoHide = false) }
        capture("09-player-no-guide")
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
