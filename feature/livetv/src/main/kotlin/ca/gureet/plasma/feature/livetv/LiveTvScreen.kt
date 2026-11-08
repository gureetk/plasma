// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.livetv

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.LocalHeldKeys
import ca.gureet.plasma.core.design.LocalReduceMotion
import ca.gureet.plasma.core.design.LocalShowChannelNumbers
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.SelectKeys
import ca.gureet.plasma.core.design.itemMotion
import ca.gureet.plasma.core.design.rememberStepBack
import ca.gureet.plasma.core.design.rememberSwapFade
import ca.gureet.plasma.core.design.stepBack
import ca.gureet.plasma.core.design.swapFade
import ca.gureet.plasma.core.design.component.ColorKey
import ca.gureet.plasma.core.design.component.CountedItem
import ca.gureet.plasma.core.design.component.CountedList
import ca.gureet.plasma.core.design.component.FooterKey
import ca.gureet.plasma.core.design.component.KeyFooter
import ca.gureet.plasma.core.design.component.LevelBar
import ca.gureet.plasma.core.design.component.LiveMark
import ca.gureet.plasma.core.design.component.LogoPlate
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.MoveArrow
import ca.gureet.plasma.core.design.component.NumberChooser
import ca.gureet.plasma.core.design.component.NumberEntry
import ca.gureet.plasma.core.design.component.NumberEntryOverlay
import ca.gureet.plasma.core.design.component.NumberEntryEffect
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.PlasmaColumn
import ca.gureet.plasma.core.design.component.jumpToPivot
import ca.gureet.plasma.core.design.component.ProgramSheet
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.component.SourceChoice
import ca.gureet.plasma.core.design.component.SwitchSourceSheet
import ca.gureet.plasma.core.design.component.TechBadge
import ca.gureet.plasma.core.design.component.TopBar
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.design.component.carry
import ca.gureet.plasma.core.design.component.digit
import ca.gureet.plasma.core.design.component.keepInView
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.design.textGrowth
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.core.model.LiveDetails
import ca.gureet.plasma.core.model.LiveListShows
import ca.gureet.plasma.player.VideoFrame
import ca.gureet.plasma.player.VideoSlot
import ca.gureet.plasma.player.VolumeLevels
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ca.gureet.plasma.core.design.R as DesignR

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
    /** Locked through its group (5.12), which only Settings › Parental Controls unlocks. */
    val lockedWithGroup: Boolean = false,
    /** Its number in the full list, which Change Number changes (5.2); [number] is the list's own. */
    val fullNumber: Int? = number.toIntOrNull(),
    /** Where it plays from, in play order: one source, or a merged channel's (5.1). */
    val sources: List<LiveSource> = emptyList(),
    // For Channel Info.
    val groupName: String? = null,
    /** The provider's guide id (`tvg-id`). */
    val guideId: String? = null,
    val catchUpDays: Int = 0,
    /** Chosen for local catch-up: recorded all the time, as it has no archive of its own (5.5). */
    val localCatchUp: Boolean = false,
    /**
     * Locked now (itself, its group, or its program's rating) and not opened with the PIN (5.12):
     * the picture can't show it, so OK goes straight to full screen, where the PIN is asked.
     */
    val needsPin: Boolean = locked,
)

/** A source a channel plays from: its key for Switch Source, and the source's name. */
@Immutable
data class LiveSource(val key: String, val name: String)

/** The channel playing in the preview, and the source it plays from (5.1). */
@Immutable
data class LivePlaying(val channelId: String, val source: String?)

/**
 * The source Remember What Works per Channel keeps for a channel, and since when (5.1): the
 * channel starts on it, and Switch Source lists it first, "Works best" (4.35).
 */
@Immutable
data class LiveRemembered(val source: String, val since: Instant)

data class UpcomingProgram(
    val id: String,
    val time: String,
    val title: String,
    val reminder: Boolean = false,
    val recordingScheduled: Boolean = false,
    /** A Record Series rule has its title (brief 5.6). */
    val seriesRule: Boolean = false,
)

/** What's on a channel now, under Live TV's preview (board 07). */
data class NowProgram(
    val title: String,
    val range: String,
    /** "1 h 19 min left" */
    val left: String,
    val progress: Float,
    val description: String?,
)

/** Under Live TV's preview: what's on now, and the channel's schedule after it. */
data class ChannelPreview(
    /** What's on now; null in a gap in the guide, with programs still to come. */
    val now: NowProgram?,
    /**
     * What's coming up, as far as the guide goes: the channel's schedule, as a Stalker portal shows
     * it under its picture (the owner's ask, 28 October). Board 07 shows three; the rest scroll.
     */
    val upcoming: List<UpcomingProgram>,
)

data class LiveTvUiState(
    val profileName: String,
    val profileInitial: String,
    val groups: List<LiveGroup>,
    val channels: Map<String, List<LiveChannel>>,
    /**
     * The groups the user made (the owner's ask, 1 November), shown or hidden, empty or not: Add to
     * Category puts a channel in one, or takes it out.
     */
    val ownGroups: List<OwnGroup> = emptyList(),
    /** What's on a channel and coming up, asked for as the preview moves to it; null without a guide. */
    val preview: (channelId: String) -> ChannelPreview? = { null },
    val selectedGroupId: String,
    /** The channel playing now, focused when Live TV opens. */
    val currentChannelId: String,
    /** Hidden channels keep their numbers but stay out of the lists (5.2). */
    val hidden: Set<String> = emptySet(),
    /**
     * The full list's numbers and the channel that has each, hidden ones too: Change Number
     * takes only a number none has (5.2). Without it, the full list's own numbers.
     */
    val numberOwners: Map<Int, String> = channels[ALL_CHANNELS].orEmpty().mapNotNull { c -> c.fullNumber?.let { it to c.name } }.toMap(),
) {
    /**
     * Each channel's row as All Channels lists it, by id: the picture's channel and the sheets'.
     * Like [favoriteIds] and [lockedIds], it's made once with the state rather than as focus moves,
     * off the main thread where the app makes the state there (docs/decisions.md, Snappier).
     */
    val byId: Map<String, LiveChannel> = (channels[ALL_CHANNELS] ?: channels.values.flatten()).let { all ->
        HashMap<String, LiveChannel>(all.size * 2).apply { for (channel in all) putIfAbsent(channel.id, channel) }
    }

    /** The channels any list marks as favorites. */
    val favoriteIds: Set<String> = channels.values.flatMapTo(HashSet()) { list -> list.filter { it.favorite }.map { it.id } }

    /** The channels any list marks as locked. */
    val lockedIds: Set<String> = channels.values.flatMapTo(HashSet()) { list -> list.filter { it.locked }.map { it.id } }
}

enum class SortOrder { Number, Name, Recent }

/** A group the user made, and the channels in it, by the ids they show under. */
@Immutable
data class OwnGroup(val id: String, val name: String, val channelIds: Set<String>, val hidden: Boolean = false)

/** Channels' own volume levels, and whose sound passes through to a receiver now (brief 5.4.6). */
@Immutable
data class LiveSound(
    /** dB, by channel; channels at 0 dB aren't here. */
    val levels: Map<String, Int> = emptyMap(),
    /** The channel playing whose sound passes through untouched, so a level can't change it. */
    val passingThrough: String? = null,
)

