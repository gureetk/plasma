// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.SpaceMono
import ca.gureet.plasma.core.design.component.LogoPlate
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.outline
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

/** A setting's options under its description, the current one marked (board 22's Home Button). */
@Composable
internal fun ChoicesPreview(options: List<String>, selected: Int, top: Dp = 30.px) {
    Column(Modifier.padding(top = top).width(560.px), verticalArrangement = Arrangement.spacedBy(2.px)) {
        options.forEachIndexed { i, option ->
            val chosen = i == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(58.px)
                    .background(if (chosen) PlasmaColors.Surface1 else Color.Transparent, PlasmaShapes.Tab)
                    .padding(horizontal = 22.px),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlasmaText(option, plasmaStyle(23f, 500), Modifier.weight(1f), color = if (chosen) PlasmaColors.Text else PlasmaColors.Text2)
                if (chosen) PlasmaIcon(PlasmaIcons.Check, 22.px, tint = PlasmaColors.Text, stroke = 2.6f)
            }
        }
    }
}

/** The sources on Settings' own detail: each with its status on the right (board 19). */
@Composable
internal fun SourceStatusLines(sources: List<Pair<String, Status>>) {
    Column(Modifier.padding(top = 30.px).width(560.px), verticalArrangement = Arrangement.spacedBy(14.px)) {
        for ((name, status) in sources) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PlasmaText(name, plasmaStyle(22f, 500), Modifier.weight(1f), color = PlasmaColors.TextSoft3)
                StatusLine(status, sizePx = 22f, weight = 500)
            }
        }
    }
}

/** A source's details as label and value pairs (board 20): server, MAC address, subscription… */
@Composable
internal fun DetailGrid(rows: List<Pair<String, String>>, monospaceDigits: Set<String> = emptySet()) {
    Column(Modifier.padding(top = 34.px).width(640.px), verticalArrangement = Arrangement.spacedBy(16.px)) {
        for ((label, value) in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.px)) {
                PlasmaText(label, plasmaStyle(22f, 500, lineHeight = 1.3f), Modifier.width(220.px), color = PlasmaColors.Text3)
                PlasmaText(
                    value,
                    plasmaStyle(22f, 500, lineHeight = 1.3f, trackingPx = if (label in monospaceDigits) 0.3f else 0f, tabular = label in monospaceDigits),
                    Modifier.weight(1f),
                    color = PlasmaColors.TextSoft3,
                    maxLines = 3,
                )
            }
        }
    }
}

/** One engine on Playback's detail (board 27): its name and what it's for, and its role. */
@Composable
internal fun EngineCards(engines: List<Triple<String, String, Status>>) {
    Column(Modifier.padding(top = 26.px).width(640.px), verticalArrangement = Arrangement.spacedBy(8.px)) {
        for ((name, line, status) in engines) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(PlasmaColors.Surface1, PlasmaShapes.Tab)
                    .padding(horizontal = 20.px, vertical = 16.px),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.px)) {
                    PlasmaText(name, plasmaStyle(23f, 600))
                    PlasmaText(line, plasmaStyle(19f, 500), color = PlasmaColors.Text3)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.px)) {
                    Box(Modifier.size(9.px).background(if (status.tone == Tone.Ok) PlasmaColors.Ok else PlasmaColors.Text4, CircleShape))
                    PlasmaText(status.text, plasmaStyle(20f, 600), color = if (status.tone == Tone.Ok) PlasmaColors.OkText else PlasmaColors.Text2)
                }
            }
        }
    }
}

