// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalAccent
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PressState
import ca.gureet.plasma.core.design.component.ChoiceButton
import ca.gureet.plasma.core.design.component.ColorKey
import ca.gureet.plasma.core.design.component.ColorKeyHint
import ca.gureet.plasma.core.design.component.LiveMark
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.ProgramSheet
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.TechBadge
import ca.gureet.plasma.core.design.component.TopBar
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.design.focusRestoringGroup
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaClickable
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.player.VideoFrame
import ca.gureet.plasma.player.VideoSlot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

// ---- State ---------------------------------------------------------------------------------------

data class GuideProgram(
    val id: String,
    val title: String,
    val start: Instant,
    val end: Instant,
    val subtitle: String? = null,
    val description: String? = null,
    /** HD, 5.1, CC… */
    val badges: List<String> = emptyList(),
    val recording: Boolean = false,
    val recordingScheduled: Boolean = false,
    val reminder: Boolean = false,
)

data class GuideChannel(
    val id: String,
    val number: String,
    val name: String,
    val logo: String?,
    val frame: VideoFrame,
    /** The provider keeps an archive, so past programmes can be watched (↺). */
    val catchUp: Boolean,
    val programs: List<GuideProgram>,
)

data class GuideUiState(
    val profileName: String,
    val profileInitial: String,
    val groupName: String,
    val channels: List<GuideChannel>,
    /** The channel playing now, where focus starts. */
    val currentChannelId: String,
)

/** What OK on a programme does (brief 4.8). */
enum class ProgramAction { Play, PlayCatchUp, Sheet, Info }

class GuideActions(
    val onTab: (TopTab) -> Unit = {},
    val onSettings: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val onProgram: (channelId: String, programId: String, action: ProgramAction) -> Unit = { _, _, _ -> },
    val onGroup: () -> Unit = {},
)

// ---- Geometry (board 08) -------------------------------------------------------------------------

/** The channel column; the time area starts after it. */
private const val CHANNEL_COLUMN_PX = 316

/** Two hours fill 1,412 px: 11.77 px a minute. */
private const val WINDOW_PX = 1412f
private const val PX_PER_MS = WINDOW_PX / GuideNavigator.WINDOW
private const val GRID_TOP_PX = 540

/** The grid stops above the bottom overscan margin, where the now-line ends. */
private const val GRID_BOTTOM_PX = 1030
private const val ROW_PX = 62
private const val ROW_GAP_PX = 8

/** Where the window starts when the guide opens: so now sits in its first hour (20:41 → 20:00). */
internal fun initialWindowStart(now: ZonedDateTime): ZonedDateTime {
    val earlier = now.minusMinutes(30)
    return earlier.truncatedTo(ChronoUnit.HOURS).plusMinutes(if (earlier.minute >= 30) 30L else 0L)
}

// ---- Screen --------------------------------------------------------------------------------------

/**
 * The Guide (board 08, brief 4.8): the focused programme's details and a live preview on top, day
 * tabs and the group, then a two-hour grid. The grid moves focus itself (see [GuideNavigator]):
 * Up and Down keep to the same time, Left and Right move by programme and scroll at the edges.
 */
