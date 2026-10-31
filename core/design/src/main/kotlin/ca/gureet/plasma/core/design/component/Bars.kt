// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.R
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

/** The top bar's destinations (brief 3.1). */
enum class TopTab { Search, Home, LiveTv, Guide, Movies, Series, Apps }

/**
 * The tabs the top bar shows: all of them, or all but Apps when Show Android Apps is off
 * (Settings › Launcher & Home, brief 5.13).
 */
val LocalTopTabs = staticCompositionLocalOf { TopTab.entries.toList() }

/**
 * Something in Settings waits for the user, such as an update to install (brief 5.18): the top
 * bar's Settings button shows a quiet dot.
 */
val LocalSettingsBadge = compositionLocalOf { false }

@Composable
private fun TopTab.label(): String = stringResource(
    when (this) {
        TopTab.Search -> R.string.tab_search
        TopTab.Home -> R.string.tab_home
        TopTab.LiveTv -> R.string.tab_live_tv
        TopTab.Guide -> R.string.tab_guide
        TopTab.Movies -> R.string.tab_movies
        TopTab.Series -> R.string.tab_series
        TopTab.Apps -> R.string.tab_apps
    },
)

/**
 * The pinned top bar on Home, Search, Live TV, Guide, Movies, Series and Apps (board 01):
 * wordmark in a 300 px slot, centred tabs, then Settings, the clock and the profile avatar.
 */
@Composable
fun TopBar(
    current: TopTab,
    profileInitial: String,
    profileName: String,
    onTab: (TopTab) -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
    currentTabFocusRequester: FocusRequester? = null,
    focusDown: FocusRequester? = null,
    avatarColor: Color = Color(0xFF3B404B),
    tabs: List<TopTab> = LocalTopTabs.current,
) {
    // Down from anywhere in the bar goes to [focusDown] (the Home hero) rather than whatever is below.
    val down = if (focusDown != null) Modifier.focusProperties { this.down = focusDown } else Modifier
    Row(
        modifier.padding(start = 96.px, top = 40.px).width(1728.px).height(72.px),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(300.px)) {
            PlasmaText(stringResource(R.string.wordmark), PlasmaType.Wordmark)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.px), verticalAlignment = Alignment.CenterVertically) {
            for (tab in tabs) {
                val selected = tab == current
                val requester = if (selected) currentTabFocusRequester else null
                val tabModifier = down.then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                if (tab == TopTab.Search) {
                    val search = tab.label()
                    PlasmaSurface(
                        onClick = { onTab(tab) },
                        modifier = tabModifier.size(56.px).semantics { contentDescription = search },
                        style = FocusStyle.Pill,
                        shape = PlasmaShapes.Tab,
                        selected = selected,
                        contentAlignment = Alignment.Center,
                    ) {
                        val tint = if (LocalFocused.current) PlasmaColors.OnFocus else if (selected) PlasmaColors.Text else PlasmaColors.Text2
                        PlasmaIcon(PlasmaIcons.Search, 26.px, tint = tint)
                    }
                } else {
                    PlasmaSurface(
                        onClick = { onTab(tab) },
                        modifier = tabModifier.height(56.px),
                        style = FocusStyle.Pill,
                        shape = PlasmaShapes.Tab,
                        selected = selected,
                        contentAlignment = Alignment.Center,
                    ) {
                        val focused = LocalFocused.current
                        PlasmaText(
                            tab.label(),
                            plasmaStyle(26f, if (selected || focused) 600 else 500),
                            Modifier.padding(horizontal = 22.px),
                            color = when {
                                focused -> PlasmaColors.OnFocus
                                selected -> PlasmaColors.Text
                                else -> PlasmaColors.Text2
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.width(300.px),
            horizontalArrangement = Arrangement.spacedBy(24.px, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val badge = LocalSettingsBadge.current
            val settings = if (badge) stringResource(R.string.settings_waiting) else stringResource(R.string.settings)
            PlasmaSurface(
                onClick = onSettings,
                modifier = down.size(48.px).semantics { contentDescription = settings },
                style = FocusStyle.Pill,
                shape = PlasmaShapes.Tab,
                contentAlignment = Alignment.Center,
            ) {
                val focused = LocalFocused.current
                PlasmaIcon(PlasmaIcons.Settings, 26.px, tint = if (focused) PlasmaColors.OnFocus else PlasmaColors.Text2)
                if (badge) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(top = 7.px, end = 7.px).size(10.px)
                            .background(if (focused) PlasmaColors.OkOnFocus else PlasmaColors.Ok, CircleShape),
                    )
                }
            }
            ClockText()
            val profile = stringResource(R.string.profile_named, profileName)
            PlasmaSurface(
                onClick = onProfile,
                modifier = down.size(46.px).semantics { contentDescription = profile },
                style = FocusStyle.Avatar,
                shape = CircleShape,
                color = avatarColor,
                contentAlignment = Alignment.Center,
            ) {
                PlasmaText(profileInitial, plasmaStyle(20f, 600), color = PlasmaColors.Text)
            }
        }
    }
}

/**
 * The header on Recordings, Catch-up, Settings pages, Add a Source and the PIN screen (brief 3.2):
 * "‹ Back target" on the left, the page title centred, the clock on the right. Back is the remote's
 * Back key, so the label isn't focusable. A [status] takes the clock's place, in the back label's
 * style: first-time setup's "Step 2 of 5" (#85).
 */
@Composable
fun SecondaryHeader(
    backLabel: String?,
    title: String?,
    modifier: Modifier = Modifier,
    showClock: Boolean = true,
    status: String? = null,
) {
    Row(
        modifier.padding(start = 96.px, top = 46.px).width(1728.px).height(56.px),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(300.px), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.px)) {
            if (backLabel != null) {
                PlasmaIcon(PlasmaIcons.ChevronLeft, 22.px, tint = PlasmaColors.Text2)
                PlasmaText(backLabel, plasmaStyle(22f, 500), color = PlasmaColors.Text2)
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (title != null) PlasmaText(title, PlasmaType.PageTitle, align = TextAlign.Center)
        }
        Box(Modifier.width(300.px), contentAlignment = Alignment.CenterEnd) {
            when {
                status != null -> PlasmaText(status, plasmaStyle(22f, 500, tabular = true), color = PlasmaColors.Text2)
                showClock -> ClockText()
            }
        }
    }
}

/**
 * The "‹ Movies" pill at the top left of a detail page (boards 14 and 16). Back does the same; the
 * pill is there for anyone who looks for it, so Up from the buttons reaches it.
 */
@Composable
fun BackPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.height(52.px),
        style = FocusStyle.Button,
        shape = PlasmaShapes.Button,
        color = PlasmaColors.white(0.1f),
        contentAlignment = Alignment.CenterStart,
    ) {
        val tint = if (LocalFocused.current) PlasmaColors.OnFocus else PlasmaColors.TextSoft2
        Row(
            Modifier.padding(start = 14.px, end = 20.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.px),
        ) {
            PlasmaIcon(PlasmaIcons.ChevronLeft, 22.px, tint = tint)
            PlasmaText(label, plasmaStyle(22f, 500), color = tint)
        }
    }
}
