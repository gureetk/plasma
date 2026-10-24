// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.rememberNow
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.fixtures.fx
import ca.gureet.plasma.core.model.Formats
import java.time.temporal.ChronoUnit
import ca.gureet.plasma.core.design.R as DesignR

internal const val KEY_PHONE_REMOTE = "phoneRemote"

// ---- Home Screen setup (board 23) ------------------------------------------------------------------

/**
 * The ways to make Plasma the Home screen (brief 4.23 and 5.13), with "This TV" over the list: what
 * the Home button does now, and each way as this TV offers it. On the sample TV Home Button
 * Override is on, and the TV doesn't let an app become its Home app.
 */
@Composable
internal fun homeScreenPage(home: HomeScreenFacts, onHome: (HomeAction) -> Unit): PageSpec {
    val replace = stringResource(R.string.replace_home)
    val replaced = home.method == HomeScreenMethod.Replace
    return PageSpec(
        title = stringResource(R.string.home_screen),
        back = stringResource(R.string.launcher),
        style = PageStyle.Setup,
        metrics = RowMetrics.Setup,
        detailTop = 166.px,
        header = {
            ThisTvCard(
                label = stringResource(R.string.this_tv),
                device = stringResource(R.string.tv_device, home.device, stringResource(R.string.tv_system, home.androidVersion)),
                status = homeStatus(home),
            )
        },
        sections = listOf(
            SettingSection(
                stringResource(R.string.section_home_ways),
                listOf(
                    overrideRow(home, onHome),
                    setupRow(
                        id = "replace",
                        label = replace,
                        subtitle = stringResource(R.string.replace_home_subtitle),
                        value = stringResource(if (replaced) R.string.on else R.string.set_up),
                        status = if (replaced) Tone.Ok else null,
                        icon = PlasmaIcons.Terminal,
                        subline = stringResource(R.string.replace_home_subline),
                        text = stringResource(R.string.replace_home_text),
                        checks = listOf(
                            CheckItem(true, stringResource(R.string.replace_home_once)),
                            CheckItem(true, stringResource(R.string.replace_home_not_deleted)),
                            CheckItem(false, stringResource(R.string.replace_home_updates)),
                            CheckItem(false, stringResource(R.string.replace_home_inputs)),
                        ),
                        action = SettingAction.Go(SettingsDestination.ReplaceHome),
                    ),
                    defaultHomeRow(home, onHome),
                ),
            ),
            SettingSection(
                stringResource(R.string.section_home_wrong),
                listOf(
                    // Replaced: Plasma undoes it over its pairing. Otherwise the commands show how.
                    setupRow(
                        id = "restore",
                        label = stringResource(R.string.restore_home),
                        subtitle = stringResource(R.string.restore_home_subtitle),
                        value = null,
                        icon = PlasmaIcons.History,
                        text = stringResource(R.string.restore_home_text),
                        action = if (replaced) {
                            SettingAction.Menu(
                                title = stringResource(R.string.restore_confirm),
                                subtitle = stringResource(R.string.restore_confirm_text),
                                // Cancel first, so a second OK can't undo it by accident.
                                options = listOf(
                                    MenuOption(stringResource(R.string.cancel)),
                                    MenuOption(stringResource(R.string.restore_now)) { onHome(HomeAction.Restore) },
                                ),
                            )
                        } else {
                            SettingAction.Go(SettingsDestination.ReplaceHome)
                        },
                    ),
                    setupRow(
                        id = "help",
                        label = stringResource(R.string.home_help),
                        subtitle = stringResource(R.string.home_help_subtitle),
                        value = null,
                        icon = PlasmaIcons.Terminal,
                        text = stringResource(R.string.home_help_text),
                        action = SettingAction.Go(SettingsDestination.ReplaceHome),
                    ),
                ),
            ),
        ),
    )
}

