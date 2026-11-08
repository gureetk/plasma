// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.SpaceMono
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.component.SecondaryHeader
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.outline
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.fixtures.fx
import ca.gureet.plasma.core.model.QrMatrix

/** What Plasma is doing on Replace Google TV Home (brief 4.24), for the step in progress. */
sealed interface ReplaceStatus {
    /** Waiting on the step: for wireless debugging, for the code, or ready to replace. */
    data object Waiting : ReplaceStatus

    /** Pairing with the code from the phone, or replacing. */
    data object Working : ReplaceStatus

    /** The code from the phone didn't pair. */
    data object WrongCode : ReplaceStatus

    /** Pairing from the TV itself needs Android 12 or later: a computer can still do it. */
    data object NeedsComputer : ReplaceStatus

    /** Replacing stopped, with what the TV said; nothing it did can't be undone. */
    data class Failed(val output: String) : ReplaceStatus
}

/**
 * Where Replace Google TV Home has got to (brief 4.24), and what this TV's commands need: its
 * address and debugging ports, Plasma's Home activity, and the launcher packages to turn off.
 */
@Immutable
data class ReplaceHomeState(
    /** The step in progress: 1 wireless debugging, 2 pairing, 3 replacing; 4 when all are done. */
    val step: Int,
    val address: String,
    /** What wireless debugging shows, for the commands; null until Plasma knows. */
    val pairingPort: Int?,
    val pairingCode: String?,
    val connectPort: Int?,
    /** Plasma's Home activity, as `package/.Activity`. */
    val homeActivity: String,
    val launcherPackages: List<String>,
    /** The port of Plasma's page for phones (5.15). */
    val phonePort: Int,
    val status: ReplaceStatus = ReplaceStatus.Waiting,
    /** The phone page's QR code; null shows the board's (the sample world). */
    val qr: QrMatrix? = null,
    /** Home Button Override is on: once Plasma is Home, it offers to turn it off (4.23). */
    val overrideOn: Boolean = false,
)

/** What the steps' buttons do. */
class ReplaceHomeActions(
    val onOpenDeveloperOptions: () -> Unit = {},
    val onReplace: () -> Unit = {},
    val onTurnOverrideOff: () -> Unit = {},
    val onDone: () -> Unit = {},
)

/** Board 24: debugging is on, and Plasma waits for the pairing code from the phone. */
object ReplaceHomeSamples {
    val state = ReplaceHomeState(
        step = 2,
        address = SettingsSamples.facts.address,
        pairingPort = 37115,
        pairingCode = "482913",
        connectPort = 41285,
        homeActivity = "ca.gureet.plasma/.MainActivity",
        launcherPackages = listOf("com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith"),
        phonePort = SettingsSamples.facts.phonePort,
    )
}

private enum class StepState { Done, Current, Later }

/**
 * Replace Google TV Home (board 24): three steps to pair Plasma with the TV's own debugging and
 * make it the Home app, the same commands for a computer, and a QR code for typing the pairing
 * code on a phone. The steps tick themselves as Plasma sees them done; the step in progress has
 * the button, and focus.
 */
