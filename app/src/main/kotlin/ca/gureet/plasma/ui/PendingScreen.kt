// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ca.gureet.plasma.R
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.component.SecondaryHeader

/**
 * Stands in for a screen that hasn't been built yet, so every destination can be reached, or one
 * with nothing to show ([message]). Back returns.
 */
@Composable
fun PendingScreen(title: String, backLabel: String? = stringResource(R.string.back_home), message: String = stringResource(R.string.pending_screen)) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // The screen itself has focus, so TalkBack reads its title and what it says (#128).
    Box(Modifier.fillMaxSize().focusRequester(focus).semantics { contentDescription = "$title, $message" }.focusable()) {
        SecondaryHeader(backLabel = backLabel, title = title)
        PlasmaText(
            message,
            PlasmaType.Body,
            Modifier.align(Alignment.Center),
            color = PlasmaColors.Text2,
        )
    }
}
