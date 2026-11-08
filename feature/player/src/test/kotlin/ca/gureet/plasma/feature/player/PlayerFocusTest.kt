// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.design.KeyChoices
import ca.gureet.plasma.core.design.LocalKeyChoices
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

    /** The player with Settings' [keys] (Remote & Buttons, and Up/Down in Player), as the app provides them. */
    private fun show(keys: KeyChoices) = screen {
        CompositionLocalProvider(LocalKeyChoices provides keys) {
            PlayerScreen(
                PlayerSamples.state,
                PlayerActions(onTune = { tuned += it }, onChannelList = { channelList = true }, onBack = { left = true }),
                initialOverlay = PlayerOverlay.None,
                autoHide = false,
            )
        }
    }

    @Test fun channelUpCanGoToThePreviousNumber() {
        show(KeyChoices(channelUpToNext = false))
        press(Key.ChannelUp)
        check(tuned.last() == "ch104") { "Tuned $tuned" }
        // Up goes the way CH + does.
        press(Key.DirectionUp)
        check(tuned.last() == "ch103") { "Tuned $tuned" }
        press(Key.ChannelDown, times = 3)
        check(tuned.last() == "ch106") { "Tuned $tuned" }
    }

    @Test fun upAndDownCanShowTheChannelList() {
        show(KeyChoices(upDownChangesChannel = false))
        press(Key.DirectionUp)
        check(channelList && tuned.isEmpty()) { "Tuned $tuned" }
        channelList = false
        press(Key.DirectionDown)
        check(channelList && tuned.isEmpty()) { "Tuned $tuned" }
        // CH + still changes channel.
        press(Key.ChannelUp)
        check(tuned.last() == "ch106") { "Tuned $tuned" }
    }

    @Test fun holdingOkIsAnOkWhenLongPressOkDoesNothing() {
        show(KeyChoices(longPressOk = false))
        longPress(Key.DirectionCenter)
        // The overlay, on Play/Pause, as a press shows it, rather than Player Options.
        focused().assert(hasContentDescription("Pause"))
        press(Key.Back)
        // Menu still opens Player Options.
        press(Key.Menu)
        focused().assert(hasText("Watch from the Start"))
    }

    @Test fun holdingOkWithTheNumberPadOnOpensItAndWatchTunes() {
        // The owner's #105: for a remote without number keys.
        show(KeyChoices(holdOkNumberPad = true))
        longPress(Key.DirectionCenter)
        compose.onNodeWithText("Channel Number").assertExists()
        focused().assert(hasText("1"))
        capture("09-player-number-pad")

        // 1, 0 and 8 on the pad: the number shows with its channel, and nothing tunes by itself.
        press(Key.DirectionCenter)
        press(Key.DirectionRight, times = 9)
        press(Key.DirectionCenter)
        press(Key.DirectionLeft, times = 2)
        press(Key.DirectionCenter)
        compose.onNodeWithText("108").assertExists()
        compose.onNodeWithText(PlayerSamples.state.channels.first { it.number == "108" }.name).assertExists()
        compose.mainClock.advanceTimeBy(PlasmaMotion.NUMBER_ENTRY_TUNE_MS + 100)
        compose.waitForIdle()
        check(tuned.isEmpty()) { "Tuned $tuned" }

        press(Key.DirectionDown)
        focused().assert(hasText("Watch"))
        press(Key.DirectionCenter)
        check(tuned.last() == "ch108") { "Tuned $tuned" }
        compose.onNodeWithText("Channel Number").assertDoesNotExist()
    }

    @Test fun theNumberPadTakesTheRemotesDigitsAndBackClosesIt() {
        show(KeyChoices(holdOkNumberPad = true))
        longPress(Key.DirectionCenter)
        press(Key.Nine)
        press(Key.Nine)
        press(Key.Nine)
        compose.onNodeWithText("No channel 999").assertExists()
        compose.onNodeWithText("Watch").assertDoesNotExist()
        press(Key.Back)
        compose.onNodeWithText("Channel Number").assertDoesNotExist()
        check(tuned.isEmpty() && !left) { "Tuned $tuned, left $left" }
        // Menu still opens Player Options.
        press(Key.Menu)
        focused().assert(hasText("Watch from the Start"))
    }

    @Test fun aTypedNumberWaitsAsLongAsNumberEntryDelaySays() {
        show(KeyChoices(numberEntryDelayMs = 3_000))
        press(Key.One)
        press(Key.Zero)
        press(Key.Eight)
        compose.mainClock.advanceTimeBy(PlasmaMotion.NUMBER_ENTRY_TUNE_MS + 100)
        compose.waitForIdle()
        check(tuned.isEmpty()) { "Tuned $tuned" }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        check(tuned.last() == "ch108") { "Tuned $tuned" }
    }

    /** Show Stream Stats (Settings › Playback › Advanced, 5.4.9): the stream's numbers, over the picture. */
    @Test fun streamStatsShowOverThePicture() {
        val stats = StreamStats(
            engine = "VLC 3.6.2",
            decoder = "mediacodec",
            hardware = true,
            format = "1920×1080 · 50 fps · H.264 · AC-3 5.1 passthrough",
            bitrate = "8.2 Mb/s",
            buffer = "2.1 s",
            droppedFrames = 12,
            source = "Home Portal",
            reconnects = 1,
        )
        screen {
            val state = PlayerSamples.state
            PlayerScreen(state.copy(playback = state.playback.copy(stats = stats)), PlayerActions(), initialOverlay = PlayerOverlay.None, autoHide = false)
        }
        compose.onNodeWithText("VLC 3.6.2 · mediacodec, hardware").assertExists()
        compose.onNodeWithText("1920×1080 · 50 fps · H.264 · AC-3 5.1 passthrough").assertExists()
        compose.onNodeWithText("8.2 Mb/s · buffer 2.1 s").assertExists()
        compose.onNodeWithText("12 dropped frames").assertExists()
        compose.onNodeWithText("Home Portal · reconnected 1 time").assertExists()
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

    /**
     * The error card's Switch Source and Report (#114): the channel's sources and Report a
     * Problem's sheet, each of which Back closes, back to the card's button that opened it.
     */
    @Test fun theErrorCardsSwitchSourceAndReportGoBackToTheCard() {
        val switched = mutableListOf<String>()
        val sources = listOf(SourceChoice("home:105", "Home Portal", playing = true), SourceChoice("backup:9105", "Backup Line", playing = false))
        val state = PlayerSamples.state.let { it.copy(status = PlayerStatus.Failed(PlayerProblem.SourceUnavailable), playback = it.playback.copy(sources = sources)) }
        val actions = PlayerActions(onSwitchSource = { switched += it }, onBack = { left = true }, reportProblem = ReportSheetStandIn)
        screen { PlayerScreen(state, actions, initialOverlay = PlayerOverlay.None, autoHide = false) }
        focused().assert(hasText("Try Again"))

        press(Key.DirectionRight)
        focused().assert(hasText("Switch Source"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Home Portal"))
        back()
        focused().assert(hasText("Switch Source"))
        // Not Player Options, as from its own Switch Source.
        compose.onNodeWithText("Picture Size").assertDoesNotExist()

        press(Key.DirectionRight, times = 2)
        focused().assert(hasText("Report"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("Report a Problem").assertExists()
        compose.onNodeWithText("Try Again").assertDoesNotExist()
        back()
        compose.onNodeWithText("Report a Problem").assertDoesNotExist()
        focused().assert(hasText("Report"))
        check(!left) { "Back left the player" }

        // A source picked from the card plays.
        press(Key.DirectionLeft, times = 2)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        focused().assert(hasText("Backup Line"))
        press(Key.DirectionCenter)
        check(switched == listOf("backup:9105")) { "Switched to $switched" }
    }

    /** Switch Player on the error card plays the channel with the next player; one source has no Switch Source. */
    @Test fun theErrorCardsSwitchPlayerTriesTheNextPlayer() {
        var switched = 0
        val state = PlayerSamples.state.copy(status = PlayerStatus.Failed(PlayerProblem.CantPlay))
        screen { PlayerScreen(state, PlayerActions(onSwitchPlayer = { switched++ }), initialOverlay = PlayerOverlay.None, autoHide = false) }
        compose.onNodeWithText("Switch Source").assertDoesNotExist()
        // Without Report a Problem's sheet from the app, as on sample data, no Report.
        compose.onNodeWithText("Report").assertDoesNotExist()
        press(Key.DirectionRight)
        focused().assert(hasText("Switch Player"))
        press(Key.DirectionCenter)
        check(switched == 1) { "Switched $switched times" }
        focused().assert(hasText("Switch Player"))
    }

    /**
     * No Switch Player when the provider turned the stream down, which no player gets past, or
     * with no other player to switch to: the card has Try Again and Close.
     */
    @Test fun theErrorCardLeavesOutSwitchPlayerWhereItCantHelp() {
        var state by mutableStateOf(PlayerSamples.state.copy(status = PlayerStatus.Failed(PlayerProblem.ConnectionLimit)))
        // With Switch Player from the app, the screen shows the engines the app reports.
        screen { PlayerScreen(state, PlayerActions(onSwitchPlayer = {}), initialOverlay = PlayerOverlay.None, autoHide = false) }
        compose.onNodeWithText("Switch Player").assertDoesNotExist()
        press(Key.DirectionRight)
        focused().assert(hasText("Close"))

        state = state.copy(status = PlayerStatus.Failed(PlayerProblem.CantPlay))
        compose.waitForIdle()
        compose.onNodeWithText("Switch Player").assertExists()

        // Nor for a channel its provider sends scrambled, which no player can show (brief 6.9).
        state = state.copy(status = PlayerStatus.Failed(PlayerProblem.Encrypted))
        compose.waitForIdle()
        check(compose.onAllNodes(hasText("This channel is encrypted", substring = true)).fetchSemanticsNodes().isNotEmpty()) { "Not said" }
        compose.onNodeWithText("Switch Player").assertDoesNotExist()

        state = state.copy(status = PlayerStatus.Failed(PlayerProblem.CantPlay), playback = state.playback.copy(nextEngine = ""))
        compose.waitForIdle()
        compose.onNodeWithText("Switch Player").assertDoesNotExist()
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
    @Test fun audioDelayMovesTheSoundAsItGoesAndOkKeepsItWithTheChannel() {
        val moved = mutableListOf<Pair<String, Int>>()
        val kept = mutableListOf<Pair<String, Int>>()
        screen {
            PlayerScreen(
                PlayerSamples.state,
                PlayerActions(onAudioDelay = { id, ms -> moved += id to ms }, onKeepAudioDelay = { id, ms -> kept += id to ms }),
                initialOverlay = PlayerOverlay.None,
                autoHide = false,
            )
        }
        val channel = PlayerSamples.state.currentChannelId
        press(Key.Menu)
        downTo("Audio Delay")
        focused().assert(hasText("0 ms"))

        // The bar, over the picture: 10 ms a step, and the sound follows each one (#116).
        press(Key.DirectionCenter)
        press(Key.DirectionRight, times = 3)
        press(Key.DirectionLeft)
        compose.onNodeWithText("+20 ms").assertExists()
        check(moved == listOf(channel to 10, channel to 20, channel to 30, channel to 20)) { "Moved $moved" }

        // OK keeps it with the channel, and the picture shows alone.
        press(Key.DirectionCenter)
        check(kept == listOf(channel to 20)) { "Kept $kept" }
        compose.onNodeWithText("+20 ms").assertDoesNotExist()
        compose.onNodeWithText("Switch Player").assertDoesNotExist()
    }

    @Test fun backFromAudioDelayPutsTheDelayBackAndReturnsToPlayerOptions() {
        val moved = mutableListOf<Pair<String, Int>>()
        val kept = mutableListOf<Pair<String, Int>>()
        val state = PlayerSamples.state.copy(playback = PlayerSamples.state.playback.copy(channelDelayMs = -40))
        screen {
            PlayerScreen(
                state,
                PlayerActions(onAudioDelay = { id, ms -> moved += id to ms }, onKeepAudioDelay = { id, ms -> kept += id to ms }),
                initialOverlay = PlayerOverlay.None,
                autoHide = false,
            )
        }
        val channel = state.currentChannelId
        press(Key.Menu)
        downTo("Audio Delay")
        focused().assert(hasText("−40 ms"))
        press(Key.DirectionCenter)
        press(Key.DirectionLeft, times = 2)
        compose.onNodeWithText("−60 ms").assertExists()

        back()
        check(moved == listOf(channel to -50, channel to -60, channel to -40)) { "Moved $moved" }
        check(kept.isEmpty()) { "Kept $kept" }
        compose.onNodeWithText("Switch Player").assertExists()
        downTo("Audio Delay")
        focused().assert(hasText("−40 ms"))

        // A zap while it's up puts it back too.
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.ChannelUp)
        check(moved.takeLast(2) == listOf(channel to -30, channel to -40)) { "Moved $moved" }
        check(kept.isEmpty()) { "Kept $kept" }
    }

    @Test fun withTheAndroidPlayerAudioDelaySaysSoAndOffersSwitchPlayer() {
        var switched = 0
        val state = PlayerSamples.state.copy(playback = PlayerSamples.state.playback.copy(engine = "Android player", nextEngine = "VLC", delayWorks = false))
        screen { PlayerScreen(state, PlayerActions(onSwitchPlayer = { switched++ }), initialOverlay = PlayerOverlay.None, autoHide = false) }
        press(Key.Menu)
        downTo("Audio Delay")
        focused().assert(hasText("Not with the Android player"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("The Android player can’t delay the sound. VLC and mpv can, and keep this channel’s delay.").assertExists()
        focused().assert(hasText("Switch Player"))
        press(Key.DirectionCenter)
        check(switched == 1) { "Switched $switched" }
    }
}
