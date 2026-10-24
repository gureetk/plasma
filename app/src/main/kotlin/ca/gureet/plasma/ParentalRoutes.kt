// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.feature.profiles.ParentalPinScreen
import ca.gureet.plasma.feature.profiles.PinRequest
import ca.gureet.plasma.feature.profiles.PinTries
import ca.gureet.plasma.feature.profiles.UnlockFor
import ca.gureet.plasma.feature.settings.SettingsPage
import ca.gureet.plasma.feature.settings.SettingsValues
import ca.gureet.plasma.nav.ChoosePinRoute
import ca.gureet.plasma.nav.UnlockChannelRoute
import kotlinx.coroutines.launch

/**
 * Whether Settings' [page] may show (brief 5.12). With Lock Settings on, Settings asks for the PIN
 * before it opens, and stays open until Home or the TV sleeps; turned on with Settings open, it
 * counts as opened. Parental Controls' own page asks for the PIN on each visit once there is one.
 * Until then the PIN shows here instead, and nothing until it's known whether there's a PIN.
 */
@Composable
fun settingsOpened(graph: AppGraph, settings: SettingsValues, page: SettingsPage, backLabel: String): Boolean {
    val parental = graph.parental
    val pinSet by parental.pinSet.collectAsState()
    val known = pinSet ?: return false
    val sleeps = parental.sleeps
    val lockSettings = settings.isOn(SettingKeys.LOCK_SETTINGS, false)
    val lockedAtOpen = remember(sleeps) { lockSettings }
    LaunchedEffect(lockSettings) {
        if (lockSettings && !lockedAtOpen) parental.settingsOpen = true
    }
    // Opened before there was a PIN, or with it: open for the rest of this visit.
    var parentalOpen by remember(sleeps) { mutableStateOf(!known) }
    val forgot = stringResource(R.string.pin_forgot)
    when {
        known && lockSettings && lockedAtOpen && !parental.settingsOpen ->
            PinPrompt(graph, stringResource(R.string.pin_for_settings), forgot, backLabel) { parental.settingsOpen = true }
        known && page == SettingsPage.Parental && !parentalOpen && !parental.settingsOpen ->
            PinPrompt(graph, stringResource(R.string.pin_for_parental), forgot, backLabel) { parentalOpen = true }
        else -> return true
    }
    return false
}

/** The parental PIN asked for [title], counting wrong tries wherever it's asked for. */
@Composable
private fun PinPrompt(graph: AppGraph, title: String, hint: String?, backLabel: String, onRight: () -> Unit) {
    ParentalPinScreen(
        request = PinRequest.Prompt(title, hint),
        backLabel = backLabel,
        checkPin = graph.parental::check,
        onUnlocked = { onRight() },
        tries = graph.parental.tries,
    )
}

/**
 * Choosing the parental PIN (brief 5.12): four digits, then the same four again; when they
 * differ, from the first again. Then what asked for one first is locked with it: the channel
 * [ChoosePinRoute.lockChannel], or Settings.
 */
@Composable
fun ChoosePin(graph: AppGraph, settings: SettingsValues, route: ChoosePinRoute, backLabel: String, onDone: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var differed by remember { mutableStateOf(false) }
    // What was typed, kept for the step after: any four digits will do.
    val typed = remember { arrayOfNulls<String>(1) }
    val hint = stringResource(R.string.pin_choose_hint)
    val request = if (first == null) {
        PinRequest.Prompt(
            stringResource(if (route.change) R.string.choose_new_pin else R.string.choose_pin),
            hint = hint,
            warning = if (differed) stringResource(R.string.pins_differ) else null,
        )
    } else {
        PinRequest.Prompt(stringResource(R.string.pin_again), hint = hint)
    }
    ParentalPinScreen(
        request = request,
        backLabel = backLabel,
        checkPin = { pin ->
            typed[0] = pin
            true
        },
        onUnlocked = unlocked@{
            val pin = typed[0] ?: return@unlocked
            val chosen = first
            when {
                chosen == null -> {
                    first = pin
                    differed = false
                }
                chosen != pin -> {
                    first = null
                    differed = true
                }
                else -> {
                    graph.scope.launch {
                        graph.parental.setPin(pin)
                        route.lockChannel?.let { graph.data.channels.setLocked(it, true) }
                    }
                    if (route.lockSettings) {
                        // Settings is open already, as it is when Lock Settings is turned on in it.
                        graph.parental.settingsOpen = true
                        settings.set(SettingKeys.LOCK_SETTINGS, true)
                    }
                    onDone()
                }
            }
        },
        tries = remember { PinTries() },
    )
}

/** Unlock Channel in Live TV (5.12): with parental controls on, the PIN first. */
@Composable
fun UnlockChannel(graph: AppGraph, channelId: String, backLabel: String, onDone: () -> Unit) {
    val world by graph.world.collectAsState()
    val name = world?.byId?.get(channelId)?.name ?: return
    ParentalPinScreen(
        request = PinRequest.Prompt(stringResource(R.string.pin_to_unlock_channel, name)),
        backLabel = backLabel,
        checkPin = graph.parental::check,
        onUnlocked = {
            graph.scope.launch { graph.data.channels.setLocked(channelId, false) }
            onDone()
        },
        tries = graph.parental.tries,
    )
}

/**
 * Lock Channel or Unlock Channel from a channel's options in Live TV (5.12): locking with no PIN
 * yet chooses one first, and unlocking for good needs the PIN.
 */
fun lockChannel(nav: NavHostController, graph: AppGraph, channelId: String, locked: Boolean) {
    when (graph.parental.pinSet.value) {
        null -> Unit
        false -> if (locked) nav.navigate(ChoosePinRoute(lockChannel = channelId)) else graph.scope.launch { graph.data.channels.setLocked(channelId, false) }
        true -> if (locked) graph.scope.launch { graph.data.channels.setLocked(channelId, true) } else nav.navigate(UnlockChannelRoute(channelId))
    }
}

/** Unlock For's choice in Settings › Parental Controls (4.25), as the PIN screen offers it first. */
val AppGraph.unlockFor: UnlockFor get() = UnlockFor.entries.getOrElse(choices.unlockFor) { UnlockFor.Program }
