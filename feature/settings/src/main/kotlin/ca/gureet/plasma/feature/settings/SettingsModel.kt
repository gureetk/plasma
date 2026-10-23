// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.px

/** The pages of Settings (brief 4.19 and 5.16). */
enum class SettingsPage {
    Main, Sources, Channels, Guide, Playback, Audio, Rewind, Recording, Parental, Profiles,
    Launcher, HomeScreen, ContinueWatching, Remote, PhoneRemote, Appearance, Network, About,
}

/** Screens a settings row leads to that aren't settings pages. */
enum class SettingsDestination { AddSource, DuplicateNumbers, ReplaceHome, Profiles, KeyTest, LearnKey }

/** What OK on a row does. */
internal sealed interface SettingAction {
    data object None : SettingAction

    /** Opens another page of Settings. */
    data class Open(val page: SettingsPage) : SettingAction

    /** Leaves Settings for another screen. */
    data class Go(val destination: SettingsDestination) : SettingAction

    /** Opens a small sheet on the right with the setting's options, [selected] checked (brief 4.27). */
    data class Choose(val key: String, val options: List<String>, val selected: Int, val note: String? = null) : SettingAction

    /** Flips a switch between On and Off. */
    data class Toggle(val key: String, val on: Boolean) : SettingAction

    /** Opens a sheet of things to do, such as a source's actions (brief 4.20). */
    class Menu(val title: String, val options: List<MenuOption>, val subtitle: String? = null) : SettingAction
}

/** A choice in a [SettingAction.Menu]: it does [run], or opens [next], such as a confirmation. */
internal class MenuOption(val label: String, val next: SettingAction.Menu? = null, val run: () -> Unit = {})

/** What can be done to a source from Portals & Playlists (brief 4.20); the rest of its actions come with M3. */
enum class SourceAction { RefreshNow, Delete }

/** What the detail panel shows while a row has focus. */
@Immutable
internal class SettingDetail(
    val icon: PlasmaIcons,
    val title: String,
    /** A line right under the title (a source's type, the audio output's effect). */
    val subline: String? = null,
    val status: Status? = null,
    /** The description. */
    val text: String? = null,
    /** Cards, choices and notes under the text. */
    val extra: (@Composable ColumnScope.() -> Unit)? = null,
)

@Immutable
internal class SettingItem(
    val id: String,
    val label: String,
    val value: String?,
    val detail: SettingDetail,
    val action: SettingAction = SettingAction.None,
    val subtitle: String? = null,
    val icon: PlasmaIcons? = null,
    val leadingIcon: PlasmaIcons? = null,
    val status: Tone? = null,
    val chevron: Boolean = action !is SettingAction.Toggle,
    val dimmed: Boolean = false,
    /** A label size of its own, for rows that stand out (sources on board 20). */
    val labelPx: Float? = null,
)

@Immutable
internal class SettingSection(val label: String?, val items: List<SettingItem>, val divider: Boolean = false)

/** A page of Settings: its header, how it's drawn, and its rows. */
@Immutable
internal class PageSpec(
    val title: String,
    val back: String,
    val style: PageStyle,
    val metrics: RowMetrics,
    /** Where the detail panel starts: the boards place it to sit level with the list. */
    val detailTop: Dp,
    val sections: List<SettingSection>,
    val footer: (@Composable () -> Unit)? = null,
    /** A card above the rows that isn't focusable ("This TV" on board 23). */
    val header: (@Composable () -> Unit)? = null,
    /**
     * Where the list stops. Rows past it scroll into view; the boards show none of them, so a
     * page's list ends just under its last drawn row (or above its footer).
     */
    val listBottom: Dp = 996.px,
) {
    val items: List<SettingItem> get() = sections.flatMap { it.items }
}

/**
 * The settings' current values: for each setting, the index of the chosen option, or for a switch
 * 0 for On and 1 for Off. Settings that haven't been changed keep their defaults. The app starts it
 * from what's stored and stores each change through [onChange]; previews and tests keep it in memory.
 */
@Stable
class SettingsValues(initial: Map<String, Int> = emptyMap(), private val onChange: (key: String, index: Int) -> Unit = { _, _ -> }) {
    private val chosen = mutableStateMapOf<String, Int>().apply { putAll(initial) }

    fun index(key: String, default: Int): Int = chosen[key] ?: default

    fun choose(key: String, index: Int) {
        if (chosen[key] == index) return
        chosen[key] = index
        onChange(key, index)
    }

    fun isOn(key: String, default: Boolean): Boolean = index(key, if (default) 0 else 1) == 0

    fun set(key: String, on: Boolean) = choose(key, if (on) 0 else 1)
}
