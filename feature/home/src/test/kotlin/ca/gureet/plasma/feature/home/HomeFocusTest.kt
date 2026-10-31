// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.model.HomeRow
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** D-pad focus on Home (brief 4.1 and 4.2). */
class HomeFocusTest : ScreenshotTest() {
    private val docked = mutableListOf<DockTarget>()
    private var heroPlayed: String? = null
    private var tuned: String? = null
    private var continued: String? = null
    private val removed = mutableListOf<String>()
    private val removedContinue = mutableListOf<String>()
    private var moved: Pair<String, String?>? = null
    private var opened: String? = null
    private val hiddenOther = mutableListOf<Pair<String, Boolean>>()
    private var series: String? = null
    private var seeAll: String? = null
    private var tunedUpcoming: String? = null
    private val reminders = mutableListOf<Triple<String, String, Boolean>>()
    private val recordings = mutableListOf<Triple<String, String, Boolean>>()
    private var played: RecordedItem? = null

    private fun show(state: HomeUiState = HomeSamples.state) = screen {
        HomeScreen(
            state,
            HomeActions(
                onDock = { docked += it },
                onHero = { heroPlayed = it.title },
                onChannel = { tuned = it },
                onContinue = { continued = it },
                onRemoveFavorite = { removed += it },
                onMoveFavorite = { id, after -> moved = id to after },
                onRemoveContinue = { removedContinue += it },
                onHideOther = { id, wholeApp -> hiddenOther += id to wholeApp },
                onApp = { opened = it },
                onSeries = { series = it },
                onSeeAllSeries = { seeAll = "series" },
                onUpcoming = { tunedUpcoming = it },
                onRemind = { channelId, programId, on -> reminders += Triple(channelId, programId, on) },
                onRecord = { channelId, programId, on -> recordings += Triple(channelId, programId, on) },
                onRecording = { played = it },
                onSeeAllRecordings = { seeAll = "recordings" },
            ),
            heroAutoAdvance = false,
        )
    }

    private fun focusedOn(description: String) = focused().assert(hasContentDescription(description, substring = true))

    private fun focusedNotMoving() = focused().assert(!hasContentDescription(", moving", substring = true))

    private fun focusedNot(description: String) = focused().assert(!hasContentDescription(description, substring = true))

    /** Menu › Move on the focused favorite. */
    private fun startMove() {
        press(Key.Menu)
        press(Key.DirectionDown)
        focused().assert(hasText("Move"))
        press(Key.DirectionCenter)
    }

    @Test fun theDockTheHeroAndTheTopBar() {
        show()
        // The Live TV tile has focus when Home opens.
        focusedOn("Live TV")
        press(Key.DirectionRight)
        focusedOn("TV Guide")
        press(Key.DirectionCenter)
        check(docked == listOf(DockTarget.Guide)) { "Opened $docked" }

        // Up goes to the hero, where OK plays it, and Up again to the top bar's Home tab.
        press(Key.DirectionUp)
        focused().assert(hasText("Harbour City v Redmoor"))
        press(Key.DirectionCenter)
        check(heroPlayed == "Harbour City v Redmoor") { "Played $heroPlayed" }
        press(Key.DirectionUp)
        focused().assert(hasText("Home"))

        // Down from the top bar returns to the hero, and Down again to the Live TV tile.
        press(Key.DirectionDown)
        focused().assert(hasText("Harbour City v Redmoor"))
        press(Key.DirectionDown)
        focusedOn("Live TV")
    }

    @Test fun withShowTvGuideOffTheDockHasNoGuide() {
        // The owner's #78: off, the Guide leaves the dock as it leaves the top bar.
        show(HomeSamples.state.copy(showGuide = false))
        focusedOn("Live TV")
        press(Key.DirectionRight)
        focusedOn("Movies")
        compose.onAllNodesWithText("TV Guide").assertCountEquals(0)
    }

    @Test fun aMovieWithoutAPosterShowsItsNameAndIsLookedFor() {
        // The owner's #77 and #81: its name on the plate, and the app looks for its own poster.
        val wanted = mutableListOf<String>()
        screen { HomeScreen(HomeSamples.withoutPictures, HomeActions(onPosterless = { wanted += it }), initialFocus = HomeFocus.Rows, heroAutoAdvance = false) }
        val first = HomeSamples.withoutPictures.newMovies.first()
        compose.onAllNodesWithText(first.title).assertCountEquals(1)
        check(first.id in wanted) { "Wanted $wanted" }
        check(HomeSamples.withoutPictures.newMovies.filter { it.poster != null }.none { it.id in wanted }) { "Wanted $wanted" }
    }

