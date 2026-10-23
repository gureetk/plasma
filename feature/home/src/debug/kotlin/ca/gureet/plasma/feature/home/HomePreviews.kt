// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.home

import androidx.compose.runtime.Composable
import ca.gureet.plasma.core.design.preview.TvPreview
import ca.gureet.plasma.core.fixtures.SampleHost

// Home, its rows and the screensaver (boards 01, 02 and 06), from the sample data.

/** Board 01: focus on the dock. */
@TvPreview
@Composable
private fun HomePreview() = SampleHost { HomeScreen(HomeSamples.state, HomeActions(), initialFocus = HomeFocus.Dock, heroAutoAdvance = false) }

/** Board 02: scrolled down to the rows. */
@TvPreview
@Composable
private fun HomeRowsPreview() = SampleHost { HomeScreen(HomeSamples.state, HomeActions(), initialFocus = HomeFocus.Rows, heroAutoAdvance = false) }

/** Board 06. */
@TvPreview
@Composable
private fun ScreensaverPreview() = SampleHost { ScreensaverScreen(HomeSamples.screensaver, drift = false) }
