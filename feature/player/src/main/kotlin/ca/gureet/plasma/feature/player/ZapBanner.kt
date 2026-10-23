// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.boxShadow
import ca.gureet.plasma.core.design.component.ClockText
import ca.gureet.plasma.core.design.component.FooterKey
import ca.gureet.plasma.core.design.component.HintTone
import ca.gureet.plasma.core.design.component.KeyFooter
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.component.LogoPlate
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.NumberEntry
import ca.gureet.plasma.core.design.component.NumberEntryBox
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.overhang
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import java.time.Duration
import java.time.Instant

/**
 * The zap banner (board 11, brief 4.11), shown on every channel change: the number at the top left,
 * what's on at the bottom left, and the channels above and below at the bottom right.
 */
@Composable
internal fun ZapBanner(
    channel: PlayerChannel,
    above: PlayerChannel,
    below: PlayerChannel,
    now: Instant,
    entry: NumberEntry,
    match: PlayerChannel?,
) {
    val zone = LocalPlasmaClock.current.zone
    val program = channel.programAt(now)
    val next = channel.programAfter(now)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to PlasmaColors.black(0.45f),
                    260f / 1080f to Color.Transparent,
                    520f / 1080f to Color.Transparent,
                    720f / 1080f to PlasmaColors.black(0.68f),
                    900f / 1080f to PlasmaColors.black(0.92f),
                    1f to PlasmaColors.black(0.94f),
                ),
            ),
        )
        NumberBox(channel, entry, match)

        Column(Modifier.offset(96.px, 742.px).width(1060.px)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.px)) {
                LogoImage(channel.logo, channel.name, LogoSize.Plate)
                PlasmaText("${channel.number} · ${channel.name}", plasmaStyle(23f, 500, tabular = true), color = PlasmaColors.TextSoft)
                if (channel.uhd || channel.hdr || channel.catchUp) {
                    Box(Modifier.size(1.px, 26.px).background(PlasmaColors.white(0.25f)))
                }
                if (channel.uhd) VideoBadge("4K")
                if (channel.hdr) VideoBadge("HDR")
                if (channel.catchUp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px)) {
                        PlasmaIcon(PlasmaIcons.CatchUp, 18.px, stroke = 2.4f, tint = PlasmaColors.TextSoft3)
                        PlasmaText(stringResource(R.string.catch_up), plasmaStyle(19f, 600), color = PlasmaColors.TextSoft3)
                    }
                }
            }
            if (program != null) {
                PlasmaText(
                    program.title,
                    plasmaStyle(50f, 600, trackingPx = -1.1f, lineHeight = 1.06f),
                    Modifier.padding(top = 14.px),
                    color = Color.White,
                )
                Row(
                    Modifier.padding(top = 18.px),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.px),
                ) {
                    val style = plasmaStyle(22f, 500, tabular = true)
                    val length = Duration.between(program.start, program.end).toMillis().toFloat()
                    val fraction = Duration.between(program.start, now).toMillis() / length
                    PlasmaText(Formats.time(program.start.atZone(zone)), style, color = PlasmaColors.TextSoft)
                    ProgressBar(fraction, Modifier.weight(1f), height = 6.px, track = PlasmaColors.white(0.22f))
                    PlasmaText(Formats.time(program.end.atZone(zone)), style, color = PlasmaColors.TextSoft)
                    PlasmaText(
                        stringResource(R.string.min_left, Formats.duration(Duration.between(now, program.end))),
                        style,
                        color = PlasmaColors.Text2,
                    )
                }
            }
            if (next != null) {
                PlasmaText(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = PlasmaColors.Text3, fontFeatureSettings = "tnum")) {
                            append(stringResource(R.string.next_at, Formats.time(next.start.atZone(zone))))
                        }
                        append("  ")
                        append(next.title)
                    },
                    plasmaStyle(22f, 500),
                    Modifier.padding(top = 16.px),
                    color = PlasmaColors.TextSoft,
                )
            }
        }

        Column(Modifier.offset(1224.px, 752.px).width(600.px), verticalArrangement = Arrangement.spacedBy(8.px)) {
            NeighbourCard(above, up = true, now = now)
            NeighbourCard(below, up = false, now = now)
        }

        KeyFooter(
            colorKeys = emptyList(),
            keys = listOf(
                FooterKey("CH ±", stringResource(R.string.key_change_channel)),
                FooterKey("0–9", stringResource(R.string.key_go_to_number)),
                FooterKey("OK", stringResource(R.string.key_details)),
            ),
            tone = HintTone.Video,
            leading = { ClockText(style = plasmaStyle(24f, 500, trackingPx = -0.2f, tabular = true)) },
        )
    }
}

/**
 * The number at the top left. While a number is being typed it shows the digits ("10_") with the
 * channel they match, or "No channel 999".
 */
@Composable
internal fun NumberBox(channel: PlayerChannel, entry: NumberEntry, match: PlayerChannel?) {
    val modifier = Modifier.offset(96.px, 56.px)
    if (entry.isActive) NumberEntryBox(entry, match?.name, modifier) else NumberEntryBox(channel.number, null, modifier)
}

