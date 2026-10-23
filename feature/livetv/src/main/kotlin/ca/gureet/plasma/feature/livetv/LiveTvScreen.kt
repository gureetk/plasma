// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.livetv

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.SelectKeys
import ca.gureet.plasma.core.design.component.ColorKey
import ca.gureet.plasma.core.design.component.CountedItem
import ca.gureet.plasma.core.design.component.CountedList
import ca.gureet.plasma.core.design.component.FooterKey
import ca.gureet.plasma.core.design.component.KeyFooter
import ca.gureet.plasma.core.design.component.LiveMark
import ca.gureet.plasma.core.design.component.LogoPlate
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.NumberEntry
import ca.gureet.plasma.core.design.component.NumberEntryBox
import ca.gureet.plasma.core.design.component.NumberEntryEffect
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.PlasmaColumn
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.component.TechBadge
import ca.gureet.plasma.core.design.component.TopBar
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.design.component.digit
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.player.VideoFrame
import ca.gureet.plasma.player.VideoSlot
import kotlinx.coroutines.delay

// ---- State ---------------------------------------------------------------------------------------

data class LiveGroup(
    val id: String,
    val name: String,
    val count: Int,
    /** All Channels, Favorites and Recently Watched, above the divider. */
    val smart: Boolean,
    /** The group numbers its channels 1, 2, 3… (Favorites by default, 5.2). */
    val countsFromOne: Boolean = false,
)

data class LiveChannel(
    val id: String,
    /** As this group numbers it: Favorites count from 1, other groups keep full-list numbers (5.2). */
    val number: String,
    val name: String,
    val logo: String?,
    val frame: VideoFrame,
    val nowTitle: String?,
    /** "19:45–22:00" */
    val nowRange: String?,
    val progress: Float?,
    val catchUp: Boolean,
    val favorite: Boolean,
    val uhd: Boolean,
    val locked: Boolean,
)

data class UpcomingProgram(
    val id: String,
    val time: String,
    val title: String,
    val reminder: Boolean = false,
    val recordingScheduled: Boolean = false,
)

data class ChannelPreview(
    val title: String,
    val range: String,
    /** "1 h 19 min left" */
    val left: String,
    val progress: Float,
    val description: String?,
    val upcoming: List<UpcomingProgram>,
)

data class LiveTvUiState(
    val profileName: String,
    val profileInitial: String,
    val groups: List<LiveGroup>,
    val channels: Map<String, List<LiveChannel>>,
    val previews: Map<String, ChannelPreview>,
    val selectedGroupId: String,
    /** The channel playing now, focused when Live TV opens. */
    val currentChannelId: String,
)

enum class SortOrder { Number, Name, Recent }

/** What Live TV asks the app to do. */
class LiveTvActions(
    val onTab: (TopTab) -> Unit = {},
    val onSettings: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val onWatch: (channelId: String) -> Unit = {},
    val onCatchUp: (channelId: String) -> Unit = {},
    val onMultiview: () -> Unit = {},
)

// ---- Screen --------------------------------------------------------------------------------------

/** Where the list's rows start: below the "Sports · 186 channels" header (board 07). */
private const val LIST_TOP_PX = 208

/** Where the lists end: above the footer's key hints. */
private const val LIST_BOTTOM_PX = 976

/** The full list, whose numbers a typed number falls back to (5.2). */
private const val ALL_CHANNELS = "all"

/** A sheet over Live TV. */
private sealed interface LiveSheet {
    /** Menu or long-press OK on a channel: its options. */
    data class Channel(val id: String) : LiveSheet

    /** OK on a program coming up: Remind Me and Record. */
    data class Upcoming(val channelId: String, val programId: String) : LiveSheet

    /** A typed number belongs to a hidden channel (5.2). */
    data class Hidden(val id: String) : LiveSheet
}

/** Reminders and recordings set from Live TV, until they're kept for real (M2 and M6). */
private data class Marks(val reminder: Boolean, val recording: Boolean)

