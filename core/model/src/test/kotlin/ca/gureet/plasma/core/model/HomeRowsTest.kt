// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import ca.gureet.plasma.core.model.HomeRow.Apps
import ca.gureet.plasma.core.model.HomeRow.ContinueWatching
import ca.gureet.plasma.core.model.HomeRow.Favorites
import ca.gureet.plasma.core.model.HomeRow.NewMovies
import ca.gureet.plasma.core.model.HomeRow.NewSeries
import ca.gureet.plasma.core.model.HomeRow.RecentlyRecorded
import ca.gureet.plasma.core.model.HomeRow.UpcomingEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's rows, their order and their switches (the owner's #80). */
class HomeRowsTest {
    @Test fun theDefaultsAreTheOwnersOrderWithTheNewRowsOff() {
        assertEquals(listOf(Favorites, ContinueWatching, NewMovies, NewSeries, UpcomingEvents, RecentlyRecorded, Apps), HomeRow.order(null))
        assertEquals(listOf(Favorites, ContinueWatching, NewMovies, NewSeries, Apps), SettingsChoices().homeRows(HomeRow.order(null)))
        assertEquals(HomeRow.defaults, SettingsChoices().homeRows(HomeRow.order(null)))
    }

    @Test fun aKeptOrderComesBackWithEveryRowOnce() {
        val moved = listOf(Apps, UpcomingEvents, Favorites, NewSeries, ContinueWatching, RecentlyRecorded, NewMovies)
        assertEquals(moved, HomeRow.order(moved.map { it.id }))
        // A name twice, or one Plasma doesn't know, doesn't count.
        assertEquals(moved, HomeRow.order(listOf("apps", "upcomingEvents", "apps", "weather") + moved.drop(2).map { it.id }))
    }

    @Test fun rowsTheKeptOrderDoesntNameGoAfterTheRowBeforeThem() {
        // An order kept before New in Series and the others came: they go where they would by default.
        assertEquals(
            listOf(Apps, Favorites, ContinueWatching, NewMovies, NewSeries, UpcomingEvents, RecentlyRecorded),
            HomeRow.order(listOf("apps", "favorites", "continueWatching", "newMovies")),
        )
        // With nothing before it by default, Favorite Channels goes first.
        assertEquals(
            listOf(Favorites, ContinueWatching, Apps, NewMovies, NewSeries, UpcomingEvents, RecentlyRecorded),
            HomeRow.order(listOf("apps", "newMovies")),
        )
        assertEquals(HomeRow.entries, HomeRow.order(emptyList()))
    }

    @Test fun switchesTurnRowsOnAndOff() {
        val chosen = SettingsChoices(mapOf(UpcomingEvents.key to 0, ContinueWatching.key to 1, NewSeries.key to 1))
        assertTrue(chosen.rowOn(UpcomingEvents))
        assertFalse(chosen.rowOn(ContinueWatching))
        assertEquals(listOf(UpcomingEvents, Favorites, NewMovies, Apps), chosen.homeRows(listOf(UpcomingEvents, Favorites, ContinueWatching, NewMovies, NewSeries, RecentlyRecorded, Apps)))
    }

    @Test fun showAndroidAppsOffHidesAppsWhateverItsSwitchSays() {
        val off = SettingsChoices(mapOf(SettingKeys.ANDROID_APPS to 1, Apps.key to 0))
        assertEquals(listOf(Favorites, ContinueWatching, NewMovies, NewSeries), off.homeRows(HomeRow.order(null)))
        assertTrue(off.rowOn(Apps))
    }

    @Test fun upcomingEventsBringsInEveryKindUntilOneIsTurnedOff() {
        assertEquals(UpcomingKind.entries.toSet(), SettingsChoices().upcomingKinds)
        val noSports = SettingsChoices(mapOf(SettingKeys.UPCOMING_SPORTS to 1))
        assertEquals(setOf(UpcomingKind.RemindersAndRecordings, UpcomingKind.StartingSoonOnFavorites), noSports.upcomingKinds)
    }
}
