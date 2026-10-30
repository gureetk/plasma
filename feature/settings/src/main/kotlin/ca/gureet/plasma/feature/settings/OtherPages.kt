// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.model.SettingsChoices
import java.time.ZonedDateTime
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
internal fun guidePage(values: SettingsValues, facts: SettingsFacts, onOverApps: (OverAppsAction) -> Unit = {}): PageSpec {
    // A time offset per source (brief 5.16), each named under it once there's more than one.
    val offsets = offsetOptions()
    val offsetLabel = stringResource(R.string.time_offset)
    val offsetText = stringResource(R.string.time_offset_text)
    val offsetRows = facts.sources.map { source ->
        val key = SourceSettings.key(SourceSettings.GUIDE_OFFSET, source.id)
        val selected = values.index(key, SourceSettings.offsetIndex(source.playback.epgOffsetMinutes)).coerceIn(offsets.indices)
        SettingItem(
            id = "offset-${source.id}",
            label = offsetLabel,
            value = offsets[selected],
            subtitle = source.name.takeIf { facts.sources.size > 1 },
            action = SettingAction.Choose(key, offsets, selected, note = source.name),
            detail = SettingDetail(PlasmaIcons.GuideTable, offsetLabel, subline = source.name, text = offsetText),
        )
    }
    val past = values.choice(KEY_PAST_DAYS, R.array.past_days_options, 2)
    val ahead = values.choice(KEY_AHEAD_DAYS, R.array.ahead_days_options, 1)
    val update = values.choice(KEY_UPDATE_TIME, R.array.update_time_options, 1)
    val matching = values.choice(KEY_MATCHING, R.array.matching_options, 0)
    val details = values.choice(KEY_LIVE_DETAILS, R.array.live_details_options, 0)
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
                ) + offsetRows + listOf(
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
            SettingSection(
                stringResource(R.string.section_reminders),
                listOf(overAppsRow(facts.remindersOverApps, facts.home.packageName, onOverApps)),
            ),
            // Whose schedule shows under Live TV's picture (the owner's choice, 30 October).
            SettingSection(
                stringResource(R.string.section_live_tv),
                listOf(choiceRow("live-details", stringResource(R.string.live_details), details, PlasmaIcons.LiveTv, stringResource(R.string.live_details_text))),
            ),
        ),
    )
}

/**
 * Show over Other Apps (brief 5.7): a reminder coming due in another app shows over it, once
 * Android lets Plasma "display over other apps". Many TVs hide that setting, so Plasma can allow it
 * over its pairing with the TV.
 */
@Composable
private fun overAppsRow(facts: ReminderOverApps, packageName: String, onAction: (OverAppsAction) -> Unit): SettingItem {
    val label = stringResource(R.string.over_apps)
    return SettingItem(
        id = "over-apps",
        label = label,
        value = stringResource(if (facts.allowed) R.string.allowed else R.string.not_allowed),
        status = if (facts.allowed) Tone.Ok else null,
        chevron = !facts.allowed,
        action = if (facts.allowed) {
            SettingAction.None
        } else {
            SettingAction.Explain(
                title = label,
                icon = PlasmaIcons.BellRing,
                text = stringResource(R.string.over_apps_sheet_text),
                points = listOf(
                    CheckItem(true, stringResource(R.string.over_apps_only)),
                    CheckItem(true, stringResource(R.string.over_apps_keys)),
                ),
                note = stringResource(R.string.over_apps_command, packageName),
                options = listOfNotNull(
                    MenuOption(stringResource(R.string.allow_over_debugging)) { onAction(OverAppsAction.AllowOverWirelessDebugging) },
                    if (facts.tvSettings) MenuOption(stringResource(R.string.open_tv_settings)) { onAction(OverAppsAction.OpenTvSettings) } else null,
                    MenuOption(stringResource(R.string.not_now)),
                ),
            )
        },
        detail = SettingDetail(PlasmaIcons.BellRing, label, text = stringResource(R.string.over_apps_text)),
    )
}

/** "On · 18+ locked", "On", or "Off" before there's a PIN: Parental Controls on Settings' first page. */
@Composable
internal fun parentalValue(values: SettingsValues, facts: SettingsFacts): String {
    val age = values.choice(KEY_AGE_LIMIT, R.array.age_limit_options, AGE_LIMIT_DEFAULT)
    return when {
        !facts.pinSet -> stringResource(R.string.parental_value_off)
        age.selected == 0 -> stringResource(R.string.parental_value_on)
        else -> stringResource(R.string.parental_value, age.value)
    }
}

/**
 * Parental Controls (5.12): they're on once a PIN is set, and then the page itself opens with the
 * PIN. PIN chooses one, or changes it or turns parental controls off; Locked Channels unlocks those
 * locked from Live TV; Lock Settings needs a PIN first. Kid-Safe Groups and Kids Words say which
 * groups kids profiles see (5.11).
 */
