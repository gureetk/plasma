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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalAccent
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.absolute
import ca.gureet.plasma.core.design.component.GraphiteFill
import ca.gureet.plasma.core.design.component.ImageBadge
import ca.gureet.plasma.core.design.component.LiveMark
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.PlasmaRow
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.component.TopBar
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.design.component.brightness
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.feature.home.R
import ca.gureet.plasma.player.VideoFrame
import ca.gureet.plasma.player.VideoSlot
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
    val still: String?,
    val stillAlignment: Alignment,
    val progress: Float,
    /** For screen readers: "Wildframe 4K, Kingdoms of Ice". */
    val description: String,
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
)

data class PosterItem(val id: String, val title: String, val poster: String?)

data class AppItem(val packageName: String, val label: String, val banner: String?)

data class HomeUiState(
    val profileName: String,
    val profileInitial: String,
    val hero: List<HeroItem>,
    val favorites: List<ChannelThumb>,
    val continueWatching: List<ContinueItem>,
    val newMovies: List<PosterItem>,
    val apps: List<AppItem>,
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
    val onChannelOptions: (String) -> Unit = {},
    val onContinue: (String) -> Unit = {},
    val onContinueOptions: (String) -> Unit = {},
    val onMovie: (String) -> Unit = {},
    val onSeeAllMovies: () -> Unit = {},
    val onApp: (String) -> Unit = {},
    val onAllApps: () -> Unit = {},
)

/** Where focus starts when Home opens. */
enum class HomeFocus { Dock, Favorites, Rows, None }

// ---- Screen --------------------------------------------------------------------------------------

/**
 * Home scrolls between three resting positions instead of freely: the top (board 01), a little
 * further down so Favorite Channels fit (focus on that row), and the rows below (board 02).
 */
private enum class HomeSection(val scrollPx: Int) { Top(0), Favorites(120), Rows(1040) }

/** Where the rows start on the page: 1186 − 1040 puts Continue Watching at y 146, as on board 02. */
private const val ROWS_TOP_PX = 1186

/**
 * Home (boards 01 and 02, brief 4.1–4.2): the last channel's live video behind a hero, the dock,
 * Favorite Channels, then Continue Watching, New in Movies and Apps further down. The top bar
 * stays pinned. [heroAutoAdvance] is off in screenshot tests.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    initialFocus: HomeFocus = HomeFocus.Dock,
    heroAutoAdvance: Boolean = true,
) {
    var section by remember { mutableStateOf(HomeSection.Top) }
    var heroIndex by remember { mutableIntStateOf(0) }
    var heroFocused by remember { mutableStateOf(false) }
    var backdrop by remember { mutableStateOf(state.continueWatching.firstOrNull()?.image) }

    val homeTab = remember { FocusRequester() }
    val hero = remember { FocusRequester() }
    val liveTvTile = remember { FocusRequester() }
    val firstFavorite = remember { FocusRequester() }
    val firstContinue = remember { FocusRequester() }

    val scroll by animateDpAsState(section.scrollPx.px, PlasmaMotion.screen(), label = "homeScroll")
    val topAlpha by animateFloatAsState(if (section == HomeSection.Rows) 0f else 1f, PlasmaMotion.screen(), label = "homeTop")

    LaunchedEffect(initialFocus) {
        // Rows are lazy: their tiles join the layout in the first frame, after this effect starts.
        withFrameNanos { }
        when (initialFocus) {
            HomeFocus.Dock -> liveTvTile.requestFocus()
            HomeFocus.Favorites -> firstFavorite.requestFocus()
            HomeFocus.Rows -> firstContinue.requestFocus()
            HomeFocus.None -> Unit
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

    Box(modifier.fillMaxSize()) {
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
                if (item != null) {
                    Hero(
                        item = item,
                        count = state.hero.size,
                        index = heroIndex,
                        focused = heroFocused,
                        onIndex = { heroIndex = it },
                        onFocused = {
                            heroFocused = it
                            if (it) section = HomeSection.Top
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
                Dock(
                    onDock = actions.onDock,
                    firstTile = liveTvTile,
                    up = hero,
                    modifier = Modifier
                        .offset(y = 640.px)
                        .onFocusChanged { if (it.hasFocus) section = HomeSection.Top },
                )
                FavoriteChannels(
                    state.favorites,
                    first = firstFavorite,
                    onChannel = actions.onChannel,
                    onOptions = actions.onChannelOptions,
                    modifier = Modifier
                        .offset(y = 880.px)
                        .onFocusChanged { if (it.hasFocus) section = HomeSection.Favorites },
                )
            }
            HomeRows(
                state,
                actions,
                first = firstContinue,
                onBackdrop = { backdrop = it },
                modifier = Modifier
                    .offset(y = ROWS_TOP_PX.px)
                    .onFocusChanged { if (it.hasFocus) section = HomeSection.Rows },
            )
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
 */
