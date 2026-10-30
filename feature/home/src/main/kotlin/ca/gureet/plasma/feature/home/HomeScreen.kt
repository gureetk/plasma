// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalAccent
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.SelectKeys
import ca.gureet.plasma.core.design.absolute
import ca.gureet.plasma.core.design.component.AppArtwork
import ca.gureet.plasma.core.design.component.GraphiteFill
import ca.gureet.plasma.core.design.component.ImageBadge
import ca.gureet.plasma.core.design.component.LiveMark
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.component.LogoPlate
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.MoveArrow
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.PlasmaRow
import ca.gureet.plasma.core.design.component.PosterTile
import ca.gureet.plasma.core.design.component.ProgramSheet
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.component.TopBar
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.design.component.brightness
import ca.gureet.plasma.core.design.component.carry
import ca.gureet.plasma.core.design.component.keepInView
import ca.gureet.plasma.core.design.component.rememberNow
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.core.model.HomeRow
import ca.gureet.plasma.feature.home.R
import ca.gureet.plasma.player.VideoFrame
import ca.gureet.plasma.player.VideoSlot
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import ca.gureet.plasma.core.design.R as DesignR

// ---- State ---------------------------------------------------------------------------------------

/** What's behind the hero. */
sealed interface HeroBackdrop {
    /** The live channel: the video layer (a still until the player arrives in M1). */
    data class Video(val frame: VideoFrame) : HeroBackdrop

    /** Artwork. A 2:3 poster is [blurred]: shown enlarged, blurred and darkened. */
    data class Art(val image: String?, val alignment: Alignment = Alignment.Center, val blurred: Boolean = false) : HeroBackdrop
}

/** The four kinds of hero item, in their fixed order (brief 4.1). */
enum class HeroKind { Live, ContinueWatching, NewMovie, Reminder }

/** One item in the Home hero carousel. The first is always the last channel, live. */
data class HeroItem(
    val id: String,
    val kind: HeroKind,
    val title: String,
    val backdrop: HeroBackdrop,
    val logo: String? = null,
    val logoName: String = "",
    /** Live: the channel number, never with a thousands separator. */
    val channelNumber: String? = null,
    /** Live sports: the score ("2–1") and match minute ("64′"), only when the guide provides them. */
    val score: String? = null,
    val minute: String? = null,
    /** The meta line: the programme subtitle, or "S2, E4 · Atlas Station". */
    val subtitle: String? = null,
    val progress: Float? = null,
    /** Live: when the programme ends ("22:00") and what's next. */
    val endsAt: String? = null,
    val next: String? = null,
    /** Other kinds: the line beside the progress bar ("18 min left", "Tonight 21:00 on Atlas One"). */
    val note: String? = null,
)

data class ChannelThumb(
    val channelId: String,
    val name: String,
    val logo: String?,
    /** A picture of the channel; a source's channels have none, and show their logo large instead. */
    val still: String?,
    val stillAlignment: Alignment,
    val progress: Float,
    /** For screen readers: "Wildframe 4K, Kingdoms of Ice". */
    val description: String,
    /** What's on now, under the logo of a channel without a [still]. */
    val nowTitle: String? = null,
)

enum class ContinueKind {
    /** 16:9 art, cropped to fill. */
    Art,

    /** Only a 2:3 poster: sharp on the left over a blurred enlargement (brief 4.2). */
    Poster,

    /** A catch-up programme: the picture with the channel logo on it. */
    CatchUp,
}

data class ContinueItem(
    val id: String,
    val title: String,
    val meta: String,
    val image: String?,
    val kind: ContinueKind,
    val progress: Float,
    val imageAlignment: Alignment = Alignment.Center,
    /** Zooms the art in around [zoomPivot], for art that needs a closer crop. */
    val imageZoom: Float = 1f,
    val zoomPivot: TransformOrigin = TransformOrigin.Center,
    val logo: String? = null,
    /** "JELLYFIN", or another app's name, for items from other apps (brief 5.19). */
    val sourceTag: String? = null,
    /**
     * Another app's item offers Hide All from This App; a Jellyfin server's is hidden one at a
     * time (5.19).
     */
    val offersHideAll: Boolean = true,
    /** OK plays it in Plasma, so its options say Resume; another app's item opens in its app. */
    val resumes: Boolean = sourceTag == null,
)

data class PosterItem(val id: String, val title: String, val poster: String?)

data class AppItem(val packageName: String, val label: String, val banner: String?, val icon: String? = null)

/**
 * A program in Upcoming Events (the owner's #80): one with a reminder or a recording set, one about
 * to start on a favorite, or sport on now or soon.
 */
data class UpcomingItem(
    /** "channelId@start in epoch seconds", as the Guide and Live TV give a program. */
    val id: String,
    val channelId: String,
    val channelName: String,
    val logo: String?,
    val title: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val reminder: Boolean = false,
    val recording: Boolean = false,
    /** It's on a favorite or it's sport, so it stays in the row without a reminder or a recording. */
    val fromGuide: Boolean = false,
) {
    /** Whether it's on at [at]. */
    fun isOn(at: ZonedDateTime): Boolean = !start.isAfter(at) && end.isAfter(at)
}

/** A recording in Recently Recorded (the owner's #80): its picture, or its channel's logo, and when it aired. */
data class RecordedItem(
    val id: String,
    val title: String,
    val channelName: String,
    val logo: String?,
    /** Its program's picture; without one, the channel's logo shows large. */
    val image: String?,
    val aired: ZonedDateTime,
    val length: Duration,
    val imageAlignment: Alignment = Alignment.Center,
    /** How far it's been watched, while it's started and not finished. */
    val progress: Float? = null,
    /** Never played: a dot before its title (brief 2.9). */
    val unwatched: Boolean = false,
    /** News: when it aired has the time too, as a news channel airs bulletins of one name all day. */
    val news: Boolean = false,
)

data class HomeUiState(
    val profileName: String,
    val profileInitial: String,
    val hero: List<HeroItem>,
    val favorites: List<ChannelThumb>,
    val continueWatching: List<ContinueItem>,
    val newMovies: List<PosterItem>,
    val apps: List<AppItem>,
    /** Settings › Launcher & Home › Show Android Apps: Off takes the Apps row away (brief 5.13). */
    val showApps: Boolean = true,
    /** Settings › Launcher & Home › Show TV Guide: Off takes the Guide off the dock, as off the top bar (the owner's #78). */
    val showGuide: Boolean = true,
    /** New in Series: series with new episodes, the latest first (the owner's #80). */
    val newSeries: List<PosterItem> = emptyList(),
    /** Upcoming Events, soonest first (the owner's #80). */
    val upcoming: List<UpcomingItem> = emptyList(),
    /** Recently Recorded: the newest finished recordings first (the owner's #80). */
    val recorded: List<RecordedItem> = emptyList(),
    /**
     * The rows under the dock, in Settings › Launcher & Home › Home Rows' order, those turned on
     * (the owner's #80). The first sits under the dock; a row with nothing to show is left out.
     */
    val rows: List<HomeRow> = HomeRow.defaults,
)

enum class DockTarget { LiveTv, Guide, Movies, Series, CatchUp, Recordings }

/** What Home asks the app to do. */
class HomeActions(
    val onTab: (TopTab) -> Unit = {},
    val onSettings: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val onDock: (DockTarget) -> Unit = {},
    val onHero: (HeroItem) -> Unit = {},
    val onChannel: (String) -> Unit = {},
    val onContinue: (String) -> Unit = {},
    val onMovie: (String) -> Unit = {},
    val onSeeAllMovies: () -> Unit = {},
    val onApp: (String) -> Unit = {},
    val onAllApps: () -> Unit = {},
    /** Remove from Favorites on a favorite's options (4.1). */
    val onRemoveFavorite: (channelId: String) -> Unit = {},
    /** Move: the favorite now comes after [after], or first when that's null (4.1). */
    val onMoveFavorite: (channelId: String, after: String?) -> Unit = { _, _ -> },
    /** Remove from Continue Watching on one of Plasma's own items, kept by the app (4.1). */
    val onRemoveContinue: (id: String) -> Unit = {},
    /** Hide from Plasma on another app's item, or Hide All from This App when [wholeApp] (5.19). */
    val onHideOther: (id: String, wholeApp: Boolean) -> Unit = { _, _ -> },
    /** A movie in New in Movies shows without a poster: the app may find its own (the owner's #81). */
    val onPosterless: (movieId: String) -> Unit = {},
    /** A series in New in Series opens its page, and See All opens Series (the owner's #80). */
    val onSeries: (String) -> Unit = {},
    val onSeeAllSeries: () -> Unit = {},
    /** OK on a program in Upcoming Events that's on now: its channel, full screen (the owner's #80). */
    val onUpcoming: (channelId: String) -> Unit = {},
    /** Remind Me and Record on a program in Upcoming Events, or their undoing, by its [programId]. */
    val onRemind: (channelId: String, programId: String, on: Boolean) -> Unit = { _, _, _ -> },
    val onRecord: (channelId: String, programId: String, on: Boolean) -> Unit = { _, _, _ -> },
    /** OK on a recording in Recently Recorded plays it, and See All opens Recordings (the owner's #80). */
    val onRecording: (RecordedItem) -> Unit = {},
    val onSeeAllRecordings: () -> Unit = {},
)

