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

    /** Playback for This Source (brief 4.20), for the source the screen is given. */
    SourcePlayback,

    /** A profile's own page (brief 5.11), for the profile the screen is given. */
    Profile,
}

/** Screens a settings row leads to that aren't settings pages. */
enum class SettingsDestination {
    AddSource,
    DuplicateNumbers,
    ReplaceHome,
    Profiles,
    KeyTest,
    LearnKey,

    /** Parental Controls' PIN (5.12): choosing one, or a new one. */
    SetPin,
    ChangePin,

    /** Lock Settings without a PIN yet: choosing one, and then Settings is locked with it. */
    LockSettings,
}

/** What OK on a row does. */
internal sealed interface SettingAction {
    data object None : SettingAction

    /** Opens another page of Settings. */
    data class Open(val page: SettingsPage) : SettingAction

    /** Leaves Settings for another screen. */
    data class Go(val destination: SettingsDestination) : SettingAction

    /** Does something straight away, such as opening one of the TV's own settings. */
    class Run(val run: () -> Unit) : SettingAction

    /** Opens a small sheet on the right with the setting's options, [selected] checked (brief 4.27). */
    data class Choose(val key: String, val options: List<String>, val selected: Int, val note: String? = null) : SettingAction

    /** Flips a switch between On and Off. */
    data class Toggle(val key: String, val on: Boolean) : SettingAction

    /** Opens a sheet of things to do, such as a source's actions (brief 4.20). */
    class Menu(val title: String, val options: List<MenuOption>, val subtitle: String? = null) : SettingAction

    /**
     * Opens a sheet in the middle of the screen that explains something before it's done (brief
     * 4.35): [text], the [points] for and against, a [note] in small print, then [options].
     */
    class Explain(
        val title: String,
        val icon: PlasmaIcons,
        val text: String,
        val points: List<CheckItem>,
        val options: List<MenuOption>,
        val note: String? = null,
    ) : SettingAction

    /**
     * Opens a page to type the setting in with the on-screen keyboard (brief 4.21), one field or
     * more; Save hands [onSave] what's typed, trimmed but for passwords. [save] names the button
     * where it does more than save, and [note] beside it says what happens then.
     */
    class Type(
        val title: String,
        val fields: List<TypedField>,
        val text: String? = null,
        val save: String? = null,
        val note: String? = null,
        /** The keyboard's shortcut keys, and their widths in board pixels, in place of the web's `http://`, `.com`… */
        val shortcuts: List<Pair<String, Int>>? = null,
        val onSave: (List<String>) -> Unit,
    ) : SettingAction

    /**
     * Opens a sheet of its own over the page, such as Jellyfin's sign-in (brief 4.32): [content]
     * draws it and calls its argument to close it.
     */
    class Sheet(val content: @Composable (close: () -> Unit) -> Unit) : SettingAction
}

/**
 * A field on a [SettingAction.Type] page: what's there now, and what an empty one stands for. A
 * [secret] one, a password, shows dots unless it's being typed in.
 */
internal class TypedField(val label: String, val value: String, val hint: String? = null, val secret: Boolean = false)

/**
 * A choice in a [SettingAction.Menu]: it does [run], or opens [next], such as a confirmation, or
 * a [sheet] of its own, such as the network folder's setup.
 */
internal class MenuOption(
    val label: String,
    val next: SettingAction.Menu? = null,
    val value: String? = null,
    val sheet: SettingAction.Sheet? = null,
    val run: () -> Unit = {},
)

/** A channel's own sound, as Settings › Audio lists it (brief 4.28 and 5.4.6). */
@Immutable
data class ChannelSoundInfo(val channelId: String, val name: String, val levelDb: Int, val decoded: Boolean = false)

/** What can be done to a source from Portals & Playlists (brief 4.20). */
enum class SourceAction { Edit, RefreshNow, SetPrimary, Test, Playback, Disable, Enable, Delete }

