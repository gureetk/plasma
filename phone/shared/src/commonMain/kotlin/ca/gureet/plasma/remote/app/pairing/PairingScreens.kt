// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.pairing

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.plasma.remote.app.resources.Res
import ca.gureet.plasma.remote.app.resources.address_hint
import ca.gureet.plasma.remote.app.resources.cancel
import ca.gureet.plasma.remote.app.resources.cant_reach_pairing
import ca.gureet.plasma.remote.app.resources.enter_address
import ca.gureet.plasma.remote.app.resources.find_help
import ca.gureet.plasma.remote.app.resources.find_looking
import ca.gureet.plasma.remote.app.resources.find_text
import ca.gureet.plasma.remote.app.resources.find_title
import ca.gureet.plasma.remote.app.resources.name_text
import ca.gureet.plasma.remote.app.resources.name_this_phone
import ca.gureet.plasma.remote.app.resources.needs_update
import ca.gureet.plasma.remote.app.resources.next
import ca.gureet.plasma.remote.app.resources.not_that_tv
import ca.gureet.plasma.remote.app.resources.on_this_wifi
import ca.gureet.plasma.remote.app.resources.pair
import ca.gureet.plasma.remote.app.resources.pair_text
import ca.gureet.plasma.remote.app.resources.pair_title
import ca.gureet.plasma.remote.app.resources.pairing
import ca.gureet.plasma.remote.app.resources.plasma_tv
import ca.gureet.plasma.remote.app.resources.scan_code
import ca.gureet.plasma.remote.app.resources.the_tv
import ca.gureet.plasma.remote.app.resources.too_many_tries
import ca.gureet.plasma.remote.app.resources.try_demo
import ca.gureet.plasma.remote.app.resources.wrong_code
import ca.gureet.plasma.remote.app.resources.your_tvs
import ca.gureet.plasma.remote.app.tv.FoundTv
import ca.gureet.plasma.remote.app.tv.PairedTv
import ca.gureet.plasma.remote.app.tv.PhoneApp
import ca.gureet.plasma.remote.app.ui.RemoteColors
import ca.gureet.plasma.remote.app.ui.RemoteIcons
import ca.gureet.plasma.remote.app.ui.Icon
import ca.gureet.plasma.remote.app.ui.pressedLook
import ca.gureet.plasma.remote.app.ui.rememberPressed
import ca.gureet.plasma.remote.app.ui.tap
import ca.gureet.plasma.remote.app.ui.textStyle
import ca.gureet.plasma.remote.protocol.RemoteProtocol
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Find your TV (brief 5.22): the TVs this phone is paired with, the TVs running Plasma on the
 * Wi-Fi that it isn't, Enter Address, and Try the Demo. [note] is a line above it all, such as a
 * TV that no longer knows this phone.
 */