@Composable
private fun Dock(onDock: (DockTarget) -> Unit, firstTile: FocusRequester, up: FocusRequester, modifier: Modifier) {
    Row(
        modifier.padding(start = 96.px).width(1728.px).focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(29.px),
    ) {
        DockEntries.forEachIndexed { i, entry ->
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

// ---- Favorite Channels ---------------------------------------------------------------------------

@Composable
private fun FavoriteChannels(
    items: List<ChannelThumb>,
    first: FocusRequester,
    onChannel: (String) -> Unit,
    onOptions: (String) -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        PlasmaText(stringResource(R.string.favorite_channels), PlasmaType.Section, Modifier.padding(start = 96.px))
        PlasmaRow(gap = 28.px, modifier = Modifier.padding(top = 20.px), focusFallback = first) {
            items(items.size, key = { items[it].channelId }) { i ->
                val item = items[i]
                PlasmaSurface(
                    onClick = { onChannel(item.channelId) },
                    onLongClick = { onOptions(item.channelId) },
                    modifier = (if (i == 0) Modifier.focusRequester(first) else Modifier).size(352.px, 198.px),
                    style = FocusStyle.Tile,
                    shape = PlasmaShapes.Thumbnail,
                    color = PlasmaColors.Surface2,
                ) {
                    LiveThumbnail(item.still, item.stillAlignment, item.logo, item.name, item.progress)
                }
            }
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

// ---- Rows below (board 02) -----------------------------------------------------------------------

@Composable
private fun HomeRows(
    state: HomeUiState,
    actions: HomeActions,
    first: FocusRequester,
    onBackdrop: (String?) -> Unit,
    modifier: Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        // Continue Watching (y 146 on board 02)
        Column {
            PlasmaText(stringResource(R.string.continue_watching), PlasmaType.Section, Modifier.padding(start = 96.px))
            PlasmaRow(gap = 28.px, modifier = Modifier.padding(top = 24.px), focusFallback = first) {
                items(state.continueWatching.size, key = { state.continueWatching[it].id }) { i ->
                    val item = state.continueWatching[i]
                    ContinueTile(
                        item,
                        onClick = { actions.onContinue(item.id) },
                        onLongClick = { actions.onContinueOptions(item.id) },
                        onFocused = { onBackdrop(item.image) },
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }

        // New in Movies (y 540)
        Column(Modifier.offset(y = 394.px)) {
            Row(Modifier.padding(start = 96.px), horizontalArrangement = Arrangement.spacedBy(14.px)) {
                PlasmaText(stringResource(R.string.new_in_movies), PlasmaType.Section, Modifier.alignBy(FirstBaseline))
                PlasmaText(stringResource(R.string.see_all), plasmaStyle(22f, 500), Modifier.alignBy(FirstBaseline), color = PlasmaColors.Text2)
            }
            val firstMovie = remember { FocusRequester() }
            PlasmaRow(gap = 24.px, modifier = Modifier.padding(top = 22.px), focusFallback = firstMovie) {
                itemsIndexed(state.newMovies, key = { _, movie -> movie.id }) { i, movie ->
                    PlasmaSurface(
                        onClick = { actions.onMovie(movie.id) },
                        modifier = Modifier
                            .then(if (i == 0) Modifier.focusRequester(firstMovie) else Modifier)
                            .size(168.px, 252.px)
                            .semantics { contentDescription = movie.title },
                        style = FocusStyle.Tile,
                        shape = PlasmaShapes.Poster,
                        color = PlasmaColors.Surface2,
                        onFocusChanged = { if (it) onBackdrop(movie.poster) },
                    ) {
                        PlasmaImage(movie.poster, Modifier.fillMaxSize())
                    }
                }
                item(key = "see-all") {
                    PlasmaSurface(
                        onClick = actions.onSeeAllMovies,
                        modifier = Modifier.size(168.px, 252.px),
                        style = FocusStyle.Tile,
                        shape = PlasmaShapes.Poster,
                        contentAlignment = Alignment.Center,
                    ) {
                        GraphiteFill()
                        PlasmaText(stringResource(R.string.see_all), plasmaStyle(22f, 500), color = PlasmaColors.TextSoft)
                    }
                }
            }
        }

        // Apps (y 878), in the user's order, then All Apps.
        Column(Modifier.offset(y = 732.px)) {
            PlasmaText(stringResource(R.string.apps), PlasmaType.Section, Modifier.padding(start = 96.px))
            val firstApp = remember { FocusRequester() }
            PlasmaRow(gap = 24.px, modifier = Modifier.padding(top = 20.px), focusFallback = firstApp) {
                itemsIndexed(state.apps, key = { _, app -> app.packageName }) { i, app ->
                    PlasmaSurface(
                        onClick = { actions.onApp(app.packageName) },
                        modifier = Modifier
                            .then(if (i == 0) Modifier.focusRequester(firstApp) else Modifier)
                            .size(268.px, 151.px)
                            .semantics { contentDescription = app.label },
                        style = FocusStyle.Tile,
                        shape = PlasmaShapes.Poster,
                        color = PlasmaColors.Surface2,
                        onFocusChanged = { if (it) onBackdrop(app.banner) },
                    ) {
                        PlasmaImage(app.banner, Modifier.fillMaxSize())
                    }
                }
                item(key = "all-apps") {
                    PlasmaSurface(
                        onClick = actions.onAllApps,
                        modifier = Modifier.size(268.px, 151.px),
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
