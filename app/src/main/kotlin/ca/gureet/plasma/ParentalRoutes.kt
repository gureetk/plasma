// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import ca.gureet.plasma.core.data.ProfileKind
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.model.RecoveryCode
import ca.gureet.plasma.feature.profiles.ParentalPinScreen
import ca.gureet.plasma.feature.profiles.RecoveryCodeEntry
import ca.gureet.plasma.feature.profiles.RecoveryCodeScreen
import ca.gureet.plasma.feature.profiles.PinRequest
import ca.gureet.plasma.feature.profiles.PinTries
import ca.gureet.plasma.feature.profiles.UnlockFor
import ca.gureet.plasma.feature.settings.SettingsPage
import ca.gureet.plasma.feature.settings.SettingsValues
import ca.gureet.plasma.nav.ChoosePinRoute
import ca.gureet.plasma.nav.ForgotPinRoute
import ca.gureet.plasma.nav.UnlockChannelRoute
import kotlinx.coroutines.launch

/**
 * Whether Settings' [page] may show (brief 5.12). With Lock Settings on, or for a kids profile
 * (5.11), Settings asks for the PIN before it opens, and stays open until Home or the TV sleeps;
 * turned on with Settings open, it counts as opened. Parental Controls' own page asks for the PIN
 * on each visit once there is one. Until then the PIN shows here instead, and nothing until it's
 * known whether there's a PIN and who's watching.
 */
@Composable
fun settingsOpened(graph: AppGraph, settings: SettingsValues, page: SettingsPage, backLabel: String): Boolean {
    val parental = graph.parental
    val pinSet by parental.pinSet.collectAsState()
    // Known at once, so Settings shows in its first frame (docs/decisions.md, Snappier).
    val profile by graph.currentProfile.collectAsState()
    val known = pinSet ?: return false
    val watching = profile ?: return false
    val sleeps = parental.sleeps
    // A kids profile has no Settings without the parental PIN (5.11).
    val lockSettings = settings.isOn(SettingKeys.LOCK_SETTINGS, false) || watching.kind == ProfileKind.Kids
    val lockedAtOpen = remember(sleeps) { lockSettings }
    LaunchedEffect(lockSettings) {
        if (lockSettings && !lockedAtOpen) parental.settingsOpen = true
    }
    // Opened before there was a PIN, or with it: open for the rest of this visit.
    var parentalOpen by remember(sleeps) { mutableStateOf(!known) }
    when {
        known && lockSettings && lockedAtOpen && !parental.settingsOpen ->
            PinPrompt(graph, stringResource(R.string.pin_for_settings), backLabel) { parental.settingsOpen = true }
        known && page == SettingsPage.Parental && !parentalOpen && !parental.settingsOpen ->
            PinPrompt(graph, stringResource(R.string.pin_for_parental), backLabel) { parentalOpen = true }
        else -> return true
    }
    return false
}

/**
 * The parental PIN asked for [title], counting wrong tries wherever it's asked for. With a recovery
 * code there's Forgot PIN (#112); without, the hint says what's left: clearing Plasma's storage.
 */
@Composable
private fun PinPrompt(graph: AppGraph, title: String, backLabel: String, onRight: () -> Unit) {
    val forgot = forgotPin(graph, back = title)
    ParentalPinScreen(
        request = PinRequest.Prompt(title, if (forgot == null) stringResource(R.string.pin_forgot) else null),
        backLabel = backLabel,
        checkPin = graph.parental::check,
        onUnlocked = { onRight() },
        tries = graph.parental.tries,
        onForgot = forgot,
    )
}

/**
 * Reset Settings' PIN (#159): with parental controls on and Settings not opened with the PIN, the
 * parental PIN comes before the settings go back to their defaults, since Age Limit and the rest
 * are settings too. Back goes back to Settings.
 */
@Composable
fun ResetSettingsPin(graph: AppGraph, backLabel: String, onRight: () -> Unit, onBack: () -> Unit) {
    PinPrompt(graph, stringResource(R.string.pin_for_reset), backLabel, onRight)
    BackHandler(onBack = onBack)
}

/** Whether Reset Settings asks for the parental PIN first (#159): there's one, and Settings wasn't opened with it. */
fun resetNeedsPin(graph: AppGraph): Boolean = graph.parental.pinSet.value == true && !graph.parental.settingsOpen

/**
 * Forgot PIN on a PIN screen (#112) while the PIN, the parental one or [profile]'s own, has a
 * recovery code; null leaves it out. [back] names the screen, for Forgot PIN's back link.
 */
