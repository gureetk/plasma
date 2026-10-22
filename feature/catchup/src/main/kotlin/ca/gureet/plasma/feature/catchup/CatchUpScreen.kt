// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.catchup

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalAccent
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.component.ColorKey
import ca.gureet.plasma.core.design.component.ColorKeyHint
import ca.gureet.plasma.core.design.component.FooterKey
import ca.gureet.plasma.core.design.component.KeyFooter
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.component.LogoPlate
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.PlasmaColumn
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.component.SecondaryHeader
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.component.TabPill
import ca.gureet.plasma.core.design.component.rememberNow
import ca.gureet.plasma.core.design.focusRestoringGroup
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import java.time.LocalDate
import kotlinx.coroutines.delay
import ca.gureet.plasma.core.design.R as DesignR

/** How long focus rests on a channel before its programmes show (as Live TV's groups, 4.7). */
private const val SHOW_ON_REST_MS = 300L

/** Ten channel rows fit; the tenth is drawn at half strength while more follow (board 18). */
private const val CHANNELS_TOP_PX = 184
private const val CHANNELS_BOTTOM_PX = 922

/** Five programme rows fit above the footer; the focused one stays third while there's room to scroll. */
private const val PROGRAMS_TOP_PX = 306
private const val PROGRAMS_HEIGHT_PX = 650
private const val PROGRAM_PIVOT = 260f / PROGRAMS_HEIGHT_PX

/**
 * Catch-up (board 18, brief 4.18): channels with an archive on the left, then the selected channel's
 * days and programmes. Focus resting on a channel shows it, and moving along the days shows each
 * day. OK plays a programme from its start; CH ± changes the day; green opens the Guide, where any
 * channel's past programmes can be played.
 */