/**
 * Where focus starts when Home opens: the dock; the first row under it (Favorite Channels, unless
 * Home Rows puts another first); the rows below it (board 02); or nowhere.
 */
enum class HomeFocus { Dock, FirstRow, Rows, None }

/** Menu or long-press OK on a favorite, a Continue Watching item or a program in Upcoming Events: its options. */
private sealed interface HomeSheet {
    data class Favorite(val channelId: String) : HomeSheet
    data class Continue(val id: String) : HomeSheet
    data class Upcoming(val id: String) : HomeSheet
}

/** A program's bell and red dot as set on Home, shown until the app's own catch up. */
private data class Marks(val reminder: Boolean, val recording: Boolean)

/** Where focus goes when the items in [gone] leave a row from [id]: the next one that stays, or else the nearest before. */
private fun List<String>.nextAfter(id: String, gone: Set<String>): String? {
    val i = indexOf(id)
    return (drop(i + 1) + take(i.coerceAtLeast(0)).reversed()).firstOrNull { it !in gone }
}

// ---- Screen --------------------------------------------------------------------------------------

/**
 * Home scrolls between resting positions instead of freely: the top (board 01); a little further
 * down so the first row fits, with focus on it; and the rows below it (board 02), where the row
 * with focus takes the second place on the screen with the row before it on top, and the rows
 * above those fade out.
 */
private enum class HomeSection { Top, First, Rows }

/** The first row sits under the dock, where Favorite Channels does on board 01. */
private const val FIRST_ROW_PX = 880

/** The second row starts here at least: 1186 − 1040 puts it at y 146, as Continue Watching on board 02. */
private const val ROWS_TOP_PX = 1186

/** Where the rows' top one sits on the screen (board 02). */
private const val ROWS_SCREEN_PX = 146

/** How far Home scrolls for Favorite Channels to fit, and at least for any first row. */
private const val FIRST_SCROLL_PX = 120

/** What's left under the first row once it fits, as under Favorite Channels. */
private const val BELOW_FIRST_PX = 65
private const val SCREEN_PX = 1080

/** Scrolled further than this for a tall first row, the hero would run under the top bar: it fades. */
private const val HERO_ROOM_PX = 150

/**
 * A row's height on the page and the step to the next one's top, in board pixels: Continue
 * Watching's and New in Movies' as on board 02; the others the same way.
 */
private class RowSize(val height: Int, val step: Int)

private fun rowSize(row: HomeRow, continueEmpty: Boolean): RowSize = when (row) {
    HomeRow.Favorites -> RowSize(255, 284)
    // Nothing started yet: its heading and a line on what shows there.
    HomeRow.ContinueWatching -> if (continueEmpty) RowSize(89, 124) else RowSize(359, 394)
    HomeRow.NewMovies, HomeRow.NewSeries -> RowSize(311, 338)
    HomeRow.UpcomingEvents -> RowSize(157, 190)
    HomeRow.RecentlyRecorded -> RowSize(359, 394)
    HomeRow.Apps -> RowSize(208, 236)
}

