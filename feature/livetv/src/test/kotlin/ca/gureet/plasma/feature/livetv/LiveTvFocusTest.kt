// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.livetv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.testing.ScreenshotTest
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

/** D-pad, number keys and sheets on Live TV (brief 4.7, 4.11 and 5.2). */
class LiveTvFocusTest : ScreenshotTest() {
    private var watched: String? = null

    private fun show() = screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions(onWatch = { watched = it })) }

    /** Channel rows read as "105, Arena Sports 1, Harbour City v Redmoor". */
    private fun focusedChannel(number: Int, name: String) = focused().assert(hasContentDescription("$number, $name", substring = true))

    private fun waitForTune() {
        compose.mainClock.advanceTimeBy(PlasmaMotion.NUMBER_ENTRY_TUNE_MS + 100)
        compose.waitForIdle()
    }

    @Test fun focusStartsOnTheChannelPlayingAndMovesBetweenColumns() {
        show()
        focusedChannel(105, "Arena Sports 1")
        press(Key.DirectionDown)
        focusedChannel(106, "Arena Sports 2")

        // Left goes to the groups, on the group shown.
        press(Key.DirectionLeft)
        focused().assert(hasText("Sports"))
        press(Key.DirectionRight)
        focusedChannel(106, "Arena Sports 2")

        // Right goes to what's coming up on the channel in the preview.
        press(Key.DirectionUp)
        compose.mainClock.advanceTimeBy(PlasmaMotion.PREVIEW_DWELL_MS + 100)
        press(Key.DirectionRight)
        focused().assert(hasText("Match of the Week Extra"))
        press(Key.DirectionLeft)
        focusedChannel(105, "Arena Sports 1")
    }

    @Test fun typingANumberTunesItAfterAPause() {
        show()
        press(Key.One)
        press(Key.Zero)
        press(Key.Eight)
        compose.onNodeWithText("108_").assertExists()
        compose.onNodeWithText("Courtside+").assertExists()
        check(watched == null) { "Tuned $watched before the pause" }

        waitForTune()
        check(watched == "ch108") { "Tuned $watched" }
        compose.onNodeWithText("108_").assertDoesNotExist()
    }

    @Test fun okTunesTheNumberAtOnceAndBackDropsIt() {
        show()
        press(Key.One)
        press(Key.Zero)
        press(Key.Seven)
        press(Key.DirectionCenter)
        check(watched == "ch107") { "Tuned $watched" }

        watched = null
        press(Key.One)
        compose.onNodeWithText("1_").assertExists()
        press(Key.Back)
        compose.onNodeWithText("1_").assertDoesNotExist()
        waitForTune()
        check(watched == null) { "Tuned $watched after Back" }
        // OK and Back went to the number, not to the list.
        focusedChannel(105, "Arena Sports 1")
    }

    @Test fun aNumberNoChannelHasSaysSo() {
        show()
        press(Key.Nine, times = 3)
        compose.onNodeWithText("999_").assertExists()
        waitForTune()
        compose.onNodeWithText("No channel 999").assertExists()
        check(watched == null) { "Tuned $watched" }
        waitForTune()
        compose.onNodeWithText("No channel 999").assertDoesNotExist()
    }

    @Test fun inFavoritesANumberIsTheListsOwn() {
        show()
        press(Key.DirectionLeft)
        press(Key.DirectionUp, times = 2)
        focused().assert(hasText("Favorites"))
        // The list shows Favorites once focus rests on it.
        compose.mainClock.advanceTimeBy(400)
        press(Key.Two)
        compose.onNodeWithText("Favorites 2 · Velocity Motor").assertExists()
        press(Key.DirectionCenter)
        check(watched == "ch107") { "Tuned $watched" }

        // A number Favorites doesn't have falls back to the full list.
        press(Key.One)
        press(Key.Zero)
        press(Key.Eight)
        compose.onNodeWithText("Courtside+").assertExists()
    }

    @Test fun menuOpensChannelOptions() {
        show()
        press(Key.Menu)
        focused().assert(hasText("Remove from Favorites"))
        compose.onNodeWithText("105 · Harbour City v Redmoor").assertExists()
        press(Key.DirectionCenter)

        // The sheet closes on the channel, and the favorite is gone.
        focusedChannel(105, "Arena Sports 1")
        press(Key.Menu)
        focused().assert(hasText("Add to Favorites"))
        press(Key.Back)
        focusedChannel(105, "Arena Sports 1")

        // Red toggles it back without a sheet.
        press(Key.ProgramRed)
        press(Key.Menu)
        focused().assert(hasText("Remove from Favorites"))
    }

    @Test fun hiddenChannelsLeaveTheListAndTheirNumberOffersUnhide() {
        show()
        press(Key.Menu)
        press(Key.DirectionDown)
        focused().assert(hasText("Hide Channel"))
        press(Key.DirectionCenter)

        // Focus moves on to the next channel.
        focusedChannel(106, "Arena Sports 2")
        compose.onNode(hasContentDescription("105, Arena Sports 1", substring = true)).assertDoesNotExist()

        press(Key.One)
        press(Key.Zero)
        press(Key.Five)
        press(Key.DirectionCenter)
        check(watched == null) { "Tuned hidden $watched" }
        compose.onNodeWithText("Channel 105 is hidden").assertExists()
        focused().assert(hasText("Unhide"))
        press(Key.DirectionCenter)
        check(watched == "ch105") { "Tuned $watched" }
        compose.onNode(hasContentDescription("105, Arena Sports 1", substring = true)).assertExists()
    }

    @Test fun okOnAProgramComingUpSetsAndCancelsAReminder() {
        val reminded = mutableListOf<Pair<String, Boolean>>()
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions(onRemind = { channelId, _, on -> reminded += channelId to on })) }
        press(Key.DirectionRight)
        focused().assert(hasText("Match of the Week Extra"))
        focused().assert(hasContentDescription("Reminder set"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Cancel Reminder"))
        press(Key.DirectionCenter)

        // Focus comes back to the program, now without its bell.
        focused().assert(hasText("Match of the Week Extra"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Remind Me"))
        press(Key.DirectionDown)
        focused().assert(hasText("Record"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Match of the Week Extra"))
        focused().assert(hasContentDescription("Recording scheduled"))
        // The app keeps the reminder cancelled; recordings come with M6.
        check(reminded == listOf("ch105" to false)) { "Reminded $reminded" }
    }

    @Test fun volumeLevelSetsTheChannelsOwnLevel() {
        val moved = mutableListOf<Pair<String, Int>>()
        var kept: Pair<String, Int>? = null
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions(onLevel = { id, db -> moved += id to db }, onKeepLevel = { id, db -> kept = id to db })) }
        press(Key.Menu)
        pressDownTo("Volume Level")
        focused().assert(hasText("0 dB"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("Volume level").assertExists()

        // A step at a time, as the channel plays: OK keeps it.
        press(Key.DirectionRight, times = 3)
        compose.onNodeWithText("+3 dB").assertExists()
        press(Key.DirectionCenter)
        assertEquals("ch105" to 3, kept)
        assertEquals(listOf(1, 2, 3), moved.map { it.second })
        focusedChannel(105, "Arena Sports 1")

        // Back puts the level it had back.
        press(Key.Menu)
        pressDownTo("Volume Level")
        focused().assert(hasText("+3 dB"))
        press(Key.DirectionCenter)
        press(Key.DirectionLeft, times = 2)
        compose.onNodeWithText("+1 dB").assertExists()
        press(Key.Back)
        assertEquals(3, moved.last().second)
        compose.onNodeWithText("Volume level").assertDoesNotExist()
        focusedChannel(105, "Arena Sports 1")
    }

    @Test fun soundPassingThroughOffersDecodeThisChannel() {
        var decoded: String? = null
        var sound by mutableStateOf(LiveSound(passingThrough = "ch105"))
        val moved = mutableListOf<Int>()
        val actions = LiveTvActions(
            onDecode = {
                decoded = it
                // The app decodes it, and the sound no longer passes through.
                sound = LiveSound()
            },
            onLevel = { _, db -> moved += db },
        )
        screen { LiveTvScreen(LiveTvSamples.state, actions, sound = sound) }
        press(Key.Menu)
        pressDownTo("Volume Level")
        press(Key.DirectionCenter)

        compose.onNodeWithText("Your receiver sets the volume while audio passes through.", substring = true).assertExists()
        focused().assert(hasText("Decode This Channel"))
        // The arrows don't take focus off the bar.
        press(Key.DirectionLeft)
        press(Key.DirectionUp)
        focused().assert(hasText("Decode This Channel"))
        press(Key.DirectionCenter)
        assertEquals("ch105", decoded)

        // Decoded, the bar has its steps, and focus.
        compose.onNodeWithText("0 dB").assertExists()
        press(Key.DirectionRight)
        assertEquals(listOf(1), moved)
        press(Key.Back)
        focusedChannel(105, "Arena Sports 1")
    }

    /** The sample, with Arena Sports 1 on two sources and its guide id. */
    private val merged: LiveTvUiState
        get() = LiveTvSamples.state.let { state ->
            state.copy(
                channels = state.channels.mapValues { (_, list) ->
                    list.map {
                        if (it.id != "ch105") it else it.copy(sources = listOf(LiveSource("home:105", "Home Portal"), LiveSource("backup:105", "Backup Line")), guideId = "arena1.example")
                    }
                },
            )
        }

    @Test fun switchSourceListsTheChannelsSourcesAndPlaysTheOneChosen() {
        val switched = mutableListOf<Pair<String, String>>()
        screen { LiveTvScreen(merged, LiveTvActions(onSwitchSource = { id, source -> switched += id to source }), playing = LivePlaying("ch105", "backup:105")) }
        press(Key.Menu)
        pressDownTo("Switch Source")
        // The source it plays from now.
        focused().assert(hasText("Backup Line"))
        press(Key.DirectionCenter)

        compose.onNodeWithText("Home Portal").assertExists()
        focused().assert(hasText("Backup Line"))
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        assertEquals(listOf("ch105" to "home:105"), switched)
        focusedChannel(105, "Arena Sports 1")
    }

    @Test fun aChannelNotPlayingShowsTheSourceItStartsOnAndWhatWorksBest() {
        val switched = mutableListOf<Pair<String, String>>()
        val forgot = mutableListOf<String>()
        val remembered = mapOf("ch105" to LiveRemembered("backup:105", Sample.now.minus(Duration.ofDays(3))))
        screen {
            LiveTvScreen(
                merged,
                LiveTvActions(onSwitchSource = { id, source -> switched += id to source }, onForgetSource = { forgot += it }),
                playing = LivePlaying("ch106", "home:106"),
                remembered = remembered,
            )
        }
        press(Key.Menu)
        pressDownTo("Switch Source")
        // It starts on the source Remember What Works keeps.
        focused().assert(hasText("Backup Line"))
        press(Key.DirectionCenter)

        compose.onNodeWithText("Works best").assertExists()
        compose.onNodeWithText("Remembered 3 days ago").assertExists()
        // Picking the one it starts on changes nothing.
        focused().assert(hasText("Backup Line"))
        press(Key.DirectionCenter)
        assertEquals(emptyList<Pair<String, String>>(), switched)

        press(Key.Menu)
        pressDownTo("Switch Source")
        press(Key.DirectionCenter)
        pressDownTo("Forget What Works")
        press(Key.DirectionCenter)
        assertEquals(listOf("ch105"), forgot)
        focusedChannel(105, "Arena Sports 1")
    }

    @Test fun aChannelFromOneSourceHasNoSwitchSource() {
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions()) }
        press(Key.Menu)
        pressDownTo("Change Number")
        compose.onNodeWithText("Switch Source").assertDoesNotExist()
    }

    @Test fun changeNumberTakesOnlyAFreeNumber() {
        val changed = mutableListOf<Pair<String, Int>>()
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions(onChangeNumber = { id, number -> changed += id to number })) }
        press(Key.Menu)
        pressDownTo("Change Number")
        focused().assert(hasText("105"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("Arena Sports 1 · now 105").assertExists()

        // The remote's number keys type: 106 is Arena Sports 2's.
        listOf(Key.One, Key.Zero, Key.Six).forEach { press(it) }
        compose.onNodeWithText("106 is taken by Arena Sports 2").assertExists()
        listOf(Key.Delete, Key.Delete, Key.Delete).forEach { press(it) }
        listOf(Key.Five, Key.Zero, Key.Five).forEach { press(it) }
        compose.onNodeWithText("505 is free").assertExists()
        press(Key.DirectionDown)
        focused().assert(hasText("Use 505"))
        press(Key.DirectionCenter)

        assertEquals(listOf("ch105" to 505), changed)
        focusedChannel(105, "Arena Sports 1")
    }

    @Test fun changeNumberBackLeavesItAsItWas() {
        val changed = mutableListOf<Pair<String, Int>>()
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions(onChangeNumber = { id, number -> changed += id to number })) }
        press(Key.Menu)
        pressDownTo("Change Number")
        press(Key.DirectionCenter)
        listOf(Key.Five, Key.Zero, Key.Five).forEach { press(it) }
        press(Key.Back)

        assertEquals(emptyList<Pair<String, Int>>(), changed)
        focusedChannel(105, "Arena Sports 1")
    }

    @Test fun channelInfoSaysWhereItComesFrom() {
        screen { LiveTvScreen(merged, LiveTvActions(), playing = LivePlaying("ch105", "backup:105")) }
        press(Key.Menu)
        pressDownTo("Channel Info")
        press(Key.DirectionCenter)

        // The source it plays from first.
        compose.onNodeWithText("Backup Line · Home Portal").assertExists()
        compose.onNodeWithText("arena1.example").assertExists()
        compose.onNode(hasText("Group") and hasText("Sports")).assertExists()
        compose.onNode(hasText("Catch-up") and hasText("7 days")).assertExists()
        press(Key.Back)
        focusedChannel(105, "Arena Sports 1")
    }

    /** Down through a sheet until [label] has focus. */
    private fun pressDownTo(label: String) {
        repeat(12) {
            if (runCatching { focused().assert(hasText(label)) }.isSuccess) return
            press(Key.DirectionDown)
        }
        focused().assert(hasText(label))
    }
}
