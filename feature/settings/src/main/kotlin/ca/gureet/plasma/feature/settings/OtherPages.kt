// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.design.R as DesignR

/*
 * The Settings pages without a board of their own, in the same pattern (brief 4.35), with the rows
 * and defaults from the settings inventory (5.16).
 */

internal fun compactPage(title: String, back: String, sections: List<SettingSection>) = PageSpec(
    title = title,
    back = back,
    style = PageStyle.Compact,
    metrics = RowMetrics.Page,
    detailTop = 160.px,
    sections = sections,
)

@Composable
internal fun guidePage(values: SettingsValues, facts: SettingsFacts): PageSpec {
    val offset = values.choice(KEY_TIME_OFFSET, R.array.time_offset_options, 2)
    val past = values.choice(KEY_PAST_DAYS, R.array.past_days_options, 2)
    val ahead = values.choice(KEY_AHEAD_DAYS, R.array.ahead_days_options, 1)
    val update = values.choice(KEY_UPDATE_TIME, R.array.update_time_options, 1)
    val matching = values.choice(KEY_MATCHING, R.array.matching_options, 0)
    return compactPage(
        stringResource(R.string.guide),
        stringResource(DesignR.string.settings),
        listOf(
            SettingSection(
                stringResource(R.string.section_feeds),
                listOf(
                    infoRow(
                        "feeds", stringResource(R.string.xmltv), pluralStringResource(R.plurals.xmltv_feeds, facts.xmltvFeeds, facts.xmltvFeeds),
                        PlasmaIcons.GuideTable, stringResource(R.string.xmltv_text),
                    ),
                    choiceRow("offset", stringResource(R.string.time_offset), offset, PlasmaIcons.GuideTable, stringResource(R.string.time_offset_text)),
                    choiceRow("matching", stringResource(R.string.matching), matching, PlasmaIcons.GuideTable, stringResource(R.string.matching_text)),
                ),
            ),
            SettingSection(
                stringResource(R.string.section_keeping),
                listOf(
                    choiceRow("past", stringResource(R.string.keep_past), past, PlasmaIcons.History, stringResource(R.string.keep_past_text)),
                    choiceRow("ahead", stringResource(R.string.load_ahead), ahead, PlasmaIcons.GuideTable, stringResource(R.string.load_ahead_text)),
                    choiceRow("update", stringResource(R.string.update_time), update, PlasmaIcons.History, stringResource(R.string.update_time_text)),
                ),
            ),
        ),
    )
}

@Composable
internal fun parentalPage(values: SettingsValues): PageSpec {
    val adult = values.switch(KEY_LOCK_ADULT, true)
    val age = values.choice(KEY_AGE_LIMIT, R.array.age_limit_options, 3)
    val unlock = values.choice(KEY_UNLOCK_FOR, R.array.unlock_for_options, 0)
    val lockSettings = values.switch(KEY_LOCK_SETTINGS, false)
    return compactPage(
        stringResource(R.string.parental),
        stringResource(DesignR.string.settings),
        listOf(
            SettingSection(
                null,
                listOf(
                    infoRow("pin", stringResource(R.string.pin), stringResource(R.string.pin_value), PlasmaIcons.Lock, stringResource(R.string.pin_text)),
                    switchRow("adult", stringResource(R.string.lock_adult), adult, PlasmaIcons.Shield, stringResource(R.string.lock_adult_text)),
                    choiceRow("age", stringResource(R.string.age_limit), age, PlasmaIcons.Shield, stringResource(R.string.age_limit_text)),
                    infoRow("locked-channels", stringResource(R.string.locked_channels), "1", PlasmaIcons.Lock, stringResource(R.string.locked_channels_text)),
                    infoRow("locked-groups", stringResource(R.string.locked_groups), "1", PlasmaIcons.Lock, stringResource(R.string.locked_groups_text)),
                    choiceRow("unlock", stringResource(R.string.unlock_for), unlock, PlasmaIcons.Lock, stringResource(R.string.unlock_for_text)),
                    switchRow("lock-settings", stringResource(R.string.lock_settings), lockSettings, PlasmaIcons.Settings, stringResource(R.string.lock_settings_text)),
                ),
            ),
        ),
    )
}