/** What Live TV asks the app to do. */
class LiveTvActions(
    val onTab: (TopTab) -> Unit = {},
    val onSettings: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val onWatch: (channelId: String) -> Unit = {},
    /**
     * What's on and coming up under the picture is [channelId]'s now: the focused channel's once
     * focus rests on it, or the playing one's (Live TV Details). A channel the guide misses has its
     * server's now and next asked for (5.3).
     */
    val onDetails: (channelId: String) -> Unit = {},
    /**
     * OK played [channelId] in the picture, from [groupId] (the owner's ask, 29 October): it's the
     * last channel now, so Home, the Guide and When the TV Turns On carry on with it.
     */
    val onPlay: (channelId: String, groupId: String) -> Unit = { _, _ -> },
    /** Watching from a group, so zapping follows that group (5.2); [onWatch] when it's not set. */
    val onWatchFrom: ((channelId: String, groupId: String) -> Unit)? = null,
    val onCatchUp: (channelId: String) -> Unit = {},
    val onMultiview: () -> Unit = {},
    /** Red, or Add to / Remove from Favorites. */
    val onFavorite: (channelId: String, favorite: Boolean) -> Unit = { _, _ -> },
    /**
     * In Favorites, a channel taken out and back in before it went from the list: it's a favorite
     * again in the place it had, right after [after] there, or first when that's null.
     */
    val onFavoriteBack: (channelId: String, after: String?) -> Unit = { channelId, _ -> onFavorite(channelId, true) },
    val onHide: (channelId: String, hidden: Boolean) -> Unit = { _, _ -> },
    val onLock: (channelId: String, locked: Boolean) -> Unit = { _, _ -> },
    /** Remind Me or Cancel Reminder on a programme coming up. */
    val onRemind: (channelId: String, programId: String, on: Boolean) -> Unit = { _, _, _ -> },
    /** Record, or Cancel Recording, on a programme coming up (brief 5.6). */
    val onRecord: (channelId: String, programId: String, on: Boolean) -> Unit = { _, _, _ -> },
    /** Record Series, or Cancel Series, on a programme coming up: a rule for its title on its channel. */
    val onRecordSeries: (channelId: String, programId: String, on: Boolean) -> Unit = { _, _, _ -> },
    /** Volume Level: the bar moved, and the channel, if it's playing, plays at [db] now (5.4.6). */
    val onLevel: (channelId: String, db: Int) -> Unit = { _, _ -> },
    /** Volume Level: OK keeps [db] for the channel. */
    val onKeepLevel: (channelId: String, db: Int) -> Unit = { _, _ -> },
    /** Decode This Channel, so a level can change its sound. */
    val onDecode: (channelId: String) -> Unit = {},
    /** Add to, or Remove from, Local Catch-up (brief 5.5). */
    val onLocalCatchUp: (channelId: String, on: Boolean) -> Unit = { _, _ -> },
    /** Switch Source: [channelId] plays from [source], one of its [LiveChannel.sources] (5.1). */
    val onSwitchSource: (channelId: String, source: String) -> Unit = { _, _ -> },
    /** Forget What Works, in Switch Source: [channelId] goes back to its own order (4.35). */
    val onForgetSource: (channelId: String) -> Unit = {},
    /** Change Number: [channelId] has [number] in the full list now (5.2). */
    val onChangeNumber: (channelId: String, number: Int) -> Unit = { _, _ -> },
    /** Move in Favorites: [channelId] comes right after [after] there now, or first when it's null (4.7). */
    val onMoveFavorite: (channelId: String, after: String?) -> Unit = { _, _ -> },
    /** Add to Category: [channelId] goes into the user's group [groupId], or comes out of it when [add] is false. */
    val onOwnGroup: (channelId: String, groupId: String, add: Boolean) -> Unit = { _, _, _ -> },
)

// ---- Screen --------------------------------------------------------------------------------------

/** Where the list's rows start: below the "Sports · 186 channels" header (board 07). */
private const val LIST_TOP_PX = 208

/** Where the lists end: above the footer's key hints. */
private const val LIST_BOTTOM_PX = 976

/** Where the channel list ends, at the footer: its last rows go under the fade (board 07). */
private const val CHANNELS_END_PX = 992

/** The fade at the bottom of the channel list, which the list leaves room for past its last channel. */
private const val LIST_FADE_PX = 120

/** The full list, whose numbers a typed number falls back to (5.2). */
private const val ALL_CHANNELS = "all"

/** Favorites, whose channels Move puts in the user's order (4.7). */
private const val FAVORITES = "favorites"

/** How long a jump to a typed number's channel waits for it to be in the list, then laid out. */
private const val JUMP_WAIT_MS = 1_000L

/** A sheet over Live TV. */
private sealed interface LiveSheet {
    /** Menu or long-press OK on a channel: its options. */
    data class Channel(val id: String) : LiveSheet

    /** OK on a program coming up: Remind Me and Record. */
    data class Upcoming(val channelId: String, val programId: String) : LiveSheet

    /** A typed number belongs to a hidden channel (5.2). */
    data class Hidden(val id: String) : LiveSheet

    /** Channel options › Volume Level: the bar, [start] being the level it had (5.4.6). */
    data class Level(val id: String, val start: Int) : LiveSheet

    /** Channel options › Switch Source: the channel's sources (4.10 and 5.1). */
    data class Sources(val id: String) : LiveSheet

    /** Channel options › Change Number: the number pad (4.35). */
    data class Number(val id: String) : LiveSheet

    /** Channel options › Channel Info. */
    data class Info(val id: String) : LiveSheet

    /** Channel options › Add to Category: the groups the user made. */
    data class Categories(val id: String) : LiveSheet
}

/** Reminders and recordings set from Live TV, until they're kept for real (M2 and M6). */
private data class Marks(val reminder: Boolean, val recording: Boolean)

/** The channel a typed number tunes, and whether the number is the list's own rather than the full list's. */
private data class TypedChannel(val channel: LiveChannel, val listNumber: Boolean)

/**
 * Live TV (board 07, brief 4.7): groups on the left, the group's channels with what's on, and a
 * live picture with what's on and coming up, using the one video layer.
 *
 * Moving through the list never changes the channel in the picture (the owner's ask, 29 October,
 * where brief 4.7 had it follow focus): OK plays the focused channel there, and OK on the channel
 * already there goes full screen. What's on and coming up under the picture follows focus once it
 * rests on a channel, or stays with the picture, as [details] says.
 *
 * Menu or long-press OK on a channel opens its options; OK on a program coming up opens Remind
 * Me and Record. The number keys type a channel number (4.11): in a list that counts from 1, the
 * list's own numbers come first (5.2). The number's channel gets focus, and OK plays it (the
 * owner's #79).
 */
