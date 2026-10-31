// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Multiview against board 12 (docs/design/screens), and its D-pad paths. */
class MultiviewTest : ScreenshotTest() {
    @Test fun multiview() {
        screen { MultiviewScreen(PlayerSamples.multiview, MultiviewActions()) }
        capture("12-multiview")
    }

    @Test fun soundFollowsFocus() {
        var fullScreen: String? = null
        screen { MultiviewScreen(PlayerSamples.multiview, MultiviewActions(onFullScreen = { fullScreen = it })) }
        compose.onNodeWithContentDescription("Arena Sports 1: Harbour City v Redmoor").assertIsFocused()

        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("Velocity Motor: Endurance 24: Night Stint").assertIsFocused()
        compose.onNode(hasText("Sound")).assertExists()
        press(Key.DirectionDown)
        compose.onNodeWithContentDescription("Wildframe 4K: Kingdoms of Ice, Episode 3").assertIsFocused()

        // Down again reaches the toolbar, on the current layout.
        press(Key.DirectionDown)
        focused().assert(hasText("4 Tiles"))
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        // Focus + 3: the toolbar keeps focus, now on the new layout.
        focused().assert(hasText("Focus + 3"))

        press(Key.DirectionRight, times = 3)
        focused().assert(hasText("Full Screen"))
        press(Key.DirectionCenter)
        check(fullScreen == "ch180") { "Full Screen opens the tile with the sound, got $fullScreen" }
    }

    @Test fun fewerTilesKeepTheOneHeardAndMoreBringBackTheOthers() {
        val channels = mutableListOf<List<String>>()
        val layouts = mutableListOf<MultiviewLayout>()
        screen { MultiviewScreen(PlayerSamples.multiview, MultiviewActions(onChannels = { channels += it }, onLayout = { layouts += it })) }
        // Northline News, under Arena Sports 1, has the sound.
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        focused().assert(hasText("4 Tiles"))
        press(Key.DirectionLeft, times = 2)
        focused().assert(hasText("2 Tiles"))
        press(Key.DirectionCenter)
        check(channels.last() == listOf("ch105", "ch101")) { "2 Tiles: ${channels.last()}" }
        compose.onNodeWithContentDescription("Velocity Motor: Endurance 24: Night Stint").assertDoesNotExist()
        focused().assert(hasText("2 Tiles"))

        // 3 Tiles: the first let go comes back, then the next.
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        check(channels.last() == listOf("ch105", "ch101", "ch107")) { "3 Tiles: ${channels.last()}" }
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        check(channels.last() == listOf("ch105", "ch101", "ch107", "ch180")) { "4 Tiles: ${channels.last()}" }

        // Picture in Picture: the tile heard fills the screen, the first other in the corner.
        press(Key.DirectionRight, times = 2)
        focused().assert(hasText("Picture in Picture"))
        press(Key.DirectionCenter)
        check(channels.last() == listOf("ch101", "ch105")) { "Picture in Picture: ${channels.last()}" }
        check(layouts == listOf(MultiviewLayout.TwoTiles, MultiviewLayout.ThreeTiles, MultiviewLayout.FourTiles, MultiviewLayout.PictureInPicture)) { "Layouts $layouts" }
    }

    @Test fun aLargerLayoutAddsTheChannelsAfterTheTilesInTheirList() {
        var channels: List<String>? = null
        val two = PlayerSamples.multiview.let { it.copy(tiles = it.tiles.take(2), layout = MultiviewLayout.TwoTiles) }
        screen { MultiviewScreen(two, MultiviewActions(onChannels = { channels = it })) }
        press(Key.DirectionDown)
        focused().assert(hasText("2 Tiles"))
        press(Key.DirectionRight, times = 2)
        press(Key.DirectionCenter)
        // Favorites: Arena Sports 1 and Velocity Motor, then Northline News and Meridian 24.
        check(channels == listOf("ch105", "ch107", "ch101", "ch102")) { "Channels $channels" }
    }

    @Test fun onlyTheLayoutsTheDecodersTakeAreOffered() {
        val two = PlayerSamples.multiview.let { it.copy(tiles = it.tiles.take(2), maxTiles = 2) }
        screen { MultiviewScreen(two, MultiviewActions()) }
        press(Key.DirectionDown)
        // It opens on 4 Tiles, which the TV can't take: 2 Tiles instead.
        focused().assert(hasText("2 Tiles"))
        compose.onNode(hasText("Picture in Picture")).assertExists()
        compose.onNode(hasText("3 Tiles")).assertDoesNotExist()
        compose.onNode(hasText("4 Tiles")).assertDoesNotExist()
        compose.onNode(hasText("Focus + 3")).assertDoesNotExist()
    }

    @Test fun twoTiles() {
        screen { MultiviewScreen(PlayerSamples.multiview.let { it.copy(tiles = it.tiles.take(2), layout = MultiviewLayout.TwoTiles) }, MultiviewActions()) }
        capture("12-multiview-two-tiles")
    }

    @Test fun changeChannelPicksFromTheLists() {
        var channels: List<String>? = null
        screen { MultiviewScreen(PlayerSamples.multiview, MultiviewActions(onChannels = { channels = it })) }
        press(Key.Menu)
        focused().assert(hasText("Change Channel"))
        press(Key.DirectionCenter)
        // Favorites, on the tile's own channel.
        focused().assert(hasContentDescription("Arena Sports 1", substring = true))
        // The picker's title, over the toolbar's button.
        compose.onAllNodes(hasText("Change Channel")).assertCountEquals(2)
        // Velocity Motor and Northline News are in other tiles: Down passes them by.
        press(Key.DirectionDown)
        focused().assert(hasContentDescription("Meridian 24", substring = true))
        press(Key.DirectionCenter)
        // The sound tile shows it now, and has focus again.
        focused().assert(hasContentDescription("Meridian 24", substring = true))
        check(channels == listOf("ch102", "ch107", "ch101", "ch180")) { "Channels $channels" }
    }

    @Test fun changeChannel() {
        screen { MultiviewScreen(PlayerSamples.multiview, MultiviewActions()) }
        press(Key.Menu)
        press(Key.DirectionCenter)
        capture("12-multiview-change-channel")
    }

    @Test fun aTileThatCantPlaySaysWhy() {
        val state = PlayerSamples.multiview.let { s ->
            s.copy(tiles = s.tiles.mapIndexed { i, tile -> if (i == 3) tile.copy(card = TileCard.Limit(2)) else tile })
        }
        screen { MultiviewScreen(state, MultiviewActions()) }
        compose.onNode(hasText("Your subscription allows 2 streams at a time.")).assertExists()
    }
}