/** The channel a typed number tunes, and whether the number is the list's own rather than the full list's. */
private data class TypedChannel(val channel: LiveChannel, val listNumber: Boolean)

/**
 * Live TV (board 07, brief 4.7): groups on the left, the group's channels with what's on, and a
 * live preview of the focused channel with its next programs. The preview follows focus after
 * a 600 ms dwell, using the one video layer.
 *
 * Menu or long-press OK on a channel opens its options; OK on a program coming up opens Remind
 * Me and Record. The number keys type a channel number (4.11): in a list that counts from 1, the
 * list's own numbers come first (5.2).
 */
@Composable
fun LiveTvScreen(
    state: LiveTvUiState,
    actions: LiveTvActions,
    modifier: Modifier = Modifier,
    previewDwellMs: Long = PlasmaMotion.PREVIEW_DWELL_MS,
) {
    var groupId by remember { mutableStateOf(state.selectedGroupId) }
    var focusedId by remember { mutableStateOf(state.currentChannelId) }
    var previewId by remember { mutableStateOf(state.currentChannelId) }
    var sort by remember { mutableStateOf(SortOrder.Number) }
    var favorites by remember { mutableStateOf(state.channels.values.flatten().filter { it.favorite }.map { it.id }.toSet()) }
    var locked by remember { mutableStateOf(state.channels.values.flatten().filter { it.locked }.map { it.id }.toSet()) }
    var hidden by remember { mutableStateOf(emptySet<String>()) }
    var marks by remember { mutableStateOf(emptyMap<String, Marks>()) }
    var sheet by remember { mutableStateOf<LiveSheet?>(null) }
    // The program the last Remind / Record sheet was for, which gets focus back when it closes.
    var upcomingFor by remember { mutableStateOf<String?>(null) }
    val entry = remember { NumberEntry() }

    val group = state.groups.firstOrNull { it.id == groupId } ?: state.groups.first()
    val allChannels = state.channels[ALL_CHANNELS] ?: state.channels.values.flatten().distinctBy { it.id }
    val channels = remember(groupId, sort, favorites, locked, hidden, state) {
        val list = state.channels[group.id].orEmpty()
            .filter { it.id !in hidden }
            .map { it.copy(favorite = it.id in favorites, locked = it.id in locked) }
        when (sort) {
            SortOrder.Number -> list
            SortOrder.Name -> list.sortedBy { it.name }
            SortOrder.Recent -> {
                val recent = state.channels["recent"].orEmpty().map { it.id }
                list.sortedBy { recent.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            }
        }
    }

    val listFocus = remember { FocusRequester() }
    val currentRow = remember { FocusRequester() }
    val groupsFocus = remember { FocusRequester() }
    val upcomingFirst = remember { FocusRequester() }
    val focusedRow = remember { FocusRequester() }
    val upcomingReturn = remember { FocusRequester() }
    val currentIndex = channels.indexOfFirst { it.id == state.currentChannelId }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (currentIndex >= 0) currentRow.requestFocus() else listFocus.requestFocus()
    }
    // The preview tunes to the focused channel once focus has rested on it (4.7).
    LaunchedEffect(focusedId) {
        if (focusedId != previewId) {
            delay(previewDwellMs)
            previewId = focusedId
        }
    }
    // A typed number tunes 1.5 s after the last digit, or on OK (4.11). A hidden channel asks first.
    val find = { number: String -> findChannel(number, group, state) }
    val tune = { typed: TypedChannel ->
        if (typed.channel.id in hidden) sheet = LiveSheet.Hidden(typed.channel.id) else actions.onWatch(typed.channel.id)
    }
    NumberEntryEffect(entry, find, tune)

    val focused = channels.firstOrNull { it.id == focusedId }
    val hasUpcoming = state.previews[previewId]?.upcoming.orEmpty().isNotEmpty()
    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (sheet != null) return@onPreviewKeyEvent false
                val up = event.type == KeyEventType.KeyUp
                val digit = event.key.digit()
                when {
                    digit != null -> {
                        if (!up) entry.type(digit)
                        true
                    }
                    // While a number is being typed, OK tunes it now and Back drops it.
                    entry.isActive && event.key in SelectKeys -> {
                        if (up) entry.finish(find, tune)
                        true
                    }
                    entry.isActive && (event.key == Key.Back || event.key == Key.Escape) -> {
                        if (up) entry.clear()
                        true
                    }
                    !up -> false
                    event.key == Key.ProgramRed -> {
                        focused?.let { ch -> favorites = favorites.toggle(ch.id) }
                        true
                    }
                    event.key == Key.ProgramGreen -> {
                        focused?.let { actions.onCatchUp(it.id) }
                        true
                    }
                    event.key == Key.ProgramYellow -> {
                        sort = SortOrder.entries[(sort.ordinal + 1) % SortOrder.entries.size]
                        true
                    }
                    event.key == Key.ProgramBlue -> {
                        actions.onMultiview()
                        true
                    }
                    else -> false
                }
            },
    ) {
        TopBar(
            current = TopTab.LiveTv,
            profileInitial = state.profileInitial,
            profileName = state.profileName,
            onTab = actions.onTab,
            onSettings = actions.onSettings,
            onProfile = actions.onProfile,
        )

        CountedList(
            items = remember(state.groups) { state.groups.map { CountedItem(it.id, it.name, it.count, it.smart) } },
            selectedId = group.id,
            onFocusItem = { groupId = it },
            onOpenItem = {
                groupId = it
                listFocus.requestFocus()
            },
            modifier = Modifier
                .offset(96.px, 148.px)
                .size(300.px, (LIST_BOTTOM_PX - 148).px)
                .focusRequester(groupsFocus),
        )

        // Channel list (x 436)
        Column(Modifier.offset(436.px, 148.px).width(720.px)) {
            Row(Modifier.height(46.px), horizontalArrangement = Arrangement.spacedBy(14.px)) {
                PlasmaText(group.name, plasmaStyle(32f, 600, trackingPx = -0.4f), Modifier.alignBy(FirstBaseline))
                PlasmaText(
                    pluralStringResource(R.plurals.channel_count, group.count, Formats.count(group.count)),
                    plasmaStyle(22f, 500),
                    Modifier.alignBy(FirstBaseline),
                    color = PlasmaColors.Text3,
                )
            }
        }
        if (channels.isEmpty()) {
            PlasmaText(
                stringResource(R.string.empty_group),
                plasmaStyle(23f, 400),
                Modifier.offset(448.px, LIST_TOP_PX.px),
                color = PlasmaColors.Text2,
            )
        }
        PlasmaColumn(
            state = listState,
            gap = 6.px,
            modifier = Modifier
                .offset(436.px, LIST_TOP_PX.px)
                .size(720.px, (992 - LIST_TOP_PX).px)
                .focusRequester(listFocus),
        ) {
            itemsIndexed(channels, key = { _, ch -> ch.id }) { _, channel ->
                ChannelRow(
                    channel = channel,
                    onClick = { actions.onWatch(channel.id) },
                    onLongClick = { sheet = LiveSheet.Channel(channel.id) },
                    onFocused = { focusedId = channel.id },
                    modifier = Modifier
                        .then(if (channel.id == state.currentChannelId) Modifier.focusRequester(currentRow) else Modifier)
                        .then(if (channel.id == focusedId) Modifier.focusRequester(focusedRow) else Modifier)
                        // Right goes to what's coming up, however far below the row it is.
                        .focusProperties { right = if (hasUpcoming) upcomingFirst else FocusRequester.Cancel },
                )
            }
        }
        // The list fades out at the bottom to show it scrolls.
        Box(
            Modifier
                .offset(436.px, 872.px)
                .size(720.px, 120.px)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.85f to PlasmaColors.Bg)),
        )

        val previewChannel = allChannels.firstOrNull { it.id == previewId }
        Preview(
            channel = previewChannel,
            preview = state.previews[previewId]?.withMarks(marks),
            onUpcoming = { programId ->
                upcomingFor = programId
                sheet = LiveSheet.Upcoming(previewId, programId)
            },
            upcomingFirst = upcomingFirst,
            upcomingReturn = upcomingReturn,
            returnTo = upcomingFor,
            modifier = Modifier.offset(1196.px, 148.px).width(628.px),
        )

        KeyFooter(
            colorKeys = listOf(
                ColorKey.Red to stringResource(R.string.key_favorite),
                ColorKey.Green to stringResource(R.string.key_catch_up),
                ColorKey.Yellow to stringResource(R.string.key_sort),
                ColorKey.Blue to stringResource(R.string.key_multiview),
            ),
            keys = listOf(
                FooterKey("OK", stringResource(R.string.key_watch)),
                FooterKey(PlasmaIcons.KeyLeft, stringResource(R.string.key_groups), iconSize = 13.px),
                FooterKey(PlasmaIcons.Menu, stringResource(R.string.key_options)),
                FooterKey("0–9", stringResource(R.string.key_go_to_channel)),
            ),
        )

        if (entry.isActive) {
            val typed = find(entry.digits)
            val match = when {
                typed == null -> null
                typed.listNumber -> stringResource(R.string.list_number, group.name, typed.channel.number, typed.channel.name)
                else -> typed.channel.name
            }
            NumberEntryBox(entry, match, Modifier.offset(96.px, 56.px))
        }

        val sheetChannel = (sheet as? LiveSheet.Channel)?.let { s -> channels.firstOrNull { it.id == s.id } }
        // Hiding the last channel in the list leaves the groups to take focus.
        SheetHost(visible = sheetChannel != null, returnFocus = if (channels.isEmpty()) groupsFocus else focusedRow) {
            if (sheetChannel != null) {
                ChannelOptions(
                    channel = sheetChannel,
                    inFavorites = group.countsFromOne,
                    onDismiss = { sheet = null },
                    onFavorite = { favorites = favorites.toggle(sheetChannel.id) },
                    onLock = { locked = locked.toggle(sheetChannel.id) },
                    onHide = {
                        // Focus moves on to the next channel, or the one before at the end of the list.
                        val i = channels.indexOf(sheetChannel)
                        channels.getOrNull(i + 1)?.let { focusedId = it.id } ?: channels.getOrNull(i - 1)?.let { focusedId = it.id }
                        hidden = hidden + sheetChannel.id
                    },
                )
            }
        }

        val upcomingSheet = sheet as? LiveSheet.Upcoming
        val upcoming = upcomingSheet?.let { s -> state.previews[s.channelId]?.withMarks(marks)?.upcoming?.firstOrNull { it.id == s.programId } }
        SheetHost(visible = upcoming != null, returnFocus = upcomingReturn) {
            if (upcoming != null) {
                UpcomingOptions(
                    program = upcoming,
                    channelName = allChannels.firstOrNull { it.id == upcomingSheet?.channelId }?.name.orEmpty(),
                    onDismiss = { sheet = null },
                    onChange = { reminder, recording -> marks = marks + (upcoming.id to Marks(reminder, recording)) },
                )
            }
        }

        val hiddenChannel = (sheet as? LiveSheet.Hidden)?.let { s -> allChannels.firstOrNull { it.id == s.id } }
        SheetHost(visible = hiddenChannel != null, returnFocus = if (channels.isEmpty()) groupsFocus else focusedRow) {
            if (hiddenChannel != null) {
                HiddenChannel(
                    channel = hiddenChannel,
                    onDismiss = { sheet = null },
                    onUnhide = {
                        hidden = hidden - hiddenChannel.id
                        actions.onWatch(hiddenChannel.id)
                    },
                )
            }
        }
    }
}