@Composable
fun LiveTvScreen(
    state: LiveTvUiState,
    actions: LiveTvActions,
    modifier: Modifier = Modifier,
    detailsDwellMs: Long = PlasmaMotion.DETAILS_DWELL_MS,
    /** Settings › TV Guide › Live TV Details: whose schedule shows under the picture. */
    details: LiveDetails = LiveDetails.FocusedChannel,
    /** Settings › TV Guide › Channel List Shows: what each row leads with (the owner's #137). */
    listShows: LiveListShows = LiveListShows.WhatsOn,
    sound: LiveSound = LiveSound(),
    /** What plays in the preview, and from which source (5.1). */
    playing: LivePlaying? = null,
    /** The sources Remember What Works per Channel keeps, by channel (5.1). */
    remembered: Map<String, LiveRemembered> = emptyMap(),
) {
    var groupId by remember { mutableStateOf(state.selectedGroupId) }
    var focusedId by remember { mutableStateOf(state.currentChannelId) }
    // The channel in the picture: the one playing when Live TV opens, then the last one OK played.
    var previewId by remember { mutableStateOf(state.currentChannelId) }
    // The channel focus last rested on, which what's on and coming up follows.
    var restedId by remember { mutableStateOf(state.currentChannelId) }
    // A channel a typed number jumps to, until it has focus (the owner's #79).
    var jumpTo by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(SortOrder.Number) }
    // Stars and hidden channels change here at once, and stay changed until a state from the app
    // has them too: one it made before a change reached its database doesn't put the old one back
    // (the owner's report, 1 November: a channel taken out of Favorites came back with its star).
    val starEdits = remember { MarkEdits() }
    val hideEdits = remember { MarkEdits() }
    val favorites = remember(state, starEdits.changed) { starEdits.over(state.favoriteIds) }
    val hidden = remember(state, hideEdits.changed) { hideEdits.over(state.hidden) }
    LaunchedEffect(state) {
        starEdits.settle(state.favoriteIds)
        hideEdits.settle(state.hidden)
    }
    // Changed here at once, and kept by the app (once the PIN allows it); a new state from the app
    // starts them afresh.
    var locked by remember(state) { mutableStateOf(state.lockedIds) }
    var marks by remember { mutableStateOf(emptyMap<String, Marks>()) }
    var sheet by remember { mutableStateOf<LiveSheet?>(null) }
    // Levels set here, until the app's sound catches up.
    var levels by remember(sound.levels) { mutableStateOf(sound.levels) }
    // The program the last Remind / Record sheet was for, which gets focus back when it closes.
    var upcomingFor by remember { mutableStateOf<String?>(null) }
    val entry = remember { NumberEntry() }
    // Move in Favorites (4.7): the channel being carried, the order it's carried through, and
    // Favorites' order when it lifted.
    var moving by remember { mutableStateOf<String?>(null) }
    var moveOrder by remember(groupId) { mutableStateOf<List<String>?>(null) }
    var moveBase by remember(groupId) { mutableStateOf<List<String>?>(null) }
    // The list and order last edited here (a channel moved, hidden or taken out of Favorites): in
    // it, rows slide to their new places (brief 2.6). A list that becomes another, as a new
    // category or order, fades in instead, rather than sliding rows from the old one's places.
    var edited by remember { mutableStateOf<Pair<String, SortOrder>?>(null) }
    // A category picked in the list of categories shows from its top, and focus goes into it at
    // its first channel, rather than where the last category's list had got to (the owner's ask,
    // 1 November): it's [fresh] until focus has been in its list.
    var fresh by remember { mutableStateOf(false) }
    var enterFirst by remember { mutableStateOf(false) }
    // Channels taken out of Favorites while Favorites shows, with the places they had: each stays,
    // its star off, while focus is on it or it plays in the picture, and goes once neither (the
    // owner's ask, 1 November).
    var staying by remember { mutableStateOf(emptyMap<String, Int>()) }

    val group = state.groups.firstOrNull { it.id == groupId } ?: state.groups.first()

    /**
     * Favorites in its own order, as it shows: the app's, without the channels taken out here,
     * though the app's state may not have caught up yet, and with each [staying] in its place.
     */
    fun favoritesShown(): List<LiveChannel> {
        val own = state.channels[FAVORITES].orEmpty().filter { it.id !in hidden && (!starEdits.unmarked(it.id) || it.id in staying) }
        return if (staying.isEmpty()) own else withStaying(own, staying, state.byId)
    }
    val channels = remember(groupId, sort, favorites, starEdits.changed, locked, hidden, state, staying) {
        val shown = if (group.id == FAVORITES) favoritesShown() else state.channels[group.id].orEmpty().filter { it.id !in hidden }
        val list = shown.map { it.marked(favorite = it.id in favorites, locked = it.id in locked) }
        when (sort) {
            SortOrder.Number -> list
            SortOrder.Name -> list.sortedBy { it.name }
            SortOrder.Recent -> {
                val recent = state.channels["recent"].orEmpty().map { it.id }
                list.sortedBy { recent.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            }
        }
    }

    // While a channel moves, and once it's dropped until the app's own order changes, the list
    // goes in the moved order, Favorites numbered as they stand. (Made with the list, not at each
    // focus move: All Channels can have thousands.)
    val stateOrder = remember(channels) { channels.map { it.id } }
    LaunchedEffect(stateOrder) {
        if (moving == null && moveBase != null && stateOrder != moveBase) {
            moveOrder = null
            moveBase = null
        }
    }
    val listed = moveOrder?.takeIf { moving != null || stateOrder == moveBase }?.let { order ->
        val byId = channels.associateBy { it.id }
        order.mapNotNull(byId::get).mapIndexed { i, ch -> if (group.countsFromOne) ch.copy(number = (i + 1).toString()) else ch }
    } ?: channels

    val listFocus = remember { FocusRequester() }
    val currentRow = remember { FocusRequester() }
    val groupsFocus = remember { FocusRequester() }
    val upcomingFirst = remember { FocusRequester() }
    val focusedRow = remember { FocusRequester() }
    val upcomingReturn = remember { FocusRequester() }
    val currentIndex = remember(channels, state.currentChannelId) { channels.indexOfFirst { it.id == state.currentChannelId } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (currentIndex >= 0) currentRow.requestFocus() else listFocus.requestFocus()
    }
    // What's on and coming up follows focus once it rests on a channel, so holding Down doesn't
    // redraw it for every row; or it stays with the picture (Live TV Details).
    LaunchedEffect(focusedId) {
        if (focusedId != restedId) {
            delay(detailsDwellMs)
            restedId = focusedId
        }
    }
    val detailsId = if (details == LiveDetails.PlayingChannel) previewId else restedId
    LaunchedEffect(detailsId) { actions.onDetails(detailsId) }
    val find = { number: String -> findChannel(number, group, state) }
    fun watch(channelId: String, from: String) {
        val watchFrom = actions.onWatchFrom
        if (watchFrom != null) watchFrom(channelId, from) else actions.onWatch(channelId)
    }

    /**
     * OK on a channel (the owner's ask, 29 October): it plays in the picture, and OK on the one
     * already there goes full screen. One that needs the PIN can't show in the picture, so it goes
     * straight to full screen, where the PIN is asked.
     */
    fun ok(channel: LiveChannel) {
        if (channel.id == previewId || channel.needsPin) {
            watch(channel.id, group.id)
        } else {
            previewId = channel.id
            actions.onPlay(channel.id, group.id)
        }
    }

    /** A favorite now, with the stars changed here: read as it's asked, so a second change sees the first. */
    fun isFavorite(channelId: String): Boolean = starEdits.changed[channelId] ?: (channelId in state.favoriteIds)

    /** The favorite before [channelId] in Favorites' own order, as it shows: null when it's first. */
    fun favoriteBefore(channelId: String): String? {
        val own = favoritesShown()
        val at = own.indexOfFirst { it.id == channelId }
        return if (at <= 0) null else own.subList(0, at).lastOrNull { isFavorite(it.id) }?.id
    }

    /**
     * Red, or Add to / Remove from Favorites. Taken out of Favorites while it shows there, a
     * channel stays in its place while focus is on it or it plays (the owner's ask, 1 November);
     * put back before it goes, it's a favorite again in that place.
     */
    fun favorite(channelId: String) {
        edited = group.id to sort
        val adding = !isFavorite(channelId)
        val inFavorites = group.id == FAVORITES
        when {
            inFavorites && !adding -> {
                staying = staying + (channelId to favoritesShown().indexOfFirst { it.id == channelId }.coerceAtLeast(0))
                actions.onFavorite(channelId, false)
            }
            inFavorites && channelId in staying -> actions.onFavoriteBack(channelId, favoriteBefore(channelId))
            else -> actions.onFavorite(channelId, adding)
        }
        starEdits.set(channelId, adding)
    }

    /** A category picked in the list of categories: its channels show from the top. */
    fun pick(id: String) {
        if (id == group.id) return
        groupId = id
        fresh = true
        listState.requestScrollToItem(0)
    }

    /** Focus goes to [channelId] in [listId], which opens if it isn't the one showing. */
    fun focusChannel(channelId: String, listId: String) {
        groupId = listId
        focusedId = channelId
        jumpTo = channelId
    }
    // A typed number jumps to its channel 1.5 s after the last digit, or on OK (4.11), and focuses
    // it without playing it; OK then plays it (the owner's #79). A hidden channel asks first.
    val jump = { typed: TypedChannel ->
        if (typed.channel.id in hidden) {
            sheet = LiveSheet.Hidden(typed.channel.id)
        } else {
            // A full-list number goes to All Channels, where zapping then follows it (5.2).
            focusChannel(typed.channel.id, if (typed.listNumber) group.id else ALL_CHANNELS)
        }
    }
    NumberEntryEffect(entry, find, jump)
    // The list scrolls to the channel jumped to, third from the top as when Live TV opens, and it
    // takes focus once it's laid out; the list may change first, as when the number opened All
    // Channels.
    val latestListed by rememberUpdatedState(listed)
    LaunchedEffect(jumpTo) {
        val id = jumpTo ?: return@LaunchedEffect
        val index = withTimeoutOrNull(JUMP_WAIT_MS) { snapshotFlow { latestListed.indexOfFirst { it.id == id } }.first { it >= 0 } }
        if (index != null) {
            listState.scrollToItem((index - 2).coerceAtLeast(0))
            withTimeoutOrNull(JUMP_WAIT_MS) { snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == id } }.first { it } }
            // Focus coming into the list from outside (the groups, a sheet) goes through its focus
            // restorer, which would put it back on the row that had it last; once focus is in the
            // list, it moves to the channel. Neither call waits, so the row still has the requester.
            runCatching { listFocus.requestFocus() }
            runCatching { focusedRow.requestFocus() }
        } else {
            runCatching { listFocus.requestFocus() }
        }
        jumpTo = null
    }
    // OK on a category just picked, or Right into it: focus goes to its first channel.
    LaunchedEffect(enterFirst) {
        if (!enterFirst) return@LaunchedEffect
        enterFirst = false
        fresh = false
        val first = latestListed.firstOrNull()
        if (first != null) {
            focusedId = first.id
            jumpTo = first.id
        } else {
            runCatching { listFocus.requestFocus() }
        }
    }
    // A channel kept in Favorites goes once focus has moved off it and it isn't in the picture, or
    // once another category shows.
    LaunchedEffect(focusedId, previewId, group.id) {
        if (staying.isEmpty()) return@LaunchedEffect
        staying = if (group.id == FAVORITES) staying.filterKeys { it == focusedId || it == previewId } else emptyMap()
    }

    // A held Up or Down (the owner's ask, 1 November): focus moves on at once, further the longer
    // the key is held, the list following without easing, and it stops at the list's ends rather
    // than going up to the top bar. A press moves one channel, as before.
    val held = LocalHeldKeys.current
    var heldTo by remember { mutableStateOf<String?>(null) }
    fun heldMove(event: KeyEvent): Boolean {
        if (!held.holding || event.type != KeyEventType.KeyDown) return false
        val direction = when (event.key) {
            Key.DirectionUp -> -1
            Key.DirectionDown -> 1
            else -> return false
        }
        val from = listed.indexOfFirst { it.id == focusedId }
        if (from < 0) return false
        val to = (from + direction * held.stride()).coerceIn(0, listed.lastIndex)
        if (to != from) {
            focusedId = listed[to].id
            listState.jumpToPivot(to)
            heldTo = focusedId
        }
        return true
    }
    LaunchedEffect(heldTo) {
        val id = heldTo ?: return@LaunchedEffect
        // The row is there once the list has moved, a frame on; it takes focus where it already is.
        withTimeoutOrNull(JUMP_WAIT_MS) { snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == id } }.first { it } }
        runCatching { focusedRow.requestFocus() }
        heldTo = null
    }

    /** Move: [channel] lifts in Favorites' own order, the order it's numbered in. */
    fun startMove(channel: LiveChannel) {
        sort = SortOrder.Number
        edited = group.id to SortOrder.Number
        // Favorites as it shows, without a channel taken out that's still there.
        val order = favoritesShown().filter { !starEdits.unmarked(it.id) }.map { it.id }
        moveOrder = order
        moveBase = order
        moving = channel.id
    }

    /** Up or Down while a channel moves: it trades places with its neighbor, and stays in view. */
    fun moveBy(delta: Int) {
        val order = moveOrder ?: return
        listState.carry(order, order.indexOf(moving ?: return), delta, MOVE_CONTEXT)?.let { moveOrder = it }
    }

    /** OK: the channel stays where it is, and the app keeps the order. */
    fun drop() {
        val id = moving ?: return
        val order = moveOrder ?: return
        moving = null
        if (order != moveBase) {
            actions.onMoveFavorite(id, order.getOrNull(order.indexOf(id) - 1))
        } else {
            moveOrder = null
            moveBase = null
        }
    }

    /** Back: the channel goes back where it was, and the list keeps it in view there. */
    fun cancelMove() {
        val id = moving ?: return
        listState.keepInView(stateOrder.indexOf(id), stateOrder.size, MOVE_CONTEXT)
        moving = null
        moveOrder = null
        moveBase = null
    }

    // The focused channel, looked for when Red or Green asks rather than at each focus move.
    fun focused() = channels.firstOrNull { it.id == focusedId }
    val detailsNow = state.preview(detailsId)?.withMarks(marks)
    val hasUpcoming = detailsNow?.upcoming.orEmpty().isNotEmpty()
    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (sheet != null) return@onPreviewKeyEvent false
                // While a channel moves, Up and Down carry it, OK drops it and Back puts it back;
                // nothing else acts until it's dropped (board 04's move mode).
                if (moving != null) {
                    val down = event.type == KeyEventType.KeyDown
                    val released = event.type == KeyEventType.KeyUp
                    when {
                        event.key == Key.DirectionUp -> if (down) moveBy(-1)
                        event.key == Key.DirectionDown -> if (down) moveBy(+1)
                        event.key in SelectKeys -> if (released) drop()
                        event.key == Key.Back || event.key == Key.Escape -> if (released) cancelMove()
                    }
                    return@onPreviewKeyEvent true
                }
                val up = event.type == KeyEventType.KeyUp
                val digit = event.key.digit()
                when {
                    digit != null -> {
                        if (!up) entry.type(digit)
                        true
                    }
                    // While a number is being typed, OK goes to it now and Back drops it.
                    entry.isActive && event.key in SelectKeys -> {
                        if (up) entry.finish(find, jump)
                        true
                    }
                    entry.isActive && (event.key == Key.Back || event.key == Key.Escape) -> {
                        if (up) entry.clear()
                        true
                    }
                    !up -> false
                    event.key == Key.ProgramRed -> {
                        focused()?.let { ch -> favorite(ch.id) }
                        true
                    }
                    event.key == Key.ProgramGreen -> {
                        focused()?.let { actions.onCatchUp(it.id) }
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
            onFocusItem = { pick(it) },
            onOpenItem = {
                pick(it)
                if (fresh) enterFirst = true else listFocus.requestFocus()
            },
            modifier = Modifier
                .offset(96.px, 148.px)
                .size(300.px, (LIST_BOTTOM_PX - 148).px)
                // Right into a category just picked goes to its first channel.
                .onPreviewKeyEvent { event ->
                    if (fresh && event.key == Key.DirectionRight && event.type == KeyEventType.KeyDown && listed.isNotEmpty()) {
                        enterFirst = true
                        true
                    } else {
                        false
                    }
                }
                .focusRequester(groupsFocus),
        )

        // Another category or order fades in over 150 ms rather than jumping (brief 2.6).
        val listFade = rememberSwapFade(group.id to sort)
        val reduceMotion = LocalReduceMotion.current
        val slides = edited == group.id to sort

        // Channel list (x 436)
        Column(Modifier.offset(436.px, 148.px).width(720.px).swapFade(listFade)) {
            val carried = moving?.let { id -> channels.firstOrNull { it.id == id } }
            Row(Modifier.height(46.px), horizontalArrangement = Arrangement.spacedBy(14.px)) {
                if (carried != null) {
                    // "Moving Arena Sports 1 · Press OK to drop it here", over the list it moves in.
                    PlasmaText(stringResource(DesignR.string.moving, carried.name), plasmaStyle(32f, 600, trackingPx = -0.4f), Modifier.alignBy(FirstBaseline))
                    PlasmaText(stringResource(DesignR.string.moving_hint), plasmaStyle(22f, 500), Modifier.alignBy(FirstBaseline), color = PlasmaColors.Text3)
                } else {
                    PlasmaText(group.name, plasmaStyle(32f, 600, trackingPx = -0.4f), Modifier.alignBy(FirstBaseline))
                    PlasmaText(
                        pluralStringResource(R.plurals.channel_count, group.count, Formats.count(group.count)),
                        plasmaStyle(22f, 500),
                        Modifier.alignBy(FirstBaseline),
                        color = PlasmaColors.Text3,
                    )
                }
            }
        }
        if (channels.isEmpty()) {
            PlasmaText(
                stringResource(R.string.empty_group),
                plasmaStyle(23f, 400),
                Modifier.offset(448.px, LIST_TOP_PX.px).swapFade(listFade),
                color = PlasmaColors.Text2,
            )
        }
        PlasmaColumn(
            state = listState,
            gap = 6.px,
            // Room past the last channel for the fade: at the end of the list, it shows whole above
            // the fade rather than under it (the owner's report, 1 November).
            contentPadding = PaddingValues(bottom = LIST_FADE_PX.px),
            modifier = Modifier
                .offset(436.px, LIST_TOP_PX.px)
                .size(720.px, (CHANNELS_END_PX - LIST_TOP_PX).px)
                .swapFade(listFade)
                .onFocusChanged { if (it.hasFocus) fresh = false }
                .onPreviewKeyEvent(::heldMove)
                .focusRequester(listFocus),
        ) {
            itemsIndexed(listed, key = { _, ch -> ch.id }) { index, channel ->
                ChannelRow(
                    itemModifier = Modifier.itemMotion(this, reduceMotion, placement = slides, fade = false),
                    channel = channel,
                    onClick = { ok(channel) },
                    onLongClick = { sheet = LiveSheet.Channel(channel.id) },
                    onFocused = { focusedId = channel.id },
                    playing = channel.id == previewId,
                    nameFirst = listShows == LiveListShows.ChannelNames,
                    moving = channel.id == moving,
                    dimmed = moving != null && channel.id != moving,
                    canMoveUp = index > 0,
                    canMoveDown = index < listed.lastIndex,
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
                .offset(436.px, (CHANNELS_END_PX - LIST_FADE_PX).px)
                .size(720.px, LIST_FADE_PX.px)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.85f to PlasmaColors.Bg)),
        )

        Preview(
            playing = state.byId[previewId],
            detailsFor = detailsId,
            preview = detailsNow,
            onUpcoming = { programId ->
                upcomingFor = programId
                sheet = LiveSheet.Upcoming(detailsId, programId)
            },
            upcomingFirst = upcomingFirst,
            upcomingReturn = upcomingReturn,
            returnTo = upcomingFor,
            modifier = Modifier.offset(1196.px, 148.px).size(628.px, (LIST_BOTTOM_PX - 148).px),
        )

        KeyFooter(
            colorKeys = if (moving != null) {
                emptyList()
            } else {
                listOf(
                    ColorKey.Red to stringResource(R.string.key_favorite),
                    ColorKey.Green to stringResource(R.string.key_catch_up),
                    ColorKey.Yellow to stringResource(R.string.key_sort),
                    ColorKey.Blue to stringResource(R.string.key_multiview),
                )
            },
            keys = if (moving != null) {
                listOf(
                    FooterKey("OK", stringResource(DesignR.string.key_drop)),
                    FooterKey(PlasmaIcons.Back, stringResource(DesignR.string.key_cancel)),
                )
            } else {
                listOf(
                    FooterKey("OK", stringResource(R.string.key_watch)),
                    FooterKey(PlasmaIcons.KeyLeft, stringResource(R.string.key_groups), iconSize = 13.px),
                    FooterKey(PlasmaIcons.Menu, stringResource(R.string.key_options)),
                    FooterKey("0–9", stringResource(R.string.key_go_to_channel)),
                )
            },
        )

        val typed = if (entry.isActive) find(entry.digits) else null
        val match = when {
            typed == null -> null
            typed.listNumber && group.countsFromOne -> stringResource(R.string.list_number, group.name, typed.channel.number, typed.channel.name)
            else -> typed.channel.name
        }
        NumberEntryOverlay(entry, match, Modifier.offset(96.px, 56.px))

        val optionsFor = (sheet as? LiveSheet.Channel)?.let { s -> channels.firstOrNull { it.id == s.id } }
        // Hiding the last channel in the list leaves the groups to take focus.
        SheetHost(optionsFor, returnFocus = if (channels.isEmpty()) groupsFocus else focusedRow) { sheetChannel ->
            ChannelOptions(
                channel = sheetChannel,
                canMove = group.id == FAVORITES && listed.size > 1,
                level = levels[sheetChannel.id] ?: 0,
                source = sheetChannel.sourceFrom(playing, remembered),
                onDismiss = { sheet = null },
                onVolumeLevel = { sheet = LiveSheet.Level(sheetChannel.id, levels[sheetChannel.id] ?: 0) },
                onSwitchSource = { sheet = LiveSheet.Sources(sheetChannel.id) },
                onChangeNumber = { sheet = LiveSheet.Number(sheetChannel.id) },
                onInfo = { sheet = LiveSheet.Info(sheetChannel.id) },
                onCategories = if (state.ownGroups.isEmpty()) null else ({ sheet = LiveSheet.Categories(sheetChannel.id) }),
                onLocalCatchUp = { actions.onLocalCatchUp(sheetChannel.id, !sheetChannel.localCatchUp) },
                onMove = { startMove(sheetChannel) },
                onFavorite = { favorite(sheetChannel.id) },
                onLock = {
                    locked = locked.toggle(sheetChannel.id)
                    actions.onLock(sheetChannel.id, sheetChannel.id in locked)
                },
                onHide = {
                    // Focus moves on to the next channel, or the one before at the end of the list.
                    val i = channels.indexOf(sheetChannel)
                    channels.getOrNull(i + 1)?.let { focusedId = it.id } ?: channels.getOrNull(i - 1)?.let { focusedId = it.id }
                    edited = group.id to sort
                    hideEdits.set(sheetChannel.id, true)
                    actions.onHide(sheetChannel.id, true)
                },
            )
        }

        val upcomingSheet = sheet as? LiveSheet.Upcoming
        val upcomingOpen = upcomingSheet?.let { s -> state.preview(s.channelId)?.withMarks(marks)?.upcoming?.firstOrNull { it.id == s.programId } }
        SheetHost(upcomingOpen, returnFocus = upcomingReturn) { upcoming ->
            UpcomingOptions(
                program = upcoming,
                channelName = upcomingSheet?.let { state.byId[it.channelId] }?.name.orEmpty(),
                onDismiss = { sheet = null },
                onSeries = { upcomingSheet?.let { actions.onRecordSeries(it.channelId, upcoming.id, !upcoming.seriesRule) } },
                onChange = { reminder, recording ->
                    if (reminder != upcoming.reminder) upcomingSheet?.let { actions.onRemind(it.channelId, upcoming.id, reminder) }
                    if (recording != upcoming.recordingScheduled) upcomingSheet?.let { actions.onRecord(it.channelId, upcoming.id, recording) }
                    marks = marks + (upcoming.id to Marks(reminder, recording))
                },
            )
        }

        val levelOpen = (sheet as? LiveSheet.Level)?.let { s -> state.byId[s.id]?.let { s to it } }
        SheetHost(levelOpen, returnFocus = if (channels.isEmpty()) groupsFocus else focusedRow) { (levelSheet, levelChannel) ->
            val level = levels[levelChannel.id] ?: 0
            fun move(db: Int) {
                levels = levels + (levelChannel.id to db)
                actions.onLevel(levelChannel.id, db)
            }
            LevelBar(
                label = stringResource(R.string.volume_level_bar),
                subject = levelChannel.name,
                value = level,
                valueText = levelText(level),
                min = VolumeLevels.MIN_DB,
                max = VolumeLevels.MAX_DB,
                onChange = ::move,
                onKeep = {
                    actions.onKeepLevel(levelChannel.id, level)
                    sheet = null
                },
                onCancel = {
                    if (level != levelSheet.start) move(levelSheet.start)
                    sheet = null
                },
                note = if (sound.passingThrough == levelChannel.id) stringResource(R.string.level_passthrough) else null,
                action = stringResource(R.string.decode_channel),
                onAction = { actions.onDecode(levelChannel.id) },
            )
        }

        val returnFocus = if (channels.isEmpty()) groupsFocus else focusedRow
        val sourcesOpen = (sheet as? LiveSheet.Sources)?.let { s -> state.byId[s.id] }
        SheetHost(sourcesOpen, returnFocus = returnFocus) { sourcesChannel ->
            // Ticked: the source it plays from in the preview, or else the one it would start on.
            val from = sourcesChannel.sourceFrom(playing, remembered)
            val works = remembered[sourcesChannel.id]
            SwitchSourceSheet(
                channel = sourcesChannel.name,
                sources = sourcesChannel.sources.map { source ->
                    SourceChoice(source.key, source.name, playing = source == from, remembered = works?.takeIf { it.source == source.key }?.since)
                },
                onPick = { choice ->
                    sheet = null
                    if (!choice.playing) actions.onSwitchSource(sourcesChannel.id, choice.key)
                },
                onForget = {
                    sheet = null
                    actions.onForgetSource(sourcesChannel.id)
                },
                onDismiss = { sheet = null },
            )
        }

        val numberOpen = (sheet as? LiveSheet.Number)?.let { s -> state.byId[s.id] }?.takeIf { it.fullNumber != null }
        SheetHost(numberOpen, returnFocus = returnFocus) { numberChannel ->
            val current = numberChannel.fullNumber ?: return@SheetHost
            NumberChooser(
                back = stringResource(DesignR.string.tab_live_tv),
                title = stringResource(R.string.change_number),
                subtitle = stringResource(R.string.change_number_for, numberChannel.name, current),
                current = current,
                ownerOf = { number -> state.numberOwners[number] },
                onUse = { number ->
                    actions.onChangeNumber(numberChannel.id, number)
                    sheet = null
                },
                onDismiss = { sheet = null },
            )
        }

        val infoOpen = (sheet as? LiveSheet.Info)?.let { s -> state.byId[s.id] }
        SheetHost(infoOpen, returnFocus = returnFocus) { infoChannel ->
            ChannelInfo(infoChannel, infoChannel.sourceFrom(playing, remembered), onDismiss = { sheet = null })
        }

        val categoriesOpen = (sheet as? LiveSheet.Categories)?.let { s -> state.byId[s.id] }
        SheetHost(categoriesOpen, returnFocus = returnFocus) { categoriesChannel ->
            OwnGroupsSheet(
                channel = categoriesChannel,
                groups = state.ownGroups,
                onPick = { picked, add -> actions.onOwnGroup(categoriesChannel.id, picked.id, add) },
                onDismiss = { sheet = null },
            )
        }

        val hiddenOpen = (sheet as? LiveSheet.Hidden)?.let { s -> state.byId[s.id] }
        // Unhide takes focus to the channel, which the jump to it hands back once it's in view.
        SheetHost(hiddenOpen, returnFocus = if (channels.isEmpty()) groupsFocus else focusedRow, handBack = { jumpTo == null }) { hiddenChannel ->
            HiddenChannel(
                channel = hiddenChannel,
                onDismiss = { sheet = null },
                onUnhide = {
                    hideEdits.set(hiddenChannel.id, false)
                    actions.onHide(hiddenChannel.id, false)
                    // Back in its list, with focus, as any typed number's channel (the owner's #79).
                    val inList = state.channels[group.id].orEmpty().any { it.id == hiddenChannel.id }
                    focusChannel(hiddenChannel.id, if (inList) group.id else ALL_CHANNELS)
                },
            )
        }
    }
}

