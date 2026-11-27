// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.plasma.remote.app.resources.Res
import ca.gureet.plasma.remote.app.resources.behind_live
import ca.gureet.plasma.remote.app.resources.go_live
import ca.gureet.plasma.remote.app.resources.key_back
import ca.gureet.plasma.remote.app.resources.key_blue
import ca.gureet.plasma.remote.app.resources.key_channel
import ca.gureet.plasma.remote.app.resources.key_channel_down
import ca.gureet.plasma.remote.app.resources.key_channel_up
import ca.gureet.plasma.remote.app.resources.key_down
import ca.gureet.plasma.remote.app.resources.key_fast_forward
import ca.gureet.plasma.remote.app.resources.key_green
import ca.gureet.plasma.remote.app.resources.key_home
import ca.gureet.plasma.remote.app.resources.key_left
import ca.gureet.plasma.remote.app.resources.key_menu
import ca.gureet.plasma.remote.app.resources.key_numbers
import ca.gureet.plasma.remote.app.resources.key_numbers_short
import ca.gureet.plasma.remote.app.resources.key_ok
import ca.gureet.plasma.remote.app.resources.key_play_pause
import ca.gureet.plasma.remote.app.resources.key_red
import ca.gureet.plasma.remote.app.resources.key_rewind
import ca.gureet.plasma.remote.app.resources.key_right
import ca.gureet.plasma.remote.app.resources.key_up
import ca.gureet.plasma.remote.app.resources.key_yellow
import ca.gureet.plasma.remote.app.tv.TvLink
import ca.gureet.plasma.remote.protocol.RemoteKey
import ca.gureet.plasma.remote.protocol.RemotePlaying
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The Remote tab (board 33, brief 4.33): what plays with its timeline and Go Live, the D-pad, the
 * round buttons, the media and channel keys, and the colour keys labelled with what they do on
 * the TV's screen now. What only works in Plasma is dimmed while another app is in front.
 */
@Composable
fun RemoteTab(tv: TvLink, front: Boolean, playing: RemotePlaying?, colorKeys: Map<String, String>, onNumbers: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (playing != null) {
            NowPlaying(tv, playing, Modifier.alpha(if (front) 1f else AWAY_ALPHA))
        }
        DPad(tv, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundKey(tv, RemoteKey.Back, RemoteIcons.Back, stringResource(Res.string.key_back))
            RoundKey(tv, RemoteKey.Home, RemoteIcons.Home, stringResource(Res.string.key_home))
            RoundKey(tv, RemoteKey.Menu, RemoteIcons.Menu, stringResource(Res.string.key_menu))
            NumbersKey(stringResource(Res.string.key_numbers), onNumbers)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).alpha(if (front) 1f else AWAY_ALPHA), horizontalArrangement = Arrangement.SpaceBetween) {
            TileKey(tv, RemoteKey.Rewind, RemoteIcons.Rewind, stringResource(Res.string.key_rewind), front)
            TileKey(tv, RemoteKey.PlayPause, if (playing?.paused == true) RemoteIcons.Play else RemoteIcons.Pause, stringResource(Res.string.key_play_pause), front, iconSize = 20.dp)
            TileKey(tv, RemoteKey.FastForward, RemoteIcons.FastForward, stringResource(Res.string.key_fast_forward), front)
            ChannelKeys(tv, front)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).alpha(if (front) 1f else AWAY_ALPHA), horizontalArrangement = Arrangement.SpaceBetween) {
            ColorKey(tv, RemoteKey.Red, RemoteColors.Red, colorKeys["red"] ?: stringResource(Res.string.key_red), front)
            ColorKey(tv, RemoteKey.Green, RemoteColors.Green, colorKeys["green"] ?: stringResource(Res.string.key_green), front)
            ColorKey(tv, RemoteKey.Yellow, RemoteColors.Yellow, colorKeys["yellow"] ?: stringResource(Res.string.key_yellow), front)
            ColorKey(tv, RemoteKey.Blue, RemoteColors.Blue, colorKeys["blue"] ?: stringResource(Res.string.key_blue), front)
        }
    }
}