@Composable
fun CatchUpScreen(state: CatchUpUiState, actions: CatchUpActions, modifier: Modifier = Modifier) {
    val now by rememberNow()
    val today = now.toLocalDate()
    var channelId by rememberSaveable { mutableStateOf(state.selectedChannelId) }
    val channel = state.channels.firstOrNull { it.id == channelId } ?: state.channels.first()
    val days = channel.days(today)
    var pickedDay by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val day = pickedDay?.takeIf { it in days } ?: openingDay(channel, today)
    val programs = remember(channel.id, day, state, now) {
        programsOn(state.programs[channel.id].orEmpty(), day, now.zone, now.toInstant())
    }
    val opening = remember(programs) { openingProgram(programs, day, now) }

    val channelsFocus = remember { FocusRequester() }
    val selectedChannel = remember { FocusRequester() }
    val selectedTab = remember { FocusRequester() }
    val openingFocus = remember { FocusRequester() }
    val sheetReturn = remember { FocusRequester() }
    var programsHaveFocus by remember { mutableStateOf(false) }
    var tabsHaveFocus by remember { mutableStateOf(false) }
    var focusNewPrograms by remember { mutableStateOf(false) }
    var focusNewTab by remember { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<CatchUpProgram?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (opening != null) openingFocus.requestFocus() else selectedChannel.requestFocus()
    }

    fun changeDay(step: Int) {
        val next = days.getOrNull(days.indexOf(day) + step) ?: return
        focusNewPrograms = programsHaveFocus
        focusNewTab = tabsHaveFocus
        pickedDay = next
    }

    fun onKey(event: KeyEvent): Boolean {
        if (sheetOpen) return false
        val up = event.type == KeyEventType.KeyUp
        return when (event.key) {
            // CH+ moves to a newer day, CH− to an older one.
            Key.ChannelUp, Key.PageUp -> {
                if (up) changeDay(-1)
                true
            }
            Key.ChannelDown, Key.PageDown -> {
                if (up) changeDay(1)
                true
            }
            Key.ProgramGreen -> {
                if (up) actions.onGuide()
                true
            }
            else -> false
        }
    }

    Box(modifier.fillMaxSize().onPreviewKeyEvent(::onKey)) {
        SecondaryHeader(stringResource(DesignR.string.tab_home), stringResource(R.string.catch_up))

        PlasmaText(
            stringResource(R.string.channels_with_catch_up, Formats.count(state.channelCount)),
            plasmaStyle(21f, 600),
            Modifier.offset(108.px, 148.px),
            color = PlasmaColors.Text3,
        )
        ChannelList(
            channels = state.channels,
            selectedId = channel.id,
            today = today,
            onFocusChannel = { id ->
                channelId = id
                pickedDay = day
            },
            onOpenChannel = { id ->
                if (id == channel.id) {
                    openingFocus.requestFocus()
                } else {
                    channelId = id
                    pickedDay = day
                    focusNewPrograms = true
                }
            },
            selectedFocus = selectedChannel,
            modifier = Modifier
                .offset(96.px, CHANNELS_TOP_PX.px)
                .size(350.px, (CHANNELS_BOTTOM_PX - CHANNELS_TOP_PX).px)
                .focusRequester(channelsFocus),
        )

        ChannelHeader(channel, Modifier.offset(500.px, 148.px).width(1324.px))

        Row(
            Modifier
                .offset(500.px, 226.px)
                .onFocusChanged { tabsHaveFocus = it.hasFocus }
                .focusRestoringGroup(selectedTab),
            horizontalArrangement = Arrangement.spacedBy(6.px),
        ) {
            for (date in days) {
                val isSelected = date == day
                TabPill(
                    text = dayLabel(date, today),
                    selected = isSelected,
                    onClick = { if (opening != null) openingFocus.requestFocus() },
                    height = 52.px,
                    textSizePx = 23f,
                    horizontalPadding = 22.px,
                    shape = PlasmaShapes.Tab,
                    // Moving onto a day shows it, as the Guide's days do.
                    onFocusChanged = { if (it) pickedDay = date },
                    modifier = if (isSelected) Modifier.focusRequester(selectedTab) else Modifier,
                )
            }
        }
        LaunchedEffect(day) {
            if (focusNewTab) {
                focusNewTab = false
                withFrameNanos { }
                selectedTab.requestFocus()
            }
        }

        if (programs.isEmpty()) {
            PlasmaText(stringResource(R.string.empty_day), plasmaStyle(23f, 400), Modifier.offset(508.px, (PROGRAMS_TOP_PX + 20).px), color = PlasmaColors.Text2)
        }
        key(channel.id, day) {
            val openingIndex = programs.indexOf(opening).coerceAtLeast(0)
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = (openingIndex - 2).coerceAtLeast(0))
            LaunchedEffect(Unit) {
                if (focusNewPrograms) {
                    focusNewPrograms = false
                    withFrameNanos { }
                    if (opening != null) openingFocus.requestFocus()
                }
            }
            PlasmaColumn(
                modifier = Modifier
                    .offset(500.px, PROGRAMS_TOP_PX.px)
                    .size(1324.px, PROGRAMS_HEIGHT_PX.px)
                    .onFocusChanged { programsHaveFocus = it.hasFocus },
                state = listState,
                gap = 6.px,
                pivot = PROGRAM_PIVOT,
                focusFallback = openingFocus,
            ) {
                itemsIndexed(programs, key = { _, program -> program.id }) { _, program ->
                    ProgramRow(
                        program = program,
                        channel = channel,
                        onClick = { if (program.available) actions.onPlay(channel.id, program) },
                        onLongClick = {
                            sheetFor = program
                            sheetOpen = true
                        },
                        modifier = Modifier
                            .then(if (program == opening) Modifier.focusRequester(openingFocus) else Modifier)
                            .then(if (program == sheetFor) Modifier.focusRequester(sheetReturn) else Modifier),
                    )
                }
            }
        }

        KeyFooter(
            colorKeys = emptyList(),
            keys = listOf(
                FooterKey("OK", stringResource(R.string.key_watch_from_start)),
                FooterKey("CH ±", stringResource(R.string.key_change_day)),
                FooterKey(PlasmaIcons.Menu, stringResource(DesignR.string.key_options)),
            ),
        )

        SheetHost(visible = sheetOpen, returnFocus = sheetReturn) {
            val program = sheetFor ?: return@SheetHost
            OptionsSheet(
                title = program.title,
                subtitle = "${channel.name} · ${dayLabel(program.start.toLocalDate(), today)}, ${Formats.range(program.start, program.end)}",
                onDismiss = { sheetOpen = false },
                leading = { LogoPlate(channel.logo, channel.name, 108.px, 52.px, logoSize = LogoSize.Small, shape = PlasmaShapes.SmallPlate) },
                options = buildList {
                    if (program.available) {
                        add(SheetOption(stringResource(R.string.option_watch_from_start), { actions.onPlay(channel.id, program) }, icon = PlasmaIcons.StartOver))
                    }
                    add(SheetOption(stringResource(R.string.option_show_in_guide), { actions.onShowInGuide(channel.id, program.start) }, icon = PlasmaIcons.Guide))
                },
            )
        }
    }
}