@Composable
fun GuideScreen(
    state: GuideUiState,
    actions: GuideActions,
    modifier: Modifier = Modifier,
    previewDwellMs: Long = PlasmaMotion.PREVIEW_DWELL_MS,
) {
    val clock = LocalPlasmaClock.current
    val zone = clock.zone
    val now = remember(clock) { clock.nowZoned() }
    val today = now.toLocalDate()
    var day by remember { mutableStateOf(today) }

    val navigator = remember(state.channels) {
        GuideNavigator(state.channels.map { ch -> ch.programs.map { GuideSlot(it.id, it.start.toEpochMilli(), it.end.toEpochMilli()) } })
    }
    val startRow = state.channels.indexOfFirst { it.id == state.currentChannelId }.coerceAtLeast(0)
    var cursor by remember {
        mutableStateOf(navigator.enter(startRow, now.toInstant().toEpochMilli(), initialWindowStart(now).toInstant().toEpochMilli()))
    }
    var gridFocused by remember { mutableStateOf(false) }
    val grid = remember { FocusRequester() }
    val toolbar = remember { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (startRow - 2).coerceAtLeast(0))

    LaunchedEffect(Unit) {
        withFrameNanos { }
        grid.requestFocus()
    }
    // Keep the focused row third from the top while there are rows above it (board 08).
    LaunchedEffect(cursor.row) {
        val first = listState.firstVisibleItemIndex
        val visible = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        if (cursor.row < first || cursor.row >= first + visible - 1) {
            listState.animateScrollToItem((cursor.row - 2).coerceAtLeast(0))
        }
    }

    // Reminders and recordings set here, until they're kept for real (M2 and M6).
    var marks by remember { mutableStateOf(emptyMap<String, Marks>()) }
    // The program whose Remind and Record sheet is open: OK on a program coming up, or Menu (4.8).
    var sheetFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    val press = remember { PressState() }
    val channel = state.channels.getOrNull(cursor.row)?.withMarks(marks)
    val program = channel?.programs?.firstOrNull { it.id == cursor.programId }
    var previewChannelId by remember { mutableStateOf(channel?.id) }
    LaunchedEffect(channel?.id) {
        if (channel?.id != previewChannelId) {
            delay(previewDwellMs)
            previewChannelId = channel?.id
        }
    }
    val previewChannel = state.channels.firstOrNull { it.id == previewChannelId }
    val windowStart = Instant.ofEpochMilli(cursor.windowStart)

    Box(modifier.fillMaxSize()) {
        TopBar(
            current = TopTab.Guide,
            profileInitial = state.profileInitial,
            profileName = state.profileName,
            onTab = actions.onTab,
            onSettings = actions.onSettings,
            onProfile = actions.onProfile,
            focusDown = toolbar,
        )

        if (channel != null) {
            SelectedProgram(channel, program, now, Modifier.offset(96.px, 146.px).width(1180.px))
        }
        GuidePreview(previewChannel, now.toInstant(), Modifier.offset(1344.px, 148.px))

        Toolbar(
            today = today,
            day = day,
            groupName = state.groupName,
            onDay = { picked ->
                day = picked
                // Other days open at the same time of day; days other than today default to 20:00 (4.8).
                val start = if (picked == today) {
                    initialWindowStart(now)
                } else {
                    picked.atTime(20, 0).atZone(zone)
                }
                val anchor = if (picked == today) now.toInstant().toEpochMilli() else start.toInstant().toEpochMilli()
                cursor = navigator.enter(cursor.row, anchor, start.toInstant().toEpochMilli())
            },
            onDayPicked = { grid.requestFocus() },
            onGroup = actions.onGroup,
            modifier = Modifier
                .offset(96.px, 428.px)
                .focusRequester(toolbar),
        )

        TimeHeader(windowStart, zone, Modifier.offset(96.px, 500.px))

        LazyColumn(
            state = listState,
            userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(ROW_GAP_PX.px),
            modifier = Modifier
                .offset(96.px, GRID_TOP_PX.px)
                .size(1728.px, (GRID_BOTTOM_PX - GRID_TOP_PX).px)
                .focusRequester(grid)
                .onFocusChanged { gridFocused = it.isFocused }
                .focusProperties { up = toolbar }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> navigator.vertical(cursor, -1)?.let { cursor = it; true } ?: false
                        Key.DirectionDown -> { navigator.vertical(cursor, +1)?.let { cursor = it }; true }
                        Key.DirectionRight -> { cursor = navigator.right(cursor); true }
                        Key.DirectionLeft -> { cursor = navigator.left(cursor); true }
                        Key.ChannelUp -> { repeat(7) { navigator.vertical(cursor, -1)?.let { cursor = it } }; true }
                        Key.ChannelDown -> { repeat(7) { navigator.vertical(cursor, +1)?.let { cursor = it } }; true }
                        else -> false
                    }
                }
                .plasmaClickable(
                    press,
                    onClick = {
                        if (channel != null && program != null) {
                            when (val action = actionFor(channel, program, now.toInstant())) {
                                ProgramAction.Sheet -> sheetFor = channel.id to program.id
                                else -> actions.onProgram(channel.id, program.id, action)
                            }
                        }
                    },
                    // Menu, or holding OK, offers Record, and Remind for a program coming up (4.8).
                    onLongClick = {
                        if (channel != null && program != null && program.end.isAfter(now.toInstant())) {
                            sheetFor = channel.id to program.id
                        }
                    },
                )
                .focusable(),
        ) {
            itemsIndexed(state.channels, key = { _, ch -> ch.id }) { index, ch ->
                GuideRow(
                    channel = ch.withMarks(marks),
                    current = ch.id == state.currentChannelId,
                    windowStart = windowStart,
                    focusedProgramId = if (gridFocused && index == cursor.row) cursor.programId else null,
                    now = now.toInstant(),
                    zone = zone,
                )
            }
        }

        NowLine(now.toInstant(), windowStart, zone, today == day)

        val sheetChannel = sheetFor?.let { (channelId, _) -> state.channels.firstOrNull { it.id == channelId }?.withMarks(marks) }
        val sheetProgram = sheetChannel?.programs?.firstOrNull { it.id == sheetFor?.second }
        SheetHost(visible = sheetProgram != null, returnFocus = grid) {
            if (sheetChannel != null && sheetProgram != null) {
                ProgramSheet(
                    title = sheetProgram.title,
                    subtitle = "${programTime(sheetProgram, now)} · ${sheetChannel.name}",
                    reminder = sheetProgram.reminder,
                    recording = sheetProgram.recordingScheduled,
                    onDismiss = { sheetFor = null },
                    onReminder = { marks = marks + (sheetProgram.id to Marks(!sheetProgram.reminder, sheetProgram.recordingScheduled)) },
                    onRecording = { marks = marks + (sheetProgram.id to Marks(sheetProgram.reminder, !sheetProgram.recordingScheduled)) },
                    canRemind = sheetProgram.start.isAfter(now.toInstant()),
                )
            }
        }
    }
}

