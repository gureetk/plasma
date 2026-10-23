// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Test

/** Add a Source against board 21, and typing into it with the keyboard and the remote. */
class AddSourceScreenshotTest : ScreenshotTest() {
    private var done = false

    private fun show() = screen { AddSourceScreen(AddSourceSamples.state, AddSourceActions(onDone = { done = true })) }

    @Test fun addASource() {
        show()
        capture("21-add-a-source")
    }

    @Test fun theKeyboardTypesIntoTheFieldBeingEdited() {
        show()
        compose.onNodeWithContentDescription("Portal URL, http://portal.example.net/c/").assertIsFocused()
        // OK on a field moves to the keyboard; its keys and the remote's number keys type there.
        press(Key.DirectionCenter)
        focused().assert(hasText("1"))
        press(Key.DirectionCenter)
        press(Key.Five)
        compose.onNodeWithContentDescription("Portal URL, http://portal.example.net/c/15").assertExists()

        // Down to the bottom row, then across to Next: the MAC address is next.
        press(Key.DirectionDown, times = 4)
        press(Key.DirectionRight, times = 8)
        focused().assert(hasText("Next"))
        press(Key.DirectionCenter)
        press(Key.DirectionUp, times = 4)
        press(Key.DirectionLeft, times = 3)
        focused().assert(hasText("7"))
        press(Key.DirectionCenter)
        // Changed, it isn't the one made for this TV any more.
        compose.onNodeWithContentDescription("MAC address, 00:1A:79:4C:2E:917").assertExists()
    }

    @Test fun eachKindOfSourceHasItsFields() {
        show()
        press(Key.DirectionUp, times = 2)
        focused().assert(hasText("Stalker Portal"))
        press(Key.DirectionRight)
        compose.onNodeWithText("Server URL").assertExists()
        compose.onNodeWithText("Username").assertExists()
        press(Key.DirectionRight)
        compose.onNodeWithText("Playlist URL").assertExists()
        compose.onNodeWithText("From the playlist").assertExists()
    }

    @Test fun nextFromTheLastFieldGoesToConnect() {
        show()
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 4)
        press(Key.DirectionRight, times = 8)
        // Next twice: to the MAC address, then past the last field to Connect.
        press(Key.DirectionCenter, times = 2)
        focused().assert(hasText("Connect"))
        press(Key.DirectionCenter)
        compose.onNodeWithText("Connected to Home Portal").assertExists()
        press(Key.DirectionCenter)
        check(done)
    }

    @Test fun aPortalConnectsWithThisTvsMacAddress() {
        var sent: AddSourceForm? = null
        val state = AddSourceState(
            type = SourceType.Stalker,
            values = mapOf(FIELD_NAME to "Home Portal", FIELD_PORTAL to "http://portal.example.net/c/", FIELD_MAC to "00:1A:79:12:34:56"),
            editing = FIELD_PORTAL,
            primary = false,
            deviceMac = "00:1A:79:12:34:56",
        )
        screen {
            AddSourceScreen(
                state,
                AddSourceActions(
                    onConnect = { form ->
                        sent = form
                        ConnectOutcome.Failed(ConnectProblem.MacRefused)
                    },
                ),
            )
        }
        compose.onNodeWithContentDescription("MAC address, 00:1A:79:12:34:56, Made for this TV · change").assertExists()

        // To the keyboard's Next, then past the MAC address to Connect.
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 4)
        press(Key.DirectionRight, times = 8)
        press(Key.DirectionCenter, times = 2)
        focused().assert(hasText("Connect"))
        press(Key.DirectionCenter)

        compose.onNodeWithText("The portal rejected this MAC address. Check it’s registered with your provider.").assertExists()
        check(sent == AddSourceForm(SourceType.Stalker, "Home Portal", "http://portal.example.net/c/", "", "", primary = false, mac = "00:1A:79:12:34:56")) { "Sent $sent" }
    }

    @Test fun anotherMacAddressIsntCalledThisTvs() {
        val state = AddSourceState.edit(SourceType.Stalker, "Home Portal", "http://portal.example.net/c/", mac = "00:1A:79:AA:BB:CC", deviceMac = "00:1A:79:12:34:56")
        screen { AddSourceScreen(state, AddSourceActions()) }

        compose.onNodeWithContentDescription("MAC address, 00:1A:79:AA:BB:CC").assertExists()
        compose.onNodeWithContentDescription("Portal URL, http://portal.example.net/c/").assertIsFocused()
    }

    @Test fun editStartsOnTheAddressAndKeepsThePasswordUnlessOneIsTyped() {
        var sent: AddSourceForm? = null
        val state = AddSourceState.edit(SourceType.Xtream, "Backup Line", "http://line.example.org:8080/", username = "living-room")
        screen {
            AddSourceScreen(
                state,
                AddSourceActions(
                    onConnect = { form ->
                        sent = form
                        ConnectOutcome.Connected("Backup Line", activeUntil = null, connections = 1, channels = 1_190)
                    },
                ),
            )
        }
        compose.onNodeWithText("Edit Backup Line").assertExists()
        compose.onNodeWithContentDescription("Server URL, http://line.example.org:8080/").assertIsFocused()
        // Its kind can't change, and Set as Primary is the source's own action.
        compose.onNodeWithText("Xtream Codes").assertDoesNotExist()
        compose.onNodeWithText("Primary source").assertDoesNotExist()
        compose.onNodeWithContentDescription("Password, Leave empty to keep it").assertExists()

        // To the keyboard's Next, then through the username and password to Save.
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 4)
        press(Key.DirectionRight, times = 8)
        press(Key.DirectionCenter, times = 3)
        focused().assert(hasText("Save"))
        press(Key.DirectionCenter)

        compose.onNodeWithText("Saved Backup Line").assertExists()
        check(sent == AddSourceForm(SourceType.Xtream, "Backup Line", "http://line.example.org:8080/", "living-room", password = "", primary = false)) { "Sent $sent" }
    }
}
