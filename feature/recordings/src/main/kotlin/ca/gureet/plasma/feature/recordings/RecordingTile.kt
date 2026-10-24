// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.recordings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalAccent
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.ImageBadge
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.component.RedDot
import ca.gureet.plasma.core.design.component.Scrim
import ca.gureet.plasma.core.design.component.WatchedBadge
import ca.gureet.plasma.core.design.component.verticalScrim
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import ca.gureet.plasma.core.design.R as DesignR

/** A recording's thumbnail is 184 px tall in the four-column grid (board 17). */
internal val TileHeight = 184.px

/** The caption sits 14 px under the thumbnail and moves 15 px further down with focus. */
private val CaptionGap = 14.px
internal val CaptionShift = 15.px

/**
 * A tile in the Recorded grid (board 17): the thumbnail with the channel's logo and a badge (REC,
 * "3 EPISODES" or the watched check), progress along the bottom, and the title and meta line under
 * it. A series folder has two layers peeking above it. Focused, it grows from its top edge and its
 * caption moves down to make room. While selecting, the badge becomes a check box.
 */
@Composable
internal fun RecordingTile(
    tile: RecordedTile,
    now: ZonedDateTime,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val lead = when (tile) {
        is RecordedTile.Single -> tile.recording
        is RecordedTile.Folder -> tile.newest
    }
    val dim = tile is RecordedTile.Single && lead.watched
    val description = tile.description(now, selecting && selected)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(TileHeight)) {
            if (tile is RecordedTile.Folder) FolderLayers()
            PlasmaSurface(
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier.fillMaxSize().semantics { contentDescription = description },
                style = FocusStyle.Tile,
                shape = PlasmaShapes.Thumbnail,
                color = PlasmaColors.Surface2,
                pivot = TransformOrigin(0.5f, 0f),
                onFocusChanged = {
                    focused = it
                    onFocusChanged(it)
                },
            ) {
                PlasmaImage(lead.art, Modifier.fillMaxSize())
                Scrim(verticalScrim(0f to PlasmaColors.black(if (lead.inProgress) 0.62f else 0.6f), 0.5f to Color.Transparent))
                Box(Modifier.padding(start = 12.px).height(52.px), contentAlignment = Alignment.CenterStart) {
                    LogoImage(lead.logo, lead.channelName, LogoSize.Recording)
                }
                Box(Modifier.align(Alignment.TopEnd).padding(top = 12.px, end = 12.px)) {
                    when {
                        selecting -> SelectionMark(selected)
                        tile is RecordedTile.Folder -> ImageBadge(
                            pluralStringResource(R.plurals.episodes_badge, tile.episodes.size, tile.episodes.size),
                            background = PlasmaColors.black(0.62f),
                            trackingPx = 1f,
                            weight = 700,
                        )
                        lead.inProgress -> ImageBadge(
                            stringResource(R.string.rec),
                            background = PlasmaColors.black(0.62f),
                            weight = 700,
                            leading = { RedDot(9.px) },
                        )
                        lead.watched -> WatchedBadge()
                    }
                }
                if (tile is RecordedTile.Single) {
                    val bar = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    when {
                        lead.inProgress ->
                            ProgressBar(lead.progressAt(now.toInstant()), bar, color = PlasmaColors.Live, track = PlasmaColors.white(0.22f), rounded = false)
                        lead.resume != null ->
                            ProgressBar(lead.resume, bar, color = LocalAccent.current, track = PlasmaColors.white(0.22f), rounded = false)
                    }
                }
            }
        }
        val shift by animateDpAsState(if (focused) CaptionShift else 0.px, PlasmaMotion.focus(), label = "captionShift")
        Column(
            Modifier.padding(top = CaptionGap).offset { IntOffset(0, shift.roundToPx()) },
            verticalArrangement = Arrangement.spacedBy(3.px),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                if (tile.showsUnwatchedDot) Box(Modifier.size(9.px).background(PlasmaColors.Text, CircleShape))
                PlasmaText(
                    tile.title,
                    plasmaStyle(23f, if (focused) 600 else 500),
                    color = when {
                        focused -> PlasmaColors.Text
                        dim -> PlasmaColors.TextSoft
                        else -> PlasmaColors.TextSoft3
                    },
                )
            }
            PlasmaText(tile.meta(now), plasmaStyle(20f, 500, tabular = true), color = if (dim) PlasmaColors.Text3 else PlasmaColors.Text2)
        }
        Spacer(Modifier.height(CaptionShift))
    }
}

/** The two layers that peek above a series folder (brief 2.9). */
@Composable
private fun FolderLayers() {
    Box(
        Modifier
            .offset(y = (-16).px)
            .padding(horizontal = 28.px)
            .fillMaxWidth()
            .height(30.px)
            .background(PlasmaColors.StackFar, RoundedCornerShape(12.px)),
    )
    Box(
        Modifier
            .offset(y = (-8).px)
            .padding(horizontal = 14.px)
            .fillMaxWidth()
            .height(30.px)
            .background(PlasmaColors.StackNear, RoundedCornerShape(13.px)),
    )
}

/** While selecting: an empty ring, or a white check once the tile is picked. */
@Composable
private fun SelectionMark(selected: Boolean) {
    if (selected) {
        Box(Modifier.size(30.px).background(PlasmaColors.Text, CircleShape), contentAlignment = Alignment.Center) {
            PlasmaIcon(PlasmaIcons.Check, 16.px, tint = PlasmaColors.OnFocus, stroke = 3f)
        }
    } else {
        Box(Modifier.size(30.px).background(PlasmaColors.black(0.62f), CircleShape).border(2.px, PlasmaColors.white(0.8f), CircleShape))
    }
}