@Composable
fun FindTvScreen(
    paired: List<PairedTv>,
    found: List<FoundTv>,
    note: String?,
    onPaired: (PairedTv) -> Unit,
    onPair: (PairTarget) -> Unit,
    onScan: () -> Unit,
    onDemo: () -> Unit,
) {
    var typing by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    val unpaired = found.filter { tv -> paired.none { it.id == tv.advert.id } }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp)) {
        if (note != null) Note(note)
        BasicText(stringResource(Res.string.find_title), Modifier.padding(horizontal = 4.dp), style = textStyle(22.sp, FontWeight.SemiBold))
        BasicText(stringResource(Res.string.find_text), Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 14.dp), style = textStyle(14.sp, color = RemoteColors.Text2))
        if (paired.isNotEmpty()) {
            Heading(stringResource(Res.string.your_tvs))
            paired.forEach { tv -> TvRow(tv.name, tv.host, online = found.any { it.advert.id == tv.id }) { onPaired(tv) } }
            Spacer(Modifier.height(10.dp))
            Heading(stringResource(Res.string.on_this_wifi))
        }
        if (unpaired.isEmpty()) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(RemoteColors.Text3))
                BasicText(stringResource(Res.string.find_looking), style = textStyle(14.sp, color = RemoteColors.Text3))
            }
            BasicText(stringResource(Res.string.find_help), Modifier.padding(horizontal = 4.dp), style = textStyle(13.sp, color = RemoteColors.Text3).copy(lineHeight = 18.sp))
        } else {
            val plasma = stringResource(Res.string.plasma_tv)
            unpaired.forEach { tv -> TvRow(tv.advert.name, plasma, online = true) { onPair(PairTarget(tv.advert.name, tv.endpoint)) } }
        }
        Spacer(Modifier.height(18.dp))
        if (typing) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            val go = { typedEndpoint(address)?.let { onPair(PairTarget(null, it)) } }
            Field(address, { address = it }, stringResource(Res.string.address_hint), Modifier.focusRequester(focus), KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next), KeyboardActions(onNext = { go() }))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(Res.string.cancel), RemoteColors.Button, RemoteColors.Text) { typing = false }
                Spacer(Modifier.weight(1f))
                Pill(stringResource(Res.string.next), RemoteColors.Text, RemoteColors.Bg, enabled = typedEndpoint(address) != null) { go() }
            }
        } else {
            // Scanning first: it pins the TV's certificate before connecting (docs/decisions.md).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(Res.string.scan_code), RemoteColors.Text, RemoteColors.Bg, onTap = onScan)
                Pill(stringResource(Res.string.enter_address), RemoteColors.Button, RemoteColors.Text) { typing = true }
            }
            Spacer(Modifier.height(8.dp))
            Pill(stringResource(Res.string.try_demo), RemoteColors.Button, RemoteColors.Text, onTap = onDemo)
        }
    }
}

/**
 * Pairing (brief 5.22): "Enter the code shown on Living Room TV" in six boxes, then "Name this
 * phone", filled in from the phone's name. Pair sends both; the TV answers or says why not. With
 * a scanned code there are no boxes, only the name, unless the TV turns the code away.
 */
@Composable
fun PairScreen(target: PairTarget, deviceName: String, app: PhoneApp, onPaired: (PairedTv) -> Unit, onCancel: () -> Unit) {
    var scanned by remember { mutableStateOf(target.code != null) }
    var code by remember { mutableStateOf(target.code.orEmpty()) }
    var name by remember { mutableStateOf(deviceName) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    // The boxes take the keyboard when they're shown: at once for a code to type, after a refusal for a scanned one.
    LaunchedEffect(scanned) { if (!scanned) runCatching { focus.requestFocus() } }
    val messages = mapOf(
        RemoteProtocol.Reasons.CODE to stringResource(Res.string.wrong_code),
        RemoteProtocol.Reasons.TRIES to stringResource(Res.string.too_many_tries),
        RemoteProtocol.Reasons.VERSION to stringResource(Res.string.needs_update),
    )
    val unreachable = stringResource(Res.string.cant_reach_pairing)
    val notThatTv = stringResource(Res.string.not_that_tv)
    val ready = code.length == CODE_LENGTH && name.isNotBlank() && !busy

    fun go() {
        if (!ready) return
        busy = true
        problem = null
        scope.launch {
            when (val outcome = pairWith(target, code, name.trim(), app)) {
                is PairOutcome.Paired -> onPaired(outcome.tv)
                is PairOutcome.Refused -> {
                    problem = messages[outcome.reason] ?: unreachable
                    code = ""
                    scanned = false
                }
                PairOutcome.Unreachable -> problem = unreachable
                PairOutcome.NotThatTv -> problem = notThatTv
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp)) {
        val tvName = target.name ?: stringResource(Res.string.the_tv)
        if (scanned) {
            BasicText(stringResource(Res.string.name_this_phone), Modifier.padding(horizontal = 4.dp), style = textStyle(22.sp, FontWeight.SemiBold))
            BasicText(stringResource(Res.string.name_text, tvName), Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 18.dp), style = textStyle(14.sp, color = RemoteColors.Text2).copy(lineHeight = 19.sp))
            if (problem != null) Note(problem!!)
        } else {
            BasicText(stringResource(Res.string.pair_title, tvName), Modifier.padding(horizontal = 4.dp), style = textStyle(22.sp, FontWeight.SemiBold))
            BasicText(stringResource(Res.string.pair_text), Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 18.dp), style = textStyle(14.sp, color = RemoteColors.Text2).copy(lineHeight = 19.sp))
            CodeBoxes(code, { code = it.filter(Char::isLetterOrDigit).uppercase().take(CODE_LENGTH) }, Modifier.focusRequester(focus))
            if (problem != null) Note(problem!!, Modifier.padding(top = 14.dp))
            BasicText(stringResource(Res.string.name_this_phone), Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp), style = textStyle(13.sp, color = RemoteColors.Text3))
        }
        Field(name, { name = it.take(MAX_NAME) }, "", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { go() }))
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(stringResource(Res.string.cancel), RemoteColors.Button, RemoteColors.Text, onTap = onCancel)
            Spacer(Modifier.weight(1f))
            Pill(stringResource(if (busy) Res.string.pairing else Res.string.pair), RemoteColors.Text, RemoteColors.Bg, enabled = ready) { go() }
        }
    }
}