/** "Today", "Yesterday", then "Mon 19". */
@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(DesignR.string.today)
    today.minusDays(1) -> stringResource(DesignR.string.yesterday)
    else -> Formats.weekdayAndDay(date)
}

/** "7 days" in the list, or "Local · 24 h" for local catch-up. */
@Composable
private fun depthText(channel: CatchUpChannel): String = when {
    channel.archiveDays != null -> pluralStringResource(R.plurals.archive_days, channel.archiveDays, channel.archiveDays)
    else -> stringResource(R.string.local_hours, channel.localHours ?: 0)
}

/** "Arena Sports 1 · 105 · 7-day archive", and the green key's hint on the right. */
@Composable
private fun ChannelHeader(channel: CatchUpChannel, modifier: Modifier) {
    Row(modifier.height(56.px), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.px)) {
            PlasmaText(channel.name, PlasmaType.PageTitle, Modifier.alignByBaseline())
            val depth = when {
                channel.archiveDays != null -> pluralStringResource(R.plurals.archive_days_header, channel.archiveDays, channel.archiveDays)
                else -> stringResource(R.string.local_hours_header, channel.localHours ?: 0)
            }
            PlasmaText("${channel.number} · $depth", plasmaStyle(22f, 500, tabular = true), Modifier.alignByBaseline(), color = PlasmaColors.Text3)
        }
        Spacer(Modifier.weight(1f))
        ColorKeyHint(ColorKey.Green, stringResource(R.string.any_channel_from_guide))
    }
}

/**
 * The channels with catch-up: 70 px rows with the logo on its plate, the name and how far back
 * the archive goes. The current one is marked; the row at a cut-off edge is drawn at half strength
 * to say there's more.
 */
@Composable
private fun ChannelList(
    channels: List<CatchUpChannel>,
    selectedId: String,
    today: LocalDate,
    onFocusChannel: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    selectedFocus: FocusRequester,
    modifier: Modifier,
) {
    val state = rememberLazyListState()
    val faded by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val whole = info.visibleItemsInfo.filter { it.offset >= -1 && it.offset + it.size <= info.viewportEndOffset + 1 }
            buildSet {
                whole.lastOrNull()?.let { if (it.index < info.totalItemsCount - 1) add(it.index) }
                whole.firstOrNull()?.let { if (it.index > 0) add(it.index) }
            }
        }
    }
    PlasmaColumn(
        modifier = modifier,
        state = state,
        gap = 4.px,
        pivot = null,
        context = 74.px,
        focusFallback = selectedFocus,
    ) {
        itemsIndexed(channels, key = { _, channel -> channel.id }) { i, channel ->
            val selected = channel.id == selectedId
            ChannelRow(
                channel = channel,
                selected = selected,
                faded = i in faded,
                depth = depthText(channel),
                onClick = { onOpenChannel(channel.id) },
                onRest = { onFocusChannel(channel.id) },
                modifier = if (selected) Modifier.focusRequester(selectedFocus) else Modifier,
            )
        }
    }
}

