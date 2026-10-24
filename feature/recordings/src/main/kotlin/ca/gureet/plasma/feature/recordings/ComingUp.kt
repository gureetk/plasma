// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.recordings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.StatusDot
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.design.ring
import ca.gureet.plasma.core.model.Formats
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import ca.gureet.plasma.core.design.R as DesignR

/**
 * Coming Up (board 17): the next three scheduled recordings as cards, with whether there are
 * conflicts and how many of the source's connections recordings use. OK opens a card in the Guide;
 * Menu opens its options.
 */
@Composable
internal fun ComingUp(
    items: List<ScheduledItem>,
    status: @Composable () -> Unit,
    now: ZonedDateTime,
    onOpen: (ScheduledItem) -> Unit,
    onOptions: (ScheduledItem) -> Unit,
    returnFocusId: String?,
    returnFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(44.px), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.px)) {
                PlasmaText(stringResource(R.string.coming_up), PlasmaType.Section, Modifier.alignByBaseline())
                PlasmaText(
                    if (items.isEmpty()) {
                        stringResource(R.string.nothing_scheduled)
                    } else {
                        pluralStringResource(R.plurals.scheduled_count, items.size, items.size)
                    },
                    plasmaStyle(22f, 500),
                    Modifier.alignByBaseline(),
                    color = PlasmaColors.Text3,
                )
            }
            Spacer(Modifier.weight(1f))
            status()
        }
        if (items.isNotEmpty()) {
            Row(Modifier.padding(top = 14.px).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.px)) {
                val shown = items.take(3)
                for (item in shown) {
                    ComingUpCard(
                        item,
                        now,
                        onClick = { onOpen(item) },
                        onLongClick = { onOptions(item) },
                        modifier = Modifier
                            .weight(1f)
                            .then(if (item.id == returnFocusId) Modifier.focusRequester(returnFocus) else Modifier),
                    )
                }
                // Fewer than three keep the cards their width.
                repeat(3 - shown.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** "● No conflicts · recordings use 1 of 2 connections" (brief 5.6). */
@Composable
internal fun ConnectionStatus(inUse: Int, limit: Int?, conflicts: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
        StatusDot(if (conflicts == 0) PlasmaColors.Ok else PlasmaColors.Warn)
        val state = if (conflicts == 0) {
            stringResource(R.string.no_conflicts)
        } else {
            pluralStringResource(R.plurals.conflict_count, conflicts, conflicts)
        }
        // A source without a connection limit has nothing to count against.
        val connections = limit?.let { " · " + pluralStringResource(R.plurals.connections_in_use, it, inUse, it) }.orEmpty()
        PlasmaText(state + connections, plasmaStyle(21f, 500), color = PlasmaColors.Text2)
    }
}

/**
 * A 100 px card: the title, then the channel's logo and when. Its logo sits on the card, so focus
 * lifts the card and lightens it rather than filling it white.
 */
@Composable
private fun ComingUpCard(item: ScheduledItem, now: ZonedDateTime, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    val kind = stringResource(if (item.byRule) R.string.series_rule_recording else R.string.single_recording)
    val conflict = item.conflict?.let { stringResource(R.string.in_conflict) }
    val whenText = comingUpWhen(item, now)
    PlasmaSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .height(100.px)
            .semantics { contentDescription = listOfNotNull(item.title, item.channelName, whenText, kind, conflict).joinToString(", ") },
        style = FocusStyle.Tile,
        shape = PlasmaShapes.Card,
        color = PlasmaColors.Surface1,
        focusedScale = 1.04f,
        contentAlignment = Alignment.CenterStart,
    ) {
        if (LocalFocused.current) Box(Modifier.fillMaxSize().background(PlasmaColors.Surface3))
        Row(Modifier.padding(horizontal = 22.px), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.px)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.px)) {
                PlasmaText(item.title, plasmaStyle(24f, 600), color = PlasmaColors.Text)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px)) {
                    // A tall logo hangs past the line rather than making it taller, as on the board.
                    Box(Modifier.height(24.px), contentAlignment = Alignment.Center) {
                        LogoImage(item.logo, item.channelName, LogoSize.Inline, Modifier.wrapContentHeight(unbounded = true))
                    }
                    PlasmaText(whenText, plasmaStyle(20f, 500, tabular = true), color = PlasmaColors.Text2)
                    // More at once than there are connections: OK says what to skip.
                    if (conflict != null) PlasmaText(conflict, plasmaStyle(20f, 600), color = PlasmaColors.Warn)
                }
            }
            RuleIcon(item.byRule)
        }
    }
}

/**
 * A single red dot for a one-off recording, two overlapping dots for a series rule (brief 2.9), on
 * a 40 px tinted square.
 */
@Composable
internal fun RuleIcon(series: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(40.px).background(PlasmaColors.Live.copy(alpha = 0.14f), RoundedCornerShape(10.px)),
        contentAlignment = Alignment.Center,
    ) {
        if (series) {
            Box(Modifier.size(24.px, 14.px)) {
                Box(Modifier.size(14.px).alpha(0.55f).background(PlasmaColors.Live, CircleShape))
                Box(
                    Modifier
                        .offset(x = 10.px)
                        .size(14.px)
                        .ring(2.5f.px, PlasmaColors.RuleDotRing, 7.px)
                        .background(PlasmaColors.Live, CircleShape),
                )
            }
        } else {
            Box(Modifier.size(14.px).background(PlasmaColors.Live, CircleShape))
        }
    }
}

/** When a scheduled recording is (see [scheduleWhen]). */
@Composable
internal fun comingUpWhen(item: ScheduledItem, now: ZonedDateTime): String = scheduleWhen(item.start, item.end, now)

/**
 * When something scheduled is: "Tonight, 00:30–03:00" for this evening and the small hours after
 * it, "Today, 15:00–16:00" earlier today, otherwise the day and start time ("Thu 22 Oct, 20:00").
 * Without an [end] (a reminder), only the start time.
 */
@Composable
internal fun scheduleWhen(start: ZonedDateTime, end: ZonedDateTime?, now: ZonedDateTime): String {
    val local = start.withZoneSameInstant(now.zone)
    val times = if (end != null) Formats.range(local, end.withZoneSameInstant(now.zone)) else Formats.time(local)
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), local.toLocalDate())
    return when {
        days == 0L && local.hour >= 18 || days == 1L && local.hour < 5 -> stringResource(DesignR.string.tonight_at, times)
        days == 0L -> stringResource(DesignR.string.today_at, times)
        else -> stringResource(DesignR.string.date_at, Formats.date(local), Formats.time(local))
    }
}