/**
 * Home (boards 01 and 02, brief 4.1–4.2): the last channel's live video behind a hero, the dock,
 * then the rows in Settings › Launcher & Home › Home Rows' order (the owner's #80): the first under
 * the dock, where Favorite Channels sits on board 01, the others further down, as board 02 draws
 * Continue Watching, New in Movies and Apps. The top bar stays pinned. [heroAutoAdvance] is off in
 * screenshot tests.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    initialFocus: HomeFocus = HomeFocus.Dock,
    heroAutoAdvance: Boolean = true,
) {
    // The row with focus; null while focus is on the hero or the dock.
    var focusedRow by remember { mutableStateOf<HomeRow?>(null) }
    var heroIndex by remember { mutableIntStateOf(0) }
    var heroFocused by remember { mutableStateOf(false) }
    var backdrop by remember { mutableStateOf(state.continueWatching.firstOrNull()?.image) }
    var sheet by remember { mutableStateOf<HomeSheet?>(null) }
    // Removed here at once, and kept out until the app's own favorites have caught up.
    var removedFavorites by remember { mutableStateOf(emptySet<String>()) }
    var hiddenContinue by remember { mutableStateOf(emptySet<String>()) }
    // Upcoming Events' programs taken out here, and their marks changed here, until the app's own catch up.
    var droppedUpcoming by remember { mutableStateOf(emptySet<String>()) }
    var upcomingMarks by remember { mutableStateOf(emptyMap<String, Marks>()) }
    // The item a sheet closes back to: the one it was for, or its neighbor once that has gone.
    var returnTo by remember { mutableStateOf<String?>(null) }
    val sheetReturn = remember { FocusRequester() }
    // Move (4.1): the favorite being carried, the order it's carried through, and the row's order
    // when it lifted.
    var moving by remember { mutableStateOf<String?>(null) }
    var moveOrder by remember { mutableStateOf<List<String>?>(null) }
    var moveBase by remember { mutableStateOf<List<String>?>(null) }
    val favoritesRow = rememberLazyListState()

    LaunchedEffect(state.favorites) {
        removedFavorites = removedFavorites.filterTo(mutableSetOf()) { id -> state.favorites.any { it.channelId == id } }
    }
    val kept = state.favorites.filter { it.channelId !in removedFavorites }
    // While a favorite moves, and once it's dropped until the app's own order changes, the row
    // goes in the moved order.
    val stateOrder = kept.map { it.channelId }
    LaunchedEffect(stateOrder) {
        if (moving == null && moveBase != null && stateOrder != moveBase) {
            moveOrder = null
            moveBase = null
        }
    }
    val favorites = moveOrder?.takeIf { moving != null || stateOrder == moveBase }?.let { order ->
        val byId = kept.associateBy { it.channelId }
        order.mapNotNull(byId::get)
    } ?: kept
    val continueWatching = state.continueWatching.filter { it.id !in hiddenContinue }
    LaunchedEffect(state.upcoming) {
        droppedUpcoming = droppedUpcoming.filterTo(mutableSetOf()) { id -> state.upcoming.any { it.id == id } }
        upcomingMarks = upcomingMarks.filter { (id, marks) -> state.upcoming.any { it.id == id && (it.reminder != marks.reminder || it.recording != marks.recording) } }
    }
    val upcoming = state.upcoming.filter { it.id !in droppedUpcoming }.map { item ->
        upcomingMarks[item.id]?.let { item.copy(reminder = it.reminder, recording = it.recording) } ?: item
    }

    /** Move on a favorite's options: it lifts, and Left and Right carry it along the row. */
    fun startMove(channelId: String) {
        moveOrder = stateOrder
        moveBase = stateOrder
        moving = channelId
    }

    fun moveBy(delta: Int) {
        val order = moveOrder ?: return
        favoritesRow.carry(order, order.indexOf(moving ?: return), delta)?.let { moveOrder = it }
    }

    /** OK: the favorite stays where it is, and the app keeps the order. */
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

    /** Back: the favorite goes back where it was, and the row keeps it in view there. */
    fun cancelMove() {
        val id = moving ?: return
        favoritesRow.keepInView(stateOrder.indexOf(id), stateOrder.size)
        moving = null
        moveOrder = null
        moveBase = null
    }

    /**
     * Remind Me or Record on a program in Upcoming Events, or their undoing: the bell and the red
     * dot follow at once. A program nothing keeps in the row any more goes, and focus moves on.
     */
    fun mark(item: UpcomingItem, reminder: Boolean, recording: Boolean) {
        if (reminder != item.reminder) actions.onRemind(item.channelId, item.id, reminder)
        if (recording != item.recording) actions.onRecord(item.channelId, item.id, recording)
        if (!reminder && !recording && !item.fromGuide) {
            returnTo = upcoming.map { it.id }.nextAfter(item.id, setOf(item.id))
            droppedUpcoming = droppedUpcoming + item.id
        } else {
            upcomingMarks = upcomingMarks + (item.id to Marks(reminder, recording))
        }
    }

    val homeTab = remember { FocusRequester() }
    val hero = remember { FocusRequester() }
    val liveTvTile = remember { FocusRequester() }
    // Each row's first item, where focus lands the first time it comes into the row.
    val firstOf = remember { HomeRow.entries.associateWith { FocusRequester() } }

    // The rows that show, in their order: each with something to show. Continue Watching stays to
    // say what goes there; Apps keeps All Apps.
    val rows = state.rows.distinct().filter { row ->
        when (row) {
            HomeRow.Favorites -> favorites.isNotEmpty()
            HomeRow.ContinueWatching -> true
            HomeRow.NewMovies -> state.newMovies.isNotEmpty()
            HomeRow.NewSeries -> state.newSeries.isNotEmpty()
            HomeRow.UpcomingEvents -> upcoming.isNotEmpty()
            HomeRow.RecentlyRecorded -> state.recorded.isNotEmpty()
            HomeRow.Apps -> state.showApps
        }
    }
    val sizes = rows.map { rowSize(it, continueEmpty = continueWatching.isEmpty()) }
    // Each row's top from the first's: the second row starts at y 1186, or under a taller first.
    val steps = sizes.runningFold(0) { at, size -> at + size.step }
    val rowsTop = maxOf(ROWS_TOP_PX, FIRST_ROW_PX + (sizes.firstOrNull()?.step ?: 0))

    /** Where row [i] (the second or later) starts on the page. */
    fun rowTop(i: Int): Int = rowsTop + steps[i] - steps[1]

    val rowIndex = focusedRow?.let(rows::indexOf)?.takeIf { it >= 0 }
    val section = when (rowIndex) {
        null -> HomeSection.Top
        0 -> HomeSection.First
        else -> HomeSection.Rows
    }
    // The first row in full: as far as Favorite Channels takes Home, or further for a taller row.
    val firstScroll = maxOf(FIRST_SCROLL_PX, FIRST_ROW_PX + (sizes.firstOrNull()?.height ?: 0) + BELOW_FIRST_PX - SCREEN_PX)
    // Below it, the row on top: the second row, or the one before the row with focus, so the row
    // with focus takes the second place (as Apps did on the owner's 28 October build).
    val topRow = ((rowIndex ?: 1) - 1).coerceAtLeast(1)
    val target = when (section) {
        HomeSection.Top -> 0
        HomeSection.First -> firstScroll
        HomeSection.Rows -> rowTop(topRow) - ROWS_SCREEN_PX
    }
    val scroll by animateDpAsState(target.px, PlasmaMotion.screen(), label = "homeScroll")
    val topAlpha by animateFloatAsState(if (section == HomeSection.Rows) 0f else 1f, PlasmaMotion.screen(), label = "homeTop")
    val heroAlpha by animateFloatAsState(if (section == HomeSection.First && firstScroll > HERO_ROOM_PX) 0f else 1f, PlasmaMotion.screen(), label = "heroAlpha")

    LaunchedEffect(initialFocus) {
        // Rows are lazy: their tiles join the layout in the first frame, after this effect starts.
        withFrameNanos { }
        // A row's first item: Continue Watching has none until something's started.
        val withItems = rows.filter { it != HomeRow.ContinueWatching || continueWatching.isNotEmpty() }
        when (initialFocus) {
            HomeFocus.Dock -> liveTvTile.requestFocus()
            HomeFocus.FirstRow -> withItems.firstOrNull()?.let { firstOf.getValue(it).requestFocus() }
            HomeFocus.Rows -> withItems.firstOrNull { it != rows.firstOrNull() }?.let { firstOf.getValue(it).requestFocus() }
            HomeFocus.None -> Unit
        }
    }

    // The row with focus has gone, its last item with it: focus goes back to the Live TV tile.
    LaunchedEffect(focusedRow != null && rowIndex == null) {
        if (focusedRow != null && rowIndex == null && sheet == null) {
            focusedRow = null
            runCatching { liveTvTile.requestFocus() }
        }
    }

    // The hero moves on every 10 s while it's on screen and doesn't have focus.
    LaunchedEffect(heroAutoAdvance, heroFocused, section, state.hero.size) {
        if (!heroAutoAdvance || heroFocused || section != HomeSection.Top || state.hero.size < 2) return@LaunchedEffect
        while (true) {
            delay(PlasmaMotion.HERO_ADVANCE_MS)
            heroIndex = (heroIndex + 1) % state.hero.size
        }
    }

    // Back anywhere below the dock returns to the top, on the Live TV tile.
    BackHandler(enabled = section != HomeSection.Top) {
        liveTvTile.requestFocus()
    }

    val item = state.hero.getOrNull(heroIndex)
    val rowContext = RowsContext(
        state = state,
        actions = actions,
        favorites = favorites,
        continueWatching = continueWatching,
        upcoming = upcoming,
        firstOf = firstOf,
        returnTo = returnTo,
        sheetReturn = sheetReturn,
        favoritesRow = favoritesRow,
        moving = moving,
        onBackdrop = { backdrop = it },
        onFavoriteOptions = { id ->
            returnTo = id
            sheet = HomeSheet.Favorite(id)
        },
        onContinueOptions = { id ->
            returnTo = id
            sheet = HomeSheet.Continue(id)
        },
        // OK on a program on now plays its channel; on one later, Remind Me and Record, as in the Guide.
        onUpcoming = { program, live ->
            if (live) {
                actions.onUpcoming(program.channelId)
            } else {
                returnTo = program.id
                sheet = HomeSheet.Upcoming(program.id)
            }
        },
        onUpcomingOptions = { program ->
            returnTo = program.id
            sheet = HomeSheet.Upcoming(program.id)
        },
    )

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // While a favorite moves, Left and Right carry it, OK drops it and Back puts it
                // back; nothing else acts until it's dropped (board 04's move mode).
                if (moving == null) return@onPreviewKeyEvent false
                val down = event.type == KeyEventType.KeyDown
                val released = event.type == KeyEventType.KeyUp
                when {
                    event.key == Key.DirectionLeft -> if (down) moveBy(-1)
                    event.key == Key.DirectionRight -> if (down) moveBy(+1)
                    event.key in SelectKeys -> if (released) drop()
                    event.key == Key.Back || event.key == Key.Escape -> if (released) cancelMove()
                }
                true
            },
    ) {
        // Board 02: a blurred, darkened enlargement of the focused item's art behind the rows.
        if (topAlpha < 1f) {
            Box(Modifier.fillMaxSize().alpha(1f - topAlpha)) {
                Crossfade(backdrop, animationSpec = tween(400), label = "rowsBackdrop") { image ->
                    PlasmaImage(
                        image,
                        Modifier
                            .absolute((-120).px, (-420).px, 2160.px, 1215.px)
                            .blur(CssBlur60)
                            .alpha(0.45f),
                        placeholder = Color.Transparent,
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to PlasmaColors.bg(0.5f),
                            300f / 1080f to PlasmaColors.bg(0.85f),
                            520f / 1080f to PlasmaColors.Bg,
                        ),
                    ),
                )
            }
        }

        // Everything below the top bar scrolls together.
        Box(Modifier.fillMaxSize().offset { IntOffset(0, -scroll.roundToPx()) }) {
            Box(Modifier.alpha(topAlpha)) {
                if (item != null) HeroBackdropLayer(item.backdrop, topAlpha)
                // Left-to-right behind the hero text, and the fade into the dock (board 01).
                Box(
                    Modifier.size(1920.px, 1080.px).background(
                        Brush.horizontalGradient(
                            0f to PlasmaColors.bg(0.95f),
                            0.24f to PlasmaColors.bg(0.82f),
                            0.46f to PlasmaColors.bg(0.38f),
                            0.64f to Color.Transparent,
                        ),
                    ),
                )
                Box(
                    Modifier.size(1920.px, 1080.px).background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            410f / 1080f to Color.Transparent,
                            600f / 1080f to PlasmaColors.bg(0.9f),
                            700f / 1080f to PlasmaColors.Bg,
                        ),
                    ),
                )
                // A first row taller than Favorite Channels takes Home up past the hero's room
                // under the top bar: the hero steps aside while it has focus.
                Box(
                    if (heroAlpha < 1f) {
                        Modifier.graphicsLayer {
                            alpha = heroAlpha
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                    } else {
                        Modifier
                    },
                ) {
                    if (item != null) {
                        Hero(
                            item = item,
                            count = state.hero.size,
                            index = heroIndex,
                            focused = heroFocused,
                            onIndex = { heroIndex = it },
                            onFocused = {
                                heroFocused = it
                                if (it) focusedRow = null
                            },
                            onClick = { actions.onHero(item) },
                            modifier = Modifier
                                .focusRequester(hero)
                                .focusProperties {
                                    up = homeTab
                                    down = liveTvTile
                                },
                        )
                    }
                }
                Dock(
                    onDock = actions.onDock,
                    showGuide = state.showGuide,
                    firstTile = liveTvTile,
                    up = hero,
                    modifier = Modifier
                        .offset(y = 640.px)
                        .onFocusChanged { if (it.hasFocus) focusedRow = null },
                )
                rows.firstOrNull()?.let { first ->
                    key(first) {
                        HomeRowView(
                            first,
                            rowContext,
                            Modifier
                                .offset(y = FIRST_ROW_PX.px)
                                .onFocusChanged { if (it.hasFocus) focusedRow = first },
                        )
                    }
                }
            }
            // The rows below the first, the ones above the top one faded out.
            rows.forEachIndexed { i, row ->
                if (i > 0) {
                    key(row) {
                        val alpha by animateFloatAsState(if (section == HomeSection.Rows && i < topRow) 0f else 1f, PlasmaMotion.screen(), label = "rowAlpha")
                        HomeRowView(
                            row,
                            rowContext,
                            Modifier
                                .offset(y = rowTop(i).px)
                                .then(if (alpha < 1f) Modifier.alpha(alpha) else Modifier)
                                .onFocusChanged { if (it.hasFocus) focusedRow = row },
                        )
                    }
                }
            }
        }

        // The dark band behind the top bar stays put while the page scrolls.
        Box(
            Modifier.fillMaxWidth().height(270.px).alpha(topAlpha).background(
                Brush.verticalGradient(
                    0f to PlasmaColors.bg(0.94f),
                    120f / 270f to PlasmaColors.bg(0.6f),
                    1f to Color.Transparent,
                ),
            ),
        )
        TopBar(
            current = TopTab.Home,
            profileInitial = state.profileInitial,
            profileName = state.profileName,
            onTab = actions.onTab,
            onSettings = actions.onSettings,
            onProfile = actions.onProfile,
            currentTabFocusRequester = homeTab,
            focusDown = hero,
        )

        // A sheet closes back on its item, or on the Live TV tile when the item's row has emptied.
        val back = if (returnTo != null) sheetReturn else liveTvTile
        val favorite = (sheet as? HomeSheet.Favorite)?.let { s -> favorites.firstOrNull { it.channelId == s.channelId } }
        SheetHost(visible = favorite != null, returnFocus = back) {
            if (favorite != null) {
                OptionsSheet(
                    title = favorite.name,
                    onDismiss = { sheet = null },
                    leading = { LogoPlate(favorite.logo, favorite.name, 150.px, 66.px, logoSize = LogoSize.Plate) },
                    options = listOfNotNull(
                        SheetOption(
                            stringResource(R.string.remove_favorite),
                            onClick = {
                                returnTo = favorites.map { it.channelId }.nextAfter(favorite.channelId, setOf(favorite.channelId))
                                removedFavorites = removedFavorites + favorite.channelId
                                actions.onRemoveFavorite(favorite.channelId)
                            },
                            icon = PlasmaIcons.Star,
                        ),
                        // With another favorite to move past.
                        if (favorites.size > 1) SheetOption(stringResource(R.string.move), onClick = { startMove(favorite.channelId) }, icon = PlasmaIcons.Move) else null,
                    ),
                )
            }
        }

        val continuing = (sheet as? HomeSheet.Continue)?.let { s -> continueWatching.firstOrNull { it.id == s.id } }
        SheetHost(visible = continuing != null, returnFocus = back) {
            if (continuing != null) {
                fun hide(ids: Set<String>) {
                    returnTo = continueWatching.map { it.id }.nextAfter(continuing.id, ids)
                    hiddenContinue = hiddenContinue + ids
                }
                OptionsSheet(
                    title = continuing.title,
                    subtitle = continuing.meta,
                    onDismiss = { sheet = null },
                    options = if (continuing.sourceTag == null) {
                        listOf(
                            SheetOption(stringResource(R.string.resume), onClick = { actions.onContinue(continuing.id) }, icon = PlasmaIcons.Play),
                            SheetOption(
                                stringResource(R.string.remove_from_continue_watching),
                                onClick = {
                                    hide(setOf(continuing.id))
                                    actions.onRemoveContinue(continuing.id)
                                },
                                icon = PlasmaIcons.Hide,
                            ),
                        )
                    } else {
                        // Another app's item can only be hidden here (brief 5.19).
                        val fromApp = continueWatching.filter { it.sourceTag == continuing.sourceTag }.map { it.id }.toSet()
                        listOfNotNull(
                            SheetOption(stringResource(if (continuing.resumes) R.string.resume else R.string.open), onClick = { actions.onContinue(continuing.id) }, icon = PlasmaIcons.Play),
                            SheetOption(
                                stringResource(R.string.hide_from_plasma),
                                onClick = {
                                    hide(setOf(continuing.id))
                                    actions.onHideOther(continuing.id, false)
                                },
                                icon = PlasmaIcons.Hide,
                            ),
                            SheetOption(
                                stringResource(R.string.hide_all_from_app),
                                onClick = {
                                    hide(fromApp)
                                    actions.onHideOther(continuing.id, true)
                                },
                                icon = PlasmaIcons.Hide,
                            ).takeIf { continuing.offersHideAll },
                        )
                    },
                )
            }
        }

        // Remind Me and Record on a program in Upcoming Events (brief 4.7's sheet); one on now offers Record alone.
        val program = (sheet as? HomeSheet.Upcoming)?.let { s -> upcoming.firstOrNull { it.id == s.id } }
        SheetHost(visible = program != null, returnFocus = back) {
            if (program != null) {
                val now by rememberNow()
                ProgramSheet(
                    title = program.title,
                    subtitle = listOf(program.channelName, upcomingWhen(program, now)).joinToString(" · "),
                    reminder = program.reminder,
                    recording = program.recording,
                    onDismiss = { sheet = null },
                    onReminder = { mark(program, !program.reminder, program.recording) },
                    onRecording = { mark(program, program.reminder, !program.recording) },
                    canRemind = !program.isOn(now),
                )
            }
        }
    }
}