private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

private fun ChannelPreview.withMarks(marks: Map<String, Marks>) = copy(
    upcoming = upcoming.map { program ->
        marks[program.id]?.let { program.copy(reminder = it.reminder, recordingScheduled = it.recording) } ?: program
    },
)

/**
 * The channel a typed number means (5.2, Current list first): in a list that counts from 1, its
 * own number; otherwise, or when it has no such number, the full list's. Hidden channels count,
 * so typing one's number can offer to unhide it.
 */
private fun findChannel(number: String, group: LiveGroup, state: LiveTvUiState): TypedChannel? =
    state.channels[group.id].orEmpty().takeIf { group.countsFromOne }?.firstOrNull { it.number == number }
        ?.let { TypedChannel(it, listNumber = true) }
        ?: state.channels[ALL_CHANNELS].orEmpty().firstOrNull { it.number == number }?.let { TypedChannel(it, listNumber = false) }

/**
 * Menu or long-press OK on a channel (brief 4.7 and 4.35). Favorites, Lock and Hide work on the
 * sample; Move, Switch Source, Change Number, Volume Level, local catch-up and Channel Info come
 * with the features behind them.
 */
@Composable
private fun ChannelOptions(
    channel: LiveChannel,
    inFavorites: Boolean,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onLock: () -> Unit,
    onHide: () -> Unit,
) {
    val later = {}
    OptionsSheet(
        title = channel.name,
        subtitle = listOfNotNull(channel.number, channel.nowTitle).joinToString(" · "),
        onDismiss = onDismiss,
        leading = { LogoPlate(channel.logo, channel.name, 150.px, 66.px, logoSize = LogoSize.Plate) },
        options = listOfNotNull(
            SheetOption(stringResource(if (channel.favorite) R.string.remove_favorite else R.string.add_favorite), onFavorite, icon = PlasmaIcons.Star),
            if (inFavorites) SheetOption(stringResource(R.string.move), later, icon = PlasmaIcons.Move) else null,
            SheetOption(stringResource(R.string.hide_channel), onHide, icon = PlasmaIcons.Hide),
            SheetOption(stringResource(if (channel.locked) R.string.unlock_channel else R.string.lock_channel), onLock, icon = PlasmaIcons.Lock),
            SheetOption.Divider,
            SheetOption(stringResource(R.string.switch_source), later, icon = PlasmaIcons.SwitchSource),
            SheetOption(stringResource(R.string.change_number), later, icon = PlasmaIcons.Hash),
            SheetOption(stringResource(R.string.volume_level), later, icon = PlasmaIcons.Volume),
            if (!channel.catchUp) SheetOption(stringResource(R.string.add_local_catch_up), later, icon = PlasmaIcons.CatchUp) else null,
            SheetOption(stringResource(R.string.channel_info), later, icon = PlasmaIcons.Info),
        ),
    )
}

