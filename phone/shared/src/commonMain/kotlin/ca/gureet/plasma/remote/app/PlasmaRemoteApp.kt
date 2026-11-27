// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.plasma.remote.app.resources.Res
import ca.gureet.plasma.remote.app.resources.demo_movies
import ca.gureet.plasma.remote.app.resources.demo_one_movie
import ca.gureet.plasma.remote.app.resources.demo_one_program
import ca.gureet.plasma.remote.app.resources.demo_programs
import ca.gureet.plasma.remote.app.resources.demo_tv_name
import ca.gureet.plasma.remote.app.resources.tab_channels
import ca.gureet.plasma.remote.app.resources.tab_remote
import ca.gureet.plasma.remote.app.resources.tab_type
import ca.gureet.plasma.remote.app.resources.forgotten
import ca.gureet.plasma.remote.app.resources.settings
import ca.gureet.plasma.remote.app.settings.LocalPhoneSettings
import ca.gureet.plasma.remote.app.settings.PhoneSettings
import ca.gureet.plasma.remote.app.settings.SettingsScreen
import ca.gureet.plasma.remote.app.ui.KeepScreenOn
import ca.gureet.plasma.remote.app.resources.needs_update
import ca.gureet.plasma.remote.app.resources.not_paired_now
import ca.gureet.plasma.remote.app.resources.unreachable
import ca.gureet.plasma.remote.app.resources.wordmark
import ca.gureet.plasma.remote.app.pairing.FindTvScreen
import ca.gureet.plasma.remote.app.pairing.PairScreen
import ca.gureet.plasma.remote.app.pairing.PairTarget
import ca.gureet.plasma.remote.app.pairing.ScanScreen
import ca.gureet.plasma.remote.app.pairing.target
import ca.gureet.plasma.remote.app.tv.FoundTv
import ca.gureet.plasma.remote.app.tv.PairedTv
import ca.gureet.plasma.remote.app.tv.PairedTvs
import ca.gureet.plasma.remote.app.tv.DemoTv
import ca.gureet.plasma.remote.app.tv.LinkStatus
import ca.gureet.plasma.remote.app.tv.NetworkTv
import ca.gureet.plasma.remote.app.tv.TvLink
import ca.gureet.plasma.remote.app.ui.ChannelsTab
import ca.gureet.plasma.remote.app.ui.Icon
import ca.gureet.plasma.remote.app.ui.LocalLogos
import ca.gureet.plasma.remote.app.ui.Logos
import ca.gureet.plasma.remote.app.ui.NumberPad
import ca.gureet.plasma.remote.app.ui.RemoteColors
import ca.gureet.plasma.remote.app.ui.RemoteIcons
import ca.gureet.plasma.remote.app.ui.RemoteTab
import ca.gureet.plasma.remote.app.ui.RemoteTheme
import ca.gureet.plasma.remote.app.ui.SystemBack
import ca.gureet.plasma.remote.app.ui.TypeTab
import ca.gureet.plasma.remote.app.ui.tap
import ca.gureet.plasma.remote.app.ui.textStyle
import ca.gureet.plasma.remote.protocol.RemoteProtocol
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import org.jetbrains.compose.resources.stringResource

/** The app's three tabs (board 33). */
enum class Tab { Remote, Channels, Type }

/** Where the app is: finding a TV, scanning its code, pairing with it, on a paired TV, or on the demo TV. */
sealed interface AppScreen {
    data class Find(val note: String? = null) : AppScreen

    data object Scan : AppScreen

    data class Pair(val target: PairTarget) : AppScreen

    data class OnTv(val id: String) : AppScreen

    data object Demo : AppScreen

    data object Settings : AppScreen
}

/**
 * Plasma Remote (brief 5.22). It opens on the TV it was last on, or on Find your TV the first
 * time. TVs are looked for on the Wi-Fi the whole time the app is open, and a paired TV found at
 * a new address is followed there. Tapping the TV's name in the top bar goes to Find your TV, to
 * pair another TV, go to one or try the demo; Android's Back goes back from there.
 */
@Composable
fun PlasmaRemoteApp(phone: Phone) {
    val settings = remember(phone) { PhoneSettings(phone.prefs) }
    RemoteTheme {
        CompositionLocalProvider(LocalPhoneSettings provides settings) { AppScreens(phone, settings) }
    }
}