/**
 * A source's own settings: Playback for This Source (brief 4.20) and its guide's time offset
 * (5.16). Their rows choose under "name:sourceId" keys, which the app keeps with the source rather
 * than with Settings' own choices.
 */
object SourceSettings {
    const val FORMAT = "source.format"
    const val BUFFER = "source.buffer"
    const val AUDIO = "source.audio"
    const val GUIDE_OFFSET = "source.guideOffset"
    const val CATCHUP_OFFSET = "source.catchupOffset"
    const val ENGINE = "source.engine"
    const val HUB = "source.hub"

    // Typed rather than chosen: what [typed] reads.
    const val USER_AGENT = "source.userAgent"
    const val HEADERS = "source.headers"
    const val UDPXY = "source.udpxy"

    /** The guide and catch-up offsets on offer, in hours. */
    val OFFSET_HOURS: List<Int> = (-12..12).toList()

    fun key(name: String, sourceId: String): String = "$name:$sourceId"

    /** The source [key] is for, or null for one of Settings' own. */
    fun sourceOf(key: String): String? = key.takeIf { it.startsWith(PREFIX) }?.substringAfter(':', "")?.takeIf { it.isNotEmpty() }

    /** [playback] with option [index] chosen on the row whose key is [key]. */
    fun chosen(playback: SourcePlaybackInfo, key: String, index: Int): SourcePlaybackInfo = when (key.substringBefore(':')) {
        FORMAT -> playback.copy(streamFormat = if (index == 1) HLS else TS)
        // The first option is "Same as Playback settings"; Settings' own options follow it.
        BUFFER -> playback.copy(bufferChoice = (index - 1).takeIf { it >= 0 })
        AUDIO -> playback.copy(audioOutputChoice = (index - 1).takeIf { it >= 0 })
        GUIDE_OFFSET -> playback.copy(epgOffsetMinutes = minutesOf(index))
        CATCHUP_OFFSET -> playback.copy(catchupOffsetMinutes = minutesOf(index))
        ENGINE -> playback.copy(engineChoice = (index - 1).takeIf { it >= 0 })
        // Same as Playback settings, On, Off.
        HUB -> playback.copy(useHub = if (index == 0) null else index == 1)
        else -> playback
    }

    /**
     * [playback] with [texts] typed on the row whose key is [key]: the user agent; the referrer,
     * then the extra headers; or the udpxy proxy. Empty is the default.
     */
    fun typed(playback: SourcePlaybackInfo, key: String, texts: List<String>): SourcePlaybackInfo {
        fun at(i: Int) = texts.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
        return when (key.substringBefore(':')) {
            USER_AGENT -> playback.copy(userAgent = at(0))
            HEADERS -> playback.copy(referrer = at(0), extraHeaders = at(1))
            UDPXY -> playback.copy(udpxy = at(0))
            else -> playback
        }
    }

    /** Use Stream Hub's option for [useHub]: Same as Playback settings, On or Off. */
    internal fun hubIndex(useHub: Boolean?): Int = when (useHub) {
        null -> 0
        true -> 1
        false -> 2
    }

    /** Which offset option [minutes] is; one that isn't on offer reads as none. */
    internal fun offsetIndex(minutes: Int): Int =
        OFFSET_HOURS.indexOf(minutes / MINUTES_PER_HOUR).takeIf { it >= 0 && minutes % MINUTES_PER_HOUR == 0 } ?: OFFSET_HOURS.indexOf(0)

    internal fun formatIndex(format: String): Int = if (format == HLS) 1 else 0

    private fun minutesOf(index: Int): Int = (OFFSET_HOURS.getOrNull(index) ?: 0) * MINUTES_PER_HOUR

    private const val PREFIX = "source."
    private const val MINUTES_PER_HOUR = 60
    private const val TS = "ts"
    private const val HLS = "m3u8"
}

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