/** What the Home button does on this TV now, for This TV: another override that's on comes first. */
@Composable
private fun homeStatus(home: HomeScreenFacts): Status = when {
    home.conflicts.isNotEmpty() && home.overrideOn -> Status(stringResource(R.string.home_status_conflict, home.conflicts.first()), Tone.Warn)
    home.method == HomeScreenMethod.Override -> Status(stringResource(R.string.home_status_override), Tone.Ok)
    home.method == HomeScreenMethod.Replace -> Status(stringResource(R.string.home_status_replaced, home.launcherName), Tone.Ok)
    home.method == HomeScreenMethod.DefaultApp -> Status(stringResource(R.string.home_status_default), Tone.Ok)
    home.defaultHome == DefaultHomeOffer.Chooser -> Status(stringResource(R.string.home_status_chooser), Tone.Idle)
    else -> Status(stringResource(R.string.home_status_stock, home.launcherName), Tone.Idle)
}

/**
 * Home Button Override: off, OK explains it and opens Accessibility (brief 4.23 and 4.35); on, OK
 * offers to turn it off, first when Plasma is already the Home app another way (one method at a
 * time).
 */
@Composable
private fun overrideRow(home: HomeScreenFacts, onHome: (HomeAction) -> Unit): SettingItem {
    val label = stringResource(R.string.home_override)
    val conflict = home.conflicts.firstOrNull()?.let { stringResource(R.string.home_status_conflict, it) }
    val notNeeded = home.overrideOn && (home.method == HomeScreenMethod.Replace || home.method == HomeScreenMethod.DefaultApp)
    val action = if (home.overrideOn) {
        val turnOff = MenuOption(stringResource(R.string.turn_off)) { onHome(HomeAction.TurnOverrideOff) }
        val keep = MenuOption(stringResource(R.string.keep_on))
        SettingAction.Menu(
            title = label,
            subtitle = when {
                notNeeded -> stringResource(R.string.override_not_needed)
                conflict != null -> conflict
                else -> stringResource(R.string.override_on_text)
            },
            options = listOf(turnOff, keep),
        )
    } else {
        val restricted = home.restricted
        SettingAction.Explain(
            title = label,
            icon = PlasmaIcons.House,
            text = stringResource(R.string.override_sheet_text),
            points = listOf(
                CheckItem(true, stringResource(R.string.override_sheet_sees)),
                CheckItem(true, stringResource(R.string.override_sheet_never)),
            ),
            note = if (restricted) stringResource(R.string.override_sheet_restricted, home.packageName) else null,
            options = listOfNotNull(
                MenuOption(stringResource(R.string.open_accessibility)) { onHome(HomeAction.OpenAccessibility) },
                // A TV that holds the switch back, or hides it: Plasma can turn it on over its pairing with the TV.
                MenuOption(stringResource(R.string.turn_on_over_debugging)) { onHome(HomeAction.TurnOnOverDebugging) },
                if (restricted) MenuOption(stringResource(R.string.open_app_info)) { onHome(HomeAction.OpenAppInfo) } else null,
                MenuOption(stringResource(R.string.not_now)),
            ),
        )
    }
    return setupRow(
        id = "override",
        label = label,
        subtitle = stringResource(R.string.home_override_subtitle),
        value = stringResource(if (home.overrideOn) R.string.on else R.string.off),
        status = if (home.overrideOn) Tone.Ok else null,
        icon = PlasmaIcons.House,
        text = stringResource(R.string.home_override_text),
        checks = listOfNotNull(
            conflict?.let { CheckItem(false, it) },
            CheckItem(true, stringResource(R.string.home_override_good)),
            CheckItem(true, stringResource(R.string.home_override_private)),
            CheckItem(false, stringResource(R.string.home_override_flash)),
        ),
        action = action,
    )
}

/** Default Home App, as this TV offers it: dimmed where it doesn't (brief 5.13, method 3). */
@Composable
private fun defaultHomeRow(home: HomeScreenFacts, onHome: (HomeAction) -> Unit): SettingItem {
    val on = home.method == HomeScreenMethod.DefaultApp
    val offered = home.defaultHome != DefaultHomeOffer.None
    val text = when {
        on -> R.string.default_home_text_on
        else -> when (home.defaultHome) {
            DefaultHomeOffer.Role -> R.string.default_home_text_role
            DefaultHomeOffer.Settings -> R.string.default_home_text_settings
            DefaultHomeOffer.Chooser -> R.string.default_home_text_chooser
            DefaultHomeOffer.None -> R.string.default_home_text
        }
    }
    return setupRow(
        id = "default",
        label = stringResource(R.string.default_home),
        subtitle = stringResource(R.string.default_home_subtitle),
        value = stringResource(
            when {
                on -> R.string.on
                offered -> R.string.set_up
                else -> R.string.default_home_value
            },
        ),
        status = if (on) Tone.Ok else null,
        icon = PlasmaIcons.Screen,
        text = stringResource(text),
        action = if (!on && offered) SettingAction.Run { onHome(HomeAction.SetDefaultHome) } else SettingAction.None,
        chevron = !on && offered,
        dimmed = !on && !offered,
    )
}

