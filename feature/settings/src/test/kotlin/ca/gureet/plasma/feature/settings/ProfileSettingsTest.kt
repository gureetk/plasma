// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Settings › Profiles and a profile's own page (brief 5.11). */
class ProfileSettingsTest : ScreenshotTest() {
    @Test fun profilesListsEachOneThenAddProfile() {
        val opened = mutableListOf<String>()
        val added = mutableListOf<String>()
        screen { SettingsScreen(SettingsPage.Profiles, SettingsValues(), SettingsActions(onProfile = { opened += it }, onAddProfile = { added += it })) }

        focused().assert(hasText("Maya"))
        focused().assert(hasText("Watching now"))
        press(Key.DirectionCenter)
        check(opened == listOf("maya")) { "Opened $opened" }

        press(Key.DirectionDown)
        focused().assert(hasText("PIN"))
        press(Key.DirectionDown, times = 3)
        focused().assert(hasText("Add Profile"))
        press(Key.DirectionCenter)
        press(Key.L)
        press(Key.E)
        press(Key.O)
        // OK moves to the keyboard, Next from the only field to Add Profile.
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 4)
        press(Key.DirectionRight, times = 8)
        focused().assert(hasText("Next"))
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)

        check(added == listOf("leo")) { "Added $added" }
    }

    @Test fun aKidsProfileHasAges() {
        val values = SettingsValues()
        screen { SettingsScreen(SettingsPage.Profile, values, SettingsActions(), profileId = "daniel") }
        compose.onNodeWithText("Ages").assertDoesNotExist()

        press(Key.DirectionDown, times = 2)
        focused().assert(hasText("Profile Type"))
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        focused().assert(hasText("Kids"))
        press(Key.DirectionCenter)

        check(values.index(ProfileSettings.key(ProfileSettings.TYPE, "daniel"), -1) == ProfileType.Kids.ordinal)
        compose.onNodeWithText("Ages").assertExists()
        compose.onNodeWithText("Ages 7 and under").assertExists()
    }

    @Test fun theFirstProfileStaysAStandardOneAndCantGo() {
        screen { SettingsScreen(SettingsPage.Profile, SettingsValues(), SettingsActions(), profileId = "maya") }

        compose.onNodeWithText("Profile Type").assertDoesNotExist()
        compose.onNodeWithText("Delete Profile").assertDoesNotExist()
        compose.onNodeWithText("Picture").assertExists()
    }

    @Test fun deleteProfileAsksFirst() {
        val deleted = mutableListOf<String>()
        screen { SettingsScreen(SettingsPage.Profile, SettingsValues(), SettingsActions(onDeleteProfile = { deleted += it }), profileId = "kids") }

        // Name, Picture, Profile Type, Ages, PIN, Delete Profile.
        press(Key.DirectionDown, times = 5)
        focused().assert(hasText("Delete Profile"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("Delete Kids?").assertExists()
        focused().assert(hasText("Delete"))
        press(Key.DirectionCenter)

        check(deleted == listOf("kids")) { "Deleted $deleted" }
    }

    @Test fun defaultProfileStartsWithWhoeverWatchedLast() {
        val values = SettingsValues()
        screen { SettingsScreen(SettingsPage.Profiles, values, SettingsActions()) }

        compose.onNodeWithText("Whoever Watched Last").assertExists()
        // Maya, Daniel, Kids, Guest, Add Profile, Ask Who's Watching, Default Profile.
        press(Key.DirectionDown, times = 6)
        focused().assert(hasText("Default Profile"))
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 2)
        focused().assert(hasText("Daniel"))
        press(Key.DirectionCenter)

        check(values.index(ProfileSettings.defaultKey(listOf("maya", "daniel", "kids", "guest")), -1) == 2)
    }
}
