// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.livetv

import androidx.compose.runtime.Composable
import ca.gureet.plasma.core.design.preview.TvPreview
import ca.gureet.plasma.core.fixtures.SampleHost

// Live TV (board 07), from the sample data.

/** Board 07. */
@TvPreview
@Composable
private fun LiveTvPreview() = SampleHost { LiveTvScreen(LiveTvSamples.state, LiveTvActions()) }