@Composable
private fun setupRow(
    id: String,
    label: String,
    subtitle: String,
    value: String?,
    icon: PlasmaIcons,
    text: String,
    subline: String = subtitle,
    checks: List<CheckItem> = emptyList(),
    status: Tone? = null,
    action: SettingAction = SettingAction.None,
    chevron: Boolean = true,
    dimmed: Boolean = false,
) = SettingItem(
    id = id,
    label = label,
    value = value,
    subtitle = subtitle,
    status = status,
    action = action,
    chevron = chevron,
    dimmed = dimmed,
    detail = SettingDetail(
        icon = icon,
        title = label,
        subline = subline,
        text = text,
        extra = if (checks.isEmpty()) null else ({ CheckList(checks) }),
    ),
)

// ---- Phone Remote (board 33, the TV's side) --------------------------------------------------------

/**
 * Settings › Remote & Buttons › Phone Remote (brief 4.33): the QR code and address a phone opens,
 * and the phones paired already. Rename and Forget come with the phone page itself (M7).
 */
@Composable
internal fun phoneRemotePage(values: SettingsValues, facts: SettingsFacts): PageSpec {
    val on = values.switch(KEY_PHONE_REMOTE, true)
    val now by rememberNow()
    val address = "${facts.address}:${facts.phonePort}"
    val scan = SettingDetail(
        icon = PlasmaIcons.Phone,
        title = stringResource(R.string.phone_remote),
        text = stringResource(R.string.phone_remote_scan),
        extra = {
            QrCode(fx("pair_qr_remote"), 212.px, 16.px, 16.px, Modifier.padding(top = 30.px))
            AddressLine(stringResource(R.string.open_and_enter), address, Modifier.padding(top = 24.px))
            PlasmaText(facts.phoneCode, plasmaStyle(44f, 600, trackingPx = 6f, tabular = true), Modifier.padding(top = 6.px))
            DetailNote(stringResource(R.string.phone_code_note))
        },
    )
    val phones = facts.pairedPhones.map { phone ->
        val days = ChronoUnit.DAYS.between(phone.lastUsed.toLocalDate(), now.withZoneSameInstant(phone.lastUsed.zone).toLocalDate())
        val lastUsed = when (days) {
            0L -> stringResource(DesignR.string.today)
            1L -> stringResource(DesignR.string.yesterday)
            else -> Formats.date(phone.lastUsed)
        }
        val lastUsedLine = when (days) {
            0L -> stringResource(R.string.phone_last_used_today)
            1L -> stringResource(R.string.phone_last_used_yesterday)
            else -> stringResource(R.string.phone_last_used, lastUsed)
        }
        SettingItem(
            id = "phone-${phone.name}",
            label = phone.name,
            value = if (phone.connected) stringResource(R.string.phone_connected) else lastUsed,
            status = if (phone.connected) Tone.Ok else null,
            chevron = false,
            detail = SettingDetail(
                icon = PlasmaIcons.Phone,
                title = phone.name,
                subline = if (phone.connected) stringResource(R.string.phone_connected_now) else lastUsedLine,
                text = stringResource(R.string.phone_text),
            ),
        )
    }
    return compactPage(
        stringResource(R.string.phone_remote),
        stringResource(R.string.remote),
        listOf(
            SettingSection(
                null,
                listOf(SettingItem(id = "phone-remote", label = stringResource(R.string.phone_remote), value = on.value, action = on.action, detail = scan)),
            ),
            SettingSection(stringResource(R.string.section_paired_phones), phones),
        ),
    )
}