/** OK on a program coming up: Remind Me and Record, or their undoing (brief 4.7). */
@Composable
private fun UpcomingOptions(
    program: UpcomingProgram,
    channelName: String,
    onDismiss: () -> Unit,
    onChange: (reminder: Boolean, recording: Boolean) -> Unit,
) {
    OptionsSheet(
        title = program.title,
        subtitle = listOf(program.time, channelName).joinToString(" · "),
        onDismiss = onDismiss,
        options = listOf(
            SheetOption(
                stringResource(if (program.reminder) R.string.cancel_reminder else R.string.remind_me),
                onClick = { onChange(!program.reminder, program.recordingScheduled) },
                icon = PlasmaIcons.Bell,
            ),
            SheetOption(
                stringResource(if (program.recordingScheduled) R.string.cancel_recording else R.string.record),
                onClick = { onChange(program.reminder, !program.recordingScheduled) },
                icon = PlasmaIcons.Record,
            ),
        ),
    )
}

/** A typed number belongs to a hidden channel: "Channel 108 is hidden", with Unhide (5.2). */
@Composable
private fun HiddenChannel(channel: LiveChannel, onDismiss: () -> Unit, onUnhide: () -> Unit) {
    OptionsSheet(
        title = stringResource(R.string.channel_hidden, channel.number),
        subtitle = channel.name,
        onDismiss = onDismiss,
        leading = { LogoPlate(channel.logo, channel.name, 150.px, 66.px, logoSize = LogoSize.Plate) },
        options = listOf(SheetOption(stringResource(R.string.unhide), onUnhide, icon = PlasmaIcons.Show)),
    )
}

