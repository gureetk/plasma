// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ca.gureet.plasma.core.model.QrMatrix
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

    @Test fun advanced() {
        show()
        press(Key.DirectionDown, times = 2)
        focused().assert(hasText("Advanced"))
        press(Key.DirectionCenter)
        capture("21-add-a-source-advanced")
    }

    /** Down until [label] has focus, through the keyboard. */
    private fun pressDownTo(label: String) {
        repeat(10) {
            if (runCatching { focused().assert(hasText(label)) }.isSuccess) return
            press(Key.DirectionDown)
        }
        focused().assert(hasText(label))
    }

    /** Down from the portal's address to the Advanced row, and OK. */
    private fun openAdvanced() {
        press(Key.DirectionDown, times = 2)
        focused().assert(hasText("Advanced"))
        press(Key.DirectionCenter)
    }

    @Test fun advancedHasThePortalsBoxAndComesBackWithBack() {
        var sent: AddSourceForm? = null
        screen {
            AddSourceScreen(
                AddSourceSamples.state,
                AddSourceActions(
                    onConnect = { form ->
                        sent = form
                        ConnectOutcome.Failed(ConnectProblem.WrongLogin)
                    },
                ),
            )
        }
        openAdvanced()
        compose.onNodeWithText("Advanced").assertExists()
        compose.onNodeWithContentDescription("STB model, MAG250").assertIsFocused()

        // A choice opens its sheet, on the right.
        press(Key.DirectionCenter)
        focused().assert(hasText("MAG250"))
        press(Key.DirectionDown, times = 3)
        focused().assert(hasText("MAG254"))
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("STB model, MAG254").assertIsFocused()

        // The serial number is typed, with capitals.
        press(Key.DirectionRight, times = 2)
        compose.onNodeWithContentDescription("Serial number, Made from the MAC").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 3)
        focused().assert(hasText("ABC"))
        press(Key.DirectionCenter)
        press(Key.DirectionRight, times = 4)
        focused().assert(hasText("V"))
        press(Key.DirectionCenter)
        press(Key.One)
        compose.onNodeWithContentDescription("Serial number, V1").assertExists()

        // Back returns to the portal's fields, on Advanced, which sums it up.
        press(Key.Back)
        compose.onNodeWithText("MAG254 · serial · time zone").assertExists()
        focused().assert(hasText("Advanced"))
        compose.onNodeWithContentDescription("Portal URL, http://portal.example.net/c/").assertExists()

        // Connect sends what's set.
        pressDownTo("Connect")
        press(Key.DirectionCenter)
        check(sent?.advanced == mapOf(AdvancedFields.STB_MODEL to "MAG254", AdvancedFields.SERIAL to "V1")) { "Sent ${sent?.advanced}" }
    }

    @Test fun aPhoneSendingWhileAdvancedIsOpenKeepsWhatsSetThere() {
        var sent by mutableStateOf<SentFromPhone?>(null)
        var connected: AddSourceForm? = null
        screen {
            AddSourceScreen(
                AddSourceSamples.state,
                AddSourceActions(
                    onConnect = { form ->
                        connected = form
                        ConnectOutcome.Failed(ConnectProblem.MacRefused)
                    },
                ),
                sent = sent,
            )
        }
        openAdvanced()
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        focused().assert(hasText("MAG200"))
        press(Key.DirectionCenter)

        sent = SentFromPhone(1, SourceType.Stalker, mapOf("name" to "Home Portal", "portal" to "http://portal.example.net/c/", "mac" to "00:1A:79:12:34:56"))
        compose.waitForIdle()

        // The portal's fields again, with Connect next.
        compose.onNodeWithText("Received from your phone").assertExists()
        compose.onNodeWithText("MAG200 · serial · time zone").assertExists()
        focused().assert(hasText("Connect"))
        press(Key.DirectionCenter)
        check(connected?.advanced == mapOf(AdvancedFields.STB_MODEL to "MAG200")) { "Connected with ${connected?.advanced}" }
    }

    @Test fun theKeyboardHasCapitalsAndSymbols() {
        show()
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 3)
        focused().assert(hasText("ABC"))
        press(Key.DirectionCenter)
        focused().assert(hasText("#+="))
        compose.onNodeWithText("Q").assertExists()
        press(Key.DirectionCenter)
        focused().assert(hasText("abc"))
        press(Key.DirectionUp, times = 2)
        focused().assert(hasText("!"))
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Portal URL, http://portal.example.net/c/!").assertExists()
    }

    @Test fun editShowsTheSourcesAdvancedAndKeepsItsPassword() {
        var sent: AddSourceForm? = null
        val state = AddSourceState.edit(
            SourceType.Stalker, "Home Portal", "http://portal.example.net/c/", mac = "00:1A:79:AA:BB:CC",
            advanced = mapOf(AdvancedFields.TIME_ZONE to "Europe/London", AdvancedFields.LOGIN to "family"),
        )
        screen {
            AddSourceScreen(
                state,
                AddSourceActions(
                    onConnect = { form ->
                        sent = form
                        ConnectOutcome.Failed(ConnectProblem.WrongLogin)
                    },
                ),
            )
        }
        openAdvanced()
        compose.onNodeWithContentDescription("Time zone, Europe/London").assertExists()
        compose.onNodeWithContentDescription("Login, family").assertExists()
        compose.onNodeWithContentDescription("Password, Leave empty to keep it").assertExists()
        press(Key.Back)
        focused().assert(hasText("Advanced"))
        pressDownTo("Save")
        press(Key.DirectionCenter)
        check(sent?.advanced == mapOf(AdvancedFields.TIME_ZONE to "Europe/London", AdvancedFields.LOGIN to "family")) { "Sent ${sent?.advanced}" }
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

    @Test fun aSourceSentFromAPhoneFillsTheFormIn() {
        var sent by mutableStateOf<SentFromPhone?>(null)
        var connected: AddSourceForm? = null
        val qr = QrMatrix(21, BooleanArray(21 * 21) { it % 3 == 0 })
        screen {
            AddSourceScreen(
                AddSourceState.blank(SourceType.Stalker, primary = false, deviceMac = "00:1A:79:12:34:56"),
                AddSourceActions(
                    onConnect = { form ->
                        connected = form
                        ConnectOutcome.Failed(ConnectProblem.WrongLogin)
                    },
                ),
                phone = PhonePairing.Ready("192.168.1.42:8484", "7KQ4MZ", qr),
                sent = sent,
            )
        }
        compose.onNodeWithText("7KQ 4MZ").assertExists()

        sent = SentFromPhone(
            1,
            SourceType.Xtream,
            mapOf("name" to "Backup Line", "server" to "http://line.example.org:8080", "username" to "living-room", "password" to "s3cret"),
        )
        compose.waitForIdle()

        // The phone's kind of source, its fields, and Connect next.
        compose.onNodeWithText("Received from your phone").assertExists()
        compose.onNodeWithContentDescription("Server URL, http://line.example.org:8080").assertExists()
        focused().assert(hasText("Connect"))
        press(Key.DirectionCenter)
        check(connected == AddSourceForm(SourceType.Xtream, "Backup Line", "http://line.example.org:8080", "living-room", "s3cret", primary = false)) {
            "Connected with $connected"
        }

        // A portal sent without a MAC address keeps this TV's.
        sent = SentFromPhone(2, SourceType.Stalker, mapOf("name" to "Home Portal", "portal" to "http://portal.example.net/c/"))
        compose.waitForIdle()
        compose.onNodeWithContentDescription("MAC address, 00:1A:79:12:34:56, Made for this TV · change").assertExists()
    }

    @Test fun aTvOffTheNetworkSaysSoInsteadOfACode() {
        screen { AddSourceScreen(AddSourceSamples.state, AddSourceActions(), phone = PhonePairing.Unavailable) }

        compose.onNodeWithText("Connect this TV to your Wi-Fi or Ethernet first, so your phone can reach it.").assertExists()
        compose.onNodeWithText("7KQ 4MZ").assertDoesNotExist()
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
