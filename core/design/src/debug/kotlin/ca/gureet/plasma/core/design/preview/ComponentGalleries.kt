// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocusPreview
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.BackPill
import ca.gureet.plasma.core.design.component.CaptionedTile
import ca.gureet.plasma.core.design.component.CenteredKeyFooter
import ca.gureet.plasma.core.design.component.ChoiceButton
import ca.gureet.plasma.core.design.component.ClockText
import ca.gureet.plasma.core.design.component.ColorKey
import ca.gureet.plasma.core.design.component.ColorKeyHint
import ca.gureet.plasma.core.design.component.CountedItem
import ca.gureet.plasma.core.design.component.CountedList
import ca.gureet.plasma.core.design.component.Divider
import ca.gureet.plasma.core.design.component.FooterKey
import ca.gureet.plasma.core.design.component.GraphiteFill
import ca.gureet.plasma.core.design.component.GraphiteIconTile
import ca.gureet.plasma.core.design.component.ImageBadge
import ca.gureet.plasma.core.design.component.KeyCap
import ca.gureet.plasma.core.design.component.KeyCapIcon
import ca.gureet.plasma.core.design.component.KeyFooter
import ca.gureet.plasma.core.design.component.KeyHint
import ca.gureet.plasma.core.design.component.LiveMark
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.component.LogoPlate
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.NumberEntryBox
import ca.gureet.plasma.core.design.component.NumberPad
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.component.PlasmaColumn
import ca.gureet.plasma.core.design.component.PlasmaGrid
import ca.gureet.plasma.core.design.component.PlasmaIconButton
import ca.gureet.plasma.core.design.component.PlasmaRow
import ca.gureet.plasma.core.design.component.PosterBadge
import ca.gureet.plasma.core.design.component.PosterTile
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.component.RedDot
import ca.gureet.plasma.core.design.component.SecondaryHeader
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.component.SheetRow
import ca.gureet.plasma.core.design.component.StatusDot
import ca.gureet.plasma.core.design.component.TabPill
import ca.gureet.plasma.core.design.component.TechBadge
import ca.gureet.plasma.core.design.component.TopBar
import ca.gureet.plasma.core.design.component.TopTab
import ca.gureet.plasma.core.design.component.WatchedBadge
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

// Every component in core/design, plain and focused, on 1920 × 1080 canvases. Like the interface
// kit, these are references rather than screens, so their words are written here.

/** Draws [content] as if it had focus (see [LocalFocusPreview]). */
@Composable
internal fun Focused(content: @Composable () -> Unit) = CompositionLocalProvider(LocalFocusPreview provides true, content = content)

/** A gallery's title at the top left. */
@Composable
private fun GalleryTitle(title: String) {
    PlasmaText(title, plasmaStyle(40f, 600, trackingPx = -0.8f), Modifier.offset(96.px, 54.px))
}

/** A labelled group of samples in a row. */
@Composable
private fun Samples(label: String, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.px)) {
        PlasmaText(label, plasmaStyle(21f, 600), color = PlasmaColors.Text3)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.px), content = content)
    }
}

/** A column of samples [gap] board pixels apart. */
@Composable
private fun Stack(modifier: Modifier = Modifier, gap: Int, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap.px), content = content)

// ---- Bars ----------------------------------------------------------------------------------------

/** The top bar, the secondary header and the footers (boards 01, 07, 10 and 19). */
@Composable
fun BarsGallery() {
    Box(Modifier.fillMaxSize()) {
        TopBar(TopTab.Home, profileInitial = "M", profileName = "Maya", onTab = {}, onSettings = {}, onProfile = {})
        Box(Modifier.offset(y = 220.px)) { SecondaryHeader(backLabel = "Settings", title = "Playback") }
        Samples("Back pill (detail pages)", Modifier.offset(96.px, 420.px)) {
            BackPill("Movies", onClick = {})
            Focused { BackPill("Movies", onClick = {}) }
        }
        Box(Modifier.offset(y = (-260).px)) {
            CenteredKeyFooter(
                keys = listOf(FooterKey("OK", "Select"), FooterKey(PlasmaIcons.Back, "Close")),
            )
        }
        KeyFooter(
            colorKeys = listOf(
                ColorKey.Red to "Favorite",
                ColorKey.Green to "Catch-up",
                ColorKey.Yellow to "Sort",
                ColorKey.Blue to "Multiview",
            ),
            keys = listOf(
                FooterKey("OK", "Watch"),
                FooterKey(PlasmaIcons.KeyLeft, "Groups", iconSize = 13.px),
                FooterKey(PlasmaIcons.Menu, "Options"),
                FooterKey("0–9", "Go to channel"),
            ),
        )
    }
}

