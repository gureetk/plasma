// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.model.SettingKeys

/**
 * Each profile's own audio and subtitle choices (brief 5.11): Preferred Language, Skip Audio
 * Description, and Subtitles and Teletext. The first profile keeps them under Settings' own keys,
 * as from before there were profiles; another keeps its own under "key@profileId", and has the
 * first profile's until it chooses.
 */
object ProfileChoices {
    /** The settings each profile has its own of. */
    val KEYS: Set<String> = setOf(SettingKeys.LANGUAGE, SettingKeys.SKIP_DESCRIPTION, SettingKeys.SUBTITLES)

    /** Where [profileId]'s choice for [key] is kept: Settings' own key, but for a profile's own choices. */
    fun keyFor(key: String, profileId: String): String = if (key !in KEYS || profileId == DEFAULT_PROFILE) key else "$key$SEPARATOR$profileId"

    /** Everything chosen in Settings as [profileId] has it: its own audio and subtitle choices over the first profile's. */
    fun forProfile(stored: Map<String, Int>, profileId: String): Map<String, Int> {
        val shared = stored.filterKeys { key -> KEYS.none { key.startsWith(it + SEPARATOR) } }
        if (profileId == DEFAULT_PROFILE) return shared
        return shared + KEYS.mapNotNull { key -> stored[keyFor(key, profileId)]?.let { key to it } }
    }

    /** The keys [profileId]'s own choices are kept under, to forget them with it. */
    fun keysOf(profileId: String): List<String> = if (profileId == DEFAULT_PROFILE) emptyList() else KEYS.map { keyFor(it, profileId) }

    private const val SEPARATOR = "@"
}