/** What the sound system is and which formats it takes (board 28). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AudioCard(connectedLabel: String, connection: String, supportsLabel: String, supported: List<String>, unsupported: List<String>) {
    OutlinedCard(top = 28.px) {
        Column(verticalArrangement = Arrangement.spacedBy(6.px)) {
            PlasmaText(connectedLabel, plasmaStyle(20f, 600), color = PlasmaColors.Text3)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                Box(Modifier.size(10.px).background(PlasmaColors.Ok, CircleShape))
                PlasmaText(connection, plasmaStyle(24f, 500))
            }
            PlasmaText(supportsLabel, plasmaStyle(20f, 600), Modifier.padding(top = 12.px), color = PlasmaColors.Text3)
            FlowRow(Modifier.padding(top = 6.px), horizontalArrangement = Arrangement.spacedBy(8.px), verticalArrangement = Arrangement.spacedBy(8.px)) {
                for (format in supported) FormatChip(format, supported = true)
                for (format in unsupported) FormatChip(format, supported = false)
            }
        }
    }
}

@Composable
private fun FormatChip(name: String, supported: Boolean) {
    Row(
        Modifier
            .height(40.px)
            .background(if (supported) PlasmaColors.Ok.copy(alpha = 0.12f) else PlasmaColors.white(0.05f), RoundedCornerShape(10.px))
            .padding(horizontal = 14.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.px),
    ) {
        if (supported) {
            PlasmaIcon(PlasmaIcons.Check, 16.px, tint = PlasmaColors.Ok, stroke = 3f)
        } else {
            Box(Modifier.size(12.px, 2.px).background(PlasmaColors.Text4, RoundedCornerShape(1.px)))
        }
        PlasmaText(name, plasmaStyle(19f, 600), color = if (supported) PlasmaColors.OkChipText else PlasmaColors.Text4)
    }
}

/**
 * Board 29's two examples side by side: the same three favorites with their own numbers, and
 * counted from 1. The chosen way is filled and outlined.
 */
@Composable
internal fun NumberingExamples(
    favorites: List<Triple<Int, String, String?>>,
    keepLabel: String,
    countLabel: String,
    countFromOne: Boolean,
) {
    Row(Modifier.padding(top = 28.px).width(640.px), horizontalArrangement = Arrangement.spacedBy(20.px)) {
        NumberingCard(keepLabel, favorites.map { it.first.toString() to it }, chosen = !countFromOne, Modifier.weight(1f))
        NumberingCard(countLabel, favorites.mapIndexed { i, it -> (i + 1).toString() to it }, chosen = countFromOne, Modifier.weight(1f))
    }
}

@Composable
private fun NumberingCard(label: String, rows: List<Pair<String, Triple<Int, String, String?>>>, chosen: Boolean, modifier: Modifier) {
    Column(
        modifier
            .background(if (chosen) PlasmaColors.white(0.08f) else Color.Transparent, PlasmaShapes.Card)
            .outline(1.5f.px, PlasmaColors.white(if (chosen) 0.5f else 0.1f), PlasmaShapes.Card)
            .padding(start = 20.px, end = 20.px, top = 18.px, bottom = 22.px),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.px),
    ) {
        Row(Modifier.height(26.px), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px)) {
            if (chosen) PlasmaIcon(PlasmaIcons.Check, 18.px, tint = PlasmaColors.Text, stroke = 3f)
            PlasmaText(label, plasmaStyle(20f, 600), color = if (chosen) PlasmaColors.Text else PlasmaColors.Text3)
        }
        for ((number, channel) in rows) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.px)) {
                PlasmaText(
                    number,
                    plasmaStyle(25f, 600, tabular = true),
                    Modifier.width(56.px),
                    color = if (chosen) PlasmaColors.Text else PlasmaColors.Text2,
                    align = TextAlign.End,
                )
                LogoPlate(channel.third, channel.second, 108.px, 52.px, logoSize = LogoSize.Small, shape = PlasmaShapes.SmallPlate)
            }
        }
    }
}

/**
 * The drive and what fills it (board 31): a bar split into recordings, local catch-up and the
 * rewind buffer, a legend, and a warning about connections under a rule.
 */