// ---- Channel rows --------------------------------------------------------------------------------

/**
 * A channel in the list: number, logo plate, what's on with its time and progress, then markers
 * (★ favourite, ↺ catch-up, 4K). Rows are 90 px; the focused row grows to 96 px and turns white.
 * A locked channel is dimmed, with a lock after its title.
 */
@Composable
private fun ChannelRow(
    channel: LiveChannel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val height by animateDpAsState(if (focused) 96.px else 90.px, PlasmaMotion.focus(), label = "rowHeight")
    val margin by animateDpAsState(if (focused) 4.px else 0.dp, PlasmaMotion.focus(), label = "rowMargin")
    val description = listOfNotNull(channel.number, channel.name, channel.nowTitle).joinToString(", ")
    PlasmaSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .padding(vertical = margin)
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
        style = FocusStyle.Row,
        focusedScale = 1.03f,
        shape = PlasmaShapes.Row,
        onFocusChanged = {
            focused = it
            if (it) onFocused()
        },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .alpha(if (channel.locked && !focused) 0.45f else 1f)
                .padding(start = 12.px, end = 20.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.px),
        ) {
            PlasmaText(
                channel.number,
                plasmaStyle(23f, if (focused) 600 else 500, tabular = true),
                Modifier.width(52.px),
                color = if (focused) PlasmaColors.OnFocus2 else PlasmaColors.Text3,
                align = TextAlign.End,
            )
            LogoPlate(channel.logo, channel.name, 150.px, 66.px, logoSize = LogoSize.Plate)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.px)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                    PlasmaText(
                        channel.nowTitle ?: channel.name,
                        plasmaStyle(25f, 600),
                        Modifier.weight(1f, fill = false),
                        color = if (focused) PlasmaColors.OnFocus else PlasmaColors.Text,
                    )
                    if (channel.locked) {
                        PlasmaIcon(
                            PlasmaIcons.Lock,
                            20.px,
                            tint = if (focused) PlasmaColors.OnFocus2 else PlasmaColors.Text2,
                            contentDescription = stringResource(R.string.locked),
                        )
                    }
                }
                if (channel.nowRange != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px)) {
                        PlasmaText(
                            channel.nowRange,
                            plasmaStyle(21f, 500, tabular = true),
                            color = if (focused) PlasmaColors.OnFocus3 else PlasmaColors.Text3,
                        )
                        if (channel.progress != null) {
                            ProgressBar(
                                channel.progress,
                                Modifier.width(110.px),
                                height = 3.px,
                                color = if (focused) PlasmaColors.OnFocus else PlasmaColors.Text3,
                                track = if (focused) PlasmaColors.OnFocus.copy(alpha = 0.14f) else PlasmaColors.white(0.14f),
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.px)) {
                if (channel.favorite) {
                    PlasmaIcon(
                        PlasmaIcons.Star,
                        22.px,
                        tint = if (focused) PlasmaColors.FavoriteOnFocus else PlasmaColors.Favorite,
                        contentDescription = stringResource(R.string.favorite),
                    )
                }
                // A 4K badge takes the place of the catch-up mark, so rows stay uncluttered (board 07, 107).
                if (channel.uhd) {
                    TechBadge(
                        "4K",
                        borderColor = if (focused) PlasmaColors.OnFocus3 else PlasmaColors.Text4,
                        color = if (focused) PlasmaColors.OnFocus2 else PlasmaColors.Text2,
                    )
                } else if (channel.catchUp) {
                    PlasmaIcon(
                        PlasmaIcons.History,
                        24.px,
                        stroke = 2f,
                        tint = if (focused) PlasmaColors.OnFocus2 else PlasmaColors.Text3,
                        contentDescription = stringResource(R.string.catch_up_available),
                    )
                }
            }
        }
    }
}