@Composable
fun ReplaceHomeScreen(state: ReplaceHomeState, actions: ReplaceHomeActions, modifier: Modifier = Modifier) {
    val button = remember { FocusRequester() }
    // The button moves with the step, and focus with it.
    LaunchedEffect(state.step) {
        withFrameNanos { }
        button.requestFocus()
    }
    fun stateOf(step: Int) = when {
        step < state.step -> StepState.Done
        step == state.step -> StepState.Current
        else -> StepState.Later
    }

    Box(modifier.fillMaxSize()) {
        SecondaryHeader(stringResource(R.string.home_screen), stringResource(R.string.replace_home))

        Column(Modifier.offset(96.px, 148.px).width(1060.px)) {
            PlasmaText(stringResource(R.string.replace_intro), plasmaStyle(23f, 400), Modifier.padding(horizontal = 4.px), color = PlasmaColors.Text2)
            Column(Modifier.padding(top = 20.px), verticalArrangement = Arrangement.spacedBy(10.px)) {
                StepCard(1, stringResource(R.string.step_debugging), stringResource(R.string.step_debugging_text), stateOf(1)) {
                    StepButton(stringResource(R.string.open_developer_options), actions.onOpenDeveloperOptions, button, Status(stringResource(R.string.waiting_for_debugging), Tone.Idle))
                }
                StepCard(2, stringResource(R.string.step_pair), stringResource(R.string.step_pair_text), stateOf(2)) {
                    StepButton(stringResource(R.string.open_developer_options), actions.onOpenDeveloperOptions, button, pairingStatus(state.status))
                }
                StepCard(3, stringResource(R.string.step_replace), stringResource(R.string.step_replace_text), stateOf(3)) {
                    StepButton(stringResource(R.string.replace_home), actions.onReplace, button, replaceStatus(state.status))
                    (state.status as? ReplaceStatus.Failed)?.let { failed ->
                        PlasmaText(failed.output.trim(), plasmaStyle(18f, 400, lineHeight = 1.5f, family = SpaceMono), Modifier.padding(top = 12.px), color = PlasmaColors.TextSoft2, maxLines = 3)
                    }
                }
            }
            if (state.step > LAST_STEP) {
                Row(Modifier.padding(top = 22.px), verticalAlignment = Alignment.CenterVertically) {
                    if (state.overrideOn) {
                        StepButton(stringResource(R.string.turn_off_override), actions.onTurnOverrideOff, button, Status(stringResource(R.string.replace_done), Tone.Ok))
                    } else {
                        StepButton(stringResource(R.string.done), actions.onDone, button, Status(stringResource(R.string.replace_done), Tone.Ok))
                    }
                }
            }
            PlasmaText(
                stringResource(R.string.prefer_computer),
                plasmaStyle(21f, 600),
                Modifier.padding(start = 4.px, top = 24.px, bottom = 10.px),
                color = PlasmaColors.Text3,
            )
            Commands(state)
            PlasmaText(
                highlighted(
                    stringResource(if (state.launcherPackages.size == 2) R.string.undo_with else R.string.undo_with_any),
                    "pm enable",
                    SpanStyle(fontFamily = SpaceMono, fontSize = plasmaStyle(18f).fontSize, color = PlasmaColors.TextSoft),
                ),
                plasmaStyle(20f, 500),
                Modifier.padding(start = 4.px, top = 12.px),
                color = PlasmaColors.Text3,
                maxLines = 2,
            )
        }

        PhoneCard(
            title = stringResource(R.string.phone_code_title),
            text = stringResource(R.string.phone_code_text),
            qr = state.qr?.let { QrSource.Code(it) } ?: QrSource.Fixture(fx("pair_qr_adb")),
            modifier = Modifier.offset(1236.px, 148.px),
        ) {
            AddressLine(stringResource(R.string.open_on_phone), "${state.address}:${state.phonePort}", Modifier.padding(top = 24.px))
            PhoneCardNote(stringResource(R.string.adb_note))
        }
    }
}

/** Pairing's line: waiting for the code, pairing, or why it didn't. */
@Composable
private fun pairingStatus(status: ReplaceStatus): Status = when (status) {
    ReplaceStatus.Working -> Status(stringResource(R.string.pairing_now), Tone.Idle)
    ReplaceStatus.WrongCode -> Status(stringResource(R.string.pairing_wrong), Tone.Warn)
    ReplaceStatus.NeedsComputer -> Status(stringResource(R.string.pairing_needs_computer), Tone.Warn)
    else -> Status(stringResource(R.string.waiting_for_code), Tone.Idle)
}

/** Replacing's line: paired and ready, replacing, or where it stopped. */
@Composable
private fun replaceStatus(status: ReplaceStatus): Status = when (status) {
    ReplaceStatus.Working -> Status(stringResource(R.string.replacing), Tone.Idle)
    is ReplaceStatus.Failed -> Status(stringResource(R.string.replace_failed), Tone.Warn)
    else -> Status(stringResource(R.string.replace_ready), Tone.Ok)
}

private const val LAST_STEP = 3

