// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.boxShadow
import ca.gureet.plasma.core.design.component.FooterKey
import ca.gureet.plasma.core.design.component.GraphiteFill
import ca.gureet.plasma.core.design.component.KeyFooter
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.PlasmaRow
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.component.TopBar
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

/** An installed app, drawn with its TV banner. */
@Immutable
data class AppItem(val packageName: String, val label: String, val banner: String?)

/** The tiles after the apps in All Apps (4.4). Get More Apps shows only with the Play Store. */
enum class SystemTile { Inputs, DeviceSettings, GetMoreApps }

@Immutable
data class AppsUiState(
    /** The Apps row on Home, in its order. */
    val onHome: List<AppItem>,
    /** Everything else, A to Z. */
    val others: List<AppItem>,
    val profileName: String,
    val profileInitial: String,
    val systemTiles: List<SystemTile> = SystemTile.entries,
    /** An app being moved along the Home row (board 04 opens this way). */
    val moving: String? = null,
)

class AppsActions(
    val onTab: (TopTab) -> Unit = {},
    val onSettings: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onSystem: (SystemTile) -> Unit = {},
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
    var moving by remember(state) { mutableStateOf(state.moving) }
    var orderBeforeMove by remember(state) { mutableStateOf(state.onHome) }
    var sheetFor by remember { mutableStateOf<AppItem?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }

    val tabFocus = remember { FocusRequester() }
    val homeRow = remember { FocusRequester() }
    val sheetReturn = remember { FocusRequester() }
    val start = remember { FocusRequester() }
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
        val from = home.indexOfFirst { it.packageName == moving }
        val to = from + by
        if (from >= 0 && to in home.indices) home = home.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun drop() {
        moving = null
        actions.onHomeRow(home.map { it.packageName })
    }

    fun cancelMove() {
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
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (up) drop(); true }
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
                trailing = pluralStringResource(R.plurals.apps_installed, home.size + others.size, home.size + others.size),
            )
            // A row like Home's: it runs past the right edge when there are more than six. It's tall
            // enough for the moving app's hint, which a row would otherwise cut off.
            PlasmaRow(
                gap = Gap,
                modifier = Modifier.padding(top = 22.px).height(TileHeight + HintSpace).focusRequester(homeRow),
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
                                is SystemTile -> SystemTileView(tile, onClick = { actions.onSystem(tile) }, dimmed = dimmed)
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
                    FooterKey("OK", stringResource(R.string.key_drop)),
                    FooterKey(PlasmaIcons.Back, stringResource(R.string.key_cancel)),
                    FooterKey(PlasmaIcons.Menu, stringResource(R.string.key_moving_options)),
                )
            } else {
                listOf(
                    FooterKey("OK", stringResource(R.string.key_open)),
                    FooterKey(PlasmaIcons.Menu, stringResource(R.string.key_options)),
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
                    PlasmaImage(app.banner, Modifier.size(112.px, 63.px).clip(PlasmaShapes.Plate))
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
                            home = home - app
                            others = others - app
                            actions.onHide(app.packageName)
                        }),
                    )
                    add(SheetOption.Divider)
                    add(SheetOption(stringResource(R.string.option_app_info), onClick = { actions.onAppInfo(app.packageName) }))
                    add(SheetOption(stringResource(R.string.option_uninstall), onClick = { actions.onUninstall(app.packageName) }))
                },
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
    val description = if (moving) stringResource(R.string.moving_named, app.label) else app.label
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
            PlasmaImage(app.banner, Modifier.fillMaxSize())
            if (moving) Box(Modifier.fillMaxSize().border(1.px, PlasmaColors.white(0.12f), PlasmaShapes.Poster))
        }
        if (moving) {
            MoveArrow(PlasmaIcons.ChevronLeft, stringResource(R.string.move_left), Modifier.align(Alignment.TopStart).offset((-36).px, 56.px))
            MoveArrow(PlasmaIcons.ChevronRight, stringResource(R.string.move_right), Modifier.align(Alignment.TopEnd).offset(36.px, 56.px))
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .requiredWidth(TileWidth + 120.px)
                    .offset(y = 184.px),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.px),
            ) {
                PlasmaText(stringResource(R.string.moving, app.label), plasmaStyle(23f, 600), align = TextAlign.Center)
                PlasmaText(stringResource(R.string.moving_hint), plasmaStyle(20f, 500), color = PlasmaColors.Text2, align = TextAlign.Center)
            }
        }
    }
}

/** A white 40 px disc with a chevron: where Left and Right will carry the app. */
@Composable
private fun MoveArrow(icon: PlasmaIcons, description: String, modifier: Modifier) {
    Box(
        modifier
            .size(40.px)
            .boxShadow(PlasmaColors.black(0.5f), offsetY = 8.px, blur = 20.px, cornerRadius = 20.px)
            .background(PlasmaColors.Text, CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        PlasmaIcon(icon, 20.px, tint = PlasmaColors.OnFocus, stroke = 3f)
    }
}

@Composable
private fun SystemTileView(tile: SystemTile, onClick: () -> Unit, dimmed: Boolean) {
    val (icon, iconSize, label) = when (tile) {
        SystemTile.Inputs -> Triple(PlasmaIcons.Inputs, 40.px, stringResource(R.string.inputs))
        SystemTile.DeviceSettings -> Triple(PlasmaIcons.Settings, 38.px, stringResource(R.string.device_settings))
        SystemTile.GetMoreApps -> Triple(PlasmaIcons.GetMoreApps, 38.px, stringResource(R.string.get_more_apps))
    }
    PlasmaSurface(
        onClick = onClick,
        modifier = Modifier.size(TileWidth, TileHeight).alpha(if (dimmed) 0.6f else 1f),
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