@Composable
internal fun StorageBreakdown(
    drive: String,
    freeLine: String,
    parts: List<Pair<String, Float>>,
    warning: String?,
) {
    OutlinedCard(top = 28.px) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                PlasmaIcon(PlasmaIcons.Drive, 24.px, tint = PlasmaColors.Text)
                PlasmaText(drive, plasmaStyle(24f, 600))
            }
            PlasmaText(freeLine, plasmaStyle(21f, 500, tabular = true), color = PlasmaColors.Text2)
        }
        val colors = listOf(PlasmaColors.Text, PlasmaColors.Text2, PlasmaColors.Text4)
        Row(
            Modifier
                .padding(top = 16.px)
                .fillMaxWidth()
                .height(10.px)
                .clip(RoundedCornerShape(5.px))
                .background(PlasmaColors.white(0.12f)),
            horizontalArrangement = Arrangement.spacedBy(2.px),
        ) {
            parts.forEachIndexed { i, (_, fraction) ->
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(colors[i % colors.size]))
            }
        }
        Row(Modifier.padding(top = 14.px), horizontalArrangement = Arrangement.spacedBy(22.px), verticalAlignment = Alignment.CenterVertically) {
            parts.forEachIndexed { i, (label, _) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px)) {
                    Box(Modifier.size(10.px).background(colors[i % colors.size], CircleShape))
                    PlasmaText(label, plasmaStyle(20f, 500, tabular = true), color = PlasmaColors.Text2)
                }
            }
        }
        if (warning != null) {
            Box(Modifier.padding(top = 18.px).fillMaxWidth().height(1.px).background(PlasmaColors.Divider))
            Row(Modifier.padding(top = 16.px), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                Box(Modifier.size(10.px).background(PlasmaColors.Warn, CircleShape))
                PlasmaText(warning, plasmaStyle(21f, 500), color = PlasmaColors.WarnText)
            }
        }
    }
}

/** Where Continue Watching's other items come from (board 32): the Play Next row and Jellyfin. */
@Composable
internal fun ContinueSourcesCard(rows: List<Triple<String, Status, String>>) {
    OutlinedCard(top = 28.px) {
        Column(verticalArrangement = Arrangement.spacedBy(6.px)) {
            rows.forEachIndexed { i, (label, status, count) ->
                PlasmaText(label, plasmaStyle(20f, 600), Modifier.padding(top = if (i == 0) 0.px else 14.px), color = PlasmaColors.Text3)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                        Box(Modifier.size(10.px).background(if (status.tone == Tone.Ok) PlasmaColors.Ok else PlasmaColors.Text4, CircleShape))
                        PlasmaText(status.text, plasmaStyle(24f, 500))
                    }
                    PlasmaText(count, plasmaStyle(21f, 500, tabular = true), color = PlasmaColors.Text2)
                }
            }
        }
    }
}

/** The network folder and the layout Plasma writes there, in Space Mono (board 34). */
@Composable
internal fun FolderCard(folder: String, free: String, layout: AnnotatedString) {
    OutlinedCard(top = 24.px, horizontal = 22.px, topPadding = 20.px) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.px)) {
            PlasmaText(folder, plasmaStyle(22f, 600), Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px)) {
                Box(Modifier.size(9.px).background(PlasmaColors.Ok, CircleShape))
                PlasmaText(free, plasmaStyle(19f, 500, tabular = true), color = PlasmaColors.Text2)
            }
        }
        Box(
            Modifier
                .padding(top = 14.px)
                .fillMaxWidth()
                .background(PlasmaColors.Code, RoundedCornerShape(10.px))
                .outline(1.px, PlasmaColors.white(0.06f), RoundedCornerShape(10.px))
                .padding(horizontal = 16.px, vertical = 14.px),
        ) {
            PlasmaText(layout, plasmaStyle(15f, 400, lineHeight = 1.55f, family = SpaceMono), color = PlasmaColors.Text2, maxLines = 12)
        }
    }
}