@Composable
fun forgotPin(graph: AppGraph, profile: String? = null, back: String? = null): (() -> Unit)? {
    val has by remember(graph, profile) { graph.data.settings.hasRecovery(profile) }.collectAsState(false)
    return if (has) ({ graph.openForgotPin?.invoke(ForgotPinRoute(profile, back)) }) else null
}

/**
 * Forgot PIN (the owner's #112): the PIN's recovery code, counting wrong tries as the PIN does,
 * then a new PIN twice, then its new code. Back on the PIN screen it came from, the new PIN opens
 * what that asked for.
 */
@Composable
fun ForgotPin(graph: AppGraph, settings: SettingsValues, route: ForgotPinRoute, backLabel: String, onDone: () -> Unit) {
    var right by remember { mutableStateOf(false) }
    val tries = if (route.profile == null) graph.parental.tries else remember { PinTries() }
    if (!right) {
        RecoveryCodeEntry(
            backLabel = backLabel,
            checkCode = { code -> graph.data.settings.isRecovery(code, route.profile) },
            onRight = { right = true },
            tries = tries,
        )
        return
    }
    ChoosePin(graph, settings, ChoosePinRoute(change = true, profile = route.profile), backLabel, onDone)
}

/**
 * A new recovery code for the parental PIN (#112), from Settings › Parental Controls, which is
 * behind the PIN: made, kept and shown once, in place of the one it had.
 */
@Composable
fun NewRecoveryCode(graph: AppGraph, onDone: () -> Unit) {
    val code = remember { RecoveryCode.new() }
    LaunchedEffect(code) { graph.data.settings.setRecovery(code) }
    RecoveryCodeScreen(code, onDone)
}

/**
 * Choosing the parental PIN (brief 5.12): four digits, then the same four again; when they
 * differ, from the first again. Then what asked for one first is locked with it: the channel
 * [ChoosePinRoute.lockChannel], or Settings. With [ChoosePinRoute.profile], it's that profile's
 * own PIN (5.11). With [ChoosePinRoute.verify], the PIN there is now comes first, counting wrong
 * tries as anywhere: first-time setup's Change PIN isn't behind it as Parental Controls is (#85).
 */
@Composable
fun ChoosePin(graph: AppGraph, settings: SettingsValues, route: ChoosePinRoute, backLabel: String, onDone: () -> Unit) {
    var verified by remember { mutableStateOf(!route.verify) }
    if (!verified) {
        PinPrompt(graph, stringResource(R.string.pin_for_parental), backLabel) { verified = true }
        return
    }
    var first by remember { mutableStateOf<String?>(null) }
    var differed by remember { mutableStateOf(false) }
    // The new PIN's recovery code (#112), shown once it's chosen.
    var code by remember { mutableStateOf<String?>(null) }
    code?.let { made ->
        RecoveryCodeScreen(made, onDone)
        return
    }
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
                    // With a recovery code of its own, shown next (#112).
                    val made = RecoveryCode.new()
                    graph.scope.launch {
                        // A profile's own PIN (5.11), or the parental one.
                        val profile = route.profile
                        if (profile != null) {
                            graph.data.profiles.setPin(profile, pin)
                        } else {
                            graph.parental.setPin(pin)
                            route.lockChannel?.let { graph.data.channels.setLocked(it, true) }
                        }
                        graph.data.settings.setRecovery(made, profile)
                    }
                    if (route.lockSettings) {
                        // Settings is open already, as it is when Lock Settings is turned on in it.
                        graph.parental.settingsOpen = true
                        settings.set(SettingKeys.LOCK_SETTINGS, true)
                    }
                    code = made
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
    val title = stringResource(R.string.pin_to_unlock_channel, name)
    ParentalPinScreen(
        request = PinRequest.Prompt(title),
        backLabel = backLabel,
        checkPin = graph.parental::check,
        onUnlocked = {
            graph.scope.launch { graph.data.channels.setLocked(channelId, false) }
            onDone()
        },
        tries = graph.parental.tries,
        onForgot = forgotPin(graph, back = title),
    )
}

/**
 * Enter PIN on a locked movie or series category (5.12): it opens for an hour, or until the TV
 * sleeps when Unlock For says so.
 */
@Composable
fun UnlockCategory(graph: AppGraph, categoryId: String, name: String, backLabel: String, onDone: () -> Unit) {
    val title = stringResource(R.string.pin_to_open_category, name)
    ParentalPinScreen(
        request = PinRequest.Prompt(title),
        backLabel = backLabel,
        checkPin = graph.parental::check,
        onUnlocked = {
            graph.unlockCategory(categoryId, graph.unlockFor)
            onDone()
        },
        tries = graph.parental.tries,
        onForgot = forgotPin(graph, back = title),
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
