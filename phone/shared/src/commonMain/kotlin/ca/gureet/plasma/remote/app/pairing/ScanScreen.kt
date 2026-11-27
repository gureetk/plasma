// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import ca.gureet.plasma.remote.app.resources.Res
import ca.gureet.plasma.remote.app.resources.cancel
import ca.gureet.plasma.remote.app.resources.open_settings
import ca.gureet.plasma.remote.app.resources.scan_denied
import ca.gureet.plasma.remote.app.resources.scan_not_plasma
import ca.gureet.plasma.remote.app.resources.scan_text
import ca.gureet.plasma.remote.app.resources.scan_title
import ca.gureet.plasma.remote.app.resources.scan_unavailable
import ca.gureet.plasma.remote.app.scan.QrScanner
import ca.gureet.plasma.remote.app.scan.ScanProblem
import ca.gureet.plasma.remote.app.scan.rememberOpenSettings
import ca.gureet.plasma.remote.app.ui.RemoteColors
import ca.gureet.plasma.remote.app.ui.textStyle
import ca.gureet.plasma.remote.protocol.RemoteInvite
import org.jetbrains.compose.resources.stringResource

/**
 * Scanning the TV's code (brief 4.35): the camera over the whole screen, dimmed outside a rounded
 * viewfinder, with "Scan the code on your TV" above it. The first Phone Remote code read goes to
 * [onInvite]; another QR code says it isn't one. If the camera can't be used, it says why, with a
 * way to the phone's settings when the phone said no.
 */
@Composable
fun ScanScreen(onInvite: (RemoteInvite) -> Unit, onCancel: () -> Unit) {
    var problem by remember { mutableStateOf<ScanProblem?>(null) }
    var notPlasma by remember { mutableStateOf(false) }
    var read by remember { mutableStateOf(false) }
    val openSettings = rememberOpenSettings()
    Box(Modifier.fillMaxSize().background(RemoteColors.Bg)) {
        if (problem == null) {
            QrScanner(
                onText = { text ->
                    if (read) return@QrScanner
                    val invite = RemoteInvite.parse(text)
                    if (invite == null) {
                        notPlasma = true
                    } else {
                        read = true
                        onInvite(invite)
                    }
                },
                onProblem = { problem = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            BasicText(stringResource(Res.string.scan_title), style = textStyle(22.sp, FontWeight.SemiBold).copy(textAlign = TextAlign.Center))
            BasicText(
                stringResource(Res.string.scan_text),
                Modifier.padding(top = 6.dp, bottom = 24.dp),
                style = textStyle(14.sp, color = RemoteColors.Text2).copy(textAlign = TextAlign.Center, lineHeight = 19.sp),
            )
            // Drawn before the words, the dimming around the viewfinder stays under them.
            Box(Modifier.zIndex(-1f).widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f).viewfinder())
            Box(Modifier.padding(top = 20.dp).widthIn(max = 340.dp).fillMaxWidth()) {
                when {
                    problem == ScanProblem.Denied -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Note(stringResource(Res.string.scan_denied))
                        Pill(stringResource(Res.string.open_settings), RemoteColors.Text, RemoteColors.Bg, onTap = openSettings)
                    }
                    problem == ScanProblem.Unavailable -> Note(stringResource(Res.string.scan_unavailable))
                    notPlasma -> Note(stringResource(Res.string.scan_not_plasma))
                }
            }
            Spacer(Modifier.weight(1f))
            Pill(stringResource(Res.string.cancel), RemoteColors.Button, RemoteColors.Text, onTap = onCancel)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Dims everything but this box, a rounded square with a light edge. */
private fun Modifier.viewfinder(): Modifier = drawBehind {
    val corner = CornerRadius(28.dp.toPx())
    val hole = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, corner)) }
    val far = 10_000f
    clipPath(hole, ClipOp.Difference) {
        drawRect(RemoteColors.Scrim, topLeft = Offset(-far, -far), size = Size(size.width + 2 * far, size.height + 2 * far))
    }
    drawPath(hole, RemoteColors.Text, style = Stroke(3.dp.toPx()))
}
