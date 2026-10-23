// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.recordings

import androidx.compose.runtime.Composable
import ca.gureet.plasma.core.design.preview.TvPreview
import ca.gureet.plasma.core.fixtures.SampleHost

// Recordings (board 17), from the sample data.

/** Board 17. */
@TvPreview
@Composable
private fun RecordingsPreview() = SampleHost { RecordingsScreen(RecordingsSamples.state, RecordingsActions()) }