/** CH + above, CH − below: the channel and what's on, so a zap isn't a guess. */
@Composable
private fun NeighbourCard(channel: PlayerChannel, up: Boolean, now: Instant) {
    val zone = LocalPlasmaClock.current.zone
    val program = channel.programAt(now)
    Row(
        Modifier
            .size(600.px, 88.px)
            .background(PlasmaColors.ZapCard, PlasmaShapes.Row)
            .padding(start = 14.px, end = 20.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.px),
    ) {
        Column(Modifier.width(40.px), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.px)) {
            val label = plasmaStyle(14f, 700, trackingPx = 0.6f)
            if (up) PlasmaIcon(PlasmaIcons.ChevronUp, 18.px, tint = PlasmaColors.Text2)
            PlasmaText(stringResource(R.string.ch), label, color = PlasmaColors.Text2)
            if (!up) PlasmaIcon(PlasmaIcons.ChevronDown, 18.px, stroke = 2.8f, tint = PlasmaColors.Text2)
        }
        LogoPlate(channel.logo, channel.name, 104.px, 52.px, logoSize = LogoSize.Small, color = PlasmaColors.black(0.35f), shape = PlasmaShapes.SmallPlate)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.px)) {
            PlasmaText(program?.title ?: channel.name, plasmaStyle(23f, 600))
            val sub = buildList {
                add(channel.number)
                add(channel.name)
                if (program != null) add(stringResource(R.string.until, Formats.time(program.end.atZone(zone))))
            }.joinToString(" · ")
            PlasmaText(sub, plasmaStyle(19f, 500, tabular = true), color = PlasmaColors.Text2)
        }
    }
}

// ---- Reminder ------------------------------------------------------------------------------------

/**
 * A reminder at the top right (board 11): a programme starts in a minute. It takes focus so OK
 * switches, while channel and number keys still reach the player underneath.
 */
@Composable
internal fun ReminderToast(reminder: Reminder, now: Instant, onSwitch: () -> Unit, onDismiss: () -> Unit) {
    val zone = LocalPlasmaClock.current.zone
    val switch = remember { FocusRequester() }
    LaunchedEffect(reminder) {
        withFrameNanos { }
        switch.requestFocus()
    }
    Column(
        Modifier
            .offset(1224.px, 56.px)
            .width(600.px)
            .boxShadow(PlasmaColors.black(0.55f), offsetY = 30.px, blur = 70.px, cornerRadius = 22.px)
            .background(PlasmaColors.Toast, PlasmaShapes.Toast)
            .drawWithContent {
                drawContent()
                drawLine(PlasmaColors.white(0.08f), Offset(22.px.toPx(), 0.5f), Offset(size.width - 22.px.toPx(), 0.5f))
            }
            .padding(start = 26.px, end = 26.px, top = 24.px, bottom = 26.px),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(18.px)) {
            Box(Modifier.size(52.px).background(PlasmaColors.white(0.1f), PlasmaShapes.Row), contentAlignment = Alignment.Center) {
                PlasmaIcon(PlasmaIcons.BellRing, 26.px)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.px)) {
                val minutes = Duration.between(now, reminder.start).toMinutes().coerceAtLeast(1).toInt()
                PlasmaText(
                    stringResource(R.string.reminder_starts_in, pluralStringResource(R.plurals.minutes, minutes, minutes)),
                    plasmaStyle(19f, 600),
                    color = PlasmaColors.Text2,
                )
                PlasmaText(reminder.title, plasmaStyle(28f, 600, trackingPx = -0.3f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px)) {
                    LogoImage(reminder.logo, reminder.channelName, LogoSize.Inline, Modifier.overhang(4.px))
                    PlasmaText(
                        "${reminder.channelNumber} · ${Formats.range(reminder.start.atZone(zone), reminder.end.atZone(zone))}",
                        plasmaStyle(21f, 500, tabular = true),
                        color = PlasmaColors.Text2,
                    )
                }
            }
        }
        Row(Modifier.padding(top = 22.px), verticalAlignment = Alignment.CenterVertically) {
            PlasmaSurface(
                onClick = onSwitch,
                modifier = Modifier.height(56.px).focusRequester(switch),
                style = FocusStyle.Button,
                shape = PlasmaShapes.Button,
                color = PlasmaColors.Selected,
                pivot = TransformOrigin(0f, 0.5f),
                contentAlignment = Alignment.Center,
            ) {
                PlasmaText(
                    stringResource(R.string.switch_now),
                    plasmaStyle(22f, if (LocalFocused.current) 600 else 500),
                    Modifier.padding(horizontal = 26.px),
                )
            }
            Spacer(Modifier.width(20.px))
            PlasmaSurface(
                onClick = onDismiss,
                modifier = Modifier.height(56.px),
                style = FocusStyle.Button,
                shape = PlasmaShapes.Button,
                color = PlasmaColors.Selected,
                contentAlignment = Alignment.Center,
            ) {
                PlasmaText(
                    stringResource(R.string.dismiss),
                    plasmaStyle(22f, if (LocalFocused.current) 600 else 500),
                    Modifier.padding(horizontal = 24.px),
                )
            }
            Spacer(Modifier.weight(1f))
            if (reminder.autoSwitch) {
                PlasmaText(
                    stringResource(R.string.switches_at, Formats.time(reminder.start.atZone(zone))),
                    plasmaStyle(19f, 500, tabular = true),
                    color = PlasmaColors.Text3,
                )
            }
        }
    }
}
