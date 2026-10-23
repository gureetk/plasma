// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.runtime.Composable
import ca.gureet.plasma.core.design.preview.TvPreview
import ca.gureet.plasma.core.fixtures.SampleHost

// Settings, its pages and the screens it opens (boards 19–24 and 27–34), from the sample data.

/** Board 19. */
@TvPreview
@Composable
private fun SettingsPreview() = SampleHost { SettingsScreen(SettingsPage.Main, SettingsValues(), SettingsActions()) }

/** Board 20: Portals & Playlists. */
@TvPreview
@Composable
private fun SourcesPreview() = SampleHost { SettingsScreen(SettingsPage.Sources, SettingsValues(), SettingsActions()) }

/** Board 21. */
@TvPreview
@Composable
private fun AddSourcePreview() = SampleHost { AddSourceScreen(AddSourceSamples.state, AddSourceActions()) }

/** Board 22: Launcher & Home. */
@TvPreview
@Composable
private fun LauncherPreview() = SampleHost { SettingsScreen(SettingsPage.Launcher, SettingsValues(), SettingsActions(), initialItem = "home-button") }

/** Board 23: Use Plasma as Home Screen. */
@TvPreview
@Composable
private fun HomeScreenSetupPreview() = SampleHost { SettingsScreen(SettingsPage.HomeScreen, SettingsValues(), SettingsActions(), initialItem = "replace") }

/** Board 24: Replace Google TV Home. */
@TvPreview
@Composable
private fun ReplaceHomePreview() = SampleHost { ReplaceHomeScreen(ReplaceHomeSamples.state, onOpenDeveloperOptions = {}) }

/** Board 27. */
@TvPreview
@Composable
private fun PlaybackPreview() = SampleHost { SettingsScreen(SettingsPage.Playback, SettingsValues(), SettingsActions()) }

/** Board 28. */
@TvPreview
@Composable
private fun AudioPreview() = SampleHost { SettingsScreen(SettingsPage.Audio, SettingsValues(), SettingsActions()) }

/** Board 29. */
@TvPreview
@Composable
private fun ChannelsPreview() = SampleHost { SettingsScreen(SettingsPage.Channels, SettingsValues(), SettingsActions(), initialItem = "favorites") }

/** Board 30. */
@TvPreview
@Composable
private fun DuplicateNumbersPreview() = SampleHost { DuplicateNumbersScreen(DuplicateNumbersSamples.state, initialConflict = 108) }

/** Board 31: Rewind and Local Catch-up. */
@TvPreview
@Composable
private fun RewindPreview() = SampleHost { SettingsScreen(SettingsPage.Rewind, SettingsValues(), SettingsActions()) }

/** Board 32. */
@TvPreview
@Composable
private fun ContinueWatchingPreview() = SampleHost { SettingsScreen(SettingsPage.ContinueWatching, SettingsValues(), SettingsActions()) }

/** Board 33: the TV's side of Phone Remote. */
@TvPreview
@Composable
private fun PhoneRemotePreview() = SampleHost { SettingsScreen(SettingsPage.PhoneRemote, SettingsValues(), SettingsActions()) }

/** Board 34. */
@TvPreview
@Composable
private fun RecordingPreview() = SampleHost { SettingsScreen(SettingsPage.Recording, SettingsValues(), SettingsActions(), initialItem = "copy") }