/** A reminder and a recording set on a program in the Guide. */
private data class Marks(val reminder: Boolean, val recording: Boolean)

private fun GuideChannel.withMarks(marks: Map<String, Marks>): GuideChannel =
    if (marks.isEmpty()) {
        this
    } else {
        copy(programs = programs.map { p -> marks[p.id]?.let { p.copy(reminder = it.reminder, recordingScheduled = it.recording) } ?: p })
    }

/** "Today, 19:45–22:00", or "Thu 22 Oct, 20:00–21:00" on another day. */
@Composable
private fun programTime(program: GuideProgram, now: ZonedDateTime): String {
    val start = program.start.atZone(now.zone)
    val range = Formats.range(start, program.end.atZone(now.zone))
    return if (start.toLocalDate() == now.toLocalDate()) stringResource(R.string.today_range, range) else "${Formats.date(start)}, $range"
}

private fun actionFor(channel: GuideChannel, program: GuideProgram, now: Instant): ProgramAction = when {
    !program.end.isAfter(now) -> if (channel.catchUp) ProgramAction.PlayCatchUp else ProgramAction.Info
    program.start.isAfter(now) -> ProgramAction.Sheet
    else -> ProgramAction.Play
}

// ---- Selected programme ----------------------------------------------------------------------------

@Composable
private fun SelectedProgram(channel: GuideChannel, program: GuideProgram?, now: ZonedDateTime, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.px)) {
            LogoImage(channel.logo, channel.name, LogoSize.Guide)
            PlasmaText(channel.number, plasmaStyle(22f, 500, tabular = true), color = PlasmaColors.Text2)
            if (program != null) {
                val live = !program.start.isAfter(now.toInstant()) && program.end.isAfter(now.toInstant())
                if (live || program.recording) Box(Modifier.size(1.px, 26.px).background(PlasmaColors.white(0.2f)))
                if (live) {
                    LiveMark(stringResource(R.string.live), style = plasmaStyle(19f, 600, trackingPx = 1.4f), dot = 9.px, gap = 9.px)
                }
                if (program.recording) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.px)) {
                        RecordingIcon()
                        PlasmaText(stringResource(R.string.recording), plasmaStyle(20f, 500), color = PlasmaColors.Text2)
                    }
                }
            }
        }
        if (program == null) {
            PlasmaText(stringResource(R.string.no_information), plasmaStyle(54f, 600, trackingPx = -1.2f, lineHeight = 1.06f), Modifier.padding(top = 14.px))
            return@Column
        }
        PlasmaText(
            program.title,
            plasmaStyle(54f, 600, trackingPx = -1.2f, lineHeight = 1.06f),
            Modifier.padding(top = 14.px),
        )
        Row(
            Modifier.padding(top = 12.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.px),
        ) {
            val meta = plasmaStyle(23f, 500)
            PlasmaText(programTime(program, now), meta.copy(fontFeatureSettings = "tnum"), color = PlasmaColors.Text2)
            if (program.subtitle != null) {
                PlasmaText("·", meta, color = PlasmaColors.Text4)
                PlasmaText(program.subtitle, meta, color = PlasmaColors.Text2)
            }
            if (program.badges.isNotEmpty()) {
                Row(Modifier.padding(start = 6.px), horizontalArrangement = Arrangement.spacedBy(8.px)) {
                    for (badge in program.badges) TechBadge(badge, borderColor = PlasmaColors.Text4, color = PlasmaColors.TextSoft)
                }
            }
        }
        if (program.description != null) {
            PlasmaText(
                program.description,
                plasmaStyle(23f, 400, lineHeight = 1.48f),
                Modifier.padding(top = 14.px).width(1060.px),
                color = PlasmaColors.Text2,
                maxLines = 3,
            )
        }
    }
}