/** "105 · Arena Sports 1", the title, the timeline with the live edge, and how far behind live. */
@Composable
private fun NowPlaying(tv: TvLink, playing: RemotePlaying, modifier: Modifier = Modifier) {
    // Paused, the picture falls further behind live by itself until the TV says otherwise.
    var pausedFor by remember(playing) { mutableLongStateOf(0L) }
    LaunchedEffect(playing) {
        val since = TimeSource.Monotonic.markNow()
        while (playing.paused) {
            delay(1_000)
            pausedFor = since.elapsedNow().inWholeSeconds
        }
    }
    val behind = playing.behindLive + pausedFor
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(RemoteColors.Surface1).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoPlate(Modifier.size(72.dp, 36.dp))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText("${playing.number} · ${playing.name}", style = textStyle(13.sp, color = RemoteColors.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(playing.title.orEmpty(), style = textStyle(17.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Timeline(picture = playing.progress ?: playing.live ?: 0f, live = playing.live, modifier = Modifier.padding(top = 14.dp))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 30.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText(if (behind > 0) stringResource(Res.string.behind_live, clock(behind)) else "", style = textStyle(13.sp, color = RemoteColors.Text2))
            if (behind > 0) {
                val label = stringResource(Res.string.go_live)
                Row(
                    Modifier.height(30.dp).clip(RoundedCornerShape(9.dp)).background(RemoteColors.Button).tap(label) { tv.goLive() }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(RemoteColors.Live))
                    BasicText(label, style = textStyle(13.sp, FontWeight.SemiBold))
                }
            }
        }
    }
}

/** The plate a channel's logo sits on. Logos come with the connection to the TV. */
@Composable
fun LogoPlate(modifier: Modifier = Modifier, color: Color = RemoteColors.Surface3) {
    Box(modifier.clip(RoundedCornerShape(7.dp)).background(color))
}

/** The program's timeline: the picture's place in white, the live edge as a red mark. */
@Composable
private fun Timeline(picture: Float, live: Float?, modifier: Modifier = Modifier) {
    // 10 dp tall for the live mark; the 4 dp track runs through its middle.
    BoxWithConstraints(modifier.fillMaxWidth().height(10.dp).offset(y = (-3).dp)) {
        Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(RemoteColors.Track))
        Box(Modifier.padding(top = 3.dp).width(maxWidth * picture.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(RemoteColors.Text))
        if (live != null) {
            Box(Modifier.offset(x = maxWidth * live.coerceIn(0f, 1f) - 1.5.dp).size(3.dp, 10.dp).clip(RoundedCornerShape(2.dp)).background(RemoteColors.Live))
        }
    }
}

/** The 240 dp ring with its four arrows and the 100 dp OK in the middle. */
@Composable
private fun DPad(tv: TvLink, modifier: Modifier = Modifier) {
    Box(modifier.size(240.dp).clip(CircleShape).background(RemoteColors.Surface1).border(1.dp, RemoteColors.Ring, CircleShape)) {
        Arrow(tv, RemoteKey.Up, RemoteIcons.Up, stringResource(Res.string.key_up), Modifier.align(Alignment.TopCenter))
        Arrow(tv, RemoteKey.Down, RemoteIcons.Down, stringResource(Res.string.key_down), Modifier.align(Alignment.BottomCenter))
        Arrow(tv, RemoteKey.Left, RemoteIcons.Left, stringResource(Res.string.key_left), Modifier.align(Alignment.CenterStart))
        Arrow(tv, RemoteKey.Right, RemoteIcons.Right, stringResource(Res.string.key_right), Modifier.align(Alignment.CenterEnd))
        val pressed = rememberPressed()
        val ok = stringResource(Res.string.key_ok)
        Box(
            Modifier.align(Alignment.Center).size(100.dp).shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape).background(RemoteColors.OkFill).pressedLook(pressed).remoteKey(tv, RemoteKey.Ok, ok, pressed),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(ok, style = textStyle(19.sp, FontWeight.SemiBold))
        }
    }
}

@Composable
private fun Arrow(tv: TvLink, key: RemoteKey, icon: ImageVector, label: String, modifier: Modifier) {
    val pressed = rememberPressed()
    Box(modifier.size(70.dp).clip(CircleShape).pressedLook(pressed).remoteKey(tv, key, label, pressed), contentAlignment = Alignment.Center) {
        Icon(icon, 26.dp, RemoteColors.Soft)
    }
}

