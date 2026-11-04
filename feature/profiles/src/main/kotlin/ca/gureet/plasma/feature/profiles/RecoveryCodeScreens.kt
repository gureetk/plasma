// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.profiles

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.GraphiteIconTile
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.component.SecondaryHeader
import ca.gureet.plasma.core.design.component.digit
import ca.gureet.plasma.core.design.focusRestoringGroup
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.RecoveryCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ca.gureet.plasma.core.design.R as DesignR

/**
 * A PIN's recovery code, shown once (the owner's #112), after a PIN is chosen or a new code made:
 * to write down for Forgot PIN on the PIN screen. Done, or Back, goes on, and it isn't shown again.
 */
@Composable
fun RecoveryCodeScreen(code: String, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val done = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        done.requestFocus()
    }
    BackHandler(onBack = onDone)
    Box(modifier.fillMaxSize().pinBackdrop()) {
        Column(Modifier.fillMaxSize().padding(top = 200.px), horizontalAlignment = Alignment.CenterHorizontally) {
            GraphiteIconTile(PlasmaIcons.Shield, 120.px, 30.px, 54.px, stroke = 1.6f)
            PlasmaText(stringResource(R.string.recovery_code_title), plasmaStyle(48f, 600, trackingPx = -1f), Modifier.padding(top = 36.px))
            Box(
                Modifier
                    .padding(top = 48.px)
                    .background(PlasmaColors.Surface1, PlasmaShapes.Row)
                    .padding(horizontal = 56.px, vertical = 26.px),
                contentAlignment = Alignment.Center,
            ) {
                PlasmaText(RecoveryCode.shown(code), plasmaStyle(64f, 600, trackingPx = 4f, tabular = true))
            }
            PlasmaText(
                stringResource(R.string.recovery_code_text),
                plasmaStyle(24f, 500, lineHeight = 1.4f),
                Modifier.padding(top = 40.px).widthIn(max = 1000.px),
                color = PlasmaColors.Text2,
                align = TextAlign.Center,
            )
            PlasmaButton(stringResource(R.string.done), onClick = onDone, modifier = Modifier.padding(top = 48.px).focusRequester(done))
        }
    }
}

/** The keys' rows: every character a code can have, in [RecoveryCode.ALPHABET]'s order, Delete last. */
private val CodeRows = RecoveryCode.ALPHABET.chunked(8)

/** The character a keyboard's letter or digit key types in a code, in capitals; null for others. */
private fun Key.codeChar(): Char? {
    val code = nativeKeyCode
    val char = when (code) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 'A' + (code - KeyEvent.KEYCODE_A)
        else -> digit()
    }
    return char?.takeIf { it in RecoveryCode.ALPHABET }
}

/**
 * Forgot PIN (the owner's #112): the PIN's recovery code, on keys of just the characters codes
 * have, or a keyboard's keys. The twelve characters are checked as the last goes in, dashes or not.
 * Wrong codes count as wrong PINs do: after five, entry stops for a minute ([tries]).
 *
 * @param checkCode whether a code is right; the screen never sees the real one.
 */
