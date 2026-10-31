// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.R as DesignR

/** What a profile is, as Settings shows it (brief 5.11). */
enum class ProfileType { Standard, Kids, Guest }

/** A profile, as Settings › Profiles lists it (brief 5.11). */
@Immutable
data class ProfileInfo(
    val id: String,
    /** Its name; empty for the first one until it's named, which shows as "Me". */
    val name: String,
    val type: ProfileType = ProfileType.Standard,
    /** Its picture, by [ProfileSettings.AVATARS]' keys; null for none. */
    val avatar: String? = null,
    val hasPin: Boolean = false,
    /** A kids profile's age rating: "Ages 7 and under". */
    val maxAge: Int? = null,
    /** The first profile, there since before profiles: it stays a standard one, and can't go. */
    val first: Boolean = false,
)

/**
 * Keys for what's chosen on a profile's page (brief 5.11), which the app keeps with the profile
 * rather than with Settings' own choices.
 */
object ProfileSettings {
    const val AVATAR = "avatar"
    const val TYPE = "type"
    const val AGES = "ages"
    private const val PREFIX = "profile."
    private const val DEFAULT = "profile.default/"

    /** The pictures a profile can have, by key, in Picture's order after None. */
    val AVATARS: List<String> = listOf("aurora", "city", "stadium", "concert", "orbit", "lighthouse", "motor")

    /** A kids profile's age ratings, in Ages' order: "Ages 7 and under". */
    val AGES_OPTIONS: List<Int> = listOf(4, 7, 10, 12, 15)

    /** The key [field] of the profile [profileId] is chosen under. */
    fun key(field: String, profileId: String): String = "$PREFIX$field:$profileId"

    /** The field and profile [key] is for, or null for one that isn't a profile's. */
    fun of(key: String): Pair<String, String>? {
        if (!key.startsWith(PREFIX) || key.startsWith(DEFAULT)) return null
        val parts = key.removePrefix(PREFIX).split(':', limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else null
    }

    /**
     * Default Profile's key for these profiles: a choice, an option's place in the list, made for
     * another list isn't this one's.
     */
    fun defaultKey(profileIds: List<String>): String = DEFAULT + profileIds.joinToString(",").hashCode()

    fun isDefault(key: String): Boolean = key.startsWith(DEFAULT)
}

/** Who's Watching's own words for a profile: its name, or "Me" for the first one unnamed. */
@Composable
internal fun nameOf(profile: ProfileInfo): String = profile.name.ifBlank { stringResource(R.string.profile_name_default) }

/** "Watching now", "PIN", "Ages 7 and under" or "No history kept". */
@Composable
private fun profileValue(profile: ProfileInfo, current: String): String? = when {
    profile.id == current -> stringResource(R.string.profile_current)
    profile.hasPin -> stringResource(R.string.profile_pin)
    profile.type == ProfileType.Kids && profile.maxAge != null -> stringResource(R.string.profile_kids, profile.maxAge)
    profile.type == ProfileType.Guest -> stringResource(R.string.profile_guest)
    else -> null
}

/**
 * Settings › Profiles (brief 5.11): the profiles, each opening its own page, Add Profile while
 * there's room, Ask Who's Watching, and the profile Plasma starts as when the TV wakes.
 */
@Composable
internal fun profilesPage(values: SettingsValues, facts: SettingsFacts, actions: SettingsActions): PageSpec {
    val ask = values.switch(KEY_ASK_WHO, false)
    val profiles = facts.profiles
    val names = listOf(stringResource(R.string.default_profile_last)) + profiles.map { nameOf(it) }
    val defaultKey = ProfileSettings.defaultKey(profiles.map { it.id })
    val defaultIndex = values.index(defaultKey, facts.defaultProfile?.let { id -> profiles.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) } ?: 0)
        .coerceIn(names.indices)
    val defaultProfile = Choice(names, defaultIndex, SettingAction.Choose(defaultKey, names, defaultIndex))
    return compactPage(
        stringResource(R.string.profiles),
        stringResource(DesignR.string.settings),
        listOf(
            SettingSection(stringResource(R.string.section_profiles), profileRows(facts, actions)),
            SettingSection(
                stringResource(R.string.section_when),
                listOf(
                    switchRow("ask", stringResource(R.string.ask_who), ask, PlasmaIcons.People, stringResource(R.string.ask_who_text)),
                    choiceRow("default", stringResource(R.string.default_profile), defaultProfile, PlasmaIcons.Person, stringResource(R.string.default_profile_text)),
                ),
            ),
        ),
    )
}

/**
 * The profiles, each opening its own page, then Add Profile while there's room (brief 5.11): on
 * Settings › Profiles, and first-time setup's profiles step (#85).
 */
@Composable
internal fun profileRows(facts: SettingsFacts, actions: SettingsActions): List<SettingItem> {
    val rows = facts.profiles.map { profile ->
        val name = nameOf(profile)
        infoRow(
            "profile-${profile.id}", name, profileValue(profile, facts.currentProfile), PlasmaIcons.Person, stringResource(R.string.profile_text, name),
            SettingAction.Run { actions.onProfile(profile.id) },
        )
    }
    val addLabel = stringResource(R.string.add_profile)
    val add = SettingItem(
        id = ADD_PROFILE,
        label = addLabel,
        value = null,
        chevron = false,
        leadingIcon = PlasmaIcons.Plus,
        action = SettingAction.Type(
            title = addLabel,
            fields = listOf(TypedField(stringResource(R.string.field_name), "", hint = stringResource(R.string.profile_name_hint))),
            text = stringResource(R.string.add_profile_text),
            save = addLabel,
            onSave = { typed -> typed.first().takeIf { it.isNotBlank() }?.let(actions.onAddProfile) },
        ),
        detail = SettingDetail(PlasmaIcons.People, addLabel, text = stringResource(R.string.add_profile_text)),
    )
    return if (facts.profiles.size < MAX_PROFILES) rows + add else rows
}

