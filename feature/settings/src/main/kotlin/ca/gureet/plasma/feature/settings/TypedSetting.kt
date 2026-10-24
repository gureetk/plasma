// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.component.SecondaryHeader
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

/** The DEL control character, which a keyboard's Delete key can carry. */
private const val DELETE_CHAR = 0x7F

/**
 * A setting typed in (brief 4.21's pattern, for Settings): its fields, the on-screen keyboard and
 * Save. As on Add a Source, OK on a field moves to the keyboard, Next to the next field and then to
 * Save, and the remote's number keys and a plugged-in keyboard type too. Back leaves without
 * saving.
 */
@Composable
internal fun TypedSetting(back: String, entry: SettingAction.Type, onDone: () -> Unit) {
    val values = remember(entry) { mutableStateListOf(*entry.fields.map { it.value }.toTypedArray()) }
    var editing by remember(entry) { mutableIntStateOf(0) }
    val fieldFocus = remember(entry) { entry.fields.map { FocusRequester() } }
    val keyboard = remember { FocusRequester() }
    val save = remember { FocusRequester() }

    LaunchedEffect(entry) {
        withFrameNanos { }
        fieldFocus.first().requestFocus()
    }

    fun type(text: String) {
        values[editing] = values[editing] + text
    }

    fun delete() {
        values[editing] = values[editing].dropLast(1)
    }

    fun next() {
        if (editing < values.lastIndex) editing++ else save.requestFocus()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(PlasmaColors.Bg)
            .onPreviewKeyEvent { event ->
                when {
                    event.key == Key.Back || event.key == Key.Escape -> {
                        if (event.type == KeyEventType.KeyUp) onDone()
                        true
                    }
                    event.type != KeyEventType.KeyDown -> false
                    event.key == Key.Backspace || event.key == Key.Delete -> {
                        delete()
                        true
                    }
                    else -> {
                        val code = event.utf16CodePoint
                        if (code < ' '.code || code == DELETE_CHAR) {
                            false
                        } else {
                            type(code.toChar().toString())
                            true
                        }
                    }
                }
            },
    ) {
        SecondaryHeader(back, entry.title)
        Column(Modifier.offset(430.px, 148.px).width(1060.px)) {
            entry.text?.let { text ->
                PlasmaText(text, plasmaStyle(21f, 400, lineHeight = 1.35f), Modifier.padding(horizontal = 4.px), color = PlasmaColors.Text2, maxLines = 2)
            }
            Column(Modifier.padding(top = 20.px), verticalArrangement = Arrangement.spacedBy(10.px)) {
                entry.fields.forEachIndexed { i, field ->
                    SourceField(
                        label = field.label,
                        value = values[i],
                        editing = editing == i,
                        onFocused = { editing = i },
                        onClick = { keyboard.requestFocus() },
                        modifier = Modifier.focusRequester(fieldFocus[i]),
                        hint = if (values[i].isEmpty()) field.hint else null,
                    )
                }
            }
            SourceKeyboard(::type, ::delete, ::next, first = keyboard, modifier = Modifier.padding(top = 26.px))
            Row(Modifier.padding(top = 26.px), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.px)) {
                PlasmaButton(
                    stringResource(R.string.save),
                    onClick = {
                        entry.onSave(values.map { it.trim() })
                        onDone()
                    },
                    modifier = Modifier.focusRequester(save),
                    height = 58.px,
                    startPadding = 30.px,
                    endPadding = 30.px,
                    pivot = TransformOrigin(0f, 0.5f),
                    unfocusedColor = PlasmaColors.Selected,
                )
                PlasmaText(stringResource(R.string.typed_save_note), plasmaStyle(20f, 500), color = PlasmaColors.Text3)
            }
        }
    }
}
