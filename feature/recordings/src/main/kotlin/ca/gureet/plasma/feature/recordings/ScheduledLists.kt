// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.component.LogoPlate
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.PlasmaColumn
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import java.time.Duration
import java.time.ZonedDateTime

private const val LIST_TOP_PX = 206
private const val LIST_BOTTOM_PX = 984

/**
 * Recordings › Scheduled (brief 4.17): every scheduled recording, then the reminders as a section
 * of their own (4.35). OK shows one in the Guide; Menu offers to cancel it.
 */
@Composable
internal fun ScheduledList(
    scheduled: List<ScheduledItem>,
    reminders: List<ReminderItem>,
    status: @Composable () -> Unit,
    now: ZonedDateTime,
    onShow: (channelName: String, start: ZonedDateTime) -> Unit,
    onScheduledOptions: (ScheduledItem) -> Unit,
    onReminderOptions: (ReminderItem) -> Unit,
    onFocusItem: (String) -> Unit,
    returnFocusId: String?,
    returnFocus: FocusRequester,
    listFocus: FocusRequester,
) {
    ListHeader(
        stringResource(R.string.scheduled),
        if (scheduled.isEmpty()) stringResource(R.string.nothing_scheduled) else pluralStringResource(R.plurals.scheduled_count, scheduled.size, scheduled.size),
        status,
    )
    if (scheduled.isEmpty() && reminders.isEmpty()) {
        EmptyText(stringResource(R.string.empty_scheduled))
        return
    }
    PlasmaColumn(
        modifier = Modifier
            .offset(430.px, LIST_TOP_PX.px)
            .size(1394.px, (LIST_BOTTOM_PX - LIST_TOP_PX).px)
            .focusRequester(listFocus),
        gap = 12.px,
        contentPadding = PaddingValues(vertical = 8.px),
    ) {
        items(scheduled, key = { it.id }) { item ->
            val whenText = comingUpWhen(item, now)
            ListRow(
                logo = item.logo,
                logoName = item.channelName,
                title = item.title,
                line = "${item.channelName} · $whenText",
                trailing = {
                    PlasmaText(paddingText(item.startsEarly, item.endsLate), plasmaStyle(20f, 500, tabular = true), color = secondary())
                    RuleIcon(item.byRule)
                },
                onClick = { onShow(item.channelName, item.start) },
                onLongClick = { onScheduledOptions(item) },
                onFocused = { onFocusItem(item.id) },
                modifier = if (item.id == returnFocusId) Modifier.focusRequester(returnFocus) else Modifier,
            )
        }
        if (reminders.isNotEmpty()) {
            item(key = "reminders") {
                PlasmaText(stringResource(R.string.reminders), plasmaStyle(26f, 600, trackingPx = -0.3f), Modifier.padding(top = 22.px, bottom = 4.px))
            }
            items(reminders, key = { it.id }) { reminder ->
                val whenText = scheduleWhen(reminder.start, end = null, now)
                ListRow(
                    logo = reminder.logo,
                    logoName = reminder.channelName,
                    title = reminder.title,
                    line = "${reminder.channelName} · $whenText",
                    trailing = { PlasmaIcon(PlasmaIcons.Bell, 24.px, tint = secondary()) },
                    onClick = { onShow(reminder.channelName, reminder.start) },
                    onLongClick = { onReminderOptions(reminder) },
                    onFocused = { onFocusItem(reminder.id) },
                    modifier = if (reminder.id == returnFocusId) Modifier.focusRequester(returnFocus) else Modifier,
                )
            }
        }
    }
}

/** Recordings › Series Rules: each rule, the channel it records from and how many it keeps. */
@Composable
internal fun RulesList(
    rules: List<SeriesRuleItem>,
    onOptions: (SeriesRuleItem) -> Unit,
    onFocusItem: (String) -> Unit,
    returnFocusId: String?,
    returnFocus: FocusRequester,
    listFocus: FocusRequester,
) {
    ListHeader(stringResource(R.string.series_rules), pluralStringResource(R.plurals.rule_count, rules.size, rules.size), status = null)
    if (rules.isEmpty()) {
        EmptyText(stringResource(R.string.empty_rules))
        return
    }
    PlasmaColumn(
        modifier = Modifier
            .offset(430.px, LIST_TOP_PX.px)
            .size(1394.px, (LIST_BOTTOM_PX - LIST_TOP_PX).px)
            .focusRequester(listFocus),
        gap = 12.px,
        contentPadding = PaddingValues(vertical = 8.px),
    ) {
        items(rules, key = { it.id }) { rule ->
            val channel = rule.channelName ?: stringResource(R.string.any_channel)
            val keep = rule.keepLast?.let { pluralStringResource(R.plurals.keep_last_rule, it, it) } ?: stringResource(R.string.keep_all)
            ListRow(
                logo = rule.logo,
                logoName = channel,
                title = rule.title,
                line = "$channel · $keep",
                trailing = {
                    PlasmaText(paddingText(rule.startsEarly, rule.endsLate), plasmaStyle(20f, 500, tabular = true), color = secondary())
                    RuleIcon(series = true)
                },
                onClick = { onOptions(rule) },
                onLongClick = { onOptions(rule) },
                onFocused = { onFocusItem(rule.id) },
                modifier = if (rule.id == returnFocusId) Modifier.focusRequester(returnFocus) else Modifier,
            )
        }
    }
}

/** "Scheduled · 3 scheduled", with the connection status on the right, where Coming Up's header is. */
@Composable
private fun ListHeader(title: String, count: String, status: (@Composable () -> Unit)?) {
    Row(Modifier.offset(430.px, 148.px).width(1394.px).height(44.px), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.px)) {
            PlasmaText(title, PlasmaType.Section, Modifier.alignByBaseline())
            PlasmaText(count, plasmaStyle(22f, 500), Modifier.alignByBaseline(), color = PlasmaColors.Text3)
        }
        Spacer(Modifier.weight(1f))
        status?.invoke()
    }
}

@Composable
private fun EmptyText(text: String) {
    PlasmaText(text, plasmaStyle(23f, 400), Modifier.offset(430.px, (LIST_TOP_PX + 20).px), color = PlasmaColors.Text2)
}

/** A 100 px row: the channel's logo on its plate, the title and a line under it, and what's on the right. */
@Composable
private fun ListRow(
    logo: String?,
    logoName: String,
    title: String,
    line: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier,
) {
    PlasmaSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .height(100.px)
            .semantics { contentDescription = "$title, $line" },
        style = FocusStyle.Row,
        shape = PlasmaShapes.Row,
        color = PlasmaColors.Surface1,
        onFocusChanged = { if (it) onFocused() },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.padding(start = 14.px, end = 28.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.px),
        ) {
            LogoPlate(logo, logoName, 108.px, 52.px, logoSize = LogoSize.Small, shape = PlasmaShapes.SmallPlate)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.px)) {
                PlasmaText(title, plasmaStyle(24f, 600))
                PlasmaText(line, plasmaStyle(20f, 500, tabular = true), color = secondary())
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.px)) { trailing() }
        }
    }
}

@Composable
private fun secondary() = if (LocalFocused.current) PlasmaColors.OnFocus2 else PlasmaColors.Text2

/** "1 min early · 5 min late" (padding, brief 5.6). */
@Composable
private fun paddingText(early: Duration, late: Duration): String =
    stringResource(R.string.padding, Formats.duration(early), Formats.duration(late))
