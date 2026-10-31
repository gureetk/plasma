// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.component.SourceChoice
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.testing.ScreenshotTest
import java.time.Duration
import org.junit.Test

/** The remote in the full-screen player (brief 3.6, 4.9–4.11). */
class PlayerFocusTest : ScreenshotTest() {
    private val tuned = mutableListOf<String>()
    private var channelList = false
    private var left = false

    private fun show() = screen {
        PlayerScreen(
            PlayerSamples.state,
            PlayerActions(onTune = { tuned += it }, onChannelList = { channelList = true }, onBack = { left = true }),
            initialOverlay = PlayerOverlay.None,
            autoHide = false,
        )
    }

    @Test fun upAndDownChangeChannelAndLeftOpensTheList() {
        show()
        press(Key.DirectionUp)
        check(tuned.last() == "ch106") { "Tuned $tuned" }
        press(Key.DirectionDown, times = 2)
        check(tuned.last() == "ch104") { "Tuned $tuned" }
        press(Key.DirectionLeft)
        check(channelList) { "Left didn't open the channel list" }
    }

    @Test fun okShowsTheOverlayOnPlayPause() {
        show()
        press(Key.DirectionCenter)
        focused().assert(hasContentDescription("Pause"))
        press(Key.DirectionCenter)
        focused().assert(hasContentDescription("Play"))
        // Up reaches the timeline, and Down comes back.
        press(Key.DirectionUp)
        focused().assert(hasContentDescription("Timeline"))
        press(Key.DirectionDown)
        focused().assert(hasContentDescription("Play"))
    }

    @Test fun downFromTheControlsOpensTheRowUnderThemAndBackClosesIt() {
        var searched = false
        screen {
            PlayerScreen(
                PlayerSamples.state,
                PlayerActions(onChannelList = { channelList = true }, onSearch = { searched = true }),
                initialOverlay = PlayerOverlay.None,
                autoHide = false,
            )
        }
        press(Key.DirectionCenter)
        focused().assert(hasContentDescription("Pause"))
        press(Key.DirectionDown)
        // Arena Sports 1 is a favorite.
        focused().assert(hasText("Remove from Favorites"))
        press(Key.DirectionRight)
        focused().assert(hasText("Channel List"))
        press(Key.DirectionRight)
        focused().assert(hasText("Search"))
        press(Key.DirectionCenter)
        check(searched) { "Search didn't open" }

        // Back closes the row, focus back on the controls, then the overlay.
        back()
        focused().assert(hasContentDescription("Pause"))
        back()
        compose.onNode(hasContentDescription("Pause")).assertDoesNotExist()
        check(!left) { "Left the player" }
    }

    @Test fun theRowUnderTheControlsTogglesTheFavoriteAndUpClosesIt() {
        val favorites = mutableListOf<Pair<String, Boolean>>()
        screen {
            PlayerScreen(PlayerSamples.state, PlayerActions(onFavorite = { id, on -> favorites += id to on }), initialOverlay = PlayerOverlay.None, autoHide = false)
        }
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(favorites == listOf(Sample.arena1.id to false)) { "Favorites $favorites" }
        focused().assert(hasText("Add to Favorites"))
        // The Guide and Search are left out without somewhere to go.
        compose.onNode(hasText("Search")).assertDoesNotExist()
        compose.onNode(hasText("Guide")).assertDoesNotExist()

        // Up goes back to the controls; Down opens the row again.
        press(Key.DirectionUp)
        compose.onNode(hasText("Add to Favorites") and isFocused()).assertDoesNotExist()
        press(Key.DirectionDown)
        focused().assert(hasText("Add to Favorites"))
    }

    @Test fun rightShowsTheOverlayOnTheTimeline() {
        show()
        press(Key.DirectionRight)
        focused().assert(hasContentDescription("Timeline"))
    }

    @Test fun menuOpensPlayerOptions() {
        show()
        press(Key.Menu)
        focused().assert(hasText("Watch from the Start"))
    }

    @Test fun holdingOkOpensPlayerOptions() {
        show()
        longPress(Key.DirectionCenter)
        focused().assert(hasText("Watch from the Start"))
    }

    @Test fun typingANumberTunesIt() {
        show()
        press(Key.One)
        press(Key.Zero)
        press(Key.Eight)
        compose.onNodeWithText("108_").assertExists()
        compose.mainClock.advanceTimeBy(PlasmaMotion.NUMBER_ENTRY_TUNE_MS + 100)
        compose.waitForIdle()
        check(tuned.last() == "ch108") { "Tuned $tuned" }
    }