/** CSS `blur(60px)` as a Compose blur radius (Android blurs with sigma ≈ 0.58 × radius). */
private val CssBlur60 = 104.px

/** CSS `blur(28px)`. */
private val CssBlur28 = 48.px

// ---- Hero ----------------------------------------------------------------------------------------

/** The video (or art) behind the hero: full width, nudged up so the action shows behind the hero. */
@Composable
private fun HeroBackdropLayer(backdrop: HeroBackdrop, alpha: Float) {
    val area = Modifier.offset(y = (-330).px).size(1920.px, 1080.px)
    when (backdrop) {
        is HeroBackdrop.Video -> VideoSlot(backdrop.frame, area, alpha = alpha)
        is HeroBackdrop.Art -> Crossfade(backdrop, area, label = "heroArt") { art ->
            if (art.blurred) {
                PlasmaImage(
                    art.image,
                    Modifier.fillMaxSize().blur(CssBlur60),
                    alignment = art.alignment,
                    colorFilter = brightness(0.6f),
                )
            } else {
                PlasmaImage(art.image, Modifier.fillMaxSize(), alignment = art.alignment)
            }
        }
    }
}

@Composable
private fun Hero(
    item: HeroItem,
    count: Int,
    index: Int,
    focused: Boolean,
    onIndex: (Int) -> Unit,
    onFocused: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier
            .offset(96.px, 300.px)
            .width(940.px)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || count < 2) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> { onIndex((index + 1) % count); true }
                    Key.DirectionLeft -> { onIndex((index - 1 + count) % count); true }
                    else -> false
                }
            },
        style = FocusStyle.Plain,
        shape = RoundedCornerShape(0.dp),
        onFocusChanged = onFocused,
    ) {
        // Focus lifts the hero slightly; the dots brighten (review notes, 01 Home).
        val lift by animateDpAsState(if (LocalFocused.current) (-6).px else 0.dp, PlasmaMotion.focus(), label = "heroLift")
        AnimatedContent(
            targetState = item,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            contentKey = { it.id },
            label = "heroItem",
        ) { shown ->
            HeroText(shown, Modifier.offset { IntOffset(0, lift.roundToPx()) })
        }
    }
    HeroDots(count, index, focused, Modifier.offset(1724.px, 560.px))
}