private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

/**
 * A mark changed on this screen, a star or a hidden channel, over the app's own: each change stays
 * until a state from the app has it too. A state the app made before the change reached its
 * database still has the mark as it was, and doesn't put it back.
 */
@Stable
private class MarkEdits {
    /** Marked (true) or not (false), by id, as changed here. */
    var changed by mutableStateOf(emptyMap<String, Boolean>())
        private set

    fun set(id: String, marked: Boolean) {
        changed = changed + (id to marked)
    }

    /** Unmarked here: a channel taken out of Favorites, or shown again. */
    fun unmarked(id: String): Boolean = changed[id] == false

    /** The app's [marked] ids, with the changes made here. */
    fun over(marked: Set<String>): Set<String> =
        if (changed.isEmpty()) marked else HashSet(marked).apply { for ((id, on) in changed) if (on) add(id) else remove(id) }

    /** Lets go of the changes a state from the app has: its [marked] ids. */
    fun settle(marked: Set<String>) {
        if (changed.isNotEmpty()) changed = changed.filter { (id, on) -> (id in marked) != on }
    }
}

/**
 * [list] with each channel in [staying] that has left it back in the place it had, as All Channels
 * has it ([byId]): Favorites while a channel just taken out of it still has focus or plays.
 */
private fun withStaying(list: List<LiveChannel>, staying: Map<String, Int>, byId: Map<String, LiveChannel>): List<LiveChannel> {
    val have = list.mapTo(HashSet()) { it.id }
    val back = list.toMutableList()
    for ((id, place) in staying.entries.sortedBy { it.value }) {
        if (id !in have) byId[id]?.let { back.add(place.coerceIn(0, back.size), it) }
    }
    return back
}

