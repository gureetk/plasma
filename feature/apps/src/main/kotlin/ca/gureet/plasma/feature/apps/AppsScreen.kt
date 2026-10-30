// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.apps

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.UnclippedLine
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.SelectKeys
import ca.gureet.plasma.core.design.component.AppArtwork
import ca.gureet.plasma.core.design.component.FooterKey
import ca.gureet.plasma.core.design.component.GraphiteFill
import ca.gureet.plasma.core.design.component.KeyFooter
import ca.gureet.plasma.core.design.component.MoveArrow
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.PlasmaRow
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.component.TopBar
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.design.component.carry
import ca.gureet.plasma.core.design.component.keepInView
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.design.R as DesignR

/** An installed app, drawn with its TV banner, or its [icon] where it has none (brief 4.4). */
@Immutable
data class AppItem(val packageName: String, val label: String, val banner: String?, val icon: String? = null)

/**
 * The tiles after the apps in All Apps (4.4). Inputs shows only on a TV with inputs, Get More Apps
 * only with the Play Store.
 */
enum class SystemTile { Inputs, DeviceSettings, GetMoreApps }

/** One of the TV's inputs, in the list Inputs opens (4.4): "HDMI 1", or the name it was given. */
@Immutable
data class InputItem(val id: String, val label: String)

@Immutable
data class AppsUiState(
    /** The Apps row on Home, in its order. */
    val onHome: List<AppItem>,
    /** Everything else, A to Z. */
    val others: List<AppItem>,
    val profileName: String,
    val profileInitial: String,
    val systemTiles: List<SystemTile> = SystemTile.entries,
    /** What Inputs lists. */
    val inputs: List<InputItem> = emptyList(),
    /** Apps hidden with Hide, which "13 apps installed" counts too. */
    val hiddenCount: Int = 0,
    /** An app being moved along the Home row (board 04 opens this way). */
    val moving: String? = null,
)

class AppsActions(
    val onTab: (TopTab) -> Unit = {},
    val onSettings: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val onOpen: (String) -> Unit = {},
    /** A system tile chosen; Inputs, when there are inputs, lists them instead. */
    val onSystem: (SystemTile) -> Unit = {},
    /** An input chosen from the list Inputs opens. */
    val onInput: (id: String) -> Unit = {},
    /** The Home row's new order, after a move or adding or removing an app. */
    val onHomeRow: (List<String>) -> Unit = {},
    val onHide: (String) -> Unit = {},
    val onAppInfo: (String) -> Unit = {},
    val onUninstall: (String) -> Unit = {},
)

private const val COLUMNS = 6
private const val GAP_PX = 28f

/** Six banners and their gaps across the 1728 px between the margins: 264.67 × 149 px. */
private val TileWidth = ((1728f - (COLUMNS - 1) * GAP_PX) / COLUMNS).px
private val TileHeight = 149.px
private val Gap = GAP_PX.px

/** Room under the Home row for "Moving Sonar · Press OK to drop it here". */
private val HintSpace = 100.px

/**
 * Apps (board 04, brief 4.4): "On Home", the Apps row on Home in its order, then All Apps from A to
 * Z with Inputs, Device Settings and Get More Apps at the end. Menu offers Move, Add to Home or
 * Remove from Home, Hide, App Info and Uninstall. While an app moves, Left and Right carry it
 * along the row, OK drops it and Back puts it back.
 */
