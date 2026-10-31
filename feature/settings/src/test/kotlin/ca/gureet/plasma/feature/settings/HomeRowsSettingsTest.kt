// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.model.HomeRow
import ca.gureet.plasma.core.model.HomeRow.Apps
import ca.gureet.plasma.core.model.HomeRow.ContinueWatching
import ca.gureet.plasma.core.model.HomeRow.Favorites
import ca.gureet.plasma.core.model.HomeRow.NewMovies
import ca.gureet.plasma.core.model.HomeRow.NewSeries
import ca.gureet.plasma.core.model.HomeRow.RecentlyRecorded
import ca.gureet.plasma.core.model.HomeRow.UpcomingEvents
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Settings › Launcher & Home › Home Rows (the owner's #80): each row's switch, and moving rows. */
class HomeRowsSettingsTest : ScreenshotTest() {
    // The order as the app keeps it: a move comes back as the page's own order, as from the store.
    private var order by mutableStateOf(HomeRow.entries.toList())
    private val moves = mutableListOf<List<HomeRow>>()
    private var opened: SettingsPage? = null

    private fun show(values: SettingsValues = SettingsValues(), page: SettingsPage = SettingsPage.HomeRows) = screen {
        SettingsScreen(
            page,
            values,
            SettingsActions(
                onOpen = { opened = it },
                onHomeRows = {
                    moves += it
                    order = it
                },
            ),
            facts = SettingsSamples.facts.copy(homeRowOrder = order),
        )
    }

    /** The focused row reads as [description], whole. */
    private fun focusedIs(description: String) = focused().assert(hasContentDescription(description))

    @Test fun okFlipsARowsSwitch() {
        val values = SettingsValues()
        show(values)
        // Every row in its order, the first with focus; the new ones start off.
        focusedIs("Favorite Channels, On")
        press(Key.DirectionCenter)
        focusedIs("Favorite Channels, Off")
        check(!values.isOn(Favorites.key, Favorites.onByDefault)) { "Favorite Channels still on" }

        press(Key.DirectionDown, times = 4)
        focusedIs("Upcoming Events, Off")
        press(Key.DirectionCenter)
        focusedIs("Upcoming Events, On")
        check(values.isOn(UpcomingEvents.key, UpcomingEvents.onByDefault)) { "Upcoming Events still off" }
        check(moves.isEmpty()) { "Moved $moves" }
    }

    @Test fun holdingOkLiftsARowAndOkDropsIt() {
        val values = SettingsValues()
        show(values)
        press(Key.DirectionDown, times = 3)
        focusedIs("New in Series, On")

        // Held, OK lifts it rather than flipping it.
        longPress(Key.DirectionCenter)
        focusedIs("New in Series, On, moving")
        compose.onNodeWithText("Moving New in Series").assertExists()
        press(Key.DirectionUp, times = 2)
        // Nothing else acts until it's dropped.
        press(Key.DirectionLeft)
        press(Key.Menu)
        focusedIs("New in Series, On, moving")
        press(Key.DirectionCenter)
        check(moves == listOf(listOf(Favorites, NewSeries, ContinueWatching, NewMovies, UpcomingEvents, RecentlyRecorded, Apps))) { "Moved $moves" }
        check(values.isOn(NewSeries.key, NewSeries.onByDefault)) { "The switch flipped" }

        // It stays where it was dropped, between Favorite Channels and Continue Watching.
        focusedIs("New in Series, On")
        press(Key.DirectionUp)
        focusedIs("Favorite Channels, On")
        press(Key.DirectionDown, times = 2)
        focusedIs("Continue Watching, On")
    }

    @Test fun menuLiftsARowAndBackPutsItBack() {
        val values = SettingsValues()
        show(values)
        press(Key.DirectionDown)
        press(Key.Menu)
        focusedIs("Continue Watching, On, moving")
        press(Key.DirectionDown, times = 3)
        // The last row can't go further.
        press(Key.DirectionDown, times = 3)
        back()
        focusedIs("Continue Watching, On")
        check(moves.isEmpty()) { "Moved $moves" }
        press(Key.DirectionUp)
        focusedIs("Favorite Channels, On")

        // Dropped where it lifted, nothing changes either, and the switch stays.
        press(Key.Menu)
        press(Key.DirectionCenter)
        focusedIs("Favorite Channels, On")
        check(moves.isEmpty()) { "Moved $moves" }
        check(values.isOn(Favorites.key, Favorites.onByDefault)) { "The switch flipped" }
    }

    /** Board 04's move mode on Home Rows: New in Series carried up one. No board draws it. */
    @Test fun movingARowScreenshot() {
        show()
        press(Key.DirectionDown, times = 3)
        press(Key.Menu)
        press(Key.DirectionUp)
        compose.mainClock.advanceTimeBy(400)
        capture("22-settings-home-rows-move")
    }

    @Test fun appsSaysItStaysHiddenWithoutAndroidApps() {
        show(SettingsValues(mapOf(SettingKeys.ANDROID_APPS to 1)))
        compose.onNodeWithText("Hidden while Show Android Apps is off").assertExists()
        compose.onNodeWithContentDescription("Apps, Hidden while Show Android Apps is off, On").assertExists()
    }

    @Test fun continueWatchingAndUpcomingEventsOpenTheirPages() {
        show()
        // Past the seven rows, the rows' own settings.
        press(Key.DirectionDown, times = 7)
        focusedIs("Continue Watching")
        press(Key.DirectionCenter)
        check(opened == SettingsPage.ContinueWatching) { "Opened $opened" }
        press(Key.DirectionDown)
        focusedIs("Upcoming Events")
        press(Key.DirectionCenter)
        check(opened == SettingsPage.UpcomingEvents) { "Opened $opened" }
    }

    @Test fun launcherAndHomeSaysHowManyRowsShowAndOpensHomeRows() {
        screen { SettingsScreen(SettingsPage.Launcher, SettingsValues(), SettingsActions(onOpen = { opened = it }), initialItem = "home-rows") }
        compose.onNodeWithContentDescription("Home Rows, 5 rows").assertIsFocused()
        press(Key.DirectionCenter)
        check(opened == SettingsPage.HomeRows) { "Opened $opened" }
    }

    @Test fun withoutAndroidAppsLauncherAndHomeCountsOneRowFewer() {
        screen { SettingsScreen(SettingsPage.Launcher, SettingsValues(mapOf(SettingKeys.ANDROID_APPS to 1)), SettingsActions(), initialItem = "home-rows") }
        compose.onNodeWithContentDescription("Home Rows, 4 rows").assertIsFocused()
    }

    @Test fun upcomingEventsKindsEachHaveASwitch() {
        val values = SettingsValues()
        show(values, SettingsPage.UpcomingEvents)
        focusedIs("Reminders and Recordings, On")
        press(Key.DirectionDown)
        focusedIs("Starting Soon on Favorites, On")
        press(Key.DirectionDown)
        focusedIs("Live Sports and Events, On")
        press(Key.DirectionCenter)
        focusedIs("Live Sports and Events, Off")
        check(!values.isOn(SettingKeys.UPCOMING_SPORTS, true)) { "Live Sports and Events still on" }
    }
}