/** The row with this screen's own star and lock: itself while they're what the app gave it, so a list keeps its rows. */
private fun LiveChannel.marked(favorite: Boolean, locked: Boolean): LiveChannel =
    if (favorite == this.favorite && locked == this.locked) this else copy(favorite = favorite, locked = locked)

/** "+3 dB", "0 dB", "−2 dB". */
@Composable
private fun levelText(db: Int): String = when {
    db > 0 -> stringResource(R.string.level_up, db)
    db < 0 -> stringResource(R.string.level_down, -db)
    else -> stringResource(R.string.level_none)
}

private fun ChannelPreview.withMarks(marks: Map<String, Marks>) = copy(
    upcoming = upcoming.map { program ->
        marks[program.id]?.let { program.copy(reminder = it.reminder, recordingScheduled = it.recording) } ?: program
    },
)

/**
 * The channel a typed [number] tunes (5.2): the open list's channel with that number, as the player
 * picks, then All Channels'. Whatever a list shows beside its channels is what its numbers tune.
 */
private fun findChannel(number: String, group: LiveGroup, state: LiveTvUiState): TypedChannel? =
    state.channels[group.id].orEmpty().firstOrNull { it.number == number }?.let { TypedChannel(it, listNumber = true) }
        ?: state.channels[ALL_CHANNELS].orEmpty().firstOrNull { it.number == number }?.let { TypedChannel(it, listNumber = false) }

