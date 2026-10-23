// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.runtime.Composable
import ca.gureet.plasma.core.design.preview.TvPreview
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.fixtures.SampleHost

// The player, its options, the zap banner and Multiview (boards 09–12), from the sample data.

/** Board 09: the overlay. */
@TvPreview
@Composable
private fun PlayerPreview() = SampleHost { PlayerScreen(PlayerSamples.state, PlayerActions(), initialOverlay = PlayerOverlay.Controls, autoHide = false) }

/** Board 10: Player Options. */
@TvPreview
@Composable
private fun PlayerOptionsPreview() = SampleHost { PlayerScreen(PlayerSamples.state, PlayerActions(), initialOverlay = PlayerOverlay.Options, autoHide = false) }

/** Board 11: the zap banner and a reminder, a minute before ten. */
@TvPreview
@Composable
private fun ZapBannerPreview() = SampleHost(Sample.zapClock) { PlayerScreen(PlayerSamples.zapState, PlayerActions(), initialOverlay = PlayerOverlay.Zap, autoHide = false) }

/** Board 12. */
@TvPreview
@Composable
private fun MultiviewPreview() = SampleHost { MultiviewScreen(PlayerSamples.multiview, MultiviewActions()) }