@Composable
private fun HeroText(item: HeroItem, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.px)) {
            if (item.logo != null) {
                LogoImage(item.logo, item.logoName, LogoSize.Hero)
                Box(Modifier.size(1.px, 30.px).background(PlasmaColors.white(0.24f)))
            }
            val eyebrow = plasmaStyle(20f, 600, trackingPx = 1.6f)
            when (item.kind) {
                HeroKind.Live -> LiveMark(stringResource(DesignR.string.live))
                HeroKind.ContinueWatching -> PlasmaText(stringResource(R.string.hero_continue_watching), eyebrow)
                HeroKind.NewMovie -> PlasmaText(stringResource(R.string.hero_new_movie), eyebrow)
                HeroKind.Reminder -> PlasmaText(stringResource(R.string.hero_reminder), eyebrow)
            }
            if (item.channelNumber != null) {
                PlasmaText(
                    stringResource(R.string.channel_number, item.channelNumber),
                    plasmaStyle(22f, 500, tabular = true),
                    color = PlasmaColors.Text2,
                )
            }
        }
        PlasmaText(
            item.title,
            PlasmaType.Display,
            Modifier.padding(top = 20.px),
            color = Color.White,
            maxLines = 2,
        )
        Row(
            Modifier.padding(top = 14.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.px),
        ) {
            val meta = plasmaStyle(26f, 400)
            if (item.score != null) PlasmaText(item.score, plasmaStyle(26f, 600, tabular = true), color = PlasmaColors.Text)
            if (item.minute != null) PlasmaText(item.minute, plasmaStyle(26f, 400, tabular = true), color = PlasmaColors.TextSoft)
            if ((item.score != null || item.minute != null) && item.subtitle != null) PlasmaText("·", meta, color = PlasmaColors.Text4)
            if (item.subtitle != null) PlasmaText(item.subtitle, meta, color = PlasmaColors.TextSoft)
        }
        Row(
            Modifier.padding(top = 24.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.px),
        ) {
            if (item.progress != null) {
                ProgressBar(item.progress, Modifier.width(280.px), height = 4.px, track = PlasmaColors.white(0.22f))
            }
            val caption = plasmaStyle(22f, 500, tabular = true)
            val parts = buildList {
                if (item.endsAt != null) add(stringResource(R.string.ends_at, item.endsAt))
                if (item.next != null) add(stringResource(R.string.next_title, item.next))
                if (item.note != null) add(item.note)
            }
            parts.forEachIndexed { i, part ->
                if (i > 0) PlasmaText("·", caption, color = PlasmaColors.Text4)
                PlasmaText(part, caption, color = PlasmaColors.Text2)
            }
        }
    }
}

/** Carousel dots, right-aligned at about y 563: the current item is a 22 px pill. */
@Composable
private fun HeroDots(count: Int, index: Int, focused: Boolean, modifier: Modifier) {
    if (count < 2) return
    Row(
        modifier.width(100.px),
        horizontalArrangement = Arrangement.spacedBy(9.px, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val idle = PlasmaColors.white(if (focused) 0.6f else 0.35f)
        repeat(count) { i ->
            val active = i == index
            val width by animateDpAsState(if (active) 22.px else 7.px, PlasmaMotion.focus(), label = "dot")
            Box(Modifier.size(width, 7.px).background(if (active) PlasmaColors.Text else idle, CircleShape))
        }
    }
}

// ---- Dock ----------------------------------------------------------------------------------------

private data class DockEntry(val target: DockTarget, val icon: PlasmaIcons, val label: Int)

private val DockEntries = listOf(
    DockEntry(DockTarget.LiveTv, PlasmaIcons.LiveTv, R.string.dock_live_tv),
    DockEntry(DockTarget.Guide, PlasmaIcons.Guide, R.string.dock_guide),
    DockEntry(DockTarget.Movies, PlasmaIcons.Movies, R.string.dock_movies),
    DockEntry(DockTarget.Series, PlasmaIcons.Series, R.string.dock_series),
    DockEntry(DockTarget.CatchUp, PlasmaIcons.History, R.string.dock_catch_up),
    DockEntry(DockTarget.Recordings, PlasmaIcons.Record, R.string.dock_recordings),
)

/**
 * Six graphite tiles at y 640, gap 29 (board 01). Up always goes to the hero. The dock doesn't
 * remember its last tile: the hero, Back and opening Home all land on Live TV (brief 3.5, 4.1).
 * With Show TV Guide off, TV Guide leaves it, and the other five share its width.
 */
@Composable
private fun Dock(onDock: (DockTarget) -> Unit, showGuide: Boolean, firstTile: FocusRequester, up: FocusRequester, modifier: Modifier) {
    Row(
        modifier.padding(start = 96.px).width(1728.px).focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(29.px),
    ) {
        DockEntries.filter { it.target != DockTarget.Guide || showGuide }.forEachIndexed { i, entry ->
            DockTile(
                icon = entry.icon,
                label = stringResource(entry.label),
                onClick = { onDock(entry.target) },
                modifier = Modifier.weight(1f),
                tileModifier = (if (i == 0) Modifier.focusRequester(firstTile) else Modifier)
                    .focusProperties { this.up = up },
            )
        }
    }
}

@Composable
private fun DockTile(icon: PlasmaIcons, label: String, onClick: () -> Unit, modifier: Modifier, tileModifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PlasmaSurface(
            onClick = onClick,
            // The label sits under the tile, so the tile carries it for TalkBack.
            modifier = tileModifier.fillMaxWidth().height(158.px).semantics { contentDescription = label },
            style = FocusStyle.Tile,
            shape = PlasmaShapes.DockTile,
            onFocusChanged = { focused = it },
            contentAlignment = Alignment.Center,
        ) {
            GraphiteFill()
            val size by animateDpAsState(if (focused) 56.px else 52.px, PlasmaMotion.focus(), label = "dockIcon")
            PlasmaIcon(icon, size, tint = if (focused) Color.White else PlasmaColors.TextSoft2)
        }
        val shift by animateDpAsState(if (focused) 8.px else 0.dp, PlasmaMotion.focus(), label = "dockLabel")
        PlasmaText(
            label,
            plasmaStyle(24f, if (focused) 600 else 500),
            Modifier.padding(top = 18.px).offset { IntOffset(0, shift.roundToPx()) },
            color = if (focused) PlasmaColors.Text else PlasmaColors.Text2,
        )
    }
}

// ---- The rows ------------------------------------------------------------------------------------

/** What Home's rows draw from, and what they tell Home. */
private class RowsContext(
    val state: HomeUiState,
    val actions: HomeActions,
    val favorites: List<ChannelThumb>,
    val continueWatching: List<ContinueItem>,
    val upcoming: List<UpcomingItem>,
    val firstOf: Map<HomeRow, FocusRequester>,
    val returnTo: String?,
    val sheetReturn: FocusRequester,
    val favoritesRow: LazyListState,
    /** The favorite being moved, if one is. */
    val moving: String?,
    val onBackdrop: (String?) -> Unit,
    val onFavoriteOptions: (String) -> Unit,
    val onContinueOptions: (String) -> Unit,
    val onUpcoming: (UpcomingItem, live: Boolean) -> Unit,
    val onUpcomingOptions: (UpcomingItem) -> Unit,
)

