// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.SecondaryHeader
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.RemoteKeys
import java.util.Locale

/*
 * Settings › Remote & Buttons › Remote Key Test and Learn a Key (not drawn; the owner's decision of
 * 22 October 2026): what each key on a remote sends, and teaching Plasma a key it doesn't know.
 */

/** A key as a remote sent it. */
@Immutable
data class KeyPress(val code: Int, val scanCode: Int, val device: String?) {
    val id: String get() = RemoteKeys.idOf(code, scanCode)
}

/** What a learned key can act as: the keys Plasma uses that remotes most often lack. */
enum class KeyRole(val code: Int, @StringRes val label: Int) {
    ChannelUp(KeyEvent.KEYCODE_CHANNEL_UP, R.string.role_channel_up),
    ChannelDown(KeyEvent.KEYCODE_CHANNEL_DOWN, R.string.role_channel_down),
    Options(KeyEvent.KEYCODE_MENU, R.string.role_options),
    Info(KeyEvent.KEYCODE_INFO, R.string.role_info),
    Red(KeyEvent.KEYCODE_PROG_RED, R.string.role_red),
    Green(KeyEvent.KEYCODE_PROG_GREEN, R.string.role_green),
    Yellow(KeyEvent.KEYCODE_PROG_YELLOW, R.string.role_yellow),
    Blue(KeyEvent.KEYCODE_PROG_BLUE, R.string.role_blue),
}

/**
 * The remote key test: each key pressed shows with what Android calls it, its codes, the remote
 * it came from, and whether Plasma uses it. Every key stops here; Back leaves.
 *
 * @param learned keys taught in Learn a Key, by id, and the key code each acts as.
 */
@Composable
fun KeyTestScreen(learned: Map<String, Int>, backLabel: String?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var presses by remember { mutableStateOf(emptyList<KeyPress>()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focus.requestFocus()
    }
    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                    presses = (listOf(KeyPress(native.keyCode, native.scanCode, native.device?.name)) + presses).take(SHOWN)
                }
                if (native.keyCode == KeyEvent.KEYCODE_BACK && event.type == KeyEventType.KeyUp) onBack()
                true
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        SecondaryHeader(backLabel = backLabel, title = stringResource(R.string.key_test))
        Column(Modifier.offset(96.px, 150.px).width(1728.px)) {
            PlasmaText(stringResource(R.string.key_test_hint), PlasmaType.Body, color = PlasmaColors.Text2)
            Column(Modifier.padding(top = 32.px), verticalArrangement = Arrangement.spacedBy(6.px)) {
                if (presses.isEmpty()) {
                    PlasmaText(stringResource(R.string.key_test_empty), PlasmaType.Body, color = PlasmaColors.Text3)
                }
                presses.forEachIndexed { index, press ->
                    KeyRow(press, learned[press.id], newest = index == 0)
                }
            }
        }
    }
}

@Composable
private fun KeyRow(press: KeyPress, actsAs: Int?, newest: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(68.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.px),
    ) {
        Column(Modifier.weight(1f)) {
            PlasmaText(keyName(press.code, press.scanCode), plasmaStyle(25f, 600), color = if (newest) PlasmaColors.Text else PlasmaColors.Text2)
            PlasmaText(keyDetails(press), plasmaStyle(19f, 500, tabular = true), color = PlasmaColors.Text3)
        }
        val role = actsAs?.let { code -> KeyRole.entries.firstOrNull { it.code == code } }
        PlasmaText(
            when {
                role != null -> stringResource(R.string.key_acts_as, stringResource(role.label))
                actsAs != null -> stringResource(R.string.key_acts_as, keyName(actsAs, 0))
                press.code in USED -> stringResource(R.string.key_used)
                else -> stringResource(R.string.key_unused)
            },
            plasmaStyle(21f, 500),
            color = if (actsAs != null || press.code in USED) PlasmaColors.Text2 else PlasmaColors.Text3,
        )
    }
}

/**
 * Learn a Key: press a key, then choose what it does. Keys Plasma needs to get around, and the
 * TV's own (D-pad, OK, Back, Home, volume, power), stay as they are. OK on a learned key offers
 * Forget.
 */