@Composable
internal fun profilesPage(values: SettingsValues): PageSpec {
    val ask = values.switch(KEY_ASK_WHO, false)
    val names = Sample.profiles.map { it.name }
    // The profiles are data, not copy, so their names are the options.
    val selectedProfile = values.index(KEY_DEFAULT_PROFILE, 0).coerceIn(names.indices)
    val defaultProfile = Choice(names, selectedProfile, SettingAction.Choose(KEY_DEFAULT_PROFILE, names, selectedProfile))
    val profiles = Sample.profiles.map { profile ->
        val value = when {
            profile.id == Sample.maya.id -> stringResource(R.string.profile_current)
            profile.pin -> stringResource(R.string.profile_pin)
            profile.maxAge != null -> stringResource(R.string.profile_kids, profile.maxAge!!)
            profile.guest -> stringResource(R.string.profile_guest)
            else -> null
        }
        infoRow(
            "profile-${profile.id}", profile.name, value, PlasmaIcons.Person, stringResource(R.string.profile_text, profile.name),
            SettingAction.Go(SettingsDestination.Profiles),
        )
    }
    return compactPage(
        stringResource(R.string.profiles),
        stringResource(DesignR.string.settings),
        listOf(
            SettingSection(
                stringResource(R.string.section_profiles),
                profiles + SettingItem(
                    id = "add-profile",
                    label = stringResource(R.string.add_profile),
                    value = null,
                    chevron = false,
                    leadingIcon = PlasmaIcons.Plus,
                    action = SettingAction.Go(SettingsDestination.Profiles),
                    detail = SettingDetail(PlasmaIcons.People, stringResource(R.string.add_profile), text = stringResource(R.string.add_profile_text)),
                ),
            ),
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

@Composable
internal fun remotePage(values: SettingsValues): PageSpec {
    val longPress = values.choice(KEY_LONG_PRESS_OK, R.array.long_press_ok_options, 0)
    val delay = values.choice(KEY_NUMBER_DELAY, R.array.number_delay_options, 1)
    val channelUp = values.choice(KEY_CHANNEL_UP, R.array.channel_up_options, 0)
    return compactPage(
        stringResource(R.string.remote),
        stringResource(DesignR.string.settings),
        listOf(
            SettingSection(
                stringResource(R.string.section_phone),
                listOf(
                    infoRow(
                        "phone", stringResource(R.string.phone_remote), pluralStringResource(R.plurals.phone_remote_value, 2, 2), PlasmaIcons.Remote,
                        stringResource(R.string.phone_remote_text), SettingAction.Open(SettingsPage.PhoneRemote),
                    ),
                ),
            ),
            SettingSection(
                stringResource(R.string.section_keys),
                listOf(
                    infoRow("color-keys", stringResource(R.string.color_keys), stringResource(R.string.color_keys_value), PlasmaIcons.Remote, stringResource(R.string.color_keys_text)),
                    choiceRow("long-press-ok", stringResource(R.string.long_press_ok), longPress, PlasmaIcons.Remote, stringResource(R.string.long_press_ok_text)),
                    choiceRow("number-delay", stringResource(R.string.number_delay), delay, PlasmaIcons.Hash, stringResource(R.string.number_delay_text)),
                    choiceRow("channel-up", stringResource(R.string.channel_up), channelUp, PlasmaIcons.ChangeChannel, stringResource(R.string.channel_up_text)),
                ),
            ),
            SettingSection(
                stringResource(R.string.section_diagnostics),
                listOf(
                    infoRow(
                        "key-test", stringResource(R.string.key_test), null, PlasmaIcons.Remote,
                        stringResource(R.string.key_test_text), SettingAction.Go(SettingsDestination.KeyTest),
                    ),
                    infoRow(
                        "learn-key", stringResource(R.string.learn_key), null, PlasmaIcons.Remote,
                        stringResource(R.string.learn_key_text), SettingAction.Go(SettingsDestination.LearnKey),
                    ),
                ),
            ),
        ),
    )
}

@Composable
internal fun appearancePage(values: SettingsValues): PageSpec {
    val accent = values.choice(KEY_ACCENT, R.array.accent_options, 0)
    val clock = values.choice(KEY_CLOCK, R.array.clock_options, 0)
    val textSize = values.choice(KEY_TEXT_SIZE, R.array.text_size_options, 0)
    val reduceMotion = values.switch(KEY_REDUCE_MOTION, false)
    val numbers = values.switch(KEY_SHOW_NUMBERS, true)
    return compactPage(
        stringResource(R.string.appearance),
        stringResource(DesignR.string.settings),
        listOf(
            SettingSection(
                null,
                listOf(
                    choiceRow("accent", stringResource(R.string.accent), accent, PlasmaIcons.Contrast, stringResource(R.string.accent_text)),
                    choiceRow("clock", stringResource(R.string.clock), clock, PlasmaIcons.History, stringResource(R.string.clock_text)),
                    choiceRow("text-size", stringResource(R.string.text_size), textSize, PlasmaIcons.Contrast, stringResource(R.string.text_size_text)),
                    switchRow("reduce-motion", stringResource(R.string.reduce_motion), reduceMotion, PlasmaIcons.Contrast, stringResource(R.string.reduce_motion_text)),
                    switchRow("numbers", stringResource(R.string.channel_numbers_shown), numbers, PlasmaIcons.Hash, stringResource(R.string.channel_numbers_shown_text)),
                ),
            ),
        ),
    )
}

@Composable
internal fun networkPage(facts: SettingsFacts): PageSpec = compactPage(
    stringResource(R.string.network),
    stringResource(DesignR.string.settings),
    listOf(
        SettingSection(
            stringResource(R.string.section_this_tv),
            listOf(
                infoRow("connection", stringResource(R.string.connection), facts.connection, PlasmaIcons.Wifi, stringResource(R.string.connection_text)),
                infoRow("ip", stringResource(R.string.ip_address), facts.address, PlasmaIcons.Wifi, stringResource(R.string.ip_address_text)),
                infoRow("speed", stringResource(R.string.speed), facts.speed, PlasmaIcons.Wifi, stringResource(R.string.speed_text)),
            ),
        ),
        SettingSection(
            stringResource(R.string.section_source_tests),
            facts.sources.map { source ->
                val status = source.health.status(withPing = true)
                SettingItem(
                    id = "test-${source.id}",
                    label = source.name,
                    value = status.text,
                    status = status.tone,
                    detail = SettingDetail(PlasmaIcons.ServerLines, source.name, status = status, text = stringResource(R.string.source_test_text, source.name)),
                )
            },
        ),
    ),
)

@Composable
internal fun aboutPage(values: SettingsValues, facts: SettingsFacts): PageSpec {
    val updates = values.switch(KEY_CHECK_UPDATES, true)
    val channel = values.choice(KEY_UPDATE_CHANNEL, R.array.update_channel_options, 0)
    return compactPage(
        stringResource(R.string.about),
        stringResource(DesignR.string.settings),
        listOf(
            SettingSection(
                null,
                listOf(
                    infoRow("version", stringResource(R.string.version), facts.version, PlasmaIcons.Info, stringResource(R.string.version_text)),
                    switchRow("updates", stringResource(R.string.check_updates), updates, PlasmaIcons.Info, stringResource(R.string.check_updates_text)),
                    choiceRow("update-channel", stringResource(R.string.update_channel), channel, PlasmaIcons.Info, stringResource(R.string.update_channel_text)),
                    infoRow("licenses", stringResource(R.string.licenses), null, PlasmaIcons.Info, stringResource(R.string.licenses_text)),
                    infoRow("diagnostics", stringResource(R.string.diagnostics), null, PlasmaIcons.Info, stringResource(R.string.diagnostics_text)),
                    infoRow("backup", stringResource(R.string.backup), null, PlasmaIcons.Drive, stringResource(R.string.backup_text)),
                ),
            ),
        ),
    )
}

internal const val KEY_TIME_OFFSET = "timeOffset"
internal const val KEY_PAST_DAYS = "pastDays"
internal const val KEY_AHEAD_DAYS = "aheadDays"
internal const val KEY_UPDATE_TIME = "updateTime"
internal const val KEY_MATCHING = "matching"
internal const val KEY_LOCK_ADULT = "lockAdult"
internal const val KEY_AGE_LIMIT = "ageLimit"
internal const val KEY_UNLOCK_FOR = "unlockFor"
internal const val KEY_LOCK_SETTINGS = "lockSettings"
internal const val KEY_ASK_WHO = "askWho"
internal const val KEY_DEFAULT_PROFILE = "defaultProfile"
internal const val KEY_LONG_PRESS_OK = "longPressOk"
internal const val KEY_NUMBER_DELAY = "numberDelay"
internal const val KEY_CHANNEL_UP = "channelUp"
internal const val KEY_ACCENT = "accent"
internal const val KEY_TEXT_SIZE = "textSize"
internal const val KEY_REDUCE_MOTION = "reduceMotion"
internal const val KEY_SHOW_NUMBERS = "showNumbers"
internal const val KEY_CHECK_UPDATES = "checkUpdates"
internal const val KEY_UPDATE_CHANNEL = "updateChannel"
