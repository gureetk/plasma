// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ca.gureet.plasma.core.design.OverlayContent
import ca.gureet.plasma.core.design.rememberOverlay

/**
 * A window of its own over every screen (a reminder, a notice, a question) that comes and goes as
 * an overlay (brief 2.6): it fades in as it rises 8 dp, and fades out as it sinks back. A
 * [focusable] one takes the keys only while it's [visible]: going, it lets them go at once, so the
 * screen underneath, which kept its focus, has the next key. [content] fills the screen.
 */
@Composable
fun OverlayPopup(
    visible: Boolean,
    focusable: Boolean,
    onDismissRequest: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val motion = rememberOverlay(visible)
    if (!motion.present) return
    Popup(onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = focusable && visible)) {
        OverlayContent(motion) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}