@Composable
fun LearnKeyScreen(
    learned: Map<String, Int>,
    backLabel: String?,
    onLearn: (id: String, actsAs: Int) -> Unit,
    onForget: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf<KeyPress?>(null) }
    var choosingNumber by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf<String?>(null) }
    var justLearned by remember { mutableStateOf<Pair<KeyPress, Int>?>(null) }
    val sheetOpen = pressed != null || forgetting != null
    val root = remember { FocusRequester() }
    val firstRow = remember { FocusRequester() }
    val hadLearned = remember { learned.isNotEmpty() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (hadLearned) firstRow.requestFocus() else root.requestFocus()
    }

    fun learn(press: KeyPress, code: Int) {
        onLearn(press.id, code)
        justLearned = press to code
        pressed = null
        choosingNumber = false
    }

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                // The keys that get around Plasma, and the TV's own, work as usual.
                if (sheetOpen || native.keyCode in FIXED) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                    justLearned = null
                    pressed = KeyPress(native.keyCode, native.scanCode, native.device?.name)
                }
                true
            }
            // With nothing learned the screen itself has focus; Down reaches the learned keys.
            .focusProperties { down = if (learned.isNotEmpty()) firstRow else FocusRequester.Default }
            .focusRequester(root)
            .focusable(),
    ) {
        SecondaryHeader(backLabel = backLabel, title = stringResource(R.string.learn_key))
        Column(Modifier.offset(96.px, 150.px).width(1080.px)) {
            PlasmaText(stringResource(R.string.learn_key_prompt), PlasmaType.Section)
            PlasmaText(stringResource(R.string.learn_key_hint), PlasmaType.Body, Modifier.padding(top = 10.px), color = PlasmaColors.Text2)
            justLearned?.let { (press, code) ->
                PlasmaText(
                    stringResource(R.string.learn_key_done, keyName(press.code, press.scanCode), roleName(code)),
                    plasmaStyle(24f, 600),
                    Modifier.padding(top = 24.px),
                )
            }
            PlasmaText(stringResource(R.string.learned_keys), plasmaStyle(21f, 600), Modifier.padding(top = 48.px, bottom = 12.px), color = PlasmaColors.Text3)
            if (learned.isEmpty()) {
                PlasmaText(stringResource(R.string.no_learned_keys), PlasmaType.Body, color = PlasmaColors.Text3)
            }
            Column(Modifier.offset(x = (-22).px), verticalArrangement = Arrangement.spacedBy(4.px)) {
                learned.entries.sortedBy { it.key }.forEachIndexed { index, (id, code) ->
                    PlasmaSurface(
                        onClick = { forgetting = id },
                        modifier = Modifier.width(1124.px).height(64.px).then(if (index == 0) Modifier.focusRequester(firstRow) else Modifier),
                        style = FocusStyle.Row,
                        shape = PlasmaShapes.Row,
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        val focused = LocalFocused.current
                        Row(Modifier.padding(horizontal = 22.px), verticalAlignment = Alignment.CenterVertically) {
                            PlasmaText(nameOfId(id), plasmaStyle(24f, if (focused) 600 else 500), Modifier.weight(1f))
                            PlasmaText(
                                stringResource(R.string.key_acts_as, roleName(code)),
                                plasmaStyle(21f, 500),
                                color = if (focused) PlasmaColors.OnFocus3 else PlasmaColors.Text2,
                            )
                        }
                    }
                }
            }
        }

        // What the key just pressed does.
        val back = if (learned.isNotEmpty()) firstRow else root
        SheetHost(visible = pressed != null, returnFocus = back) {
            val press = pressed ?: return@SheetHost
            val name = keyName(press.code, press.scanCode)
            if (choosingNumber) {
                OptionsSheet(
                    title = stringResource(R.string.learn_key_choose, name),
                    subtitle = keyDetails(press),
                    onDismiss = { choosingNumber = false },
                    options = (0..9).map { digit -> SheetOption(digit.toString(), { learn(press, KeyEvent.KEYCODE_0 + digit) }) },
                )
            } else {
                OptionsSheet(
                    title = stringResource(R.string.learn_key_choose, name),
                    subtitle = keyDetails(press),
                    onDismiss = { pressed = null },
                    options = KeyRole.entries.map { role ->
                        SheetOption(stringResource(role.label), { learn(press, role.code) }, icon = PlasmaIcons.Remote, checked = learned[press.id] == role.code)
                    } + SheetOption(
                        stringResource(R.string.learn_key_number),
                        {
                            // The sheet closes before its choice runs: the key stays for the numbers.
                            pressed = press
                            choosingNumber = true
                        },
                        icon = PlasmaIcons.Hash,
                    ),
                )
            }
        }

        // OK on a learned key.
        SheetHost(visible = forgetting != null, returnFocus = back) {
            val id = forgetting ?: return@SheetHost
            OptionsSheet(
                title = nameOfId(id),
                subtitle = learned[id]?.let { stringResource(R.string.key_acts_as, roleName(it)) },
                onDismiss = { forgetting = null },
                options = listOf(SheetOption(stringResource(R.string.forget_key), { onForget(id) })),
            )
        }
    }
}