/** ◉ with a red centre: "Recording". */
@Composable
private fun RecordingIcon() {
    Box(Modifier.size(20.px), contentAlignment = Alignment.Center) {
        PlasmaIcon(PlasmaIcons.Ring, 20.px, stroke = 2f, tint = PlasmaColors.Text2)
        Box(Modifier.size(6.67f.px).background(PlasmaColors.Live, CircleShape))
    }
}

// ---- Preview -------------------------------------------------------------------------------------

@Composable
private fun GuidePreview(channel: GuideChannel?, now: Instant, modifier: Modifier) {
    Box(modifier.size(480.px, 270.px)) {
        VideoSlot(channel?.frame, Modifier.fillMaxSize(), cornerRadius = 14.px, background = PlasmaColors.VideoWell)
        val live = channel?.programs?.firstOrNull { !it.start.isAfter(now) && it.end.isAfter(now) }
        if (live != null) {
            val fraction = (now.toEpochMilli() - live.start.toEpochMilli()).toFloat() / (live.end.toEpochMilli() - live.start.toEpochMilli())
            ProgressBar(
                fraction,
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 14.px, bottomEnd = 14.px)),
                height = 4.px,
                track = PlasmaColors.white(0.2f),
                rounded = false,
            )
        }
    }
}

// ---- Toolbar -------------------------------------------------------------------------------------

@Composable
private fun Toolbar(
    today: LocalDate,
    day: LocalDate,
    groupName: String,
    onDay: (LocalDate) -> Unit,
    onDayPicked: () -> Unit,
    onGroup: () -> Unit,
    modifier: Modifier,
) {
    val selected = remember { FocusRequester() }
    Row(modifier.width(1728.px).height(52.px), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.focusRestoringGroup(selected),
            horizontalArrangement = Arrangement.spacedBy(4.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Yesterday (catch-up), Today, then the next five days.
            for (offset in -1L..5L) {
                val date = today.plusDays(offset)
                val isSelected = date == day
                val label = when (offset) {
                    -1L -> stringResource(R.string.yesterday)
                    0L -> stringResource(R.string.today)
                    else -> Formats.weekdayAndDay(date)
                }
                PlasmaSurface(
                    onClick = onDayPicked,
                    // Moving onto a day shows it, as Catch-up's days do; OK goes back to the grid.
                    onFocusChanged = { if (it && date != day) onDay(date) },
                    modifier = (if (isSelected) Modifier.focusRequester(selected) else Modifier).height(52.px),
                    style = FocusStyle.Pill,
                    shape = PlasmaShapes.Tab,
                    selected = isSelected,
                    contentAlignment = Alignment.Center,
                ) {
                    val focused = LocalFocused.current
                    val color = when {
                        focused -> PlasmaColors.OnFocus
                        isSelected -> PlasmaColors.Text
                        else -> PlasmaColors.Text2
                    }
                    Row(
                        Modifier.padding(horizontal = if (isSelected) 22.px else 20.px),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.px),
                    ) {
                        if (offset == -1L) PlasmaIcon(PlasmaIcons.CatchUp, 20.px, stroke = 2.2f, tint = color)
                        PlasmaText(label, plasmaStyle(23f, if (isSelected || focused) 600 else 500), color = color)
                    }
                }
            }
        }
        Box(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.px)) {
            ColorKeyHint(ColorKey.Yellow, stringResource(R.string.key_group))
            ChoiceButton(groupName, onClick = onGroup, textSizePx = 23f)
        }
    }
}

