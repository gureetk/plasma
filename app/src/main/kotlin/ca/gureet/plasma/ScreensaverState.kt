// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ca.gureet.plasma.core.model.LivePreview
import ca.gureet.plasma.core.model.PictureSource
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.feature.home.HeroPhoto
import ca.gureet.plasma.feature.home.HomeSamples
import ca.gureet.plasma.feature.home.PlasmaPictures
import ca.gureet.plasma.feature.home.ScreensaverUiState
import ca.gureet.plasma.ui.LocalScreensaverShowing
import kotlinx.coroutines.delay

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
    val yours = choices.screensaverPictures == PictureSource.YourPhotos
    val photo by produceState<ScreensaverPhotos.Shown?>(null, yours, place, minutes, order) {
        value = null
        val at = place?.takeIf { yours } ?: return@produceState
        val folder = if (at.startsWith(SMB)) graph.data.photoFolders.get() ?: return@produceState else null
        graph.screensaverPhotos.show(at, folder, minutes * MS_PER_MINUTE, order).collect { value = it }
    }
    val base = if (samples) HomeSamples.screensaver else PlasmaPictures.copy(recording = recordingNow)
    return photo?.let { ScreensaverUiState(art = it.file, recording = base.recording, minutes = minutes, whole = it.whole) } ?: base.copy(minutes = minutes)
}

/**
 * Home's banner with Live Preview on Home › Photos (#171): Plasma's pictures in turn, or the
 * viewer's own photos as Photos on Home says, from Screensaver Pictures' folder, each for
 * [BANNER_MS]; Plasma's while the first photo's on its way, or none can be had. Null for the
 * other choices, which have the channel.
 *
 * While the screensaver's over Home, or another app's in front, the photo on screen stays and
 * none are copied from the network; then they carry on from it.
 */
@Composable
internal fun rememberHomePhoto(graph: AppGraph, chosen: Map<String, Int>): HeroPhoto? {
    val choices = remember(chosen) { SettingsChoices(chosen) }
    if (choices.livePreview != LivePreview.Photos) return null
    val place by graph.data.settings.screensaverPhotos.collectAsState(null)
    val yours = choices.homePhotos == PictureSource.YourPhotos
    val order = choices.photoOrder
    val resting = LocalScreensaverShowing.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val photo by produceState<ScreensaverPhotos.Shown?>(null, yours, place, order, resting, lifecycle) {
        val at = place?.takeIf { yours }
        val folder = at?.takeIf { it.startsWith(SMB) }?.let { graph.data.photoFolders.get() }
        if (at == null || (at.startsWith(SMB) && folder == null)) {
            value = null
            return@produceState
        }
        if (resting) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            graph.bannerPhotos.show(at, folder, BANNER_MS, order).collect { value = it }
        }
    }
    val plasma by produceState(PLASMAS.first()) {
        var at = 0
        while (true) {
            value = PLASMAS[at % PLASMAS.size]
            delay(BANNER_MS)
            at++
        }
    }
    return photo?.let { HeroPhoto(it.file, it.whole) } ?: HeroPhoto(plasma)
}

/** Plasma's own pictures, for the banner as for the screensaver. */
private val PLASMAS: List<String> = listOfNotNull(PlasmaPictures.art) + PlasmaPictures.more

/** Each photo's time in the banner: 20 seconds (#171). */
private const val BANNER_MS = 20_000L

private const val SMB = "smb://"
private const val MS_PER_MINUTE = 60_000L