@TvPreview
@Composable
private fun BarsGalleryPreview() = PreviewCanvas { BarsGallery() }

// ---- Buttons -------------------------------------------------------------------------------------

/** Buttons, icon buttons, choices and tabs, each plain and focused. */
@Composable
fun ButtonsGallery() {
    Box(Modifier.fillMaxSize()) {
        GalleryTitle("Buttons")
        Stack(Modifier.offset(96.px, 170.px), gap = 56) {
            Samples("PlasmaButton") {
                PlasmaButton("Watch Live", onClick = {}, icon = PlasmaIcons.Play)
                Focused { PlasmaButton("Watch Live", onClick = {}, icon = PlasmaIcons.Play) }
                PlasmaButton("Trailer", onClick = {})
                Focused { PlasmaButton("Trailer", onClick = {}) }
            }
            Samples("PlasmaIconButton") {
                PlasmaIconButton(PlasmaIcons.Plus, "Add to My List", onClick = {})
                Focused { PlasmaIconButton(PlasmaIcons.Plus, "Add to My List", onClick = {}) }
                PlasmaIconButton(PlasmaIcons.Multiview, "Multiview", onClick = {})
                Focused { PlasmaIconButton(PlasmaIcons.Multiview, "Multiview", onClick = {}) }
            }
            Samples("ChoiceButton") {
                ChoiceButton("Keep 1108", onClick = {})
                Focused { ChoiceButton("Keep 1108", onClick = {}) }
            }
            Samples("TabPill") {
                TabPill("Today", selected = true, onClick = {})
                TabPill("Thu 22", selected = false, onClick = {})
                Focused { TabPill("Fri 23", selected = false, onClick = {}) }
            }
        }
    }
}

@TvPreview
@Composable
private fun ButtonsGalleryPreview() = PreviewCanvas { ButtonsGallery() }

// ---- Badges and hints ----------------------------------------------------------------------------

/** Marks, badges, status dots, progress bars, key hints, logo plates and the clock (brief 2.9). */
@Composable
fun BadgesGallery() {
    Box(Modifier.fillMaxSize()) {
        GalleryTitle("Badges and hints")
        Stack(Modifier.offset(96.px, 170.px), gap = 56) {
            Samples("Marks and badges") {
                LiveMark("LIVE")
                LiveMark("REC")
                ImageBadge("LIVE", leading = { RedDot() })
                ImageBadge("CATCH-UP")
                WatchedBadge()
                PosterBadge("NEW EPISODE")
                for (badge in listOf("4K", "HDR", "HD", "5.1", "CC", "50 FPS", "13+")) TechBadge(badge)
            }
            Samples("Status and progress") {
                StatusDot(PlasmaColors.Ok)
                StatusDot(PlasmaColors.Warn)
                StatusDot(PlasmaColors.Text4)
                StatusDot(PlasmaColors.Live)
                ProgressBar(0.42f, Modifier.width(300.px))
                ProgressBar(0.7f, Modifier.width(300.px), color = PlasmaColors.Live)
                ProgressBar(0.25f, Modifier.width(160.px), height = 3.px, track = PlasmaColors.white(0.14f))
            }
            Samples("Key hints") {
                KeyCap("OK")
                KeyCap("0–9")
                KeyCapIcon { PlasmaIcon(PlasmaIcons.Menu, 15.px, tint = PlasmaColors.TextSoft2) }
                KeyHint("OK", "Watch")
                KeyHint(PlasmaIcons.Back, "Close")
                ColorKeyHint(ColorKey.Red, "Favorite")
                ColorKeyHint(ColorKey.Yellow, "Sort")
            }
            Samples("Logos (without art, the channel's name)") {
                LogoPlate(null, "Arena Sports 1", 150.px, 66.px)
                LogoPlate(null, "Velocity Motor", 104.px, 52.px, color = PlasmaColors.Surface3)
                LogoImage(null, "Northline News", LogoSize.Player)
            }
            Samples("Clock and divider") {
                ClockText()
                Divider(Modifier.width(400.px))
            }
        }
    }
}

@TvPreview
@Composable
private fun BadgesGalleryPreview() = PreviewCanvas { BadgesGallery() }