/** The screens, one at a time, with Android's Back going back through them. */
@Composable
private fun AppScreens(phone: Phone, settings: PhoneSettings) {
    val pairedTvs = remember(phone) { PairedTvs(phone.vault) }
    var paired by remember { mutableStateOf(pairedTvs.all()) }
    var found by remember { mutableStateOf<List<FoundTv>>(emptyList()) }
    var screens by remember { mutableStateOf(listOf(paired.lastOrNull()?.let { AppScreen.OnTv(it.id) } ?: AppScreen.Find())) }
    fun open(screen: AppScreen) {
        screens = screens + screen
    }
    fun only(screen: AppScreen) {
        screens = listOf(screen)
    }
    fun goTo(tv: PairedTv) {
        pairedTvs.use(tv.id)
        paired = pairedTvs.all()
        only(AppScreen.OnTv(tv.id))
    }
    LaunchedEffect(phone) {
        phone.finder.tvs().collect { now ->
            found = now
            if (pairedTvs.follow(now)) paired = pairedTvs.all()
        }
    }
    SystemBack(enabled = screens.size > 1) { screens = screens.dropLast(1) }
    val openSettings = { open(AppScreen.Settings) }
    when (val at = screens.last()) {
        is AppScreen.Find -> AppFrame(null, connected = false, onSettings = openSettings) {
            FindTvScreen(paired, found, at.note, onPaired = ::goTo, onPair = { open(AppScreen.Pair(it)) }, onScan = { open(AppScreen.Scan) }, onDemo = { open(AppScreen.Demo) })
        }
        AppScreen.Scan -> ScanScreen(onInvite = { invite ->
            // Pairing takes the scanner's place: Cancel and Back go to Find your TV.
            invite.target(found)?.let { screens = screens.dropLast(1) + AppScreen.Pair(it) }
        }, onCancel = { screens = screens.dropLast(1) })
        is AppScreen.Pair -> AppFrame(null, connected = false) {
            PairScreen(at.target, phone.deviceName, phone.app, onPaired = { tv ->
                pairedTvs.save(tv)
                goTo(tv)
            }, onCancel = { screens = screens.dropLast(1) })
        }
        AppScreen.Demo -> RemoteScreens(rememberDemoTv(), onTv = { open(AppScreen.Find()) }, onSettings = openSettings)
        AppScreen.Settings -> AppFrame(null, connected = false) {
            SettingsScreen(settings, paired.asReversed(), phone.app.version, onForget = { tv ->
                pairedTvs.remove(tv.id)
                paired = pairedTvs.all()
            }, onDemo = { open(AppScreen.Demo) }, onDone = { screens = screens.dropLast(1) })
        }
        is AppScreen.OnTv -> {
            val tv = paired.firstOrNull { it.id == at.id }
            if (tv == null) {
                LaunchedEffect(at) { only(AppScreen.Find()) }
            } else {
                OnTv(tv, phone, onEnded = { forgotten, note ->
                    if (forgotten) {
                        pairedTvs.remove(tv.id)
                        paired = pairedTvs.all()
                    }
                    only(AppScreen.Find(note))
                }, onTv = { open(AppScreen.Find()) }, onSettings = openSettings)
            }
        }
    }
}

/**
 * A paired TV's tabs, connected while they're shown and the app is in front (brief 5.22), at
 * wherever the TV was last found. The TV no longer knowing the phone ends it with [onEnded] (true:
 * forget the TV). So does a protocol one of them needs updating for, and a certificate other than
 * the pinned one (false for both: the TV stays paired; its token is still good, or the TV at that
 * address may be another for now).
 */
@Composable
private fun OnTv(tv: PairedTv, phone: Phone, onEnded: (forgotten: Boolean, note: String) -> Unit, onTv: () -> Unit, onSettings: () -> Unit) {
    val where by rememberUpdatedState(tv.endpoint)
    val forgotten = stringResource(Res.string.forgotten, tv.name)
    val needsUpdate = stringResource(Res.string.needs_update)
    val notPaired = stringResource(Res.string.not_paired_now, tv.name)
    val end by rememberUpdatedState(onEnded)
    val link = remember(tv.id, tv.fingerprint, tv.token) {
        tv.link(phone.app, where = { where }) { reason ->
            when (reason) {
                RemoteProtocol.Reasons.VERSION -> end(false, needsUpdate)
                NetworkTv.CERTIFICATE -> end(false, notPaired)
                else -> end(true, forgotten)
            }
        }
    }
    // Connected only while the app is in front: going back to it connects again at once.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(link, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { link.keepConnected(this) }
    }
    RemoteScreens(link, onTv = onTv, onSettings = onSettings)
}

/** The app's frame: its background, the top bar with "plasma" and the TV (tapping it calls [onTv]), and [content] under it. */
@Composable
fun AppFrame(tvName: String?, connected: Boolean, onTv: (() -> Unit)? = null, onSettings: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(RemoteColors.Bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(tvName, connected, onTv, onSettings)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), content = content)
            }
        }
    }
}

/** The demo TV (brief 5.22), in the app's words. */
@Composable
fun rememberDemoTv(name: String = stringResource(Res.string.demo_tv_name)): DemoTv {
    val oneProgram = stringResource(Res.string.demo_one_program)
    val programs = stringResource(Res.string.demo_programs)
    val oneMovie = stringResource(Res.string.demo_one_movie)
    val movies = stringResource(Res.string.demo_movies)
    return remember(name) {
        DemoTv(name) { programCount, movieCount ->
            listOfNotNull(
                when (programCount) { 0 -> null; 1 -> oneProgram; else -> programs.replace("%1\$d", programCount.toString()) },
                when (movieCount) { 0 -> null; 1 -> oneMovie; else -> movies.replace("%1\$d", movieCount.toString()) },
            ).joinToString(" · ").ifEmpty { null }
        }
    }
}