// ---- Grid ----------------------------------------------------------------------------------------

/** "Wed 21 Oct" over the channel column, then a label every half hour. */
@Composable
private fun TimeHeader(windowStart: Instant, zone: ZoneId, modifier: Modifier) {
    val start = windowStart.atZone(zone)
    Box(modifier.size(1728.px, 28.px)) {
        val style = plasmaStyle(20f, 500, tabular = true)
        PlasmaText(Formats.date(start), style, color = PlasmaColors.Text3)
        for (step in 0 until 4) {
            val time = start.plusMinutes(30L * step)
            Row(
                Modifier.offset(x = (CHANNEL_COLUMN_PX + step * WINDOW_PX / 4).px).height(28.px),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.px),
            ) {
                Box(Modifier.size(1.px, 18.px).background(PlasmaColors.white(0.22f)))
                PlasmaText(Formats.time(time), style, color = PlasmaColors.Text3)
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: GuideChannel,
    current: Boolean,
    windowStart: Instant,
    focusedProgramId: String?,
    now: Instant,
    zone: ZoneId,
) {
    val windowEnd = windowStart.plusMillis(GuideNavigator.WINDOW)
    val description = "${channel.number} ${channel.name}"
    Row(Modifier.height(ROW_PX.px).semantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.width(CHANNEL_COLUMN_PX.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.px),
        ) {
            PlasmaText(
                channel.number,
                plasmaStyle(21f, if (current) 600 else 500, tabular = true),
                Modifier.width(44.px),
                color = if (current) PlasmaColors.Text else PlasmaColors.Text3,
                align = TextAlign.End,
            )
            LogoImage(channel.logo, channel.name, LogoSize.Guide)
        }
        Box(Modifier.size(WINDOW_PX.px, ROW_PX.px)) {
            val visible = channel.programs.filter { it.end.isAfter(windowStart) && it.start.isBefore(windowEnd) }
            if (visible.isEmpty()) {
                EmptyCell()
            }
            for (program in visible) {
                val from = maxOf(program.start, windowStart)
                val to = minOf(program.end, windowEnd)
                val left = (from.toEpochMilli() - windowStart.toEpochMilli()) * PX_PER_MS
                val width = (to.toEpochMilli() - from.toEpochMilli()) * PX_PER_MS
                GuideCell(
                    program = program,
                    timing = when {
                        !program.end.isAfter(now) -> Timing.Past
                        program.start.isAfter(now) -> Timing.Future
                        else -> Timing.Now
                    },
                    focused = program.id == focusedProgramId,
                    archive = channel.catchUp,
                    startedEarlier = program.start.isBefore(windowStart),
                    zone = zone,
                    widthPx = width - 4f,
                    modifier = Modifier.offset(x = (left + 2f).px).size((width - 4f).px, ROW_PX.px),
                )
            }
        }
    }
}

private enum class Timing { Past, Now, Future }

@Composable
private fun BoxScope.EmptyCell() {
    Box(
        Modifier
            .offset(x = 2.px)
            .size((WINDOW_PX - 4f).px, ROW_PX.px)
            .background(PlasmaColors.GuideFuture, PlasmaShapes.Cell)
            .padding(horizontal = 16.px),
        contentAlignment = Alignment.CenterStart,
    ) {
        PlasmaText(stringResource(R.string.no_information), plasmaStyle(22f, 600), color = PlasmaColors.Text4)
    }
}

/**
 * A programme in the grid: past cells are darkest, the one on now lighter, future ones in between;
 * the focused cell is white. Title (22 px) and time (19 px) truncate; markers show catch-up (↺),
 * a programme that started before the window (◀), reminders, and recordings.
 */
@Composable
private fun GuideCell(
    program: GuideProgram,
    timing: Timing,
    focused: Boolean,
    archive: Boolean,
    startedEarlier: Boolean,
    zone: ZoneId,
    widthPx: Float,
    modifier: Modifier,
) {
    val background = when {
        focused -> PlasmaColors.Text
        timing == Timing.Past -> PlasmaColors.GuidePast
        timing == Timing.Now -> PlasmaColors.Surface3
        else -> PlasmaColors.GuideFuture
    }
    val titleColor = when {
        focused -> PlasmaColors.OnFocus
        timing == Timing.Past -> PlasmaColors.Text3
        timing == Timing.Now -> PlasmaColors.Text
        else -> PlasmaColors.TextSoft2
    }
    val timeColor = when {
        focused -> PlasmaColors.OnFocus3
        timing == Timing.Past -> PlasmaColors.Text4
        timing == Timing.Now -> PlasmaColors.Text2
        else -> PlasmaColors.Text3
    }
    val narrow = widthPx < 200f
    val start = program.start.atZone(zone)
    val time = if (narrow) Formats.time(start) else Formats.range(start, program.end.atZone(zone))
    Row(
        modifier
            .then(if (focused) Modifier.shadow(10.px, PlasmaShapes.Cell, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
            .background(background, PlasmaShapes.Cell)
            .padding(horizontal = if (focused) 18.px else if (narrow) 14.px else 16.px)
            .alpha(if (timing == Timing.Past && !archive && !focused) 0.6f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.px)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (narrow) 7.px else 8.px)) {
                val marker = if (focused) PlasmaColors.OnFocus3 else PlasmaColors.Text3
                when {
                    timing == Timing.Past && archive -> PlasmaIcon(PlasmaIcons.CatchUp, 17.px, stroke = 2.4f, tint = titleColor)
                    startedEarlier -> PlasmaIcon(PlasmaIcons.KeyLeft, 12.px, tint = marker, contentDescription = stringResource(R.string.started_earlier))
                }
                if (program.recordingScheduled) {
                    val description = stringResource(R.string.recording_scheduled)
                    Box(Modifier.size(9.px).background(PlasmaColors.Live, CircleShape).semantics { contentDescription = description })
                }
                PlasmaText(program.title, plasmaStyle(22f, 600), color = titleColor)
            }
            PlasmaText(time, plasmaStyle(19f, 500, tabular = true), color = timeColor)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px)) {
            if (focused && archive && timing != Timing.Future) {
                PlasmaIcon(
                    PlasmaIcons.History,
                    22.px,
                    stroke = 2f,
                    tint = PlasmaColors.OnFocus2,
                    contentDescription = stringResource(R.string.catch_up_available),
                )
            }
            if (program.reminder) {
                PlasmaIcon(
                    PlasmaIcons.Bell,
                    21.px,
                    stroke = 2f,
                    tint = if (focused) PlasmaColors.OnFocus2 else PlasmaColors.Text3,
                    contentDescription = stringResource(R.string.reminder_set),
                )
            }
            if (program.recording) {
                Box(Modifier.size(11.px).background(PlasmaColors.RecordingDot, CircleShape))
            }
        }
    }
}

/** The accent line at the current time, with a "20:41" pill in the header (board 08). */
@Composable
private fun NowLine(now: Instant, windowStart: Instant, zone: ZoneId, visibleDay: Boolean) {
    if (!visibleDay) return
    val offsetMs = now.toEpochMilli() - windowStart.toEpochMilli()
    if (offsetMs < 0 || offsetMs > GuideNavigator.WINDOW) return
    val x = 96 + CHANNEL_COLUMN_PX + offsetMs * PX_PER_MS
    val accent = LocalAccent.current
    Box(Modifier.offset(x = (x - 1f).px, y = 530.px).size(2.px, (GRID_BOTTOM_PX - 530).px).background(accent.copy(alpha = 0.9f)))
    Box(
        Modifier
            .offset(x = (x - 36f).px, y = 496.px)
            .size(72.px, 32.px)
            .background(accent, PlasmaShapes.Cell),
        contentAlignment = Alignment.Center,
    ) {
        PlasmaText(Formats.time(now.atZone(zone)), plasmaStyle(18f, 600, tabular = true), color = PlasmaColors.OnFocus)
    }
}
