// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import ca.gureet.plasma.core.model.ScreensaverPictures
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.feature.home.HomeSamples
import ca.gureet.plasma.feature.home.PlasmaPictures
import ca.gureet.plasma.feature.home.ScreensaverUiState

/**
 * The screensaver as Settings has it (#170): Plasma's own pictures, each for the time chosen, or
 * the viewer's own photos once one can be shown; with what's recording now, or in the sample
 * world, its recording. Plasma's show while a photo's on its way, and whenever none can be had.
 */
@Composable
internal fun rememberScreensaver(graph: AppGraph, chosen: Map<String, Int>, samples: Boolean): ScreensaverUiState {
    val choices = remember(chosen) { SettingsChoices(chosen) }
    val recordingNow by graph.recordingNow.collectAsState(null)
    val place by graph.data.settings.screensaverPhotos.collectAsState(null)
    val minutes = choices.screensaverEach.minutes
    val order = choices.photoOrder
    val yours = choices.screensaverPictures == ScreensaverPictures.YourPhotos
    val photo by produceState<ScreensaverPhotos.Shown?>(null, yours, place, minutes, order) {
        value = null
        val at = place?.takeIf { yours } ?: return@produceState
        val folder = if (at.startsWith(SMB)) graph.data.photoFolders.get() ?: return@produceState else null
        graph.screensaverPhotos.show(at, folder, minutes, order).collect { value = it }
    }
    val base = if (samples) HomeSamples.screensaver else PlasmaPictures.copy(recording = recordingNow)
    return photo?.let { ScreensaverUiState(art = it.file, recording = base.recording, minutes = minutes, whole = it.whole) } ?: base.copy(minutes = minutes)
}

private const val SMB = "smb://"
