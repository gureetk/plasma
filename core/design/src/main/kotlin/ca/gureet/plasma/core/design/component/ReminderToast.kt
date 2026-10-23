// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.R
import ca.gureet.plasma.core.design.boxShadow
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.overhang
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import java.time.Duration
import java.time.Instant

/** What a reminder is for (brief 4.11 and 5.7). */
data class ReminderInfo(
    val channelId: String,
    val channelNumber: String,
    val logo: String?,
    val channelName: String,
    val title: String,
    val start: Instant,
    val end: Instant,
    /** Settings › Reminders › Switch Automatically. */
    val autoSwitch: Boolean = false,
)

/**
 * A reminder at the top right (board 11): a programme starts in a minute. It takes focus so OK
 * switches; in the player, channel and number keys still reach the player underneath.
 */
@Composable
fun ReminderToast(reminder: ReminderInfo, now: Instant, onSwitch: () -> Unit, onDismiss: () -> Unit) {
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
                val line = if (now.isBefore(reminder.start)) {
                    val minutes = Duration.between(now, reminder.start).toMinutes().coerceAtLeast(1).toInt()
                    stringResource(R.string.reminder_starts_in, pluralStringResource(R.plurals.minutes, minutes, minutes))
                } else {
                    // It stays until it's acted on (brief 2.6), so it may still be up once the programme starts.
                    stringResource(R.string.reminder_started, Formats.time(reminder.start.atZone(zone)))
                }
                PlasmaText(line, plasmaStyle(19f, 600), color = PlasmaColors.Text2)
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