/**
 * Menu or long-press OK on a channel (brief 4.7 and 4.35). Move is there in Favorites, with
 * another favorite to move past, Switch Source for a channel with more than one source, with
 * the one it plays from, and Add to Local Catch-up for one without an archive of its own (5.5).
 */
@Composable
private fun ChannelOptions(
    channel: LiveChannel,
    canMove: Boolean,
    level: Int,
    /** The source it plays from, or would. */
    source: LiveSource?,
    onDismiss: () -> Unit,
    onVolumeLevel: () -> Unit,
    onFavorite: () -> Unit,
    onLock: () -> Unit,
    onHide: () -> Unit,
    onSwitchSource: () -> Unit,
    onChangeNumber: () -> Unit,
    onInfo: () -> Unit,
    /** Add to Category, while the user has made a group to put it in. */
    onCategories: (() -> Unit)? = null,
    onLocalCatchUp: () -> Unit = {},
    onMove: () -> Unit = {},
) {
    OptionsSheet(
        title = channel.name,
        subtitle = listOfNotNull(channel.number.takeIf { LocalShowChannelNumbers.current }, channel.nowTitle).joinToString(" · "),
        onDismiss = onDismiss,
        leading = { LogoPlate(channel.logo, channel.name, 150.px, 66.px, logoSize = LogoSize.Plate) },
        options = listOfNotNull(
            SheetOption(stringResource(if (channel.favorite) R.string.remove_favorite else R.string.add_favorite), onFavorite, icon = PlasmaIcons.Star),
            onCategories?.let { SheetOption(stringResource(R.string.add_to_category), it, icon = PlasmaIcons.Folder) },
            if (canMove) SheetOption(stringResource(R.string.move), onMove, icon = PlasmaIcons.Move) else null,
            SheetOption(stringResource(R.string.hide_channel), onHide, icon = PlasmaIcons.Hide),
            // A channel locked with its group is unlocked with it, in Settings.
            if (channel.lockedWithGroup) null else SheetOption(stringResource(if (channel.locked) R.string.unlock_channel else R.string.lock_channel), onLock, icon = PlasmaIcons.Lock),
            SheetOption.Divider,
            if (channel.sources.size > 1) SheetOption(stringResource(DesignR.string.switch_source), onSwitchSource, value = source?.name, icon = PlasmaIcons.SwitchSource) else null,
            if (channel.fullNumber != null) {
                SheetOption(stringResource(R.string.change_number), onChangeNumber, value = channel.fullNumber.toString(), icon = PlasmaIcons.Hash)
            } else {
                null
            },
            SheetOption(stringResource(R.string.volume_level), onVolumeLevel, value = levelText(level), icon = PlasmaIcons.Volume),
            if (channel.catchUpDays == 0) {
                SheetOption(stringResource(if (channel.localCatchUp) R.string.remove_local_catch_up else R.string.add_local_catch_up), onLocalCatchUp, icon = PlasmaIcons.CatchUp)
            } else {
                null
            },
            SheetOption(stringResource(R.string.channel_info), onInfo, icon = PlasmaIcons.Info),
        ),
    )
}