/** One of Home's rows, wherever it sits. */
@Composable
private fun HomeRowView(row: HomeRow, rows: RowsContext, modifier: Modifier) {
    val first = rows.firstOf.getValue(row)
    val actions = rows.actions
    when (row) {
        HomeRow.Favorites -> FavoriteChannels(
            rows.favorites,
            first = first,
            onChannel = actions.onChannel,
            onOptions = rows.onFavoriteOptions,
            returnTo = rows.returnTo,
            sheetReturn = rows.sheetReturn,
            state = rows.favoritesRow,
            moving = rows.moving,
            modifier = modifier,
        )
        HomeRow.ContinueWatching -> ContinueRow(rows.continueWatching, first, actions.onContinue, rows.onContinueOptions, rows.onBackdrop, rows.returnTo, rows.sheetReturn, modifier)
        // Without a poster, a movie's name on the plate, as on Movies (the owner's #77), while the app looks for its own (#81).
        HomeRow.NewMovies -> PosterRow(
            stringResource(R.string.new_in_movies), rows.state.newMovies, first, actions.onMovie, actions.onSeeAllMovies, rows.onBackdrop, actions.onPosterless, modifier,
        )
        HomeRow.NewSeries -> PosterRow(
            stringResource(R.string.new_in_series), rows.state.newSeries, first, actions.onSeries, actions.onSeeAllSeries, rows.onBackdrop, onNoImage = null, modifier = modifier,
        )
        HomeRow.UpcomingEvents -> UpcomingRow(rows.upcoming, first, rows.onUpcoming, rows.onUpcomingOptions, rows.returnTo, rows.sheetReturn, modifier)
        HomeRow.RecentlyRecorded -> RecordedRow(rows.state.recorded, first, actions.onRecording, actions.onSeeAllRecordings, rows.onBackdrop, modifier)
        HomeRow.Apps -> AppsRow(rows.state.apps, first, actions, rows.onBackdrop, modifier)
    }
}

// ---- Favorite Channels ---------------------------------------------------------------------------

@Composable
private fun FavoriteChannels(
    items: List<ChannelThumb>,
    first: FocusRequester,
    onChannel: (String) -> Unit,
    onOptions: (String) -> Unit,
    returnTo: String?,
    sheetReturn: FocusRequester,
    state: LazyListState,
    /** The favorite being moved, if one is. */
    moving: String?,
    modifier: Modifier,
) {
    Column(modifier) {
        val carried = moving?.let { id -> items.firstOrNull { it.channelId == id } }
        if (carried == null) {
            PlasmaText(stringResource(R.string.favorite_channels), PlasmaType.Section, Modifier.padding(start = 96.px))
        } else {
            // "Moving Arena Sports 1 · Press OK to drop it here" takes the heading's place: the
            // row sits too low for it to go below, as it does on Apps.
            Row(Modifier.padding(start = 96.px), horizontalArrangement = Arrangement.spacedBy(14.px)) {
                PlasmaText(stringResource(DesignR.string.moving, carried.name), PlasmaType.Section, Modifier.alignBy(FirstBaseline))
                PlasmaText(stringResource(DesignR.string.moving_hint), plasmaStyle(22f, 500), Modifier.alignBy(FirstBaseline), color = PlasmaColors.Text2)
            }
        }
        // An emptied row has nothing to fall back on, and focus passes it by.
        PlasmaRow(
            gap = 28.px,
            modifier = Modifier.padding(top = 20.px),
            state = state,
            focusFallback = if (items.isEmpty()) FocusRequester.Default else first,
        ) {
            items(items.size, key = { items[it].channelId }) { i ->
                val item = items[i]
                FavoriteTile(
                    item,
                    onClick = { onChannel(item.channelId) },
                    onLongClick = { onOptions(item.channelId) },
                    moving = item.channelId == moving,
                    dimmed = moving != null && item.channelId != moving,
                    canMoveLeft = i > 0,
                    canMoveRight = i < items.lastIndex,
                    modifier = Modifier
                        .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                        .then(if (item.channelId == returnTo) Modifier.focusRequester(sheetReturn) else Modifier),
                )
            }
        }
    }
}

/**
 * A favorite channel. While it moves it's lifted (scale 1.12, up 6 px, a faint edge) with arrows
 * where Left and Right can carry it, and the other favorites step back (board 04's move mode).
 */
@Composable
private fun FavoriteTile(
    item: ChannelThumb,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    moving: Boolean,
    dimmed: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    modifier: Modifier,
) {
    val movingName = if (moving) stringResource(DesignR.string.moving_named, item.name) else null
    Box(Modifier.size(352.px, 198.px).zIndex(if (moving) 2f else 0f)) {
        PlasmaSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
                .fillMaxSize()
                .then(if (moving) Modifier.offset(y = (-6).px) else Modifier)
                .then(if (dimmed) Modifier.alpha(DIMMED) else Modifier)
                .then(if (movingName != null) Modifier.semantics { contentDescription = movingName } else Modifier),
            style = if (moving) FocusStyle.Avatar else FocusStyle.Tile,
            shape = PlasmaShapes.Thumbnail,
            color = PlasmaColors.Surface2,
        ) {
            if (item.still != null) {
                LiveThumbnail(item.still, item.stillAlignment, item.logo, item.name, item.progress)
            } else {
                LogoThumbnail(item.logo, item.name, item.nowTitle, item.progress)
            }
            if (moving) Box(Modifier.fillMaxSize().border(1.px, PlasmaColors.white(0.12f), PlasmaShapes.Thumbnail))
        }
        if (moving) {
            if (canMoveLeft) MoveArrow(PlasmaIcons.ChevronLeft, stringResource(DesignR.string.move_left), Modifier.align(Alignment.CenterStart).offset((-36).px, (-6).px))
            if (canMoveRight) MoveArrow(PlasmaIcons.ChevronRight, stringResource(DesignR.string.move_right), Modifier.align(Alignment.CenterEnd).offset(36.px, (-6).px))
        }
    }
}

/** Favorites step back to this while another one moves (board 04's move mode). */
private const val DIMMED = 0.6f

/**
 * A live channel without a picture, as a source's channels are: its logo, large, on a graphite
 * card, as Search draws a program without art, with what's on under it and its progress (the
 * owner's #77). Without a logo, its name.
 */
@Composable
private fun LogoThumbnail(logo: String?, name: String, nowTitle: String?, progress: Float) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PlasmaColors.Surface3, PlasmaColors.Plate)))) {
        LogoImage(
            logo,
            name,
            LogoSize.Fallback,
            Modifier.align(Alignment.Center).offset(y = if (nowTitle != null) (-16).px else 0.dp),
            fallbackStyle = plasmaStyle(26f, 700),
        )
        if (nowTitle != null) {
            PlasmaText(
                nowTitle,
                plasmaStyle(21f, 500),
                Modifier.align(Alignment.BottomStart).padding(start = 18.px, end = 18.px, bottom = 20.px),
                color = if (LocalFocused.current) PlasmaColors.Text else PlasmaColors.TextSoft2,
            )
            ProgressBar(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth(), height = 4.px, track = PlasmaColors.white(0.18f), rounded = false)
        }
    }
}

/** A live channel thumbnail: the picture, a dark fade at the top with the logo on it, and progress. */
@Composable
internal fun LiveThumbnail(
    still: String?,
    alignment: Alignment,
    logo: String?,
    name: String,
    progress: Float?,
    logoSize: LogoSize = LogoSize.Thumbnail,
    scrimTop: Float = 0.62f,
    scrimEnd: Float = 0.52f,
    progressHeight: Dp = 4.px,
    progressTrack: Color = PlasmaColors.white(0.18f),
) {
    Box(Modifier.fillMaxSize()) {
        PlasmaImage(still, Modifier.fillMaxSize(), alignment = alignment)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to PlasmaColors.black(scrimTop), scrimEnd to Color.Transparent)))
        // Logos sit centred on y 29, so differently shaped logos line up (board 01).
        Box(Modifier.padding(start = 16.px).height(58.px), contentAlignment = Alignment.CenterStart) {
            LogoImage(logo, name, logoSize)
        }
        if (progress != null) {
            ProgressBar(
                progress,
                Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                height = progressHeight,
                track = progressTrack,
                rounded = false,
            )
        }
    }
}

// ---- Continue Watching (board 02) ----------------------------------------------------------------