/** The top bar, the tab that's open, and the tab bar, for [tv]. */
@Composable
fun RemoteScreens(tv: TvLink, initialTab: Tab = Tab.Remote, onTv: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    var tab by remember { mutableStateOf(initialTab) }
    var numbers by remember { mutableStateOf(false) }
    val state by tv.state.collectAsState()
    val status by tv.status.collectAsState()
    val logos = remember(tv) { Logos(tv) }

    // A text field got focus on the TV: the Type tab opens by itself and puts the keyboard up.
    // One there already when the app opens doesn't move it.
    val hasField = state.field != null
    var hadField by remember(tv) { mutableStateOf(hasField) }
    var typeOpenedItself by remember { mutableStateOf(false) }
    LaunchedEffect(hasField) {
        if (hasField && !hadField) {
            tab = Tab.Type
            typeOpenedItself = true
        }
        hadField = hasField
    }

    CompositionLocalProvider(LocalLogos provides logos) {
        Box(Modifier.fillMaxSize().background(RemoteColors.Bg)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                TopBar(tv.name, status == LinkStatus.Connected, onTv, onSettings)
                if (status == LinkStatus.Unreachable) Unreachable(tv.name)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                        if (tab == Tab.Remote && LocalPhoneSettings.current?.keepScreenOn == true) KeepScreenOn()
                        when (tab) {
                            Tab.Remote -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp)) {
                                RemoteTab(tv, state.front, state.playing, state.keys, onNumbers = { numbers = true })
                            }
                            Tab.Channels -> ChannelsTab(tv, state.playing?.channelId)
                            Tab.Type -> TypeTab(tv, state.front, state.field, focusNow = typeOpenedItself)
                        }
                    }
                }
                TabBar(tab) {
                    tab = it
                    typeOpenedItself = false
                }
            }
            if (numbers && state.front) NumberPad(tv) { numbers = false }
        }
    }
}

/** "plasma" and "● Living Room TV", the dot green while the TV answers; no TV on Find your TV and pairing. */
@Composable
private fun TopBar(name: String?, connected: Boolean, onTv: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.widthIn(max = 420.dp).fillMaxWidth().height(48.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(stringResource(Res.string.wordmark), style = textStyle(22.sp, FontWeight.SemiBold, letterSpacing = (-0.7).sp))
            Spacer(Modifier.weight(1f))
            if (name != null) {
                Row(
                    (if (onTv != null) Modifier.tap(name, action = onTv) else Modifier).heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (connected) RemoteColors.Ok else RemoteColors.Text3))
                    Spacer(Modifier.size(7.dp))
                    BasicText(name, style = textStyle(13.sp, color = RemoteColors.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (onSettings != null) {
                val label = stringResource(Res.string.settings)
                // The icon's edge lines up with the row's 20 dp margin; its 40 dp target reaches past it.
                Box(Modifier.padding(start = 6.dp).offset(x = 10.dp).size(40.dp).clip(CircleShape).tap(label, action = onSettings), contentAlignment = Alignment.Center) {
                    Icon(RemoteIcons.Settings, 20.dp, RemoteColors.Text2)
                }
            }
        }
    }
}

/** "Can't reach Living Room TV. Open Plasma on the TV.", the web page's words, while it can't. */
@Composable
private fun Unreachable(name: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(RemoteColors.Surface1).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(RemoteColors.Warn))
            BasicText(stringResource(Res.string.unreachable, name), style = textStyle(13.sp, color = RemoteColors.Text2))
        }
    }
}

@Composable
private fun TabBar(open: Tab, onTab: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(RemoteColors.Bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(RemoteColors.Line))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
                TabButton(Tab.Remote, RemoteIcons.TabRemote, stringResource(Res.string.tab_remote), open, onTab)
                TabButton(Tab.Channels, RemoteIcons.TabChannels, stringResource(Res.string.tab_channels), open, onTab)
                TabButton(Tab.Type, RemoteIcons.TabType, stringResource(Res.string.tab_type), open, onTab)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabButton(tab: Tab, icon: ImageVector, label: String, open: Tab, onTab: (Tab) -> Unit) {
    val on = tab == open
    val color = if (on) RemoteColors.Text else RemoteColors.Text3
    Column(
        Modifier.weight(1f).heightIn(min = 48.dp).tap(label) { onTab(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, 22.dp, color)
        BasicText(label, style = textStyle(12.sp, if (on) FontWeight.SemiBold else FontWeight.Medium, color))
    }
}

/** For the number pad's sheet. */
internal val SheetShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