@Composable
fun RecoveryCodeEntry(
    backLabel: String,
    checkCode: suspend (String) -> Boolean,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
    tries: PinTries = remember { PinTries() },
) {
    var typed by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val wrong = tries.wrong
    val blocked = tries.blocked
    LaunchedEffect(Unit) {
        withFrameNanos { }
        first.requestFocus()
    }
    LaunchedEffect(tries.blockedUntil) {
        val until = tries.blockedUntil ?: return@LaunchedEffect
        delay((until - System.currentTimeMillis()).coerceAtLeast(0))
        tries.reopenIfDue()
    }

    fun type(char: Char) {
        if (blocked || checking || typed.length >= RecoveryCode.LENGTH) return
        typed += char
        if (typed.length < RecoveryCode.LENGTH) return
        val code = typed
        checking = true
        scope.launch {
            val right = try {
                checkCode(code)
            } finally {
                checking = false
            }
            typed = ""
            if (right) {
                tries.succeeded()
                onRight()
            } else {
                tries.failed()
            }
        }
    }

    fun delete() {
        if (!checking) typed = typed.dropLast(1)
    }

    Box(
        modifier
            .fillMaxSize()
            .pinBackdrop()
            .onPreviewKeyEvent { event ->
                // A keyboard's letters and digits type too, in capitals.
                val char = event.key.codeChar() ?: event.utf16CodePoint.takeIf { it > 0 }?.toChar()?.uppercaseChar()?.takeIf { it in RecoveryCode.ALPHABET }
                when {
                    char != null -> {
                        if (event.type == KeyEventType.KeyDown) type(char)
                        true
                    }
                    event.key == Key.Backspace || event.key == Key.Delete -> {
                        if (event.type == KeyEventType.KeyDown) delete()
                        true
                    }
                    else -> false
                }
            },
    ) {
        SecondaryHeader(backLabel, title = null)
        Column(Modifier.fillMaxSize().padding(top = 150.px), horizontalAlignment = Alignment.CenterHorizontally) {
            GraphiteIconTile(PlasmaIcons.PinLock, 120.px, 30.px, 54.px, stroke = 1.6f)
            PlasmaText(stringResource(R.string.enter_recovery_code), plasmaStyle(48f, 600, trackingPx = -1f), Modifier.padding(top = 36.px))
            // What's typed so far, in its groups, the rest dotted.
            val entered = stringResource(R.string.recovery_code_entered, typed.length, RecoveryCode.LENGTH)
            PlasmaText(
                RecoveryCode.shown(typed.padEnd(RecoveryCode.LENGTH, '·')),
                plasmaStyle(52f, 600, trackingPx = 4f, tabular = true),
                Modifier.padding(top = 36.px).semantics { contentDescription = entered },
                color = if (typed.isEmpty()) PlasmaColors.Text4 else PlasmaColors.Text,
            )
            Column(
                Modifier.padding(top = 40.px).focusRestoringGroup(first),
                verticalArrangement = Arrangement.spacedBy(12.px),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CodeRows.forEachIndexed { row, keys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.px)) {
                        keys.forEachIndexed { i, char ->
                            CodeKey(
                                onClick = { type(char) },
                                modifier = if (row == 0 && i == 0) Modifier.focusRequester(first) else Modifier,
                            ) {
                                val focused = LocalFocused.current
                                PlasmaText(
                                    char.toString(),
                                    plasmaStyle(if (focused) 30f else 28f, if (focused) 600 else 500, tabular = true),
                                    color = if (focused) PlasmaColors.OnFocus else PlasmaColors.TextSoft3,
                                )
                            }
                        }
                        if (row == CodeRows.lastIndex) {
                            val delete = stringResource(DesignR.string.key_delete)
                            CodeKey(onClick = ::delete, modifier = Modifier.semantics { contentDescription = delete }) {
                                PlasmaIcon(
                                    PlasmaIcons.Backspace,
                                    30.px,
                                    tint = if (LocalFocused.current) PlasmaColors.OnFocus else PlasmaColors.TextSoft,
                                    stroke = 1.8f,
                                )
                            }
                        }
                    }
                }
            }
            PlasmaText(
                when {
                    blocked -> stringResource(R.string.pin_blocked)
                    wrong > 0 -> pluralStringResource(R.plurals.wrong_code, PinTries.TRIES - wrong, PinTries.TRIES - wrong)
                    else -> stringResource(R.string.recovery_code_hint)
                },
                plasmaStyle(21f, 500),
                Modifier.padding(top = 34.px),
                color = if (wrong > 0) PlasmaColors.WarnText else PlasmaColors.Text3,
            )
        }
    }
}

@Composable
private fun CodeKey(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 72.px, content: @Composable () -> Unit) {
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.size(size),
        style = FocusStyle.Key,
        shape = PlasmaShapes.PinKey,
        color = PlasmaColors.Surface2,
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
