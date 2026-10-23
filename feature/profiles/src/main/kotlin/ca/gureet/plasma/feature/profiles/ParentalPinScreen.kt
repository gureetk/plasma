// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.profiles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ca.gureet.plasma.core.design.Hairline
import ca.gureet.plasma.core.design.LocalReduceMotion
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.ChoiceButton
import ca.gureet.plasma.core.design.component.GraphiteIconTile
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.component.NumberPad
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.SecondaryHeader
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.component.digit
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.outline
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the PIN unlocks (brief 4.25). */
@Immutable
sealed interface PinRequest {
    /** A program on a locked channel, or one rated above the profile's limit. */
    data class Program(val channelNumber: Int, val channelName: String, val logo: String?, val title: String, val rating: String?) : PinRequest

    /** A profile with its own PIN. */
    data class Profile(val name: String) : PinRequest
}

/** How long an unlocked program stays unlocked (brief 4.25). */
enum class UnlockFor { Program, Hour, UntilSleep }

private const val PIN_LENGTH = 4
private const val TRIES = 5
private const val BLOCKED_MS = 60_000L

/**
 * The parental PIN (board 25): four digits on the number pad or the remote's number keys. A wrong
 * PIN shakes the dots (a fade under Reduce Motion) and says how many tries are left; after five,
 * entry stops for a minute. A program can stay unlocked for a while (Unlock for).
 *
 * @param checkPin whether a PIN is right; the screen never sees the real one.
 */
@Composable
fun ParentalPinScreen(
    request: PinRequest,
    backLabel: String,
    checkPin: (String) -> Boolean,
    onUnlocked: (UnlockFor) -> Unit,
    modifier: Modifier = Modifier,
) {
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableIntStateOf(0) }
    var blocked by remember { mutableStateOf(false) }
    var unlockFor by remember { mutableStateOf(UnlockFor.Program) }
    var choosing by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val unlockButton = remember { FocusRequester() }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(Unit) {
        withFrameNanos { }
        first.requestFocus()
    }
    LaunchedEffect(blocked) {
        if (blocked) {
            delay(BLOCKED_MS)
            blocked = false
            wrong = 0
        }
    }

    fun type(digit: Char) {
        if (blocked || entered.length >= PIN_LENGTH) return
        entered += digit
        if (entered.length < PIN_LENGTH) return
        if (checkPin(entered)) {
            onUnlocked(unlockFor)
            return
        }
        wrong++
        if (wrong >= TRIES) blocked = true
        scope.launch {
            // The dots shake (or fade, with Reduce Motion), then empty for the next try.
            shake.snapTo(0f)
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = 420
                    if (reduceMotion) {
                        1f at 140
                        1f at 280
                    } else {
                        -14f at 60
                        14f at 120
                        -10f at 180
                        10f at 240
                        -5f at 300
                        5f at 360
                    }
                },
            )
            entered = ""
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .pinBackdrop()
            .onPreviewKeyEvent { event ->
                if (choosing) return@onPreviewKeyEvent false
                val digit = event.key.digit()
                when {
                    digit != null -> {
                        if (event.type == KeyEventType.KeyDown) type(digit)
                        true
                    }
                    event.key == Key.Backspace || event.key == Key.Delete -> {
                        if (event.type == KeyEventType.KeyDown) entered = entered.dropLast(1)
                        true
                    }
                    else -> false
                }
            },
    ) {
        SecondaryHeader(backLabel, title = null)
        Column(Modifier.fillMaxSize().padding(top = 226.px), horizontalAlignment = Alignment.CenterHorizontally) {
            GraphiteIconTile(PlasmaIcons.PinLock, 120.px, 30.px, 54.px, stroke = 1.6f)
            PlasmaText(
                when (request) {
                    is PinRequest.Program -> stringResource(R.string.enter_pin_to_watch)
                    is PinRequest.Profile -> stringResource(R.string.enter_profile_pin, request.name)
                },
                plasmaStyle(48f, 600, trackingPx = -1f),
                Modifier.padding(top = 36.px),
            )
            if (request is PinRequest.Program) ProgramLine(request)

            val dots = stringResource(R.string.pin_entered, entered.length, PIN_LENGTH)
            Row(
                Modifier
                    .padding(top = if (request is PinRequest.Program) 48.px else 92.px)
                    .graphicsLayer {
                        if (reduceMotion) alpha = 1f - 0.7f * shake.value else translationX = shake.value.px.toPx()
                    }
                    .semantics { contentDescription = dots },
                horizontalArrangement = Arrangement.spacedBy(22.px),
            ) {
                repeat(PIN_LENGTH) { i ->
                    val filled = i < entered.length
                    Box(
                        Modifier
                            .size(26.px)
                            .then(
                                if (filled) {
                                    Modifier.background(PlasmaColors.Text, CircleShape)
                                } else {
                                    Modifier.outline(2.5f.px, PlasmaColors.white(0.35f), CircleShape)
                                },
                            ),
                    )
                }
            }

            NumberPad(
                onDigit = ::type,
                onDelete = { entered = entered.dropLast(1) },
                modifier = Modifier.padding(top = 52.px),
                first = first,
            )

            if (request is PinRequest.Program) {
                val options = stringArrayResource(R.array.unlock_for_options)
                Row(
                    Modifier.padding(top = 56.px),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.px),
                ) {
                    PlasmaText(stringResource(R.string.unlock_for), plasmaStyle(22f, 500), color = PlasmaColors.Text2)
                    ChoiceButton(
                        options[unlockFor.ordinal],
                        onClick = { choosing = true },
                        modifier = Modifier.focusRequester(unlockButton),
                        color = PlasmaColors.white(0.1f),
                    )
                }
            }

            PlasmaText(
                when {
                    blocked -> stringResource(R.string.pin_blocked)
                    wrong > 0 -> pluralStringResource(R.plurals.wrong_pin, TRIES - wrong, TRIES - wrong)
                    else -> stringResource(R.string.pin_hint)
                },
                plasmaStyle(21f, 500),
                Modifier.padding(top = 34.px),
                color = if (wrong > 0) PlasmaColors.WarnText else PlasmaColors.Text3,
            )
        }

        SheetHost(visible = choosing, returnFocus = unlockButton) {
            val options = stringArrayResource(R.array.unlock_for_options)
            OptionsSheet(
                title = stringResource(R.string.unlock_for),
                onDismiss = { choosing = false },
                options = UnlockFor.entries.map { option ->
                    SheetOption(options[option.ordinal], onClick = { unlockFor = option }, checked = option == unlockFor)
                },
            )
        }
    }
}

