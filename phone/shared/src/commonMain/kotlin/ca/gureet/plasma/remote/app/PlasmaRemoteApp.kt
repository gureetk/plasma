// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import ca.gureet.plasma.remote.app.resources.coming_channels
import ca.gureet.plasma.remote.app.resources.coming_type
import ca.gureet.plasma.remote.app.resources.demo_tv_name
import ca.gureet.plasma.remote.app.resources.tab_channels
import ca.gureet.plasma.remote.app.resources.tab_remote
import ca.gureet.plasma.remote.app.resources.tab_type
import ca.gureet.plasma.remote.app.resources.wordmark
import ca.gureet.plasma.remote.app.tv.DemoTv
import ca.gureet.plasma.remote.app.tv.LinkStatus
import ca.gureet.plasma.remote.app.tv.TvLink
import ca.gureet.plasma.remote.app.ui.Icon
import ca.gureet.plasma.remote.app.ui.NumberPad
import ca.gureet.plasma.remote.app.ui.RemoteColors
import ca.gureet.plasma.remote.app.ui.RemoteIcons
import ca.gureet.plasma.remote.app.ui.RemoteTab
import ca.gureet.plasma.remote.app.ui.RemoteTheme
import ca.gureet.plasma.remote.app.ui.tap
import ca.gureet.plasma.remote.app.ui.textStyle
import org.jetbrains.compose.resources.stringResource

/** The app's three tabs (board 33). */
enum class Tab { Remote, Channels, Type }

/**
 * Plasma Remote (brief 5.22): the phone remote's three tabs for a TV. Until finding and pairing
 * TVs come, it opens on the demo TV.
 */
@Composable
fun PlasmaRemoteApp() {
    RemoteTheme {
        val name = stringResource(Res.string.demo_tv_name)
        val tv = remember(name) { DemoTv(name) }
        RemoteScreens(tv)
    }
}

/** The top bar, the tab that's open, and the tab bar, for [tv]. */
@Composable
fun RemoteScreens(tv: TvLink, initialTab: Tab = Tab.Remote) {
    var tab by remember { mutableStateOf(initialTab) }
    var numbers by remember { mutableStateOf(false) }
    val state by tv.state.collectAsState()
    val status by tv.status.collectAsState()
    Box(Modifier.fillMaxSize().background(RemoteColors.Bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(tv.name, status == LinkStatus.Connected)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp)) {
                    when (tab) {
                        Tab.Remote -> RemoteTab(tv, state.front, state.playing, state.keys, onNumbers = { numbers = true })
                        Tab.Channels -> Later(stringResource(Res.string.coming_channels))
                        Tab.Type -> Later(stringResource(Res.string.coming_type))
                    }
                }
            }
            TabBar(tab) { tab = it }
        }
        if (numbers && state.front) NumberPad(tv) { numbers = false }
    }
}

/** "plasma" and "● Living Room TV", the dot green while the TV answers. */
@Composable
private fun TopBar(name: String, connected: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.widthIn(max = 420.dp).fillMaxWidth().height(48.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(stringResource(Res.string.wordmark), style = textStyle(22.sp, FontWeight.SemiBold, letterSpacing = (-0.7).sp))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (connected) RemoteColors.Ok else RemoteColors.Text3))
            Spacer(Modifier.size(7.dp))
            BasicText(name, style = textStyle(13.sp, color = RemoteColors.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** A tab still to come. */
@Composable
private fun Later(text: String) {
    BasicText(text, Modifier.padding(horizontal = 8.dp, vertical = 24.dp), style = textStyle(14.sp, color = RemoteColors.Text3))
}

/** For the number pad's sheet. */
internal val SheetShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
