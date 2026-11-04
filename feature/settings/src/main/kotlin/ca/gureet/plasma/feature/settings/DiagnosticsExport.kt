// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.QrModules
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import kotlinx.coroutines.flow.Flow
import ca.gureet.plasma.core.design.R as DesignR

/**
 * About › Export Diagnostics (brief 5.17): recent logs, with logins, passwords and server
 * addresses taken out, saved to a USB drive ([onSaveToUsb]; the app says how it went), or fetched
 * on a phone, whose page also opens an issue on GitHub to attach them to (6.11).
 */
@Composable
internal fun diagnosticsMenu(onSaveToUsb: () -> Unit, phone: () -> Flow<PhonePairing>): SettingAction.Menu = SettingAction.Menu(
    title = stringResource(R.string.diagnostics),
    subtitle = stringResource(R.string.diagnostics_menu_text),
    options = listOf(
        MenuOption(stringResource(R.string.diagnostics_to_usb), run = onSaveToUsb),
        MenuOption(
            stringResource(R.string.diagnostics_to_phone),
            sheet = SettingAction.Sheet { close -> DiagnosticsPhoneSheet(phone, close) },
        ),
    ),
)

/**
 * The QR code for a phone (5.17 and 6.11), from Export Diagnostics, or from Report a Problem
 * ([report]), whose words lead with the issue. It's the same page on the phone. The TV's local
 * server runs while the sheet shows, since [phone] runs it while it's collected; Close or Back ends it.
 */
@Composable
internal fun DiagnosticsPhoneSheet(phone: () -> Flow<PhonePairing>, onClose: () -> Unit, report: Boolean = false) {
    val pairing by remember(phone) { phone() }.collectAsState(PhonePairing.Starting)
    val offline = pairing == PhonePairing.Unavailable
    SignInSheet(
        title = stringResource(if (report) R.string.report_problem else R.string.diagnostics_phone_title),
        text = stringResource(
            when {
                offline -> R.string.diagnostics_phone_offline
                report -> R.string.report_phone_text
                else -> R.string.diagnostics_phone_text
            },
        ),
        onClose = onClose,
        options = listOf(SheetOption(stringResource(DesignR.string.key_close), onClick = onClose)),
        icon = PlasmaIcons.Phone,
        body = if (offline) null else { { PhoneCode(pairing) } },
    )
}

/** The code to scan, then the address and code to type instead, as Add a Source shows them. */
@Composable
private fun ColumnScope.PhoneCode(pairing: PhonePairing) {
    val ready = pairing as? PhonePairing.Ready
    Box(
        Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 28.px)
            .background(PlasmaColors.white(1f), RoundedCornerShape(18.px))
            .padding(18.px),
    ) {
        // With the plate's margin, about four modules of white: what a camera wants.
        if (ready != null) QrModules(ready.qr, Modifier.size(264.px), quietZone = 2) else Box(Modifier.size(264.px))
    }
    if (ready != null) {
        PlasmaText(
            stringResource(R.string.open_and_enter, ready.address) + " " + ready.code,
            plasmaStyle(20f, 500, lineHeight = 1.4f),
            Modifier.align(Alignment.CenterHorizontally).padding(top = 18.px),
            color = PlasmaColors.Text2,
            align = TextAlign.Center,
        )
    }
}
