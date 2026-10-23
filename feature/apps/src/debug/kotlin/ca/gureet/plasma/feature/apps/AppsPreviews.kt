// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.apps

import androidx.compose.runtime.Composable
import ca.gureet.plasma.core.design.preview.TvPreview
import ca.gureet.plasma.core.fixtures.SampleHost

// Apps (board 04), from the sample data.

/** Board 04: an app being moved. */
@TvPreview
@Composable
private fun AppsPreview() = SampleHost { AppsScreen(AppsSamples.moving, AppsActions()) }

/** The Apps page without a move in progress. */
@TvPreview
@Composable
private fun AppsBrowsePreview() = SampleHost { AppsScreen(AppsSamples.state, AppsActions()) }
