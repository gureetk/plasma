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

    /** The sample PIN, for the parental lock and Daniel's profile alike, until profiles arrive (M7). */
    const val PIN = "1234"

    /** What's on a locked [channel] now, as the PIN screen shows it; Combat Arena's fight on board 25. */
    fun lockedProgram(channel: Sample.Channel = Sample.combat): PinRequest.Program = PinRequest.Program(
        channelNumber = channel.number,
        channelName = channel.name,
        logo = channel.logo,
        title = Sample.nowOn(channel)?.title ?: channel.name,
        // The sample's locked channel is an 18+ one (appendix A).
        rating = "18+",
    )
}