    @Test fun aReminderTakesFocusAndSwitchNowTunesItsChannel() {
        var done = 0
        screen(Sample.zapClock) {
            PlayerScreen(
                PlayerSamples.zapState,
                PlayerActions(onTune = { tuned += it }, onReminderDone = { done++ }),
                initialOverlay = PlayerOverlay.Zap,
                autoHide = false,
            )
        }
        focused().assert(hasText("Switch Now"))
        // Channel keys still reach the player underneath (brief 4.11).
        press(Key.ChannelUp)
        check(tuned.size == 1) { "Tuned $tuned" }
        focused().assert(hasText("Switch Now"))

        press(Key.DirectionCenter)
        check(tuned.last() == Sample.arena1.id) { "Tuned $tuned" }
        check(done == 1) { "Done $done times" }
        compose.onNodeWithText("Switch Now").assertDoesNotExist()
    }

    @Test fun backDismissesAReminderBeforeLeaving() {
        var done = 0
        screen(Sample.zapClock) {
            PlayerScreen(PlayerSamples.zapState, PlayerActions(onBack = { left = true }, onReminderDone = { done++ }), initialOverlay = PlayerOverlay.None, autoHide = false)
        }
        focused().assert(hasText("Switch Now"))
        back()
        check(!left && done == 1) { "Left $left, done $done times" }
        compose.onNodeWithText("Switch Now").assertDoesNotExist()
        back()
        check(left) { "Back didn't leave the player" }
    }

    @Test fun theReminderKeepsLeftAndRightAndOkTunesATypedNumber() {
        screen(Sample.zapClock) {
            PlayerScreen(
                PlayerSamples.zapState,
                PlayerActions(onTune = { tuned += it }, onChannelList = { channelList = true }),
                initialOverlay = PlayerOverlay.None,
                autoHide = false,
            )
        }
        focused().assert(hasText("Switch Now"))
        press(Key.DirectionRight)
        focused().assert(hasText("Dismiss"))
        press(Key.DirectionLeft)
        focused().assert(hasText("Switch Now"))
        check(!channelList) { "Left opened the channel list" }

        // A number typed under the reminder tunes on OK, and the reminder stays.
        press(Key.One)
        press(Key.Zero)
        press(Key.Eight)
        press(Key.DirectionCenter)
        check(tuned.last() == "ch108") { "Tuned $tuned" }
        compose.onNodeWithText("Switch Now").assertExists()
    }

    @Test fun aReminderComingDueForAChannelOutsideTheListOpensIt() {
        val opened = mutableListOf<String>()
        var state by mutableStateOf(PlayerSamples.state)
        screen { PlayerScreen(state, PlayerActions(onTune = { tuned += it }, onOpenChannel = { opened += it }), initialOverlay = PlayerOverlay.None, autoHide = false) }

        state = state.copy(reminder = PlayerSamples.zapState.reminder!!.copy(channelId = "elsewhere"))
        compose.waitForIdle()
        focused().assert(hasText("Switch Now"))
        press(Key.DirectionCenter)

        check(opened == listOf("elsewhere")) { "Opened $opened" }
        check(tuned.isEmpty()) { "Tuned $tuned" }
    }

    @Test fun tuningInLateOffersWatchFromTheStartOnce() {
        val started = mutableListOf<Pair<String, java.time.Instant>>()
        screen {
            PlayerScreen(
                PlayerSamples.state,
                PlayerActions(onTune = { tuned += it }, onStartOver = { id, start -> started += id to start }),
                initialOverlay = PlayerOverlay.None,
                autoHide = false,
            )
        }
        // Grand Slam Tennis on Arena Sports 2 started at 19:00; it's 20:41.
        press(Key.DirectionUp)
        compose.onNodeWithText("Started 1 h 41 min ago · OK to watch from the start").assertExists()
        // OK opens the overlay on Start Over, and a second OK starts it.
        press(Key.DirectionCenter)
        focused().assert(hasContentDescription("Start over"))
        press(Key.DirectionCenter)
        check(started == listOf(Sample.arena2.id to Sample.schedule.getValue(Sample.arena2).first().start.toInstant())) { "Started $started" }

        // Back on the same program later, it isn't offered again.
        back()
        press(Key.DirectionDown)
        press(Key.DirectionUp)
        compose.onNodeWithText("Started 1 h 41 min ago · OK to watch from the start").assertDoesNotExist()
    }