@Composable
internal fun parentalPage(
    values: SettingsValues,
    facts: SettingsFacts,
    onGo: (SettingsDestination) -> Unit = {},
    onLockChannel: (channelId: String, locked: Boolean) -> Unit = { _, _ -> },
    onTurnOff: () -> Unit = {},
    onLockGroup: (groupId: String, locked: Boolean) -> Unit = { _, _ -> },
    onAdultWords: (String) -> Unit = {},
    onKidSafeGroup: (groupId: String, kidSafe: Boolean) -> Unit = { _, _ -> },
    onKidWords: (String) -> Unit = {},
): PageSpec {
    val adult = values.switch(KEY_LOCK_ADULT, true)
    val age = values.choice(KEY_AGE_LIMIT, R.array.age_limit_options, AGE_LIMIT_DEFAULT)
    val unlock = values.choice(KEY_UNLOCK_FOR, R.array.unlock_for_options, 0)
    val lockSettings = values.switch(KEY_LOCK_SETTINGS, false)
    val pinLabel = stringResource(R.string.pin)
    val pinAction = if (!facts.pinSet) {
        SettingAction.Go(SettingsDestination.SetPin)
    } else {
        SettingAction.Menu(
            title = pinLabel,
            subtitle = stringResource(R.string.pin_text),
            options = listOf(
                MenuOption(stringResource(R.string.change_pin)) { onGo(SettingsDestination.ChangePin) },
                MenuOption(
                    stringResource(R.string.turn_off_parental),
                    next = SettingAction.Menu(
                        title = stringResource(R.string.turn_off_parental_title),
                        subtitle = stringResource(R.string.turn_off_parental_text),
                        options = listOf(MenuOption(stringResource(R.string.turn_off), run = onTurnOff), MenuOption(stringResource(R.string.cancel))),
                    ),
                ),
            ),
        )
    }
    val lockedLabel = stringResource(R.string.locked_channels)
    val groupsLabel = stringResource(R.string.locked_groups)
    val wordsLabel = stringResource(R.string.adult_words)
    val wordsText = stringResource(R.string.adult_words_text)
    val wordsAction = SettingAction.Type(
        title = wordsLabel,
        fields = listOf(TypedField(stringResource(R.string.field_adult_words), facts.adultWords, hint = stringResource(R.string.adult_words_hint))),
        text = wordsText,
        onSave = { typed -> onAdultWords(typed.first()) },
    )
    val lockSettingsLabel = stringResource(R.string.lock_settings)
    val lockSettingsText = stringResource(R.string.lock_settings_text)
    val kidWordsLabel = stringResource(R.string.kid_words)
    val kidWordsText = stringResource(R.string.kid_words_text)
    val kidWordsAction = SettingAction.Type(
        title = kidWordsLabel,
        fields = listOf(TypedField(stringResource(R.string.field_kid_words), facts.kidWords, hint = stringResource(R.string.adult_words_hint))),
        text = kidWordsText,
        onSave = { typed -> onKidWords(typed.first()) },
    )
    return compactPage(
        stringResource(R.string.parental),
        stringResource(DesignR.string.settings),
        listOf(
            SettingSection(
                null,
                listOf(
                    infoRow(
                        "pin", pinLabel, stringResource(if (facts.pinSet) R.string.pin_value else R.string.pin_value_not_set), PlasmaIcons.Lock,
                        stringResource(if (facts.pinSet) R.string.pin_text else R.string.pin_text_not_set), pinAction,
                    ),
                    switchRow("adult", stringResource(R.string.lock_adult), adult, PlasmaIcons.Shield, stringResource(R.string.lock_adult_text)),
                    infoRow("adult-words", wordsLabel, facts.adultWords, PlasmaIcons.Shield, wordsText, wordsAction),
                    choiceRow("age", stringResource(R.string.age_limit), age, PlasmaIcons.Shield, stringResource(R.string.age_limit_text)),
                    infoRow(
                        "locked-channels", lockedLabel, facts.lockedChannels.size.toString(), PlasmaIcons.Lock, stringResource(R.string.locked_channels_text),
                        if (facts.lockedChannels.isEmpty()) {
                            SettingAction.None
                        } else {
                            SettingAction.Sheet { close -> LockedChannelsSheet(facts.lockedChannels, onLockChannel, close) }
                        },
                    ),
                    infoRow(
                        "locked-groups", groupsLabel, facts.lockedGroups.count { it.locked }.toString(), PlasmaIcons.Lock, stringResource(R.string.locked_groups_text),
                        if (facts.lockedGroups.isEmpty()) {
                            SettingAction.None
                        } else {
                            SettingAction.Sheet { close -> LockedGroupsSheet(facts.lockedGroups, onLockGroup, close) }
                        },
                    ),
                    choiceRow("unlock", stringResource(R.string.unlock_for), unlock, PlasmaIcons.Lock, stringResource(R.string.unlock_for_text)),
                    // Without a PIN there's nothing to ask for: choosing one comes first, then it's on.
                    if (facts.pinSet) {
                        switchRow("lock-settings", lockSettingsLabel, lockSettings, PlasmaIcons.Settings, lockSettingsText)
                    } else {
                        SettingItem(
                            id = "lock-settings",
                            label = lockSettingsLabel,
                            value = stringResource(R.string.off),
                            action = SettingAction.Go(SettingsDestination.LockSettings),
                            detail = SettingDetail(PlasmaIcons.Settings, lockSettingsLabel, text = lockSettingsText),
                        )
                    },
                ),
            ),
            // What kids profiles see (5.11).
            SettingSection(
                stringResource(R.string.section_kids_profiles),
                listOf(
                    infoRow(
                        "kid-safe-groups", stringResource(R.string.kid_safe_groups), facts.kidSafeGroups.count { it.kidSafe }.toString(), PlasmaIcons.People,
                        stringResource(R.string.kid_safe_groups_text),
                        if (facts.kidSafeGroups.isEmpty()) {
                            SettingAction.None
                        } else {
                            SettingAction.Sheet { close -> KidSafeGroupsSheet(facts.kidSafeGroups, onKidSafeGroup, close) }
                        },
                    ),
                    infoRow("kid-words", kidWordsLabel, facts.kidWords, PlasmaIcons.People, kidWordsText, kidWordsAction),
                ),
            ),
        ),
    )
}

