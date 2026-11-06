// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test

/** Adding a source after Connect (brief 4.35's sync progress, the owner's #158). */
class SourceSyncTest : ScreenshotTest() {
    private class Adding(start: SourceSync) : SourceAdding {
        val state = MutableStateFlow(start)
        override val progress: StateFlow<SourceSync> = state
        var background = false
        var stopped = false

        override fun inBackground() {
            background = true
        }

        override fun stop() {
            stopped = true
        }
    }

    private val starting = SourceSync("Home Portal", SourceType.Stalker)
    private val reading = starting.copy(stage = SyncStage.Channels, channels = 3_500)
    private val library = starting.copy(
        stage = SyncStage.Library,
        channels = 1_284,
        programs = 52_310,
        movies = 3_250,
        account = ConnectOutcome.Connected("Home Portal", LocalDate.of(2027, 3, 1), connections = null, channels = 1_284, knowsConnections = false),
    )

    @Test fun whileTheChannelsLoad() {
        screen { SourceSyncScreen(reading, onBackground = {}, onDone = {}, onStop = {}) }
        compose.onNodeWithText("Adding Home Portal").assertExists()
        compose.onNodeWithText("Signed in").assertExists()
        compose.onNodeWithText("Channels · 3,500…").assertExists()
        focused().assert(hasText("Load in Background"))
        capture("21-add-a-source-loading")
    }

    @Test fun theRestLoadsAfterTheChannels() {
        screen { SourceSyncScreen(library, onBackground = {}, onDone = {}, onStop = {}) }
        compose.onNodeWithText("Connected to Home Portal").assertExists()
        compose.onNodeWithText("1,284 channels").assertExists()
        compose.onNodeWithText("Guide · 52,310 programs").assertExists()
        compose.onNodeWithText("Movies · 3,250…").assertExists()
        focused().assert(hasText("Done"))
        capture("21-add-a-source-loaded")
    }

    @Test fun aPlaylistHasNoSigningInNorMovies() {
        screen { SourceSyncScreen(SourceSync("Living Room List", SourceType.M3U, SyncStage.Channels), onBackground = {}, onDone = {}, onStop = {}) }
        compose.onNodeWithText("Channels · loading…").assertExists()
        compose.onNodeWithText("Signing in…").assertDoesNotExist()
        compose.onNodeWithText("Movies").assertDoesNotExist()
    }

    /** Connect on the sample portal, with [adding] as what the app does. */
    private fun connect(adding: Adding, onDone: () -> Unit = {}) {
        screen { AddSourceScreen(AddSourceSamples.state, AddSourceActions(onDone = onDone, onAdd = { adding })) }
        repeat(10) {
            if (runCatching { focused().assert(hasText("Connect")) }.isSuccess) return@repeat
            press(Key.DirectionDown)
        }
        focused().assert(hasText("Connect"))
        press(Key.DirectionCenter)
    }

    @Test fun loadInBackgroundLeavesItLoading() {
        val adding = Adding(starting)
        var done = false
        connect(adding) { done = true }
        compose.onNodeWithText("Adding Home Portal").assertExists()
        focused().assert(hasText("Load in Background"))
        press(Key.DirectionCenter)
        check(adding.background && done && !adding.stopped) { "background ${adding.background}, done $done, stopped ${adding.stopped}" }
    }

    @Test fun backWhileTheChannelsLoadStopsAndGoesBackToTheForm() {
        val adding = Adding(reading)
        var done = false
        connect(adding) { done = true }
        back()
        check(adding.stopped && !done) { "stopped ${adding.stopped}, done $done" }
        compose.onNodeWithText("Adding Home Portal").assertDoesNotExist()
        focused().assert(hasText("Connect"))
    }

    @Test fun channelsThatDontLoadSayWhyOnTheForm() {
        val adding = Adding(reading)
        connect(adding)
        adding.state.value = reading.copy(stage = SyncStage.Failed, problem = ConnectProblem.MacRefused)
        compose.onNodeWithText("Couldn’t Connect").assertExists()
        compose.onNodeWithText("The portal rejected this MAC address. Check it’s registered with your provider.").assertExists()
        compose.onNodeWithText("Adding Home Portal").assertDoesNotExist()
    }

    @Test fun onceTheChannelsAreInBackIsDone() {
        val adding = Adding(reading)
        var done = false
        connect(adding) { done = true }
        adding.state.value = library
        compose.waitForIdle()
        focused().assert(hasText("Done"))
        back()
        check(done && !adding.stopped) { "done $done, stopped ${adding.stopped}" }
    }
}