/** Back, Home, Menu: a 58 dp round button with its name under it. */
@Composable
private fun RoundKey(tv: TvLink, key: RemoteKey, icon: ImageVector, label: String) {
    val pressed = rememberPressed()
    Column(Modifier.widthIn(min = 64.dp).remoteKey(tv, key, label, pressed), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(RemoteColors.Surface1).pressedLook(pressed), contentAlignment = Alignment.Center) {
            Icon(icon, 22.dp, RemoteColors.Text)
        }
        BasicText(label, style = textStyle(11.sp, color = RemoteColors.Text3))
    }
}

/** Numbers: opens the 0–9 pad. */
@Composable
private fun NumbersKey(label: String, onNumbers: () -> Unit) {
    val pressed = rememberPressed()
    Column(Modifier.widthIn(min = 64.dp).tap(label, pressed = pressed, action = onNumbers), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(RemoteColors.Surface1).pressedLook(pressed), contentAlignment = Alignment.Center) {
            BasicText(stringResource(Res.string.key_numbers_short), style = textStyle(15.sp, FontWeight.SemiBold, letterSpacing = 0.3.sp))
        }
        BasicText(label, style = textStyle(11.sp, color = RemoteColors.Text3))
    }
}

/** ◀◀, Play/Pause, ▶▶: a 74 × 46 dp tile. */
@Composable
private fun TileKey(tv: TvLink, key: RemoteKey, icon: ImageVector, label: String, enabled: Boolean, iconSize: Dp = 22.dp) {
    val pressed = rememberPressed()
    Box(Modifier.size(74.dp, 46.dp).clip(RoundedCornerShape(14.dp)).background(RemoteColors.Surface1).pressedLook(pressed).remoteKey(tv, key, label, pressed, enabled), contentAlignment = Alignment.Center) {
        Icon(icon, iconSize, RemoteColors.Text)
    }
}

/** CH ▲▼: the tile's top half is channel up, its bottom half channel down. */
@Composable
private fun ChannelKeys(tv: TvLink, enabled: Boolean) {
    val up = rememberPressed()
    val down = rememberPressed()
    Box(Modifier.size(74.dp, 46.dp).clip(RoundedCornerShape(14.dp)).background(RemoteColors.Surface1), contentAlignment = Alignment.Center) {
        Column(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).pressedLook(up).remoteKey(tv, RemoteKey.ChannelUp, stringResource(Res.string.key_channel_up), up, enabled))
            Box(Modifier.fillMaxWidth().weight(1f).pressedLook(down).remoteKey(tv, RemoteKey.ChannelDown, stringResource(Res.string.key_channel_down), down, enabled))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(stringResource(Res.string.key_channel), style = textStyle(14.sp, FontWeight.SemiBold))
            Column {
                Icon(RemoteIcons.Up, 15.dp, RemoteColors.Text)
                Icon(RemoteIcons.Down, 15.dp, RemoteColors.Text)
            }
        }
    }
}

/** A colour key, labelled with what it does on the TV's screen now ("Favorite"). */
@Composable
private fun ColorKey(tv: TvLink, key: RemoteKey, color: Color, label: String, enabled: Boolean) {
    val pressed = rememberPressed()
    Row(
        Modifier.size(77.dp, 38.dp).clip(RoundedCornerShape(10.dp)).background(RemoteColors.Surface1).pressedLook(pressed).remoteKey(tv, key, label, pressed, enabled).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(color))
        BasicText(label, style = textStyle(12.sp, color = RemoteColors.Soft), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A held key is lit a little, as on the web page. */
@Composable
fun Modifier.pressedLook(pressed: MutableState<Boolean>): Modifier = if (pressed.value) background(RemoteColors.Pressed) else this

@Composable
fun Icon(icon: ImageVector, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Image(icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** 134 seconds as "2:14", an hour or more as "1:02:14". */
fun clock(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = (seconds % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$s" else "$m:$s"
}

/** What needs Plasma in front, while another app is. */
private const val AWAY_ALPHA = 0.32f