@Composable
private fun ContinueRow(
    continuing: List<ContinueItem>,
    first: FocusRequester,
    onContinue: (String) -> Unit,
    onOptions: (String) -> Unit,
    onBackdrop: (String?) -> Unit,
    returnTo: String?,
    sheetReturn: FocusRequester,
    modifier: Modifier,
) {
    Column(modifier) {
        PlasmaText(stringResource(R.string.continue_watching), PlasmaType.Section, Modifier.padding(start = 96.px))
        if (continuing.isEmpty()) {
            // Nothing started yet: a word on what shows here, where the row would be.
            PlasmaText(
                stringResource(R.string.continue_watching_empty),
                plasmaStyle(23f, 500),
                Modifier.padding(start = 96.px, top = 24.px),
                color = PlasmaColors.Text3,
            )
        } else {
            PlasmaRow(gap = 28.px, modifier = Modifier.padding(top = 24.px), focusFallback = first) {
                items(continuing.size, key = { continuing[it].id }) { i ->
                    val item = continuing[i]
                    ContinueTile(
                        item,
                        onClick = { onContinue(item.id) },
                        onLongClick = { onOptions(item.id) },
                        onFocused = { onBackdrop(item.image) },
                        modifier = Modifier
                            .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                            .then(if (item.id == returnTo) Modifier.focusRequester(sheetReturn) else Modifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueTile(
    item: ContinueItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(400.px)) {
        PlasmaSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.size(400.px, 225.px).semantics { contentDescription = item.title },
            style = FocusStyle.Tile,
            shape = PlasmaShapes.Thumbnail,
            color = PlasmaColors.Surface2,
            pivot = TransformOrigin(0.5f, 0f),
            onFocusChanged = {
                focused = it
                if (it) onFocused()
            },
        ) {
            when (item.kind) {
                ContinueKind.Poster -> {
                    PlasmaImage(
                        item.image,
                        Modifier
                            .absolute((-60).px, (-330).px, 520.px, 780.px)
                            .blur(CssBlur28),
                        colorFilter = brightness(0.72f),
                    )
                    PlasmaImage(
                        item.image,
                        Modifier
                            .offset(32.px, 20.px)
                            .size(124.px, 186.px)
                            .shadow(10.px, RoundedCornerShape(8.px))
                            .clip(RoundedCornerShape(8.px)),
                    )
                }
                ContinueKind.CatchUp -> LiveThumbnail(
                    item.image,
                    item.imageAlignment,
                    item.logo,
                    item.title,
                    progress = null,
                    logoSize = LogoSize.Card,
                    scrimTop = 0.55f,
                    scrimEnd = 0.45f,
                )
                ContinueKind.Art -> PlasmaImage(
                    item.image,
                    Modifier.fillMaxSize(),
                    alignment = item.imageAlignment,
                    zoom = item.imageZoom,
                    zoomPivot = item.zoomPivot,
                )
            }
            if (item.sourceTag != null) {
                ImageBadge(item.sourceTag, Modifier.align(Alignment.TopEnd).padding(top = 14.px, end = 14.px))
            }
            ProgressBar(
                item.progress,
                Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                height = 5.px,
                color = LocalAccent.current,
                rounded = false,
            )
        }
        // The focused tile grows downward from its top edge, so its caption moves down to make room.
        val shift by animateDpAsState(if (focused) 16.px else 0.dp, PlasmaMotion.focus(), label = "continueCaption")
        Column(Modifier.padding(top = 14.px).offset { IntOffset(0, shift.roundToPx()) }, verticalArrangement = Arrangement.spacedBy(3.px)) {
            PlasmaText(
                item.title,
                plasmaStyle(24f, if (focused) 600 else 500),
                color = if (focused) PlasmaColors.Text else PlasmaColors.TextSoft,
            )
            PlasmaText(item.meta, plasmaStyle(21f, 500, tabular = true), color = PlasmaColors.Text2)
        }
    }
}

// ---- New in Movies and New in Series (board 02) --------------------------------------------------

/** Posters with "See All" by the heading and at the end: New in Movies (board 02), and New in Series the same way. */
@Composable
private fun PosterRow(
    title: String,
    posters: List<PosterItem>,
    first: FocusRequester,
    onOpen: (String) -> Unit,
    onSeeAll: () -> Unit,
    onBackdrop: (String?) -> Unit,
    onNoImage: ((String) -> Unit)?,
    modifier: Modifier,
) {
    Column(modifier) {
        Row(Modifier.padding(start = 96.px), horizontalArrangement = Arrangement.spacedBy(14.px)) {
            PlasmaText(title, PlasmaType.Section, Modifier.alignBy(FirstBaseline))
            PlasmaText(stringResource(R.string.see_all), plasmaStyle(22f, 500), Modifier.alignBy(FirstBaseline), color = PlasmaColors.Text2)
        }
        PlasmaRow(gap = 24.px, modifier = Modifier.padding(top = 22.px), focusFallback = first) {
            itemsIndexed(posters, key = { _, poster -> poster.id }) { i, poster ->
                // Without a poster, its name on the plate, as on Movies and Series (the owner's #77).
                PosterTile(
                    image = poster.poster,
                    title = poster.title,
                    caption = null,
                    onClick = { onOpen(poster.id) },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    width = 168.px,
                    height = 252.px,
                    pivot = TransformOrigin.Center,
                    showCaption = false,
                    onFocusChanged = { if (it) onBackdrop(poster.poster) },
                    onNoImage = onNoImage?.let { find -> { find(poster.id) } },
                )
            }
            item(key = "see-all") { SeeAllTile(onSeeAll, 168.px, 252.px, PlasmaShapes.Poster) }
        }
    }
}

/** The tile at a row's end that opens the whole list: graphite, with "See All". */
@Composable
private fun SeeAllTile(onClick: () -> Unit, width: Dp, height: Dp, shape: Shape) {
    PlasmaSurface(
        onClick = onClick,
        modifier = Modifier.size(width, height),
        style = FocusStyle.Tile,
        shape = shape,
        contentAlignment = Alignment.Center,
    ) {
        GraphiteFill()
        PlasmaText(stringResource(R.string.see_all), plasmaStyle(22f, 500), color = PlasmaColors.TextSoft)
    }
}

// ---- Upcoming Events (the owner's #80) -----------------------------------------------------------

/**
 * Upcoming Events: a card for each program, soonest first. OK on one that's on plays its channel;
 * on one later it opens Remind Me and Record, as the Guide does; Menu opens them on any.
 */
@Composable
private fun UpcomingRow(
    items: List<UpcomingItem>,
    first: FocusRequester,
    onOpen: (UpcomingItem, live: Boolean) -> Unit,
    onOptions: (UpcomingItem) -> Unit,
    returnTo: String?,
    sheetReturn: FocusRequester,
    modifier: Modifier,
) {
    val now by rememberNow()
    Column(modifier) {
        PlasmaText(stringResource(R.string.upcoming_events), PlasmaType.Section, Modifier.padding(start = 96.px))
        PlasmaRow(gap = 24.px, modifier = Modifier.padding(top = 20.px), focusFallback = first) {
            itemsIndexed(items, key = { _, program -> program.id }) { i, program ->
                val live = program.isOn(now)
                UpcomingCard(
                    program,
                    now,
                    onClick = { onOpen(program, live) },
                    onLongClick = { onOptions(program) },
                    modifier = Modifier
                        .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                        .then(if (program.id == returnTo) Modifier.focusRequester(sheetReturn) else Modifier),
                )
            }
        }
    }
}

/**
 * A 448 × 100 card, as Recordings' Coming Up draws one (board 17): the title, then the channel's
 * logo and when, with ● LIVE and its progress while it's on; the bell and the red dot on the right.
 * The logo sits on the card, so focus lifts it and lightens it rather than filling it white.
 */
@Composable
private fun UpcomingCard(item: UpcomingItem, now: ZonedDateTime, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    val live = item.isOn(now)
    val whenText = upcomingWhen(item, now)
    val liveText = stringResource(DesignR.string.live)
    val description = listOfNotNull(
        item.title,
        item.channelName,
        liveText.takeIf { live },
        whenText,
        stringResource(R.string.reminder_set).takeIf { item.reminder },
        stringResource(R.string.recording_now).takeIf { item.recording },
    ).joinToString(", ")
    PlasmaSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.size(448.px, 100.px).semantics { contentDescription = description },
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
                    // A tall logo hangs past the line rather than making it taller, as on board 17.
                    Box(Modifier.height(24.px), contentAlignment = Alignment.Center) {
                        LogoImage(item.logo, item.channelName, LogoSize.Inline, Modifier.wrapContentHeight(unbounded = true))
                    }
                    if (live) LiveMark(liveText, style = plasmaStyle(17f, 600, trackingPx = 1.4f), dot = 8.px, gap = 7.px)
                    PlasmaText(whenText, plasmaStyle(20f, 500, tabular = true), color = PlasmaColors.Text2)
                }
            }
            UpcomingMarks(item.reminder, item.recording)
        }
        if (live) {
            val length = Duration.between(item.start, item.end).toMillis().coerceAtLeast(1)
            val progress = Duration.between(item.start, now).toMillis().toFloat() / length
            ProgressBar(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth(), height = 4.px, track = PlasmaColors.white(0.12f), rounded = false)
        }
    }
}

/**
 * The bell of a reminder and the red dot of a recording, each on a 40 px tinted square, as the
 * Coming Up cards mark theirs (board 17).
 */
@Composable
private fun UpcomingMarks(reminder: Boolean, recording: Boolean) {
    if (!reminder && !recording) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.px)) {
        if (reminder) {
            Box(Modifier.size(40.px).background(PlasmaColors.white(0.08f), RoundedCornerShape(10.px)), contentAlignment = Alignment.Center) {
                PlasmaIcon(PlasmaIcons.Bell, 22.px, tint = PlasmaColors.TextSoft, stroke = 2f)
            }
        }
        if (recording) {
            Box(Modifier.size(40.px).background(PlasmaColors.Live.copy(alpha = 0.14f), RoundedCornerShape(10.px)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(14.px).background(PlasmaColors.Live, CircleShape))
            }
        }
    }
}

