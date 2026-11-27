// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.plasma.remote.app.SheetShape
import ca.gureet.plasma.remote.app.resources.Res
import ca.gureet.plasma.remote.app.resources.close
import ca.gureet.plasma.remote.app.resources.key_ok
import ca.gureet.plasma.remote.app.tv.TvLink
import ca.gureet.plasma.remote.protocol.RemoteKey
import org.jetbrains.compose.resources.stringResource

/**
 * Numbers (board 33): a 0–9 pad over the remote. Each digit goes to the TV as it's tapped, and
 * the TV collects them as from its own remote's number keys; OK is the remote's OK. A tap
 * outside the pad, or Close, puts it away.
 */
@Composable
fun NumberPad(tv: TvLink, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(RemoteColors.Scrim).tap("", action = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(SheetShape).background(RemoteColors.Surface1)
                .swallowTaps()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { digit -> Digit(digit) { tv.digits(digit) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PadKey(stringResource(Res.string.close), Color.Transparent, RemoteColors.Text2, 16, onClose)
                Digit("0") { tv.digits("0") }
                val ok = stringResource(Res.string.key_ok)
                val pressed = rememberPressed()
                Box(
                    Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(14.dp)).background(RemoteColors.Text).pressedLook(pressed).remoteKey(tv, RemoteKey.Ok, ok, pressed),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(ok, style = textStyle(18.sp, FontWeight.SemiBold, RemoteColors.Bg))
                }
            }
        }
    }
}

@Composable
private fun RowScope.Digit(digit: String, onTap: () -> Unit) = PadKey(digit, RemoteColors.Surface3, RemoteColors.Text, 24, onTap)

@Composable
private fun RowScope.PadKey(label: String, fill: Color, text: Color, size: Int, onTap: () -> Unit) {
    val pressed = rememberPressed()
    Box(Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(14.dp)).background(fill).pressedLook(pressed).tap(label, pressed = pressed, action = onTap), contentAlignment = Alignment.Center) {
        BasicText(label, style = textStyle(size.sp, FontWeight.SemiBold, text))
    }
}