/** The step's button, and next to it what Plasma is waiting for or doing. */
@Composable
private fun StepButton(label: String, onClick: () -> Unit, focus: FocusRequester, status: Status) {
    Row(Modifier.padding(top = 14.px), verticalAlignment = Alignment.CenterVertically) {
        PlasmaButton(
            label,
            onClick = onClick,
            modifier = Modifier.focusRequester(focus),
            height = 56.px,
            textSizePx = 22f,
            pivot = TransformOrigin(0f, 0.5f),
        )
        Box(Modifier.padding(start = 28.px)) { StatusDotLine(status.text, status.tone) }
    }
}

/** A step: its number (or a tick when done), what to do, and for the step in progress its button. */
@Composable
private fun StepCard(number: Int, title: String, text: String, state: StepState, current: @Composable ColumnScope.() -> Unit) {
    val ring = PlasmaColors.white(0.5f)
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (state == StepState.Later) 0.55f else 1f)
            .then(
                if (state == StepState.Current) {
                    // A 2 px ring just outside the card, as the board's box-shadow draws it.
                    Modifier.drawBehind {
                        val w = 2.px.toPx()
                        drawRoundRect(
                            ring,
                            topLeft = Offset(-w / 2, -w / 2),
                            size = Size(size.width + w, size.height + w),
                            cornerRadius = CornerRadius(14.px.toPx() + w / 2),
                            style = Stroke(w),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .background(if (state == StepState.Current) PlasmaColors.Surface2 else PlasmaColors.Surface1, PlasmaShapes.Row)
            .padding(horizontal = 26.px, vertical = 20.px),
        horizontalArrangement = Arrangement.spacedBy(22.px),
    ) {
        StepBadge(number, state)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.px)) {
            PlasmaText(title, plasmaStyle(26f, 600))
            PlasmaText(
                text,
                plasmaStyle(21f, 500, lineHeight = 1.4f),
                color = if (state == StepState.Current) PlasmaColors.TextSoft else PlasmaColors.Text2,
                maxLines = 3,
            )
            if (state == StepState.Current) current()
        }
        if (state == StepState.Done) {
            PlasmaText(stringResource(R.string.step_done), plasmaStyle(21f, 600), color = PlasmaColors.OkText)
        }
    }
}

@Composable
private fun StepBadge(number: Int, state: StepState) {
    val modifier = Modifier.size(44.px)
    when (state) {
        StepState.Done -> Box(modifier.background(PlasmaColors.Ok, CircleShape), contentAlignment = Alignment.Center) {
            PlasmaIcon(PlasmaIcons.Check, 24.px, tint = PlasmaColors.OnFocus, stroke = 3f)
        }
        StepState.Current -> Box(modifier.background(PlasmaColors.Text, CircleShape), contentAlignment = Alignment.Center) {
            PlasmaText(number.toString(), plasmaStyle(22f, 700), color = PlasmaColors.OnFocus)
        }
        StepState.Later -> Box(modifier.outline(2.px, PlasmaColors.white(0.35f), CircleShape), contentAlignment = Alignment.Center) {
            PlasmaText(number.toString(), plasmaStyle(22f, 700), color = PlasmaColors.TextSoft)
        }
    }
}

/** The same steps for a computer, with this TV's address, ports and packages (Space Mono 18 px). */
@Composable
private fun Commands(state: ReplaceHomeState) {
    val comment = stringResource(R.string.adb_comment)
    val commands = buildList {
        add("adb pair ${state.address}:${state.pairingPort ?: "<port>"} ${state.pairingCode ?: "<code>"}")
        add("adb connect ${state.address}:${state.connectPort ?: "<port>"}")
        add("adb shell cmd package set-home-activity ${state.homeActivity}")
        for (launcher in state.launcherPackages) add("adb shell pm disable-user --user 0 $launcher")
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(PlasmaColors.Code, RoundedCornerShape(12.px))
            .outline(1.px, PlasmaColors.white(0.06f), RoundedCornerShape(12.px))
            .padding(horizontal = 24.px, vertical = 16.px),
    ) {
        PlasmaText(
            buildAnnotatedString {
                withStyle(SpanStyle(color = PlasmaColors.Text4)) { append(comment) }
                for (line in commands) {
                    append("\n")
                    append(line)
                }
            },
            plasmaStyle(18f, 400, lineHeight = 1.55f, family = SpaceMono),
            color = PlasmaColors.TextSoft2,
            maxLines = 12,
        )
    }
}
