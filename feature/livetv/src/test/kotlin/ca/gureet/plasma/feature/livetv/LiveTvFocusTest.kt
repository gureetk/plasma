// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.livetv

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.design.KeyChoices
import ca.gureet.plasma.core.design.LocalKeyChoices
import ca.gureet.plasma.core.design.LocalShowChannelNumbers
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.model.LiveDetails
import ca.gureet.plasma.core.testing.ScreenshotTest
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-pad, number keys and sheets on Live TV (brief 4.7, 4.11 and 5.2). */
class LiveTvFocusTest : ScreenshotTest() {
    private var watched: String? = null
    private val played = mutableListOf<String>()

    private fun show() = screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions(onWatch = { watched = it }, onPlay = { id, _ -> played += id })) }

    private companion object {
        /** A favorite's star, as a row reads it. */
        const val STAR = "Favorite"
    }

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

        // Right goes to what's coming up on the channel focus rests on.
        press(Key.DirectionUp)
        compose.mainClock.advanceTimeBy(PlasmaMotion.DETAILS_DWELL_MS + 100)
        press(Key.DirectionRight)
        focused().assert(hasText("Match of the Week Extra"))
        press(Key.DirectionLeft)
        focusedChannel(105, "Arena Sports 1")
    }

    @Test fun theScheduleUnderThePreviewScrollsAsFarAsTheGuideGoes() {
        val previewed = mutableListOf<String>()
        screen { LiveTvScreen(LiveTvSamples.withLongSchedule, LiveTvActions(onDetails = { previewed += it })) }
        // The schedule starts on the channel playing, and says so.
        check(previewed == listOf("ch105")) { "Previewed $previewed" }
        press(Key.DirectionRight)
        focused().assert(hasText("Match of the Week Extra"))
        press(Key.DirectionDown, times = 7)
        focused().assert(hasText("Overnight Highlights 5"))
        press(Key.DirectionLeft)
        focusedChannel(105, "Arena Sports 1")
    }

    @Test fun aGapInTheGuideShowsWhatsComingUp() {
        val state = LiveTvSamples.state.let { s ->
            s.copy(preview = { id -> s.preview(id)?.let { if (id == "ch105") it.copy(now = null) else it } })
        }
        screen { LiveTvScreen(state, LiveTvActions()) }
        // The program's in the list, not over the schedule.
        compose.onAllNodesWithText("Harbour City v Redmoor").assertCountEquals(1)
        press(Key.DirectionRight)
        focused().assert(hasText("Match of the Week Extra"))
    }

    @Test fun movingThroughTheListLeavesTheChannelOkPlaysItThenGoesFullScreen() {
        val details = mutableListOf<String>()
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions(onWatch = { watched = it }, onPlay = { id, _ -> played += id }, onDetails = { details += it })) }
        // Moving and resting changes the schedule under the picture, never the channel (the owner's #76).
        press(Key.DirectionDown, times = 2)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        focusedChannel(107, "Velocity Motor")
        check(played.isEmpty() && watched == null) { "Played $played, watched $watched" }
        check(details.last() == "ch107") { "Details $details" }

        // OK plays it in the picture; OK again goes full screen.
        press(Key.DirectionCenter)
        check(played == listOf("ch107") && watched == null) { "Played $played, watched $watched" }
        press(Key.DirectionCenter)
        check(watched == "ch107") { "Watched $watched" }

        // The one that played before comes back to the picture first.
        watched = null
        press(Key.DirectionUp, times = 2)
        press(Key.DirectionCenter)
        check(played == listOf("ch107", "ch105") && watched == null) { "Played $played, watched $watched" }
    }

    @Test fun okOnTheChannelInThePictureGoesFullScreen() {
        show()
        press(Key.DirectionCenter)
        check(watched == "ch105" && played.isEmpty()) { "Watched $watched, played $played" }
    }

    @Test fun aChannelThatNeedsThePinGoesStraightToFullScreen() {
        // The picture can't show a locked channel, so the PIN is asked full screen (5.12).
        show()
        press(Key.DirectionDown, times = 5)
        focusedChannel(110, "Combat Arena")
        press(Key.DirectionCenter)
        check(watched == "ch110" && played.isEmpty()) { "Watched $watched, played $played" }
    }

    @Test fun theDetailsFollowFocusOrStayWithThePicture() {
        // Live TV Details › Focused Channel: what's on the channel focus rests on.
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions()) }
        press(Key.DirectionDown)
        compose.mainClock.advanceTimeBy(PlasmaMotion.DETAILS_DWELL_MS + 100)
        compose.waitForIdle()
        compose.onAllNodesWithText("Grand Slam Tennis: Quarter-final").assertCountEquals(2)
        compose.onAllNodesWithText("Harbour City v Redmoor").assertCountEquals(1)
    }

    @Test fun playingChannelDetailsStayWithThePicture() {
        // Live TV Details › Playing Channel: what's on the channel in the picture, until OK plays another.
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions(), details = LiveDetails.PlayingChannel) }
        press(Key.DirectionDown)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onAllNodesWithText("Harbour City v Redmoor").assertCountEquals(2)
        compose.onAllNodesWithText("Grand Slam Tennis: Quarter-final").assertCountEquals(1)
        press(Key.DirectionCenter)
        compose.onAllNodesWithText("Grand Slam Tennis: Quarter-final").assertCountEquals(2)
    }

    @Test fun typingANumberFocusesItsChannelAfterAPause() {
        show()
        press(Key.One)
        press(Key.Zero)
        press(Key.Eight)
        compose.onNodeWithText("108_").assertExists()
        compose.onNodeWithText("Courtside+").assertExists()

        // It gets focus, and doesn't play until OK (the owner's #79).
        waitForTune()
        compose.onNodeWithText("108_").assertDoesNotExist()
        focusedChannel(108, "Courtside+")
        check(watched == null && played.isEmpty()) { "Watched $watched, played $played" }
        press(Key.DirectionCenter)
        check(played == listOf("ch108")) { "Played $played" }
    }

    @Test fun okGoesToTheNumberAtOnceAndBackDropsIt() {
        show()
        press(Key.One)
        press(Key.Zero)
        press(Key.Seven)
        press(Key.DirectionCenter)
        focusedChannel(107, "Velocity Motor")
        check(watched == null && played.isEmpty()) { "Watched $watched, played $played" }

        press(Key.One)
        compose.onNodeWithText("1_").assertExists()
        press(Key.Back)
        compose.onNodeWithText("1_").assertDoesNotExist()
        waitForTune()
        check(watched == null && played.isEmpty()) { "Watched $watched, played $played after Back" }
        // Back went to the number, not to the list.
        focusedChannel(107, "Velocity Motor")
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
        focusedChannel(2, "Velocity Motor")

        // A number Favorites doesn't have falls back to the full list, which opens on it.
        press(Key.One)
        press(Key.Zero)
        press(Key.Eight)
        compose.onNodeWithText("Courtside+").assertExists()
        press(Key.DirectionCenter)
        focusedChannel(108, "Courtside+")
        // All Channels' header: "All Channels 1,284 channels".
        compose.onNodeWithText("1,284 channels").assertExists()
        check(watched == null && played.isEmpty()) { "Watched $watched, played $played" }
    }

    @Test fun aNumberIsTheOpenListsFirst() {
        // A provider that numbers each group on its own: the number beside a channel in the open
        // group tunes that channel, as it does in the player, not All Channels' with that number.
        val base = LiveTvSamples.state
        val group = base.selectedGroupId
        val renumbered = base.copy(channels = base.channels + (group to base.channels.getValue(group).mapIndexed { i, ch -> ch.copy(number = (i + 1).toString()) }))
        val second = renumbered.channels.getValue(group)[1]
        screen { LiveTvScreen(renumbered, LiveTvActions(onWatch = { watched = it })) }
        press(Key.Two)
        compose.onNodeWithText(second.name).assertExists()
        press(Key.DirectionCenter)
        focusedChannel(2, second.name)
    }

    @Test fun aHeldArrowStaysInTheChannelListAndAPressLeavesIt() {
        show()
        focusedChannel(105, "Arena Sports 1")
        // Held Up goes to the top of Sports and stops there, rather than up to the top bar.
        hold(Key.DirectionUp, 1_000)
        focusedChannel(103, "Arena Sports News")
        press(Key.DirectionUp)
        focused().assert(!hasContentDescription("Arena Sports News", substring = true))
    }

    @Test fun aCategoryPickedOpensAtItsFirstChannel() {
        show()
        press(Key.DirectionLeft)
        focused().assert(hasText("Sports"))
        press(Key.DirectionDown)
        focused().assert(hasText("News"))
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        press(Key.DirectionRight)
        focusedChannel(101, "Northline News")

        // Out to the categories and back in without picking another: the channel it had.
        press(Key.DirectionDown)
        focusedChannel(102, "Meridian 24")
        press(Key.DirectionLeft)
        press(Key.DirectionRight)
        focusedChannel(102, "Meridian 24")
    }

    @Test fun aChannelTakenOutOfFavoritesStaysWhileFocusIsOnIt() {
        var state by mutableStateOf(LiveTvSamples.state)
        screen { LiveTvScreen(state, LiveTvActions(onFavorite = { id, on -> if (!on) state = state.withoutFavorite(id) })) }
        press(Key.DirectionLeft)
        press(Key.DirectionUp, times = 2)
        focused().assert(hasText("Favorites"))
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        press(Key.DirectionRight)
        focusedChannel(1, "Arena Sports 1")
        press(Key.DirectionDown)
        focusedChannel(2, "Velocity Motor")

        // Red takes it out of Favorites: it stays where it was, its star off, while focus is on it…
        press(Key.ProgramRed)
        focusedChannel(107, "Velocity Motor")
        // …and goes once focus moves on.
        press(Key.DirectionDown)
        focusedChannel(2, "Northline News")
        compose.onNode(hasContentDescription("Velocity Motor", substring = true)).assertDoesNotExist()
    }

    /**
     * The app's state can be behind a change made here, its database busy: a state it makes before
     * the change gets there still has the channel in Favorites, with its star. The channel taken out
     * stays out all the same (the owner's report, 1 November: it came back with its star once
     * another channel played).
     */
    @Test fun aChannelTakenOutOfFavoritesStaysOutWhileTheAppCatchesUp() {
        // OK has the app make a new state, still from before the change.
        var state by mutableStateOf(LiveTvSamples.state)
        screen { LiveTvScreen(state, LiveTvActions(onPlay = { _, _ -> state = LiveTvSamples.state })) }
        openFavorites()
        press(Key.DirectionDown)
        focusedChannel(2, "Velocity Motor")

        press(Key.ProgramRed)
        focusedChannel(2, "Velocity Motor")
        focused().assert(!hasContentDescription(STAR))
        // Focus moves on: it goes, though the app's state still has it…
        press(Key.DirectionDown)
        focusedChannel(3, "Northline News")
        compose.onNode(hasContentDescription("Velocity Motor", substring = true)).assertDoesNotExist()
        // …and OK plays Northline News, the app making a state from what it has: it stays out.
        press(Key.DirectionCenter)
        compose.onNode(hasContentDescription("Velocity Motor", substring = true)).assertDoesNotExist()
        // All Channels has it, its star off.
        press(Key.DirectionLeft)
        press(Key.DirectionUp)
        focused().assert(hasText("All Channels"))
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNode(hasContentDescription("107, Velocity Motor", substring = true)).assert(!hasContentDescription(STAR))

        // Once the app has caught up, it's the app's own again.
        state = state.withoutFavorite(Sample.velocity.id)
        compose.waitForIdle()
        compose.onNode(hasContentDescription("107, Velocity Motor", substring = true)).assert(!hasContentDescription(STAR))
    }

    @Test fun aChannelPutBackInFavoritesBeforeItGoesKeepsItsPlace() {
        var state by mutableStateOf(LiveTvSamples.state)
        val putBack = mutableListOf<Pair<String, String?>>()
        screen {
            LiveTvScreen(
                state,
                LiveTvActions(
                    onFavorite = { id, on -> if (!on) state = state.withoutFavorite(id) },
                    onFavoriteBack = { id, after ->
                        putBack += id to after
                        state = LiveTvSamples.state
                    },
                ),
            )
        }
        openFavorites()
        press(Key.DirectionDown)
        focusedChannel(2, "Velocity Motor")
        press(Key.ProgramRed)
        focusedChannel(107, "Velocity Motor")

        // Red again while it stays: a favorite again, after Arena Sports 1 as it was.
        press(Key.ProgramRed)
        assertEquals(listOf(Sample.velocity.id to Sample.arena1.id), putBack)
        focusedChannel(2, "Velocity Motor")
        focused().assert(hasContentDescription(STAR))
        press(Key.DirectionDown)
        focusedChannel(3, "Northline News")
        compose.onNode(hasContentDescription("2, Velocity Motor", substring = true)).assertExists()
    }

    /**
     * At the end of a long list, the last channel shows whole above the list's fade rather than
     * under it, against the footer (the owner's report, 1 November).
     */
    @Test fun theLastChannelShowsAboveTheFade() {
        show()
        press(Key.DirectionLeft)
        press(Key.DirectionUp, times = 3)
        focused().assert(hasText("All Channels"))
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        press(Key.DirectionRight)
        focusedChannel(101, "Northline News")
        press(Key.DirectionDown, times = Sample.channels.lastIndex)
        focusedChannel(210, "Orbit Science")

        // The fade starts 120 px above the footer, at y 872.
        val bottom = focused().fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("The last channel ends at y $bottom", bottom <= 872f)
    }

    /** Favorites, with focus on its first channel. */
    private fun openFavorites() {
        press(Key.DirectionLeft)
        press(Key.DirectionUp, times = 2)
        focused().assert(hasText("Favorites"))
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        press(Key.DirectionRight)
        focusedChannel(1, "Arena Sports 1")
    }

    /** Favorites as the app has it once [id] is out of it: numbered from 1 again, its star off everywhere. */
    private fun LiveTvUiState.withoutFavorite(id: String) = copy(
        channels = channels.mapValues { (group, list) ->
            if (group == "favorites") {
                list.filter { it.id != id }.mapIndexed { i, channel -> channel.copy(number = (i + 1).toString()) }
            } else {
                list.map { if (it.id == id) it.copy(favorite = false) else it }
            }
        },
    )

    /** Settings › Remote & Buttons › Long-Press OK: Nothing. */
    @Test fun holdingOkIsAnOkWhenLongPressOkDoesNothing() {
        screen {
            CompositionLocalProvider(LocalKeyChoices provides KeyChoices(longPressOk = false)) {
                LiveTvScreen(LiveTvSamples.state, LiveTvActions(onPlay = { id, _ -> played += id }))
            }
        }
        press(Key.DirectionDown)
        focusedChannel(106, "Arena Sports 2")
        longPress(Key.DirectionCenter)
        // It plays in the picture, as OK does, rather than opening the channel's options.
        assertEquals(listOf("ch106"), played)
        focusedChannel(106, "Arena Sports 2")
        // Menu still opens them.
        press(Key.Menu)
        focused().assert(hasText("Add to Favorites"))
    }

    /** Settings › Appearance › Show Channel Numbers: Off. */
    @Test fun channelNumbersCanBeHidden() {
        screen {
            CompositionLocalProvider(LocalShowChannelNumbers provides false) {
                LiveTvScreen(LiveTvSamples.state, LiveTvActions())
            }
        }
        compose.onAllNodesWithText("105", useUnmergedTree = true).assertCountEquals(0)
        // A row still says its number to TalkBack, and typing it still finds it.
        focusedChannel(105, "Arena Sports 1")
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
        compose.onNodeWithText("Channel 105 is hidden").assertExists()
        focused().assert(hasText("Unhide"))
        // Unhidden, it's back in its list with focus, as a typed number's channel is (#79).
        press(Key.DirectionCenter)
        focusedChannel(105, "Arena Sports 1")
        check(watched == null && played.isEmpty()) { "Watched $watched, played $played" }
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

    @Test fun moveCarriesAFavoriteThroughFavoritesAndOkDropsIt() {
        val moved = mutableListOf<Pair<String, String?>>()
        screen { LiveTvScreen(LiveTvSamples.state.copy(selectedGroupId = "favorites"), LiveTvActions(onMoveFavorite = { id, after -> moved += id to after })) }
        focusedChannel(1, "Arena Sports 1")
        press(Key.Menu)
        pressDownTo("Move")
        press(Key.DirectionCenter)

        compose.onNodeWithText("Moving Arena Sports 1").assertExists()
        press(Key.DirectionDown, times = 2)
        // Third now, and numbered so; Northline News moved up to second.
        focusedChannel(3, "Arena Sports 1")
        compose.onNode(hasContentDescription("2, Northline News", substring = true)).assertExists()
        press(Key.DirectionCenter)

        assertEquals(listOf("ch105" to "ch101"), moved)
        compose.onNodeWithText("Moving Arena Sports 1").assertDoesNotExist()
        focusedChannel(3, "Arena Sports 1")
    }

    @Test fun backPutsAMovingFavoriteBack() {
        val moved = mutableListOf<Pair<String, String?>>()
        screen { LiveTvScreen(LiveTvSamples.state.copy(selectedGroupId = "favorites"), LiveTvActions(onMoveFavorite = { id, after -> moved += id to after })) }
        press(Key.Menu)
        pressDownTo("Move")
        press(Key.DirectionCenter)
        press(Key.DirectionDown)

        back()

        assertEquals(emptyList<Pair<String, String?>>(), moved)
        focusedChannel(1, "Arena Sports 1")
        compose.onNodeWithText("Moving Arena Sports 1").assertDoesNotExist()
    }

    @Test fun moveIsOnlyInFavorites() {
        screen { LiveTvScreen(LiveTvSamples.state, LiveTvActions()) }
        press(Key.Menu)
        pressDownTo("Hide Channel")
        compose.onNodeWithText("Move").assertDoesNotExist()
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