/** A white dot before the title of a recording nobody has played, or a folder with some (brief 2.9). */
private val RecordedTile.showsUnwatchedDot: Boolean
    get() = when (this) {
        is RecordedTile.Single -> recording.unwatched && !recording.inProgress
        is RecordedTile.Folder -> unwatchedCount > 0
    }

/**
 * The line under the title: "Recording · 19:45–22:00" while it records, "2 unwatched · Series rule"
 * for a folder, the amber "Deletes in 2 days · keep last 3" when retention is about to remove it,
 * or when it aired and how long it is ("Sat 17 Oct · 4 h 55 min left" once started).
 */
@Composable
internal fun RecordedTile.meta(now: ZonedDateTime): AnnotatedString {
    val separator = " · "
    return when (this) {
        is RecordedTile.Folder -> AnnotatedString(
            buildList {
                add(
                    if (unwatchedCount > 0) {
                        pluralStringResource(R.plurals.unwatched_count, unwatchedCount, unwatchedCount)
                    } else {
                        pluralStringResource(R.plurals.episode_count, episodes.size, episodes.size)
                    },
                )
                if (byRule) add(stringResource(R.string.series_rule))
            }.joinToString(separator),
        )
        is RecordedTile.Single -> {
            val r = recording
            val today = now.toLocalDate()
            when {
                r.inProgress -> {
                    val label = stringResource(R.string.recording_now)
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = PlasmaColors.LiveText)) { append(label) }
                        append(separator)
                        append(Formats.range(r.start.withZoneSameInstant(now.zone), r.end.withZoneSameInstant(now.zone)))
                    }
                }
                r.trouble != null -> {
                    val label = stringResource(if (r.trouble == RecordingTrouble.Failed) R.string.recording_failed else R.string.recording_incomplete)
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = PlasmaColors.Warn)) { append(label) }
                        append(separator)
                        append(airedLabel(r, now))
                    }
                }
                r.deletesSoon(today) -> {
                    val warning = deletesText(daysUntil(checkNotNull(r.deletesOn), today))
                    val keep = r.keepLast?.let { pluralStringResource(R.plurals.keep_last, it, it) }
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = PlasmaColors.Warn)) { append(warning) }
                        if (keep != null) {
                            append(separator)
                            append(keep)
                        }
                    }
                }
                else -> {
                    val length = r.resume?.let { Formats.left(Duration.ofMillis((r.duration.toMillis() * (1f - it)).toLong())) }
                        ?: Formats.duration(r.duration)
                    val kept = stringResource(R.string.kept)
                    val rest = listOfNotNull(kept.takeIf { r.kept }, airedLabel(r, now), length).joinToString(separator)
                    // Its copy to the network folder first (brief 5.6): "Copying…", then "On nas.home.lan".
                    val copy = when (val copy = r.copy) {
                        null -> null
                        RecordingCopy.Waiting -> stringResource(R.string.copy_waiting)
                        RecordingCopy.Copying -> stringResource(R.string.copy_copying)
                        is RecordingCopy.Copied -> stringResource(R.string.copy_on, copy.host)
                        RecordingCopy.Failed -> stringResource(R.string.copy_failed)
                    }
                    buildAnnotatedString {
                        if (copy != null) {
                            withStyle(SpanStyle(color = if (r.copy == RecordingCopy.Failed) PlasmaColors.Warn else PlasmaColors.Text2)) { append(copy) }
                            append(separator)
                        }
                        append(rest)
                    }
                }
            }
        }
    }
}

/** "Deletes in 2 days", "Deletes tomorrow", "Deletes today". */
@Composable
internal fun deletesText(days: Long): String = when {
    days <= 0L -> stringResource(R.string.deletes_today)
    days == 1L -> stringResource(R.string.deletes_tomorrow)
    else -> pluralStringResource(R.plurals.deletes_in, days.toInt(), days.toInt())
}

/**
 * When it aired: "Yesterday", "Sun 18 Oct". News adds the time ("Yesterday, 20:30"), since a news
 * channel airs a bulletin of the same name several times a day.
 */
@Composable
private fun airedLabel(recording: RecordingItem, now: ZonedDateTime): String {
    val start = recording.start.withZoneSameInstant(now.zone)
    val days = ChronoUnit.DAYS.between(start.toLocalDate(), now.toLocalDate())
    val time = Formats.time(start)
    val withTime = recording.kind == RecordingKind.News
    return when (days) {
        0L -> if (withTime) stringResource(DesignR.string.today_at, time) else stringResource(DesignR.string.today)
        1L -> if (withTime) stringResource(DesignR.string.yesterday_at, time) else stringResource(DesignR.string.yesterday)
        else -> if (withTime) stringResource(DesignR.string.date_at, Formats.date(start), time) else Formats.date(start)
    }
}

/** What TalkBack reads for a tile: "Harbour City v Redmoor, recording now". */
@Composable
private fun RecordedTile.description(now: ZonedDateTime, selected: Boolean): String {
    val base = when (this) {
        is RecordedTile.Folder -> pluralStringResource(R.plurals.described_folder, episodes.size, title, episodes.size)
        is RecordedTile.Single -> {
            val r = recording
            val today = now.toLocalDate()
            when {
                r.inProgress -> stringResource(R.string.described_recording_now, title)
                r.deletesSoon(today) -> stringResource(R.string.described_deletes, title, deletesText(daysUntil(checkNotNull(r.deletesOn), today)))
                r.watched -> stringResource(R.string.described_watched, title)
                r.unwatched -> stringResource(R.string.described_unwatched, title)
                else -> title
            }
        }
    }
    return if (selected) stringResource(R.string.described_selected, base) else base
}