/**
 * Channel options › Add to Category (the owner's ask, 1 November): the groups the user made, those
 * the channel is in ticked. OK on one puts it in, or takes it out of a ticked one.
 */
@Composable
private fun OwnGroupsSheet(channel: LiveChannel, groups: List<OwnGroup>, onPick: (OwnGroup, add: Boolean) -> Unit, onDismiss: () -> Unit) {
    val hidden = stringResource(R.string.own_category_hidden)
    OptionsSheet(
        title = stringResource(R.string.add_to_category),
        subtitle = stringResource(R.string.add_to_category_text, channel.name),
        onDismiss = onDismiss,
        options = groups.map { group ->
            val inIt = channel.id in group.channelIds
            SheetOption(group.name, { onPick(group, !inIt) }, checked = inIt, note = hidden.takeIf { group.hidden })
        },
    )
}

/**
 * The source this channel plays from in the preview, or else the one it would start on: the one
 * Remember What Works per Channel keeps, or its first (5.1).
 */
private fun LiveChannel.sourceFrom(playing: LivePlaying?, remembered: Map<String, LiveRemembered>): LiveSource? {
    if (playing?.channelId == id) sources.firstOrNull { it.key == playing.source }?.let { return it }
    return sources.firstOrNull { it.key == remembered[id]?.source } ?: sources.firstOrNull()
}

/**
 * Channel options › Channel Info: its number, group, sources, guide id and archive, in the
 * options sheet's rows. OK or Back closes it.
 */
@Composable
private fun ChannelInfo(channel: LiveChannel, source: LiveSource?, onDismiss: () -> Unit) {
    val none = stringResource(R.string.info_none)
    val sources = channel.sources.sortedByDescending { it == source }.map { it.name }.distinct()
    OptionsSheet(
        title = channel.name,
        subtitle = stringResource(R.string.channel_info),
        onDismiss = onDismiss,
        leading = { LogoPlate(channel.logo, channel.name, 150.px, 66.px, logoSize = LogoSize.Plate) },
        options = listOfNotNull(
            SheetOption(stringResource(R.string.info_number), onDismiss, value = (channel.fullNumber ?: channel.number).toString(), icon = PlasmaIcons.Hash),
            SheetOption(stringResource(R.string.info_group), onDismiss, value = channel.groupName ?: none, icon = PlasmaIcons.ChannelList),
            if (sources.isNotEmpty()) {
                SheetOption(
                    pluralStringResource(R.plurals.info_sources, sources.size),
                    onDismiss,
                    value = sources.joinToString(" · "),
                    icon = PlasmaIcons.SwitchSource,
                )
            } else {
                null
            },
            SheetOption(stringResource(R.string.info_guide), onDismiss, value = channel.guideId ?: none, icon = PlasmaIcons.Guide),
            SheetOption(
                stringResource(R.string.info_catch_up),
                onDismiss,
                value = if (channel.catchUpDays > 0) pluralStringResource(R.plurals.info_days, channel.catchUpDays, channel.catchUpDays) else none,
                icon = PlasmaIcons.CatchUp,
            ),
        ),
    )
}