/** "Channel Up", "Red", "5", or what a learned key acts as when it isn't a role. */
@Composable
private fun roleName(code: Int): String =
    KeyRole.entries.firstOrNull { it.code == code }?.let { stringResource(it.label) } ?: keyName(code, 0)

@Composable
private fun nameOfId(id: String): String = when {
    id.startsWith("k") -> keyName(id.drop(1).toIntOrNull() ?: RemoteKeys.UNKNOWN, 0)
    else -> keyName(RemoteKeys.UNKNOWN, id.drop(1).toIntOrNull() ?: 0)
}

/** What a key is called: Plasma's name for the keys it uses, otherwise Android's. */
@Composable
internal fun keyName(code: Int, scanCode: Int): String {
    NAMES[code]?.let { return stringResource(it) }
    return when (code) {
        RemoteKeys.UNKNOWN -> stringResource(R.string.key_unknown, scanCode)
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> (code - KeyEvent.KEYCODE_0).toString()
        else -> {
            val android = KeyEvent.keyCodeToString(code)
            // Android names the keys it knows ("KEYCODE_TV_INPUT"); the others are only a number.
            if (android.startsWith(PREFIX)) {
                android.removePrefix(PREFIX).replace('_', ' ').lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
            } else {
                stringResource(R.string.key_number, code)
            }
        }
    }
}

/** "KEYCODE_CHANNEL_UP · code 166 · scan 402 · Living Room Remote" */
@Composable
private fun keyDetails(press: KeyPress): String = listOfNotNull(
    KeyEvent.keyCodeToString(press.code),
    stringResource(R.string.key_code, press.code),
    stringResource(R.string.key_scan, press.scanCode),
    press.device,
).joinToString(" · ")

private const val SHOWN = 10
private const val PREFIX = "KEYCODE_"

/** Plasma's names for the keys it knows. */
private val NAMES = mapOf(
    KeyEvent.KEYCODE_DPAD_UP to R.string.key_up,
    KeyEvent.KEYCODE_DPAD_DOWN to R.string.key_down,
    KeyEvent.KEYCODE_DPAD_LEFT to R.string.key_left,
    KeyEvent.KEYCODE_DPAD_RIGHT to R.string.key_right,
    KeyEvent.KEYCODE_DPAD_CENTER to R.string.key_ok,
    KeyEvent.KEYCODE_ENTER to R.string.key_ok,
    KeyEvent.KEYCODE_BACK to R.string.key_back,
    KeyEvent.KEYCODE_MENU to R.string.role_options,
    KeyEvent.KEYCODE_INFO to R.string.role_info,
    KeyEvent.KEYCODE_CHANNEL_UP to R.string.role_channel_up,
    KeyEvent.KEYCODE_CHANNEL_DOWN to R.string.role_channel_down,
    KeyEvent.KEYCODE_PROG_RED to R.string.role_red,
    KeyEvent.KEYCODE_PROG_GREEN to R.string.role_green,
    KeyEvent.KEYCODE_PROG_YELLOW to R.string.role_yellow,
    KeyEvent.KEYCODE_PROG_BLUE to R.string.role_blue,
    KeyEvent.KEYCODE_MEDIA_REWIND to R.string.key_rewind,
)

/** The keys Plasma acts on (brief 3.6 and 3.7). */
private val USED: Set<Int> = buildSet {
    addAll(NAMES.keys)
    addAll(KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9)
    addAll(KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9)
    add(KeyEvent.KEYCODE_NUMPAD_ENTER)
    add(KeyEvent.KEYCODE_BUTTON_A)
    add(KeyEvent.KEYCODE_ESCAPE)
}

/** Keys that stay as they are: getting around Plasma, and the TV's own. */
private val FIXED = setOf(
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A,
    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_HOME,
    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER,
)