    @Test fun greenStartsOverOnAChannelWithAnArchiveAndGoLiveComesBack() {
        val started = mutableListOf<String>()
        var live = 0
        screen {
            PlayerScreen(
                PlayerSamples.state,
                PlayerActions(onStartOver = { id, _ -> started += id }, onGoLive = { live++ }),
                initialOverlay = PlayerOverlay.None,
                autoHide = false,
            )
        }
        press(Key.ProgramGreen)
        check(started == listOf(Sample.arena1.id)) { "Started $started" }

        press(Key.DirectionCenter)
        press(Key.DirectionRight, times = 2)
        focused().assert(hasText("Go Live"))
        press(Key.DirectionCenter)
        check(live == 1) { "Went live $live times" }

        // Arena Sports News keeps no archive: nothing to start over.
        back()
        press(Key.DirectionDown, times = 2)
        press(Key.ProgramGreen)
        check(started == listOf(Sample.arena1.id)) { "Started $started" }
    }

    @Test fun switchSourceListsTheChannelsSources() {
        val switched = mutableListOf<String>()
        val sources = listOf(SourceChoice("home:105", "Home Portal", playing = true), SourceChoice("backup:9105", "Backup Line", playing = false))
        val state = PlayerSamples.state.copy(playback = PlayerSamples.state.playback.copy(sources = sources))
        screen { PlayerScreen(state, PlayerActions(onSwitchSource = { switched += it }), initialOverlay = PlayerOverlay.None, autoHide = false) }
        fun downTo(label: String) {
            for (row in 1..12) {
                if (compose.onAllNodes(isFocused() and hasText(label)).fetchSemanticsNodes().isNotEmpty()) break
                press(Key.DirectionDown)
            }
            focused().assert(hasText(label))
        }
        press(Key.Menu)
        downTo("Switch Source")

        // The source playing now is checked and has focus; Back goes back to Player Options.
        press(Key.DirectionCenter)
        focused().assert(hasText("Home Portal"))
        back()
        compose.onNodeWithText("Switch Player").assertExists()

        downTo("Switch Source")
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        focused().assert(hasText("Backup Line"))
        press(Key.DirectionCenter)
        check(switched == listOf("backup:9105")) { "Switched to $switched" }
        compose.onNodeWithText("Switch Player").assertDoesNotExist()
    }

    @Test fun aRememberedSourceComesFirstWithForgetWhatWorksAtTheEnd() {
        var forgot = false
        val remembered = Sample.now.minus(java.time.Duration.ofDays(3))
        val sources = listOf(
            SourceChoice("backup:9105", "Backup Line", playing = true, remembered = remembered),
            SourceChoice("home:105", "Home Portal", playing = false),
        )
        val state = PlayerSamples.state.copy(playback = PlayerSamples.state.playback.copy(sources = sources))
        screen { PlayerScreen(state, PlayerActions(onForgetSource = { forgot = true }), initialOverlay = PlayerOverlay.None, autoHide = false) }
        press(Key.Menu)
        for (row in 1..12) {
            if (compose.onAllNodes(isFocused() and hasText("Switch Source")).fetchSemanticsNodes().isNotEmpty()) break
            press(Key.DirectionDown)
        }
        press(Key.DirectionCenter)

        compose.onNodeWithText("Works best").assertExists()
        compose.onNodeWithText("Remembered 3 days ago").assertExists()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        focused().assert(hasText("Forget What Works"))
        press(Key.DirectionCenter)
        check(forgot) { "Forget What Works did nothing" }
    }

    /** Down through Player Options until [label] has focus. */
    private fun downTo(label: String) {
        for (row in 1..12) {
            if (compose.onAllNodes(isFocused() and hasText(label)).fetchSemanticsNodes().isNotEmpty()) break
            press(Key.DirectionDown)
        }
        focused().assert(hasText(label))
    }

    @Test fun audioAndSubtitlesListsTheTracksAndPicksOne() {
        val audio = mutableListOf<String>()
        val subtitles = mutableListOf<String?>()
        screen {
            PlayerScreen(
                PlayerSamples.state,
                PlayerActions(onAudio = { audio += it }, onSubtitles = { subtitles += it }),
                initialOverlay = PlayerOverlay.None,
                autoHide = false,
            )
        }
        press(Key.Menu)
        downTo("Audio and Subtitles")
        focused().assert(hasText("English 5.1 · Off"))

        // The sound playing has focus; Back goes back to Player Options.
        press(Key.DirectionCenter)
        focused().assert(hasText("English 5.1") and hasText("Dolby Digital"))
        compose.onNodeWithText("Audio Description").assertExists()
        back()
        compose.onNodeWithText("Switch Player").assertExists()

        downTo("Audio and Subtitles")
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 2)
        focused().assert(hasText("Spanish Stereo"))
        press(Key.DirectionCenter)
        check(audio == listOf("3")) { "Picked $audio" }
        compose.onNodeWithText("Switch Player").assertDoesNotExist()