/**
 * When a program in Upcoming Events is: "Ends 22:00" while it's on; "Tonight, 21:00–22:00" this
 * evening and in the small hours after it; "Today, 15:00–16:00" earlier today; otherwise the day
 * and start time, "Thu 22 Oct, 20:00" (as Recordings writes what's coming up).
 */
@Composable
private fun upcomingWhen(item: UpcomingItem, now: ZonedDateTime): String {
    val start = item.start.withZoneSameInstant(now.zone)
    val end = item.end.withZoneSameInstant(now.zone)
    if (item.isOn(now)) return stringResource(R.string.ends_at, Formats.time(end))
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), start.toLocalDate())
    val times = Formats.range(start, end)
    return when {
        days == 0L && start.hour >= EVENING || days == 1L && start.hour < SMALL_HOURS -> stringResource(DesignR.string.tonight_at, times)
        days == 0L -> stringResource(DesignR.string.today_at, times)
        else -> stringResource(DesignR.string.date_at, Formats.date(start), Formats.time(start))
    }
}

/** "Tonight" runs from 18:00 to 05:00 the next morning. */
private const val EVENING = 18
private const val SMALL_HOURS = 5

// ---- Recently Recorded (the owner's #80) ---------------------------------------------------------

/** The newest finished recordings, as Continue Watching draws its tiles; OK plays one, See All opens Recordings. */
@Composable
private fun RecordedRow(
    recorded: List<RecordedItem>,
    first: FocusRequester,
    onPlay: (RecordedItem) -> Unit,
    onSeeAll: () -> Unit,
    onBackdrop: (String?) -> Unit,
    modifier: Modifier,
) {
    val now by rememberNow()
    Column(modifier) {
        Row(Modifier.padding(start = 96.px), horizontalArrangement = Arrangement.spacedBy(14.px)) {
            PlasmaText(stringResource(R.string.recently_recorded), PlasmaType.Section, Modifier.alignBy(FirstBaseline))
            PlasmaText(stringResource(R.string.see_all), plasmaStyle(22f, 500), Modifier.alignBy(FirstBaseline), color = PlasmaColors.Text2)
        }
        PlasmaRow(gap = 28.px, modifier = Modifier.padding(top = 24.px), focusFallback = first) {
            itemsIndexed(recorded, key = { _, recording -> recording.id }) { i, recording ->
                RecordedTile(
                    recording,
                    now,
                    onClick = { onPlay(recording) },
                    onFocused = { onBackdrop(recording.image) },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
            item(key = "see-all") { SeeAllTile(onSeeAll, 400.px, 225.px, PlasmaShapes.Thumbnail) }
        }
    }
}

/**
 * A recording: its picture with the channel's logo on it, or the logo large on graphite without
 * one, with how far it's been watched; under it, a dot while it's unwatched (brief 2.9), its title,
 * and when it aired and for how long.
 */
@Composable
private fun RecordedTile(item: RecordedItem, now: ZonedDateTime, onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val meta = recordedMeta(item, now)
    val description = listOfNotNull(item.title, item.channelName, meta, stringResource(R.string.unwatched).takeIf { item.unwatched }).joinToString(", ")
    Column(Modifier.width(400.px)) {
        PlasmaSurface(
            onClick = onClick,
            modifier = modifier.size(400.px, 225.px).semantics { contentDescription = description },
            style = FocusStyle.Tile,
            shape = PlasmaShapes.Thumbnail,
            color = PlasmaColors.Surface2,
            pivot = TransformOrigin(0.5f, 0f),
            onFocusChanged = {
                focused = it
                if (it) onFocused()
            },
        ) {
            if (item.image != null) {
                LiveThumbnail(item.image, item.imageAlignment, item.logo, item.channelName, progress = null, logoSize = LogoSize.Card, scrimTop = 0.55f, scrimEnd = 0.45f)
            } else {
                LogoThumbnail(item.logo, item.channelName, nowTitle = null, progress = 0f)
            }
            if (item.progress != null) {
                ProgressBar(item.progress, Modifier.align(Alignment.BottomStart).fillMaxWidth(), height = 5.px, color = LocalAccent.current, rounded = false)
            }
        }
        // The focused tile grows downward from its top edge, so its caption moves down to make room.
        val shift by animateDpAsState(if (focused) 16.px else 0.dp, PlasmaMotion.focus(), label = "recordedCaption")
        Column(Modifier.padding(top = 14.px).offset { IntOffset(0, shift.roundToPx()) }, verticalArrangement = Arrangement.spacedBy(3.px)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                if (item.unwatched) Box(Modifier.size(9.px).background(PlasmaColors.Text, CircleShape))
                PlasmaText(
                    item.title,
                    plasmaStyle(24f, if (focused) 600 else 500),
                    color = if (focused) PlasmaColors.Text else PlasmaColors.TextSoft,
                )
            }
            PlasmaText(meta, plasmaStyle(21f, 500, tabular = true), color = PlasmaColors.Text2)
        }
    }
}

/**
 * When a recording aired, and for how long, as Recordings writes it: "Yesterday · 2 h 10 min", or
 * for news "Yesterday, 20:30 · 30 min".
 */
@Composable
private fun recordedMeta(item: RecordedItem, now: ZonedDateTime): String {
    val aired = item.aired.withZoneSameInstant(now.zone)
    val time = Formats.time(aired)
    val days = ChronoUnit.DAYS.between(aired.toLocalDate(), now.toLocalDate())
    val day = if (item.news) {
        when (days) {
            0L -> stringResource(DesignR.string.today_at, time)
            1L -> stringResource(DesignR.string.yesterday_at, time)
            else -> stringResource(DesignR.string.date_at, Formats.date(aired), time)
        }
    } else {
        when (days) {
            0L -> stringResource(DesignR.string.today)
            1L -> stringResource(DesignR.string.yesterday)
            else -> Formats.date(aired)
        }
    }
    return listOf(day, Formats.duration(item.length)).joinToString(" · ")
}

// ---- Apps (board 02) -----------------------------------------------------------------------------

/** Home's Apps row (board 02): the apps in the user's order, then All Apps. */
@Composable
private fun AppsRow(apps: List<AppItem>, first: FocusRequester, actions: HomeActions, onBackdrop: (String?) -> Unit, modifier: Modifier) {
    Column(modifier) {
        PlasmaText(stringResource(R.string.apps), PlasmaType.Section, Modifier.padding(start = 96.px))
        PlasmaRow(gap = 24.px, modifier = Modifier.padding(top = 20.px), focusFallback = first) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { i, app ->
                PlasmaSurface(
                    onClick = { actions.onApp(app.packageName) },
                    modifier = Modifier
                        .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                        .size(268.px, 151.px)
                        .semantics { contentDescription = app.label },
                    style = FocusStyle.Tile,
                    shape = PlasmaShapes.Poster,
                    color = PlasmaColors.Surface2,
                    onFocusChanged = { if (it) onBackdrop(app.banner) },
                ) {
                    AppArtwork(app.banner, app.icon, app.label, Modifier.fillMaxSize())
                }
            }
            item(key = "all-apps") {
                PlasmaSurface(
                    onClick = actions.onAllApps,
                    // With no apps on the row, All Apps is where focus comes in.
                    modifier = Modifier
                        .then(if (apps.isEmpty()) Modifier.focusRequester(first) else Modifier)
                        .size(268.px, 151.px),
                    style = FocusStyle.Tile,
                    shape = PlasmaShapes.Poster,
                    contentAlignment = Alignment.Center,
                ) {
                    GraphiteFill()
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.px)) {
                        PlasmaIcon(PlasmaIcons.AllApps, 40.px, tint = PlasmaColors.TextSoft2)
                        PlasmaText(stringResource(R.string.all_apps), plasmaStyle(22f, 500), color = PlasmaColors.TextSoft)
                    }
                }
            }
        }
    }
}