    @Test fun leftAndRightStepThroughTheHero() {
        show()
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        focused().assert(hasText("Atlas Station"))
        press(Key.DirectionLeft)
        focused().assert(hasText("Harbour City v Redmoor"))
    }

    @Test fun downGoesToFavoritesThenTheRowsAndBackComesHome() {
        show()
        press(Key.DirectionDown)
        focusedOn("Wildframe 4K")
        press(Key.DirectionCenter)
        check(tuned == "ch180") { "Tuned $tuned" }

        press(Key.DirectionDown)
        focusedOn("Atlas Station")
        press(Key.DirectionCenter)
        check(continued != null) { "Nothing continued" }
        press(Key.DirectionDown)
        focusedOn("Neon Tide")
        // New in Series is on until it's turned off (the owner's #80).
        press(Key.DirectionDown)
        focusedOn("Kingsway")
        press(Key.DirectionDown)
        focusedOn("Reelbox")

        // Back from anywhere below the dock returns to the top, on the Live TV tile.
        back()
        focusedOn("Live TV")
    }

    @Test fun appsOpenFromTheirRow() {
        show()
        press(Key.DirectionDown, times = 5)
        focusedOn("Reelbox")
        press(Key.DirectionCenter)
        check(opened == "tv.example.reelbox") { "Opened $opened" }
    }

    @Test fun withoutAndroidAppsTheRowsEndWithNewInSeries() {
        // Show Android Apps off takes the Apps row away, whatever Home Rows says (the owner's #80).
        show(HomeSamples.state.copy(showApps = false))
        press(Key.DirectionDown, times = 4)
        focusedOn("Kingsway")
        press(Key.DirectionDown)
        focusedOn("Kingsway")
        compose.onAllNodesWithText("All Apps").assertCountEquals(0)
    }

    @Test fun favoritesAndContinueWatchingHaveOptions() {
        show()
        press(Key.DirectionDown)
        focusedOn("Wildframe 4K")
        longPress(Key.DirectionCenter)
        focused().assert(hasText("Remove from Favorites"))
        press(Key.DirectionCenter)
        // The favorite leaves the row, the app forgets it, and focus moves on to the next one.
        focusedOn("Northline News")
        check(removed == listOf("ch180")) { "Removed $removed" }

        press(Key.DirectionDown)
        focusedOn("Atlas Station")
        press(Key.Menu)
        focused().assert(hasText("Resume"))
        press(Key.DirectionDown)
        focused().assert(hasText("Remove from Continue Watching"))
        press(Key.DirectionCenter)
        focusedOn("Kite Season")
        // The app keeps it out of the row from now on.
        check(removedContinue == listOf("series:atlas:s2e4")) { "Removed $removedContinue" }

        // A Jellyfin item can only be hidden in Plasma, one at a time.
        press(Key.Menu)
        focused().assert(hasText("Open"))
        press(Key.DirectionDown)
        focused().assert(hasText("Hide from Plasma"))
        compose.onAllNodesWithText("Hide All from This App").assertCountEquals(0)
        press(Key.Back)
        focusedOn("Kite Season")
    }

    @Test fun anotherAppsItemsCanAllBeHidden() {
        val app = ContinueItem("playnext:tv.example.reelbox|ep-12", "Night Stint", "S2, E4 · 23 min left", null, ContinueKind.Art, 0.4f, sourceTag = "REELBOX")
        show(HomeSamples.state.copy(continueWatching = listOf(app) + HomeSamples.state.continueWatching))
        press(Key.DirectionDown, times = 2)
        focusedOn("Night Stint")

        // Another app's item can only be hidden in Plasma: it, or everything from its app.
        press(Key.Menu)
        focused().assert(hasText("Open"))
        press(Key.DirectionDown, times = 2)
        focused().assert(hasText("Hide All from This App"))
        press(Key.DirectionCenter)
        check(hiddenOther == listOf("playnext:tv.example.reelbox|ep-12" to true)) { "Hid $hiddenOther" }
    }

    @Test fun withNothingStartedContinueWatchingSaysWhatGoesThere() {
        show(HomeSamples.state.copy(continueWatching = emptyList()))
        compose.onNodeWithText("Movies and episodes you start show up here.").assertExists()
    }

    @Test fun removingEveryFavoriteReturnsToTheDock() {
        show()
        press(Key.DirectionDown)
        repeat(HomeSamples.state.favorites.size) {
            press(Key.Menu)
            press(Key.DirectionCenter)
        }
        // With the row empty, focus goes back to the Live TV tile, and Down passes the row by.
        focusedOn("Live TV")
        press(Key.DirectionDown)
        focusedOn("Atlas Station")
    }

