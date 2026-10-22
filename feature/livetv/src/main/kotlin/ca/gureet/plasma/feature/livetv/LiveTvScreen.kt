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
import ca.gureet.plasma.core.design.component.ColorKey
import ca.gureet.plasma.core.design.component.CountedItem
import ca.gureet.plasma.core.design.component.CountedList
import ca.gureet.plasma.core.design.component.FooterKey
import ca.gureet.plasma.core.design.component.KeyFooter
import ca.gureet.plasma.core.design.component.LiveMark
import ca.gureet.plasma.core.design.component.LogoPlate
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.PlasmaColumn
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.component.TechBadge
import ca.gureet.plasma.core.design.component.TopBar
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.design.focusRestoringGroup
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
    val onChannelOptions: (channelId: String) -> Unit = {},
    val onUpcoming: (channelId: String, programId: String) -> Unit = { _, _ -> },
    val onCatchUp: (channelId: String) -> Unit = {},
    val onMultiview: () -> Unit = {},
)

// ---- Screen --------------------------------------------------------------------------------------

/** Where the list's rows start: below the "Sports · 186 channels" header (board 07). */
private const val LIST_TOP_PX = 208

/** Where the lists end: above the footer's key hints. */
private const val LIST_BOTTOM_PX = 976

/**
 * Live TV (board 07, brief 4.7): groups on the left, the group's channels with what's on, and a
 * live preview of the focused channel with its next programmes. The preview follows focus after
 * a 600 ms dwell, using the one video layer.
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

    val group = state.groups.firstOrNull { it.id == groupId } ?: state.groups.first()
    val channels = remember(groupId, sort, favorites, state) {
        val list = state.channels[group.id].orEmpty().map { it.copy(favorite = it.id in favorites) }
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
    val upcomingFocus = remember { FocusRequester() }
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

    val focused = channels.firstOrNull { it.id == focusedId }
    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.ProgramRed -> {
                        focused?.let { ch -> favorites = if (ch.id in favorites) favorites - ch.id else favorites + ch.id }
                        true
                    }
                    Key.ProgramGreen -> { focused?.let { actions.onCatchUp(it.id) }; true }
                    Key.ProgramYellow -> { sort = SortOrder.entries[(sort.ordinal + 1) % SortOrder.entries.size]; true }
                    Key.ProgramBlue -> { actions.onMultiview(); true }
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
                .focusRequester(groupsFocus)
                .focusProperties { right = listFocus },
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
                .focusRequester(listFocus)
                .focusProperties {
                    left = groupsFocus
                    right = upcomingFocus
                },
        ) {
            itemsIndexed(channels, key = { _, ch -> ch.id }) { _, channel ->
                ChannelRow(
                    channel = channel,
                    onClick = { actions.onWatch(channel.id) },
                    onLongClick = { actions.onChannelOptions(channel.id) },
                    onFocused = { focusedId = channel.id },
                    modifier = if (channel.id == state.currentChannelId) Modifier.focusRequester(currentRow) else Modifier,
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

        val preview = state.previews[previewId]
        val previewChannel = state.channels.values.flatten().firstOrNull { it.id == previewId }
        Preview(
            channel = previewChannel,
            preview = preview,
            onUpcoming = { programId -> actions.onUpcoming(previewId, programId) },
            upcomingFocus = upcomingFocus,
            back = listFocus,
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
    }
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
    upcomingFocus: FocusRequester,
    back: FocusRequester,
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
                    .padding(top = 12.px)
                    .focusRequester(upcomingFocus)
                    .focusRestoringGroup()
                    .focusProperties { left = back },
                verticalArrangement = Arrangement.spacedBy(-2.px),
            ) {
                for (program in preview.upcoming) UpcomingRow(program, onClick = { onUpcoming(program.id) })
            }
        }
    }
}

/** A programme coming up: OK opens Remind / Record (4.7). Focus draws a white pill around it. */
@Composable
private fun UpcomingRow(program: UpcomingProgram, onClick: () -> Unit) {
    PlasmaSurface(
        onClick = onClick,
        // Wider than the column by the pill's padding on each side, so the text lines up with the column.
        modifier = Modifier.requiredWidth((628 + 28).px),
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