// ---- Tiles ---------------------------------------------------------------------------------------

/** Posters, captioned tiles and graphite tiles, plain and focused, in a [PlasmaRow]. */
@Composable
fun TilesGallery() {
    Box(Modifier.fillMaxSize()) {
        GalleryTitle("Tiles")
        PlasmaText("PosterTile and CaptionedTile in a PlasmaRow", plasmaStyle(21f, 600), Modifier.offset(96.px, 170.px), color = PlasmaColors.Text3)
        PlasmaRow(gap = 40.px, modifier = Modifier.offset(y = 240.px).size(1920.px, 460.px), verticalAlignment = Alignment.Top) {
            item { PosterTile(null, "The Last Lighthouse", "2026 · Drama", onClick = {}, badge = "NEW", progress = 0.3f) }
            item { Focused { PosterTile(null, "The Last Lighthouse", "2026 · Drama · 2 h 04 min", onClick = {}) } }
            item {
                CaptionedTile("Harbour City v Redmoor", "Arena Sports 1 · 1 h 19 min left", onClick = {}) {
                    LogoImage(null, "Arena Sports 1", LogoSize.Card, Modifier.align(Alignment.Center))
                }
            }
            item {
                Focused {
                    CaptionedTile("Harbour City v Redmoor", "Arena Sports 1 · 1 h 19 min left", onClick = {}) {
                        LogoImage(null, "Arena Sports 1", LogoSize.Card, Modifier.align(Alignment.Center))
                    }
                }
            }
        }
        Samples("Graphite tiles: the dock (plain and focused), Settings, its pages and the PIN screen", Modifier.offset(96.px, 720.px)) {
            DockTile()
            Focused { DockTile() }
            GraphiteIconTile(PlasmaIcons.Settings, 220.px, 48.px, 112.px, stroke = 1.2f)
            GraphiteIconTile(PlasmaIcons.Hash, 180.px, 40.px, 92.px, stroke = 1.3f)
            GraphiteIconTile(PlasmaIcons.PinLock, 120.px, 30.px, 54.px, stroke = 1.6f)
        }
    }
}

@Composable
private fun DockTile() {
    PlasmaSurface(
        onClick = {},
        modifier = Modifier.size(150.px, 90.px),
        style = FocusStyle.Tile,
        shape = PlasmaShapes.Button,
        contentAlignment = Alignment.Center,
    ) {
        GraphiteFill()
        PlasmaIcon(PlasmaIcons.LiveTv, 34.px, tint = if (LocalFocused.current) PlasmaColors.white(1f) else PlasmaColors.TextSoft2)
    }
}

@TvPreview
@Composable
private fun TilesGalleryPreview() = PreviewCanvas { TilesGallery() }

// ---- Lists ---------------------------------------------------------------------------------------

private val Groups = listOf(
    CountedItem("all", "All Channels", 1_284, smart = true),
    CountedItem("favorites", "Favorites", 24, smart = true),
    CountedItem("recent", "Recently Watched", 18, smart = true),
    CountedItem("sports", "Sports", 186),
    CountedItem("news", "News", 92),
    CountedItem("movies", "Movies", 140),
    CountedItem("entertainment", "Entertainment", 212),
    CountedItem("documentary", "Documentary", 58),
    CountedItem("kids", "Kids", 64),
    CountedItem("music", "Music", 41),
    CountedItem("uhd", "4K UHD", 36),
)

/** The groups column, sheet rows in a [PlasmaColumn], and a [PlasmaGrid]. */
@Composable
fun ListsGallery() {
    Box(Modifier.fillMaxSize()) {
        GalleryTitle("Lists")
        CountedList(Groups, selectedId = "sports", onFocusItem = {}, onOpenItem = {}, Modifier.offset(96.px, 148.px).size(300.px, 828.px))
        PlasmaColumn(Modifier.offset(456.px, 148.px).size(600.px, 420.px), gap = 4.px) {
            item { SheetRow("Audio", "English 5.1", onClick = {}, icon = { PlasmaIcon(PlasmaIcons.Speaker, 24.px, tint = PlasmaColors.TextSoft) }) }
            item { Focused { SheetRow("Subtitles", "Off", onClick = {}, icon = { PlasmaIcon(PlasmaIcons.Subtitles, 24.px, tint = PlasmaColors.OnFocus) }) } }
            item { SheetRow("Auto", null, onClick = {}, checked = true) }
            item { SheetRow("Passthrough", null, onClick = {}) }
        }
        PlasmaGrid(columns = 4, columnGap = 26.px, rowGap = 26.px, modifier = Modifier.offset(1116.px, 148.px).size(708.px, 420.px)) {
            items(listOf(PlasmaIcons.LiveTv, PlasmaIcons.Guide, PlasmaIcons.Movies, PlasmaIcons.Series, PlasmaIcons.Multiview, PlasmaIcons.Search)) {
                PlasmaSurface(onClick = {}, Modifier.size(150.px, 90.px), style = FocusStyle.Tile, shape = PlasmaShapes.Button, contentAlignment = Alignment.Center) {
                    GraphiteFill()
                    PlasmaIcon(it, 34.px, tint = PlasmaColors.TextSoft2)
                }
            }
        }
    }
}