    @Test fun moveCarriesAFavoriteAlongTheRowAndOkDropsIt() {
        show()
        press(Key.DirectionDown)
        focusedOn("Wildframe 4K")
        startMove()
        // It lifts where it is. It's first, so Left does nothing, and Right carries it past
        // Northline News and Velocity Motor.
        focusedOn("Wildframe 4K, moving")
        press(Key.DirectionLeft)
        press(Key.DirectionRight, times = 2)
        // Nothing else acts until it's dropped.
        press(Key.DirectionUp)
        press(Key.Menu)
        focusedOn("Wildframe 4K, moving")
        press(Key.DirectionCenter)
        check(moved == ("ch180" to "ch107")) { "Moved $moved" }
        check(tuned == null) { "Tuned $tuned" }

        // It stays where it was dropped, between Velocity Motor and Pulse.
        focusedNotMoving()
        focusedOn("Wildframe 4K")
        press(Key.DirectionLeft)
        focusedOn("Velocity Motor")
        press(Key.DirectionRight, times = 2)
        focusedOn("Pulse")
    }

    @Test fun backPutsAMovingFavoriteBack() {
        show()
        press(Key.DirectionDown)
        startMove()
        press(Key.DirectionRight, times = 3)
        back()
        // It's first again, with focus, and the app keeps nothing.
        focusedNotMoving()
        focusedOn("Wildframe 4K")
        press(Key.DirectionRight)
        focusedOn("Northline News")
        check(moved == null) { "Moved $moved" }

        // Dropped where it lifted, nothing changes either.
        startMove()
        press(Key.DirectionCenter)
        focusedNotMoving()
        check(moved == null) { "Moved $moved" }

        // Back goes home, as it does from the row.
        back()
        focusedOn("Live TV")
    }

    @Test fun aLoneFavoriteHasNoMove() {
        show(HomeSamples.state.copy(favorites = HomeSamples.state.favorites.take(1)))
        press(Key.DirectionDown)
        press(Key.Menu)
        focused().assert(hasText("Remove from Favorites"))
        compose.onNodeWithText("Move").assertDoesNotExist()
    }

    // ---- Home Rows (the owner's #80) -----------------------------------------------------------

    @Test fun aCustomOrderPutsItsFirstRowUnderTheDock() {
        show(HomeSamples.state.copy(rows = listOf(HomeRow.NewMovies, HomeRow.Favorites, HomeRow.Apps)))
        press(Key.DirectionDown)
        focusedOn("Neon Tide")
        press(Key.DirectionDown)
        focusedOn("Wildframe 4K")
        press(Key.DirectionDown)
        focusedOn("Reelbox")
        press(Key.DirectionUp, times = 2)
        focusedOn("Neon Tide")
        // Rows turned off aren't there at all.
        compose.onAllNodesWithText("Continue Watching").assertCountEquals(0)
        compose.onAllNodesWithText("New in Series").assertCountEquals(0)
        back()
        focusedOn("Live TV")
    }

    @Test fun focusFollowsTheOrderAllTheWayDown() {
        // Every row on, in an order of the owner's own: Down goes through them as they come.
        show(HomeSamples.allRows.copy(rows = listOf(HomeRow.Apps, HomeRow.RecentlyRecorded, HomeRow.Favorites, HomeRow.NewSeries, HomeRow.UpcomingEvents, HomeRow.NewMovies, HomeRow.ContinueWatching)))
        val path = listOf("Reelbox", "Business Wrap", "Wildframe 4K", "Kingsway", "Grand Slam Tennis", "Neon Tide", "Atlas Station")
        for (description in path) {
            press(Key.DirectionDown)
            focusedOn(description)
        }
        // And back up again, row by row.
        for (description in path.reversed().drop(1)) {
            press(Key.DirectionUp)
            focusedOn(description)
        }
        press(Key.DirectionUp)
        focusedOn("Live TV")
    }

    @Test fun rowsWithNothingToShowLeaveNoGap() {
        // No favorites and no new series: Continue Watching comes up under the dock.
        show(HomeSamples.state.copy(favorites = emptyList(), newSeries = emptyList()))
        compose.onAllNodesWithText("Favorite Channels").assertCountEquals(0)
        compose.onAllNodesWithText("New in Series").assertCountEquals(0)
        press(Key.DirectionDown)
        focusedOn("Atlas Station")
        press(Key.DirectionDown)
        focusedOn("Neon Tide")
        press(Key.DirectionDown)
        focusedOn("Reelbox")
    }