/** Add Profile's row, by its id. */
internal const val ADD_PROFILE = "add-profile"

/**
 * A profile's own page (brief 5.11): its name, picture, kind and a kids profile's ages, its own
 * PIN, and Delete Profile. The first profile stays a standard one, and can't be deleted.
 */
@Composable
internal fun profilePage(values: SettingsValues, facts: SettingsFacts, profileId: String?, actions: SettingsActions): PageSpec {
    val profile = facts.profiles.firstOrNull { it.id == profileId } ?: return profilesPage(values, facts, actions)
    val name = nameOf(profile)
    val nameLabel = stringResource(R.string.profile_name)
    val pictures = stringArrayResource(R.array.picture_options).toList()
    val pictureKey = ProfileSettings.key(ProfileSettings.AVATAR, profile.id)
    val picture = values.index(pictureKey, ProfileSettings.AVATARS.indexOf(profile.avatar) + 1).coerceIn(pictures.indices)
    val types = stringArrayResource(R.array.profile_type_options).toList()
    val typeKey = ProfileSettings.key(ProfileSettings.TYPE, profile.id)
    val type = values.index(typeKey, profile.type.ordinal).coerceIn(types.indices)
    val ages = ProfileSettings.AGES_OPTIONS.map { stringResource(R.string.profile_kids, it) }
    val agesKey = ProfileSettings.key(ProfileSettings.AGES, profile.id)
    val age = values.index(agesKey, ProfileSettings.AGES_OPTIONS.indexOf(profile.maxAge ?: KIDS_AGE).coerceAtLeast(0)).coerceIn(ages.indices)
    val pinLabel = stringResource(R.string.pin)
    val pinAction = if (!profile.hasPin) {
        SettingAction.Run { actions.onProfilePin(profile.id) }
    } else {
        SettingAction.Menu(
            title = pinLabel,
            subtitle = stringResource(R.string.profile_pin_text),
            options = listOf(
                MenuOption(stringResource(R.string.change_pin)) { actions.onProfilePin(profile.id) },
                MenuOption(
                    stringResource(R.string.remove_pin),
                    next = SettingAction.Menu(
                        title = stringResource(R.string.remove_pin_title),
                        subtitle = stringResource(R.string.remove_pin_text, name),
                        options = listOf(MenuOption(stringResource(R.string.remove_pin)) { actions.onRemoveProfilePin(profile.id) }, MenuOption(stringResource(R.string.cancel))),
                    ),
                ),
            ),
        )
    }
    val deleteLabel = stringResource(R.string.delete_profile)
    val rows = buildList {
        add(
            infoRow(
                "name", nameLabel, name, PlasmaIcons.Person, stringResource(R.string.profile_name_text),
                SettingAction.Type(
                    title = nameLabel,
                    fields = listOf(TypedField(stringResource(R.string.field_name), profile.name, hint = stringResource(R.string.profile_name_hint))),
                    onSave = { typed -> typed.first().takeIf { it.isNotBlank() }?.let { actions.onProfileName(profile.id, it) } },
                ),
            ),
        )
        add(
            choiceRow(
                "picture", stringResource(R.string.profile_picture), Choice(pictures, picture, SettingAction.Choose(pictureKey, pictures, picture)),
                PlasmaIcons.Person, stringResource(R.string.profile_picture_text),
            ),
        )
        if (!profile.first) {
            add(
                choiceRow(
                    "type", stringResource(R.string.profile_type), Choice(types, type, SettingAction.Choose(typeKey, types, type)),
                    PlasmaIcons.People, stringResource(R.string.profile_type_text),
                ),
            )
            if (type == ProfileType.Kids.ordinal) {
                add(
                    choiceRow(
                        "ages", stringResource(R.string.profile_ages), Choice(ages, age, SettingAction.Choose(agesKey, ages, age)),
                        PlasmaIcons.Shield, stringResource(R.string.profile_ages_text),
                    ),
                )
            }
        }
        add(
            infoRow(
                "pin", pinLabel, stringResource(if (profile.hasPin) R.string.pin_value else R.string.pin_value_not_set), PlasmaIcons.Lock,
                stringResource(R.string.profile_pin_text), pinAction,
            ),
        )
        if (!profile.first) {
            add(
                SettingItem(
                    id = "delete",
                    label = deleteLabel,
                    value = null,
                    chevron = false,
                    action = SettingAction.Menu(
                        title = stringResource(R.string.delete_profile_title, name),
                        subtitle = stringResource(R.string.delete_profile_text),
                        options = listOf(MenuOption(stringResource(R.string.delete)) { actions.onDeleteProfile(profile.id) }, MenuOption(stringResource(R.string.cancel))),
                    ),
                    detail = SettingDetail(PlasmaIcons.Person, deleteLabel, text = stringResource(R.string.delete_profile_text)),
                ),
            )
        }
    }
    return compactPage(name, stringResource(R.string.profiles), listOf(SettingSection(null, rows)))
}

/** Who's Watching has room for six (5.11). */
private const val MAX_PROFILES = 6

/** A new kids profile's ages: 7 and under. */
private const val KIDS_AGE = 7