// ---- Preview -------------------------------------------------------------------------------------

@Composable
private fun Preview(
    channel: LiveChannel?,
    preview: ChannelPreview?,
    onUpcoming: (String) -> Unit,
    upcomingFirst: FocusRequester,
    upcomingReturn: FocusRequester,
    returnTo: String?,
    modifier: Modifier,
) {
    Column(modifier) {
        VideoSlot(
            channel?.frame,
            Modifier.size(628.px, 353.px),
            cornerRadius = 14.px,
            background = PlasmaColors.VideoWell,
        )
        if (preview == null) return@Column
        Row(
            Modifier.padding(top = 26.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.px),
        ) {
            LiveMark(
                stringResource(R.string.now),
                style = plasmaStyle(19f, 600, trackingPx = 1.2f),
                dot = 9.px,
                gap = 8.px,
            )
            val meta = plasmaStyle(22f, 500, tabular = true)
            PlasmaText(preview.range, meta, color = PlasmaColors.Text2)
            PlasmaText("·", meta, color = PlasmaColors.Text4)
            PlasmaText(preview.left, meta, color = PlasmaColors.Text2)
        }
        PlasmaText(
            preview.title,
            plasmaStyle(36f, 600, trackingPx = -0.6f, lineHeight = 1.15f),
            Modifier.padding(top = 10.px),
            maxLines = 2,
        )
        ProgressBar(preview.progress, Modifier.padding(top = 16.px).fillMaxWidth(), height = 4.px, track = PlasmaColors.white(0.14f))
        if (preview.description != null) {
            PlasmaText(
                preview.description,
                plasmaStyle(23f, 400, lineHeight = 1.5f),
                Modifier.padding(top = 18.px),
                color = PlasmaColors.Text2,
                maxLines = 3,
            )
        }
        if (preview.upcoming.isNotEmpty()) {
            Column(
                Modifier
                    .padding(top = 22.px)
                    .fillMaxWidth()
                    .drawBehind { drawLine(PlasmaColors.Divider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.px.toPx()) }
                    .padding(top = 12.px),
                verticalArrangement = Arrangement.spacedBy(-2.px),
            ) {
                preview.upcoming.forEachIndexed { i, program ->
                    UpcomingRow(
                        program,
                        onClick = { onUpcoming(program.id) },
                        modifier = Modifier
                            .then(if (i == 0) Modifier.focusRequester(upcomingFirst) else Modifier)
                            .then(if (program.id == returnTo) Modifier.focusRequester(upcomingReturn) else Modifier),
                    )
                }
            }
        }
    }
}