    @Test fun newInSeriesOpensASeriesAndSeeAllOpensSeries() {
        show()
        press(Key.DirectionDown, times = 4)
        focusedOn("Kingsway")
        press(Key.DirectionCenter)
        check(series == "kingsway") { "Opened $series" }
        press(Key.DirectionRight, times = HomeSamples.state.newSeries.size)
        focused().assert(hasText("See All"))
        press(Key.DirectionCenter)
        check(seeAll == "series") { "Saw all $seeAll" }
    }

    @Test fun upcomingEventsPlaysWhatsOnAndOffersRemindMeForWhatsLater() {
        show(HomeSamples.allRows)
        press(Key.DirectionDown, times = 2)
        // Soonest first: sport on now, with its channel's logo and when it ends.
        focusedOn("Grand Slam Tennis: Quarter-final, Arena Sports 2, LIVE, Ends 22:30")
        press(Key.DirectionCenter)
        check(tunedUpcoming == Sample.arena2.id) { "Tuned $tunedUpcoming" }

        // Baker's Dozen has a reminder: OK offers to cancel it, and the bell goes at once. It's
        // on a favorite too, so it stays.
        press(Key.DirectionRight, times = 3)
        focusedOn("Baker's Dozen, Atlas One, Tonight, 21:00–22:00, Reminder set")
        press(Key.DirectionCenter)
        focused().assert(hasText("Cancel Reminder"))
        press(Key.DirectionCenter)
        val bakers = HomeSamples.allRows.upcoming.first { it.title == "Baker's Dozen" }
        check(reminders == listOf(Triple(Sample.atlas.id, bakers.id, false))) { "Reminders $reminders" }
        focusedOn("Baker's Dozen")
        focusedNot("Reminder set")

        // The World Tonight is only starting soon on a favorite: Remind Me sets one.
        press(Key.DirectionRight)
        focusedOn("The World Tonight")
        press(Key.DirectionCenter)
        focused().assert(hasText("Remind Me"))
        press(Key.DirectionCenter)
        focusedOn("The World Tonight, Northline News, Tonight, 21:00–22:00, Reminder set")
        check(tunedUpcoming == Sample.arena2.id) { "Tuned $tunedUpcoming" }

        // Menu on a program on now offers Record alone: it has started, so there's nothing to remind of.
        press(Key.DirectionLeft, times = 4)
        focusedOn("Grand Slam Tennis")
        press(Key.Menu)
        focused().assert(hasText("Record"))
        compose.onAllNodesWithText("Remind Me").assertCountEquals(0)
        press(Key.Back)
        focusedOn("Grand Slam Tennis")
    }

    @Test fun cancellingTheOnlyReasonTakesAProgramOutOfUpcomingEvents() {
        show(HomeSamples.allRows)
        press(Key.DirectionDown, times = 2)
        // Glass Orchard is there because it's set to record.
        val orchard = HomeSamples.allRows.upcoming.indexOfFirst { it.title == "Glass Orchard" }
        press(Key.DirectionRight, times = orchard)
        focusedOn("Glass Orchard, Cinema Prime 4K, Tonight, 21:45–23:40, Recording")
        press(Key.DirectionCenter)
        focused().assert(hasText("Remind Me"))
        press(Key.DirectionDown)
        focused().assert(hasText("Cancel Recording"))
        press(Key.DirectionCenter)
        check(recordings == listOf(Triple(Sample.cinema.id, HomeSamples.allRows.upcoming[orchard].id, false))) { "Recordings $recordings" }
        // It leaves the row, and focus moves on to the next.
        compose.onAllNodes(hasContentDescription("Glass Orchard, Cinema Prime 4K", substring = true)).assertCountEquals(0)
        focusedOn("Match of the Week Extra")
    }

    @Test fun recentlyRecordedPlaysARecordingAndSeeAllOpensRecordings() {
        show(HomeSamples.allRows)
        press(Key.DirectionDown, times = 3)
        focusedOn("Business Wrap, Northline News, Yesterday, 20:30 · 30 min, Unwatched")
        press(Key.DirectionCenter)
        check(played?.id == "business") { "Played $played" }
        press(Key.DirectionRight, times = HomeSamples.allRows.recorded.size)
        focused().assert(hasText("See All"))
        press(Key.DirectionCenter)
        check(seeAll == "recordings") { "Saw all $seeAll" }
    }
}
