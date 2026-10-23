// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.profiles

import androidx.compose.runtime.Composable
import ca.gureet.plasma.core.design.preview.TvPreview
import ca.gureet.plasma.core.fixtures.SampleHost

// Who's Watching and the parental PIN (boards 05 and 25), from the sample data.

/** Board 05. */
@TvPreview
@Composable
private fun ProfilesPreview() = SampleHost { ProfilesScreen(ProfilesSamples.state, ProfilesActions()) }

/** Board 25, before a digit is typed. */
@TvPreview
@Composable
private fun ParentalPinPreview() = SampleHost {
    ParentalPinScreen(
        request = ProfilesSamples.lockedProgram(),
        backLabel = "Live TV",
        checkPin = { it == ProfilesSamples.PIN },
        onUnlocked = {},
    )
}