@Composable
private fun ChannelRow(
    channel: CatchUpChannel,
    selected: Boolean,
    faded: Boolean,
    depth: String,
    onClick: () -> Unit,
    onRest: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused && !selected) {
            delay(SHOW_ON_REST_MS)
            onRest()
        }
    }
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(70.px)
            .alpha(if (faded && !focused) 0.5f else 1f)
            .semantics { contentDescription = "${channel.name}, $depth" },
        style = FocusStyle.Row,
        shape = PlasmaShapes.Tab,
        selected = selected,
        onFocusChanged = { focused = it },
        contentAlignment = Alignment.CenterStart,
    ) {
        val isFocused = LocalFocused.current
        Row(
            Modifier.padding(start = 9.px, end = 14.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.px),
        ) {
            LogoPlate(channel.logo, channel.name, 108.px, 52.px, logoSize = LogoSize.Small, shape = PlasmaShapes.SmallPlate)
            Column(verticalArrangement = Arrangement.spacedBy(1.px)) {
                PlasmaText(
                    channel.name,
                    plasmaStyle(23f, if (selected || isFocused) 600 else 500),
                    color = when {
                        isFocused -> PlasmaColors.OnFocus
                        selected -> PlasmaColors.Text
                        else -> PlasmaColors.TextSoft
                    },
                )
                PlasmaText(depth, plasmaStyle(19f, 500), color = if (isFocused) PlasmaColors.OnFocus3 else PlasmaColors.Text3)
            }
        }
    }
}

/**
 * A 124 px programme row: its picture (or the channel's logo), the time, title and subtitle, and
 * the length on the right, with ↺ once it has focus. Programmes the archive can't play are dimmed
 * and say why.
 */
@Composable
private fun ProgramRow(
    program: CatchUpProgram,
    channel: CatchUpChannel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val range = Formats.range(program.start, program.end)
    val length = Formats.duration(program.duration)
    val description = if (program.available) {
        stringResource(R.string.described_program, program.title, range, length)
    } else {
        stringResource(R.string.described_unavailable, program.title, range)
    }
    PlasmaSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .height(124.px)
            .alpha(if (!program.available && !focused) 0.55f else 1f)
            .semantics { contentDescription = description },
        style = FocusStyle.Row,
        shape = PlasmaShapes.Row,
        onFocusChanged = { focused = it },
        contentAlignment = Alignment.CenterStart,
    ) {
        val isFocused = LocalFocused.current
        val secondary = if (isFocused) PlasmaColors.OnFocus2 else PlasmaColors.Text2
        Row(
            Modifier.padding(start = 8.px, end = 28.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.px),
        ) {
            ProgramThumbnail(program, channel)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.px)) {
                PlasmaText(range, plasmaStyle(20f, 500, tabular = true), color = if (isFocused) PlasmaColors.OnFocus3 else PlasmaColors.Text3)
                PlasmaText(program.title, plasmaStyle(26f, 600))
                val subtitle = if (program.available) program.subtitle else stringResource(R.string.unavailable_region)
                if (subtitle != null) PlasmaText(subtitle, plasmaStyle(21f, 500), color = secondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                if (!program.available) {
                    PlasmaIcon(PlasmaIcons.Lock, 20.px, tint = secondary, stroke = 2.2f)
                    PlasmaText(stringResource(R.string.unavailable), plasmaStyle(21f, 500), color = secondary)
                } else {
                    if (isFocused) PlasmaIcon(PlasmaIcons.CatchUp, 20.px, tint = secondary, stroke = 2.4f)
                    PlasmaText(length, plasmaStyle(21f, 500, tabular = true), color = secondary)
                }
            }
        }
    }
}

/** 192 × 108: the programme's picture with how much has been watched, or the logo on graphite. */
@Composable
private fun ProgramThumbnail(program: CatchUpProgram, channel: CatchUpChannel) {
    Box(Modifier.size(192.px, 108.px).clip(PlasmaShapes.Plate), contentAlignment = Alignment.Center) {
        val art = program.art
        if (art != null) {
            PlasmaImage(art, Modifier.fillMaxSize())
            program.progress?.let {
                ProgressBar(
                    it,
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                    height = 4.px,
                    color = LocalAccent.current,
                    track = PlasmaColors.white(0.25f),
                    rounded = false,
                )
            }
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PlasmaColors.Surface3, PlasmaColors.Plate))))
            LogoImage(channel.logo, channel.name, LogoSize.RowThumbnail, Modifier.alpha(0.9f))
        }
    }
}