/** "COMBAT ARENA 110 · Fight Night: Prelims 18+". */
@Composable
private fun ProgramLine(program: PinRequest.Program) {
    Row(
        Modifier.padding(top = 14.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.px),
    ) {
        if (program.logo != null) LogoImage(program.logo, program.channelName, 24.px)
        PlasmaText(
            stringResource(R.string.channel_and_program, program.channelNumber, program.title),
            plasmaStyle(23f, 500, tabular = true),
            color = PlasmaColors.Text2,
        )
        if (program.rating != null) {
            Box(
                Modifier
                    .outline(Hairline, PlasmaColors.white(0.4f), PlasmaShapes.Badge)
                    .height(28.px)
                    .padding(horizontal = 8.px),
                contentAlignment = Alignment.Center,
            ) {
                PlasmaText(program.rating, plasmaStyle(15f, 700, trackingPx = 0.4f), color = PlasmaColors.TextSoft3)
            }
        }
    }
}

/** A soft pool of light behind the PIN: an ellipse 1100 × 640 px around the middle (board 25). */
private fun Modifier.pinBackdrop(): Modifier = drawBehind {
    drawRect(PlasmaColors.Bg)
    val center = Offset(size.width / 2, size.height * 0.46f)
    val stretch = 1100f / 640f
    val brush = Brush.radialGradient(0f to PlasmaColors.Surface1, 0.72f to PlasmaColors.Bg, center = center, radius = 640.px.toPx())
    scale(scaleX = stretch, scaleY = 1f, pivot = center) {
        drawRect(brush, topLeft = Offset(center.x - size.width / (2 * stretch), 0f), size = Size(size.width / stretch, size.height))
    }
}
