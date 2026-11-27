// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.plasma.remote.app.resources.Res
import ca.gureet.plasma.remote.app.resources.clear
import ca.gureet.plasma.remote.app.resources.done
import ca.gureet.plasma.remote.app.resources.nothing_to_type
import ca.gureet.plasma.remote.app.resources.paste
import ca.gureet.plasma.remote.app.resources.results_on_tv
import ca.gureet.plasma.remote.app.resources.type_text
import ca.gureet.plasma.remote.app.resources.type_title
import ca.gureet.plasma.remote.app.resources.typing_elsewhere
import ca.gureet.plasma.remote.app.resources.typing_into
import ca.gureet.plasma.remote.app.tv.TvLink
import ca.gureet.plasma.remote.protocol.RemoteField
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The Type tab (board 33, brief 4.33): the phone's keyboard types into the TV's focused field.
 * The field mirrors the TV's text while the phone isn't typing; what's typed goes to the TV a
 * moment after each change (150 ms, as on the web page), and Done sends it and submits. Paste,
 * Clear, and what the TV has found ("On the TV: 4 programs · 2 movies"). With no field on the TV,
 * it says when it will open. [focusNow] puts the keyboard up: the tab opened by itself.
 */
@Composable
fun TypeTab(tv: TvLink, front: Boolean, field: RemoteField?, focusNow: Boolean) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp)) {
        FieldCard(field, front)
        if (field != null) Typing(tv, field, focusNow)
    }
}

/** "Typing into Search on the TV / Results update on the TV as you type", with the field's kind as its icon. */
@Composable
private fun FieldCard(field: RemoteField?, front: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RemoteColors.Surface1).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(RemoteColors.Surface3), contentAlignment = Alignment.Center) {
            Icon(if (field == null || field.kind == RemoteField.SEARCH) RemoteIcons.Search else RemoteIcons.Keyboard, 20.dp, RemoteColors.Text)
        }
        Column {
            val title = if (field != null) stringResource(Res.string.typing_into, field.label) else stringResource(Res.string.type_title)
            val text = when {
                field != null -> stringResource(Res.string.type_text)
                front -> stringResource(Res.string.nothing_to_type)
                else -> stringResource(Res.string.typing_elsewhere)
            }
            BasicText(title, style = textStyle(15.sp, FontWeight.SemiBold))
            BasicText(text, Modifier.padding(top = 2.dp), style = textStyle(13.sp, color = RemoteColors.Text3).copy(lineHeight = 17.5.sp))
        }
    }
}

@Composable
private fun Typing(tv: TvLink, field: RemoteField, focusNow: Boolean) {
    var text by remember { mutableStateOf(field.text) }
    var typing by remember { mutableStateOf(false) }
    // What the phone typed and hasn't sent yet: sent once it's been left for 150 ms.
    var unsent by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    // The TV's text, while the phone isn't typing.
    LaunchedEffect(field.text, typing) { if (!typing && unsent == null) text = field.text }
    LaunchedEffect(unsent) {
        val pending = unsent ?: return@LaunchedEffect
        delay(TEXT_DEBOUNCE_MS)
        tv.text(pending)
        unsent = null
    }
    LaunchedEffect(focusNow) { if (focusNow) runCatching { focus.requestFocus() } }

    fun change(new: String) {
        text = new.take(MAX_TEXT)
        unsent = text
    }

    fun done() {
        unsent = null
        tv.text(text)
        tv.submit()
        focusManager.clearFocus()
    }

    Row(
        Modifier.padding(top = 14.dp).fillMaxWidth().height(58.dp).clip(RoundedCornerShape(14.dp)).background(RemoteColors.Surface1)
            .border(2.dp, if (typing) RemoteColors.Text else RemoteColors.MiniTrack, RoundedCornerShape(14.dp))
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            text,
            ::change,
            Modifier.weight(1f).focusRequester(focus).onFocusChanged { typing = it.isFocused },
            singleLine = true,
            textStyle = textStyle(22.sp),
            cursorBrush = SolidColor(RemoteColors.Text),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = field.kind != RemoteField.SEARCH, imeAction = if (field.kind == RemoteField.SEARCH) ImeAction.Search else ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { done() }, onSearch = { done() }),
        )
        if (text.isNotEmpty()) {
            Box(Modifier.size(32.dp).clip(CircleShape).tap(stringResource(Res.string.clear)) { change("") }, contentAlignment = Alignment.Center) {
                Icon(RemoteIcons.ClearField, 20.dp, RemoteColors.Text3)
            }
        }
    }
    Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill(stringResource(Res.string.paste), RemoteColors.Button, RemoteColors.Text) {
            clipboard.getText()?.text?.let { change(text + it) }
        }
        Pill(stringResource(Res.string.clear), RemoteColors.Button, RemoteColors.Text) { change("") }
        Spacer(Modifier.weight(1f))
        Pill(stringResource(Res.string.done), RemoteColors.Text, RemoteColors.Bg) { done() }
    }
    val results = field.results
    if (results != null) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(RemoteColors.Ok))
            BasicText(stringResource(Res.string.results_on_tv, results), style = textStyle(13.sp, color = RemoteColors.Text3))
        }
    }
}

/** Paste, Clear (soft) and Done (primary): 40 dp, 14 sp. */
@Composable
private fun Pill(label: String, fill: Color, color: Color, onTap: () -> Unit) {
    val pressed = rememberPressed()
    Box(Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(fill).pressedLook(pressed).tap(label, pressed = pressed, action = onTap).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        BasicText(label, style = textStyle(14.sp, FontWeight.SemiBold, color))
    }
}

/** As the web page: what's typed goes to the TV once it's been left this long. */
private const val TEXT_DEBOUNCE_MS = 150L

/** The TV takes 200 characters. */
private const val MAX_TEXT = 200
