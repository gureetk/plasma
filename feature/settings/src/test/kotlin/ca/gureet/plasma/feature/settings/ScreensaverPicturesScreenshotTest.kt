// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Launcher & Home › Screensaver Pictures (#170): Plasma's pictures or your photos, where they come
 * from, their order and how long each shows. No board draws it; it follows board 32's pattern.
 */
class ScreensaverPicturesScreenshotTest : ScreenshotTest() {
    private val photos = SettingsSamples.facts.copy(screensaverPhotos = "Pictures · This TV")

    @Test fun yourPhotosFromAFolderOnTheTv() {
        val values = SettingsValues(mapOf(SettingKeys.SCREENSAVER_PICTURES to 1))
        screen { SettingsScreen(SettingsPage.ScreensaverPictures, values, SettingsActions(), facts = photos, initialItem = "photos-from") }
        compose.onNodeWithContentDescription("Photos From, Pictures · This TV").assertIsFocused()
        capture("22-settings-screensaver-pictures")
    }

    @Test fun plasmasPicturesComeBackWithOk() {
        val values = SettingsValues(mapOf(SettingKeys.SCREENSAVER_PICTURES to 1))
        screen { SettingsScreen(SettingsPage.ScreensaverPictures, values, SettingsActions(), facts = photos, initialItem = "plasma") }

        press(Key.DirectionCenter)

        assertEquals(0, values.index(SettingKeys.SCREENSAVER_PICTURES, 0))
    }

    @Test fun aFolderIsChosenOnTheTv() {
        val values = SettingsValues()
        val used = mutableListOf<String>()
        val folders = object : PhotoFolders by PhotoFolders.Sample {
            override fun use(drive: PhotoDrive, path: String) {
                used += path
            }
        }
        screen { SettingsScreen(SettingsPage.ScreensaverPictures, values, SettingsActions(photoFolders = folders), initialItem = "photos") }

        // Your Photos without a folder asks where they are.
        press(Key.DirectionCenter)
        compose.onNodeWithText("Where are your photos?").assertExists()
        focused().assert(hasText("On This TV or a USB Drive"))
        press(Key.DirectionCenter)

        // This TV and the USB drive, then a folder with Use This Folder first.
        compose.onNodeWithText("Choose where your photos are.").assertExists()
        focused().assert(hasText("This TV"))
        press(Key.DirectionCenter)
        focused().assert(hasText("Use This Folder"))
        pressDownTo("Pictures")
        press(Key.DirectionCenter)
        compose.onNodeWithText("This TV › Pictures").assertExists()
        compose.onNodeWithText("128 photos here").assertExists()
        capture("22-settings-screensaver-photos-folder")

        // Using it chooses Your Photos with it.
        press(Key.DirectionCenter)
        assertEquals(listOf("/storage/emulated/0/Pictures"), used)
        assertEquals(1, values.index(SettingKeys.SCREENSAVER_PICTURES, 0))
    }

    private fun pressDownTo(label: String) {
        repeat(10) {
            if (runCatching { focused().assert(hasText(label)) }.isSuccess) return
            press(Key.DirectionDown)
        }
        focused().assert(hasText(label))
    }
}