@TvPreview
@Composable
private fun ListsGalleryPreview() = PreviewCanvas { ListsGallery() }

// ---- Sheets --------------------------------------------------------------------------------------

/** The options sheet that Menu and long-press OK open (board 10's style), with a logo beside the title. */
@Composable
fun OptionsSheetGallery() {
    Box(Modifier.fillMaxSize()) {
        GalleryTitle("Options sheet")
        OptionsSheet(
            title = "Arena Sports 1",
            subtitle = "105 · Harbour City v Redmoor",
            onDismiss = {},
            leading = { LogoPlate(null, "Arena Sports 1", 150.px, 66.px) },
            options = listOf(
                SheetOption("Remove from Favorites", {}, icon = PlasmaIcons.Star),
                SheetOption("Hide Channel", {}, icon = PlasmaIcons.Hide),
                SheetOption("Lock Channel", {}, icon = PlasmaIcons.Lock),
                SheetOption.Divider,
                SheetOption("Switch Source", {}, value = "Home Portal", icon = PlasmaIcons.SwitchSource),
                SheetOption("Channel Info", {}, icon = PlasmaIcons.Info),
            ),
        )
    }
}

@TvPreview
@Composable
private fun OptionsSheetGalleryPreview() = PreviewCanvas { OptionsSheetGallery() }

/** A setting's choices: a narrower sheet on the right with the current one ticked (brief 4.27). */
@Composable
fun SettingSheetGallery() {
    OptionsSheet(
        title = "Clock",
        onDismiss = {},
        width = 600.px,
        alignment = Alignment.CenterEnd,
        options = listOf(
            SheetOption("24-Hour", {}, checked = true),
            SheetOption("12-Hour", {}),
        ),
    )
}

@TvPreview
@Composable
private fun SettingSheetGalleryPreview() = PreviewCanvas { SettingSheetGallery() }

// ---- Numbers -------------------------------------------------------------------------------------

/** The number pad (board 25) and the box typed channel numbers collect in (board 11). */
@Composable
fun NumbersGallery() {
    Box(Modifier.fillMaxSize()) {
        GalleryTitle("Numbers")
        Samples("NumberPad", Modifier.offset(96.px, 170.px)) {
            NumberPad(onDigit = {}, onDelete = {})
        }
        Stack(Modifier.offset(96.px, 420.px), gap = 28) {
            PlasmaText("NumberEntryBox", plasmaStyle(21f, 600), color = PlasmaColors.Text3)
            NumberEntryBox("107", null)
            NumberEntryBox("10_", "Velocity Motor")
            NumberEntryBox("2_", "Favorites 2 · Velocity Motor")
            NumberEntryBox("999", "No channel 999")
        }
    }
}

@TvPreview
@Composable
private fun NumbersGalleryPreview() = PreviewCanvas { NumbersGallery() }

// ---- Icons ---------------------------------------------------------------------------------------

/** Every icon in [PlasmaIcons] at 40 px, with its name. */
@Composable
fun IconsGallery() {
    Box(Modifier.fillMaxSize()) {
        GalleryTitle("Icons")
        val columns = 12
        Stack(Modifier.offset(96.px, 150.px), gap = 14) {
            for (row in PlasmaIcons.entries.chunked(columns)) {
                Row {
                    for (icon in row) {
                        Column(
                            Modifier.width(144.px),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.px),
                        ) {
                            PlasmaIcon(icon, 40.px, tint = PlasmaColors.Text)
                            PlasmaText(icon.name, plasmaStyle(15f, 500), color = PlasmaColors.Text3, align = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@TvPreview
@Composable
private fun IconsGalleryPreview() = PreviewCanvas { IconsGallery() }
