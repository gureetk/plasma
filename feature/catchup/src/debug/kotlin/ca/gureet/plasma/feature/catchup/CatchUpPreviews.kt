// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.catchup

import androidx.compose.runtime.Composable
import ca.gureet.plasma.core.design.preview.TvPreview
import ca.gureet.plasma.core.fixtures.SampleHost

// Catch-up (board 18), from the sample data.

/** Board 18. */
@TvPreview
@Composable
private fun CatchUpPreview() = SampleHost { CatchUpScreen(CatchUpSamples.state, CatchUpActions()) }
