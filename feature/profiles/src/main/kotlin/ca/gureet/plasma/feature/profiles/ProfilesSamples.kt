// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.profiles

import ca.gureet.plasma.core.fixtures.Sample

/** Who's Watching as board 05 draws it: Maya (current), Daniel (PIN), Kids and Guest. */
object ProfilesSamples {
    val state: ProfilesUiState
        get() = ProfilesUiState(
            profiles = Sample.profiles.map {
                ProfileItem(it.id, it.name, it.avatar, it.pin, it.maxAge, it.guest, it.lastWatched)
            },
            currentId = Sample.maya.id,
        )
}