/** OK on a program coming up: Remind Me and Record, or their undoing (brief 4.7). */
@Composable
private fun UpcomingOptions(
    program: UpcomingProgram,
    channelName: String,
    onDismiss: () -> Unit,
    onSeries: () -> Unit,
    onChange: (reminder: Boolean, recording: Boolean) -> Unit,
) {
    ProgramSheet(
        title = program.title,
        subtitle = listOf(program.time, channelName).joinToString(" · "),
        reminder = program.reminder,
        recording = program.recordingScheduled,
        onDismiss = onDismiss,
        onReminder = { onChange(!program.reminder, program.recordingScheduled) },
        onRecording = { onChange(program.reminder, !program.recordingScheduled) },
        series = program.seriesRule,
        onSeries = onSeries,
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
 * (★ favourite, ↺ catch-up, 4K). With [nameFirst], the channel's name, with what's on and its
 * progress under it. Rows are 90 px; the focused row grows to 96 px and turns white.
 * A locked channel is dimmed, with a lock after its title. The channel in the picture has its
 * number in white, as the Guide marks the channel playing (board 08).
 */
@Composable
private fun ChannelRow(
    /** The row's place in the list: how it moves there (`itemMotion`). */
    itemModifier: Modifier,
    channel: LiveChannel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier,
    /** In the picture: OK on it goes full screen. */
    playing: Boolean = false,
    /** Channel List Shows › Channel Names (the owner's #137): the name leads, what's on goes under it. */
    nameFirst: Boolean = false,
    /** Being moved in Favorites: lifted, with arrows above and below its number (board 04's move mode). */
    moving: Boolean = false,
    /** Another channel is moving: this one steps back. */
    dimmed: Boolean = false,
    canMoveUp: Boolean = true,
    canMoveDown: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    // Another channel lifting to move, this one steps back over 150 ms rather than at once.
    val stepBack = rememberStepBack(dimmed, DIMMED)
    val height by animateDpAsState(if (focused) 96.px else 90.px, PlasmaMotion.focus(), label = "rowHeight")
    val margin by animateDpAsState(if (focused) 4.px else 0.dp, PlasmaMotion.focus(), label = "rowMargin")
    val named = listOfNotNull(channel.number, channel.name, channel.nowTitle).joinToString(", ")
    val description = if (moving) stringResource(DesignR.string.moving_named, named) else named
    // TalkBack reads the row once, by its description, then the marks it shows, as its state (#128).
    val marks = listOfNotNull(
        stringResource(R.string.locked).takeIf { channel.locked },
        stringResource(R.string.favorite).takeIf { channel.favorite },
        if (channel.uhd) "4K" else stringResource(R.string.catch_up_available).takeIf { channel.catchUp },
    ).joinToString(", ")
    Box(itemModifier.zIndex(if (moving) 2f else 0f)) {
        PlasmaSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
                .padding(vertical = margin)
                .fillMaxWidth()
                .height(height)
                .stepBack(stepBack)
                .then(if (marks.isNotEmpty()) Modifier.semantics { stateDescription = marks } else Modifier),
            description = description,
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
                // Settings › Appearance › Show Channel Numbers.
                if (LocalShowChannelNumbers.current) {
                    val style = plasmaStyle(23f, if (focused || playing) 600 else 500, tabular = true)
                    PlasmaText(
                        channel.number,
                        style,
                        // Wider with larger text, so the number isn't cut off (#128).
                        Modifier.width(52.px * textGrowth(style)),
                        color = when {
                            focused -> PlasmaColors.OnFocus2
                            playing -> PlasmaColors.Text
                            else -> PlasmaColors.Text3
                        },
                        align = TextAlign.End,
                    )
                }
                LogoPlate(channel.logo, channel.name, 150.px, 66.px, logoSize = LogoSize.Plate)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.px)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                        PlasmaText(
                            if (nameFirst) channel.name else channel.nowTitle ?: channel.name,
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
                    // Under it, with the progress: the program's times (board 07), or, under the
                    // channel's name, the program itself, as long as there's room for it.
                    val under = if (nameFirst) channel.nowTitle else channel.nowRange
                    if (under != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px)) {
                            PlasmaText(
                                under,
                                plasmaStyle(21f, 500, tabular = !nameFirst),
                                if (nameFirst) Modifier.weight(1f, fill = false) else Modifier,
                                color = when {
                                    nameFirst && focused -> PlasmaColors.OnFocus2
                                    nameFirst -> PlasmaColors.Text2
                                    focused -> PlasmaColors.OnFocus3
                                    else -> PlasmaColors.Text3
                                },
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
        if (moving) {
            // Where Up and Down can carry it, astride its edges by its number.
            if (canMoveUp) MoveArrow(PlasmaIcons.ChevronUp, stringResource(DesignR.string.move_up), Modifier.align(Alignment.TopStart).offset(18.px, (-20).px))
            if (canMoveDown) MoveArrow(PlasmaIcons.ChevronDown, stringResource(DesignR.string.move_down), Modifier.align(Alignment.BottomStart).offset(18.px, 20.px))
        }
    }
}

/** Rows step back to this while another channel moves (board 04's move mode). */
private const val DIMMED = 0.6f

/** Rows kept in view either side of a channel being moved. */
private const val MOVE_CONTEXT = 2

// ---- Preview -------------------------------------------------------------------------------------

/**
 * The picture of the channel [playing], and under it [preview], what's on and coming up on the
 * channel [detailsFor]: the focused one, or the one playing (Live TV Details).
 */
@Composable
private fun Preview(
    playing: LiveChannel?,
    detailsFor: String,
    preview: ChannelPreview?,
    onUpcoming: (String) -> Unit,
    upcomingFirst: FocusRequester,
    upcomingReturn: FocusRequester,
    returnTo: String?,
    modifier: Modifier,
) {
    // What's on and coming up fades in as another channel's takes its place (brief 2.6); the
    // picture above it doesn't.
    val detailsFade = rememberSwapFade(detailsFor)
    Column(modifier) {
        VideoSlot(
            playing?.frame,
            Modifier.size(628.px, 353.px),
            cornerRadius = 14.px,
            background = PlasmaColors.VideoWell,
        )
        if (preview == null) return@Column
        Details(preview, detailsFor, onUpcoming, upcomingFirst, upcomingReturn, returnTo, Modifier.weight(1f).swapFade(detailsFade))
    }
}

/** What's on now on [detailsFor], and its schedule, under the picture. */
@Composable
private fun Details(
    preview: ChannelPreview,
    detailsFor: String,
    onUpcoming: (String) -> Unit,
    upcomingFirst: FocusRequester,
    upcomingReturn: FocusRequester,
    returnTo: String?,
    modifier: Modifier,
) {
    Column(modifier) {
        val now = preview.now
        if (now != null) {
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
                PlasmaText(now.range, meta, color = PlasmaColors.Text2)
                PlasmaText("·", meta, color = PlasmaColors.Text4)
                PlasmaText(now.left, meta, color = PlasmaColors.Text2)
            }
            PlasmaText(
                now.title,
                plasmaStyle(36f, 600, trackingPx = -0.6f, lineHeight = 1.15f),
                Modifier.padding(top = 10.px),
                maxLines = 2,
            )
            ProgressBar(now.progress, Modifier.padding(top = 16.px).fillMaxWidth(), height = 4.px, track = PlasmaColors.white(0.14f), key = detailsFor to now.range)
            if (now.description != null) {
                PlasmaText(
                    now.description,
                    plasmaStyle(23f, 400, lineHeight = 1.5f),
                    Modifier.padding(top = 18.px),
                    color = PlasmaColors.Text2,
                    maxLines = 3,
                )
            }
        }
        if (preview.upcoming.isNotEmpty()) {
            // The schedule, under a rule when what's on now is above it. It runs as far as the
            // guide goes, scrolling as focus moves down it.
            Box(
                Modifier
                    .padding(top = if (now != null) 22.px else 26.px)
                    .fillMaxWidth()
                    .weight(1f)
                    .drawBehind { if (now != null) drawLine(PlasmaColors.Divider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.px.toPx()) },
            ) {
                key(detailsFor) {
                    val schedule = rememberLazyListState()
                    PlasmaColumn(
                        state = schedule,
                        modifier = Modifier.fillMaxSize(),
                        // 13 and 7 within the row put each line where board 07 has it.
                        contentPadding = PaddingValues(top = 13.px, bottom = 8.px),
                        pivot = null,
                        // The row with focus stays clear of the fade below it.
                        context = 56.px,
                    ) {
                        itemsIndexed(preview.upcoming, key = { _, program -> program.id }) { i, program ->
                            UpcomingRow(
                                program,
                                onClick = { onUpcoming(program.id) },
                                modifier = Modifier
                                    .then(if (i == 0) Modifier.focusRequester(upcomingFirst) else Modifier)
                                    .then(if (program.id == returnTo) Modifier.focusRequester(upcomingReturn) else Modifier),
                            )
                        }
                    }
                    // It fades out at the bottom while there's more below.
                    if (schedule.canScrollForward) {
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(48.px)
                                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to PlasmaColors.Bg)),
                        )
                    }
                }
            }
        }
    }
}

/** The schedule's time column, in board pixels of type: "22:00", or "10:00 PM". */
private const val TIME_24_PX = 66
private const val TIME_12_PX = 104

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
            Modifier.padding(horizontal = 14.px, vertical = 7.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.px),
        ) {
            val style = plasmaStyle(23f, 500, tabular = true)
            // "22:00", or "10:00 PM" with the 12-hour clock, so titles line up; it grows as its
            // text does with Large text (Android scales small text more than big).
            val timeWidth = (if (Formats.twelveHour) TIME_12_PX else TIME_24_PX).px * textGrowth(style)
            PlasmaText(program.time, style, Modifier.width(timeWidth), color = if (focused) PlasmaColors.OnFocus3 else PlasmaColors.Text3, maxLines = 1)
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