@Composable
internal fun remotePage(values: SettingsValues, facts: SettingsFacts): PageSpec {
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
                        "phone", stringResource(R.string.phone_remote),
                        if (values.isOn(KEY_PHONE_REMOTE, true)) pluralStringResource(R.plurals.phone_remote_value, facts.pairedPhones.size, facts.pairedPhones.size) else stringResource(R.string.off),
                        PlasmaIcons.Remote,
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
internal fun aboutPage(values: SettingsValues, facts: SettingsFacts, onUpdate: (UpdateAction) -> Unit = {}, now: ZonedDateTime? = null): PageSpec {
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
                    updateRow(facts.update, facts.home.packageName, now, onUpdate),
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

/**
 * Plasma Update (brief 5.18): whether Plasma is up to date, and an update to install when there's
 * one, after Android's "Install unknown apps" is allowed for Plasma.
 */
@Composable
private fun updateRow(facts: UpdateFacts, packageName: String, now: ZonedDateTime?, onAction: (UpdateAction) -> Unit): SettingItem {
    val label = stringResource(R.string.plasma_update)
    val trouble = facts.problem?.let { stringResource(it.text()) }
    val checked = facts.checkedAt?.let { at -> stringResource(R.string.update_checked, if (now != null) updatedText(at, now) else Formats.time(at)) }
    val status: Status? = when (facts.status) {
        UpdateStatus.UpToDate -> if (trouble != null) Status(stringResource(R.string.update_couldnt_check), Tone.Warn) else Status(stringResource(R.string.update_up_to_date), Tone.Ok)
        UpdateStatus.Ready -> if (trouble != null) Status(stringResource(R.string.update_not_installed), Tone.Warn) else Status(stringResource(R.string.update_ready, facts.version), Tone.Ok)
        else -> null
    }
    val value = when (facts.status) {
        UpdateStatus.Off -> stringResource(R.string.update_off)
        UpdateStatus.NotChecked -> stringResource(R.string.update_not_checked)
        UpdateStatus.Checking -> stringResource(R.string.update_checking)
        UpdateStatus.Downloading -> if (facts.percent >= 0) stringResource(R.string.update_downloading, facts.percent) else stringResource(R.string.update_downloading_any)
        UpdateStatus.Installing -> stringResource(R.string.update_installing)
        UpdateStatus.UpToDate, UpdateStatus.Ready -> status?.text
    }
    val text = when (facts.status) {
        UpdateStatus.Off -> stringResource(R.string.update_off_text)
        UpdateStatus.NotChecked -> stringResource(R.string.update_not_checked_text)
        UpdateStatus.UpToDate -> trouble?.let { stringResource(R.string.update_try_again, it) } ?: stringResource(R.string.update_up_to_date_text)
        UpdateStatus.Checking -> stringResource(R.string.update_checking_text)
        UpdateStatus.Ready -> trouble ?: facts.notes.ifEmpty { stringResource(R.string.update_ready_text) }
        UpdateStatus.Downloading -> stringResource(R.string.update_downloading_text, facts.version)
        UpdateStatus.Installing -> stringResource(R.string.update_installing_text, facts.version)
    }
    val action = when (facts.status) {
        UpdateStatus.NotChecked, UpdateStatus.UpToDate -> SettingAction.Run { onAction(UpdateAction.CheckNow) }
        UpdateStatus.Ready -> if (facts.allowed) installSheet(facts, onAction) else allowSheet(facts, packageName, onAction)
        // The TV's question again, if it went away without an answer.
        UpdateStatus.Installing -> SettingAction.Run { onAction(UpdateAction.Install) }
        UpdateStatus.Off, UpdateStatus.Checking, UpdateStatus.Downloading -> SettingAction.None
    }
    return SettingItem(
        id = "update",
        label = label,
        value = value,
        status = status?.tone,
        chevron = action != SettingAction.None,
        action = action,
        detail = SettingDetail(PlasmaIcons.Download, label, subline = checked, status = status, text = text),
    )
}

/** The update's notes, then Install Now (brief 5.18). */
@Composable
private fun installSheet(facts: UpdateFacts, onAction: (UpdateAction) -> Unit) = SettingAction.Explain(
    title = stringResource(R.string.update_title, facts.version),
    icon = PlasmaIcons.Download,
    text = facts.notes.ifEmpty { stringResource(R.string.update_ready_text) },
    points = listOf(
        CheckItem(true, stringResource(R.string.update_keeps)),
        CheckItem(true, stringResource(R.string.update_restarts)),
    ),
    options = listOf(
        MenuOption(stringResource(R.string.install_now)) { onAction(UpdateAction.Install) },
        MenuOption(stringResource(R.string.not_now)),
    ),
)

/**
 * Android's "Install unknown apps" for Plasma, which installing its updates needs once. Many TVs
 * hide the setting, so Plasma can allow it over its pairing with the TV.
 */
@Composable
private fun allowSheet(facts: UpdateFacts, packageName: String, onAction: (UpdateAction) -> Unit) = SettingAction.Explain(
    title = stringResource(R.string.allow_updates),
    icon = PlasmaIcons.Download,
    text = stringResource(R.string.allow_updates_text),
    points = listOf(
        CheckItem(true, stringResource(R.string.allow_updates_only)),
        CheckItem(true, stringResource(R.string.allow_updates_asks)),
    ),
    note = stringResource(R.string.allow_updates_command, packageName),
    options = listOfNotNull(
        MenuOption(stringResource(R.string.allow_over_debugging)) { onAction(UpdateAction.AllowOverWirelessDebugging) },
        if (facts.tvSettings) MenuOption(stringResource(R.string.open_tv_settings)) { onAction(UpdateAction.OpenTvSettings) } else null,
        MenuOption(stringResource(R.string.not_now)),
    ),
)

private fun UpdateTrouble.text(): Int = when (this) {
    UpdateTrouble.CantReach -> R.string.update_cant_reach
    UpdateTrouble.NotThere -> R.string.update_not_there
    UpdateTrouble.BadFile -> R.string.update_bad_file
    UpdateTrouble.Damaged -> R.string.update_damaged
    UpdateTrouble.NotPlasma -> R.string.update_not_plasma
    UpdateTrouble.NotAllowed -> R.string.allow_updates_text
    UpdateTrouble.Refused -> R.string.update_refused
    UpdateTrouble.NoRoom -> R.string.update_no_room
    UpdateTrouble.Recording -> R.string.update_recording
}

internal const val KEY_PAST_DAYS = "pastDays"
internal const val KEY_AHEAD_DAYS = "aheadDays"
internal const val KEY_UPDATE_TIME = "updateTime"
internal const val KEY_MATCHING = "matching"
internal const val KEY_LOCK_ADULT = SettingKeys.LOCK_ADULT
internal const val KEY_AGE_LIMIT = SettingKeys.AGE_LIMIT
internal const val KEY_UNLOCK_FOR = SettingKeys.UNLOCK_FOR
internal const val KEY_LOCK_SETTINGS = SettingKeys.LOCK_SETTINGS
internal const val AGE_LIMIT_DEFAULT = SettingsChoices.AGE_LIMIT_DEFAULT
internal const val KEY_ASK_WHO = SettingKeys.ASK_WHO
internal const val KEY_LONG_PRESS_OK = "longPressOk"
internal const val KEY_NUMBER_DELAY = "numberDelay"
internal const val KEY_CHANNEL_UP = "channelUp"
internal const val KEY_ACCENT = SettingKeys.ACCENT
internal const val KEY_TEXT_SIZE = SettingKeys.TEXT_SIZE
internal const val KEY_REDUCE_MOTION = SettingKeys.REDUCE_MOTION
internal const val KEY_SHOW_NUMBERS = SettingKeys.SHOW_NUMBERS
internal const val KEY_CHECK_UPDATES = SettingKeys.CHECK_UPDATES
internal const val KEY_UPDATE_CHANNEL = "updateChannel"