@Composable
fun AppsScreen(state: AppsUiState, actions: AppsActions, modifier: Modifier = Modifier) {
    var home by remember(state) { mutableStateOf(state.onHome) }
    var others by remember(state) { mutableStateOf(state.others) }
    var hiddenCount by remember(state) { mutableIntStateOf(state.hiddenCount) }
    var moving by remember(state) { mutableStateOf(state.moving) }
    var orderBeforeMove by remember(state) { mutableStateOf(state.onHome) }
    var sheetFor by remember { mutableStateOf<AppItem?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var inputsOpen by remember { mutableStateOf(false) }

    val tabFocus = remember { FocusRequester() }
    val homeRow = remember { FocusRequester() }
    val sheetReturn = remember { FocusRequester() }
    val inputsTile = remember { FocusRequester() }
    val start = remember { FocusRequester() }
    val homeState = rememberLazyListState()
    // Focus starts on the first app, or the first system tile on a TV without any.
    val startPackage = moving ?: home.firstOrNull()?.packageName ?: others.firstOrNull()?.packageName

    LaunchedEffect(Unit) {
        withFrameNanos { }
        start.requestFocus()
    }

    fun startMove(app: AppItem) {
        orderBeforeMove = home
        moving = app.packageName
    }

    fun shift(by: Int) {
        homeState.carry(home, home.indexOfFirst { it.packageName == moving }, by)?.let { home = it }
    }

    fun drop() {
        moving = null
        actions.onHomeRow(home.map { it.packageName })
    }

    fun cancelMove() {
        homeState.keepInView(orderBeforeMove.indexOfFirst { it.packageName == moving }, orderBeforeMove.size)
        home = orderBeforeMove
        moving = null
    }

    fun openSheet(app: AppItem) {
        sheetFor = app
        sheetOpen = true
    }

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (moving == null || sheetOpen) return@onPreviewKeyEvent false
                val down = event.type == KeyEventType.KeyDown
                val up = event.type == KeyEventType.KeyUp
                when (event.key) {
                    Key.DirectionLeft -> { if (down) shift(-1); true }
                    Key.DirectionRight -> { if (down) shift(1); true }
                    Key.DirectionUp, Key.DirectionDown -> true
                    in SelectKeys -> { if (up) drop(); true }
                    Key.Back, Key.Escape -> { if (up) cancelMove(); true }
                    Key.Menu -> {
                        if (up) home.firstOrNull { it.packageName == moving }?.let { app -> cancelMove(); openSheet(app) }
                        true
                    }
                    else -> false
                }
            },
    ) {
        TopBar(
            current = TopTab.Apps,
            profileInitial = state.profileInitial,
            profileName = state.profileName,
            onTab = actions.onTab,
            onSettings = actions.onSettings,
            onProfile = actions.onProfile,
            currentTabFocusRequester = tabFocus,
            focusDown = homeRow,
        )

        val dimmed = moving != null
        Column(
            Modifier
                .offset(0.px, 148.px)
                .width(1920.px)
                .height((976 - 148).px)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(
                stringResource(R.string.on_home),
                stringResource(R.string.on_home_explained),
                trailing = (home.size + others.size + hiddenCount).let { pluralStringResource(R.plurals.apps_installed, it, it) },
            )
            // A row like Home's: it runs past the right edge when there are more than six. It's tall
            // enough for the moving app's hint, which a row would otherwise cut off.
            PlasmaRow(
                gap = Gap,
                modifier = Modifier.padding(top = 22.px).height(TileHeight + HintSpace).focusRequester(homeRow),
                state = homeState,
            ) {
                items(home, key = { it.packageName }) { app ->
                    AppTile(
                        app = app,
                        onClick = { actions.onOpen(app.packageName) },
                        onLongClick = { openSheet(app) },
                        moving = app.packageName == moving,
                        dimmed = dimmed && app.packageName != moving,
                        modifier = Modifier
                            .then(if (app.packageName == startPackage) Modifier.focusRequester(start) else Modifier)
                            .then(if (app.packageName == sheetFor?.packageName) Modifier.focusRequester(sheetReturn) else Modifier),
                    )
                }
            }

            // All Apps starts 508 px down the screen (board 04), below the moving app's hint.
            Spacer(Modifier.height((508 - 148 - 37 - 22 - 149).px - HintSpace))
            SectionHeader(stringResource(R.string.all_apps), stringResource(R.string.a_to_z))
            val tiles: List<Any> = others + state.systemTiles
            Column(Modifier.padding(start = 96.px, top = 22.px, end = 96.px), verticalArrangement = Arrangement.spacedBy(Gap)) {
                for (row in tiles.chunked(COLUMNS)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                        for (tile in row) {
                            when (tile) {
                                is AppItem -> key(tile.packageName) {
                                    AppTile(
                                        app = tile,
                                        onClick = { actions.onOpen(tile.packageName) },
                                        onLongClick = { openSheet(tile) },
                                        moving = false,
                                        dimmed = dimmed,
                                        modifier = Modifier
                                            .then(if (tile.packageName == startPackage) Modifier.focusRequester(start) else Modifier)
                                            .then(if (tile.packageName == sheetFor?.packageName) Modifier.focusRequester(sheetReturn) else Modifier),
                                    )
                                }
                                is SystemTile -> SystemTileView(
                                    tile,
                                    onClick = {
                                        if (tile == SystemTile.Inputs && state.inputs.isNotEmpty()) inputsOpen = true else actions.onSystem(tile)
                                    },
                                    dimmed = dimmed,
                                    modifier = Modifier
                                        .then(if (startPackage == null && tile == state.systemTiles.first()) Modifier.focusRequester(start) else Modifier)
                                        .then(if (tile == SystemTile.Inputs) Modifier.focusRequester(inputsTile) else Modifier),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.px))
        }

        KeyFooter(
            colorKeys = emptyList(),
            keys = if (moving != null) {
                listOf(
                    FooterKey("OK", stringResource(DesignR.string.key_drop)),
                    FooterKey(PlasmaIcons.Back, stringResource(DesignR.string.key_cancel)),
                    FooterKey(PlasmaIcons.Menu, stringResource(R.string.key_moving_options)),
                )
            } else {
                listOf(
                    FooterKey("OK", stringResource(R.string.key_open)),
                    FooterKey(PlasmaIcons.Menu, stringResource(R.string.key_app_options)),
                )
            },
        )

        SheetHost(visible = sheetOpen, returnFocus = sheetReturn) {
            val app = sheetFor ?: return@SheetHost
            val onRow = home.any { it.packageName == app.packageName }
            OptionsSheet(
                title = app.label,
                onDismiss = { sheetOpen = false },
                leading = {
                    AppArtwork(app.banner, app.icon, label = null, Modifier.size(112.px, 63.px).clip(PlasmaShapes.Plate), iconSize = 40.px)
                },
                options = buildList {
                    if (onRow) {
                        add(SheetOption(stringResource(R.string.option_move), onClick = { startMove(app) }))
                        add(
                            SheetOption(stringResource(R.string.option_remove_from_home), onClick = {
                                home = home - app
                                others = (others + app).sortedBy { it.label.lowercase() }
                                actions.onHomeRow(home.map { it.packageName })
                            }),
                        )
                    } else {
                        add(
                            SheetOption(stringResource(R.string.option_add_to_home), onClick = {
                                others = others - app
                                home = home + app
                                actions.onHomeRow(home.map { it.packageName })
                            }),
                        )
                    }
                    add(
                        SheetOption(stringResource(R.string.option_hide), onClick = {
                            // Focus goes to the next app along, as the hidden one goes.
                            val row = if (onRow) home else others
                            sheetFor = row.getOrNull(row.indexOf(app) + 1) ?: row.getOrNull(row.indexOf(app) - 1)
                            home = home - app
                            others = others - app
                            hiddenCount++
                            actions.onHide(app.packageName)
                        }),
                    )
                    add(SheetOption.Divider)
                    add(SheetOption(stringResource(R.string.option_app_info), onClick = { actions.onAppInfo(app.packageName) }))
                    add(SheetOption(stringResource(R.string.option_uninstall), onClick = { actions.onUninstall(app.packageName) }))
                },
            )
        }

        // Inputs lists the TV's inputs (4.4); choosing one switches to it.
        SheetHost(visible = inputsOpen, returnFocus = inputsTile) {
            OptionsSheet(
                title = stringResource(R.string.inputs),
                onDismiss = { inputsOpen = false },
                options = state.inputs.map { input -> SheetOption(input.label, onClick = { actions.onInput(input.id) }, icon = PlasmaIcons.Inputs) },
            )
        }
    }
}

/** "On Home · The Apps row…", with a count on the right. */
@Composable
private fun SectionHeader(title: String, subtitle: String, trailing: String? = null) {
    Row(Modifier.padding(horizontal = 96.px).height(37.px), horizontalArrangement = Arrangement.spacedBy(14.px)) {
        PlasmaText(title, PlasmaType.Section, Modifier.alignByBaseline())
        PlasmaText(subtitle, plasmaStyle(22f, 500), Modifier.alignByBaseline(), color = PlasmaColors.Text3)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            PlasmaText(trailing, plasmaStyle(22f, 500), Modifier.alignByBaseline(), color = PlasmaColors.Text2)
        }
    }
}

/**
 * An app's banner. While it moves it's lifted (scale 1.12, up 6 px, a faint edge) with arrows
 * either side and a hint below.
 */
@Composable
private fun AppTile(
    app: AppItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    moving: Boolean,
    dimmed: Boolean,
    modifier: Modifier,
) {
    val description = if (moving) stringResource(DesignR.string.moving_named, app.label) else app.label
    Box(Modifier.size(TileWidth, TileHeight).zIndex(if (moving) 2f else 0f)) {
        PlasmaSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
                .fillMaxSize()
                .then(if (moving) Modifier.offset(y = (-6).px) else Modifier)
                .alpha(if (dimmed) 0.6f else 1f)
                .semantics { contentDescription = description },
            style = if (moving) FocusStyle.Avatar else FocusStyle.Tile,
            shape = PlasmaShapes.Poster,
            color = PlasmaColors.Surface2,
        ) {
            AppArtwork(app.banner, app.icon, app.label, Modifier.fillMaxSize())
            if (moving) Box(Modifier.fillMaxSize().border(1.px, PlasmaColors.white(0.12f), PlasmaShapes.Poster))
        }
        if (moving) {
            MoveArrow(PlasmaIcons.ChevronLeft, stringResource(DesignR.string.move_left), Modifier.align(Alignment.TopStart).offset((-36).px, 56.px))
            MoveArrow(PlasmaIcons.ChevronRight, stringResource(DesignR.string.move_right), Modifier.align(Alignment.TopEnd).offset(36.px, 56.px))
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .requiredWidth(TileWidth + 120.px)
                    .offset(y = 184.px),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.px),
            ) {
                // Below the tile, outside its place in the grid, where text that clips draws nothing.
                UnclippedLine(stringResource(DesignR.string.moving, app.label), plasmaStyle(23f, 600), TileWidth + 120.px)
                UnclippedLine(stringResource(DesignR.string.moving_hint), plasmaStyle(20f, 500), TileWidth + 120.px, color = PlasmaColors.Text2)
            }
        }
    }
}

@Composable
private fun SystemTileView(tile: SystemTile, onClick: () -> Unit, dimmed: Boolean, modifier: Modifier = Modifier) {
    val (icon, iconSize, label) = when (tile) {
        SystemTile.Inputs -> Triple(PlasmaIcons.Inputs, 40.px, stringResource(R.string.inputs))
        SystemTile.DeviceSettings -> Triple(PlasmaIcons.Settings, 38.px, stringResource(R.string.device_settings))
        SystemTile.GetMoreApps -> Triple(PlasmaIcons.GetMoreApps, 38.px, stringResource(R.string.get_more_apps))
    }
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.size(TileWidth, TileHeight).alpha(if (dimmed) 0.6f else 1f),
        style = FocusStyle.Tile,
        shape = PlasmaShapes.Poster,
        contentAlignment = Alignment.Center,
    ) {
        GraphiteFill()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.px)) {
            PlasmaIcon(icon, iconSize, tint = PlasmaColors.TextSoft2, stroke = 1.6f)
            PlasmaText(label, plasmaStyle(22f, 500), color = PlasmaColors.TextSoft)
        }
    }
}
