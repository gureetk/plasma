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
 * The ways to make Plasma the Home screen (brief 4.23 and 5.13), with "This TV" over the list. On
 * the sample TV Home Button Override is on, and the other TV's chooser isn't offered.
 */
@Composable
internal fun homeScreenPage(facts: SettingsFacts): PageSpec {
    val replace = stringResource(R.string.replace_home)
    return PageSpec(
        title = stringResource(R.string.home_screen),
        back = stringResource(R.string.launcher),
        style = PageStyle.Setup,
        metrics = RowMetrics.Setup,
        detailTop = 166.px,
        header = {
            ThisTvCard(
                label = stringResource(R.string.this_tv),
                device = stringResource(R.string.tv_device, facts.tvModel, facts.tvSystem),
                status = Status(stringResource(R.string.home_status_override), Tone.Ok),
            )
        },
        sections = listOf(
            SettingSection(
                stringResource(R.string.section_home_ways),
                listOf(
                    setupRow(
                        id = "override",
                        label = stringResource(R.string.home_override),
                        subtitle = stringResource(R.string.home_override_subtitle),
                        value = stringResource(R.string.on),
                        status = Tone.Ok,
                        icon = PlasmaIcons.House,
                        text = stringResource(R.string.home_override_text),
                        checks = listOf(
                            CheckItem(true, stringResource(R.string.home_override_good)),
                            CheckItem(true, stringResource(R.string.home_override_private)),
                            CheckItem(false, stringResource(R.string.home_override_flash)),
                        ),
                    ),
                    setupRow(
                        id = "replace",
                        label = replace,
                        subtitle = stringResource(R.string.replace_home_subtitle),
                        value = stringResource(R.string.set_up),
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
                    setupRow(
                        id = "default",
                        label = stringResource(R.string.default_home),
                        subtitle = stringResource(R.string.default_home_subtitle),
                        value = stringResource(R.string.default_home_value),
                        icon = PlasmaIcons.Screen,
                        text = stringResource(R.string.default_home_text),
                        chevron = false,
                        dimmed = true,
                    ),
                ),
            ),
            SettingSection(
                stringResource(R.string.section_home_wrong),
                listOf(
                    setupRow(
                        id = "restore",
                        label = stringResource(R.string.restore_home),
                        subtitle = stringResource(R.string.restore_home_subtitle),
                        value = null,
                        icon = PlasmaIcons.History,
                        text = stringResource(R.string.restore_home_text),
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
