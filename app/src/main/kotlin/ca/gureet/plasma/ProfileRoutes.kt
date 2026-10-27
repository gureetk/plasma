// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import ca.gureet.plasma.core.data.DEFAULT_PROFILE
import ca.gureet.plasma.core.data.Profile
import ca.gureet.plasma.core.data.ProfileKind
import ca.gureet.plasma.core.data.ProfileRepository
import ca.gureet.plasma.core.fixtures.fx
import ca.gureet.plasma.feature.profiles.ParentalPinScreen
import ca.gureet.plasma.feature.profiles.PinRequest
import ca.gureet.plasma.feature.profiles.ProfileItem
import ca.gureet.plasma.feature.profiles.ProfilesActions
import ca.gureet.plasma.feature.profiles.ProfilesScreen
import ca.gureet.plasma.feature.profiles.ProfilesUiState
import ca.gureet.plasma.feature.settings.ProfileInfo
import ca.gureet.plasma.feature.settings.ProfileType
import ca.gureet.plasma.nav.ProfilesRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A profile's picture (5.11): one of Plasma's own scenic pictures, by Settings' key; null for none. */
fun avatarImage(key: String?): String? = when (key) {
    "aurora" -> fx("aurora2")
    "city" -> fx("city2")
    "stadium" -> fx("stadium2")
    "concert" -> fx("concert2")
    "orbit" -> fx("orbit2")
    "lighthouse" -> fx("lighthouse3")
    "motor" -> fx("motor2")
    else -> null
}

/**
 * Who's Watching on the profiles (brief 4.5 and 5.11): each with what it watched last, its own
 * PIN, a kids profile's ages or a guest's "No history kept", and Add Profile while there's room.
 */
@Composable
fun LiveProfiles(graph: AppGraph, actions: ProfilesActions) {
    val profiles by graph.data.profiles.all.collectAsState(null)
    val current by graph.data.profiles.current.collectAsState(null)
    val world by graph.world.collectAsState()
    val me = stringResource(R.string.profile_name_default)
    val list = profiles ?: return
    val state = remember(list, current, world, me) {
        ProfilesUiState(
            profiles = list.map { profile ->
                ProfileItem(
                    id = profile.id,
                    name = profile.name.ifBlank { me },
                    avatar = avatarImage(profile.avatar),
                    pin = profile.hasPin,
                    maxAge = profile.maxAge.takeIf { profile.kind == ProfileKind.Kids },
                    guest = profile.kind == ProfileKind.Guest,
                    lastWatched = profile.lastChannelId?.let { world?.byId?.get(it)?.name },
                )
            },
            currentId = current?.id ?: list.first().id,
            canAdd = list.size < ProfileRepository.MAX,
        )
    }
    ProfilesScreen(state, actions)
}

/** A profile's own PIN, from Who's Watching (4.5); with it, that profile watches. */
@Composable
fun ProfilePin(graph: AppGraph, profileId: String, backLabel: String, onDone: () -> Unit) {
    val profiles by graph.data.profiles.all.collectAsState(null)
    val profile = profiles?.firstOrNull { it.id == profileId } ?: return
    ParentalPinScreen(
        request = PinRequest.Profile(profile.name.ifBlank { stringResource(R.string.profile_name_default) }),
        backLabel = backLabel,
        checkPin = { pin -> graph.data.profiles.isPin(profileId, pin) },
        onUnlocked = {
            graph.scope.launch { graph.data.profiles.choose(profileId) }
            onDone()
        },
        tries = graph.profileTries,
    )
}

/** The profiles as Settings › Profiles lists them (5.11). */
fun profileInfos(profiles: List<Profile>): List<ProfileInfo> = profiles.map { profile ->
    ProfileInfo(
        id = profile.id,
        name = profile.name,
        type = ProfileType.entries[profile.kind.ordinal],
        avatar = profile.avatar,
        hasPin = profile.hasPin,
        maxAge = profile.maxAge,
        first = profile.id == DEFAULT_PROFILE,
    )
}

/**
 * The TV woke (brief 5.11): with Ask Who's Watching on and more than one profile, Who's Watching
 * shows, and true says so; otherwise Default Profile, when one is chosen, watches.
 */
suspend fun whoIsWatching(graph: AppGraph, nav: NavHostController): Boolean {
    val profiles = graph.data.profiles.all.first()
    if (graph.choices.askWhosWatching && profiles.size > 1) {
        nav.navigate(ProfilesRoute)
        return true
    }
    graph.data.settings.defaultProfile()?.let { graph.data.profiles.choose(it) }
    return false
}
