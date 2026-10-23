// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.guide

import androidx.compose.runtime.Composable
import ca.gureet.plasma.core.design.preview.TvPreview
import ca.gureet.plasma.core.fixtures.SampleHost

// The Guide (board 08), from the sample data.

/** Board 08. */
@TvPreview
@Composable
private fun GuidePreview() = SampleHost { GuideScreen(GuideSamples.state, GuideActions()) }