/** Six boxes over one field: the code as the TV shows it, a character in each. */
@Composable
private fun CodeBoxes(code: String, onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        code,
        onCode,
        modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(CODE_LENGTH) { at ->
                    val current = at == code.length
                    Box(
                        Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(12.dp)).background(RemoteColors.Surface1)
                            .border(2.dp, if (current) RemoteColors.Text else RemoteColors.MiniTrack, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(code.getOrNull(at)?.toString().orEmpty(), style = textStyle(24.sp, FontWeight.SemiBold).copy(textAlign = TextAlign.Center))
                    }
                }
            }
        },
    )
}

@Composable
private fun Heading(text: String) = BasicText(text, Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp), style = textStyle(13.sp, color = RemoteColors.Text3))

/** A TV: its name, under it where it is, and a dot that's green while it's on the Wi-Fi. */
@Composable
private fun TvRow(name: String, detail: String, online: Boolean, onTap: () -> Unit) {
    val pressed = rememberPressed()
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).height(60.dp).clip(RoundedCornerShape(12.dp)).background(RemoteColors.Surface1).pressedLook(pressed)
            .tap(name, pressed = pressed, action = onTap).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(RemoteColors.Surface3), contentAlignment = Alignment.Center) {
            Icon(RemoteIcons.TabRemote, 20.dp, RemoteColors.Text)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(name, style = textStyle(15.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(detail, style = textStyle(12.5.sp, color = RemoteColors.Text3), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (online) RemoteColors.Ok else RemoteColors.Text3))
    }
}

@Composable
internal fun Note(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(bottom = 14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RemoteColors.Surface1).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 6.dp).size(7.dp).clip(CircleShape).background(RemoteColors.Warn))
        BasicText(text, style = textStyle(13.sp, color = RemoteColors.Text2).copy(lineHeight = 18.sp))
    }
}

@Composable
private fun Field(value: String, onValue: (String) -> Unit, hint: String, modifier: Modifier = Modifier, keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default) {
    Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).background(RemoteColors.Surface1).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) BasicText(hint, style = textStyle(16.sp, color = RemoteColors.Text3))
        BasicTextField(value, onValue, modifier.fillMaxWidth(), singleLine = true, textStyle = textStyle(16.sp), cursorBrush = SolidColor(RemoteColors.Text), keyboardOptions = keyboardOptions, keyboardActions = keyboardActions)
    }
}

@Composable
internal fun Pill(label: String, fill: Color, color: Color, enabled: Boolean = true, onTap: () -> Unit) {
    val pressed = rememberPressed()
    Box(
        Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (enabled) fill else RemoteColors.Surface1).pressedLook(pressed)
            .tap(label, enabled, pressed, onTap).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = textStyle(15.sp, FontWeight.SemiBold, if (enabled) color else RemoteColors.Text3))
    }
}

/** The TV's codes are six letters and digits. */
private const val CODE_LENGTH = 6

private const val MAX_NAME = 40