        press(Key.Menu)
        downTo("Audio and Subtitles")
        press(Key.DirectionCenter)
        downTo("German")
        focused().assert(hasText("Teletext"))
        press(Key.DirectionCenter)
        check(subtitles == listOf<String?>("6")) { "Picked $subtitles" }
    }

    @Test fun theOverlaysSoundAndSubtitlePillsOpenAudioAndSubtitles() {
        show()
        press(Key.DirectionCenter)
        compose.onNodeWithText("English 5.1").assertExists()
        compose.onNodeWithText("Subtitles off").assertExists()
        for (step in 1..8) {
            if (compose.onAllNodes(isFocused() and hasText("Subtitles off")).fetchSemanticsNodes().isNotEmpty()) break
            press(Key.DirectionRight)
        }
        focused().assert(hasText("Subtitles off"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("Audio and Subtitles").assertExists()
    }

    @Test fun pictureSizeOffersItsFiveSizes() {
        val sizes = mutableListOf<ca.gureet.plasma.player.PictureSize>()
        screen { PlayerScreen(PlayerSamples.state, PlayerActions(onPictureSize = { sizes += it }), initialOverlay = PlayerOverlay.None, autoHide = false) }
        press(Key.Menu)
        downTo("Picture Size")
        focused().assert(hasText("Fit · 16:9"))
        press(Key.DirectionCenter)

        focused().assert(hasText("Fit"))
        listOf("Fill", "Zoom", "16:9", "4:3").forEach { compose.onNodeWithText(it).assertExists() }
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionCenter)
        check(sizes == listOf(ca.gureet.plasma.player.PictureSize.Zoom)) { "Picked $sizes" }
    }

    @Test fun theAudioKeyMovesToTheNextSoundAndTheSubtitlesKeyTurnsThemOn() {
        var picked = 0
        var subtitles = 0
        screen {
            PlayerScreen(
                PlayerSamples.state,
                PlayerActions(onNextAudio = { picked++ }, onNextSubtitles = { subtitles++ }),
                initialOverlay = PlayerOverlay.None,
                autoHide = false,
            )
        }

        press(Key.MediaAudioTrack)
        press(Key.Captions)

        check(picked == 1 && subtitles == 1) { "Audio $picked, subtitles $subtitles" }
    }

    @Test fun backHidesTheOverlayThenLeaves() {
        show()
        press(Key.DirectionCenter)
        back()
        check(!left) { "Left with the overlay up" }
        compose.onNode(hasContentDescription("Pause")).assertDoesNotExist()
        back()
        check(left) { "Back didn't leave the player" }
    }

    @Test fun sleepTimerOffersItsTimesAndTheEndOfTheProgram() {
        val set = mutableListOf<Pair<SleepChoice, Duration?>>()
        screen {
            PlayerScreen(
                PlayerSamples.state,
                PlayerActions(onSleepTimer = { choice, after -> set += choice to after }),
                initialOverlay = PlayerOverlay.None,
                autoHide = false,
            )
        }
        press(Key.Menu)
        downTo("Sleep Timer")
        focused().assert(hasText("Off"))
        press(Key.DirectionCenter)

        // Off is ticked, with focus; End of Program says what's left of the match, 2:14 behind live.
        focused().assert(hasText("Off"))
        listOf("30 Minutes", "60 Minutes", "90 Minutes", "120 Minutes", "1 h 21 min").forEach { compose.onNodeWithText(it).assertExists() }
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        check(set == listOf(SleepChoice.Minutes30 to Duration.ofMinutes(30))) { "Set $set" }

        press(Key.Menu)
        downTo("Sleep Timer")
        press(Key.DirectionCenter)
        downTo("End of Program")
        press(Key.DirectionCenter)
        check(set.last() == SleepChoice.EndOfProgram to Duration.ofSeconds(4_874)) { "Set $set" }
    }

    @Test fun aSleepTimerSaysWhatsLeftAndBackReturnsToPlayerOptions() {
        show()
        press(Key.Menu)
        downTo("Sleep Timer")
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionCenter)

        // On sample data the timer is kept here: the row says what it has left.
        press(Key.Menu)
        downTo("Sleep Timer")
        focused().assert(hasText("1 h left"))
        press(Key.DirectionCenter)
        focused().assert(hasText("60 Minutes"))
        back()
        compose.onNodeWithText("Switch Player").assertExists()
        compose.onNodeWithText("1 h left").assertExists()
    }
}