/** A program coming up: OK opens Remind / Record (4.7). Focus draws a white pill around it. */
@Composable
private fun UpcomingRow(program: UpcomingProgram, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PlasmaSurface(
        onClick = onClick,
        // Wider than the column by the pill's padding on each side, so the text lines up with the column.
        modifier = modifier.requiredWidth((628 + 28).px),
        style = FocusStyle.Row,
        shape = PlasmaShapes.Button,
    ) {
        val focused = LocalFocused.current
        Row(
            Modifier.padding(horizontal = 14.px, vertical = 8.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.px),
        ) {
            val style = plasmaStyle(23f, 500, tabular = true)
            PlasmaText(program.time, style, Modifier.width(66.px), color = if (focused) PlasmaColors.OnFocus3 else PlasmaColors.Text3)
            PlasmaText(program.title, style, Modifier.weight(1f), color = if (focused) PlasmaColors.OnFocus else PlasmaColors.TextSoft2)
            when {
                program.reminder -> PlasmaIcon(
                    PlasmaIcons.Bell,
                    22.px,
                    stroke = 2f,
                    tint = if (focused) PlasmaColors.OnFocus2 else PlasmaColors.Text3,
                    contentDescription = stringResource(R.string.reminder_set),
                )
                program.recordingScheduled -> {
                    val description = stringResource(R.string.recording_scheduled)
                    Box(Modifier.size(11.px).background(PlasmaColors.Live, CircleShape).semantics { contentDescription = description })
                }
                else -> Spacer(Modifier.size(22.px))
            }
        }
    }
}
